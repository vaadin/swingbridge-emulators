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

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameAdapter;
import com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the SJInternalFrame surrogate (SD_sjinternalframe). Covers:
 *
 * <ol>
 *  <li><b>Ctor delta</b> — decorated + modeless: draggable, resizable, header
 *      title present, close gestures disarmed (JInternalFrame closes only
 *      via its close-X) — the inverse of SJWindow's chrome-stripping.
 *  <li><b>InternalFrameListener family</b> — OPENED on first show, CLOSED on
 *      dispose, ACTIVATED / DEACTIVATED on selection, CLOSING dispatch.
 *  <li><b>Maximize geometry</b> — viewport-fill on setMaximum(true), restore
 *      on false.
 *  <li><b>Content-pane routing</b> — RootPaneScaffold add/remove redirect.
 *  <li><b>WARN-free</b> — the demo path emits no onUnimplemented WARNs.
 * </ol>
 */
class SJInternalFrameTest extends AbstractKaribuTest {

    // --- Ctor delta -------------------------------------------------------

    @Test
    @DisplayName("ctor keeps chrome and stays modeless")
    void ctorKeepsChromeAndStaysModeless() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        assertTrue(f.isDraggable(), "internal frame drags by its title bar");
        assertTrue(f.isResizable(), "internal frame resizes by its grips");
        assertEquals(ModalityMode.MODELESS, f.getModality(), "an internal frame must not block the page");
        assertFalse(f.isCloseOnEsc());
        assertFalse(f.isCloseOnOutsideClick());
        assertEquals("Doc", f.getTitle());
    }

    @Test
    @DisplayName("setTitle mirrors and fires PCE")
    void setTitleMirrorsAndFiresPce() {
        SJInternalFrame f = new SJInternalFrame("A");
        Counter fired = new Counter();
        f.addPropertyChangeListener("title", e -> fired.inc());
        f.setTitle("B");
        assertEquals("B", f.getTitle());
        assertTrue(fired.get() > 0);
    }

    // --- InternalFrameListener family ------------------------------------

    @Test
    @DisplayName("OPENED fires once on first show, CLOSED on dispose")
    void openedFiresOnceOnFirstShowClosedOnDispose() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        List<Integer> events = new java.util.ArrayList<>();
        f.addInternalFrameListener(new SInternalFrameAdapter() {
            @Override
            public void internalFrameOpened(SInternalFrameEvent e) {
                events.add(e.getID());
            }

            @Override
            public void internalFrameClosed(SInternalFrameEvent e) {
                events.add(e.getID());
            }
        });
        f.setVisible(true);
        f.setVisible(true); // idempotent — no second OPENED
        f.dispose();
        assertEquals(
                List.of(
                        SInternalFrameEvent.INTERNAL_FRAME_OPENED,
                        SInternalFrameEvent.INTERNAL_FRAME_CLOSED),
                events);
    }

    @Test
    @DisplayName("selection fires ACTIVATED then DEACTIVATED")
    void selectionFiresActivatedThenDeactivated() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        List<Integer> events = new java.util.ArrayList<>();
        f.addInternalFrameListener(new SInternalFrameAdapter() {
            @Override
            public void internalFrameActivated(SInternalFrameEvent e) {
                events.add(e.getID());
            }

            @Override
            public void internalFrameDeactivated(SInternalFrameEvent e) {
                events.add(e.getID());
            }
        });
        f.setSelected(true);
        f.setSelected(true); // idempotent
        f.setSelected(false);
        assertEquals(
                List.of(
                        SInternalFrameEvent.INTERNAL_FRAME_ACTIVATED,
                        SInternalFrameEvent.INTERNAL_FRAME_DEACTIVATED),
                events);
        assertFalse(f.isSelected());
    }

    // --- Header title-bar controls (SD_internalframe_chrome) -------------------------------

    /** Kotlin had this as an extension on SJInternalFrame; Java takes the receiver first. */
    private static Map<String, Icon> headerIcons(SJInternalFrame frame) {
        Map<String, Icon> icons = new LinkedHashMap<>();
        frame.getHeader().getElement().getChildren()
                .map(el -> el.getComponent().orElse(null))
                .filter(Icon.class::isInstance)
                .map(Icon.class::cast)
                .forEach(icon -> icons.put(icon.getElement().getAttribute("aria-label"), icon));
        return icons;
    }

    @Test
    @DisplayName("header controls toggle visibility per flag")
    void headerControlsToggleVisibilityPerFlag() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        Map<String, Icon> icons = headerIcons(f);
        Icon close = icons.get("Close");
        Icon minimize = icons.get("Minimize");
        Icon maximize = icons.get("Maximize");

        // Defaults: close shown, minimize/maximize hidden.
        assertTrue(close.isVisible());
        assertFalse(minimize.isVisible());
        assertFalse(maximize.isVisible());

        f.setIconifiable(true);
        f.setMaximizable(true);
        f.setClosable(false);
        assertTrue(minimize.isVisible());
        assertTrue(maximize.isVisible());
        assertFalse(close.isVisible());
    }

    @Test
    @DisplayName("header minimize button runs the installed handler")
    void headerMinimizeButtonRunsTheInstalledHandler() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        Counter iconified = new Counter();
        f.setIconifiable(true);
        f.setIconifyHandler(iconified::inc);
        Icon minimize = headerIcons(f).get("Minimize");
        ComponentUtil.fireEvent(minimize, new ClickEvent<>(minimize));
        iconified.assertEquals(1);
    }

    @Test
    @DisplayName("CLOSING dispatches to the listener")
    void closingDispatchesToTheListener() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        Counter closing = new Counter();
        f.addInternalFrameListener(new SInternalFrameAdapter() {
            @Override
            public void internalFrameClosing(SInternalFrameEvent e) {
                closing.inc();
            }
        });
        f.fireInternalFrameEvent(SInternalFrameEvent.INTERNAL_FRAME_CLOSING);
        closing.assertEquals(1);
    }

    // --- Maximize geometry ------------------------------------------------

    @Test
    @DisplayName("maximize fills the viewport and restore returns")
    void maximizeFillsTheViewportAndRestoreReturns() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        f.setSize(320, 180);
        f.setMaximum(true);
        assertEquals("100%", f.getWidth());
        assertEquals("100%", f.getHeight());
        assertTrue(f.isMaximum());
        f.setMaximum(false);
        assertFalse(f.isMaximum());
        assertEquals("320px", f.getWidth());
        assertEquals("180px", f.getHeight());
    }

    // --- Content-pane routing --------------------------------------------

    @Test
    @DisplayName("add routes into the content pane")
    void addRoutesIntoTheContentPane() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        Div child = new Div();
        f.add(child);
        assertSame(f.getContentPane(), child.getParent().get());
    }

    // --- WARN-free --------------------------------------------------------

    @Test
    @DisplayName("demo path emits no WARNs")
    void demoPathEmitsNoWarns() {
        SJInternalFrame f = new SJInternalFrame("Doc");
        f.add(new Div());
        f.setVisible(true);
        f.setSelected(true);
        f.setMaximum(true);
        f.setMaximum(false);
        f.dispose();
        assertNoWarns("unexpected WARNs: " + capturedWarns);
    }
}
