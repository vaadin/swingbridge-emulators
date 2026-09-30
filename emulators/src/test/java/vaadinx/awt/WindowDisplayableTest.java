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
import vaadinx.Counter;

import java.awt.IllegalComponentStateException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code W_no_displayable} — the fourth front of the JDK call-graph audit;
 * {@code D_window_displayable} in {@code emulators/decisions.md}.
 *
 * <p>AWT has three states where SB-Emulators had two. {@code displayable} is the middle one:
 * created by {@code addNotify()} (which {@code pack()} and {@code show()} both reach), destroyed
 * only by {@code dispose()}, and explicitly <strong>not</strong> cleared by hiding. Collapsing it
 * onto Vaadin attachment — which is what SB-Emulators did — makes it a synonym for
 * visibility and silently breaks every JDK contract that keys off it.
 */
class WindowDisplayableTest extends AbstractKaribuTest {

    // --- the three states ---------------------------------------------

    @Test
    @DisplayName("a fresh window is neither displayable nor visible")
    void aFreshWindowIsNeitherDisplayableNorVisible() {
        Frame f = new Frame();
        assertFalse(f.isDisplayable());
        assertFalse(f.isVisible());
        assertFalse(f.isShowing());
    }

    @Test
    @DisplayName("pack makes a window displayable without making it visible")
    void packMakesAWindowDisplayableWithoutMakingItVisible() {
        // The middle state, and the one that had no representation at all.
        Frame f = new Frame();
        f.pack();
        assertTrue(f.isDisplayable());
        assertFalse(f.isVisible());
        assertFalse(f.isShowing());
    }

    @Test
    @DisplayName("show makes a window displayable and visible")
    void showMakesAWindowDisplayableAndVisible() {
        Frame f = new Frame();
        f.setVisible(true);
        assertTrue(f.isDisplayable());
        assertTrue(f.isVisible());
        assertTrue(f.isShowing());
    }

    @Test
    @DisplayName("hiding does not undisplayable a window")
    void hidingDoesNotUndisplayableAWindow() {
        // AWT's rule, and the one the old attachment-based isDisplayable got
        // wrong: hide() detaches our peer, but only dispose() releases it.
        Frame f = new Frame();
        f.setVisible(true);
        f.setVisible(false);
        assertFalse(f.isVisible());
        assertTrue(f.isDisplayable(), "hiding must not clear displayability");
    }

    @Test
    @DisplayName("dispose undisplayables, and a later show re-displayables")
    void disposeUndisplayablesAndALaterShowReDisplayables() {
        Frame f = new Frame();
        f.setVisible(true);
        f.dispose();
        assertFalse(f.isDisplayable());

        f.setVisible(true);
        assertTrue(f.isDisplayable());
    }

    // --- what the flag buys: setUndecorated's R_match_swing_errors throw -----------------

    @Test
    @DisplayName("setUndecorated works before the window is displayable")
    void setUndecoratedWorksBeforeTheWindowIsDisplayable() {
        Frame f = new Frame();
        f.setUndecorated(true);
        assertTrue(f.isUndecorated());
    }

    @Test
    @DisplayName("pack then setUndecorated throws, as it does on the desktop")
    void packThenSetUndecoratedThrowsAsItDoesOnTheDesktop() {
        // Free consequence of the flag: the JDK guards setUndecorated on
        // displayability, so pack() alone is enough to close the window off.
        // Under the old attachment-based flag this silently succeeded.
        Frame f = new Frame();
        f.pack();
        assertEquals(
                "The frame is displayable.",
                assertThrows(IllegalComponentStateException.class, () -> f.setUndecorated(true)).getMessage());
    }

    @Test
    @DisplayName("setUndecorated still throws after hiding, and works again after dispose")
    void setUndecoratedStillThrowsAfterHidingAndWorksAgainAfterDispose() {
        Frame f = new Frame();
        f.setVisible(true);
        f.setVisible(false);
        assertThrows(IllegalComponentStateException.class, () -> f.setUndecorated(true));

        f.dispose();
        f.setUndecorated(true);
        assertTrue(f.isUndecorated());
    }

    // --- the call graph: R_no_vaadin_in_api limb 2 -----------------------------------

    @Test
    @DisplayName("pack reaches addNotify")
    void packReachesAddNotify() {
        Counter calls = new Counter();
        Frame f = new Frame() {
            @Override
            public void addNotify() {
                calls.inc();
                super.addNotify();
            }
        };
        f.pack();
        calls.assertEquals(1);
    }

    @Test
    @DisplayName("show reaches addNotify")
    void showReachesAddNotify() {
        Counter calls = new Counter();
        Frame f = new Frame() {
            @Override
            public void addNotify() {
                calls.inc();
                super.addNotify();
            }
        };
        f.setVisible(true);
        assertTrue(calls.get() >= 1, "show() must reach addNotify");
    }

    @Test
    @DisplayName("dispose reaches removeNotify")
    void disposeReachesRemoveNotify() {
        // The JDK's doDispose walks removeNotify; SB-Emulators used to call disposePeer
        // directly and let removeNotify arrive later and indirectly, via the
        // peer's Vaadin detach listener.
        Counter calls = new Counter();
        Frame f = new Frame() {
            @Override
            public void removeNotify() {
                calls.inc();
                super.removeNotify();
            }
        };
        f.setVisible(true);
        f.dispose();
        calls.assertEquals(1);
    }

    @Test
    @DisplayName("pack then show calls addNotify only once")
    void packThenShowCallsAddNotifyOnlyOnce() {
        // Guarded like the JDK's `if (peer == null)`.
        Counter calls = new Counter();
        Frame f = new Frame() {
            @Override
            public void addNotify() {
                calls.inc();
                super.addNotify();
            }
        };
        f.pack();
        f.setVisible(true);
        calls.assertEquals(1);
    }

    @Test
    @DisplayName("hiding does not reach removeNotify")
    void hidingDoesNotReachRemoveNotify() {
        Counter calls = new Counter();
        Frame f = new Frame() {
            @Override
            public void removeNotify() {
                calls.inc();
                super.removeNotify();
            }
        };
        f.setVisible(true);
        f.setVisible(false);
        calls.assertEquals(0);
    }

    // --- ordinary components inherit the bit through the cascade -------

    @Test
    @DisplayName("a non-Window component takes displayability from its window, not from attachment")
    void aNonWindowComponentTakesDisplayabilityFromItsWindowNotFromAttachment() {
        // Inverted deliberately, and the inversion is the point: this used to
        // assert that a child derived displayability from its peer's Vaadin
        // attachment, which was the deferral D_window_displayable left open.
        // It now comes down Container's cascade from the window
        // (D_component_displayable), and the two answers differ — pack() below
        // realises the child with nothing in the DOM.
        Frame f = new Frame();
        Panel child = new Panel();
        f.add(child);
        assertFalse(child.isDisplayable());

        f.pack();
        assertTrue(child.isDisplayable(), "pack() realises descendants, with no DOM anywhere");

        f.setVisible(true);
        assertTrue(child.isDisplayable());

        f.setVisible(false);
        assertTrue(child.isDisplayable(), "hiding does not undisplayable a child either");

        f.dispose();
        assertFalse(child.isDisplayable());
    }
}
