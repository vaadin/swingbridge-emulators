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

package com.vaadin.swingbridge.surrogates.awt;

import com.vaadin.flow.component.BlurNotifier;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ClickNotifier;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.FocusNotifier;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.KeyNotifier;
import com.vaadin.flow.component.internal.KeyboardEvent;
import com.vaadin.flow.component.shared.HasTooltip;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.util.CssConvert;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.util.KeyConvert;
import com.vaadin.swingbridge.surrogates.awt.event.SComponentEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SComponentListener;
import com.vaadin.swingbridge.surrogates.awt.event.SFocusEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SFocusListener;
import com.vaadin.swingbridge.surrogates.awt.event.SHierarchyBoundsListener;
import com.vaadin.swingbridge.surrogates.awt.event.SHierarchyEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SHierarchyListener;
import com.vaadin.swingbridge.surrogates.awt.event.SInputEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyListener;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseListener;
import com.vaadin.swingbridge.surrogates.internal.NameStore;
import com.vaadin.swingbridge.surrogates.internal.PceSupport;
import com.vaadin.swingbridge.surrogates.internal.Registrations;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputMethodListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/**
 * Mixin that adds the {@link java.awt.Component} API surface to any Vaadin
 * component subclass. See {@code surrogates/architecture.md} for the design.
 *
 * <p><strong>Vaadin-first direct binding (SD_vaadin_first_binding):</strong> every Swing method
 * routes straight to its Vaadin counterpart when one exists; where Vaadin
 * has no direct analog we warn + noop rather than carrying a server-side
 * shadow cache. Listener registrations wire Vaadin subscriptions eagerly
 * via the unified {@link Registrations} registry, so {@code addFocusListener}
 * actually fires on browser focus — no store-only intermediate state.
 *
 * <p>Extends {@link HasEnabled} and {@link HasSize} so {@code setEnabled} /
 * {@code isEnabled} / {@code getElement} and Vaadin's CSS-string
 * {@code setWidth} / {@code getHeight} family are available without conflict.
 * {@code setVisible} / {@code isVisible} come from the surrogate's Vaadin
 * parent. Surrogates of Vaadin classes that implement neither cannot
 * implement this mixin.
 *
 * <p>Lossy round-trip is accepted (SD_vaadin_first_binding): {@code setForeground} /
 * {@code getForeground} reconstruct the {@link Color} from CSS each time,
 * which may lose one bit of alpha precision; {@code setFont} /
 * {@code getFont} similarly collapse intermediate CSS weights to
 * plain/bold and non-px sizes to 12. Appears identical to user code for
 * the common cases.
 *
 * <p>Excluded entirely: {@code getParent}, {@code add/remove(Component)},
 * {@code getComponent(int)}, {@code getComponents()}, {@code getComponentCount()},
 * {@code isAncestorOf(Component)}. Hard signature clash with Vaadin's
 * hierarchy API; Vaadin's wins.
 *
 * <p><strong>UI-thread-confined</strong> — the layer-wide default for every
 * surrogate (a surrogate is-a Vaadin component, and Vaadin mutation is
 * UI-thread-only). Migrated code reaching in from a background thread marshals
 * via {@code UI.access}; peer→Swing event callbacks re-enter on the UI thread
 * through {@link SHelper#callSwing} (R_callswing_envelope). Stated here once for the whole
 * hierarchy: concrete surrogates note only where a method deviates (self-marshals
 * or is safe off-thread), and none re-declare the baseline.
 */
public interface ComponentMixin extends HasEnabled, HasSize {

    /**
     * Cast {@code this} to the Vaadin {@link Component} concrete class.
     * Used by holder lookups since {@link com.vaadin.flow.component.ComponentUtil}'s
     * data API requires {@link Component}, not {@link com.vaadin.flow.component.HasElement}.
     */
    private Component _self() {
        return (Component) this;
    }

    // --- name (NameStore — no Vaadin analog) -------------------------

    /** Reads the {@link NameStore} slot. */
    default String getName() {
        return NameStore.of(_self()).get();
    }

    /**
     * Writes to {@link NameStore} only — deliberately does <em>not</em> touch
     * Vaadin's {@code setId}. Vaadin's id is a CSS-targetable element
     * identifier; Swing's name is metadata that no Swing UI inspects.
     *
     * <p>Mirrors the name onto the {@code data-swing-name} DOM attribute so
     * the value is visible in DevTools without exposing it through {@code id}
     * (which would force uniqueness Swing's name doesn't have). Cleared when
     * name is {@code null} / blank.
     *
     * <p>Fires {@code "name"} PCE per Swing's Component contract (SD_auto_pce).
     */
    default void setName(String name) {
        String old = getName();
        if (Objects.equals(old, name)) return;
        NameStore.of(_self()).set(name);
        if (name == null || name.isBlank()) {
            _self().getElement().removeAttribute("data-swing-name");
        } else {
            _self().getElement().setAttribute("data-swing-name", name);
        }
        firePropertyChange("name", old, name);
    }

    /**
     * Stamp {@code data-swing-class} on this surrogate's element using its
     * runtime class. Called once from each surrogate's constructor; an
     * emulator wrapping this surrogate will overwrite with its own class
     * (last-write-wins is the desired stage-2/stage-3 distinction).
     */
    default void _installSwingClass() {
        SHelper.stampSwingClass(_self(), getClass());
    }

    // --- enabled (HasEnabled with auto-PCE) --------------------------

    /**
     * Overrides {@link HasEnabled#setEnabled(boolean)} for the no-op-on-equal
     * guard. Fires <b>no</b> {@code "enabled"} property change:
     * {@code java.awt.Component.setEnabled} delegates to the deprecated
     * {@code enable()}, which fires only
     * {@code AccessibleContext.ACCESSIBLE_STATE_PROPERTY} on the accessible
     * context's own listener list — nothing a
     * {@code component.addPropertyChangeListener} ever sees. The bound
     * {@code "enabled"} property is {@link com.vaadin.swingbridge.surrogates.swing.JComponentMixin}'s
     * override, which is where the JDK puts it too (SD_property_fanout_audit), so AWT-level
     * surrogates ({@code SButton}, {@code SLabel}, {@code SChoice},
     * {@code SCheckbox}, {@code SList}, {@code SScrollbar}, {@code SScrollPane},
     * {@code SWindow}) correctly stay silent.
     */
    @Override
    default void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) return;
        HasEnabled.super.setEnabled(enabled);
    }

    // --- tooltip (HasTooltip — delegate or WARN, no store) -----------

    /**
     * Delegates directly to Vaadin's {@link HasTooltip#setTooltipText(String)}.
     * Non-HasTooltip peers log via {@link SHelper#onUnimplemented} — the
     * Vaadin-first stance skips a server-side shadow cache: if the peer
     * can't render a tooltip, we don't pretend to carry the value.
     */
    default void setToolTipText(String text) {
        if (this instanceof HasTooltip ht) {
            ht.setTooltipText(text);
        } else {
            SHelper.onUnimplemented(this, "setToolTipText", text);
        }
    }

    /**
     * Reads from {@link HasTooltip#getTooltip()}; non-HasTooltip peers
     * log and return {@code null}. No server-side store: the tooltip
     * value is whatever the peer currently carries.
     */
    default String getToolTipText() {
        if (this instanceof HasTooltip ht) {
            return ht.getTooltip() == null ? null : ht.getTooltip().getText();
        }
        SHelper.onUnimplemented(this, "getToolTipText");
        return null;
    }

    // --- focus (delegates to Focusable.focus() if available) ---------

    /**
     * Vaadin's analog is {@link Focusable#focus()}. {@code instanceof}-gated
     * so non-Focusable surrogates log an onUnimplemented. The four variants
     * collapse — we have no server-side distinction for AWT's
     * temporary/permanent or in-window/global focus.
     */
    default void requestFocus() {
        if (this instanceof Focusable<?> f) {
            f.focus();
            // Move the focus pointer now rather than waiting for the browser's
            // focusin, so a same-round-trip isFocusOwner() / getFocusOwner()
            // read agrees with what we just asked for (SD_focus_tracker).
            com.vaadin.swingbridge.surrogates.FocusTracker.setFocusOwner(_self());
        } else {
            SHelper.onUnimplemented(this, "requestFocus");
        }
    }

    /**
     * Returns {@code true} unconditionally — Vaadin's {@code focus()} is
     * fire-and-forget. Matches the AWT default of "we tried, we don't know."
     */
    default boolean requestFocus(boolean temporary) {
        requestFocus();
        return true;
    }

    /** Same path as {@link #requestFocus()}. */
    default void requestFocusInWindow() {
        requestFocus();
    }

    /** See {@link #requestFocus(boolean)}. */
    default boolean requestFocusInWindow(boolean temporary) {
        requestFocus();
        return true;
    }

    /** Swing's {@code grabFocus} is "request focus and force it"; Vaadin has no force option. */
    default void grabFocus() {
        requestFocus();
    }

    // --- foreground / background / font (CSS, lossy round-trip) ------
    //
    // No server-side AppearanceStore — getters reconstruct from CSS every
    // time (SD_vaadin_first_binding). Color alpha round-trips to one bit of slop; fonts
    // collapse weights outside bold/normal and non-px sizes to 12. The
    // alternative (shadow cache) drifts if anything writes the DOM style
    // independently of the mixin.

    /** Reconstructs the foreground {@link Color} from CSS {@code color}; {@code null} if unset/unparseable. */
    default Color getForeground() {
        return CssConvert.colorFromCss(getElement().getStyle().get("color"));
    }

    /**
     * Writes CSS {@code color}. {@code null} removes the property for
     * parent-inheritance — matches Swing's "null means inherit" contract.
     *
     * <p>Fires {@code "foreground"} PCE per Swing's Component contract (SD_auto_pce).
     * Old-value is read through {@link #getForeground()} which reconstructs
     * from CSS — one-bit alpha lossiness can cause a second identical set
     * to fire a spurious PCE (reconstructed alpha ≠ original alpha). Same
     * SD_border_css_lossy/SD_auto_pce round-trip caveat applies.
     */
    default void setForeground(Color c) {
        Color old = getForeground();
        if (Objects.equals(old, c)) return;
        Style style = getElement().getStyle();
        if (c == null) style.remove("color");
        else style.set("color", CssConvert.toCss(c));
        firePropertyChange("foreground", old, c);
    }

    /** Reconstructs the background {@link Color} from CSS {@code background-color}. */
    default Color getBackground() {
        return CssConvert.colorFromCss(getElement().getStyle().get("background-color"));
    }

    /**
     * CSS {@code background-color}. {@code null} removes the property.
     * Fires {@code "background"} PCE per Swing's Component contract (SD_auto_pce).
     */
    default void setBackground(Color c) {
        Color old = getBackground();
        if (Objects.equals(old, c)) return;
        Style style = getElement().getStyle();
        if (c == null) style.remove("background-color");
        else style.set("background-color", CssConvert.toCss(c));
        firePropertyChange("background", old, c);
    }

    /**
     * Reconstructs a {@link Font} from the four CSS slots ({@code font-family} /
     * {@code font-size} / {@code font-weight} / {@code font-style}). Lossy — see
     * class-level Javadoc. When no font CSS is set, returns
     * {@link CssConvert#DEFAULT_FONT} rather than {@code null}: real Swing installs
     * an L&amp;F default font at construction, so {@code getFont()} is non-null, and
     * the ubiquitous GUI-builder idiom {@code getFont().deriveFont(...)} relies
     * on it. Callers that need the local-only "was a font explicitly set"
     * answer read the {@code font-*} style slots directly rather than diffing
     * against this default.
     */
    default Font getFont() {
        Style style = getElement().getStyle();
        Font f = CssConvert.fontFromCss(
                style.get("font-family"),
                style.get("font-size"),
                style.get("font-weight"),
                style.get("font-style"));
        return f != null ? f : CssConvert.DEFAULT_FONT;
    }

    /**
     * Writes the full font quartet on every set so a previous {@code setFont}'s
     * leftover style fragments don't bleed through. {@code null} removes all
     * four for parent-inheritance.
     *
     * <p>Fires {@code "font"} PCE per Swing's Component contract (SD_auto_pce).
     * Old-value comes from {@link #getFont()} which reconstructs from CSS —
     * weight/style quantisation (weights outside bold/normal collapse, etc.)
     * can cause a second identical set to fire a spurious PCE. Same SD_auto_pce
     * round-trip caveat as foreground/background.
     */
    default void setFont(Font f) {
        Font old = getFont();
        if (Objects.equals(old, f)) return;
        Style style = getElement().getStyle();
        if (f == null) {
            style.remove("font-family");
            style.remove("font-size");
            style.remove("font-weight");
            style.remove("font-style");
        } else {
            style.set("font-family", CssConvert.toCssFontFamily(f));
            style.set("font-size", f.getSize() + "px");
            style.set("font-weight", f.isBold() ? "bold" : "normal");
            style.set("font-style", f.isItalic() ? "italic" : "normal");
        }
        firePropertyChange("font", old, f);
    }

    // --- PropertyChangeListener (PceSupport — JDK has no Vaadin analog) -

    /** Backed by per-component {@link PropertyChangeSupport} via {@link PceSupport}. */
    default void addPropertyChangeListener(PropertyChangeListener listener) {
        PceSupport.of(_self()).add(listener);
    }

    /** Name-filtered registration — {@link PropertyChangeSupport} handles the name dispatch internally. */
    default void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        PceSupport.of(_self()).add(propertyName, listener);
    }

    /** Removes a previously registered general listener. */
    default void removePropertyChangeListener(PropertyChangeListener listener) {
        PceSupport.of(_self()).remove(listener);
    }

    /** Removes listeners registered with the matching property name. */
    default void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        PceSupport.of(_self()).remove(propertyName, listener);
    }

    /** Returns all listeners — general + name-filtered wrappers per JDK contract. */
    default PropertyChangeListener[] getPropertyChangeListeners() {
        return PceSupport.of(_self()).getListeners();
    }

    /** Returns only listeners registered for the given property name. */
    default PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return PceSupport.of(_self()).getListeners(propertyName);
    }

    /**
     * Fires to registered listeners. {@link #setName}, {@link #setEnabled},
     * {@link #setForeground}, {@link #setBackground}, {@link #setFont} all
     * auto-fire under this path (SD_auto_pce).
     */
    default void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        PceSupport.of(_self()).fire(propertyName, oldValue, newValue);
    }

    /** Boolean overload. */
    default void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        PceSupport.of(_self()).fire(propertyName, oldValue, newValue);
    }

    /** Int overload. */
    default void firePropertyChange(String propertyName, int oldValue, int newValue) {
        PceSupport.of(_self()).fire(propertyName, oldValue, newValue);
    }

    // --- SComponentListener (eager wire to attach/detach) ------------

    /**
     * Registers the listener via {@link Registrations} and wires the peer's
     * attach/detach events to dispatch {@link SComponentEvent#COMPONENT_SHOWN}
     * and {@link SComponentEvent#COMPONENT_HIDDEN}. Resize and move aren't
     * surfaced by Vaadin server-side in the general case; those callbacks
     * never fire today (SD_vaadin_first_binding accepts partial coverage — browser is authoritative).
     */
    default void addComponentListener(SComponentListener listener) {
        final Component self = _self();
        Registration r1 = self.addAttachListener(e ->
                listener.componentShown(new SComponentEvent(self, SComponentEvent.COMPONENT_SHOWN)));
        Registration r2 = self.addDetachListener(e ->
                listener.componentHidden(new SComponentEvent(self, SComponentEvent.COMPONENT_HIDDEN)));
        Registrations.of(self).add(listener, () -> { r1.remove(); r2.remove(); });
    }

    /** Symmetric — removes both peer subscriptions wired by {@link #addComponentListener}. */
    default void removeComponentListener(SComponentListener listener) {
        Registrations.of(_self()).remove(listener);
    }

    /** Returns the currently registered {@link SComponentListener}s in registration order. */
    default SComponentListener[] getComponentListeners() {
        return Registrations.of(_self()).getListeners(SComponentListener.class);
    }

    // --- bounds / size (HasSize, px) ---------------------------------

    /** See class-level "Bucket A" — x/y can't be honoured under CSS flow; logs via {@link SHelper#onNoop}. */
    default void setBounds(int x, int y, int width, int height) {
        SHelper.onNoop(this, "setBounds.location");
        setSize(width, height);
    }
    /** {@link Rectangle} overload. */
    default void setBounds(Rectangle r) {
        if (r == null) { SHelper.onNoop(this, "setBounds"); return; }
        setBounds(r.x, r.y, r.width, r.height);
    }
    /** {@code (0, 0, width, height)} — origin is always (0, 0) since we have no server-side pixel coordinate. */
    default Rectangle getBounds() {
        Dimension size = getSize();
        return new Rectangle(0, 0, size.width, size.height);
    }

    /** Writes pixel dimensions via {@link HasSize} in {@code "Npx"} form. */
    default void setSize(int width, int height) {
        setWidth(width + "px");
        setHeight(height + "px");
    }
    /** {@link Dimension} overload; {@code null} is onNoop. */
    default void setSize(Dimension d) {
        if (d == null) { SHelper.onNoop(this, "setSize"); return; }
        setSize(d.width, d.height);
    }
    /** Reads {@link HasSize} back; non-pixel values fall back to 1 (see {@link CssConvert#parsePxOr1}). */
    default Dimension getSize() {
        return new Dimension(CssConvert.parsePxOr1(getWidth()), CssConvert.parsePxOr1(getHeight()));
    }

    /**
     * Bucket B {@link SHelper#onNoop} — no absolute-positioning layout
     * exists at this layer (R_layouts_close_enough: pixel-accurate layout is out of
     * scope permanently — D_pixel_layout_not_planned). This is {@code onNoop} rather than
     * {@code onUnimplemented}: there is no upstream-blocked counterpart we
     * are waiting on, so a WARN would be misleading.
     */
    default void setLocation(int x, int y) { SHelper.onNoop(this, "setLocation"); }
    /** {@link Point} overload — see {@link #setLocation(int, int)}. */
    default void setLocation(Point p) { SHelper.onNoop(this, "setLocation"); }
    /** Origin — chained reads don't NPE. */
    default Point getLocation() { SHelper.onNoop(this, "getLocation"); return new Point(); }

    /** Bucket B — see {@link #setLocation(int, int)}. */
    default int getX() { SHelper.onNoop(this, "getX"); return 0; }
    /** Bucket B — see {@link #setLocation(int, int)}. */
    default int getY() { SHelper.onNoop(this, "getY"); return 0; }

    /**
     * Routes to {@link #setSize(Dimension)}. Vaadin has only one dimension
     * per axis; "preferred" and "actual" collapse onto the same CSS value.
     *
     * <p>Fires {@code "preferredSize"} and <em>only</em> that name, even though the
     * collapse means this call also changes what {@link #getMinimumSize()} and
     * {@link #getMaximumSize()} answer. AWT fires one name per setter, and firing the
     * other two would be inventing events real Swing does not send — the thing
     * [SD_no_invented_events](../../../../../../surrogates/decisions.md) rules out. See
     * {@link #sizeBefore()} for how the old value reproduces AWT's null-for-unset
     * without a shadow flag.
     */
    default void setPreferredSize(Dimension d) {
        Dimension old = sizeBefore();
        setSize(d);
        firePropertyChange("preferredSize", old, d);
    }
    /** Reads the single HasSize dimension — same value {@link #getSize()} returns. */
    default Dimension getPreferredSize() { return getSize(); }
    /** See {@link #setPreferredSize(Dimension)}. */
    default void setMinimumSize(Dimension d) {
        Dimension old = sizeBefore();
        setSize(d);
        firePropertyChange("minimumSize", old, d);
    }
    /** See {@link #getPreferredSize()}. */
    default Dimension getMinimumSize() { return getSize(); }
    /** See {@link #setPreferredSize(Dimension)}. */
    default void setMaximumSize(Dimension d) {
        Dimension old = sizeBefore();
        setSize(d);
        firePropertyChange("maximumSize", old, d);
    }
    /** See {@link #getPreferredSize()}. */
    default Dimension getMaximumSize() { return getSize(); }

    /**
     * The old value for the three size events: the current size, or {@code null} when
     * no size has been set.
     *
     * <p>AWT carries a {@code prefSizeSet} / {@code minSizeSet} / {@code maxSizeSet}
     * flag purely so the first event's old value is {@code null} rather than a
     * computed size. R_vaadin_first forbids a shadow flag here and none is needed: an unset CSS
     * dimension already reads back as {@code null}, so Vaadin's own property absence
     * *is* the flag. {@link #getSize()} cannot express that, because
     * {@code parsePxOr1} floors it at 1.
     */
    private Dimension sizeBefore() {
        return getWidth() == null && getHeight() == null ? null : getSize();
    }

    // --- paint / repaint / validate (onNoop — Vaadin renders) --------

    /** Deliberate no-op; the browser repaints when the DOM changes. */
    default void repaint() { SHelper.onNoop(this, "repaint"); }
    /** Timed-repaint variant. */
    default void repaint(long tm) { SHelper.onNoop(this, "repaint"); }
    /** Region repaint. */
    default void repaint(int x, int y, int w, int h) { SHelper.onNoop(this, "repaint"); }
    /** Timed region repaint. */
    default void repaint(long tm, int x, int y, int w, int h) { SHelper.onNoop(this, "repaint"); }
    /** {@link Rectangle} region repaint. */
    default void repaint(Rectangle r) { SHelper.onNoop(this, "repaint"); }

    /** Deliberate no-op; Vaadin has no Swing-style validate cycle. */
    default void revalidate() { SHelper.onNoop(this, "revalidate"); }
    /** Deliberate no-op. */
    default void invalidate() { SHelper.onNoop(this, "invalidate"); }
    /** Deliberate no-op. */
    default void validate() { SHelper.onNoop(this, "validate"); }
    /** Deliberate no-op; layout is browser-driven. */
    default void doLayout() { SHelper.onNoop(this, "doLayout"); }

    /** Deliberate no-op; no {@link java.awt.Graphics} to paint into. */
    default void paint(java.awt.Graphics g) { SHelper.onNoop(this, "paint"); }
    /** Deliberate no-op. */
    default void update(java.awt.Graphics g) { SHelper.onNoop(this, "update"); }
    /** Deliberate no-op. */
    default void paintAll(java.awt.Graphics g) { SHelper.onNoop(this, "paintAll"); }
    /** Deliberate no-op. */
    default void print(java.awt.Graphics g) { SHelper.onNoop(this, "print"); }
    /** Deliberate no-op. */
    default void printAll(java.awt.Graphics g) { SHelper.onNoop(this, "printAll"); }
    /** Returns {@code null} — no offscreen Graphics exists for a Vaadin component. */
    default java.awt.Graphics getGraphics() { SHelper.onNoop(this, "getGraphics"); return null; }

    // --- AWT peer lifecycle (onNoop) ---------------------------------

    /** Deliberate no-op; AWT native-peer lifecycle has no Vaadin analog. */
    default void addNotify() { SHelper.onNoop(this, "addNotify"); }
    /** Deliberate no-op. */
    default void removeNotify() { SHelper.onNoop(this, "removeNotify"); }

    // --- AWT event listeners (SD_vaadin_first_binding: Vaadin-first direct binding) ------
    //
    // Focus / SComponent / Hierarchy / Key listeners wire Vaadin subscriptions
    // eagerly via Registrations. Mouse / InputMethod stay WARN: mouse
    // semantics vary per peer and can't be mapped uniformly; InputMethod
    // has no web analog.

    /**
     * Registers with {@link FocusNotifier}/{@link BlurNotifier} when the peer
     * supports them, dispatching {@link SFocusEvent} via the user's
     * {@link SFocusListener}. Non-focusable peers log and skip wiring —
     * there's nothing useful to listen to.
     */
    default void addFocusListener(SFocusListener l) {
        Component self = _self();
        if (!(this instanceof FocusNotifier<?>) && !(this instanceof BlurNotifier<?>)) {
            SHelper.onUnimplemented(this, "addFocusListener/non-focusable-peer", l);
            return;
        }
        Registration focusReg = (this instanceof FocusNotifier<?> fn)
                ? fn.addFocusListener(e -> l.focusGained(new SFocusEvent(self, SFocusEvent.FOCUS_GAINED)))
                : null;
        Registration blurReg = (this instanceof BlurNotifier<?> bn)
                ? bn.addBlurListener(e -> l.focusLost(new SFocusEvent(self, SFocusEvent.FOCUS_LOST)))
                : null;
        Registrations.of(self).add(l, () -> {
            if (focusReg != null) focusReg.remove();
            if (blurReg != null) blurReg.remove();
        });
    }
    /** Symmetric — removes the wired Vaadin subscriptions. */
    default void removeFocusListener(SFocusListener l) { Registrations.of(_self()).remove(l); }
    /** Returns currently registered focus listeners in registration order. */
    default SFocusListener[] getFocusListeners() {
        return Registrations.of(_self()).getListeners(SFocusListener.class);
    }

    /**
     * Registers with {@link KeyNotifier} when the peer supports it,
     * dispatching {@link SKeyEvent} via the user's {@link SKeyListener}.
     * Non-{@link KeyNotifier} peers log and skip wiring — no browser-side
     * keyboard event can reach them, so storing the listener would mislead.
     *
     * <p>One user listener installs three Vaadin subscriptions and composes
     * them into a single {@link Registration}: {@code keydown →
     * KEY_PRESSED}, {@code keypress → KEY_TYPED}, {@code keyup →
     * KEY_RELEASED}. Every dispatch wraps through {@link SHelper#callSwing}
     * (R_callswing_envelope) so the blocking-dialog hook lands in one place.
     * See SD_key_events for the mapping rationale.
     */
    default void addKeyListener(SKeyListener l) {
        Component self = _self();
        if (!(this instanceof KeyNotifier kn)) {
            SHelper.onUnimplemented(this, "addKeyListener/non-KeyNotifier-peer", l);
            return;
        }
        Registration down = kn.addKeyDownListener(e -> SHelper.callSwing(() ->
                l.keyPressed(toSKeyEvent(self, SKeyEvent.KEY_PRESSED, e))));
        Registration press = kn.addKeyPressListener(e -> SHelper.callSwing(() ->
                dispatchTyped(self, e, l)));
        Registration up = kn.addKeyUpListener(e -> SHelper.callSwing(() ->
                l.keyReleased(toSKeyEvent(self, SKeyEvent.KEY_RELEASED, e))));
        Registrations.of(self).add(l, () -> {
            down.remove();
            press.remove();
            up.remove();
        });
    }
    /** Symmetric — removes all three wired Vaadin subscriptions. */
    default void removeKeyListener(SKeyListener l) { Registrations.of(_self()).remove(l); }
    /** Returns currently registered key listeners in registration order. */
    default SKeyListener[] getKeyListeners() {
        return Registrations.of(_self()).getListeners(SKeyListener.class);
    }

    /** KeyDown/KeyUp → KEY_PRESSED/KEY_RELEASED conversion; shared shape. */
    private static SKeyEvent toSKeyEvent(Component source, int id, KeyboardEvent e) {
        return new SKeyEvent(source, id,
                System.currentTimeMillis(),
                KeyConvert.keyModifiersToMask(e.getModifiers()),
                KeyConvert.vaadinKeyToVK(e.getKey()),
                KeyConvert.keyCharFrom(e.getKey()),
                KeyConvert.keyLocationToAwt(e.getLocation()));
    }

    /**
     * KeyPress → KEY_TYPED dispatch. Browser keypress only fires for
     * character-producing keys, so keyChar will be defined in practice; if
     * an implementation ever feeds us a CHAR_UNDEFINED keypress we silently
     * drop it rather than trip {@link SKeyEvent}'s KEY_TYPED invariant
     * (keyChar != CHAR_UNDEFINED, keyCode == VK_UNDEFINED). Skipping the
     * dispatch beats throwing IAE on the Vaadin request thread.
     */
    private static void dispatchTyped(Component source, KeyboardEvent e, SKeyListener l) {
        char keyChar = KeyConvert.keyCharFrom(e.getKey());
        if (keyChar == SKeyEvent.CHAR_UNDEFINED) return;
        l.keyTyped(new SKeyEvent(source, SKeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                KeyConvert.keyModifiersToMask(e.getModifiers()),
                SKeyEvent.VK_UNDEFINED, keyChar));
    }

    /**
     * Registers with Vaadin {@link ClickNotifier} when the peer supports it,
     * dispatching {@link SMouseEvent#MOUSE_CLICKED} via the user's
     * {@link SMouseListener#mouseClicked}. Non-{@link ClickNotifier} peers
     * log {@link SHelper#onUnimplemented} and skip wiring.
     *
     * <p><strong>Partial coverage.</strong> Only
     * {@code mouseClicked} fires under this wire. The press / release /
     * enter / exit overloads are part of the JDK contract — your listener
     * implements them — but never reach the user. Vaadin doesn't surface
     * those DOM mouse events server-side without a custom listener stack
     * (out of scope here); same shape that
     * {@link #addKeyListener(SKeyListener)} takes by covering only the
     * {@link KeyNotifier} subset of the JDK key surface. {@code addMouseMotionListener}
     * and {@code addMouseWheelListener} stay drop-and-WARN with a
     * documented gap below — neither has a Vaadin counterpart at all.
     *
     * <p>Every dispatch wraps through {@link SHelper#callSwing} (R_callswing_envelope) so the
     * blocking-dialog hook lands in one place. Modifier
     * translation: Vaadin's four boolean modifier flags
     * (ctrl/shift/alt/meta) project to the {@code *_DOWN_MASK} bits;
     * button mapping is Vaadin-DOM (0/1/2 left/middle/right) →
     * {@link SMouseEvent#BUTTON1}/{@code BUTTON2}/{@code BUTTON3}, with
     * unknown ({@code -1}) reading as {@link SMouseEvent#NOBUTTON}.
     */
    default void addMouseListener(SMouseListener l) {
        Component self = _self();
        if (!(this instanceof ClickNotifier<?>)) {
            SHelper.onUnimplemented(this, "addMouseListener/non-ClickNotifier-peer", l);
            return;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        ClickNotifier<Component> cn = (ClickNotifier) this;
        Registration reg = cn.addClickListener(e -> SHelper.callSwing(() ->
                l.mouseClicked(toSMouseEvent(self, e))));
        Registrations.of(self).add(l, reg);
    }
    /** Symmetric — removes the wired Vaadin subscription. */
    default void removeMouseListener(SMouseListener l) {
        Registrations.of(_self()).remove(l);
    }
    /** Returns currently registered mouse listeners in registration order. */
    default SMouseListener[] getMouseListeners() {
        return Registrations.of(_self()).getListeners(SMouseListener.class);
    }

    /** WARN — Vaadin has no server-side mouse-motion event source. */
    default void addMouseMotionListener(MouseMotionListener l) { SHelper.onUnimplemented(this, "addMouseMotionListener", l); }
    /** WARN — see {@link #addMouseMotionListener(MouseMotionListener)}. */
    default void removeMouseMotionListener(MouseMotionListener l) { SHelper.onUnimplemented(this, "removeMouseMotionListener", l); }
    /** WARN — Vaadin has no server-side mouse-wheel event source. */
    default void addMouseWheelListener(MouseWheelListener l) { SHelper.onUnimplemented(this, "addMouseWheelListener", l); }
    /** WARN — see {@link #addMouseWheelListener(MouseWheelListener)}. */
    default void removeMouseWheelListener(MouseWheelListener l) { SHelper.onUnimplemented(this, "removeMouseWheelListener", l); }

    /** ClickEvent → SMouseEvent translation; shared by the listener wire. */
    private static SMouseEvent toSMouseEvent(Component source, ClickEvent<?> e) {
        int modifiers = 0;
        if (e.isShiftKey()) modifiers |= SInputEvent.SHIFT_DOWN_MASK;
        if (e.isCtrlKey())  modifiers |= SInputEvent.CTRL_DOWN_MASK;
        if (e.isAltKey())   modifiers |= SInputEvent.ALT_DOWN_MASK;
        if (e.isMetaKey())  modifiers |= SInputEvent.META_DOWN_MASK;
        // Vaadin's DOM button: 0=left, 1=middle, 2=right, -1=unknown.
        // Map to JDK BUTTON1/2/3 (offset by +1) or NOBUTTON for unknown.
        int button = switch (e.getButton()) {
            case 0  -> SMouseEvent.BUTTON1;
            case 1  -> SMouseEvent.BUTTON2;
            case 2  -> SMouseEvent.BUTTON3;
            default -> SMouseEvent.NOBUTTON;
        };
        return new SMouseEvent(source, SMouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), modifiers,
                e.getClientX(), e.getClientY(),
                e.getScreenX(), e.getScreenY(),
                e.getClickCount(), false, button);
    }
    /** WARN — input-method events have no web equivalent. */
    default void addInputMethodListener(InputMethodListener l) { SHelper.onUnimplemented(this, "addInputMethodListener", l); }
    /** WARN. */
    default void removeInputMethodListener(InputMethodListener l) { SHelper.onUnimplemented(this, "removeInputMethodListener", l); }

    /**
     * Wires peer attach/detach to {@link SHierarchyEvent#HIERARCHY_CHANGED}
     * with {@link SHierarchyEvent#DISPLAYABILITY_CHANGED} /
     * {@link SHierarchyEvent#SHOWING_CHANGED} flags set — that's the subset
     * Vaadin surfaces cleanly. {@link SHierarchyEvent#PARENT_CHANGED} fires
     * on attach when the new parent differs from the previous (first attach
     * still counts).
     */
    default void addHierarchyListener(SHierarchyListener l) {
        Component self = _self();
        Registration attach = self.addAttachListener(e -> {
            long flags = SHierarchyEvent.DISPLAYABILITY_CHANGED | SHierarchyEvent.SHOWING_CHANGED
                    | SHierarchyEvent.PARENT_CHANGED;
            Component parent = self.getParent().orElse(null);
            l.hierarchyChanged(new SHierarchyEvent(self, SHierarchyEvent.HIERARCHY_CHANGED,
                    self, parent, flags));
        });
        Registration detach = self.addDetachListener(e -> {
            long flags = SHierarchyEvent.DISPLAYABILITY_CHANGED | SHierarchyEvent.SHOWING_CHANGED;
            l.hierarchyChanged(new SHierarchyEvent(self, SHierarchyEvent.HIERARCHY_CHANGED,
                    self, null, flags));
        });
        Registrations.of(self).add(l, () -> { attach.remove(); detach.remove(); });
    }
    /** Symmetric to {@link #addHierarchyListener(SHierarchyListener)}. */
    default void removeHierarchyListener(SHierarchyListener l) { Registrations.of(_self()).remove(l); }
    /** Returns currently registered hierarchy listeners. */
    default SHierarchyListener[] getHierarchyListeners() {
        return Registrations.of(_self()).getListeners(SHierarchyListener.class);
    }

    /**
     * Bucket B {@link SHelper#onNoop} (SD_listeners_without_analog). Vaadin doesn't surface
     * ancestor pixel moves/resizes server-side, and R_layouts_close_enough's close-enough
     * layout stance means a {@link SHierarchyBoundsListener} has nothing
     * to fire on even if we tried to wire one. "Redundant by design" —
     * same category as {@code repaint} / {@code revalidate}.
     * {@link #getHierarchyBoundsListeners()} returns an empty array.
     */
    default void addHierarchyBoundsListener(SHierarchyBoundsListener l) {
        SHelper.onNoop(this, "addHierarchyBoundsListener");
    }
    /** Bucket B {@link SHelper#onNoop}. */
    default void removeHierarchyBoundsListener(SHierarchyBoundsListener l) {
        SHelper.onNoop(this, "removeHierarchyBoundsListener");
    }
    /** Always an empty array — see {@link #addHierarchyBoundsListener}. */
    default SHierarchyBoundsListener[] getHierarchyBoundsListeners() {
        SHelper.onNoop(this, "getHierarchyBoundsListeners");
        return new SHierarchyBoundsListener[0];
    }

    // --- programmatic event firing (no targets; WARN) ----------------

    /** Stub — programmatic event dispatch isn't wired under the Vaadin-first stance. */
    default void dispatchEvent(java.awt.AWTEvent e) { SHelper.onUnimplemented(this, "dispatchEvent", e); }

    // --- cursor / focus traversal / display predicates ---------------

    /**
     * Writes CSS {@code cursor}; {@code null} removes the property.
     * {@link Cursor#CUSTOM_CURSOR} and unmapped types log and skip the CSS
     * write (can't rasterise a custom image to a {@code data:} URL without
     * Graphics).
     */
    default void setCursor(Cursor cursor) {
        Style style = getElement().getStyle();
        if (cursor == null) {
            style.remove("cursor");
            return;
        }
        String css = CssConvert.toCssCursor(cursor);
        if (css == null) {
            SHelper.onUnimplemented(this, "setCursor", cursor);
            return;
        }
        style.set("cursor", css);
    }

    /** Reads CSS {@code cursor}; missing property → {@link Cursor#getDefaultCursor()}. */
    default Cursor getCursor() {
        String css = getElement().getStyle().get("cursor");
        return CssConvert.cursorFromCss(css);
    }

    /** {@code true} iff the component is attached to a live Vaadin {@link com.vaadin.flow.component.UI}. */
    default boolean isShowing() {
        return _self().getUI().isPresent();
    }

    /** Deliberate no-op; surrogates are always "displayable" in the AWT-peer sense. */
    default boolean isDisplayable() { SHelper.onNoop(this, "isDisplayable"); return true; }

    /** Heuristic — a {@link Focusable} peer is focusable, approximation otherwise. */
    default boolean isFocusable() { SHelper.onUnimplemented(this, "isFocusable"); return this instanceof Focusable<?>; }
    /** Stub — focusability isn't a settable concept in Vaadin. */
    default void setFocusable(boolean focusable) { SHelper.onUnimplemented(this, "setFocusable", focusable); }
    /**
     * Whether this surrogate holds browser focus, read off
     * {@link com.vaadin.swingbridge.surrogates.FocusTracker}'s per-UI pointer (SD_focus_tracker). Same answer
     * as {@link #isFocusOwner()} — AWT's split is about temporary focus loss,
     * which the browser does not model.
     */
    default boolean hasFocus() { return isFocusOwner(); }
    /** See {@link #hasFocus()}. */
    default boolean isFocusOwner() { return com.vaadin.swingbridge.surrogates.FocusTracker.getFocusOwner() == _self(); }

    // Traversal stays a stub here. It needs a component *ring* to step through,
    // and the emulator layer owns the Swing-shaped tree that defines the order
    // (D_focus_managers's KeyboardFocusManager); a surrogate has only the Vaadin parent
    // chain, where the browser's own Tab key already does the job.

    /** Stub — AWT focus traversal isn't modelled; the browser's Tab key handles the equivalent. */
    default void transferFocus() { SHelper.onUnimplemented(this, "transferFocus"); }
    /** Stub. */
    default void transferFocusBackward() { SHelper.onUnimplemented(this, "transferFocusBackward"); }
    /** Stub. */
    default void transferFocusUpCycle() { SHelper.onUnimplemented(this, "transferFocusUpCycle"); }
}
