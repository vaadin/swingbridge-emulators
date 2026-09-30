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

package vaadinx.swing.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.BorderLayout;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Wires {@link AncestorListener} on a JComponent through the peer's Vaadin
 * attach / detach. Same shape as the surrogate-side
 * {@code SAncestorListener}: {@code ancestorAdded}
 * fires on Vaadin attach, {@code ancestorRemoved} on detach. Justified
 * under D_event_port_policy's "Component-related events ported wholesale" rule —
 * {@code javax.swing.event.AncestorEvent} references {@code java.awt.Container}.
 */
class AncestorListenerTest extends AbstractKaribuTest {

    /** One recorded callback: which method fired, and with what event. */
    private record Fired(String kind, AncestorEvent event) {
    }

    /** Records ancestor events in fire order — {@code null} payloads filtered upstream. */
    private static class Recorder implements AncestorListener {

        final List<Fired> events = new ArrayList<>();

        @Override
        public void ancestorAdded(AncestorEvent e) {
            events.add(new Fired("ADDED", e));
        }

        @Override
        public void ancestorRemoved(AncestorEvent e) {
            events.add(new Fired("REMOVED", e));
        }

        @Override
        public void ancestorMoved(AncestorEvent e) {
            events.add(new Fired("MOVED", e));
        }

        /** The fire order, for a whole-sequence compare. */
        List<String> kinds() {
            return events.stream().map(Fired::kind).toList();
        }
    }

    @Test
    @DisplayName("ancestorAdded fires when the listened component's peer attaches")
    void ancestorAddedFiresOnAttach() {
        // Build a JFrame with a JPanel inside; the listener is registered
        // on the panel BEFORE the frame goes visible. setVisible(true)
        // attaches the whole subtree, which fires the peer's
        // AttachListener → addNotify → fireAncestorEvent.
        JPanel panel = new JPanel();
        Recorder rec = new Recorder();
        panel.addAncestorListener(rec);

        JFrame frame = new JFrame("ancestor-test");
        frame.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);

        assertEquals(List.of("ADDED"), rec.kinds());
        Fired fired = assertSingle(rec.events);
        assertEquals("ADDED", fired.kind());
        AncestorEvent evt = fired.event();
        assertSame(panel, evt.getSource(), "source is the listened component");
        assertSame(panel, evt.getComponent());
        assertSame(panel, evt.getAncestor(), "direct attach: ancestor = self");
        assertSame(frame.getContentPane(), evt.getAncestorParent(),
                "ancestorParent = current parent at fire time");
        assertEquals(AncestorEvent.ANCESTOR_ADDED, evt.getID());
    }

    @Test
    @DisplayName("ancestorRemoved fires when the listened component is removed from the showing tree")
    void ancestorRemovedFiresOnDetach() {
        JPanel panel = new JPanel();
        Recorder rec = new Recorder();
        panel.addAncestorListener(rec);

        JFrame frame = new JFrame("ancestor-detach-test");
        frame.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);
        // Drop the ADDED event so the assertion below sees only REMOVED.
        rec.events.clear();

        frame.getContentPane().remove(panel);

        assertEquals(List.of("REMOVED"), rec.kinds());
        AncestorEvent evt = assertSingle(rec.events).event();
        assertSame(panel, evt.getSource());
        assertEquals(AncestorEvent.ANCESTOR_REMOVED, evt.getID());
    }

    @Test
    @DisplayName("ancestorMoved is never fired — accepted incompleteness")
    void ancestorMovedIsNeverFired() {
        // JDK Swing fires ancestorMoved when an ancestor's layout position
        // changes. Vaadin doesn't surface that server-side; the wiring on
        // attach/detach never produces ANCESTOR_MOVED. Mirrors the
        // surrogate-side documented limitation.
        JPanel panel = new JPanel();
        Recorder rec = new Recorder();
        panel.addAncestorListener(rec);

        JFrame frame = new JFrame("ancestor-moved-test");
        frame.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);
        // Resize / re-layout: nothing in our stack fires ANCESTOR_MOVED.
        frame.setBounds(0, 0, 400, 300);

        assertEquals(0, rec.kinds().stream().filter("MOVED"::equals).count());
    }

    @Test
    @DisplayName("removeAncestorListener stops further callbacks")
    void removeAncestorListenerStopsCallbacks() {
        JPanel panel = new JPanel();
        Recorder rec = new Recorder();
        panel.addAncestorListener(rec);
        panel.removeAncestorListener(rec);

        JFrame frame = new JFrame("ancestor-remove-test");
        frame.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);

        assertEquals(List.of(), rec.events);
    }

    @Test
    @DisplayName("getAncestorListeners returns currently-registered listeners "
            + "in JDK-standard reverse-add order")
    void getAncestorListenersIsReverseAddOrder() {
        // Swing's EventListenerList.getListeners walks the internal pair
        // array from back to front, so the most-recently-added listener
        // appears first. Match that contract — migrated code that relies
        // on the JDK ordering shouldn't see a behavior shift.
        JPanel panel = new JPanel();
        Recorder a = new Recorder();
        Recorder b = new Recorder();
        panel.addAncestorListener(a);
        panel.addAncestorListener(b);

        assertArrayEquals(new AncestorListener[] {b, a}, panel.getAncestorListeners());
    }

    @Test
    @DisplayName("getListeners(AncestorListener.class) folds in via Component.listenerList")
    void getListenersReachesAncestorStorage() {
        // Component.getListeners is the JDK reflection-style API some user
        // code uses to enumerate all listeners of a given type. Our
        // implementation routes through the shared listenerList — verify
        // that AncestorListener storage is reachable that way too, so it
        // composes with the rest of the listener machinery.
        JPanel panel = new JPanel();
        Recorder rec = new Recorder();
        panel.addAncestorListener(rec);

        assertArrayEquals(new AncestorListener[] {rec}, panel.getListeners(AncestorListener.class));
    }

    @Test
    @DisplayName("re-attach fires ANCESTOR_ADDED again")
    void reAttachFiresAddedAgain() {
        // Sampler swap shape: panel added → removed → added again. Each
        // attach must fire ANCESTOR_ADDED so a "setup-on-show / teardown-
        // on-hide" listener pattern works across multiple cycles.
        JPanel panel = new JPanel();
        Recorder rec = new Recorder();
        panel.addAncestorListener(rec);

        JFrame frame = new JFrame("ancestor-reattach-test");
        frame.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);
        frame.getContentPane().remove(panel);
        frame.getContentPane().add(panel, BorderLayout.CENTER);

        assertEquals(List.of("ADDED", "REMOVED", "ADDED"), rec.kinds());
    }
}
