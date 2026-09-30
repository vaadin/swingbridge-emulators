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

package com.vaadin.swingbridge.surrogates.swing;

import com.vaadin.flow.component.BlurNotifier;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.shared.HasTooltip;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.util.CssConvert;
import com.vaadin.swingbridge.surrogates.util.BorderCss;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.util.KeyConvert;
import com.vaadin.swingbridge.surrogates.SJPopupMenu;
import com.vaadin.swingbridge.surrogates.SJRootPane;
import com.vaadin.swingbridge.surrogates.awt.ContainerMixin;
import com.vaadin.swingbridge.surrogates.internal.ClientPropertyStore;
import com.vaadin.swingbridge.surrogates.internal.ComponentPopupStore;
import com.vaadin.swingbridge.surrogates.internal.InputMapStore;
import com.vaadin.swingbridge.surrogates.internal.InputVerifierStore;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.InputVerifier;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import java.awt.AWTKeyStroke;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.Objects;
import java.util.Set;

/**
 * Mixin that adds the {@link javax.swing.JComponent} API surface on top
 * of {@link ContainerMixin}. Under the Vaadin-first stance (SD_vaadin_first_binding), the
 * concrete behavioural additions are:
 *
 * <ul>
 *   <li>Client properties via {@link ClientPropertyStore}.</li>
 *   <li>Opaque flag via lossy CSS {@code background-color} readback (no shadow store).</li>
 *   <li>Border round-trip through CSS via {@link BorderCss#applyBorderCss} /
 *       {@link BorderCss#borderFromCss} (SD_border_css_lossy — lossy).</li>
 *   <li>InputMap/ActionMap storage via {@link InputMapStore} (lazy-alloc,
 *       no browser-side shortcut wiring — see SD_vaadin_first_binding deferral).</li>
 *   <li>{@link #setToolTipText(String)} override that adds the
 *       {@code "ToolTipText"} PCE fire on top of {@link ContainerMixin}'s
 *       HasTooltip delegation.</li>
 *   <li>Static default-locale accessors backed by {@link VaadinSession}.</li>
 *   <li>{@link #getTopLevelAncestor()} walks the Vaadin parent chain for
 *       the first {@code Dialog} ancestor — Vaadin's closest analog to
 *       a Swing "top-level window" (SD_top_level_ancestor).</li>
 * </ul>
 *
 * <p>The bulk of the JComponent surface that doesn't map to a Vaadin
 * feature (paint pipeline, L&amp;F, ancestor/vetoable listeners, transfer
 * handler, focus traversal, keyboard-action dispatch, etc.) is split
 * between {@link SHelper#onNoop} (redundant by design) and
 * {@link SHelper#onUnimplemented} (behaviour we'd need to build). See the
 * "Bucket B" and "Bucket C" comment headers below for the split.
 *
 * <p>Static methods on this interface are callable only via
 * {@code JComponentMixin.getDefaultLocale()} etc. — Java interface
 * statics aren't inherited by implementors, which matches JDK
 * {@code JComponent.getDefaultLocale()} in practice.
 */
public interface JComponentMixin extends ContainerMixin {

    // --- enabled (the bound property is JComponent's, not Component's) ----

    /**
     * Fires the {@code "enabled"} property change that
     * {@code JComponent.setEnabled} fires and
     * {@code java.awt.Component.setEnabled} does not — the whole reason this
     * override exists at the JComponent level rather than on
     * {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin} (SD_property_fanout_audit).
     *
     * <p>Old value is read through {@link #isEnabled()} (Vaadin's <em>effective</em>
     * enabled), so a disabled ancestor masks the local flag — matching what user
     * code can observe through the same getter.
     */
    @Override
    default void setEnabled(boolean enabled) {
        boolean old = isEnabled();
        if (old == enabled) return;
        ContainerMixin.super.setEnabled(enabled);
        firePropertyChange("enabled", old, enabled);
    }

    // --- Constants (migrated code passes these to getInputMap etc.) --

    /** Condition: binding fires when this component holds keyboard focus. */
    int WHEN_FOCUSED = 0;
    /** Condition: binding fires when this component is an ancestor of the focused component. */
    int WHEN_ANCESTOR_OF_FOCUSED_COMPONENT = 1;
    /** Condition: binding fires when this component is in the focused top-level window. */
    int WHEN_IN_FOCUSED_WINDOW = 2;
    /** Returned by {@link #getConditionForKeyStroke(KeyStroke)} when the stroke isn't bound. */
    int UNDEFINED_CONDITION = -1;
    /** JComponent's tooltip-text property name — this exact spelling (capital T) is what migrated PCE listeners filter on. */
    String TOOL_TIP_TEXT_KEY = "ToolTipText";

    /** See {@link ContainerMixin#_self()}. */
    private Component _self() {
        return (Component) this;
    }

    // --- Static locale API (VaadinSession-backed) ---------------------

    /**
     * Reads {@link VaadinSession#getCurrent()} + {@link VaadinSession#getLocale()}.
     * Throws {@link NullPointerException} when no session is current —
     * matches Swing's expectation that the default locale is always
     * addressable, but honest about where it actually lives in the web
     * model (per-session, not per-classloader AppContext).
     */
    static java.util.Locale getDefaultLocale() {
        return VaadinSession.getCurrent().getLocale();
    }

    /**
     * Writes through to {@link VaadinSession#setLocale(java.util.Locale)}.
     * Throws {@link NullPointerException} when no session is current —
     * same rationale as {@link #getDefaultLocale()}.
     */
    static void setDefaultLocale(java.util.Locale locale) {
        VaadinSession.getCurrent().setLocale(locale);
    }

    /**
     * Returns {@code true} unconditionally — there's no heavyweight/native-peer
     * concept in the browser, every Vaadin component is lightweight from
     * the AWT perspective. Signature takes Vaadin's {@link Component}
     * rather than {@code java.awt.Component} (migrated surrogates are
     * Vaadin components, not AWT components — SD_naming signature clash rule).
     */
    static boolean isLightweightComponent(Component c) {
        return true;
    }

    // --- Client properties (ClientPropertyStore) ----------------------

    /**
     * Swing contract: null {@code value} removes the mapping; firing a
     * {@link PropertyChangeEvent} named after {@code key.toString()} when
     * the value changes. Null key throws {@link NullPointerException} —
     * matches JDK.
     */
    default void putClientProperty(Object key, Object value) {
        Objects.requireNonNull(key, "key");
        Object old = ClientPropertyStore.of(_self()).put(key, value);
        if (!Objects.equals(old, value)) {
            firePropertyChange(key.toString(), old, value);
        }
    }

    /**
     * Null key returns null (not NPE — diverges from {@link #putClientProperty}
     * on purpose, matching JDK's asymmetry). Missing keys also return null.
     */
    default Object getClientProperty(Object key) {
        return ClientPropertyStore.of(_self()).get(key);
    }

    // --- Opaque (CSS background-color is the source of truth) --------

    /**
     * {@code setOpaque(true)} re-asserts the current background-color CSS
     * via {@link #getBackground()} (null → clear); {@code setOpaque(false)}
     * overrides with {@code transparent} so the parent's background
     * renders through. {@code "opaque"} PCE fires on transition so
     * migrated MVC sees the change.
     *
     * <p>No shadow store per R_vaadin_first — opacity state lives entirely in the
     * rendered CSS. {@link #isOpaque()} reads it back lossily: CSS
     * {@code background-color} unset or {@code "transparent"} → false;
     * any explicit color → true. Two accepted lossy directions:
     *
     * <ol>
     *   <li>{@code setBackground(red)} on a default-opaque component
     *       flips {@code isOpaque()} from false to true, even though
     *       JDK's contract says the two are independent — under our
     *       model, "is the rendered background visible?" is what
     *       {@code isOpaque()} answers, and CSS background drives
     *       rendering regardless of the JDK flag.</li>
     *   <li>{@code setOpaque(true)} without a prior {@code setBackground}
     *       leaves CSS unset (we don't model UIManager's L&amp;F default
     *       fill colour), so {@code isOpaque()} reads back as false.
     *       Callers that want true round-trip set the background first.</li>
     * </ol>
     *
     * <p>{@code setOpaque(false)} → {@code setOpaque(true)} also loses
     * the previous color (CSS at intermediate state was
     * {@code "transparent"}, which {@link CssConvert#colorFromCss} reads as
     * null). Migrators needing strict JDK round-trip register on the
     * emulator layer (R_swing_is_truth shadow-plus-drive-peer) or re-assert
     * {@code setBackground} after flipping back to true.
     */
    default void setOpaque(boolean opaque) {
        boolean old = isOpaque();
        com.vaadin.flow.dom.Style style = _self().getElement().getStyle();
        if (opaque) {
            java.awt.Color bg = getBackground();
            if (bg != null) {
                style.set("background-color", CssConvert.toCss(bg));
            } else {
                style.remove("background-color");
            }
        } else {
            style.set("background-color", "transparent");
        }
        boolean now = isOpaque();
        if (old != now) firePropertyChange("opaque", old, now);
    }

    /**
     * Reads CSS {@code background-color}: unset or {@code "transparent"}
     * → false (matches JDK JComponent default + {@link #setOpaque(boolean) false}
     * semantics); any other value → true. Lossy round-trip per R_vaadin_first — see
     * {@link #setOpaque(boolean)}.
     */
    default boolean isOpaque() {
        String bg = _self().getElement().getStyle().get("background-color");
        if (bg == null) return false;
        String trimmed = bg.trim();
        return !trimmed.isEmpty() && !"transparent".equals(trimmed);
    }

    // --- ToolTipText (override with PCE; delegates via HasTooltip) ---

    /**
     * Overrides {@link ContainerMixin}'s inherited tooltip delegation to
     * add the {@code "ToolTipText"} PCE fire that JComponent's contract
     * promises. Old value reads through {@link HasTooltip#getTooltip()}
     * before the write, so listeners registered for {@code "ToolTipText"}
     * see the before/after pair.
     *
     * <p>No field storage (SD_vaadin_first_binding) — non-HasTooltip peers log onUnimplemented
     * and skip the PCE since there's no value to round-trip.
     *
     * <p>The {@code "ToolTipText"} event is the JDK's, reached by a different
     * route: {@code JComponent.setToolTipText} has no fire of its own and gets
     * one because it stores the text via {@code putClientProperty}, which
     * fires {@code key.toString()}. A surrogate has no client-property table
     * (that would be a shadow {@code Store}, R_vaadin_first), so the fire is direct here —
     * same name, same values, and not an invented event (D_property_fanout_audit).
     */
    default void setToolTipText(String text) {
        if (!(this instanceof HasTooltip ht)) {
            SHelper.onUnimplemented(this, "setToolTipText", text);
            return;
        }
        String old = ht.getTooltip() == null ? null : ht.getTooltip().getText();
        if (Objects.equals(old, text)) return;
        ht.setTooltipText(text);
        firePropertyChange(TOOL_TIP_TEXT_KEY, old, text);
    }

    // --- Border (CSS round-trip via SHelper; SD_border_css_lossy lossy) --------------

    /**
     * Computes the effective CSS from {@code newBorder} and applies to the
     * element, then fires {@code "border"} PCE. {@link BorderCss#applyBorderCss}
     * clears the previous border's keys first. {@code null} is a clear.
     *
     * <p>SD_border_css_lossy: the border↔CSS round-trip is lossy — custom
     * {@link Border} implementations, {@link javax.swing.border.TitledBorder},
     * and {@link javax.swing.border.CompoundBorder} with a non-EmptyBorder
     * inner log via {@code onUnimplemented} and render nothing.
     */
    default void setBorder(Border newBorder) {
        Border old = getBorder();
        BorderCss.applyBorderCss(getElement(), newBorder);
        if (!Objects.equals(old, newBorder)) {
            firePropertyChange("border", old, newBorder);
        }
    }

    /** Reconstructs the closest {@link Border} from current CSS; {@code null} when unset. Lossy — see SD_border_css_lossy. */
    default Border getBorder() {
        return BorderCss.borderFromCss(getElement());
    }

    // --- Insets (derived from border) ---------------------------------

    /** Uses the installed border's insets when present, else zero. */
    default Insets getInsets() {
        Border b = getBorder();
        if (b == null) return new Insets(0, 0, 0, 0);
        // JDK Border.getBorderInsets(Component) takes java.awt.Component —
        // passing null works for every stock Border we reconstruct (they
        // don't consult the Component arg). Custom Borders may NPE; we
        // accept that lossy edge — user's out-of-scope border type.
        return b.getBorderInsets(null);
    }

    /** Fill-the-arg variant. Null arg allocates fresh per JDK. */
    default Insets getInsets(Insets insets) {
        Insets from = getInsets();
        if (insets == null) return from;
        insets.top = from.top;
        insets.left = from.left;
        insets.bottom = from.bottom;
        insets.right = from.right;
        return insets;
    }

    // --- Fill-the-arg getters (allocate if null, per JDK) -----------

    /** Fill-the-arg {@code getBounds} — reads from {@link #getBounds()}. */
    default Rectangle getBounds(Rectangle rv) {
        Rectangle from = getBounds();
        if (rv == null) return from;
        rv.x = from.x; rv.y = from.y; rv.width = from.width; rv.height = from.height;
        return rv;
    }

    /** Fill-the-arg {@code getLocation} — reads from {@link #getLocation()}. */
    default Point getLocation(Point rv) {
        Point from = getLocation();
        if (rv == null) return from;
        rv.x = from.x; rv.y = from.y;
        return rv;
    }

    /** Fill-the-arg {@code getSize} — reads from {@link #getSize()}. */
    default Dimension getSize(Dimension rv) {
        Dimension from = getSize();
        if (rv == null) return from;
        rv.width = from.width; rv.height = from.height;
        return rv;
    }

    // --- Hit test / visibility ---------------------------------------

    /**
     * Rectangle hit-test against {@link #getSize()}. Swing's JComponent
     * extends this to ask the UI delegate; we have no UI delegate (R_layouts_close_enough —
     * browser owns hit-testing), so the rectangle answer is the closest
     * honest match.
     */
    default boolean contains(int x, int y) {
        Dimension s = getSize();
        return x >= 0 && y >= 0 && x < s.width && y < s.height;
    }

    /** Scrolls this component into the visible browser viewport. Ignores the requested rectangle (R_best_effort_behaviour close-enough). */
    default void scrollRectToVisible(Rectangle aRect) {
        getElement().scrollIntoView();
    }

    /** Fills {@code visibleRect} with {@code (0, 0, width, height)}; origin is always (0, 0) under CSS flow. */
    default void computeVisibleRect(Rectangle visibleRect) {
        Dimension s = getSize();
        visibleRect.x = 0;
        visibleRect.y = 0;
        visibleRect.width = s.width;
        visibleRect.height = s.height;
    }

    /** Returns a fresh {@link Rectangle} at origin with current size. */
    default Rectangle getVisibleRect() {
        Dimension s = getSize();
        return new Rectangle(0, 0, s.width, s.height);
    }

    // --- InputMap / ActionMap (lazy-alloc storage; no browser wiring) -

    /** Equivalent to {@code getInputMap(WHEN_FOCUSED)}. */
    default InputMap getInputMap() {
        return getInputMap(WHEN_FOCUSED);
    }

    /**
     * Lazy-allocated InputMap for {@code condition}. Migrated code's
     * idiomatic {@code getInputMap().put(ks, key)} round-trips through
     * the stored map; browser-side shortcut wiring is deferred (SD_vaadin_first_binding) —
     * {@link #registerKeyboardAction} is the Bucket C WARN-stub for that.
     *
     * @throws IllegalArgumentException when {@code condition} isn't one
     *     of the three {@code WHEN_*} constants (matches JDK).
     */
    default InputMap getInputMap(int condition) {
        InputMapStore store = InputMapStore.of(_self());
        return switch (condition) {
            case WHEN_FOCUSED -> store.getWhenFocused();
            case WHEN_ANCESTOR_OF_FOCUSED_COMPONENT -> store.getWhenAncestor();
            case WHEN_IN_FOCUSED_WINDOW -> store.getWhenInWindow();
            default -> throw new IllegalArgumentException(
                    "condition must be one of WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW");
        };
    }

    /** Lazy-allocated {@link ActionMap}. */
    default ActionMap getActionMap() {
        return InputMapStore.of(_self()).getActionMap();
    }

    /**
     * Replaces the stored InputMap for {@code condition}: tears down
     * every Vaadin shortcut we'd installed for this
     * condition first, since the replacement map may not carry the
     * previous bindings forward and stale shortcuts would fire on keys
     * the InputMap no longer binds.
     *
     * @throws IllegalArgumentException on an unknown condition.
     */
    default void setInputMap(int condition, InputMap map) {
        InputMapStore store = InputMapStore.of(_self());
        switch (condition) {
            case WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW -> {
                /* validated, fall through to install */
            }
            default -> throw new IllegalArgumentException(
                    "condition must be one of WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW");
        }
        uninstallShortcutsForCondition(condition);
        switch (condition) {
            case WHEN_FOCUSED -> store.setWhenFocused(map);
            case WHEN_ANCESTOR_OF_FOCUSED_COMPONENT -> store.setWhenAncestor(map);
            case WHEN_IN_FOCUSED_WINDOW -> store.setWhenInWindow(map);
            default -> { /* unreachable */ }
        }
    }

    /** Replaces the stored {@link ActionMap}. */
    default void setActionMap(ActionMap map) {
        InputMapStore.of(_self()).setActionMap(map);
    }

    // --- InputVerifier (Vaadin BlurNotifier wire, R_vaadin_first lossy on source) -

    /**
     * Install {@code v} as the blur-time verifier. When the surrogate's
     * peer is a {@link BlurNotifier} (most input components are), a blur
     * subscription is installed that calls {@code v.shouldYieldFocus};
     * a {@code false} return restores focus via the peer's
     * {@link Focusable#focus()} when the peer also implements that. The
     * call routes through {@link SHelper#callSwing} (R_callswing_envelope) so the
     * blocking-dialog hook lands in one place.
     *
     * <p>Replacing or clearing the verifier tears down the previous Vaadin
     * registration through {@link InputVerifierStore} so the user-listener
     * fan-out stays at one verify call per blur regardless of swap count.
     * Non-{@link BlurNotifier} peers store the verifier value (so
     * {@link #getInputVerifier} round-trips) but no blur subscription
     * installs — there's no browser-side event to react to. PCE fires on
     * the {@code "inputVerifier"} property per Swing's contract.
     *
     * <p>R_vaadin_first lossy direct binding (SD_vaadin_first_binding): JDK's
     * {@code InputVerifier.shouldYieldFocus(JComponent)} expects a
     * {@code javax.swing.JComponent}, which the surrogate is not. The
     * call passes {@code null} as the source — user verifiers that read
     * field state via {@code input.getText()} need to tolerate null on the
     * surrogate layer (or fetch the field through a captured reference).
     * The emulator layer ({@code vaadinx.swing.JComponent.setInputVerifier})
     * passes the emulator JComponent and avoids this lossiness. Migration
     * code that ports straight to the surrogate stage and relies on the
     * source argument needs adjustment; the typical "is this field's text
     * valid?" pattern works fine when the verifier closes over the field.
     */
    default void setInputVerifier(InputVerifier v) {
        InputVerifierStore store = InputVerifierStore.of(_self());
        InputVerifier old = store.getVerifier();
        if (old == v) return;
        Registration newReg = null;
        if (v != null && this instanceof BlurNotifier<?> bn) {
            newReg = bn.addBlurListener(e -> SHelper.callSwing(() -> {
                if (!v.shouldYieldFocus(null, null) && this instanceof Focusable<?> f) {
                    f.focus();
                }
            }));
        }
        store.set(v, newReg);
        firePropertyChange("inputVerifier", old, v);
    }

    /** Reads back the installed verifier. {@code null} when none is installed. */
    default InputVerifier getInputVerifier() {
        return InputVerifierStore.of(_self()).getVerifier();
    }

    // --- firePropertyChange (char overload to complete the family) ---

    /** Char overload — JDK boxes for listener delivery. Complements the boolean/int overloads on {@link ContainerMixin}. */
    default void firePropertyChange(String propertyName, char oldValue, char newValue) {
        firePropertyChange(propertyName, (Object) oldValue, (Object) newValue);
    }

    // --- Alignment / baseline / display predicates (constant defaults) -

    /** Swing default is {@code 0.5f} (CENTER_ALIGNMENT). */
    default float getAlignmentX() { return 0.5f; }
    /** See {@link #getAlignmentX()}. */
    default float getAlignmentY() { return 0.5f; }

    /** {@code -1} means "no baseline info" per Swing's contract. */
    default int getBaseline(int width, int height) { return -1; }

    /** No baseline = {@code OTHER}. */
    default java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
        return java.awt.Component.BaselineResizeBehavior.OTHER;
    }

    /** Swing default: {@code false}. */
    default boolean isValidateRoot() { return false; }
    /** Swing default: {@code true}. */
    default boolean isOptimizedDrawingEnabled() { return true; }
    /** Swing default: {@code true}. */
    default boolean isRequestFocusEnabled() { return true; }
    /** Swing default: {@code true}. */
    default boolean getVerifyInputWhenFocusTarget() { return true; }
    /** We don't paint (R_layouts_close_enough), so honest default is {@code false}. */
    default boolean isDoubleBuffered() { return false; }
    /** Swing default: {@code false}. */
    default boolean getAutoscrolls() { return false; }
    /** Swing default: {@code false}. */
    default boolean getInheritsPopupMenu() { return false; }
    /** Swing's {@code DebugGraphics.NONE_OPTION}. */
    default int getDebugGraphicsOptions() { return 0; }
    /** Paint-internal flag; never set under R_layouts_close_enough. */
    default boolean isPaintingTile() { return false; }
    /** Paint-internal flag; never set under R_layouts_close_enough. */
    default boolean isPaintingForPrint() { return false; }

    // --- Null-default getters (pair with warn-only-on-divergent set) -

    /** The installed component popup, or {@code null}. Pairs with {@link #setComponentPopupMenu}. */
    default SJPopupMenu getComponentPopupMenu() { return ComponentPopupStore.of(_self()).popup; }
    /** No transfer handler. Pairs with {@link #setTransferHandler}. */
    default TransferHandler getTransferHandler() { return null; }
    /** No deprecated next-focusable-component installed. */
    default Component getNextFocusableComponent() { return null; }
    /**
     * JDK's location-aware tooltip variant lets a component return different
     * tooltip text per hover point. We don't wire mouse positions server-side
     * (R_best_effort_behaviour), so return the static tooltip — code that doesn't override stays
     * consistent.
     */
    default String getToolTipText(MouseEvent event) { return getToolTipText(); }
    /** Popup location per hover; not modelled. */
    default Point getToolTipLocation(MouseEvent event) { return null; }
    /** See {@link #getToolTipLocation(MouseEvent)}. */
    default Point getPopupLocation(MouseEvent event) { return null; }
    /** Swing's {@code JToolTip} factory — we don't model JToolTip. */
    default JToolTip createToolTip() { return null; }

    // --- Bucket B: paint pipeline / L&F (onNoop — browser owns) ------

    /** Deliberate no-op; browser repaints on DOM mutation. */
    default void paintImmediately(int x, int y, int w, int h) { SHelper.onNoop(this, "paintImmediately"); }
    /** Deliberate no-op. */
    default void paintImmediately(Rectangle r) { SHelper.onNoop(this, "paintImmediately"); }
    /** Deliberate no-op; no offscreen Graphics. */
    default java.awt.Graphics getComponentGraphics(java.awt.Graphics g) { SHelper.onNoop(this, "getComponentGraphics"); return null; }
    /** Deliberate no-op — L&amp;F dispatch isn't modelled. */
    default void updateUI() { SHelper.onNoop(this, "updateUI"); }
    // JComponent.getUI() clashes with Vaadin Component.getUI() — different
    // return types (ComponentUI vs. Optional<UI>). Vaadin-first wins (same
    // stance the hierarchy methods take). Migrated code that called
    // getUI() gets Vaadin's Optional<UI> back; the L&F lookup is gone.
    /** Deliberate no-op — we have no {@code ComponentUI} to install. */
    default void setUI(javax.swing.plaf.ComponentUI ui) { SHelper.onNoop(this, "setUI"); }
    /** Deliberate no-op — L&amp;F key. */
    default String getUIClassID() { SHelper.onNoop(this, "getUIClassID"); return null; }
    /** Deprecated AWT 1.0 alias for setBounds; routes through it. */
    default void reshape(int x, int y, int w, int h) { setBounds(x, y, w, h); }

    // --- Bucket B: paint hints with no Vaadin counterpart -------------
    //
    // These are plain Bucket B onNoop rather than "warn-only-on-
    // divergent" Bucket-C: these setters have no Vaadin counterpart and
    // no upstream-blocked one we're waiting on (drag-autoscroll,
    // double-buffering, debug-graphics, paint-hint alignment); a
    // divergent-value WARN would signal a stub gap that doesn't exist.
    // Getters keep returning the JDK default unconditionally.
    //
    // setComponentPopupMenu graduated from conditional WARN to a real
    // setTarget-backed Swing-flavoured helper when the JPopupMenu surrogate
    // landed (SD_sjpopupmenu). See below.

    /** Bucket B {@link SHelper#onNoop} — drag-autoscroll has no Vaadin counterpart. */
    default void setAutoscrolls(boolean autoscrolls) {
        SHelper.onNoop(this, "setAutoscrolls");
    }
    /** Bucket B {@link SHelper#onNoop} — popup-menu inheritance not modelled. */
    default void setInheritsPopupMenu(boolean value) {
        SHelper.onNoop(this, "setInheritsPopupMenu");
    }
    /**
     * Install (or clear) this component's context popup (SD_sjpopupmenu). Swing-flavoured
     * helper over Vaadin's native {@link com.vaadin.flow.component.contextmenu.ContextMenu#setTarget}:
     * the popup is targeted at this component, so a right-click (or long-press)
     * opens it. Passing {@code null} clears any previously installed popup.
     * The stored reference (round-tripped by {@link #getComponentPopupMenu})
     * is backed by the live {@code setTarget} binding, not a shadow cache.
     */
    default void setComponentPopupMenu(SJPopupMenu popup) {
        ComponentPopupStore store = ComponentPopupStore.of(_self());
        SJPopupMenu old = store.popup;
        if (store.popup != null) store.popup.setTarget(null);
        store.popup = popup;
        if (popup != null) popup.setTarget(_self());
        // SD_property_fanout_audit filed componentPopupMenu under "popup internals, out of scope"; that
        // was a misread of this method, which really does install the menu on the
        // Vaadin target. An implemented property whose JDK setter fires owes the
        // event (SD_reverse_fanout_rows).
        firePropertyChange("componentPopupMenu", old, popup);
    }
    /** Bucket B {@link SHelper#onNoop} — focus-disable has no Vaadin counterpart. */
    default void setRequestFocusEnabled(boolean enabled) {
        SHelper.onNoop(this, "setRequestFocusEnabled");
    }
    /** Bucket B {@link SHelper#onNoop}. */
    default void setVerifyInputWhenFocusTarget(boolean value) {
        SHelper.onNoop(this, "setVerifyInputWhenFocusTarget");
    }
    /** Bucket B {@link SHelper#onNoop} — we don't paint; double-buffering has nothing to apply to. */
    default void setDoubleBuffered(boolean value) {
        SHelper.onNoop(this, "setDoubleBuffered");
    }
    /** Bucket B {@link SHelper#onNoop} — debug-paint pipeline has no Vaadin counterpart. */
    default void setDebugGraphicsOptions(int debugOptions) {
        SHelper.onNoop(this, "setDebugGraphicsOptions");
    }
    /** Bucket B {@link SHelper#onNoop} — getters always return 0.5f (CENTER). */
    default void setAlignmentX(float alignmentX) {
        SHelper.onNoop(this, "setAlignmentX");
    }
    /** See {@link #setAlignmentX(float)}. */
    default void setAlignmentY(float alignmentY) {
        SHelper.onNoop(this, "setAlignmentY");
    }

    // --- Keyboard-action dispatch ------------
    //
    // Wired through Vaadin Shortcuts (mirrors :emulators — SD_shelper_statics
    // forbids cross-module reuse so the implementation lives twice). The
    // ActionStandin wraps the user listener as a JDK Action so a single
    // object plays InputMap value, ActionMap key, and ActionMap value;
    // shortcut registrations are tracked in InputMapStore so unregister
    // and setInputMap-replacement can tear them down precisely.

    default void registerKeyboardAction(ActionListener action, KeyStroke stroke, int condition) {
        registerKeyboardAction(action, null, stroke, condition);
    }

    /**
     * Wraps {@code action} in an {@link ActionStandin}, stores it in the
     * (condition) InputMap and the ActionMap, and installs a Vaadin
     * {@link com.vaadin.flow.component.Shortcuts} listener that drives
     * the action through {@link SHelper#callSwing}. Unmappable strokes
     * (keyCodes outside {@link KeyConvert#vkToVaadinKey}'s table) skip the
     * browser-side install but keep the InputMap / ActionMap entries —
     * server-side {@code getActionForKeyStroke} still finds them.
     */
    default void registerKeyboardAction(ActionListener action, String command, KeyStroke stroke, int condition) {
        InputMap im = getInputMap(condition);
        ActionMap am = getActionMap();
        ActionStandin standin = new ActionStandin(action, command);
        im.put(stroke, standin);
        am.put(standin, standin);
        installVaadinShortcut(condition, stroke, standin);
    }

    /**
     * Remove the binding for {@code stroke} from every condition's
     * InputMap, drop its ActionMap entry, and uninstall the matching
     * Vaadin shortcut. Mirrors JDK's "remove across all conditions" shape.
     */
    default void unregisterKeyboardAction(KeyStroke stroke) {
        InputMapStore store = InputMapStore.of(_self());
        ActionMap am = store.rawActionMap();
        for (int condition : new int[]{WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW}) {
            InputMap im = rawInputMapFor(condition);
            if (im == null) continue;
            Object key = im.get(stroke);
            if (key == null) continue;
            im.remove(stroke);
            if (am != null) am.remove(key);
            uninstallVaadinShortcut(condition, stroke);
        }
    }

    /**
     * Clear every condition's InputMap, the ActionMap, and tear down
     * every Vaadin shortcut installed via this surrogate.
     */
    default void resetKeyboardActions() {
        InputMapStore store = InputMapStore.of(_self());
        for (int condition : new int[]{WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW}) {
            InputMap im = rawInputMapFor(condition);
            if (im != null) im.clear();
        }
        ActionMap am = store.rawActionMap();
        if (am != null) am.clear();
        java.util.Map<InputMapStore.ShortcutKey, com.vaadin.flow.component.ShortcutRegistration> shortcuts = store.rawShortcuts();
        if (shortcuts != null) {
            for (com.vaadin.flow.component.ShortcutRegistration reg : shortcuts.values()) {
                reg.remove();
            }
            shortcuts.clear();
        }
    }

    /** Union of strokes registered across all three conditions. */
    default KeyStroke[] getRegisteredKeyStrokes() {
        java.util.LinkedHashSet<KeyStroke> strokes = new java.util.LinkedHashSet<>();
        for (int condition : new int[]{WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW}) {
            InputMap im = rawInputMapFor(condition);
            if (im == null) continue;
            KeyStroke[] keys = im.allKeys();
            if (keys != null) java.util.Collections.addAll(strokes, keys);
        }
        return strokes.toArray(new KeyStroke[0]);
    }

    /**
     * Walk in JDK's documented order (focused / ancestor / window) and
     * return the first condition whose InputMap binds {@code stroke};
     * {@link #UNDEFINED_CONDITION} when not bound anywhere.
     */
    default int getConditionForKeyStroke(KeyStroke stroke) {
        if (rawInputMapFor(WHEN_FOCUSED) != null
                && rawInputMapFor(WHEN_FOCUSED).get(stroke) != null) return WHEN_FOCUSED;
        if (rawInputMapFor(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) != null
                && rawInputMapFor(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke) != null) return WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
        if (rawInputMapFor(WHEN_IN_FOCUSED_WINDOW) != null
                && rawInputMapFor(WHEN_IN_FOCUSED_WINDOW).get(stroke) != null) return WHEN_IN_FOCUSED_WINDOW;
        return UNDEFINED_CONDITION;
    }

    /**
     * Resolve InputMap → action key → ActionMap → ActionListener. Returns
     * {@code null} when the stroke isn't bound anywhere or the ActionMap
     * has no entry for the key — matches JDK's "either reason → null".
     */
    default ActionListener getActionForKeyStroke(KeyStroke stroke) {
        InputMapStore store = InputMapStore.of(_self());
        ActionMap am = store.rawActionMap();
        if (am == null) return null;
        for (int condition : new int[]{WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW}) {
            InputMap im = rawInputMapFor(condition);
            if (im == null) continue;
            Object key = im.get(stroke);
            if (key != null) return am.get(key);
        }
        return null;
    }

    /** Raw InputMap accessor that doesn't materialise empty maps — used by query paths. */
    private InputMap rawInputMapFor(int condition) {
        InputMapStore store = InputMapStore.of(_self());
        return switch (condition) {
            case WHEN_FOCUSED -> store.rawWhenFocused();
            case WHEN_ANCESTOR_OF_FOCUSED_COMPONENT -> store.rawWhenAncestor();
            case WHEN_IN_FOCUSED_WINDOW -> store.rawWhenInWindow();
            default -> null;
        };
    }

    /**
     * Install a Vaadin {@link com.vaadin.flow.component.Shortcuts}
     * listener for {@code (condition, stroke)} that fires the standin's
     * action via {@link SHelper#callSwing}. {@code WHEN_FOCUSED} and
     * {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} scope via
     * {@code listenOn(self)}; {@code WHEN_IN_FOCUSED_WINDOW} stays UI-
     * scoped (default). Unmappable strokes log onUnimplemented and skip
     * the install — the InputMap / ActionMap entries already landed.
     */
    private void installVaadinShortcut(int condition, KeyStroke stroke, ActionStandin standin) {
        KeyConvert.VaadinKeyBinding binding = KeyConvert.toVaadinKeyBinding(stroke);
        if (binding == null) {
            SHelper.onUnimplemented(this, "registerKeyboardAction/unmappable-keystroke", stroke);
            return;
        }
        final Component source = _self();
        com.vaadin.flow.server.Command command = () -> SHelper.callSwing(() -> {
            ActionEvent e = new ActionEvent(
                    source,
                    ActionEvent.ACTION_PERFORMED,
                    standin.command());
            standin.actionPerformed(e);
        });
        com.vaadin.flow.component.ShortcutRegistration reg =
                com.vaadin.flow.component.Shortcuts.addShortcutListener(
                        source, command, binding.key(), binding.modifiers());
        if (condition != WHEN_IN_FOCUSED_WINDOW) {
            reg.listenOn(source);
        }
        InputMapStore.ShortcutKey key = new InputMapStore.ShortcutKey(condition, stroke);
        com.vaadin.flow.component.ShortcutRegistration prev =
                InputMapStore.of(source).shortcuts().put(key, reg);
        if (prev != null) prev.remove();
    }

    /** Remove the Vaadin shortcut for a specific {@code (condition, stroke)}. */
    private void uninstallVaadinShortcut(int condition, KeyStroke stroke) {
        java.util.Map<InputMapStore.ShortcutKey, com.vaadin.flow.component.ShortcutRegistration> shortcuts =
                InputMapStore.of(_self()).rawShortcuts();
        if (shortcuts == null) return;
        com.vaadin.flow.component.ShortcutRegistration reg =
                shortcuts.remove(new InputMapStore.ShortcutKey(condition, stroke));
        if (reg != null) reg.remove();
    }

    /**
     * Remove every Vaadin shortcut we installed for {@code condition}.
     * Used by {@link #setInputMap(int, InputMap)} when the replacement
     * may not carry forward the previous bindings.
     */
    private void uninstallShortcutsForCondition(int condition) {
        java.util.Map<InputMapStore.ShortcutKey, com.vaadin.flow.component.ShortcutRegistration> shortcuts =
                InputMapStore.of(_self()).rawShortcuts();
        if (shortcuts == null) return;
        java.util.Iterator<java.util.Map.Entry<InputMapStore.ShortcutKey, com.vaadin.flow.component.ShortcutRegistration>> it =
                shortcuts.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<InputMapStore.ShortcutKey, com.vaadin.flow.component.ShortcutRegistration> e = it.next();
            if (e.getKey().condition() == condition) {
                e.getValue().remove();
                it.remove();
            }
        }
    }

    /**
     * Adapter wrapping a plain {@link ActionListener} as a JDK
     * {@link javax.swing.Action}. Single object plays InputMap value,
     * ActionMap key, and ActionMap value — matches JDK's
     * {@code ActionStandin} idiom + the {@code :emulators.JComponent}
     * private inner class of the same name (SD_shelper_statics forbids cross-module
     * reuse, so the structure lives twice).
     */
    final class ActionStandin extends javax.swing.AbstractAction {
        private final ActionListener listener;
        private final String command;
        ActionStandin(ActionListener listener, String command) {
            this.listener = listener;
            this.command = command;
        }
        String command() { return command; }
        @Override
        public void actionPerformed(ActionEvent e) {
            listener.actionPerformed(e);
        }
    }

    // --- Bucket C: features without Vaadin analog (WARN) -------------

    /** WARN — drag-and-drop not modelled. */
    default void setTransferHandler(TransferHandler handler) {
        SHelper.onUnimplemented(this, "setTransferHandler", handler);
    }
    /** WARN — deprecated focus-traversal API. */
    default void setNextFocusableComponent(Component comp) {
        SHelper.onUnimplemented(this, "setNextFocusableComponent", comp);
    }
    /** WARN — deprecated focus-traversal API. */
    default boolean requestDefaultFocus() {
        SHelper.onUnimplemented(this, "requestDefaultFocus");
        return false;
    }
    /** WARN — AWT focus traversal keys not modelled (browser Tab handles equivalent). */
    default void setFocusTraversalKeys(int id, Set<? extends AWTKeyStroke> keystrokes) {
        SHelper.onUnimplemented(this, "setFocusTraversalKeys", id, keystrokes);
    }

    /**
     * Wired via Vaadin attach / detach listeners.
     * {@code ANCESTOR_ADDED} fires on attach, {@code ANCESTOR_REMOVED}
     * on detach. {@code ANCESTOR_MOVED} (JDK fires on layout-position
     * changes of any ancestor) is not modelled — Vaadin doesn't surface
     * server-side layout-position events; documented as accepted
     * incompleteness.
     *
     * <p>Listener type retyped to {@link com.vaadin.swingbridge.surrogates.awt.event.SAncestorListener}
     * because JDK's {@code javax.swing.event.AncestorEvent}'s ancestor /
     * ancestorParent are typed as {@link java.awt.Container}, which we
     * can't construct against a Vaadin component (SD_event_port_stance / D_event_port_policy).
     */
    default void addAncestorListener(com.vaadin.swingbridge.surrogates.awt.event.SAncestorListener listener) {
        if (listener == null) return;
        final Component self = _self();
        com.vaadin.flow.shared.Registration r1 = self.addAttachListener(e ->
                SHelper.callSwing(() -> listener.ancestorAdded(
                        new com.vaadin.swingbridge.surrogates.awt.event.SAncestorEvent(
                                self, com.vaadin.swingbridge.surrogates.awt.event.SAncestorEvent.ANCESTOR_ADDED,
                                self, self.getParent().orElse(null)))));
        com.vaadin.flow.shared.Registration r2 = self.addDetachListener(e ->
                SHelper.callSwing(() -> listener.ancestorRemoved(
                        new com.vaadin.swingbridge.surrogates.awt.event.SAncestorEvent(
                                self, com.vaadin.swingbridge.surrogates.awt.event.SAncestorEvent.ANCESTOR_REMOVED,
                                self, self.getParent().orElse(null)))));
        com.vaadin.swingbridge.surrogates.internal.Registrations.of(self).add(listener, () -> { r1.remove(); r2.remove(); });
    }

    /** Symmetric — removes both peer subscriptions wired by {@link #addAncestorListener}. */
    default void removeAncestorListener(com.vaadin.swingbridge.surrogates.awt.event.SAncestorListener listener) {
        com.vaadin.swingbridge.surrogates.internal.Registrations.of(_self()).remove(listener);
    }

    /** Returns the currently registered listeners in registration order. */
    default com.vaadin.swingbridge.surrogates.awt.event.SAncestorListener[] getAncestorListeners() {
        return com.vaadin.swingbridge.surrogates.internal.Registrations.of(_self())
                .getListeners(com.vaadin.swingbridge.surrogates.awt.event.SAncestorListener.class);
    }

    // --- VetoableChangeListener (R_vaadin_first drop-and-WARN — no Vaadin producer)
    //
    // No Vaadin counterpart for "before-change" events, and nothing in the
    // surrogate's own code path fires vetoable change (unlike PCE, which
    // SD_auto_pce auto-fires on mapped setters with Vaadin-driven producers
    // behind it). Storing vetoable listeners would be pure user-fire to
    // user-listener pass-through — R_vaadin_first's listener-analog of the shadow-
    // cache anti-pattern. Stays drop-and-WARN at the surrogate stage
    // until a migration target shows the surface is materially
    // used. The emulator-layer JComponent (vaadinx.swing.JComponent)
    // does wire it via VetoableChangeSupport — emulator carries full
    // JDK-API compat per R_match_swing_errors/R_best_effort_behaviour, which is a different value criterion.

    /** R_vaadin_first drop-and-WARN — see section comment. */
    default void addVetoableChangeListener(VetoableChangeListener listener) {
        SHelper.onUnimplemented(this, "addVetoableChangeListener", listener);
    }
    /** R_vaadin_first drop-and-WARN — see {@link #addVetoableChangeListener}. */
    default void removeVetoableChangeListener(VetoableChangeListener listener) {
        SHelper.onUnimplemented(this, "removeVetoableChangeListener", listener);
    }
    /** R_vaadin_first drop-and-WARN — returns empty array. */
    default VetoableChangeListener[] getVetoableChangeListeners() {
        SHelper.onUnimplemented(this, "getVetoableChangeListeners");
        return new VetoableChangeListener[0];
    }
    /** R_vaadin_first drop-and-WARN — does not throw. */
    default void fireVetoableChange(String propertyName, Object oldValue, Object newValue)
            throws PropertyVetoException {
        SHelper.onUnimplemented(this, "fireVetoableChange", propertyName, oldValue, newValue);
    }

    /**
     * Walks the Vaadin parent chain and returns the first
     * {@link com.vaadin.flow.component.dialog.Dialog} ancestor, or
     * {@code null} if none — which includes the detached case and the
     * route-level case (no enclosing Dialog). Dialog is Vaadin's closest
     * analog to Swing's "top-level window": it owns a modal/popup frame
     * the rest of the UI can sit inside. {@code UI} is deliberately
     * <em>not</em> treated as top-level — every attached Vaadin component
     * has a UI ancestor, so using UI would diverge from Swing's "null
     * when no enclosing JFrame" contract.
     *
     * <p>Handles the {@code :emulators} handoff transparently: an
     * emulator {@code JFrame} has a {@code Dialog} peer, so a surrogate
     * living inside that frame walks up to the Dialog and returns it.
     * In a pure-surrogate tree (surrogate used as a plain Vaadin
     * component under a route view, no Dialog wrapping), returns
     * {@code null} — same as Swing would for a detached JPanel.
     */
    default Component getTopLevelAncestor() {
        for (Component p = _self().getParent().orElse(null);
             p != null;
             p = p.getParent().orElse(null)) {
            if (p instanceof com.vaadin.flow.component.dialog.Dialog) {
                return p;
            }
        }
        return null;
    }
    /**
     * Walks the Vaadin parent chain and returns the first {@link SJRootPane}
     * ancestor, or {@code null} if none. Mirrors the JDK
     * {@code JComponent.getRootPane()} contract (climb-until-root-pane)
     * over the surrogate-layer types.
     *
     * <p>Surrogate-layer divergence from JDK signature: returns
     * {@link SJRootPane} (Vaadin-rooted) rather than {@code javax.swing.JRootPane}.
     * The same R_vaadin_first + SD_sjframe stance that drove {@link #getTopLevelAncestor}
     * to return Vaadin {@link Component} applies here — SJRootPane is
     * deliberately not a JDK JRootPane subclass (it extends
     * {@code com.vaadin.flow.component.html.Div}), so a JDK-typed
     * accessor would have to either fabricate a wrapper or stay
     * permanently null. The Vaadin-typed accessor lets {@link com.vaadin.swingbridge.surrogates.SJFrame}
     * provide a covariant override that returns its lazy-constructed
     * pane, and lets standalone surrogates report a real ancestor when
     * one exists. Migrators porting Swing code that read the JDK type
     * adjust the variable type at the surrogate stage; the
     * {@code :emulators.JComponent.getRootPane()} path keeps the
     * JDK-typed signature for stage-2 import-swap compatibility.
     */
    default SJRootPane getRootPane() {
        for (Component p = _self().getParent().orElse(null);
             p != null;
             p = p.getParent().orElse(null)) {
            if (p instanceof SJRootPane rp) return rp;
        }
        return null;
    }

    /**
     * Bucket B {@link SHelper#onNoop} — ghost method. The JDK process* hooks
     * are user-overridable dispatch entry points called by AWT's own event
     * delivery; our event routing uses Vaadin-sourced events through the
     * {@code addKeyListener}/{@code addMouseListener} surface and never
     * reaches these overloads. These are {@code onNoop} rather than
     * {@code onUnimplemented}: there's no upstream-blocked counterpart we're
     * waiting on — these will not graduate.
     */
    default void processKeyEvent(KeyEvent e) {
        SHelper.onNoop(this, "processKeyEvent");
    }
    /** Bucket B {@link SHelper#onNoop} — see {@link #processKeyEvent(KeyEvent)}. */
    default void processComponentKeyEvent(KeyEvent e) {
        SHelper.onNoop(this, "processComponentKeyEvent");
    }
    /** Bucket B {@link SHelper#onNoop} — see {@link #processKeyEvent(KeyEvent)}. */
    default boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        SHelper.onNoop(this, "processKeyBinding");
        return false;
    }
    /** Bucket B {@link SHelper#onNoop} — see {@link #processKeyEvent(KeyEvent)}. */
    default void processMouseEvent(MouseEvent e) {
        SHelper.onNoop(this, "processMouseEvent");
    }
    /** Bucket B {@link SHelper#onNoop} — see {@link #processKeyEvent(KeyEvent)}. */
    default void processMouseMotionEvent(MouseEvent e) {
        SHelper.onNoop(this, "processMouseMotionEvent");
    }
}
