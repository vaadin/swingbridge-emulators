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
import vaadinx.awt.event.ComponentAdapter;
import vaadinx.awt.event.ComponentEvent;
import vaadinx.awt.event.WindowAdapter;
import vaadinx.awt.event.WindowEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code W_visibility_inverted} — the third front of the JDK call-graph audit;
 * {@code D_visibility_direction} in {@code emulators/decisions.md}.
 *
 * <p>The JDK's arrow is {@code setVisible -> show(b) -> show() / hide()}, with all the
 * work in the deprecated pair. SB-Emulators ran it backwards, which stranded every
 * {@code show()} / {@code hide()} override and left nowhere for the JDK's
 * {@code show() -> addNotify()} edge to live. These tests pin the direction, because
 * the <em>observable</em> visibility behaviour is identical either way — only a
 * subclass can tell the difference, which is exactly the R_no_vaadin_in_api limb 2 blind spot.
 */
class WindowVisibilityPathTest extends AbstractKaribuTest {

    // --- Component level ----------------------------------------------

    @Test
    @DisplayName("setVisible reaches an overridden show and hide")
    void setVisibleReachesAnOverriddenShowAndHide() {
        List<String> calls = new ArrayList<>();
        Frame f = new Frame() {
            @Override
            public void show() {
                calls.add("show");
                super.show();
            }

            @Override
            public void hide() {
                calls.add("hide");
                super.hide();
            }
        };

        f.setVisible(true);
        f.setVisible(false);

        assertEquals(List.of("show", "hide"), calls);
    }

    @Test
    @DisplayName("setVisible reaches an overridden show(boolean)")
    void setVisibleReachesAnOverriddenShowBoolean() {
        List<Boolean> seen = new ArrayList<>();
        Frame f = new Frame() {
            @Override
            public void show(boolean b) {
                seen.add(b);
                super.show(b);
            }
        };
        f.setVisible(true);
        f.setVisible(false);
        assertEquals(List.of(true, false), seen);
    }

    @Test
    @DisplayName("calling show directly is equivalent to setVisible true")
    void callingShowDirectlyIsEquivalentToSetVisibleTrue() {
        // The deprecated pair is a real entry point, not an alias that only
        // works when routed through setVisible.
        Frame f = new Frame();
        f.show();
        assertTrue(f.isVisible());

        f.hide();
        assertFalse(f.isVisible());
    }

    // --- the guards ---------------------------------------------------

    @Test
    @DisplayName("a redundant show does not re-fire COMPONENT_SHOWN")
    void aRedundantShowDoesNotReFireComponentShown() {
        Frame f = new Frame();
        List<Integer> ids = new ArrayList<>();
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
        f.setVisible(true);

        assertEquals(List.of(ComponentEvent.COMPONENT_SHOWN), ids);
    }

    @Test
    @DisplayName("showing an already-visible window raises it instead of re-showing")
    void showingAnAlreadyVisibleWindowRaisesItInsteadOfReShowing() {
        // JDK Window.show(): `if (visible) { toFront(); } else { ...show... }`.
        Counter raised = new Counter();
        Frame f = new Frame() {
            @Override
            public void toFront() {
                raised.inc();
                super.toFront();
            }
        };
        f.setVisible(true);
        raised.assertEquals(0);

        f.setVisible(true);
        raised.assertEquals(1);
    }

    // --- Window level -------------------------------------------------

    @Test
    @DisplayName("WINDOW_OPENED still fires once per show-from-dispose cycle")
    void windowOpenedStillFiresOncePerShowFromDisposeCycle() {
        Frame f = new Frame();
        Counter opened = new Counter();
        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                opened.inc();
            }
        });

        f.setVisible(true);
        f.setVisible(false);
        f.setVisible(true);
        opened.assertEquals(1);

        f.dispose();
        f.setVisible(true);
        opened.assertEquals(2);
    }

    @Test
    @DisplayName("hide cascades to owned visible windows")
    void hideCascadesToOwnedVisibleWindows() {
        // JDK Window.hide() walks ownedWindowList and hides each showing child.
        // SB-Emulators had no equivalent, because the cascade had nowhere to live while
        // setVisible held the body.
        Frame owner = new Frame();
        Window owned = new Window(owner);
        owner.setVisible(true);
        owned.setVisible(true);

        owner.setVisible(false);

        assertFalse(owner.isVisible());
        assertFalse(owned.isVisible(), "owned window should have been hidden with its owner");
    }

    @Test
    @DisplayName("hide leaves an already-hidden owned window alone")
    void hideLeavesAnAlreadyHiddenOwnedWindowAlone() {
        Frame owner = new Frame();
        Window owned = new Window(owner);
        owner.setVisible(true);
        assertFalse(owned.isVisible());

        List<Integer> ids = new ArrayList<>();
        owned.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                ids.add(e.getID());
            }
        });
        owner.setVisible(false);

        assertTrue(ids.isEmpty(), "a never-shown child must not fire COMPONENT_HIDDEN");
    }
}
