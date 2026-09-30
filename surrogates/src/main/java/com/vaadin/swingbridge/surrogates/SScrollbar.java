/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.ClickNotifier;
import com.vaadin.flow.component.KeyNotifier;
import com.vaadin.flow.component.html.RangeInput;
import com.vaadin.flow.component.shared.HasTooltip;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;

import java.awt.Adjustable;

/**
 * Surrogate for {@link java.awt.Scrollbar} — the AWT 1.0 scrollbar, not
 * {@link javax.swing.JScrollBar}. Extends Vaadin {@link RangeInput}
 * ({@code <input type=range>}) directly (is-a) and picks up the AWT
 * {@code Component} API from {@link ComponentMixin}.
 *
 * <pre>{@code
 * SScrollbar bar = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 255);
 * bar.addValueChangeListener(e -> tint(e.getValue().intValue()));
 * bar.setValues(128, 10, 0, 255);   // clamped, silent
 * }</pre>
 *
 * <h2>It renders as a slider, not as a scrollbar</h2>
 *
 * AWT paints a track, two end arrows, and a thumb whose <em>length</em> is
 * {@code visibleAmount / (maximum - minimum)}. No HTML control has a
 * resizable thumb or end arrows, so this renders as a plain range slider:
 * functionally faithful (value, band, orientation, drag, keyboard, events),
 * visually a different widget. That trade is deliberate — AWT 1.0 had no
 * slider, so a {@code Scrollbar} <em>was</em> the zoom / volume / colour
 * knob, and that idiom reads correctly as a slider. The hand-rolled-scroller
 * idiom (a {@code Scrollbar} beside a {@code Canvas}) does not, and is
 * already out of scope via {@code Canvas} painting (R_match_swing_errors sub-bucket (b)).
 *
 * <h2>Vertical is the default, and needs two properties</h2>
 *
 * {@code new Scrollbar()} is <em>vertical</em> — unlike every Swing slider —
 * so vertical rendering is the common path, not a corner case.
 * {@link RangeInput#setOrientation} alone is not enough: it sets only the
 * pre-standardization recipe ({@code appearance: slider-vertical}, removed in
 * Chromium 132; {@code writing-mode: bt-lr}, never standardized) and on its
 * own renders a square blob in current Chromium. {@link #applyOrientationCss}
 * adds the standardized {@code writing-mode: vertical-lr}, which is what
 * actually works — and, unlike the {@code transform: rotate(-90deg)} that
 * {@code SJSlider} uses, it changes the element's <em>layout box</em>, so a
 * vertical bar in {@code BorderLayout.EAST} gets a region its own width
 * rather than one sized for a wide short control. It also puts the minimum at
 * the <em>top</em>, which is AWT's own convention.
 *
 * <h2>The value band is not [minimum, maximum]</h2>
 *
 * AWT's reachable band is {@code [minimum, maximum - visibleAmount]}, so the
 * peer's {@code max} carries {@code maximum - visibleAmount} and
 * {@link #getMaximum} reconstructs by adding {@link #visibleAmount} back.
 * {@code visibleAmount} is therefore not a shadow field: it genuinely drives
 * the peer's {@code max} (SD_sscrollbar records the choice — storing {@code maximum}
 * and deriving {@code visibleAmount} would be symmetric).
 *
 * <h2>No listener list here</h2>
 *
 * {@link java.awt.event.AdjustmentEvent}'s source parameter is typed
 * {@link Adjustable}, and this class cannot implement {@code Adjustable} —
 * {@code int getValue()} and {@code int getOrientation()} both clash
 * irreconcilably with Vaadin's {@code Double getValue()} /
 * {@code Orientation getOrientation()}. So the surrogate could not construct
 * an event naming itself, and the fan-out lives on the emulator
 * {@code vaadinx.awt.Scrollbar}, which bridges from Vaadin's
 * {@code addValueChangeListener}. This is the one structural departure from
 * {@link SButton} / {@link SLabel} (SD_sscrollbar).
 *
 * <h2>{@code step} stays 1</h2>
 *
 * AWT's {@code unitIncrement} is only an arrow-key delta over a continuous
 * value space; HTML's {@code step} quantizes the whole value space. Writing
 * {@code unitIncrement} into {@code step} would make values unreachable
 * (with {@code step=16} on a 0–90 band, {@code value=37} reads back 32 and
 * the band's top 90 reads back 80) and would manufacture value-change events
 * out of the browser's rounding of the migrator's own writes. So
 * {@code unitIncrement} is stored and inert — R_match_swing_errors sub-bucket (c), on a
 * property the JDK itself documents as ignorable by the underlying control.
 */
public class SScrollbar extends RangeInput
        implements ComponentMixin, HasTooltip, ClickNotifier<SScrollbar>, KeyNotifier {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    /**
     * Drives the peer's {@code max} (which carries
     * {@code maximum - visibleAmount}), so this is not a round-trip shadow.
     */
    private int visibleAmount;

    /** The arrow-key delta AWT means; no browser lever exists (R_match_swing_errors(c)). */
    private int unitIncrement = 1;

    /** The page-key delta AWT means; no browser lever exists (R_match_swing_errors(c)). */
    private int blockIncrement = 10;

    /**
     * True between the first {@code input} of a gesture and its
     * {@code change} — browser-driven, not a round-trip field.
     */
    private boolean valueIsAdjusting;

    public SScrollbar() {
        this(Adjustable.VERTICAL, 0, 10, 0, 100);
    }

    public SScrollbar(int orientation) {
        this(orientation, 0, 10, 0, 100);
    }

    /**
     * @param orientation {@link Adjustable#HORIZONTAL} or
     *        {@link Adjustable#VERTICAL} — {@link Adjustable#NO_ORIENTATION}
     *        is <em>not</em> legal here, matching AWT
     * @throws IllegalArgumentException on any other value, with AWT's own
     *         message, thrown before any peer write so no half-built
     *         scrollbar escapes
     */
    public SScrollbar(int orientation, int value, int visible, int minimum, int maximum) {
        super(ValueChangeMode.EAGER);
        _installSwingClass();
        // AWT validates orientation in the ctor's switch, before setValues.
        requireLegalOrientation(orientation);
        setStep(1d);
        applyOrientation(orientation);
        setValues(value, visible, minimum, maximum);
        installAdjustingTracker();
    }

    private static void requireLegalOrientation(int orientation) {
        if (orientation != Adjustable.HORIZONTAL && orientation != Adjustable.VERTICAL) {
            throw new IllegalArgumentException("illegal scrollbar orientation");
        }
    }

    /**
     * Tracks {@link #valueIsAdjusting} off the browser's own event pair: a
     * drag emits many {@code input}s (EAGER value changes) then one
     * {@code change}, so "adjusting" is true for exactly the span AWT reports
     * it for.
     *
     * <p>A keyboard arrow press emits <em>both</em> in one gesture, so
     * a single unit step produces two value changes (one adjusting,
     * one not) where AWT produces one. Suppressing the second would
     * break the "commit when isAdjusting goes false" idiom that is
     * the property's whole point, so the duplicate is the lesser
     * evil (SD_sscrollbar).
     */
    private void installAdjustingTracker() {
        addValueChangeListener(e -> {
            if (e.isFromClient()) valueIsAdjusting = true;
        });
        getElement().addEventListener("change", e -> valueIsAdjusting = false);
    }

    // --- the value model ----------------------------------------------

    /**
     * AWT's single clamping funnel: {@code setValue}, {@code setMinimum},
     * {@code setMaximum} and {@code setVisibleAmount} all route here, so the
     * DOM can never hold an out-of-band value. Never throws — every int,
     * {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE} included, is
     * clamped.
     *
     * <p>The JDK's five steps, verbatim: pin {@code minimum} below
     * {@code MAX_VALUE}; force {@code maximum > minimum}; cap the span at
     * {@code MAX_VALUE}; clamp {@code visible} into {@code [1, span]}; clamp
     * {@code value} into {@code [minimum, maximum - visible]}.
     */
    public void setValues(int value, int visible, int minimum, int maximum) {
        if (minimum == Integer.MAX_VALUE) {
            minimum = Integer.MAX_VALUE - 1;
        }
        if (maximum <= minimum) {
            maximum = minimum + 1;
        }
        if ((long) maximum - minimum > Integer.MAX_VALUE) {
            maximum = minimum + Integer.MAX_VALUE;
        }
        if (visible > maximum - minimum) {
            visible = maximum - minimum;
        }
        if (visible < 1) {
            visible = 1;
        }
        if (value < minimum) {
            value = minimum;
        }
        if (value > maximum - visible) {
            value = maximum - visible;
        }
        this.visibleAmount = visible;
        // min before max before value: RangeInput clamps a value written
        // outside the current bounds, so the bounds must land first.
        setMin(minimum);
        setMax(maximum - visible);
        setValue((double) value);
        // Then force the DOM property even when setValue was a no-op. A value
        // equal to RangeInput's own initial 0.0 makes setValue write nothing,
        // and an <input type=range> carrying no value attribute renders its
        // thumb at the *midpoint of its range* — so a fresh `new Scrollbar()`
        // (value 0, AWT's default) would show 45 on a 0-90 band while the
        // server said 0. Browser-caught, not test-caught: Karibu reads the
        // server-side value and sees nothing wrong.
        getElement().setProperty("value", (double) value);
    }

    /** @return the current value; int-exact across the whole reachable band */
    public int getIntValue() {
        Double v = getValue();
        return v == null ? 0 : (int) Math.round(v);
    }

    public void setIntValue(int value) {
        setValues(value, visibleAmount, getMinimum(), getMaximum());
    }

    public int getMinimum() {
        return (int) Math.round(getMin());
    }

    public void setMinimum(int minimum) {
        setValues(getIntValue(), visibleAmount, minimum, getMaximum());
    }

    /** @return {@code peer.max + visibleAmount} — the band's top is {@code max} */
    public int getMaximum() {
        return (int) Math.round(getMax()) + visibleAmount;
    }

    public void setMaximum(int maximum) {
        // AWT's two pre-steps, outside setValues: MIN_VALUE is nudged up, and
        // a minimum that would meet or pass the new maximum is rewritten
        // first rather than clamped afterwards.
        if (maximum == Integer.MIN_VALUE) {
            maximum = Integer.MIN_VALUE + 1;
        }
        int minimum = getMinimum();
        if (minimum >= maximum) {
            minimum = maximum - 1;
        }
        setValues(getIntValue(), visibleAmount, minimum, maximum);
    }

    public int getVisibleAmount() {
        return visibleAmount;
    }

    /**
     * Sets the span AWT would paint as the thumb's length.
     *
     * @param visible functional — it sets the peer's {@code max} and so the
     *        reachable band — but <em>invisible</em>: no HTML control has a
     *        resizable thumb (R_match_swing_errors sub-bucket (c), and not blocked-upstream,
     *        since the ceiling is the HTML control rather than a missing
     *        Vaadin API)
     */
    public void setVisibleAmount(int visible) {
        setValues(getIntValue(), visible, getMinimum(), getMaximum());
    }

    // --- orientation ---------------------------------------------------

    /** @return {@link Adjustable#HORIZONTAL} or {@link Adjustable#VERTICAL} */
    public int getAwtOrientation() {
        return getOrientation() == Orientation.VERTICAL
                ? Adjustable.VERTICAL : Adjustable.HORIZONTAL;
    }

    /**
     * @param orientation AWT's early-return on an unchanged value comes first,
     *        so an illegal int that happens to equal the current orientation
     *        cannot throw — it cannot be reached
     * @throws IllegalArgumentException with AWT's own message otherwise
     */
    public void setAwtOrientation(int orientation) {
        if (orientation == getAwtOrientation()) return;
        requireLegalOrientation(orientation);
        applyOrientation(orientation);
    }

    private void applyOrientation(int orientation) {
        boolean vertical = orientation == Adjustable.VERTICAL;
        setOrientation(vertical ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        applyOrientationCss(vertical);
    }

    /**
     * Adds what {@link RangeInput#setOrientation} is missing.
     *
     * @param vertical writes the standardized {@code writing-mode:
     *        vertical-lr}, which overrides the {@code bt-lr} Vaadin sets and
     *        is the property that actually renders vertically on current
     *        engines. Vaadin's stale {@code appearance: slider-vertical} is
     *        left in place — measured inert once a valid writing-mode is in
     *        force, so stripping it would be noise.
     */
    private void applyOrientationCss(boolean vertical) {
        if (vertical) {
            getStyle().set("writing-mode", "vertical-lr");
        } else {
            getStyle().remove("writing-mode");
        }
    }

    // --- increments ----------------------------------------------------

    public int getUnitIncrement() {
        return unitIncrement;
    }

    /**
     * @param unitIncrement clamped up to 1, as AWT does. Stored and inert:
     *        the only browser lever is {@code step}, which would quantize the
     *        whole value space rather than just the arrow delta (R_match_swing_errors(c); see
     *        the class javadoc)
     */
    public void setUnitIncrement(int unitIncrement) {
        this.unitIncrement = Math.max(1, unitIncrement);
    }

    public int getBlockIncrement() {
        return blockIncrement;
    }

    /**
     * @param blockIncrement clamped up to 1, as AWT does. Stored and inert:
     *        PageUp/PageDown on {@code <input type=range>} move by a
     *        browser-chosen amount that is not settable (R_match_swing_errors(c))
     */
    public void setBlockIncrement(int blockIncrement) {
        this.blockIncrement = Math.max(1, blockIncrement);
    }

    // --- valueIsAdjusting ----------------------------------------------

    /** @return true while a browser drag is in flight */
    public boolean getValueIsAdjusting() {
        return valueIsAdjusting;
    }

    /**
     * @param valueIsAdjusting advisory — the browser overwrites it on the
     *        next gesture (R_match_swing_errors(c)). Would become blocked-upstream if Vaadin
     *        exposed a pointer-down/up signal on {@code RangeInput}
     */
    public void setValueIsAdjusting(boolean valueIsAdjusting) {
        this.valueIsAdjusting = valueIsAdjusting;
    }

    // --- Overrides forced by "classes beat interfaces" ----------------

    /**
     * Fires SD_auto_pce's auto-PCE, then lets {@code RangeInput} do its own work.
     *
     * <p><em>Not</em> {@code ComponentMixin.super.setEnabled(...)} —
     * the SD_sbutton redirect is wrong on this host. {@code RangeInput}
     * keeps its own {@code enabled} field and reconciles it with
     * {@code readOnly}; routing past that leaves the field stale, so
     * {@code isEnabled()} would disagree with the DOM.
     */
    @Override
    public void setEnabled(boolean enabled) {
        // No "enabled" property change. java.awt.Component.setEnabled fires
        // nothing bound (only AccessibleContext.ACCESSIBLE_STATE_PROPERTY, on
        // the accessible context's own list); the "enabled" bound property is
        // JComponent's override, and java.awt.Scrollbar is not a JComponent.
        // ComponentMixin therefore no longer fires it either — the fire moved
        // to JComponentMixin, mirroring where the JDK puts it (SD_property_fanout_audit).
        super.setEnabled(enabled);
    }

    /**
     * AWT's {@code Scrollbar.paramString} tail. Note {@code ,vert} /
     * {@code ,horz} carry no {@code =}.
     */
    protected String paramString() {
        return "val=" + getIntValue()
                + ",vis=" + visibleAmount
                + ",min=" + getMinimum()
                + ",max=" + getMaximum()
                + (getAwtOrientation() == Adjustable.VERTICAL ? ",vert" : ",horz")
                + ",isAdjusting=" + valueIsAdjusting;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
