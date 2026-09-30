/*
 * Copyright (c) 1995, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.Label
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator — second widget in the AWT lane after Button, and
// the one that shows the lane's shape is repeatable: a leaf hanging off
// vaadinx.awt.Component with no JComponent in its chain, over a surrogate
// on ComponentMixin. Rationale + the sizing of the rest of the AWT widget
// family: ideas/awt-widgets.md; decisions: D_awt_label / SD_slabel.

/**
 * Emulator for {@link java.awt.Label} — AWT 1.0 static text, not
 * {@link vaadinx.swing.JLabel}, rendered by an
 * {@link com.vaadin.swingbridge.surrogates.SLabel} peer:
 *
 * <pre>{@code
 * Label total = new Label("Total", Label.RIGHT);
 * panel.add(total, BorderLayout.NORTH);   // fills the region; text right-aligned
 * total.setText("Total: 42");
 * }</pre>
 *
 * <p>The JDK class is text plus a three-value alignment and nothing else —
 * no listeners at all. Everything a migrator touches beyond the members
 * below ({@code setBounds}, {@code setFont}, {@code setForeground},
 * {@code setVisible}, {@code addMouseListener}, …) is inherited
 * {@link vaadinx.awt.Component}.
 *
 * <p>{@code text} and {@code alignment} are the JDK's fields, and the getters
 * answer from them on any thread without reaching the peer — so
 * {@link #getText()} returns null verbatim where the peer's Vaadin-backed read
 * would say {@code ""}. Leaf peer is locked down per R_leaf_peer_lockdown: no
 * protected {@code (Component peer)} ctor, so every instance — including a
 * user-code subclass — peers over an {@code SLabel}.
 *
 * <p>The alignment constants are {@code java.awt.Label}'s own
 * (0/1/2) and collide numerically with {@code SwingConstants}'
 * LEFT/CENTER/RIGHT (2/0/4) — a value borrowed from the Swing set
 * means something else here, or throws.
 */
public class Label extends vaadinx.awt.Component implements javax.accessibility.Accessible {

    /** Left-align the label's text — the AWT default. */
    public static final int LEFT = java.awt.Label.LEFT;

    /** Center the label's text. */
    public static final int CENTER = java.awt.Label.CENTER;

    /** Right-align the label's text. */
    public static final int RIGHT = java.awt.Label.RIGHT;

    // AWT stores the text verbatim: Label(null).getText() is null. The peer
    // can't hold null (Vaadin's element text content reads back "").
    private java.lang.String text;

    private int alignment = LEFT;

    /** JDK chains this to {@code this("", LEFT)} — the text is empty, not null. */
    public Label() throws java.awt.HeadlessException {
        this("", LEFT);
    }

    public Label(java.lang.String text) throws java.awt.HeadlessException {
        this(text, LEFT);
    }

    /**
     * @param text null is legal and reads back from {@link #getText} verbatim
     * @param alignment {@link #LEFT} / {@link #CENTER} / {@link #RIGHT}
     * @throws IllegalArgumentException on any other alignment, as in AWT
     * @throws java.awt.HeadlessException never in practice — the clause is kept
     *         for signature fidelity, matching {@link vaadinx.awt.Frame}
     */
    public Label(java.lang.String text, int alignment) throws java.awt.HeadlessException {
        // R_leaf_peer_lockdown lock-down: super(...) takes the SLabel directly, no peer seam.
        // The JDK assigns the text and routes the alignment through the public
        // setAlignment, so a subclass override runs here and setText's does not.
        super(new com.vaadin.swingbridge.surrogates.SLabel(text, LEFT));
        this.text = text;
        setAlignment(alignment);
    }

    private com.vaadin.swingbridge.surrogates.SLabel surrogate() {
        return (com.vaadin.swingbridge.surrogates.SLabel) getPeer();
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

    /** @return the text as stored — null when constructed or set with null */
    public java.lang.String getText() {
        return text;
    }

    /** @param text null is stored and read back verbatim; the peer renders blank */
    public void setText(java.lang.String text) {
        // The JDK's invalidateIfValid() on a change is not reproduced
        // (R_layouts_close_enough), but its change guard is: an equal text is no write.
        boolean changed = false;
        synchronized (this) {
            if (text != this.text && (this.text == null || !this.text.equals(text))) {
                this.text = text;
                changed = true;
            }
        }
        if (changed) flushPeer();
    }

    public int getAlignment() {
        return alignment;
    }

    /**
     * @throws IllegalArgumentException on a value outside {@link #LEFT} /
     *         {@link #CENTER} / {@link #RIGHT}, as in AWT (R_match_swing_errors)
     */
    public void setAlignment(int alignment) {
        // Not a synchronized method, unlike the JDK's: the flush must run after the
        // monitor is released (see flushPeer).
        synchronized (this) {
            switch (alignment) {
                case LEFT, CENTER, RIGHT -> this.alignment = alignment;
                default -> throw new IllegalArgumentException("improper alignment: " + alignment);
            }
        }
        flushPeer();
    }

    /**
     * Pushes the current text and alignment to the peer. Runs outside this object's
     * monitor, which the JDK holds around its own peer writes: an off-thread push takes
     * the session lock, and a request thread holding that lock may be waiting on the
     * monitor. The fields are read inside the body, so racing writers leave the peer on
     * the last state.
     */
    private void flushPeer() {
        withPeer(p -> {
            java.lang.String t;
            int a;
            synchronized (this) {
                t = text;
                a = alignment;
            }
            surrogate().setText(t);
            surrogate().setAlignment(a);
        });
    }

    @Override
    protected java.lang.String paramString() {
        // Component.paramString returns "" here (see its comment), so chaining up
        // contributes nothing but keeps the JDK's structure.
        java.lang.String align = switch (alignment) {
            case LEFT -> "left";
            case CENTER -> "center";
            case RIGHT -> "right";
            default -> "";
        };
        return super.paramString() + ",align=" + align + ",text=" + text;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("Label", "getAccessibleContext");
        return null;
    }
}
