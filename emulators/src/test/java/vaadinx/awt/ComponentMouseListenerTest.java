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

import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.event.MouseAdapter;
import vaadinx.awt.event.MouseEvent;
import vaadinx.awt.event.MouseListener;
import vaadinx.swing.JButton;
import vaadinx.swing.JFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for {@code Component.addMouseListener} — the DOM→AWT mouse bridge
 * (D_mouse_listener_bridge). Before it, the method stored the listener and
 * nothing ever fired it.
 *
 * <p>The measured JDK and DOM behaviours the bridge reproduces are the decision
 * entry's table; the tests below are named for the rows.
 */
class ComponentMouseListenerTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        Consumer<String> sink = capturedWarns::add;
        EHelper.warnHook = sink;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> { /* no-op default */ };
    }

    /** Attaches {@code c} to a visible frame, as a real app would. */
    private static void show(Component c) {
        JFrame frame = new JFrame();
        frame.add(c);
        frame.setVisible(true);
    }

    // --- DOM event dispatch -------------------------------------------------

    /**
     * Fires the DOM event the browser would send, with the payload the bridge's
     * {@code addEventData} expressions collect.
     *
     * @param hostX x relative to the peer host — what the bridge computes
     *     browser-side as {@code clientX - getBoundingClientRect().left}
     * @param button DOM button numbering (0 left, 1 middle, 2 right)
     * @param detail DOM click count; 0 for enter/leave
     */
    private static void fire(Component c, String domEvent,
                             int hostX, int hostY, int clientX, int clientY,
                             int button, int detail, boolean shift) {
        Element el = c.getPeer().getElement();
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("event.clientX - element.getBoundingClientRect().left", hostX);
        data.put("event.clientY - element.getBoundingClientRect().top", hostY);
        data.put("event.clientX", clientX);
        data.put("event.clientY", clientY);
        data.put("event.button", button);
        data.put("event.detail", detail);
        data.put("event.shiftKey", shift);
        data.put("event.ctrlKey", false);
        data.put("event.altKey", false);
        data.put("event.metaKey", false);
        el.getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(el, domEvent, data));
    }

    /** A plain left click at the host-relative point (40, 15). */
    private static void leftClick(Component c) {
        fire(c, "mousedown", 40, 15, 140, 115, 0, 1, false);
        fire(c, "mouseup", 40, 15, 140, 115, 0, 1, false);
        fire(c, "click", 40, 15, 140, 115, 0, 1, false);
    }

    private static List<String> recordInto(Component c, List<String> log) {
        c.addMouseListener(new MouseListener() {
            @Override public void mouseClicked(MouseEvent e) { log.add("clicked"); }
            @Override public void mousePressed(MouseEvent e) { log.add("pressed"); }
            @Override public void mouseReleased(MouseEvent e) { log.add("released"); }
            @Override public void mouseEntered(MouseEvent e) { log.add("entered"); }
            @Override public void mouseExited(MouseEvent e) { log.add("exited"); }
        });
        return log;
    }

    // --- The idiom that was dead ------------------------------------------

    @Test
    @DisplayName("a JLabel's MouseAdapter fires — the ActionButton idiom that was dead storage")
    void jLabelMouseAdapterFires() {
        JLabel label = new JLabel("Stock Query");
        show(label);
        List<String> log = new ArrayList<>();
        label.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { log.add("navigate"); }
            @Override public void mouseEntered(MouseEvent e) { log.add("highlight"); }
            @Override public void mouseExited(MouseEvent e) { log.add("unhighlight"); }
        });

        fire(label, "mouseenter", 20, 20, 120, 120, 0, 0, false);
        leftClick(label);
        fire(label, "mouseleave", 200, 300, 300, 400, 0, 0, false);

        assertEquals(List.of("highlight", "navigate", "unhighlight"), log);
    }

    // --- Ordering + ids ---------------------------------------------------

    @Test
    @DisplayName("callback order is entered, pressed, released, clicked — clicked LAST")
    void callbackOrderMatchesAwt() {
        JButton b = new JButton("target");
        show(b);
        List<String> log = recordInto(b, new ArrayList<>());

        fire(b, "mouseenter", 40, 15, 140, 115, 0, 0, false);
        leftClick(b);

        assertEquals(List.of("entered", "pressed", "released", "clicked"), log);
    }

    @Test
    @DisplayName("each DOM event maps to its AWT id, and the source is the emulator")
    void domEventsMapToAwtIds() {
        JPanel p = new JPanel();
        show(p);
        List<MouseEvent> events = new ArrayList<>();
        p.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { events.add(e); }
            @Override public void mouseReleased(MouseEvent e) { events.add(e); }
            @Override public void mouseClicked(MouseEvent e) { events.add(e); }
            @Override public void mouseEntered(MouseEvent e) { events.add(e); }
            @Override public void mouseExited(MouseEvent e) { events.add(e); }
        });

        fire(p, "mouseenter", 1, 1, 1, 1, 0, 0, false);
        leftClick(p);
        fire(p, "mouseleave", 1, 1, 1, 1, 0, 0, false);

        assertEquals(List.of(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_PRESSED,
                        MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED,
                        MouseEvent.MOUSE_EXITED),
                events.stream().map(MouseEvent::getID).toList());
        // R_swing_is_truth: user code casts getSource() to its own component.
        events.forEach(e -> assertSame(p, e.getSource()));
    }

    // --- Event payload ----------------------------------------------------

    @Test
    @DisplayName("getX/getY are host-relative; getXOnScreen/getYOnScreen carry clientX/clientY")
    void coordinatesAreHostRelative() {
        JButton b = new JButton("target");
        show(b);
        List<MouseEvent> events = new ArrayList<>();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { events.add(e); }
        });

        fire(b, "click", 40, 15, 703, 444, 0, 1, false);

        MouseEvent e = assertSingle(events);
        assertEquals(40, e.getX());
        assertEquals(15, e.getY());
        assertEquals(703, e.getXOnScreen());
        assertEquals(444, e.getYOnScreen());
    }

    @Test
    @DisplayName("DOM button 0/1/2 maps to BUTTON1/2/3")
    void domButtonNumbersMapToAwt() {
        JButton b = new JButton("target");
        show(b);
        List<MouseEvent> events = new ArrayList<>();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { events.add(e); }
        });

        fire(b, "mousedown", 1, 1, 1, 1, 0, 1, false);
        fire(b, "mousedown", 1, 1, 1, 1, 1, 1, false);
        fire(b, "mousedown", 1, 1, 1, 1, 2, 1, false);

        assertEquals(List.of(MouseEvent.BUTTON1, MouseEvent.BUTTON2, MouseEvent.BUTTON3),
                events.stream().map(MouseEvent::getButton).toList());
    }

    @Test
    @DisplayName("isPopupTrigger is true only on the button-3 press, as on X11")
    void popupTriggerRidesTheRightButtonPress() {
        JButton b = new JButton("target");
        show(b);
        List<MouseEvent> events = new ArrayList<>();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { events.add(e); }
            @Override public void mouseReleased(MouseEvent e) { events.add(e); }
        });

        fire(b, "mousedown", 1, 1, 1, 1, 2, 1, false);
        fire(b, "mouseup", 1, 1, 1, 1, 2, 1, false);
        fire(b, "mousedown", 1, 1, 1, 1, 0, 1, false);

        assertEquals(List.of(true, false, false),
                events.stream().map(MouseEvent::isPopupTrigger).toList());
    }

    @Test
    @DisplayName("click count comes from DOM detail, escalating 1-then-2 like AWT")
    void clickCountEscalates() {
        JButton b = new JButton("target");
        show(b);
        List<Integer> counts = new ArrayList<>();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { counts.add(e.getClickCount()); }
        });

        fire(b, "click", 1, 1, 1, 1, 0, 1, false);
        fire(b, "click", 1, 1, 1, 1, 0, 2, false);

        assertEquals(List.of(1, 2), counts);
    }

    @Test
    @DisplayName("modifier keys map to the *_DOWN_MASK bits")
    void modifiersMapToDownMasks() {
        JButton b = new JButton("target");
        show(b);
        List<MouseEvent> events = new ArrayList<>();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { events.add(e); }
        });

        fire(b, "click", 1, 1, 1, 1, 0, 1, /*shift*/ true);

        MouseEvent e = assertSingle(events);
        assertTrue(e.isShiftDown());
        assertFalse(e.isControlDown());
        assertFalse(e.isAltDown());
        assertFalse(e.isMetaDown());
    }

    @Test
    @DisplayName("a release landing elsewhere yields no MOUSE_CLICKED — the browser simply sends none")
    void releaseOffTargetYieldsNoClick() {
        JButton b = new JButton("target");
        show(b);
        List<String> log = recordInto(b, new ArrayList<>());

        fire(b, "mousedown", 40, 15, 140, 115, 0, 1, false);
        fire(b, "mouseleave", 900, 900, 900, 900, 0, 0, false);

        assertEquals(List.of("pressed", "exited"), log);
    }

    // --- Registration lifecycle -------------------------------------------

    @Test
    @DisplayName("a removed listener stops receiving, and the getter round-trips")
    void removedListenerStopsReceiving() {
        JButton b = new JButton("target");
        show(b);
        List<String> log = new ArrayList<>();
        MouseListener l = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { log.add("clicked"); }
        };
        b.addMouseListener(l);
        assertEquals(1, b.getMouseListeners().length);
        assertSame(l, b.getMouseListeners()[0]);

        leftClick(b);
        b.removeMouseListener(l);
        assertEquals(0, b.getMouseListeners().length);
        leftClick(b);

        assertEquals(List.of("clicked"), log);
    }

    @Test
    @DisplayName("two listeners both fire, in AWT's registration order")
    void twoListenersFireInRegistrationOrder() {
        JButton b = new JButton("target");
        show(b);
        List<String> log = new ArrayList<>();
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { log.add("first"); }
        });
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { log.add("second"); }
        });

        fire(b, "click", 1, 1, 1, 1, 0, 1, false);

        assertEquals(List.of("first", "second"), log);
    }

    @Test
    @DisplayName("addMouseListener registers WARN-free")
    void addMouseListenerIsWarnFree() {
        JButton b = new JButton("target");
        show(b);
        capturedWarns.clear();
        b.addMouseListener(new MouseAdapter() { });
        leftClick(b);
        assertEquals(List.of(), capturedWarns);
    }

    // --- No double-fire on the three that source their own events ---------

    /**
     * The generic bridge must stay uninstalled on {@code JList} / {@code JTable}
     * / {@code JTree}, which re-source their surrogate's Grid item-click wire.
     * Their own wire is covered by {@code JListTest} / {@code JTableTest} /
     * {@code JTreeTest}; what would go unnoticed is this bridge *also* firing,
     * so a DOM click on the peer element must deliver nothing here.
     */
    @Test
    @DisplayName("JList/JTable/JTree suppress the generic bridge, so a click is not delivered twice")
    void gridComponentsSuppressTheGenericBridge() {
        List<Component> grids = List.of(
                new vaadinx.swing.JList<>(new String[] {"a", "b"}),
                new vaadinx.swing.JTable(new Object[][] {{"a"}}, new Object[] {"col"}),
                new vaadinx.swing.JTree());

        for (Component grid : grids) {
            show(grid);
            List<String> log = recordInto(grid, new ArrayList<>());
            leftClick(grid);
            assertEquals(List.of(), log,
                    () -> grid.getClass().getSimpleName() + " must not deliver via the generic bridge");
        }
    }

    // --- The two honest declines ------------------------------------------

    @Test
    @DisplayName("addMouseMotionListener WARNs at registration and still round-trips")
    void motionListenerWarnsAtRegistration() {
        JButton b = new JButton("target");
        show(b);
        capturedWarns.clear();
        vaadinx.awt.event.MouseMotionListener l = new vaadinx.awt.event.MouseMotionAdapter() { };
        b.addMouseMotionListener(l);

        assertTrue(assertSingle(capturedWarns).contains("addMouseMotionListener"),
                () -> "expected an addMouseMotionListener WARN, got " + capturedWarns);
        assertEquals(1, b.getMouseMotionListeners().length);
        assertSame(l, b.getMouseMotionListeners()[0]);
    }

    @Test
    @DisplayName("addMouseWheelListener WARNs at registration and still round-trips")
    void wheelListenerWarnsAtRegistration() {
        JButton b = new JButton("target");
        show(b);
        capturedWarns.clear();
        vaadinx.awt.event.MouseWheelListener l = e -> { };
        b.addMouseWheelListener(l);

        assertTrue(assertSingle(capturedWarns).contains("addMouseWheelListener"),
                () -> "expected an addMouseWheelListener WARN, got " + capturedWarns);
        assertEquals(1, b.getMouseWheelListeners().length);
        assertSame(l, b.getMouseWheelListeners()[0]);
    }
}
