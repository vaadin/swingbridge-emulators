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

import java.awt.Event;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AWT's deprecated 1.0 handleEvent(Event) dispatcher. We replicate it
 * because legacy Swing code that overrides one of the boolean callbacks
 * (mouseDown, keyDown, action, …) and pokes the component via handleEvent
 * needs the routing to still work — R_swing_is_truth.
 */
@SuppressWarnings("deprecation")
class ComponentHandleEventTest extends AbstractKaribuTest {

    private static final class Spy extends Component {
        final List<String> calls = new ArrayList<>();

        Spy() {
            super(new Div());
        }

        @Override
        public boolean mouseDown(Event evt, int x, int y) {
            calls.add("mouseDown:" + x + "," + y);
            return true;
        }

        @Override
        public boolean keyDown(Event evt, int key) {
            calls.add("keyDown:" + key);
            return true;
        }

        @Override
        public boolean action(Event evt, Object what) {
            calls.add("action:" + what);
            return true;
        }

        @Override
        public boolean gotFocus(Event evt, Object what) {
            calls.add("gotFocus");
            return true;
        }
    }

    @Test
    @DisplayName("mouse ids dispatch to the matching mouse callback")
    void mouseIdsDispatchToTheMatchingMouseCallback() {
        Spy c = new Spy();
        Event evt = new Event(c, Event.MOUSE_DOWN, null);
        evt.x = 10;
        evt.y = 20;
        assertTrue(c.handleEvent(evt));
        assertEquals(List.of("mouseDown:10,20"), c.calls);
    }

    @Test
    @DisplayName("KEY_PRESS and KEY_ACTION both route to keyDown")
    void keyPressAndKeyActionBothRouteToKeyDown() {
        // AWT collapses action keys (F1, arrows, …) onto the same callback
        // as ordinary typed keys — keyDown doesn't distinguish.
        Spy c = new Spy();
        Event press = new Event(c, Event.KEY_PRESS, null);
        press.key = 65;
        Event action = new Event(c, Event.KEY_ACTION, null);
        action.key = 1008;
        c.handleEvent(press);
        c.handleEvent(action);
        assertEquals(List.of("keyDown:65", "keyDown:1008"), c.calls);
    }

    @Test
    @DisplayName("ACTION_EVENT dispatches to action with the arg")
    void actionEventDispatchesToActionWithTheArg() {
        Spy c = new Spy();
        Event evt = new Event(c, Event.ACTION_EVENT, "hello");
        c.handleEvent(evt);
        assertEquals(List.of("action:hello"), c.calls);
    }

    @Test
    @DisplayName("GOT_FOCUS dispatches to gotFocus")
    void gotFocusDispatchesToGotFocus() {
        Spy c = new Spy();
        Event evt = new Event(c, Event.GOT_FOCUS, null);
        c.handleEvent(evt);
        assertEquals(List.of("gotFocus"), c.calls);
    }

    @Test
    @DisplayName("unknown id returns false and does not dispatch")
    void unknownIdReturnsFalseAndDoesNotDispatch() {
        Spy c = new Spy();
        Event evt = new Event(c, 99999, null);
        assertFalse(c.handleEvent(evt));
        assertTrue(c.calls.isEmpty());
    }

    @Test
    @DisplayName("null event returns false without throwing")
    void nullEventReturnsFalseWithoutThrowing() {
        // AWT NPEs here; we stay defensive (same precedent as process* methods).
        Spy c = new Spy();
        assertFalse(c.handleEvent(null));
    }

    @Test
    @DisplayName("default callbacks return false when not overridden")
    void defaultCallbacksReturnFalseWhenNotOverridden() {
        // Base Component stubs all return false — handleEvent surfaces that
        // faithfully as "not handled," matching AWT's own contract.
        Component base = new Component(new Div()) {
        };
        Event evt = new Event(base, Event.MOUSE_UP, null);
        assertFalse(base.handleEvent(evt));
    }
}
