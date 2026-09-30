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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.internal.ButtonStateStore;
import com.vaadin.swingbridge.surrogates.swing.AbstractButtonMixin;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Surrogate for {@link javax.swing.JToggleButton} (SD_sjtogglebutton_button_peer). Extends Vaadin
 * {@link Button} directly (is-a), rendering a stays-pressed button —
 * JToggleButton's actual Swing look — rather than a checkbox glyph.
 * Reaches the migrator either through {@code :emulators.JToggleButton} as
 * peer, or as a richer-than-stock Vaadin toggle in its own right.
 *
 * <pre>{@code
 * SJToggleButton bold = new SJToggleButton("Bold");
 * bold.addItemListener(e -> font.bold = e.getStateChange() == ItemEvent.SELECTED);
 * // browser click -> Button ClickEvent -> ToggleButtonModel pulse ->
 * //                  Item + Change + Action, and aria-pressed flips.
 * }</pre>
 *
 * <h2>Button peer vs. the Checkbox-family toggles</h2>
 *
 * {@link SJCheckBox} / {@link SJRadioButton} peer over {@code HasValue<Boolean>}
 * widgets (Checkbox / radio) and render a glyph; this one peers over
 * {@link Button}, which is <em>not</em> {@code HasValue}. That single
 * difference drives every divergence from those siblings:
 *
 * <ul>
 *   <li><b>Browser wire is {@code ClickNotifier}, not {@code ValueChange}.</b>
 *       A Button emits {@code ClickEvent}; there is no boolean value to
 *       listen on. So the mixin's default {@code ClickNotifier} wire fits —
 *       {@link #installButtonBindings} only swaps the default model for
 *       {@link JToggleButton.ToggleButtonModel} so press→release-while-armed
 *       toggles {@code selected} and fires Item + Change + Action.</li>
 *   <li><b>Selection shows as an {@code aria-pressed} attribute + CSS,
 *       not a checked box.</b> The {@link #setModel} push wire mirrors
 *       {@code model.isSelected()} onto the {@code aria-pressed} host
 *       attribute; {@code emul/sjtogglebutton.css} paints the depressed
 *       look off it. No feedback-loop guard is needed (unlike the
 *       Checkbox siblings' {@code preventPeerEvents}) because writing an
 *       attribute doesn't echo back as a {@code ClickEvent}.</li>
 *   <li><b>Layout + icon bind for real</b>, inherited straight from
 *       {@link AbstractButtonMixin}: Button's host is {@code display:
 *       inline-flex} per Lumo, so alignment / text-position / iconTextGap
 *       write host CSS and read back, and {@code setIcon} hits the mixin's
 *       Vaadin-first Button branch. The Checkbox siblings drop-and-WARN
 *       these (shadow DOM won't honor host flex, no icon slot) — this
 *       surrogate doesn't.</li>
 *   <li><b>{@code setText} routes through Button's {@code HasText}</b>
 *       (SJButton shape) — Button is texted, not labelled.</li>
 * </ul>
 *
 * <h2>ButtonGroup mutex lives at the emulator layer (D_buttongroup_browser_click)</h2>
 *
 * The surrogate carries no {@code ButtonGroup} awareness — standalone, a
 * click toggles on/off via the ToggleButtonModel pulse. Stage-3 group
 * mutex uses {@code :emulators.JToggleButton} + {@code vaadinx.swing.ButtonGroup}
 * per D_buttongroup; the emulator's Item bridge coordinates and pushes the group's
 * verdict back through {@link #setSelected}.
 */
@StyleSheet("emul/sjtogglebutton.css")
public class SJToggleButton extends Button implements AbstractButtonMixin {

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
     * Teardown handle for the model→peer push ItemListener installed in
     * {@link #setModel(ButtonModel)}. Same shape as the Checkbox siblings'
     * field of the same name — opaque {@link Registration}, null when no
     * model is installed. (No {@code preventPeerEvents} companion: the
     * push writes an attribute, which can't echo back as a click.)
     */
    private Registration peerSyncRegistration;

    public SJToggleButton() {
        this((String) null, false);
    }

    public SJToggleButton(String text) {
        this(text, false);
    }

    public SJToggleButton(String text, boolean selected) {
        super();
        _installSwingClass();
        installButtonBindings();
        if (text != null) setText(text);
        if (selected) setSelected(true);
    }

    public SJToggleButton(Icon icon) {
        this((String) null, false);
        setIcon(icon);
    }

    public SJToggleButton(Icon icon, boolean selected) {
        this((String) null, selected);
        setIcon(icon);
    }

    public SJToggleButton(String text, Icon icon) {
        this(text, false);
        setIcon(icon);
    }

    public SJToggleButton(String text, Icon icon, boolean selected) {
        this(text, selected);
        setIcon(icon);
    }

    public SJToggleButton(javax.swing.Action action) {
        // JDK pattern: this(); setAction(a). Chaining to the no-arg ctor
        // means installButtonBindings fires once and setAction does the
        // NAME / MNEMONIC / ICON / COMMAND / ENABLED copy afterwards.
        this();
        setAction(action);
    }

    /**
     * Override of the mixin's default: the sole change is installing
     * {@link JToggleButton.ToggleButtonModel} (toggle-on-release) instead
     * of {@code DefaultButtonModel} (momentary). The {@code ClickNotifier}
     * wire and inline-flex CSS seed are identical to the default — Button
     * is a {@code ClickNotifier} and honors host flex — so both are kept.
     * Idempotent: a no-op once {@code model} is installed.
     */
    @Override
    public void installButtonBindings() {
        ButtonStateStore s = ButtonStateStore.of(this);
        if (s.model != null) return;
        // setModel below installs the mixin's three model bridge listeners
        // AND our model→aria-pressed push wire.
        setModel(new JToggleButton.ToggleButtonModel());
        // Browser click → model pulse. Funnels through SHelper.callSwing
        // per R_callswing_envelope — same wire the mixin default installs for SJButton.
        addClickListener(e -> SHelper.callSwing(this::synthesizePeerClick));
        // Seed JDK AbstractButton's layout defaults into host flex CSS so
        // the mixin's getters read the right canonical before any setter
        // runs. Button is already display:inline-flex per Lumo.
        Style style = getElement().getStyle();
        style.set("flex-direction", "row");     // h-text-pos TRAILING + v-text-pos CENTER
        style.set("justify-content", "center"); // h-align CENTER
        style.set("align-items", "center");     // v-align CENTER
        style.set("gap", "4px");                 // iconTextGap default
    }

    /**
     * Override of {@link AbstractButtonMixin#setModel} to additionally
     * install a model→peer push {@link ItemListener} that mirrors
     * {@code model.isSelected()} onto the {@code aria-pressed} host
     * attribute. Catches direct {@code myModel.setSelected(...)} mutations
     * as well as mixin-routed ones. Tracks teardown in
     * {@link #peerSyncRegistration} so a subsequent {@code setModel} swap
     * detaches cleanly.
     */
    @Override
    public void setModel(ButtonModel newModel) {
        // Tear down OUR push wire on the OLD model first (the mixin tears
        // down its three bridge listeners on its old model independently).
        if (peerSyncRegistration != null) {
            peerSyncRegistration.remove();
            peerSyncRegistration = null;
        }
        AbstractButtonMixin.super.setModel(newModel);
        if (newModel != null) {
            ItemListener pushToPeer = e ->
                    pushSelectedToPeer(e.getStateChange() == ItemEvent.SELECTED);
            newModel.addItemListener(pushToPeer);
            peerSyncRegistration = () -> newModel.removeItemListener(pushToPeer);
            // Seed the attribute to the model's current state so a
            // constructed-selected toggle renders pressed without a click.
            pushSelectedToPeer(newModel.isSelected());
        }
    }

    /**
     * Reflect the selected state onto the {@code aria-pressed} host
     * attribute — correct a11y for a toggle button, and the hook
     * {@code emul/sjtogglebutton.css} styles the depressed look off. No
     * echo: an attribute write never surfaces as a {@code ClickEvent},
     * so this needs no feedback-loop guard.
     */
    private void pushSelectedToPeer(boolean selected) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: ButtonModel ItemListener
        SHelper.runOnOwnerUI(this, () -> getElement().setAttribute("aria-pressed", String.valueOf(selected)));
    }

    // --- Text dispatch (mixin declares getText/setText abstract) -----
    //
    // Button IS HasText (unlike the Checkbox siblings): getText is
    // satisfied by the inherited Button.getText; setText adds the "text"
    // PCE on top of Button's rendered-caption write. Same shape as SJButton.

    @Override
    public void setText(String text) {
        String old = super.getText();
        super.setText(text == null ? "" : text);
        firePropertyChange("text", old, text);
    }

    /**
     * Java "classes beat interfaces" shadows the mixin chain's setEnabled
     * (SD_auto_pce auto-PCE + model propagation) with Vaadin {@code HasEnabled}'s
     * default. Redirect explicitly so the mixin chain runs — matters
     * because ToggleButtonModel's {@code setPressed} short-circuits when
     * disabled.
     */
    @Override
    public void setEnabled(boolean enabled) {
        AbstractButtonMixin.super.setEnabled(enabled);
    }

    // Layout setters + setIcon: NOT overridden — inherit the mixin's real
    // inline-flex CSS binding (Button honors host flex) and Vaadin-first
    // icon slot (Button has one). This is the whole point of the Button
    // peer over the Checkbox glyph.

    @Override
    public String getUIClassID() {
        return "ToggleButtonUI";
    }

    /**
     * JDK JToggleButton's paramString is essentially empty (returns
     * AbstractButton's). We skip the AbstractButton.paramString chain
     * (Vaadin's Component.toString is the honest host-side equivalent)
     * and report nothing extra.
     */
    protected String paramString() {
        return "";
    }

    // --- Accessibility (deferred per emulator stance) ----------------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
