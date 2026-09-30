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
import vaadinx.awt.event.ComponentAdapter;
import vaadinx.awt.event.ComponentEvent;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * {@code W_property_fanout} — the R_decline_effect_only half of the JDK call-graph audit;
 * {@code D_window_fanout} in {@code emulators/decisions.md}.
 *
 * <p>Four bound properties real Swing fires that SB-Emulators dropped, and two SB-Emulators fired
 * that real Swing never does. None of these has a deliverable <em>effect</em> in a
 * browser — no window z-order, no per-window icon, no focus subsystem, no
 * window-level drag-and-drop — which is precisely the point: R_decline_effect_only lets the
 * effect go and keeps the state and the notification, because those are what
 * a migrated app's listener actually observes.
 *
 * <p>Note the import that is missing: {@code List} in this package resolves to
 * {@code vaadinx.awt.List}, the AWT widget, so collection types are spelled
 * {@code java.util.List} throughout.
 */
class WindowPropertyFanoutTest extends AbstractKaribuTest {

    private static final class Recorder implements PropertyChangeListener {
        final java.util.List<PropertyChangeEvent> events = new ArrayList<>();

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            events.add(evt);
        }

        java.util.List<PropertyChangeEvent> named(String name) {
            return events.stream().filter(it -> name.equals(it.getPropertyName())).toList();
        }
    }

    private static BufferedImage image() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }

    // --- alwaysOnTop --------------------------------------------------

    @Test
    @DisplayName("alwaysOnTop round-trips even though nothing honours it")
    void alwaysOnTopRoundTripsEvenThoughNothingHonoursIt() {
        // The JDK writes the field BEFORE consulting isAlwaysOnTopSupported,
        // so its own getter echoes an unhonoured request. Echoing is faithful.
        Frame w = new Frame();
        assertFalse(w.isAlwaysOnTop());
        w.setAlwaysOnTop(true);
        assertTrue(w.isAlwaysOnTop());
    }

    @Test
    @DisplayName("alwaysOnTop fires on change and not on a no-op")
    void alwaysOnTopFiresOnChangeAndNotOnANoOp() {
        Frame w = new Frame();
        Recorder r = new Recorder();
        w.addPropertyChangeListener(r);

        w.setAlwaysOnTop(true);
        w.setAlwaysOnTop(true);

        PropertyChangeEvent e = assertSingle(r.named("alwaysOnTop"));
        assertEquals(false, e.getOldValue());
        assertEquals(true, e.getNewValue());
    }

    @Test
    @DisplayName("alwaysOnTop propagates to owned windows")
    void alwaysOnTopPropagatesToOwnedWindows() {
        Frame owner = new Frame();
        Window owned = new Window(owner);
        owner.setAlwaysOnTop(true);
        assertTrue(owned.isAlwaysOnTop());
    }

    @Test
    @DisplayName("alwaysOnTop fan-out runs even when the value did not change")
    void alwaysOnTopFanOutRunsEvenWhenTheValueDidNotChange() {
        // The JDK calls setOwnedWindowsAlwaysOnTop OUTSIDE the changed-guard,
        // so a window owned *after* the flag was set still inherits it on the
        // next re-set. Dropping the fan-out because "nothing changed" would be
        // the bug R_decline_effect_only names.
        Frame owner = new Frame();
        owner.setAlwaysOnTop(true);
        Window lateOwned = new Window(owner);
        assertFalse(lateOwned.isAlwaysOnTop());

        owner.setAlwaysOnTop(true); // no change on the owner itself
        assertTrue(lateOwned.isAlwaysOnTop());
    }

    // --- iconImage ----------------------------------------------------

    @Test
    @DisplayName("setIconImages stores the list and getIconImages copies it")
    void setIconImagesStoresTheListAndGetIconImagesCopiesIt() {
        Frame w = new Frame();
        BufferedImage img = image();
        w.setIconImages(java.util.List.of(img));
        assertEquals(java.util.List.of(img), w.getIconImages());

        // Defensive copy, as the JDK's is — mutating the returned list must
        // not reach ours.
        w.getIconImages().clear();
        assertEquals(1, w.getIconImages().size());
    }

    @Test
    @DisplayName("iconImage fires every time, with no equality suppression")
    void iconImageFiresEveryTimeWithNoEqualitySuppression() {
        // The JDK's own comment on this line reads "Always send a property
        // change event", and it passes (null, null) precisely so that
        // firePropertyChange's old==new dedupe cannot swallow it.
        Frame w = new Frame();
        Recorder r = new Recorder();
        w.addPropertyChangeListener(r);

        w.setIconImages(java.util.List.of());
        w.setIconImages(java.util.List.of());

        assertEquals(2, r.named("iconImage").size());
        assertNull(r.named("iconImage").get(0).getOldValue());
        assertNull(r.named("iconImage").get(0).getNewValue());
    }

    @Test
    @DisplayName("setIconImage delegates to setIconImages")
    void setIconImageDelegatesToSetIconImages() {
        // R_no_vaadin_in_api limb 2: one writer, so an override of it catches both entry
        // points. The JDK wraps the image in a list and calls the plural form.
        java.util.List<java.util.List<Image>> seen = new ArrayList<>();
        Frame w = new Frame() {
            @Override
            public synchronized void setIconImages(java.util.List<? extends Image> icons) {
                seen.add(icons == null ? java.util.List.of() : java.util.List.copyOf(icons));
                super.setIconImages(icons);
            }
        };
        BufferedImage img = image();
        w.setIconImage(img);
        assertEquals(java.util.List.of(img), assertSingle(seen));
    }

    @Test
    @DisplayName("setIconImage null yields an empty list, and Frame getIconImage reads it back")
    void setIconImageNullYieldsAnEmptyListAndFrameGetIconImageReadsItBack() {
        Frame f = new Frame();
        BufferedImage img = image();
        f.setIconImage(img);
        assertSame(img, f.getIconImage());

        f.setIconImage(null);
        assertTrue(f.getIconImages().isEmpty());
        assertNull(f.getIconImage());
    }

    // --- focusableWindowState -----------------------------------------

    @Test
    @DisplayName("focusableWindowState round-trips and fires")
    void focusableWindowStateRoundTripsAndFires() {
        Frame w = new Frame();
        Recorder r = new Recorder();
        assertTrue(w.getFocusableWindowState());
        w.addPropertyChangeListener(r);

        w.setFocusableWindowState(false);

        assertFalse(w.getFocusableWindowState());
        PropertyChangeEvent e = assertSingle(r.named("focusableWindowState"));
        assertEquals(true, e.getOldValue());
        assertEquals(false, e.getNewValue());
    }

    @Test
    @DisplayName("isFocusableWindow reads the flag rather than hard-returning true")
    void isFocusableWindowReadsTheFlagRatherThanHardReturningTrue() {
        // The JDK's first limb: a window made non-focusable is always
        // non-focusable. Without this edge the flag would be write-only.
        Frame w = new Frame();
        assertTrue(w.isFocusableWindow());
        w.setFocusableWindowState(false);
        assertFalse(w.isFocusableWindow());
    }

    // --- the two invented events --------------------------------------

    @Test
    @DisplayName("Window setVisible and dispose fire no visible bound property")
    void windowSetVisibleAndDisposeFireNoVisibleBoundProperty() {
        // AWT signals visibility with a ComponentEvent only; JPopupMenu is the
        // sole class in java.awt/javax.swing with a "visible" bound property.
        Frame f = new Frame();
        Recorder r = new Recorder();
        f.addPropertyChangeListener(r);

        f.setVisible(true);
        f.setVisible(false);
        f.dispose();

        assertTrue(r.named("visible").isEmpty(), r.events.toString());
    }

    @Test
    @DisplayName("COMPONENT_SHOWN and HIDDEN still fire — the contract that replaces it")
    void componentShownAndHiddenStillFireTheContractThatReplacesIt() {
        Frame f = new Frame();
        java.util.List<Integer> ids = new ArrayList<>();
        f.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                ids.add(e.getID());
            }
        });

        f.setVisible(true);
        f.setVisible(false);

        assertEquals(
                java.util.List.of(ComponentEvent.COMPONENT_SHOWN, ComponentEvent.COMPONENT_HIDDEN),
                ids);
    }
}
