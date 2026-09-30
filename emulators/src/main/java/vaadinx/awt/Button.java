/*
 * Copyright (c) 1995, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's java.awt.Button
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator — the first plain AWT leaf widget in the tree.
// Every other concrete class under vaadinx.awt.Component is either a Swing
// class or a window (Frame / Window / Dialog / FileDialog), so this is the
// class that exercises vaadinx.awt.Component on the path it was designed
// for: a leaf widget with no JComponent above it. Rationale + the sizing of
// the rest of the AWT widget family: ideas/awt-widgets.md.

/**
 * Emulator for {@link java.awt.Button} — a thin delegating shell over its
 * {@link com.vaadin.swingbridge.surrogates.SButton} peer. Not to be confused with
 * {@link vaadinx.swing.JButton}: this is the AWT 1.0 push button, whose
 * entire API is twelve methods with no {@code ButtonModel}, no
 * {@code Action}, no icon, no mnemonic and no selected state. Everything a
 * migrator touches beyond the members below — {@code setBounds},
 * {@code setFont}, {@code setBackground}, {@code setEnabled},
 * {@code addMouseListener}, {@code requestFocus} — is inherited
 * {@link vaadinx.awt.Component}.
 *
 * <p>Responsibility split with the peer:
 *
 * <ul>
 *   <li><b>Surrogate</b> — the rendered Vaadin Button, the
 *       {@code ClickNotifier} → {@code ActionEvent} fan-out, and the
 *       actionCommand-falls-back-to-label rule.</li>
 *   <li><b>Emulator (here)</b> — the AWT-shape API migrated code compiles
 *       against: {@code ActionListener} with {@code source == this}
 *       {@code Button}, and a {@code label} field shadow so
 *       {@code getLabel()} returns null verbatim where the surrogate's
 *       Vaadin-backed {@code getText()} would say {@code ""} (R_swing_is_truth over the
 *       accepted R_vaadin_first loss).</li>
 * </ul>
 *
 * <p>Leaf peer is locked down per R_leaf_peer_lockdown: {@code java.awt.Button} has no
 * public subclass in {@code java.awt}, so there is no protected
 * {@code (Component peer)} ctor and every instance peers over an
 * {@link com.vaadin.swingbridge.surrogates.SButton}.
 */
public class Button extends vaadinx.awt.Component implements javax.accessibility.Accessible {

    // AWT stores the label verbatim (null is legal, and getLabel() returns
    // it as-is). The peer can't hold null — Vaadin's setText NPEs on it and
    // reads back "" — so the emulator is the only side where the JDK's null
    // round-trip survives. Same split AbstractButton.setText already uses
    // for `text`.
    private java.lang.String label;

    // AWT: actionCommand defaults to null and getActionCommand falls back to
    // the label, so a Button("OK") with no explicit command still produces a
    // meaningful ActionEvent.actionCommand. Kept here in addition to the
    // peer's copy so the getter answers without a peer round-trip; setter
    // pushes to the peer (R_swing_is_truth) so peer-originated ActionEvents carry it too.
    private java.lang.String actionCommand;

    /** JDK chains the no-arg ctor to {@code this("")} — the label is empty, not null. */
    public Button() throws java.awt.HeadlessException {
        this("");
    }

    /**
     * @param label null is legal and reads back from {@link #getLabel} verbatim
     * @throws java.awt.HeadlessException never in practice — the clause is kept
     *         for signature fidelity, matching {@link vaadinx.awt.Frame}
     */
    public Button(java.lang.String label) throws java.awt.HeadlessException {
        // R_leaf_peer_lockdown lock-down: super(...) takes the SButton directly, no peer seam.
        super(new com.vaadin.swingbridge.surrogates.SButton());
        // Peer → AWT event pipeline. The surrogate already funnels browser
        // clicks through SHelper.callSwing into its own ActionListener
        // fan-out with the actionCommand resolved; we re-source the event to
        // `this` so migrated code casting `(Button) e.getSource()` sees the
        // emulator, not the surrogate — same pattern as AbstractButton's
        // AbstractButtonMixin bridge. callSwing again per R_callswing_envelope: the surrogate's
        // envelope is already open, and nested callSwing runs inline (D_callswing_loom).
        //
        // Enter at processEvent, not processActionEvent: AWT routes a
        // peer-posted event dispatchEvent → processEvent → processActionEvent,
        // so entering at the second hop leaves a migrator's processEvent
        // override compiling, looking wired, and never running on a real click
        // (R_no_vaadin_in_api limb 2).
        surrogate().addActionListener(e -> vaadinx.EHelper.callSwing(() ->
                processEvent(new java.awt.event.ActionEvent(
                        this,
                        e.getID(),
                        e.getActionCommand(),
                        e.getWhen(),
                        e.getModifiers()))));
        setLabel(label);
    }

    private com.vaadin.swingbridge.surrogates.SButton surrogate() {
        return (com.vaadin.swingbridge.surrogates.SButton) getPeer();
    }

    @Override
    public void addNotify() {
        // JDK creates the native peer here. Ours is eternal and created in
        // the ctor, so there is nothing to allocate — but the override is
        // kept rather than dropped: user code overriding addNotify() and
        // calling super.addNotify() is a common AWT idiom, and Component's
        // implementation is what logs the displayable transition.
        super.addNotify();
    }

    // --- label --------------------------------------------------------

    public java.lang.String getLabel() {
        // AWT returns the field verbatim — null if constructed with null.
        return label;
    }

    public void setLabel(java.lang.String label) {
        // R_swing_is_truth: store the Swing-side field first, then push to the peer, so a
        // listener firing off the peer write already sees the new value.
        this.label = label;
        withPeer(p -> surrogate().setLabel(label));
    }

    // --- actionCommand ------------------------------------------------

    public java.lang.String getActionCommand() {
        // AWT falls back to the label when no command was ever set. Null
        // label propagates through — a null-labelled Button really does
        // produce a null actionCommand in AWT.
        return actionCommand != null ? actionCommand : label;
    }

    public void setActionCommand(java.lang.String command) {
        this.actionCommand = command;
        // Push to the peer so the ActionEvent it synthesizes on a browser
        // click already carries the right command and the bridge above can
        // pass e.getActionCommand() straight through.
        withPeer(p -> surrogate().setActionCommand(command));
    }

    // --- ActionListener -----------------------------------------------

    public synchronized void addActionListener(java.awt.event.ActionListener l) {
        // AWT no-ops on null rather than throwing; EventListenerList would
        // store it and NPE at dispatch.
        if (l == null) return;
        listenerList.add(java.awt.event.ActionListener.class, l);
    }

    public synchronized void removeActionListener(java.awt.event.ActionListener l) {
        if (l == null) return;
        listenerList.remove(java.awt.event.ActionListener.class, l);
    }

    public synchronized java.awt.event.ActionListener[] getActionListeners() {
        return awtListeners(java.awt.event.ActionListener.class);
    }

    @Override
    protected void processEvent(java.awt.AWTEvent e) {
        // JDK's Button.processEvent peels ActionEvent off before delegating
        // the rest to Component.processEvent. Mirrored so user code calling
        // processEvent directly from a subclass gets the AWT routing.
        if (e instanceof java.awt.event.ActionEvent ae) {
            processActionEvent(ae);
            return;
        }
        super.processEvent(e);
    }

    /**
     * The single funnel every action reaches the listeners through — both
     * the peer click bridge and a user-code {@code processEvent} call. AWT
     * subclasses override this (calling super) to intercept actions
     * wholesale, so it must stay the only dispatch path.
     */
    protected void processActionEvent(java.awt.event.ActionEvent e) {
        if (e == null) return;
        for (java.awt.event.ActionListener l
                : awtListeners(java.awt.event.ActionListener.class)) {
            l.actionPerformed(e);
        }
    }

    @Override
    protected java.lang.String paramString() {
        // JDK's Button.paramString appends ",label=<label>" to Component's
        // shape. Component.paramString returns "" here (see its comment),
        // so chaining up contributes nothing but keeps the JDK's structure.
        return super.paramString() + ",label=" + label;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("Button", "getAccessibleContext");
        return null;
    }
}
