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

package vaadinx.awt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.JButton;
import vaadinx.swing.JDialog;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;
import vaadinx.swing.JSplitPane;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D_container_enabled_no_cascade — a disabled container must not disable its
 * subtree, because Flow's disable cascades and Swing's does not.
 */
class ContainerEnabledCascadeTest extends AbstractKaribuTest {

    /**
     * What the browser sees: Flow's effective state, which walks the ancestors.
     * The emulator's own {@code isEnabled()} cannot answer this — it is exactly
     * the disagreement between the two that this class is about.
     */
    private static boolean liveInDom(Component c) {
        return c.getPeer().getElement().getNode().isEnabled();
    }

    @Test
    @DisplayName("re-enabled children of a disabled container are live in the DOM")
    void reEnabledChildrenOfDisabledContainerAreLiveInDom() {
        // The stock Swing form idiom, and the one that killed every button in
        // the inventory testapp: disable the panel and all of its children,
        // then re-enable the few that stay usable.
        JPanel panel = new JPanel();
        JButton readAll = new JButton("Read all");
        JButton save = new JButton("Save");
        panel.add(readAll);
        panel.add(save);

        panel.setEnabled(false);
        readAll.setEnabled(false);
        save.setEnabled(false);
        readAll.setEnabled(true);

        assertTrue(liveInDom(readAll));
        assertFalse(liveInDom(save));
    }

    @Test
    @DisplayName("a child added after the container was disabled is live in the DOM")
    void childAddedAfterDisableIsLiveInDom() {
        // The case a snapshot taken at setEnabled time gets wrong: the panel
        // was childless then, so it did push its disable to the peer. Without
        // Container.addImpl lifting it, this button is dead with no way back.
        JPanel panel = new JPanel();
        panel.setEnabled(false);

        JButton late = new JButton("New");
        panel.add(late);

        assertTrue(liveInDom(late));
        assertFalse(panel.isEnabled());
    }

    @Test
    @DisplayName("a disabled container records the state without touching its children")
    void disabledContainerRecordsStateWithoutTouchingChildren() {
        JPanel panel = new JPanel();
        JButton child = new JButton("Save");
        panel.add(child);
        List<PropertyChangeEvent> events = new ArrayList<>();
        panel.addPropertyChangeListener(events::add);

        panel.setEnabled(false);

        // Not pushing must not decay into not recording: isEnabled() stays the
        // honest Swing answer and JComponent still fires its bound property.
        assertFalse(panel.isEnabled());
        assertTrue(child.isEnabled());
        assertTrue(liveInDom(child));
        assertEquals(1, events.stream().filter(e -> "enabled".equals(e.getPropertyName())).count());
    }

    @Test
    @DisplayName("a slot child added after the container was disabled is live in the DOM")
    void slotChildAddedAfterDisableIsLiveInDom() {
        // The second insertion path: setLeftComponent goes through addSlotChild,
        // not addImpl, because SplitLayout owns its own child DOM. Same lift is
        // needed, and only this test notices when it is missing.
        JSplitPane split = new JSplitPane();
        split.setEnabled(false);

        JButton late = new JButton("Save");
        split.setLeftComponent(late);

        assertTrue(liveInDom(late));
        assertFalse(split.isEnabled());
    }

    @Test
    @DisplayName("a populated container's own peer is never disabled, so re-enabling undoes nothing")
    void populatedContainersOwnPeerIsNeverDisabled() {
        JPanel panel = new JPanel();
        JButton child = new JButton("Save");
        panel.add(child);

        panel.setEnabled(false);
        // The container's own element, not just its children: the disable does
        // not reach the peer at all, which is what leaves nothing to undo.
        assertTrue(liveInDom(panel));

        panel.setEnabled(true);
        assertTrue(panel.isEnabled());
        assertTrue(liveInDom(panel));
        assertTrue(liveInDom(child));
    }

    @Test
    @DisplayName("nested disabled containers still let a leaf be re-enabled")
    void nestedDisabledContainersStillLetALeafBeReEnabled() {
        // Real forms nest; the inventory case was one level deep.
        JPanel outer = new JPanel();
        JPanel inner = new JPanel();
        JButton child = new JButton("Save");
        inner.add(child);
        outer.add(inner);

        outer.setEnabled(false);
        inner.setEnabled(false);
        child.setEnabled(false);
        child.setEnabled(true);

        assertTrue(liveInDom(child));
    }

    @Test
    @DisplayName("a child of a disabled JDialog IS dead in the DOM")
    void childOfDisabledDialogIsDeadInDom() {
        // The Window carve-out again, through the other peer shape: per
        // D_frame_strategy a JFrame may peer on an SJPanel, while a JDialog
        // always peers on a Vaadin Dialog.
        JDialog dialog = new JDialog();
        JButton child = new JButton("Save");
        dialog.getContentPane().add(child);

        dialog.setEnabled(false);

        assertFalse(liveInDom(child));
    }

    @Test
    @DisplayName("a child of a disabled JFrame IS dead in the DOM")
    void childOfDisabledFrameIsDeadInDom() {
        // The one row that goes the other way, and therefore the one a later
        // "simplification" of Window.pushesEnabledToPeer will delete. Measured
        // on JDK 25: a disabled frame delivers nothing to its children, not
        // even MouseListener callbacks, so Flow's cascade is faithful here.
        JFrame frame = new JFrame();
        JButton child = new JButton("Save");
        frame.getContentPane().add(child);

        frame.setEnabled(false);

        assertFalse(frame.isEnabled());
        assertTrue(child.isEnabled());   // AWT does not cascade the flag, even here
        assertFalse(liveInDom(child));
    }
}
