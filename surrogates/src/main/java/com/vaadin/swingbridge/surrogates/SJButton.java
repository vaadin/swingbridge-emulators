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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.swingbridge.surrogates.swing.AbstractButtonMixin;

/**
 * Surrogate for {@link javax.swing.JButton} (SD_sjbutton). Extends Vaadin
 * {@link Button} directly (is-a) so it reaches the migrator either
 * through {@code :emulators.JButton} as peer, or as a richer-than-stock
 * Vaadin component in its own right.
 *
 * <h2>Mixin split with SJToggleButton / SJCheckBox</h2>
 *
 * The {@link AbstractButtonMixin} lineage is shared with future
 * {@code SJToggleButton} / {@code SJCheckBox} / {@code SJRadioButton}
 * surrogates — any AbstractButton method lives there. SJButton only adds
 * the JButton-specific slice: {@code defaultCapable} + {@code "defaultCapable"}
 * PCE, the stub {@code isDefaultButton} (true only when the enclosing
 * root pane picks this as its default — surrogate-side root-pane wiring
 * covers that through SJRootPane's separate {@code defaultButton} state),
 * and the {@code JButton.paramString} tail.
 *
 * <h2>Peer click → Swing ActionEvent</h2>
 *
 * {@link AbstractButtonMixin#installButtonBindings} wires the peer's
 * {@code ClickNotifier} to {@link AbstractButtonMixin#synthesizePeerClick},
 * which drives the installed {@link javax.swing.ButtonModel} through its
 * armed/pressed pulse. Default model ({@link javax.swing.DefaultButtonModel})
 * fires an ActionEvent on the pressed→unpressed transition while armed,
 * which the mixin's model-listener fan-out re-sources to {@code this} and
 * delivers to user {@link java.awt.event.ActionListener}s. User code that
 * calls {@link #doClick} takes the same model path, so programmatic and
 * user clicks deliver identical event shapes.
 *
 * <h2>{@code defaultCapable} and root-pane default-button integration</h2>
 *
 * {@code defaultCapable} is field-round-trip + PCE; the actual "become the
 * root pane's default button" wiring sits on {@link SJRootPane}. Migrated
 * code that reads {@code isDefaultCapable} back after setting it works
 * without touching the root pane. {@code isDefaultButton} returns false
 * here (the button doesn't know which root pane owns it from the server
 * side); SJRootPane is the canonical query point for default-button
 * identity.
 */
public class SJButton extends Button implements AbstractButtonMixin {

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
     * Swing default: a freshly-constructed JButton is eligible to become
     * the root pane's default button. Round-trips + PCE; see class
     * javadoc for the split with {@link SJRootPane}.
     */
    private boolean defaultCapable = true;

    public SJButton() {
        this(null, null);
    }

    public SJButton(String text) {
        this(text, null);
    }

    public SJButton(javax.swing.Icon icon) {
        this(null, icon);
    }

    public SJButton(javax.swing.Action action) {
        // JDK pattern: this(); setAction(a). Chaining to the no-arg ctor
        // means installButtonBindings fires once and setAction does the
        // NAME / MNEMONIC / ICON / COMMAND / ENABLED copy afterwards.
        this();
        setAction(action);
    }

    public SJButton(String text, javax.swing.Icon icon) {
        super();
        _installSwingClass();
        installButtonBindings();
        // JDK's AbstractButton.init only calls setText/setIcon when
        // non-null so icon-only / text-only ctors don't stomp each other.
        if (text != null) setText(text);
        if (icon != null) setIcon(icon);
    }

    // --- Text dispatch (mixin declares getText/setText abstract) -----
    //
    // setText overrides Vaadin Button.setText to add the "text" PCE
    // fire on top — super.setText runs Button's textSupport-based write
    // (the rendered caption path), then we fire PCE so migrated MVC
    // listeners see the transition.
    //
    // No getText override needed: Button.getText (inherited via
    // HasText / textNode) satisfies the mixin's abstract method
    // automatically, and getText doesn't fire PCE in JDK so there's
    // nothing to add.

    @Override
    public void setText(String text) {
        String old = super.getText();
        super.setText(text == null ? "" : text);
        firePropertyChange("text", old, text);
    }

    /**
     * Java "classes beat interfaces" rule shadows the mixin chain's
     * setEnabled (which carries SD_auto_pce's auto-PCE) with Vaadin
     * {@code HasEnabled}'s default. Redirect explicitly so the auto-PCE
     * still fires on direct {@code sjButton.setEnabled(...)} calls.
     */
    @Override
    public void setEnabled(boolean enabled) {
        AbstractButtonMixin.super.setEnabled(enabled);
    }

    // --- JButton-specific state --------------------------------------

    public boolean isDefaultCapable() {
        return defaultCapable;
    }

    /** Swing fires {@code "defaultCapable"} PCE; equal-value writes dedupe. */
    public void setDefaultCapable(boolean defaultCapable) {
        boolean old = this.defaultCapable;
        this.defaultCapable = defaultCapable;
        firePropertyChange("defaultCapable", old, defaultCapable);
    }

    /**
     * Whether this button is the root pane's current default button.
     * Returns false — the surrogate-side source of truth for default-
     * button identity lives on {@link SJRootPane} (see its
     * {@code getDefaultButton()}), and a button doesn't know which root
     * pane owns it from the server side. Migrated code branching on this
     * just takes the non-default path.
     */
    public boolean isDefaultButton() {
        return false;
    }

    @Override
    public String getUIClassID() {
        return "ButtonUI";
    }

    /**
     * JDK JButton appends {@code ",defaultCapable=<bool>"} to the
     * AbstractButton shape. We skip the {@code AbstractButton.paramString}
     * chain entirely (Vaadin's {@code Component.toString} is the honest
     * host-side equivalent) and just report the JButton-specific flag.
     */
    protected String paramString() {
        return "defaultCapable=" + defaultCapable;
    }

    // --- Accessibility (deferred per emulator stance) ----------------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }

    /** Self-cast for holder lookups — same pattern as SFrame. */
    @SuppressWarnings("unused")
    private Component self() {
        return this;
    }
}
