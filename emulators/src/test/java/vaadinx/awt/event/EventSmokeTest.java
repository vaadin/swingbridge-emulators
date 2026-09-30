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

package vaadinx.awt.event;

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.Component;
import vaadinx.awt.Container;
import vaadinx.awt.Frame;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the ported Component-related events. Each class is a data
 * holder; we verify construction rounds fields through the public getters and
 * that the source type is our vaadinx Component (not java.awt.Component).
 *
 * <p>No test per VK_ constant — they're copy-pasted values from AWT and a single
 * identity check covers the whole batch.
 */
class EventSmokeTest extends AbstractKaribuTest {

    private Component component() {
        return new Component(new Div()) {
        };
    }

    @Test
    @DisplayName("ComponentEvent carries source and id")
    void componentEventCarriesSourceAndId() {
        Component c = component();
        ComponentEvent e = new ComponentEvent(c, ComponentEvent.COMPONENT_RESIZED);
        assertSame(c, e.getComponent());
        assertEquals(ComponentEvent.COMPONENT_RESIZED, e.getID());
    }

    @Test
    @DisplayName("InputEvent modifier accessors read bitmask")
    void inputEventModifierAccessorsReadBitmask() {
        KeyEvent e = new KeyEvent(component(), KeyEvent.KEY_PRESSED, 0L,
                InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_A, 'A');

        assertTrue(e.isShiftDown());
        assertTrue(e.isControlDown());
        assertFalse(e.isAltDown());
        assertFalse(e.isMetaDown());
        assertEquals(KeyEvent.VK_A, e.getKeyCode());
        assertEquals('A', e.getKeyChar());
    }

    @Test
    @DisplayName("KeyEvent rejects invalid KEY_TYPED combination matching AWT")
    void keyEventRejectsInvalidKeyTypedCombinationMatchingAwt() {
        // D_never_fail_on_gaps: match Swing's own failure modes for programming errors.
        assertThrows(IllegalArgumentException.class,
                () -> new KeyEvent(component(), KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_A, 'a'));
    }

    @Test
    @DisplayName("KeyEvent VK_ constants match AWT values")
    void keyEventVkConstantsMatchAwtValues() {
        // Sanity: if we ever drift from AWT's values, user code doing
        // `vaadinx.VK_ENTER == java.awt.KeyEvent.VK_ENTER` would silently break.
        assertEquals(java.awt.event.KeyEvent.VK_ENTER, KeyEvent.VK_ENTER);
        assertEquals(java.awt.event.KeyEvent.VK_F12, KeyEvent.VK_F12);
        assertEquals(java.awt.event.KeyEvent.VK_Z, KeyEvent.VK_Z);
        assertEquals(java.awt.event.KeyEvent.VK_UNDEFINED, KeyEvent.VK_UNDEFINED);
    }

    @Test
    @DisplayName("MouseEvent carries coords, button, and clickCount")
    void mouseEventCarriesCoordsButtonAndClickCount() {
        Component c = component();
        MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_CLICKED, 123L,
                InputEvent.BUTTON1_DOWN_MASK,
                /* x */ 10, /* y */ 20, /* xAbs */ 100, /* yAbs */ 200,
                /* clickCount */ 2, /* popupTrigger */ false, MouseEvent.BUTTON1);

        assertEquals(10, e.getX());
        assertEquals(20, e.getY());
        assertEquals(100, e.getXOnScreen());
        assertEquals(200, e.getYOnScreen());
        assertEquals(2, e.getClickCount());
        assertEquals(MouseEvent.BUTTON1, e.getButton());
        assertEquals(new Point(10, 20), e.getPoint());
        assertEquals(new Point(100, 200), e.getLocationOnScreen());
    }

    @Test
    @DisplayName("MouseEvent translatePoint shifts local coords only")
    void mouseEventTranslatePointShiftsLocalCoordsOnly() {
        MouseEvent e = new MouseEvent(component(), MouseEvent.MOUSE_MOVED, 0L, 0,
                10, 20, 100, 200, 1, false, MouseEvent.NOBUTTON);
        e.translatePoint(5, -5);

        assertEquals(15, e.getX());
        assertEquals(15, e.getY());
        // Screen coords untouched — translation is within the component.
        assertEquals(100, e.getXOnScreen());
        assertEquals(200, e.getYOnScreen());
    }

    // --- consume() / isConsumed() (D_event_consume) ---------------------------------

    @Test
    @DisplayName("consume marks the event and isConsumed reads it back")
    void consumeMarksTheEventAndIsConsumedReadsItBack() {
        KeyEvent e = new KeyEvent(component(), KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a');
        assertFalse(e.isConsumed(), "a fresh event is unconsumed");
        e.consume();
        assertTrue(e.isConsumed());
        // Idempotent, as the JDK's unconditional `consumed = true` is.
        e.consume();
        assertTrue(e.isConsumed());
    }

    @Test
    @DisplayName("consume is public on every InputEvent subclass, not just KeyEvent")
    void consumeIsPublicOnEveryInputEventSubclassNotJustKeyEvent() {
        // The JDK widens AWTEvent's protected, id-conditional consume() to a
        // public unconditional one on InputEvent; MouseEvent and
        // MouseWheelEvent inherit it. Pinned because the whole point of the
        // port is that these calls *compile* in migrated code.
        MouseEvent mouse = new MouseEvent(component(), MouseEvent.MOUSE_PRESSED, 0L, 0, 5, 5, 1, false);
        MouseWheelEvent wheel = new MouseWheelEvent(component(), MouseEvent.MOUSE_WHEEL, 0L, 0,
                5, 5, 1, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 2);
        InputMethodEvent ime = new InputMethodEvent(component(), InputMethodEvent.CARET_POSITION_CHANGED,
                null, 0, null, null);
        // Two unrelated consume() declarations (InputEvent's and
        // InputMethodEvent's), so the loop is over the calls, not the objects.
        for (Runnable check : List.<Runnable>of(
                () -> {
                    assertFalse(mouse.isConsumed());
                    mouse.consume();
                    assertTrue(mouse.isConsumed());
                },
                () -> {
                    assertFalse(wheel.isConsumed());
                    wheel.consume();
                    assertTrue(wheel.isConsumed());
                },
                () -> {
                    assertFalse(ime.isConsumed());
                    ime.consume();
                    assertTrue(ime.isConsumed());
                })) {
            check.run();
        }
    }

    @Test
    @DisplayName("the consumed flag survives listener dispatch — A consumes, B sees it")
    void theConsumedFlagSurvivesListenerDispatchAConsumesBSeesIt() {
        // The idiom the port exists for. Delivery is unaffected by consuming
        // (the JDK notifies every listener regardless); what B observes is the
        // flag A set.
        // processKeyEvent is protected, so reach it the way
        // ComponentProcessEventTest does: a subclass exposing a dispatch seam.
        class Dispatching extends Component {
            Dispatching() {
                super(new Div());
            }

            void dispatch(java.awt.AWTEvent e) {
                processEvent(e);
            }
        }
        Dispatching c = new Dispatching();
        List<Boolean> seen = new ArrayList<>();
        c.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                seen.add(e.isConsumed());
                e.consume();
            }
        });
        c.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                seen.add(e.isConsumed());
            }
        });
        c.dispatch(new KeyEvent(c, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a'));
        assertEquals(List.of(false, true), seen,
                "the second listener must still be called, and must see the first's consume");
    }

    @Test
    @DisplayName("MouseWheelEvent carries scroll fields and unitsToScroll")
    void mouseWheelEventCarriesScrollFieldsAndUnitsToScroll() {
        MouseWheelEvent e = new MouseWheelEvent(component(), MouseEvent.MOUSE_WHEEL, 0L, 0,
                5, 5, 1, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, /* scrollAmount */ 3, /* wheelRotation */ 2);

        assertEquals(MouseWheelEvent.WHEEL_UNIT_SCROLL, e.getScrollType());
        assertEquals(3, e.getScrollAmount());
        assertEquals(2, e.getWheelRotation());
        assertEquals(6, e.getUnitsToScroll()); // scrollAmount * wheelRotation
    }

    @Test
    @DisplayName("FocusEvent carries temporary flag and opposite component")
    void focusEventCarriesTemporaryFlagAndOppositeComponent() {
        Component a = component();
        Component b = component();
        FocusEvent e = new FocusEvent(a, FocusEvent.FOCUS_GAINED, /* temporary */ true, b,
                FocusEvent.Cause.TRAVERSAL_FORWARD);

        assertSame(a, e.getComponent());
        assertTrue(e.isTemporary());
        assertSame(b, e.getOppositeComponent());
        assertEquals(FocusEvent.Cause.TRAVERSAL_FORWARD, e.getCause());
    }

    @Test
    @DisplayName("FocusEvent null cause defaults to UNKNOWN")
    void focusEventNullCauseDefaultsToUnknown() {
        FocusEvent e = new FocusEvent(component(), FocusEvent.FOCUS_LOST, false, null, null);
        assertEquals(FocusEvent.Cause.UNKNOWN, e.getCause());
    }

    @Test
    @DisplayName("WindowEvent source typing is Window, not Component")
    void windowEventSourceTypingIsWindowNotComponent() {
        Frame w = new Frame();
        WindowEvent e = new WindowEvent(w, WindowEvent.WINDOW_OPENED);

        assertSame(w, e.getWindow());
        assertSame(w, e.getComponent()); // inherited from ComponentEvent
        assertEquals(0, e.getOldState());
        assertEquals(0, e.getNewState());
    }

    @Test
    @DisplayName("WindowEvent state-change carries old and new")
    void windowEventStateChangeCarriesOldAndNew() {
        Frame w = new Frame();
        WindowEvent e = new WindowEvent(w, WindowEvent.WINDOW_STATE_CHANGED, 0, 1);
        assertEquals(0, e.getOldState());
        assertEquals(1, e.getNewState());
    }

    @Test
    @DisplayName("HierarchyEvent carries changed subtree info")
    void hierarchyEventCarriesChangedSubtreeInfo() {
        Container root = new Container();
        Component child = component();
        HierarchyEvent e = new HierarchyEvent(child, HierarchyEvent.ANCESTOR_MOVED, child, root,
                HierarchyEvent.PARENT_CHANGED);

        assertSame(child, e.getComponent());
        assertSame(child, e.getChanged());
        assertSame(root, e.getChangedParent());
        assertEquals((long) HierarchyEvent.PARENT_CHANGED, e.getChangeFlags());
    }

    @Test
    @DisplayName("PaintEvent carries mutable updateRect")
    void paintEventCarriesMutableUpdateRect() {
        Component c = component();
        Rectangle r = new Rectangle(0, 0, 10, 10);
        PaintEvent e = new PaintEvent(c, PaintEvent.PAINT, r);

        assertSame(r, e.getUpdateRect());
        Rectangle r2 = new Rectangle(5, 5, 20, 20);
        e.setUpdateRect(r2);
        assertSame(r2, e.getUpdateRect());
    }

    @Test
    @DisplayName("InputMethodEvent stores source as vaadinx Component")
    void inputMethodEventStoresSourceAsVaadinxComponent() {
        Component c = component();
        InputMethodEvent e = new InputMethodEvent(c, InputMethodEvent.CARET_POSITION_CHANGED, null, null);
        assertSame(c, e.getSource());
        assertEquals(0, e.getCommittedCharacterCount());
    }

    @Test
    @DisplayName("ContainerEvent hierarchy reaches ComponentEvent and AWTEvent")
    void containerEventHierarchyReachesComponentEventAndAwtEvent() {
        // D_event_port_policy rationale: user code that walks `instanceof ComponentEvent`
        // must still see ContainerEvents. Reflection-based assertions so
        // the compiler can't inline-short-circuit them.
        assertTrue(ComponentEvent.class.isAssignableFrom(ContainerEvent.class));
        assertTrue(java.awt.AWTEvent.class.isAssignableFrom(ContainerEvent.class));
        assertTrue(InputEvent.class.isAssignableFrom(KeyEvent.class));
        assertTrue(InputEvent.class.isAssignableFrom(MouseEvent.class));
        assertTrue(MouseEvent.class.isAssignableFrom(MouseWheelEvent.class));
    }

    @Test
    @DisplayName("listener interfaces can be implemented via adapters")
    void listenerInterfacesCanBeImplementedViaAdapters() {
        // Compile-time check more than a runtime check — confirms adapters
        // cover every method on their listener interfaces.
        List<String> calls = new ArrayList<>();

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                calls.add("clicked");
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                calls.add("wheel");
            }
        };
        ma.mouseClicked(new MouseEvent(component(), MouseEvent.MOUSE_CLICKED, 0L, 0, 0, 0, 1, false));
        ma.mouseMoved(new MouseEvent(component(), MouseEvent.MOUSE_MOVED, 0L, 0, 0, 0, 0, false)); // no-op

        assertEquals(List.of("clicked"), calls);

        // WindowAdapter covers three interfaces at once — AWT parity.
        WindowAdapter wa = new WindowAdapter() {
        };
        assertNotNull((WindowListener) wa);
        assertNotNull((WindowFocusListener) wa);
        assertNotNull((WindowStateListener) wa);
    }
}
