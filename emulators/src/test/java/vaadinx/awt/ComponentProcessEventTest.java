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

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.event.ComponentEvent;
import vaadinx.awt.event.ComponentListener;
import vaadinx.awt.event.ContainerEvent;
import vaadinx.awt.event.ContainerListener;
import vaadinx.awt.event.FocusEvent;
import vaadinx.awt.event.FocusListener;
import vaadinx.awt.event.HierarchyBoundsListener;
import vaadinx.awt.event.HierarchyEvent;
import vaadinx.awt.event.InputMethodEvent;
import vaadinx.awt.event.InputMethodListener;
import vaadinx.awt.event.KeyEvent;
import vaadinx.awt.event.KeyListener;
import vaadinx.awt.event.MouseEvent;
import vaadinx.awt.event.MouseListener;
import vaadinx.awt.event.MouseMotionListener;
import vaadinx.awt.event.MouseWheelEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Component.processEvent is AWT's dispatch sink: one entry, many listener
 * families. Our usual path fires each process*Event directly from a
 * fire*Event helper (D_own_awt_package — no EventQueue), but subclasses can still reach
 * this method via dispatchEvent / super.processEvent overrides, so the
 * routing contract must hold. Container.processEvent intercepts
 * ContainerEvent before super is called; everything else flows through
 * Component.processEvent here.
 */
class ComponentProcessEventTest extends AbstractKaribuTest {

    /** Test harness that records every listener callback and exposes processEvent. */
    private static final class RecordingComponent extends Container {
        final List<String> calls = new ArrayList<>();

        RecordingComponent() {
            addComponentListener(new ComponentListener() {
                @Override
                public void componentResized(ComponentEvent e) {
                    calls.add("component:resized");
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                    calls.add("component:moved");
                }

                @Override
                public void componentShown(ComponentEvent e) {
                    calls.add("component:shown");
                }

                @Override
                public void componentHidden(ComponentEvent e) {
                    calls.add("component:hidden");
                }
            });
            addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    calls.add("focus:gained");
                }

                @Override
                public void focusLost(FocusEvent e) {
                    calls.add("focus:lost");
                }
            });
            addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {
                    calls.add("key:typed");
                }

                @Override
                public void keyPressed(KeyEvent e) {
                    calls.add("key:pressed");
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    calls.add("key:released");
                }
            });
            addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    calls.add("mouse:clicked");
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    calls.add("mouse:pressed");
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    calls.add("mouse:released");
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    calls.add("mouse:entered");
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    calls.add("mouse:exited");
                }
            });
            addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    calls.add("motion:dragged");
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    calls.add("motion:moved");
                }
            });
            addMouseWheelListener(e -> calls.add("wheel:moved"));
            addHierarchyListener(e -> calls.add("hierarchy:changed"));
            addHierarchyBoundsListener(new HierarchyBoundsListener() {
                @Override
                public void ancestorMoved(HierarchyEvent e) {
                    calls.add("hierarchy:ancestor-moved");
                }

                @Override
                public void ancestorResized(HierarchyEvent e) {
                    calls.add("hierarchy:ancestor-resized");
                }
            });
            addInputMethodListener(new InputMethodListener() {
                @Override
                public void inputMethodTextChanged(InputMethodEvent e) {
                    calls.add("im:text-changed");
                }

                @Override
                public void caretPositionChanged(InputMethodEvent e) {
                    calls.add("im:caret-changed");
                }
            });
            addContainerListener(new ContainerListener() {
                @Override
                public void componentAdded(ContainerEvent e) {
                    calls.add("container:added");
                }

                @Override
                public void componentRemoved(ContainerEvent e) {
                    calls.add("container:removed");
                }
            });
        }

        void dispatch(java.awt.AWTEvent e) {
            processEvent(e);
        }
    }

    @Test
    @DisplayName("ComponentEvent routes to processComponentEvent")
    void componentEventRoutesToProcessComponentEvent() {
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new ComponentEvent(c, ComponentEvent.COMPONENT_RESIZED));
        assertEquals(List.of("component:resized"), c.calls);
    }

    @Test
    @DisplayName("FocusEvent routes to processFocusEvent")
    void focusEventRoutesToProcessFocusEvent() {
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new FocusEvent(c, FocusEvent.FOCUS_GAINED));
        assertEquals(List.of("focus:gained"), c.calls);
    }

    @Test
    @DisplayName("KeyEvent routes to processKeyEvent")
    void keyEventRoutesToProcessKeyEvent() {
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new KeyEvent(c, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a'));
        assertEquals(List.of("key:pressed"), c.calls);
    }

    @Test
    @DisplayName("non-motion MouseEvent routes to processMouseEvent")
    void nonMotionMouseEventRoutesToProcessMouseEvent() {
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new MouseEvent(c, MouseEvent.MOUSE_CLICKED, 0L, 0, 0, 0, 1, false));
        assertEquals(List.of("mouse:clicked"), c.calls);
    }

    @Test
    @DisplayName("MOUSE_MOVED routes to processMouseMotionEvent, not processMouseEvent")
    void mouseMovedRoutesToProcessMouseMotionEventNotProcessMouseEvent() {
        // AWT's split: motion ids go to MouseMotionListeners only. A regression
        // that lumps all MouseEvents into processMouseEvent would double-fire
        // for motion events (MouseListener has no moved/dragged callbacks
        // anyway, but the listener's empty switch path would have to drop it).
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new MouseEvent(c, MouseEvent.MOUSE_MOVED, 0L, 0, 0, 0, 0, false));
        assertEquals(List.of("motion:moved"), c.calls);
    }

    @Test
    @DisplayName("MOUSE_DRAGGED routes to processMouseMotionEvent")
    void mouseDraggedRoutesToProcessMouseMotionEvent() {
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, 0L, 0, 0, 0, 0, false));
        assertEquals(List.of("motion:dragged"), c.calls);
    }

    @Test
    @DisplayName("MouseWheelEvent routes to processMouseWheelEvent, not mouse or motion")
    void mouseWheelEventRoutesToProcessMouseWheelEventNotMouseOrMotion() {
        // MouseWheelEvent extends MouseEvent; the wheel check must come
        // before the MouseEvent branch or wheel events would mis-route.
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, 0L, 0,
                0, 0, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1));
        assertEquals(List.of("wheel:moved"), c.calls);
    }

    @Test
    @DisplayName("InputMethodEvent routes to processInputMethodEvent")
    void inputMethodEventRoutesToProcessInputMethodEvent() {
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new InputMethodEvent(c, InputMethodEvent.CARET_POSITION_CHANGED, null, null));
        assertEquals(List.of("im:caret-changed"), c.calls);
    }

    @Test
    @DisplayName("HierarchyEvent with HIERARCHY_CHANGED routes to processHierarchyEvent")
    void hierarchyEventWithHierarchyChangedRoutesToProcessHierarchyEvent() {
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new HierarchyEvent(c, HierarchyEvent.HIERARCHY_CHANGED, c, null, 0));
        assertEquals(List.of("hierarchy:changed"), c.calls);
    }

    @Test
    @DisplayName("HierarchyEvent with ANCESTOR_MOVED routes to processHierarchyBoundsEvent")
    void hierarchyEventWithAncestorMovedRoutesToProcessHierarchyBoundsEvent() {
        // Same event class, different listener interface — id split must happen
        // at this layer so HierarchyListener and HierarchyBoundsListener
        // stay separate.
        RecordingComponent c = new RecordingComponent();
        c.dispatch(new HierarchyEvent(c, HierarchyEvent.ANCESTOR_MOVED, c, null, 0));
        assertEquals(List.of("hierarchy:ancestor-moved"), c.calls);
    }

    @Test
    @DisplayName("ContainerEvent routes to processContainerEvent via Container override")
    void containerEventRoutesToProcessContainerEventViaContainerOverride() {
        // Container.processEvent intercepts ContainerEvent before super —
        // otherwise it would fall through to the ComponentEvent branch in
        // Component.processEvent (ContainerEvent extends ComponentEvent) and
        // processComponentEvent's id switch would silently drop it.
        RecordingComponent c = new RecordingComponent();
        Component child = new Component(new Div()) {
        };
        c.dispatch(new ContainerEvent(c, ContainerEvent.COMPONENT_ADDED, child));
        assertEquals(List.of("container:added"), c.calls);
    }
}
