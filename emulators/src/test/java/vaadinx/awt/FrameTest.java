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

import com.vaadin.flow.component.dialog.Dialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.Color;
import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameTest extends AbstractKaribuTest {

    /** The Vaadin overlay backing {@code f}. */
    private static Dialog peerOf(Frame f) {
        return (Dialog) f.getPeer();
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new Frame();
    }

    @Test
    @DisplayName("title defaults to empty string")
    void titleDefaultsToEmptyString() {
        // AWT contract: getTitle is never null, even on a default-constructed
        // Frame. Matches "".equals(new java.awt.Frame().getTitle()) in real AWT.
        assertEquals("", new Frame().getTitle());
    }

    @Test
    @DisplayName("constructor with title seeds the title and the Dialog header")
    void constructorWithTitleSeedsTheTitleAndTheDialogHeader() {
        Frame f = new Frame("Hello");
        assertEquals("Hello", f.getTitle());
        // R_swing_is_truth: peer state kept in sync with Swing-side field from ctor onwards.
        assertEquals("Hello", peerOf(f).getHeaderTitle());
    }

    @Test
    @DisplayName("constructor with title and GraphicsConfiguration ignores GC")
    void constructorWithTitleAndGraphicsConfigurationIgnoresGc() {
        // GraphicsConfiguration has no browser equivalent (R_layouts_close_enough); it's
        // accepted-and-ignored. Title still applies normally.
        Frame f = new Frame("Ignored GC", null);
        assertEquals("Ignored GC", f.getTitle());
    }

    @Test
    @DisplayName("setTitle pushes to the Dialog header title")
    void setTitlePushesToTheDialogHeaderTitle() {
        Frame f = new Frame();
        f.setTitle("Edit");
        assertEquals("Edit", f.getTitle());
        assertEquals("Edit", peerOf(f).getHeaderTitle());
    }

    @Test
    @DisplayName("setTitle with null normalizes to empty string")
    void setTitleWithNullNormalizesToEmptyString() {
        // AWT coerces null → ""; getTitle is documented never-null.
        Frame f = new Frame("initial");
        f.setTitle(null);
        assertEquals("", f.getTitle());
        assertEquals("", peerOf(f).getHeaderTitle());
    }

    @Test
    @DisplayName("setTitle fires a title PropertyChangeEvent")
    void setTitleFiresATitlePropertyChangeEvent() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        Frame f = new Frame();
        f.addPropertyChangeListener("title", events::add);

        f.setTitle("First");
        f.setTitle("Second");

        assertEquals(2, events.size());
        assertEquals("", events.get(0).getOldValue());
        assertEquals("First", events.get(0).getNewValue());
        assertEquals("First", events.get(1).getOldValue());
        assertEquals("Second", events.get(1).getNewValue());
    }

    @Test
    @DisplayName("setTitle is idempotent for PropertyChangeEvents")
    void setTitleIsIdempotentForPropertyChangeEvents() {
        // PropertyChangeSupport dedups equal old==new, so two setTitle calls
        // with the same value fire only once. Regression guard for future
        // refactors that might bypass the support.
        List<PropertyChangeEvent> events = new ArrayList<>();
        Frame f = new Frame();
        f.addPropertyChangeListener("title", events::add);

        f.setTitle("Same");
        f.setTitle("Same");

        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("honest AWT defaults for state, decoration, and resize getters")
    void honestAwtDefaultsForStateDecorationAndResizeGetters() {
        // Regression: these used to onUnimplemented with 0/false returns —
        // state=0 was accidentally right (NORMAL) but isResizable=false
        // misrepresented AWT's true default. Fresh Frame matches real
        // java.awt.Frame for every getter user code gates on.
        Frame f = new Frame();
        assertEquals(java.awt.Frame.NORMAL, f.getState());
        assertEquals(java.awt.Frame.NORMAL, f.getExtendedState());
        assertFalse(f.isUndecorated());
        assertTrue(f.isResizable());
        assertNull(f.getMaximizedBounds());
    }

    @Test
    @DisplayName("state setters silent on default, warn only on unhonorable change")
    void stateSettersSilentOnDefaultWarnOnlyOnUnhonorableChange() {
        // No-op direction for each setter mirrors the getter's value —
        // apps that re-set the default don't generate log noise. Warn is
        // observable only via log inspection (which we don't assert); we
        // assert the setters don't throw and don't mutate observable state.
        // setResizable is excluded: per D_inline_route_sizing it field-shadows and fires PCE,
        // so it's covered separately by the round-trip test below.
        Frame f = new Frame();
        f.setState(java.awt.Frame.NORMAL);          // redundant — silent
        f.setExtendedState(java.awt.Frame.NORMAL);  // redundant — silent
        f.setUndecorated(false);                    // redundant — silent
        f.setMaximizedBounds(null);                 // redundant — silent
        // State is immutable in our emulation — getters still report defaults.
        assertEquals(java.awt.Frame.NORMAL, f.getState());
        assertEquals(java.awt.Frame.NORMAL, f.getExtendedState());
        assertFalse(f.isUndecorated());
    }

    @Test
    @DisplayName("setResizable round-trips through isResizable and fires the resizable PCE")
    void setResizableRoundTripsThroughIsResizableAndFiresTheResizablePce() {
        // Frame.resizable field-shadows per D_inline_route_sizing — the browser can't honor a
        // "disable resize gripper" request (no Vaadin API), but the value
        // round-trips for R_swing_is_truth and gates the InlineStrategy fixed-size-window
        // opt-out (setResizable(false) + setPreferredSize + pack() on a
        // @MainWindow JFrame). JDK Frame.resizable is a bound property.
        Frame f = new Frame();
        assertTrue(f.isResizable(), "AWT default is resizable");
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("resizable", events::add);

        f.setResizable(false);
        assertFalse(f.isResizable(), "field shadow reflects setter");
        assertEquals(1, events.size(), "PCE fires on change");
        assertEquals(true, events.get(0).getOldValue());
        assertEquals(false, events.get(0).getNewValue());

        f.setResizable(false);  // re-set same value — JDK fires no PCE
        assertEquals(1, events.size(), "no PCE on no-change re-set");

        f.setResizable(true);
        assertTrue(f.isResizable());
        assertEquals(2, events.size());
        assertEquals(false, events.get(1).getOldValue());
        assertEquals(true, events.get(1).getNewValue());
    }

    @Test
    @DisplayName("getCursorType returns current cursor's type code")
    void getCursorTypeReturnsCurrentCursorsTypeCode() {
        // Deprecated AWT 1.0 alias — should resolve through Component's
        // never-null getCursor(). Pre-setCursor it falls back to the
        // default cursor (type 0); after setCursor it reports the new type.
        Frame f = new Frame();
        assertEquals(Cursor.DEFAULT_CURSOR, f.getCursorType());

        f.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        assertEquals(Cursor.HAND_CURSOR, f.getCursorType());
    }

    @Test
    @DisplayName("setCursor by int translates to the matching Cursor")
    void setCursorByIntTranslatesToTheMatchingCursor() {
        // Deprecated AWT 1.0 setter — delegates to
        // setCursor(Cursor.getPredefinedCursor(type)). Round-trip through
        // Component.getCursor verifies the translation and that CSS was
        // written (Component.setCursor fires property change + CSS write).
        Frame f = new Frame();
        f.setCursor(Cursor.WAIT_CURSOR);
        assertEquals(Cursor.WAIT_CURSOR, f.getCursor().getType());
        assertEquals("wait", f.getPeer().getElement().getStyle().get("cursor"));
    }

    @Test
    @DisplayName("setBackground inherited from Window writes CSS")
    void setBackgroundInheritedFromWindowWritesCss() {
        // Regression: Frame's generator re-stubbed setBackground, masking
        // Window's delegate to Component's real CSS writer. Verify the
        // inherited path actually lands on the peer's style.
        Frame f = new Frame();
        f.setBackground(Color.RED);
        assertEquals(Color.RED, f.getBackground());
        assertEquals("rgb(255,0,0)", f.getPeer().getElement().getStyle().get("background-color"));
    }

    @Test
    @DisplayName("Frame shows up in Window getWindows and Frame getFrames")
    void frameShowsUpInWindowGetWindowsAndFrameGetFrames() {
        // Full round-trip after the constructor changes: a titled Frame
        // opens and lands in both registries with the right type filter.
        Frame f = new Frame("Registered");
        f.setVisible(true);

        assertTrue(Arrays.stream(Window.getWindows()).anyMatch(it -> it == f));
        assertTrue(Arrays.stream(Frame.getFrames()).anyMatch(it -> it == f));
    }
}
