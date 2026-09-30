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
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;

import javax.swing.event.EventListenerList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Surrogate for {@link java.awt.Button} — the AWT 1.0 push button, not
 * {@link javax.swing.JButton}. Extends Vaadin {@link Button} directly
 * (is-a) and picks up the AWT {@code Component} API from
 * {@link ComponentMixin}.
 *
 * <h2>Why not {@code AbstractButtonMixin}</h2>
 *
 * {@link SJButton} reaches its {@code ActionEvent} fan-out through
 * {@link com.vaadin.swingbridge.surrogates.swing.AbstractButtonMixin}'s {@link javax.swing.ButtonModel}
 * armed/pressed pulse. {@code java.awt.Button} has no model, no
 * {@code Action}, no icon, no mnemonic and no selected state — reusing the
 * Swing mixin would graft a {@code ButtonModel} state machine and a
 * {@code getModel()} / {@code setAction()} / {@code isSelected()} surface
 * onto a surrogate whose JDK original has none of them. The overlap is the
 * ~40 lines of listener fan-out below, and duplicating it is the cheaper
 * mistake than a shared base with shallow commonality. The click wiring is
 * the same shape R_vaadin_first sanctions for a shared subscription: one Vaadin
 * {@code ClickNotifier} subscription drives an {@link EventListenerList}
 * fan-out.
 *
 * <h2>State</h2>
 *
 * {@code actionCommand} is stored (a single field, no {@code Store} class
 * warranted) — R_vaadin_first-legitimate because it is the {@link ActionEvent} payload,
 * i.e. UI functionality, not an API round-trip shadow. {@code label} is
 * <em>not</em> stored: it routes to the Vaadin {@code Button}'s own
 * {@code getText}/{@code setText}, so {@code setLabel(null)} reads back as
 * {@code ""} (accepted R_vaadin_first loss, same contract as SJButton). The emulator
 * {@code vaadinx.awt.Button} keeps a field shadow so its {@code getLabel()}
 * returns null verbatim per R_swing_is_truth.
 */
public class SButton extends Button implements ComponentMixin {

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
     * No Vaadin analog; drives the {@link ActionEvent} payload. Null means
     * "fall back to the label", matching AWT.
     */
    private String actionCommand;

    private final EventListenerList listenerList = new EventListenerList();

    public SButton() {
        this(null);
    }

    public SButton(String label) {
        super();
        _installSwingClass();
        // One Vaadin subscription drives the whole ActionListener fan-out
        // (R_vaadin_first's shared-subscription wiring shape). R_callswing_envelope: the browser → AWT
        // seam funnels through callSwing so a listener that opens a modal
        // dialog can park on the loom virtual thread.
        addClickListener(e -> SHelper.callSwing(
                () -> processActionEvent(new ActionEvent(
                        this,
                        ActionEvent.ACTION_PERFORMED,
                        getActionCommand(),
                        System.currentTimeMillis(),
                        0))));
        if (label != null) setLabel(label);
    }

    // --- label (AWT 1.0 name for Vaadin's text) -----------------------

    /**
     * AWT's name for the caption. Reads the Vaadin {@code Button} text, so
     * a label never set reads back as {@code ""} rather than AWT's null —
     * the R_vaadin_first loss the class javadoc names.
     */
    public String getLabel() {
        return getText();
    }

    public void setLabel(String label) {
        // Vaadin's setText NPEs on null; a captionless button rendering
        // blank is what the caller asked for. AWT's setLabel fires no
        // PropertyChangeEvent, so neither do we.
        setText(label == null ? "" : label);
    }

    // --- actionCommand ------------------------------------------------

    /** Falls back to the label when never set, matching AWT. */
    public String getActionCommand() {
        return actionCommand != null ? actionCommand : getText();
    }

    public void setActionCommand(String command) {
        actionCommand = command;
    }

    // --- ActionListener fan-out ---------------------------------------

    public void addActionListener(ActionListener l) {
        // AWT silently ignores null listeners; EventListenerList.add would
        // happily store one and NPE at dispatch time.
        if (l == null) return;
        listenerList.add(ActionListener.class, l);
    }

    public void removeActionListener(ActionListener l) {
        if (l == null) return;
        listenerList.remove(ActionListener.class, l);
    }

    public ActionListener[] getActionListeners() {
        return awtOrder();
    }

    /** @return the listeners in AWT's first-registered-first dispatch order (D_awt_dead_hooks) */
    private ActionListener[] awtOrder() {
        return SHelper.awtOrder(listenerList, ActionListener.class);
    }

    /**
     * AWT's dispatch hook. Overriding it (and calling super) is the
     * documented way for {@code java.awt.Button} subclasses to intercept
     * every action before its listeners see it, so it stays the single
     * funnel the peer click routes through.
     */
    protected void processActionEvent(ActionEvent e) {
        if (e == null) return;
        for (ActionListener l : awtOrder()) {
            l.actionPerformed(e);
        }
    }

    // AWT's generic getListeners(Class<T>) is NOT declared here: Vaadin
    // Component already has getListeners(Class<? extends ComponentEvent>),
    // same erasure and an unrelated return type, so the JVM forbids both on
    // one class. Same clash AbstractButtonMixin documents for getIcon() /
    // getUI(). getActionListeners() covers the only listener type this
    // class owns, and the emulator vaadinx.awt.Button inherits a working
    // getListeners from vaadinx.awt.Component — which is where migrated
    // code calls it anyway.

    // --- Overrides forced by "classes beat interfaces" ----------------

    /**
     * Vaadin {@code HasEnabled}'s concrete default shadows the mixin's
     * {@code setEnabled} (which carries SD_auto_pce's auto-PCE). Redirect
     * explicitly, same as {@link SJButton}.
     */
    @Override
    public void setEnabled(boolean enabled) {
        ComponentMixin.super.setEnabled(enabled);
    }

    /** AWT's {@code Button.paramString} appends only the label. */
    protected String paramString() {
        return "label=" + getText();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
