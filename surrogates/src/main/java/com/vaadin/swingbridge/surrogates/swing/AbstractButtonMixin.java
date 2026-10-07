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

import com.vaadin.flow.component.ClickNotifier;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.util.KeyConvert;
import com.vaadin.swingbridge.surrogates.internal.ButtonStateStore;
import com.vaadin.swingbridge.surrogates.util.Icons;

import javax.swing.Action;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Mixin for {@link javax.swing.AbstractButton}. Layers the AbstractButton
 * API surface on top of {@link JComponentMixin}. Shared across future
 * {@code SJButton} / {@code SJToggleButton} / {@code SJCheckBox} /
 * {@code SJRadioButton} surrogates — the push-vs-toggle split lives in
 * the concrete's peer-click wiring (push buttons pulse {@code setPressed},
 * toggle buttons flip {@code setSelected}).
 *
 * <h2>R_vaadin_first stance: UI functionality yes, API round-trip only where Vaadin has it</h2>
 *
 * Per [R_vaadin_first], surrogates reproduce the UI functionality of their Swing
 * originals — click → ActionEvent, ButtonModel armed/pressed pulse,
 * ChangeListener / ItemListener fan-out, Action dispatch, mnemonic
 * accelerators — but the API is only implemented to the degree Vaadin
 * has a semantic counterpart. AbstractButton's still-uncovered visual
 * state (the six state-conditional icons, border-painted /
 * content-area-filled / focus-painted / rollover-enabled, margin,
 * displayedMnemonicIndex, multiClickThreshhold) has no Vaadin
 * counterpart today, so their setters call
 * {@link SHelper#onUnimplemented} + drop the value, and their getters
 * return the JDK default. See "Drop-and-WARN section" below.
 *
 * <p>The four alignment / text-position setters and {@code iconTextGap}
 * <em>do</em> have a Vaadin counterpart (host {@code display: inline-flex}
 * + {@code flex-direction} / {@code justify-content} / {@code align-items}
 * / {@code gap}) and bind directly: setters write CSS, getters lossy-parse
 * CSS back. Same setBorder / getBorder shape per SD_border_css_lossy — LEFT and LEADING
 * both round-trip as LEADING, RIGHT and TRAILING as TRAILING; vertical
 * text-position non-CENTER masks horizontal text-position.
 *
 * <p>Kept (UI-functional or semantically load-bearing):
 *
 * <ul>
 *   <li><b>{@code text}</b> — written to the peer's element {@code text}
 *       property; read back from the same. Minor loss per R_vaadin_first:
 *       {@code setText(null)} round-trips as {@code ""}.</li>
 *   <li><b>{@code actionCommand}</b> — drives {@link ActionEvent}'s
 *       payload delivered to user listeners (with the text fallback).</li>
 *   <li><b>{@code hideActionText}</b> — gates NAME propagation inside
 *       {@link #configurePropertiesFromAction}.</li>
 *   <li><b>{@code mnemonic}</b> — drives Vaadin {@link Shortcuts} install;
 *       stored because Shortcuts is write-only (no readback).</li>
 *   <li><b>{@link Action} state</b> — install + PCL propagation + click
 *       dispatch.</li>
 *   <li><b>{@link ButtonModel} state</b> — source of truth for
 *       armed/pressed/selected/enabled; drives three fire-families.</li>
 *   <li><b>Listener lists</b> — ActionListener / ChangeListener /
 *       ItemListener fan-out.</li>
 * </ul>
 *
 * <h2>Peer click synthesis</h2>
 *
 * Concrete push-button surrogates call {@link #installButtonBindings}
 * from their constructor. It seeds the default model, registers the
 * three model-listener fan-out wires, and — if the concrete's peer
 * implements {@link ClickNotifier} — wires a click listener that
 * synthesizes a {@code setArmed(true) → setPressed(true) → setPressed(false) → setArmed(false)}
 * pulse through {@link SHelper#callSwing} (R_callswing_envelope). The model's internal
 * {@code fireActionPerformed} fires only on the pressed→unpressed
 * transition while armed, matching JDK's sequence.
 *
 * <h2>Mnemonic</h2>
 *
 * {@code setMnemonic} installs a Vaadin {@link Shortcuts} binding for
 * {@code Alt+<key>} on the concrete's peer that invokes {@link #doClick}.
 * Replacing / clearing the mnemonic tears down the previous registration
 * first. The Vaadin-side install is UI-scoped by default; close to
 * Swing's {@code WHEN_IN_FOCUSED_WINDOW} for mnemonics on a single-UI
 * app.
 *
 * <h2>{@code getIcon()} / {@code getUI()} dropped — Vaadin Button signature clash</h2>
 *
 * The mixin doesn't declare JDK-shaped {@code getIcon()} or {@code getUI()}
 * getters. Vaadin {@code Button.getIcon()} returns
 * {@code com.vaadin.flow.component.Component} and {@code Component.getUI()}
 * returns {@code Optional<UI>} — neither is covariantly related to JDK's
 * {@code javax.swing.Icon} / {@code javax.swing.plaf.ButtonUI}, so a
 * surrogate {@code extends Button} cannot coexist with JDK-shaped
 * versions (JVM forbids two methods with the same erased signature and
 * different return types). {@code setIcon(javax.swing.Icon)} survives
 * as an overload against Vaadin's {@code setIcon(Component)} (parameter
 * types differ) and binds Vaadin-first per SD_vaadin_first_binding: {@link javax.swing.ImageIcon}
 * reaches the peer as a PNG-encoded Vaadin {@code Image}; other {@code Icon}
 * impls still
 * WARN-and-drop pending Path 2 / Path 3.
 */
public interface AbstractButtonMixin extends JComponentMixin, java.awt.ItemSelectable {

    /** Cast {@code this} to Vaadin {@link Component} for holder lookups. */
    private Component _self() {
        return (Component) this;
    }

    // --- Install hook (called from concrete's ctor) -------------------

    /**
     * Seeds the default {@link ButtonModel} + three model→button listener
     * fan-outs. If the peer is a {@link ClickNotifier}, wires a click
     * listener that synthesizes Swing armed/pressed pulses on user
     * clicks. Idempotent: a second call is a no-op once {@code model}
     * is installed.
     */
    default void installButtonBindings() {
        ButtonStateStore s = ButtonStateStore.of(_self());
        if (s.model != null) return;
        setModel(new DefaultButtonModel());
        if (_self() instanceof ClickNotifier<?> cn) {
            cn.addClickListener(e -> SHelper.callSwing(this::synthesizePeerClick));
        }
        // Seed JDK AbstractButton's layout defaults (CENTER / CENTER /
        // TRAILING / CENTER + gap 4px) into the host's flex CSS so
        // getters return the right canonical without setters being
        // called first. Vaadin Button is already display:inline-flex
        // per Lumo, so we don't write `display`.
        com.vaadin.flow.dom.Style style = _self().getElement().getStyle();
        style.set("flex-direction", "row");          // h-text-pos TRAILING + v-text-pos CENTER
        style.set("justify-content", "center");      // h-align CENTER
        style.set("align-items", "center");          // v-align CENTER
        style.set("gap", "4px");                     // iconTextGap default
        // A JDK button paints its caption on one line; the browser would wrap it.
        style.set("white-space", "nowrap");
    }

    /**
     * Synthesize a JDK-shaped click pulse on the installed {@link ButtonModel}.
     * Mirrors {@link #doClick}'s effect but reaches the model directly so
     * peer-originated clicks (user tap) flow through identical state
     * transitions as a programmatic {@code doClick}.
     */
    default void synthesizePeerClick() {
        ButtonModel m = getModel();
        if (m == null) return;
        m.setArmed(true);
        m.setPressed(true);
        m.setPressed(false);
        m.setArmed(false);
    }

    // --- ButtonModel --------------------------------------------------

    default ButtonModel getModel() {
        return ButtonStateStore.of(_self()).model;
    }

    /**
     * Install a new {@link ButtonModel}. Detaches the mixin's bridge
     * listeners from the old model via the stored
     * {@link ButtonStateStore#modelInstall} handle, attaches fresh
     * lambdas to the new model and packs their teardown into a single
     * {@link com.vaadin.flow.shared.Registration}, fires the
     * {@code "model"} PCE. A null model is legal per JDK; the detach
     * still happens so the old model doesn't retain references to our
     * wires.
     */
    default void setModel(ButtonModel newModel) {
        ButtonStateStore s = ButtonStateStore.of(_self());
        ButtonModel old = s.model;
        if (old == newModel) return;
        if (s.modelInstall != null) {
            s.modelInstall.remove();
            s.modelInstall = null;
        }
        s.model = newModel;
        if (newModel != null) {
            javax.swing.event.ChangeListener cl = e -> fireStateChanged();
            ActionListener al = e -> fireActionPerformed(new ActionEvent(
                    _self(), e.getID(), getActionCommand(), e.getWhen(), e.getModifiers()));
            ItemListener il = e -> fireItemStateChanged(new ItemEvent(
                    (java.awt.ItemSelectable) _self(), e.getID(), e.getItem(), e.getStateChange()));
            newModel.addChangeListener(cl);
            newModel.addActionListener(al);
            newModel.addItemListener(il);
            s.modelInstall = () -> {
                newModel.removeChangeListener(cl);
                newModel.removeActionListener(al);
                newModel.removeItemListener(il);
            };
            // The button takes the model's enabled state, as the JDK's setModel does: a model
            // shared with the emulator owns it, and a write must not change it.
            setEnabled(newModel.isEnabled());
        }
        firePropertyChange("model", old, newModel);
    }

    // --- Selected state (delegates to model) --------------------------

    default boolean isSelected() {
        ButtonModel m = getModel();
        return m != null && m.isSelected();
    }

    /**
     * Delegates to {@link ButtonModel#setSelected}. The model fires an
     * {@link ItemEvent} on actual transition, which routes through the
     * mixin's model-listener fan-out to button-level ItemListeners.
     */
    default void setSelected(boolean selected) {
        ButtonModel m = getModel();
        if (m != null) m.setSelected(selected);
    }

    default Object[] getSelectedObjects() {
        if (!isSelected()) return null;
        String t = getText();
        return t == null ? null : new Object[] { t };
    }

    // --- doClick ------------------------------------------------------

    /** Shorthand for {@code doClick(68)} per JDK. */
    default void doClick() {
        doClick(68);
    }

    /**
     * Programmatic click. Pulses the model's armed/pressed transitions
     * (same path as a peer click); {@code pressTime} is nominal — we
     * don't animate. Ignores {@code isEnabled} per JDK contract.
     */
    default void doClick(int pressTime) {
        synthesizePeerClick();
    }

    // --- Text (abstract — concrete surrogates implement via their HasText)

    /**
     * Abstract — each concrete AbstractButton-lineage surrogate provides
     * its own {@code getText} via the parent Vaadin component's
     * {@link com.vaadin.flow.component.HasText} contract. SJButton's
     * inherited {@code Button.getText()} (which reads {@code textNode})
     * satisfies this requirement automatically. Mixin-internal callers
     * (e.g. {@link #getActionCommand}'s text fallback,
     * {@link #setDisplayedMnemonicIndex}'s length validation) reach the
     * concrete impl via virtual dispatch.
     *
     * <p>Pulled out of the mixin's default-implementation surface so the
     * mixin doesn't need a reentry-flag dance to call back into the
     * parent's HasText methods — the rendered text path is owned by the
     * concrete surrogate, end of story.
     */
    String getText();

    /**
     * Abstract — each concrete surrogate implements via super (its
     * Vaadin parent's HasText.setText, e.g. Button.setText →
     * textSupport) plus the {@code "text"} PCE fire. Pulled out of the
     * mixin's default surface for the same reason as {@link #getText}.
     */
    void setText(String text);

    /** Deprecated AWT 1.0 alias for {@link #getText}. */
    default String getLabel() {
        return getText();
    }

    /** Deprecated AWT 1.0 alias for {@link #setText}. */
    default void setLabel(String label) {
        setText(label);
    }

    // --- actionCommand (UI: ActionEvent payload; no Vaadin analog) ---

    default String getActionCommand() {
        ButtonStateStore s = ButtonStateStore.of(_self());
        return s.actionCommand != null ? s.actionCommand : getText();
    }

    /**
     * Fires no {@code "actionCommand"} property change — the JDK's
     * {@code AbstractButton.setActionCommand} is one line,
     * {@code getModel().setActionCommand(actionCommand)}, and nothing on the
     * path fires (SD_property_fanout_audit).
     */
    default void setActionCommand(String command) {
        ButtonStateStore.of(_self()).actionCommand = command;
    }

    // --- hideActionText (affects Action NAME routing; no Vaadin analog) --

    default boolean getHideActionText() {
        return ButtonStateStore.of(_self()).hideActionText;
    }

    default void setHideActionText(boolean hideActionText) {
        ButtonStateStore s = ButtonStateStore.of(_self());
        boolean old = s.hideActionText;
        s.hideActionText = hideActionText;
        firePropertyChange("hideActionText", old, hideActionText);
    }

    // --- Plain setIcon (Vaadin-first per SD_vaadin_first_binding; Path 1 closure of the SD_sjbutton
    //     R_vaadin_first-trim) + drop-and-WARN: 6 state-conditional icons, visual
    //     flags, margin, multiClickThreshhold, displayedMnemonicIndex
    //
    //     Per R_vaadin_first: no Vaadin counterpart for the drop-and-WARN bucket, not
    //     UI-functional in isolation, so setters log onUnimplemented on
    //     non-default values and drop; getters return the JDK default.
    //     Setting the JDK default is a silent no-op (no WARN) so
    //     setAction's automatic "clear to default" branches don't trip
    //     WARNs on every attach. Validation that JDK throws on
    //     (threshhold < 0, bad mnemonic index) is preserved per R_match_swing_errors.
    //     Alignment / text-position / iconTextGap moved out of this
    //     bucket — they bind via inline-flex CSS, see the layout block
    //     below.

    private void warnDrop(String method, Object arg) {
        SHelper.onUnimplemented(_self(), method, arg);
    }

    /**
     * Plain icon — bound Vaadin-first per SD_vaadin_first_binding (icon-rendering Path 1).
     *
     * <p>Overload-safe against Vaadin {@code Button.setIcon(Component)}
     * (parameter types differ). The translation runs through
     * {@link Icons#toVaadinIconComponent}: {@link javax.swing.ImageIcon}
     * encodes as PNG and lands as a {@code <img>} child of the peer; any
     * other {@code Icon} impl WARNs and clears the slot (Path 2 / Path 3
     * deferred).
     *
     * <p>Fires {@code "icon"} PCE with Vaadin {@link Component}-typed
     * values per R_vaadin_first (lossy round-trip — the JDK {@code Icon} ref isn't
     * stored, since Vaadin {@code Button} owns the slot). Call sites whose
     * peer doesn't expose {@code setIcon(Component)} (anything that isn't
     * a Vaadin {@code Button}) WARN-and-drop, same shape as the pre-Path-1
     * fallback.
     */
    default void setIcon(Icon icon) {
        Component newIcon = Icons.toVaadinIconComponent(_self(), icon);
        if (_self() instanceof Button btn) {
            Component oldIcon = btn.getIcon();
            btn.setIcon(newIcon);
            firePropertyChange("icon", oldIcon, newIcon);
        } else if (icon != null) {
            warnDrop("setIcon", icon);
        }
    }

    default Icon getPressedIcon() { return null; }
    default void setPressedIcon(Icon icon) {
        if (icon != null) warnDrop("setPressedIcon", icon);
    }

    default Icon getSelectedIcon() { return null; }
    default void setSelectedIcon(Icon icon) {
        if (icon != null) warnDrop("setSelectedIcon", icon);
    }

    default Icon getDisabledIcon() { return null; }
    default void setDisabledIcon(Icon icon) {
        if (icon != null) warnDrop("setDisabledIcon", icon);
    }

    default Icon getDisabledSelectedIcon() { return null; }
    default void setDisabledSelectedIcon(Icon icon) {
        if (icon != null) warnDrop("setDisabledSelectedIcon", icon);
    }

    default Icon getRolloverIcon() { return null; }
    default void setRolloverIcon(Icon icon) {
        if (icon != null) warnDrop("setRolloverIcon", icon);
    }

    default Icon getRolloverSelectedIcon() { return null; }
    default void setRolloverSelectedIcon(Icon icon) {
        if (icon != null) warnDrop("setRolloverSelectedIcon", icon);
    }

    /** JDK default is true; no Vaadin counterpart. */
    default boolean isBorderPainted() { return true; }
    default void setBorderPainted(boolean v) {
        if (!v) warnDrop("setBorderPainted", v);
    }

    /** JDK default is true; no Vaadin counterpart. */
    default boolean isContentAreaFilled() { return true; }
    default void setContentAreaFilled(boolean v) {
        if (!v) warnDrop("setContentAreaFilled", v);
    }

    /** JDK default is true; no Vaadin counterpart. */
    default boolean isFocusPainted() { return true; }
    default void setFocusPainted(boolean v) {
        if (!v) warnDrop("setFocusPainted", v);
    }

    /** JDK default is false; no Vaadin counterpart. */
    default boolean isRolloverEnabled() { return false; }
    default void setRolloverEnabled(boolean v) {
        if (v) warnDrop("setRolloverEnabled", v);
    }

    /**
     * Reads CSS {@code gap} back as an int. Lossy CSS round-trip per R_vaadin_first —
     * a {@code gap} CSS value with a non-px unit (we only ever write
     * {@code "Npx"}) would parse to the JDK default 4.
     */
    default int getIconTextGap() {
        return parseGapPx(_self().getElement().getStyle().get("gap"));
    }
    default void setIconTextGap(int gap) {
        com.vaadin.flow.dom.Style style = _self().getElement().getStyle();
        String oldCss = style.get("gap");
        String newCss = gap + "px";
        if (newCss.equals(oldCss)) return;
        int oldGap = parseGapPx(oldCss);
        style.set("gap", newCss);
        firePropertyChange("iconTextGap", oldGap, gap);
    }

    /** JDK default is null; no Vaadin counterpart. */
    default Insets getMargin() { return null; }
    default void setMargin(Insets margin) {
        if (margin != null) warnDrop("setMargin", margin);
    }

    /** JDK default is 0. Validates per R_match_swing_errors; values &gt; 0 drop with WARN. */
    default long getMultiClickThreshhold() { return 0; }
    default void setMultiClickThreshhold(long threshhold) {
        if (threshhold < 0) throw new IllegalArgumentException("threshhold must be >= 0");
        if (threshhold != 0) warnDrop("setMultiClickThreshhold", threshhold);
    }

    // --- Layout (alignment + text-position; SJLabel-pattern lift) ----
    //
    // Defaults match JDK AbstractButton (CENTER / CENTER / TRAILING /
    // CENTER + gap 4); seeded into CSS by installButtonBindings. Each
    // setter validates (R_match_swing_errors IAE on bad axis) and writes its slice of
    // host CSS. Getters lossy-parse CSS back per R_vaadin_first — same shape as
    // setBorder / getBorder per SD_border_css_lossy. Vaadin Button is already
    // display:inline-flex per Lumo, so flex-direction / justify-content
    // / align-items / gap on the host propagate to the icon + text
    // children directly.
    //
    // R_vaadin_first lossy round-trip: LEFT and LEADING both write `flex-start`
    // and read back as LEADING (canonical); RIGHT and TRAILING both
    // write `flex-end` and read back as TRAILING (matches the JDK
    // textPosition default). verticalTextPosition non-CENTER overrides
    // horizontalTextPosition's effect on flex-direction — when
    // flex-direction is column/column-reverse, getHorizontalTextPosition
    // reports the JDK default TRAILING (the value is unrecoverable
    // from CSS alone).

    default int getHorizontalAlignment() {
        return justifyContentToSwing(_self().getElement().getStyle().get("justify-content"));
    }
    default void setHorizontalAlignment(int alignment) {
        int validated = checkHorizontalKey(alignment, "horizontalAlignment");
        com.vaadin.flow.dom.Style style = _self().getElement().getStyle();
        String oldCss = style.get("justify-content");
        String newCss = swingToJustifyContent(validated);
        if (newCss.equals(oldCss)) return;
        int oldCanonical = justifyContentToSwing(oldCss);
        style.set("justify-content", newCss);
        firePropertyChange("horizontalAlignment", oldCanonical, justifyContentToSwing(newCss));
    }

    default int getVerticalAlignment() {
        return alignItemsToSwing(_self().getElement().getStyle().get("align-items"));
    }
    default void setVerticalAlignment(int alignment) {
        int validated = checkVerticalKey(alignment, "verticalAlignment");
        com.vaadin.flow.dom.Style style = _self().getElement().getStyle();
        String oldCss = style.get("align-items");
        String newCss = swingToAlignItems(validated);
        if (newCss.equals(oldCss)) return;
        int oldCanonical = alignItemsToSwing(oldCss);
        style.set("align-items", newCss);
        firePropertyChange("verticalAlignment", oldCanonical, alignItemsToSwing(newCss));
    }

    default int getHorizontalTextPosition() {
        return flexDirectionToHTextPos(_self().getElement().getStyle().get("flex-direction"));
    }
    default void setHorizontalTextPosition(int pos) {
        int validated = checkHorizontalKey(pos, "horizontalTextPosition");
        com.vaadin.flow.dom.Style style = _self().getElement().getStyle();
        String oldCss = style.get("flex-direction");
        // Preserve currently-rendered v-text-pos: column/column-reverse
        // override horizontal, so the new CSS depends on whether v is
        // currently non-CENTER.
        int currentVTextPos = flexDirectionToVTextPos(oldCss);
        String newCss = buttonFlexDirectionCss(validated, currentVTextPos);
        if (newCss.equals(oldCss)) return;
        int oldCanonical = flexDirectionToHTextPos(oldCss);
        style.set("flex-direction", newCss);
        firePropertyChange("horizontalTextPosition", oldCanonical, flexDirectionToHTextPos(newCss));
    }

    default int getVerticalTextPosition() {
        return flexDirectionToVTextPos(_self().getElement().getStyle().get("flex-direction"));
    }
    default void setVerticalTextPosition(int pos) {
        int validated = checkVerticalKey(pos, "verticalTextPosition");
        com.vaadin.flow.dom.Style style = _self().getElement().getStyle();
        String oldCss = style.get("flex-direction");
        // When promoting to/from column shape, the row-axis h-text-pos
        // is masked. We use the canonical readback (TRAILING when
        // currently in column shape) — accepted lossy round-trip per R_vaadin_first.
        int currentHTextPos = flexDirectionToHTextPos(oldCss);
        String newCss = buttonFlexDirectionCss(currentHTextPos, validated);
        if (newCss.equals(oldCss)) return;
        int oldCanonical = flexDirectionToVTextPos(oldCss);
        style.set("flex-direction", newCss);
        firePropertyChange("verticalTextPosition", oldCanonical, flexDirectionToVTextPos(newCss));
    }

    private static String buttonFlexDirectionCss(int hTextPos, int vTextPos) {
        if (vTextPos == SwingConstants.TOP) return "column-reverse";
        if (vTextPos == SwingConstants.BOTTOM) return "column";
        return switch (hTextPos) {
            case SwingConstants.RIGHT, SwingConstants.TRAILING -> "row";
            default -> "row-reverse";
        };
    }

    private static String swingToJustifyContent(int alignment) {
        return switch (alignment) {
            case SwingConstants.LEFT, SwingConstants.LEADING -> "flex-start";
            case SwingConstants.CENTER -> "center";
            case SwingConstants.RIGHT, SwingConstants.TRAILING -> "flex-end";
            default -> "center";
        };
    }

    private static String swingToAlignItems(int alignment) {
        return switch (alignment) {
            case SwingConstants.TOP -> "flex-start";
            case SwingConstants.CENTER -> "center";
            case SwingConstants.BOTTOM -> "flex-end";
            default -> "center";
        };
    }

    /** Lossy CSS readback: flex-start/flex-end → canonical LEADING/TRAILING (JDK favored constants). */
    private static int justifyContentToSwing(String css) {
        if (css == null) return SwingConstants.CENTER;
        return switch (css) {
            case "flex-start" -> SwingConstants.LEADING;
            case "flex-end" -> SwingConstants.TRAILING;
            default -> SwingConstants.CENTER;
        };
    }

    /** Lossy CSS readback: align-items → TOP/CENTER/BOTTOM (no canonical ambiguity here). */
    private static int alignItemsToSwing(String css) {
        if (css == null) return SwingConstants.CENTER;
        return switch (css) {
            case "flex-start" -> SwingConstants.TOP;
            case "flex-end" -> SwingConstants.BOTTOM;
            default -> SwingConstants.CENTER;
        };
    }

    /**
     * Lossy CSS readback for horizontal text-position. row → TRAILING,
     * row-reverse → LEADING (canonical); column / column-reverse mask
     * the row axis and report TRAILING (JDK default — accepted loss).
     */
    private static int flexDirectionToHTextPos(String css) {
        if (css == null) return SwingConstants.TRAILING;
        return switch (css) {
            case "row-reverse" -> SwingConstants.LEADING;
            // row, column, column-reverse, anything else → JDK default TRAILING
            default -> SwingConstants.TRAILING;
        };
    }

    /** Lossy CSS readback for vertical text-position. column → BOTTOM, column-reverse → TOP, row* → CENTER. */
    private static int flexDirectionToVTextPos(String css) {
        if (css == null) return SwingConstants.CENTER;
        return switch (css) {
            case "column-reverse" -> SwingConstants.TOP;
            case "column" -> SwingConstants.BOTTOM;
            default -> SwingConstants.CENTER;
        };
    }

    /** Parse {@code "Npx"} → {@code N}; non-numeric / null falls back to JDK default 4. */
    private static int parseGapPx(String css) {
        if (css == null) return 4;
        String trimmed = css.endsWith("px") ? css.substring(0, css.length() - 2) : css;
        try { return Integer.parseInt(trimmed.trim()); }
        catch (NumberFormatException nfe) { return 4; }
    }

    /** JDK validator — vertical SwingConstants only; IAE otherwise (R_match_swing_errors / D_never_fail_on_gaps). */
    private static int checkVerticalKey(int key, String exception) {
        if (key == SwingConstants.TOP || key == SwingConstants.CENTER || key == SwingConstants.BOTTOM) return key;
        throw new IllegalArgumentException(exception);
    }

    /** JDK validator — horizontal SwingConstants only; IAE otherwise. */
    private static int checkHorizontalKey(int key, String exception) {
        if (key == SwingConstants.LEFT || key == SwingConstants.CENTER || key == SwingConstants.RIGHT
                || key == SwingConstants.LEADING || key == SwingConstants.TRAILING) return key;
        throw new IllegalArgumentException(exception);
    }

    /** JDK default is -1. Validates per R_match_swing_errors; non-(-1) values drop with WARN. */
    default int getDisplayedMnemonicIndex() { return -1; }
    default void setDisplayedMnemonicIndex(int index) {
        String text = getText();
        if (index < -1 || (text == null && index != -1) || (text != null && index >= text.length())) {
            throw new IllegalArgumentException("Invalid mnemonic index: " + index);
        }
        if (index != -1) warnDrop("setDisplayedMnemonicIndex", index);
    }

    // --- Mnemonic (UI-functional: drives Shortcuts accelerator) -----

    default int getMnemonic() { return ButtonStateStore.of(_self()).mnemonic; }

    /**
     * Install {@code Alt+<mnemonic>} as a Vaadin {@link Shortcuts}
     * listener on the peer that invokes {@link #doClick}. Replacing /
     * clearing tears down the previous registration first. Zero means
     * "no mnemonic" and leaves the peer without a shortcut.
     */
    default void setMnemonic(int mnemonic) {
        ButtonStateStore s = ButtonStateStore.of(_self());
        int old = s.mnemonic;
        if (old == mnemonic) return;
        s.mnemonic = mnemonic;
        if (s.mnemonicRegistration != null) {
            s.mnemonicRegistration.remove();
            s.mnemonicRegistration = null;
        }
        if (mnemonic != 0) {
            Key key = KeyConvert.vkToVaadinKey(mnemonic);
            if (key != null) {
                s.mnemonicRegistration = Shortcuts.addShortcutListener(
                        _self(),
                        () -> SHelper.callSwing(this::doClick),
                        key,
                        KeyModifier.ALT);
            } else {
                SHelper.onUnimplemented(_self(), "setMnemonic", mnemonic);
            }
        }
        firePropertyChange("mnemonic", old, mnemonic);
    }

    /** JDK's char→int path: uppercase ASCII-lower then delegate to the int variant. */
    default void setMnemonic(char mnemonic) {
        int vk = (int) mnemonic;
        if (vk >= 'a' && vk <= 'z') vk -= ('a' - 'A');
        setMnemonic(vk);
    }

    // --- Listeners (button level) ------------------------------------

    default void addActionListener(ActionListener l) {
        ButtonStateStore.of(_self()).listenerList.add(ActionListener.class, l);
    }
    default void removeActionListener(ActionListener l) {
        ButtonStateStore.of(_self()).listenerList.remove(ActionListener.class, l);
    }
    default ActionListener[] getActionListeners() {
        return ButtonStateStore.of(_self()).listenerList.getListeners(ActionListener.class);
    }

    default void addChangeListener(ChangeListener l) {
        ButtonStateStore.of(_self()).listenerList.add(ChangeListener.class, l);
    }
    default void removeChangeListener(ChangeListener l) {
        ButtonStateStore.of(_self()).listenerList.remove(ChangeListener.class, l);
    }
    default ChangeListener[] getChangeListeners() {
        return ButtonStateStore.of(_self()).listenerList.getListeners(ChangeListener.class);
    }

    default void addItemListener(ItemListener l) {
        ButtonStateStore.of(_self()).listenerList.add(ItemListener.class, l);
    }
    default void removeItemListener(ItemListener l) {
        ButtonStateStore.of(_self()).listenerList.remove(ItemListener.class, l);
    }
    default ItemListener[] getItemListeners() {
        return ButtonStateStore.of(_self()).listenerList.getListeners(ItemListener.class);
    }

    // --- Fire helpers (called by model-listener fan-out + doClick) ---

    /**
     * Re-sources the {@link ActionEvent} to {@code this} and iterates
     * the button-level ActionListener list. LIFO order matches JDK.
     */
    default void fireActionPerformed(ActionEvent event) {
        if (event == null) return;
        ActionEvent dispatched = event.getSource() == _self()
                ? event
                : new ActionEvent(_self(), event.getID(), event.getActionCommand(),
                        event.getWhen(), event.getModifiers());
        EventListenerList list = ButtonStateStore.of(_self()).listenerList;
        for (ActionListener l : list.getListeners(ActionListener.class)) {
            l.actionPerformed(dispatched);
        }
    }

    /** Fan-out for model change events; lazy ChangeEvent matches JDK. */
    default void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(_self());
        EventListenerList list = ButtonStateStore.of(_self()).listenerList;
        for (ChangeListener l : list.getListeners(ChangeListener.class)) {
            l.stateChanged(event);
        }
    }

    /** Fan-out for item events. JDK does not re-source. */
    default void fireItemStateChanged(ItemEvent event) {
        if (event == null) return;
        EventListenerList list = ButtonStateStore.of(_self()).listenerList;
        for (ItemListener l : list.getListeners(ItemListener.class)) {
            l.itemStateChanged(event);
        }
    }

    // --- Action wiring ------------------------------------------------

    default Action getAction() { return ButtonStateStore.of(_self()).action; }

    /**
     * Install / replace / clear the backing {@link Action}. Equals-based
     * dedupe matches JDK. Teardown detaches the old Action's PCL and
     * removes it from the ActionListener list; install copies name /
     * command / icon / mnemonic / tooltip / enabled via
     * {@link #configurePropertiesFromAction} and registers a fresh PCL
     * so Action mutations propagate. Fires the {@code "action"} PCE.
     */
    default void setAction(Action a) {
        ButtonStateStore s = ButtonStateStore.of(_self());
        Action oldValue = s.action;
        if (oldValue == null ? a == null : oldValue.equals(a)) return;
        s.action = a;
        if (oldValue != null) {
            removeActionListener(oldValue);
            if (s.actionPropertyChangeListener != null) {
                oldValue.removePropertyChangeListener(s.actionPropertyChangeListener);
                s.actionPropertyChangeListener = null;
            }
        }
        configurePropertiesFromAction(a);
        if (a != null) {
            if (!isActionListener(a)) addActionListener(a);
            s.actionPropertyChangeListener = createActionPropertyChangeListener(a);
            a.addPropertyChangeListener(s.actionPropertyChangeListener);
        }
        firePropertyChange("action", oldValue, a);
    }

    /** Default PCL: routes {@link Action} mutations to {@link #actionPropertyChanged}. */
    default PropertyChangeListener createActionPropertyChangeListener(Action a) {
        return new ButtonActionPropertyChangeListener(this, a);
    }

    /** Per-key Action-mutation router. JDK's exact shape. */
    default void actionPropertyChanged(Action a, String propertyName) {
        if (Action.NAME.equals(propertyName)) {
            setNameFromAction(a);
        } else if ("enabled".equals(propertyName)) {
            setEnabled(a.isEnabled());
        } else if (Action.SHORT_DESCRIPTION.equals(propertyName)) {
            setToolTipTextFromAction(a);
        } else if (Action.SMALL_ICON.equals(propertyName) || Action.LARGE_ICON_KEY.equals(propertyName)) {
            setIconFromAction(a);
        } else if (Action.ACTION_COMMAND_KEY.equals(propertyName)) {
            setActionCommandFromAction(a);
        } else if (Action.MNEMONIC_KEY.equals(propertyName)) {
            setMnemonicFromAction(a);
            setDisplayedMnemonicIndexFromAction(a);
        } else if (Action.DISPLAYED_MNEMONIC_INDEX_KEY.equals(propertyName)) {
            setDisplayedMnemonicIndexFromAction(a);
        }
        // Unknown keys: drop silently (matches JDK).
    }

    /** JDK-shaped Action-property copy. */
    default void configurePropertiesFromAction(Action a) {
        setNameFromAction(a);
        setActionCommandFromAction(a);
        setIconFromAction(a);
        setMnemonicFromAction(a);
        setDisplayedMnemonicIndexFromAction(a);
        setToolTipTextFromAction(a);
        setEnabled(a == null || a.isEnabled());
    }

    private void setNameFromAction(Action a) {
        if (getHideActionText()) return;
        setText(a == null ? null : (String) a.getValue(Action.NAME));
    }

    private void setActionCommandFromAction(Action a) {
        setActionCommand(a == null ? null : (String) a.getValue(Action.ACTION_COMMAND_KEY));
    }

    private void setIconFromAction(Action a) {
        if (a == null) { setIcon(null); return; }
        Object iconObj = a.getValue(Action.LARGE_ICON_KEY);
        if (iconObj == null) iconObj = a.getValue(Action.SMALL_ICON);
        if (iconObj == null) { setIcon(null); return; }
        if (iconObj instanceof Icon ours) { setIcon(ours); return; }
        SHelper.onUnimplemented(_self(), "setIconFromAction", iconObj);
    }

    private void setMnemonicFromAction(Action a) {
        Integer n = (a == null) ? null : (Integer) a.getValue(Action.MNEMONIC_KEY);
        setMnemonic(n == null ? 0 : n);
    }

    private void setDisplayedMnemonicIndexFromAction(Action a) {
        Integer index = (a == null) ? null : (Integer) a.getValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY);
        int normalised;
        if (index == null) {
            normalised = -1;
        } else {
            String t = getText();
            if (index < -1 || t == null || index >= t.length()) normalised = -1;
            else normalised = index;
        }
        setDisplayedMnemonicIndex(normalised);
    }

    private void setToolTipTextFromAction(Action a) {
        setToolTipText(a == null ? null : (String) a.getValue(Action.SHORT_DESCRIPTION));
    }

    private boolean isActionListener(ActionListener a) {
        for (ActionListener l : ButtonStateStore.of(_self()).listenerList.getListeners(ActionListener.class)) {
            if (l == a) return true;
        }
        return false;
    }

    /** Static PCL to avoid an anonymous-inner-class capture chain for every button. */
    final class ButtonActionPropertyChangeListener implements PropertyChangeListener {
        private final AbstractButtonMixin button;
        private final Action action;
        ButtonActionPropertyChangeListener(AbstractButtonMixin button, Action action) {
            this.button = button;
            this.action = action;
        }
        @Override
        public void propertyChange(PropertyChangeEvent e) {
            button.actionPropertyChanged(action, e.getPropertyName());
        }
    }

    // --- L&F / UI accessors (no-op; Vaadin peer is our "UI") ---------

    default String getUIClassID() {
        return "ButtonUI";
    }

    default void updateUI() {
        // No-op: our "UI" is the Vaadin peer, which isn't pluggable. JDK
        // would reinstall the ButtonUI from UIManager.
    }

    // Note: no JDK-shaped getUI() / setUI(ButtonUI) — Vaadin
    // Component.getUI() returns Optional<UI>, signatures clash the same
    // way getIcon() does. See class javadoc + SD_sjbutton.
}
