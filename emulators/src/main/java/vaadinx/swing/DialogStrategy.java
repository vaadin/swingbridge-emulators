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
import com.vaadin.swingbridge.surrogates.SJFrame;
import com.vaadin.swingbridge.surrogates.SWindow;

import javax.swing.WindowConstants;

/**
 * The regular-JFrame shape: peer is a {@link SJFrame} (extends
 * Vaadin Dialog), so {@link vaadinx.awt.Window#show()} /
 * {@link vaadinx.awt.Window#hide()} and {@link vaadinx.awt.Window#dispose()}'s
 * default hooks already do the
 * right thing, and {@link vaadinx.awt.Frame#setTitle(String)} already
 * pushes to {@link com.vaadin.swingbridge.surrogates.SFrame} for the Dialog header.
 *
 * <p>Per
 * <a href="../../../emulators/decisions.md#D_frame_strategy">D_frame_strategy</a>
 * — the strategy is an empty stateless singleton; all behaviour falls
 * through to the inherited Window/Frame logic.
 */
final class DialogStrategy implements FrameStrategy {
    static final DialogStrategy INSTANCE = new DialogStrategy();

    private DialogStrategy() { }

    @Override
    public Class<SJFrame> peerType() {
        return SJFrame.class;
    }

    @Override
    public Component createPeer() {
        return new SJFrame();
    }

    @Override
    public void applyVisibleToPeer(JFrame f, boolean b) {
        // Standard Window default — delegate to SWindow.setVisible.
        if (f.peer() instanceof SWindow sw) {
            sw.setVisible(b);
        } else {
            f.peer().setVisible(b);
        }
    }

    @Override
    public void disposePeer(JFrame f) {
        f.withPeer(p -> {
            if (p instanceof SWindow sw) sw.dispose();
        });
    }

    @Override
    public void afterTitleSet(JFrame f, String title) {
        // Frame.setTitle already pushed to SFrame (the Dialog header) via
        // its own peer-write — nothing to do here.
    }

    @Override
    public void handleClosing(JFrame f, int defaultCloseOperation) {
        // Per D_gap_severity_triage case 2: peer-originated close-attempt on
        // EXIT_ON_CLOSE throws. The throw also fires from SJFrame.processWindowEvent
        // on the surrogate side (the Dialog peer's own close gesture
        // routes through there); this emulator-side throw covers the
        // path from {@link vaadinx.awt.Window#onPeerOpenedChanged} →
        // emulator processWindowEvent.
        switch (defaultCloseOperation) {
            case WindowConstants.DISPOSE_ON_CLOSE -> f.dispose();
            case WindowConstants.HIDE_ON_CLOSE,
                 WindowConstants.DO_NOTHING_ON_CLOSE -> { /* no-op */ }
            case WindowConstants.EXIT_ON_CLOSE -> throw new IllegalStateException(
                    "EXIT_ON_CLOSE close-attempt on " + f
                            + " — Swing would terminate the JVM here. "
                            + "a Vaadin server can't honour that intent silently; "
                            + "switch to DISPOSE_ON_CLOSE / HIDE_ON_CLOSE, or annotate the frame "
                            + "with @MainWindow so EXIT_ON_CLOSE maps to session-close per D_close_operation_dispatch. "
                            + "See emulators/decisions.md D_gap_severity_triage + D_jframe_as_route.");
            default -> { /* unreachable (setter validates), drop */ }
        }
    }

}
