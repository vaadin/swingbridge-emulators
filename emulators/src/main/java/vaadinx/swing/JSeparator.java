/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JSeparator
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JSeparator (D_jseparator / SD_sjseparator).
// The orientation is the JDK's field and setOrientation its body; the SJSeparator
// peer is told the current value and never read back.
//
// Dual rendering path:
//
//  - Inside a JMenu / JMenuBar, the rebuild walk produces a MenuNode with
//    separator=true which translates to subMenu.addSeparator() at submenu
//    level and drop-and-WARNs at the top level (Vaadin MenuBar doesn't
//    expose a top-level separator API). The JSeparator's own peer is never
//    attached in this path — JMenu stores menu children in its own list
//    (not via Container.addImpl), so the SJSeparator never reaches the DOM
//    and its orientation CSS is harmless.
//
//  - Outside menus (toolbars, dialog form rows, panels), the JSeparator
//    flows through Container.addImpl like any other component and its
//    SJSeparator peer lands as a flex item in the parent layout's content
//    element, painted as a thin Lumo-toned bar (see SJSeparator).
//
// Peer: every ctor ends in super(new SJSeparator(...)) and there is no protected
// (Component peer) ctor. JSeparator is not a leaf in the public Swing hierarchy —
// JToolBar.Separator (nested in JToolBar) and JPopupMenu.Separator extend it — but
// neither wants a different peer, so the R_leaf_peer_lockdown seam stays closed.

/** Emulator for {@link javax.swing.JSeparator}. See D_jseparator / SD_sjseparator. */
public class JSeparator extends vaadinx.swing.JComponent implements javax.swing.SwingConstants, javax.accessibility.Accessible {

    private int orientation = HORIZONTAL;

    public JSeparator() {
        this(HORIZONTAL);
    }

    /**
     * @throws IllegalArgumentException if {@code orientation} is neither
     *         {@code HORIZONTAL} nor {@code VERTICAL}, as in the JDK
     */
    public JSeparator(int orientation) {
        super(com.vaadin.swingbridge.surrogates.SJSeparator.class,
                () -> new com.vaadin.swingbridge.surrogates.SJSeparator(orientation));
        checkOrientation(orientation);
        this.orientation = orientation;
        setFocusable(false);
        updateUI();
    }

    private com.vaadin.swingbridge.surrogates.SJSeparator surrogate() {
        return (com.vaadin.swingbridge.surrogates.SJSeparator) getPeer();
    }

    public int getOrientation() {
        return this.orientation;
    }

    /**
     * @throws IllegalArgumentException if {@code orientation} is neither
     *         {@code HORIZONTAL} nor {@code VERTICAL}; as in the JDK the check runs
     *         after the equality guard, so it never fires on the current value
     */
    public void setOrientation(int orientation) {
        if (this.orientation == orientation) {
            return;
        }
        int oldValue = this.orientation;
        checkOrientation(orientation);
        this.orientation = orientation;
        firePropertyChange("orientation", oldValue, orientation);
        revalidate();
        repaint();
        withPeer(p -> surrogate().setOrientation(this.orientation));
    }

    /** The JDK's check, returning its argument so a constructor can run it before {@code super}. */
    private static int checkOrientation(int orientation) {
        switch (orientation) {
            case VERTICAL:
            case HORIZONTAL:
                break;
            default:
                throw new IllegalArgumentException("orientation must be one of: VERTICAL, HORIZONTAL");
        }
        return orientation;
    }

    public java.lang.String getUIClassID() {
        return "SeparatorUI";
    }

    public void updateUI() {
        // L&F swap — no-op.
    }

    protected java.lang.String paramString() {
        java.lang.String orientationString = (orientation == HORIZONTAL ? "HORIZONTAL" : "VERTICAL");
        return super.paramString() + ",orientation=" + orientationString;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JSeparator", "getAccessibleContext");
        return null;
    }
}
