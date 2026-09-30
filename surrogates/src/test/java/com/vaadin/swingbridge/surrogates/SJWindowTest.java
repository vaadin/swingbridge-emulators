/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;

import java.awt.IllegalComponentStateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the SJWindow surrogate. Covers:
 *
 * <ol>
 *  <li><b>Ctor defaults</b> — the four JWindow-defining flips: undecorated
 *      chrome always on, modeless, both close gestures disarmed; plus the
 *      Dialog defaults deliberately NOT flipped (draggable / resizable
 *      stay false — no title bar to drag, no grips).
 *  <li><b>Owner chain</b> — SWindow's owner registration through the
 *      owner-taking ctor.
 *  <li><b>Content-pane routing</b> — RootPaneScaffold's add/remove redirect,
 *      setContentPane swap + null rejection, rootPane mirror invariant.
 *  <li><b>Window geometry</b> — SWindow's setLocation / setSize / setBounds
 *      mapping onto the overlay's top/left/width/height;
 *      centerOnScreen / setLocationRelativeTo clearing placement.
 *  <li><b>Lifecycle smoke</b> — WINDOW_OPENED on first show, WINDOW_CLOSED
 *      on dispose (inherited SWindow machinery; per-method depth lives
 *      in SFrameTest).
 *  <li><b>WARN-free splash path</b> — the typical splash flow emits no
 *      onUnimplemented WARNs.
 * </ol>
 */
class SJWindowTest extends AbstractKaribuTest {

    // --- Ctor defaults ----------------------------------------------------

    @Test
    @DisplayName("ctor strips chrome unconditionally")
    void ctorStripsChromeUnconditionally() {
        SJWindow w = new SJWindow();
        assertTrue(w.isUndecoratedChrome());
        assertTrue(w.hasClassName("emul-undecorated"));
    }

    @Test
    @DisplayName("ctor is modeless — a splash must not block the page")
    void ctorIsModeless() {
        assertEquals(ModalityMode.MODELESS, new SJWindow().getModality());
    }

    @Test
    @DisplayName("ctor disarms user close gestures")
    void ctorDisarmsUserCloseGestures() {
        SJWindow w = new SJWindow();
        assertFalse(w.isCloseOnEsc());
        assertFalse(w.isCloseOnOutsideClick());
    }

    @Test
    @DisplayName("ctor leaves draggable and resizable at Dialog's false defaults")
    void ctorLeavesDraggableAndResizableAtDialogsFalseDefaults() {
        // Unlike SFrame's ctor (which seeds both true for title-bar
        // dragging + AWT's resizable-by-default Frame), a JWindow has
        // no title bar and no grips.
        SJWindow w = new SJWindow();
        assertFalse(w.isDraggable());
        assertFalse(w.isResizable());
    }

    @Test
    @DisplayName("owner ctor registers child with owner")
    void ownerCtorRegistersChildWithOwner() {
        SFrame owner = new SFrame("owner");
        SJWindow w = new SJWindow(owner);
        assertSame(owner, w.getOwner());
        // getOwnedWindows() is an array; Kotlin's `contains` on it was an extension.
        assertTrue(Arrays.asList(owner.getOwnedWindows()).contains(w));
    }

    @Test
    @DisplayName("no-arg ctor is ownerless")
    void noArgCtorIsOwnerless() {
        assertNull(new SJWindow().getOwner());
    }

    // --- Content-pane routing (RootPaneScaffold) ---------------------------

    @Test
    @DisplayName("add routes into the lazy content pane")
    void addRoutesIntoTheLazyContentPane() {
        SJWindow w = new SJWindow();
        Button button = new Button("hi");
        w.add(button);
        assertSame(w.getContentPane(), button.getParent().get());
    }

    @Test
    @DisplayName("remove routes into the content pane")
    void removeRoutesIntoTheContentPane() {
        SJWindow w = new SJWindow();
        Button button = new Button("hi");
        w.add(button);
        w.remove(button);
        assertFalse(button.getParent().isPresent());
    }

    @Test
    @DisplayName("setContentPane swaps and keeps the rootPane mirror invariant")
    void setContentPaneSwapsAndKeepsTheRootPaneMirrorInvariant() {
        SJWindow w = new SJWindow();
        SJRootPane rootPane = w.getRootPane();  // force lazy construction
        Div newPane = new Div();
        w.setContentPane(newPane);
        assertSame(newPane, w.getContentPane());
        assertSame(newPane, rootPane.getContentPane());
    }

    @Test
    @DisplayName("setContentPane null throws")
    void setContentPaneNullThrows() {
        assertThrows(IllegalComponentStateException.class,
                () -> new SJWindow().setContentPane(null));
    }

    @Test
    @DisplayName("getRootPane sees the same content pane as getContentPane")
    void getRootPaneSeesTheSameContentPaneAsGetContentPane() {
        SJWindow w = new SJWindow();
        assertSame(w.getContentPane(), w.getRootPane().getContentPane());
    }

    // --- Window geometry ---------------------------------------------------

    @Test
    @DisplayName("setLocation maps to overlay top-left")
    void setLocationMapsToOverlayTopLeft() {
        SJWindow w = new SJWindow();
        w.setLocation(40, 25);
        assertEquals("40px", w.getLeft());
        assertEquals("25px", w.getTop());
    }

    @Test
    @DisplayName("setSize maps to overlay width-height")
    void setSizeMapsToOverlayWidthHeight() {
        SJWindow w = new SJWindow();
        w.setSize(400, 300);
        assertEquals("400px", w.getWidth());
        assertEquals("300px", w.getHeight());
    }

    @Test
    @DisplayName("setBounds maps both")
    void setBoundsMapsBoth() {
        SJWindow w = new SJWindow();
        w.setBounds(10, 20, 300, 200);
        assertEquals("10px", w.getLeft());
        assertEquals("20px", w.getTop());
        assertEquals("300px", w.getWidth());
        assertEquals("200px", w.getHeight());
    }

    @Test
    @DisplayName("centerOnScreen clears explicit placement")
    void centerOnScreenClearsExplicitPlacement() {
        SJWindow w = new SJWindow();
        w.setLocation(40, 25);
        w.centerOnScreen();
        assertNull(w.getLeft());
        assertNull(w.getTop());
    }

    @Test
    @DisplayName("setLocationRelativeTo recenters regardless of argument")
    void setLocationRelativeToRecentersRegardlessOfArgument() {
        SJWindow w = new SJWindow();
        w.setLocation(40, 25);
        w.setLocationRelativeTo(null);
        assertNull(w.getLeft());
        assertNull(w.getTop());
        assertNoWarns("was WARN before window geometry landed");
    }

    // --- Lifecycle smoke -----------------------------------------------------

    @Test
    @DisplayName("setVisible fires WINDOW_OPENED and dispose fires WINDOW_CLOSED")
    void setVisibleFiresWindowOpenedAndDisposeFiresWindowClosed() {
        SJWindow w = new SJWindow();
        List<Integer> events = new ArrayList<>();
        w.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowOpened(SWindowEvent e) {
                events.add(e.getID());
            }

            @Override
            public void windowClosed(SWindowEvent e) {
                events.add(e.getID());
            }
        });
        w.setVisible(true);
        assertTrue(w.isOpened());
        w.dispose();
        assertFalse(w.isOpened());
        assertEquals(List.of(SWindowEvent.WINDOW_OPENED, SWindowEvent.WINDOW_CLOSED), events);
    }

    // --- WARN inventory --------------------------------------------------------

    @Test
    @DisplayName("typical splash flow is WARN-free")
    void typicalSplashFlowIsWarnFree() {
        SJWindow w = new SJWindow();
        Div loading = new Div();
        loading.setText("Loading…");
        w.add(loading);
        w.setLocationRelativeTo(null);
        w.setSize(400, 120);
        w.setVisible(true);
        w.dispose();
        assertNoWarns("unexpected WARNs: " + capturedWarns);
    }
}
