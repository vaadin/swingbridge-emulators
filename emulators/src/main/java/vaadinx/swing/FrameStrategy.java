/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import com.vaadin.flow.component.Component;

/**
 * Strategy for {@link JFrame}'s rendering shape — picked once at ctor
 * time from the {@link MainWindow} annotation, pinned for the instance's
 * lifetime per
 * <a href="../../../emulators/decisions.md#D_frame_strategy">D_frame_strategy</a>.
 *
 * <p>Two impls: {@link DialogStrategy} (regular JFrame, Dialog peer) and
 * {@link InlineStrategy} (@MainWindow JFrame, Div peer
 * attached to a {@link vaadinx.swing.app.MainWindowRoute}).
 *
 * <p>Both strategies are stateless singletons — per-frame state lives on
 * the {@link JFrame} instance. The interface stays package-private:
 * R_leaf_peer_lockdown's intent (user code can't swap peers) is preserved by not exposing
 * the strategy's seam outside this package.
 */
interface FrameStrategy {
    /** What {@link #createPeer()} returns, which answers the frame's peer type checks before it exists. */
    Class<? extends Component> peerType();

    /**
     * Vaadin peer to host the JFrame instance: the factory JFrame's private ctor passes up
     * the lazy {@code super(peerType, peerFactory)} chain, so it runs once, on the UI thread.
     */
    Component createPeer();

    /**
     * Apply visibility transition. {@code b == true}: attach to the
     * containing surface (Dialog open / route-div add) + first-show
     * routing if applicable. {@code b == false}: detach (Dialog close /
     * route-div remove). Higher-level event firing (COMPONENT_SHOWN/HIDDEN,
     * WINDOW_OPENED) lives in {@link vaadinx.awt.Window#show()} /
     * {@link vaadinx.awt.Window#hide()} and is the same for both strategies —
     * only the peer-side write differs. No {@code "visible"} property change:
     * {@code JPopupMenu} is the only class in java.awt + javax.swing that fires
     * one (R_decline_effect_only).
     */
    void applyVisibleToPeer(JFrame f, boolean b);

    /**
     * Tear down the peer in response to {@link JFrame#dispose()}. The
     * subsequent WINDOW_CLOSED fire is owned by
     * {@link vaadinx.awt.Window#dispose()} (the same for both strategies);
     * this hook only owns the peer-side detach.
     */
    void disposePeer(JFrame f);

    /**
     * Apply the title to the strategy-specific surface. DialogStrategy
     * relies on the inherited {@link vaadinx.awt.Frame#setTitle(String)}
     * to push to {@link com.vaadin.swingbridge.surrogates.SFrame} (Dialog header);
     * InlineStrategy writes the browser tab title via
     * {@code UI.getCurrent().getPage().setTitle(s)}. Called from
     * {@link JFrame#setTitle(String)} after the field shadow + PCE.
     */
    void afterTitleSet(JFrame f, String title);

    /**
     * Apply the {@link javax.swing.WindowConstants} default-close-operation
     * to a peer-originated WINDOW_CLOSING event (after user listeners ran).
     * DialogStrategy throws on {@code EXIT_ON_CLOSE} per
     * <a href="../../../emulators/decisions.md#D_gap_severity_triage">D_gap_severity_triage</a>
     * case 2; InlineStrategy disposes, and the session then ends through
     * {@link vaadinx.AutoShutdown} like any other last-window dispose
     * (D_auto_shutdown). Other ops (DISPOSE / HIDE / DO_NOTHING) follow the
     * standard mapping in both.
     */
    void handleClosing(JFrame f, int defaultCloseOperation);

    // No afterDispose hook: a dispose ends the app when it takes the last displayable
    // window with it, under every close operation as the JDK does (D_auto_shutdown), so
    // nothing strategy-specific is left to do after one.

    // No applyMenuBar hook: the menu bar is not strategy-specific any more.
    // JFrame.setJMenuBar delegates into JRootPane, which plants the bar in its
    // layered pane — the JDK's own containment — so both strategies get the same
    // [menu bar, content pane] chain from the same code, and neither peer needs a
    // slot of its own.
}
