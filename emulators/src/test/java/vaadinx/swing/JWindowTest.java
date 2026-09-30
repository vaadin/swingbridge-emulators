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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJWindow;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.awt.Container;
import vaadinx.awt.LayoutManager;
import vaadinx.awt.event.ComponentAdapter;
import vaadinx.awt.event.ComponentEvent;
import vaadinx.awt.event.WindowAdapter;
import vaadinx.awt.event.WindowEvent;

import java.awt.Dimension;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JWindowTest extends AbstractKaribuTest {

    private static SJWindow peerOf(JWindow w) {
        return (SJWindow) w.getPeer();
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new JWindow();
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down peer is SJWindow")
    void peerIsSJWindow() {
        // R_leaf_peer_lockdown — JWindow is a leaf in the public javax.swing hierarchy;
        // every public ctor hardcodes SJWindow as the peer.
        assertInstanceOf(SJWindow.class, new JWindow().getPeer());
    }

    @Test
    @DisplayName("peer arrives undecorated, modeless, with close gestures disarmed")
    void peerDefaultsAreJWindowShaped() {
        // The JWindow-defining defaults live in SJWindow's ctor (covered
        // in depth by SJWindowTest); this asserts the emulator actually
        // wires that peer up.
        SJWindow peer = peerOf(new JWindow());
        assertTrue(peer.isUndecoratedChrome());
        assertEquals(com.vaadin.flow.component.ModalityMode.MODELESS, peer.getModality());
        assertFalse(peer.isCloseOnEsc());
        assertFalse(peer.isCloseOnOutsideClick());
    }

    // --- windowInit / content-pane redirect (mirrors JDialogTest) --------

    @Test
    @DisplayName("windowInit installs a non-null Container as content pane")
    void windowInitInstallsAContentPane() {
        assertNotNull(new JWindow().getContentPane());
    }

    @Test
    @DisplayName("rootPaneCheckingEnabled is true after windowInit")
    void rootPaneCheckingIsOn() {
        assertTrue(new JWindow().isRootPaneCheckingEnabled());
    }

    @Test
    @DisplayName("window add routes the child into the content pane")
    void addRoutesIntoTheContentPane() {
        JWindow w = new JWindow();
        Container child = new Container();
        w.add(child);
        // The window's one child is the root pane, as in Swing — the content
        // pane is two levels down, under the layered pane.
        assertSame(w.getRootPane(), assertSingle(w.getComponents()));
        assertSame(child, assertSingle(w.getContentPane().getComponents()));
        assertSame(w.getContentPane(), child.getParent());
        assertSame(w.getRootPane().getLayeredPane(), w.getContentPane().getParent());
    }

    @Test
    @DisplayName("window setLayout installs on the content pane")
    void setLayoutInstallsOnTheContentPane() {
        JWindow w = new JWindow();
        LayoutManager layout = new RecordingLayout();
        w.setLayout(layout);
        assertSame(layout, w.getContentPane().getLayout());
    }

    @Test
    @DisplayName("window remove deletes from the content pane")
    void removeDeletesFromTheContentPane() {
        JWindow w = new JWindow();
        Container child = new Container();
        w.add(child);
        w.remove(child);
        assertEquals(0, w.getContentPane().getComponents().length);
    }

    @Test
    @DisplayName("setContentPane replaces the pane and detaches the old one")
    void setContentPaneReplacesAndDetaches() {
        JWindow w = new JWindow();
        Container original = w.getContentPane();
        Container replacement = new Container();
        w.setContentPane(replacement);
        assertSame(replacement, w.getContentPane());
        assertNotSame(original, w.getContentPane());
        assertSame(w.getRootPane(), assertSingle(w.getComponents()));
        assertSame(replacement, assertSingle(w.getRootPane().getLayeredPane().getComponents()));
        assertNull(original.getParent());
    }

    @Test
    @DisplayName("setContentPane null throws IllegalComponentStateException")
    void setContentPaneNullThrows() {
        assertThrows(IllegalComponentStateException.class,
                () -> new JWindow().setContentPane(null));
    }

    @Test
    @DisplayName("getRootPane content pane matches getContentPane")
    void rootPaneContentPaneMatches() {
        JWindow w = new JWindow();
        assertSame(w.getContentPane(), w.getRootPane().getContentPane());
    }

    // --- Constructors ------------------------------------------------------

    @Test
    @DisplayName("constructor with Frame owner registers the owned-window link")
    void frameOwnerRegistersTheLink() {
        JFrame frame = new JFrame();
        JWindow w = new JWindow(frame);
        assertSame(frame, w.getOwner());
        assertTrue(Arrays.stream(frame.getOwnedWindows()).anyMatch(o -> o == w));
    }

    @Test
    @DisplayName("constructor with Window owner registers the owned-window link")
    void windowOwnerRegistersTheLink() {
        JWindow outer = new JWindow();
        JWindow inner = new JWindow((vaadinx.awt.Window) outer);
        assertSame(outer, inner.getOwner());
        assertTrue(Arrays.stream(outer.getOwnedWindows()).anyMatch(o -> o == inner));
    }

    @Test
    @DisplayName("no-arg constructor has null owner — no shared-owner-frame substitute")
    void noArgCtorHasNullOwner() {
        // Deliberate JDK divergence: no SwingUtilities.getSharedOwnerFrame
        // stand-in (see the ctor comment in JWindow).
        assertNull(new JWindow().getOwner());
    }

    // --- Window geometry (Window-level surface, exercised via JWindow) -----

    @Test
    @DisplayName("setLocation stores, pushes overlay top-left, fires COMPONENT_MOVED")
    void setLocationPushesAndFires() {
        JWindow w = new JWindow();
        Counter moved = new Counter();
        w.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                moved.inc();
            }
        });
        w.setLocation(40, 25);
        SJWindow peer = peerOf(w);
        assertEquals("40px", peer.getLeft());
        assertEquals("25px", peer.getTop());
        assertEquals(new Point(40, 25), w.getLocation());
        moved.assertEquals(1);
        w.setLocation(40, 25);  // no change — no re-fire
        moved.assertEquals(1);
    }

    @Test
    @DisplayName("setSize stores, pushes overlay width-height, fires COMPONENT_RESIZED")
    void setSizePushesAndFires() {
        JWindow w = new JWindow();
        Counter resized = new Counter();
        w.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resized.inc();
            }
        });
        w.setSize(400, 300);
        SJWindow peer = peerOf(w);
        assertEquals("400px", peer.getWidth());
        assertEquals("300px", peer.getHeight());
        assertEquals(new Dimension(400, 300), w.getSize());
        assertEquals(400, w.getWidth());
        assertEquals(300, w.getHeight());
        resized.assertEquals(1);
    }

    @Test
    @DisplayName("setBounds sets both and getBounds round-trips")
    void setBoundsRoundTrips() {
        JWindow w = new JWindow();
        w.setBounds(10, 20, 300, 200);
        assertEquals(new Rectangle(10, 20, 300, 200), w.getBounds());
        assertEquals(10, w.getX());
        assertEquals(20, w.getY());
    }

    @Test
    @DisplayName("setLocationRelativeTo null recenters and clears the location shadow")
    void locationRelativeToNullClearsTheShadow() {
        JWindow w = new JWindow();
        w.setLocation(40, 25);
        w.setLocationRelativeTo(null);
        SJWindow peer = peerOf(w);
        assertNull(peer.getLeft());
        assertNull(peer.getTop());
    }

    // --- Lifecycle smoke ------------------------------------------------------

    @Test
    @DisplayName("splash flow — show, then dispose, fires OPENED and CLOSED")
    void splashFlowFiresOpenedThenClosed() {
        JWindow w = new JWindow();
        List<Integer> events = new ArrayList<>();
        w.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                events.add(e.getID());
            }

            @Override
            public void windowClosed(WindowEvent e) {
                events.add(e.getID());
            }
        });
        w.add(new JLabel("Loading…"));
        w.setLocationRelativeTo(null);
        w.setSize(400, 120);
        w.setVisible(true);
        assertTrue(peerOf(w).isOpened());
        w.dispose();
        assertFalse(peerOf(w).isOpened());
        assertEquals(List.of(WindowEvent.WINDOW_OPENED, WindowEvent.WINDOW_CLOSED), events);
    }

    // --- Helpers ---------------------------------------------------------

    /** An inert LayoutManager, used only to prove setLayout lands on the content pane. */
    private static class RecordingLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, vaadinx.awt.Component comp) {
        }

        @Override
        public void removeLayoutComponent(vaadinx.awt.Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension();
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension();
        }

        @Override
        public void layoutContainer(Container parent) {
        }
    }
}
