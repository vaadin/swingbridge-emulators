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
 * This file is derived from OpenJDK's java.awt.Panel
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator — the AWT lane's first general-purpose Container,
// and by far its smallest class: java.awt.Panel adds a default FlowLayout,
// an Accessible marker and an addNotify override to java.awt.Container, and
// nothing else. vaadinx.awt.Container already implements the rest, so almost
// all of this file is the reasoning for why it stays four lines rather than
// growing an SPanel nobody needs. Rationale: D_awt_panel / SD_no_spanel; the lane:
// D_awt_lane.

/**
 * Emulator for {@link java.awt.Panel} — AWT's generic on-screen container,
 * not {@link vaadinx.swing.JPanel}. Peers over a plain Vaadin
 * {@code <div>} (inherited from {@link vaadinx.awt.Container}), which is
 * what every built-in {@link vaadinx.awt.LayoutManager} needs: the CSS
 * emitters refuse to write to a non-{@code <div>} host.
 *
 * <pre>{@code
 * Panel form = new Panel(new BorderLayout(8, 4));
 * form.add(new Label("Name"), BorderLayout.NORTH);
 * form.add(field, BorderLayout.CENTER);
 * outer.add(form);                 // FlowLayout by default on `outer`
 * }</pre>
 *
 * <p>Everything a migrator calls on a Panel is inherited
 * {@link vaadinx.awt.Container}: {@code add} and its four overloads,
 * {@code remove} / {@code removeAll}, {@code setLayout} / {@code getLayout},
 * {@code getComponent(int)} / {@code getComponents()} /
 * {@code getComponentCount()}, {@code getInsets}, {@code validate} /
 * {@code doLayout} / {@code invalidate}, {@code isAncestorOf}, Z-order, and
 * {@code ContainerListener} — which this layer fires for real, unlike a
 * pure-surrogate container (SD_listeners_without_analog).
 *
 * <p>There is deliberately no {@code SPanel} surrogate: subtract
 * {@code SJPanel}'s Swing half (border, opaque, client properties, L&amp;F)
 * and nothing is left for one to hold — the child list belongs to Vaadin's
 * {@code HasComponents}, the layout manager to {@code LayoutStore}. Per
 * SD_no_spanel this is CLAUDE.md's group-2 shape (a structural shell on a bare
 * Vaadin element), the carve-out against D_surrogate_first's surrogate-first default.
 *
 * <p>Leaf peer is locked down per R_leaf_peer_lockdown. {@code java.applet.Applet} is
 * {@code java.awt.Panel}'s only JDK subclass, and applets are permanently
 * out of scope (R_match_swing_errors sub-bucket (b)) — the JDK classes are removed in Java 26
 * per JEP 504, so unlike {@code javax.swing.plaf} this subclass cannot come
 * back as future non-leaf pressure. Both ctors funnel through
 * {@code super()}, so every instance — including a user-code subclass —
 * peers over a {@code Div} and no ctor offers a peer seam.
 *
 * <p>A layout installed after the Panel is on screen does not take
 * effect until {@code validate()} / {@code revalidate()}, matching
 * AWT — {@code setLayout} only invalidates. The outgoing manager's
 * CSS *is* dropped at {@code setLayout}, so the un-revalidated
 * interval renders as plain block flow rather than the old
 * layout's; see {@code Container#setLayout}.
 */
public class Panel extends vaadinx.awt.Container implements javax.accessibility.Accessible {

    /** JDK chains this to {@code this(new FlowLayout())} — centered, 5px gaps. */
    public Panel() {
        this(new vaadinx.awt.FlowLayout());
    }

    /**
     * @param layout null is legal and installs no manager, exactly as in AWT —
     *               children then render in the peer {@code <div>}'s default
     *               block flow and {@code getLayout()} returns null. Do not
     *               substitute a {@link vaadinx.awt.FlowLayout} here.
     */
    public Panel(vaadinx.awt.LayoutManager layout) {
        // super() is Container's no-arg ctor, which hard-codes the Div peer —
        // that IS the R_leaf_peer_lockdown lock (the lane's usual `super(new SXxx())` spelling
        // has no surrogate to name here, and naming `new Div()` would read as
        // a peer choice being made at this level rather than inherited).
        //
        // setLayout is called virtually, as the JDK's own Panel(LayoutManager)
        // does — so a user override runs during construction and sees its own
        // fields uninitialized. The footgun is inherited along with the shape,
        // the same stance D_r12_provenance took for createDefaultModel.
        super();
        setLayout(layout);
    }

    @Override
    public void addNotify() {
        // JDK allocates the native peer here. Ours is eternal and built in the
        // ctor, so there is nothing to allocate — but the override is kept
        // rather than dropped: overriding addNotify() and chaining to super is
        // one of the commonest AWT-era idioms, and Container's implementation
        // is what runs doLayout() so a lazily-built subtree lays itself out on
        // attach.
        super.addNotify();
    }

    /**
     * @return always null — accessibility contexts are permanently out of
     *         scope (R_match_swing_errors sub-bucket (b)), as on {@link vaadinx.awt.Button} /
     *         {@link vaadinx.awt.Label} / {@link vaadinx.swing.JPanel}
     */
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("Panel", "getAccessibleContext");
        return null;
    }
}
