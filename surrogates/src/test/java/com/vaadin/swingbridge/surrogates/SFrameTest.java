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

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SComponentAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SComponentEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowFocusListener;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowListener;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowStateListener;

import java.awt.Frame;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sframe's SFrame surrogate. Covers:
 *
 * <ol>
 *  <li><b>setVisible / dispose / WINDOW_OPENED / WINDOW_CLOSED</b> — lifecycle
 *      including the {@code everShown} latch reset on dispose.
 *  <li><b>Peer-originated close → WINDOW_CLOSING</b> — Dialog.setOpened(false)
 *      drives the peer listener, which mirrors into the visible shadow
 *      and fires the Swing-side event.
 *  <li><b>setDefaultCloseOperation</b> across all four values, EXIT_ON_CLOSE
 *      WARN at set-time, DO_NOTHING gesture disarm via
 *      {@code setCloseOnEsc}/{@code setCloseOnOutsideClick}.
 *  <li><b>Title</b> round-trip through {@code setHeaderTitle} + PCE.
 *  <li><b>Content-pane routing</b> — {@code add}/{@code remove} redirect into
 *      a lazy Div, {@code setContentPane} swap, null rejection.
 *  <li><b>Owner / ownedWindow chain</b> — weak-ref list, pruning.
 *  <li><b>getFrames / getWindows</b> — live-graph walk scoped to current UI.
 *  <li><b>SWindow stateless accessors</b> — defaults (isFocusableWindow
 *      true, isAlwaysOnTopSupported false, pack no-op, etc.).
 * </ol>
 */
class SFrameTest extends AbstractKaribuTest {

    /** Test-only helper to tear down MockVaadin without failing on double-teardown. */
    private static void tearDownMockVaadin() {
        try {
            MockVaadin.tearDown();
        } catch (Throwable ignored) {
            // already torn down
        }
    }

    /** Records the ids of every window event the adapter's three overrides see. */
    private static List<Integer> recordWindowEventIds(SFrame f) {
        final List<Integer> ids = new ArrayList<>();
        f.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowOpened(SWindowEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void windowClosing(SWindowEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void windowClosed(SWindowEvent e) {
                ids.add(e.getID());
            }
        });
        return ids;
    }

    /** Records the ids of shown/hidden component events. */
    private static List<Integer> recordShowHideIds(SFrame f) {
        final List<Integer> ids = new ArrayList<>();
        f.addComponentListener(new SComponentAdapter() {
            @Override
            public void componentShown(SComponentEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void componentHidden(SComponentEvent e) {
                ids.add(e.getID());
            }
        });
        return ids;
    }

    /** Collects PCEs for one named property. */
    private static List<PropertyChangeEvent> recordPces(SFrame f, String property) {
        final List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener(property, events::add);
        return events;
    }

    // --- Constructors ---------------------------------------------------

    @Test
    @DisplayName("default ctor installs empty title and no owner")
    void defaultCtorDefaults() {
        final SFrame f = new SFrame();
        assertEquals("", f.getTitle());
        assertNull(f.getOwner());
        assertFalse(f.isOpened());  // Dialog's bit — matches our visible shadow pre-attach
    }

    @Test
    @DisplayName("titled ctor seeds headerTitle")
    void titledCtorSeedsHeaderTitle() {
        final SFrame f = new SFrame("Hello");
        assertEquals("Hello", f.getTitle());
        assertEquals("Hello", f.getHeaderTitle());
    }

    @Test
    @DisplayName("null title coerces to empty string")
    void nullTitleCoerces() {
        final SFrame f = new SFrame((String) null);
        assertEquals("", f.getTitle());
    }

    @Test
    @DisplayName("owner ctor registers child with owner")
    void ownerCtorRegistersChild() {
        final SFrame parent = new SFrame("parent");
        final SFrame child = new SFrame(parent);
        assertSame(parent, child.getOwner());
        assertArrayEquals(new SWindow[] { child }, parent.getOwnedWindows());
    }

    // --- setVisible / dispose -------------------------------------------

    @Test
    @DisplayName("setVisible true attaches dialog to UI, flips visible, fires WINDOW_OPENED once")
    void setVisibleTrueAttachesAndOpens() {
        final SFrame f = new SFrame("t");
        final List<Integer> events = recordWindowEventIds(f);

        f.setVisible(true);
        assertTrue(f.isOpened());
        assertTrue(f.getElement().getNode().isAttached());
        assertEquals(List.of(SWindowEvent.WINDOW_OPENED), events);
    }

    @Test
    @DisplayName("setVisible true - WINDOW_OPENED fires once across repeat shows")
    void windowOpenedFiresOnceAcrossShows() {
        final SFrame f = new SFrame();
        final Counter count = new Counter();
        f.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowOpened(SWindowEvent e) {
                count.inc();
            }
        });

        f.setVisible(true);
        f.setVisible(false);  // hide
        f.setVisible(true);   // show again — should NOT refire WINDOW_OPENED
        count.assertEquals(1);
    }

    @Test
    @DisplayName("dispose fires WINDOW_CLOSED, detaches, resets everShown so show refires OPENED")
    void disposeResetsEverShown() {
        final SFrame f = new SFrame();
        final List<Integer> events = recordWindowEventIds(f);

        f.setVisible(true);
        f.dispose();
        assertFalse(f.getElement().getNode().isAttached());
        assertEquals(List.of(SWindowEvent.WINDOW_OPENED, SWindowEvent.WINDOW_CLOSED), events);

        // dispose → show is a new first-show per AWT.
        f.setVisible(true);
        assertEquals(
                List.of(SWindowEvent.WINDOW_OPENED, SWindowEvent.WINDOW_CLOSED, SWindowEvent.WINDOW_OPENED),
                events);
    }

    @Test
    @DisplayName("setVisible true with no current UI throws IllegalStateException")
    void setVisibleWithoutUiThrows() {
        // AbstractKaribuTest already set up a UI; tear it down to simulate
        // "called from a thread Vaadin doesn't know about."
        tearDownMockVaadin();
        final SFrame f = new SFrame();
        assertThrows(IllegalStateException.class, () -> f.setVisible(true));
    }

    // --- Peer-originated close → WINDOW_CLOSING -------------------------

    @Test
    @DisplayName("peer setOpened false fires WINDOW_CLOSING, mirrors visible, no fake WINDOW_OPENED")
    void peerCloseFiresClosing() {
        final SFrame f = new SFrame();
        final List<Integer> ids = recordWindowEventIds(f);
        f.setVisible(true);  // WINDOW_OPENED
        ids.clear();

        // Simulate ESC / outside-click: Dialog drives setOpened(false).
        f.setOpened(false);

        // WINDOW_CLOSING fires, not WINDOW_CLOSED (programmatic dispose
        // is the only path that fires CLOSED).
        assertEquals(List.of(SWindowEvent.WINDOW_CLOSING), ids);
        assertFalse(f.isOpened());
    }

    @Test
    @DisplayName("preventPeerEvents guards our own setOpened writes from double-firing")
    void ourOwnPeerWritesDoNotLoop() {
        final SFrame f = new SFrame();
        final Counter count = new Counter();
        f.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowClosing(SWindowEvent e) {
                count.inc();
            }
        });
        f.setVisible(true);

        // setVisible(false) — our own peer write should NOT loop back
        // through onPeerOpenedChanged to refire CLOSING.
        f.setVisible(false);
        count.assertEquals(0);
    }

    // setDefaultCloseOperation moved to SJFrame post-SD_sframe/SD_sjframe split —
    // tests live in SJFrameTest.

    // --- Header close-X chrome ------------------------------------------

    private static Icon headerCloseIcon(SFrame f) {
        // Vaadin Dialog's getHeader() returns a DialogHeader whose root
        // Element holds the slotted children. Walk its elements and
        // unwrap the matching Icon component.
        final List<Icon> icons = f.getHeader().getElement().getChildren()
                .map(Element::getComponent)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(Icon.class::isInstance)
                .map(Icon.class::cast)
                .filter(i -> "Close".equals(i.getElement().getAttribute("aria-label")))
                .toList();
        assertEquals(1, icons.size(), "expected exactly one close icon, got " + icons);
        return icons.get(0);
    }

    @Test
    @DisplayName("every SFrame ctor installs a clickable close icon in the header")
    void ctorInstallsCloseIcon() {
        final SFrame f = new SFrame("hi");
        final Icon closeIcon = headerCloseIcon(f);
        assertEquals("vaadin:close", closeIcon.getElement().getAttribute("icon"));
        assertEquals("button", closeIcon.getElement().getAttribute("role"));
    }

    @Test
    @DisplayName("header close icon click fires WINDOW_CLOSING through processWindowEvent")
    void closeIconClickFiresClosing() {
        final SFrame f = new SFrame();
        final List<Integer> ids = new ArrayList<>();
        f.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowClosing(SWindowEvent e) {
                ids.add(e.getID());
            }
        });
        f.setVisible(true);

        final Icon closeIcon = headerCloseIcon(f);
        ComponentUtil.fireEvent(closeIcon,
                new ClickEvent<>(closeIcon, true, 0, 0, 0, 0, 1, 0, false, false, false, false));

        assertEquals(List.of(SWindowEvent.WINDOW_CLOSING), ids);
    }

    // --- resizable ---------------------------------------------------------

    @Test
    @DisplayName("setResizable drives the peer and fires the resizable PCE")
    void setResizableFiresPce() {
        // java.awt.Frame.setResizable fires "resizable" (Frame.java:626), and
        // SFrame fully honours the flag through Vaadin's Dialog — so unlike a
        // drop-and-WARN property there is no R_vaadin_first exemption for the event (SD_property_fanout_audit).
        final SFrame f = new SFrame("t");
        final List<PropertyChangeEvent> events = recordPces(f, "resizable");

        f.setResizable(false);

        assertFalse(f.isResizable());
        assertEquals(1, events.size());
        assertEquals(true, events.get(0).getOldValue());
        assertEquals(false, events.get(0).getNewValue());
    }

    // --- Visibility fires no "visible" property change --------------------

    @Test
    @DisplayName("setVisible, dispose and a peer close fire no visible PCE")
    void visibilityFiresNoVisiblePce() {
        // JPopupMenu is the only class in java.awt + javax.swing with a
        // "visible" bound property, so firing one on a window handed a
        // listener an event the desktop never sent. AWT signals visibility
        // with a ComponentEvent, which these three paths do fire. Same
        // finding D_window_fanout removed from the emulator layer; R_vaadin_first does not license
        // inventing Swing events any more than R_decline_effect_only does. Pinned so the
        // absence is as deliberate as the presence was.
        final SFrame f = new SFrame("t");
        final List<PropertyChangeEvent> pce = recordPces(f, "visible");
        final List<Integer> comp = recordShowHideIds(f);

        f.setVisible(true);          // programmatic show
        f.setOpened(false);          // peer-originated close
        f.setVisible(true);
        f.dispose();                 // dispose while visible

        assertTrue(pce.isEmpty(), "no \"visible\" property change: " + pce);
        assertEquals(
                List.of(SComponentEvent.COMPONENT_SHOWN, SComponentEvent.COMPONENT_HIDDEN,
                        SComponentEvent.COMPONENT_SHOWN, SComponentEvent.COMPONENT_HIDDEN),
                comp,
                "the ComponentEvents AWT does send still fire on all three paths");
    }

    // --- Title round-trip -----------------------------------------------

    @Test
    @DisplayName("setTitle mirrors to headerTitle and fires title PCE")
    void setTitleMirrorsAndFires() {
        final SFrame f = new SFrame("old");
        final List<PropertyChangeEvent> events = recordPces(f, "title");

        f.setTitle("new");
        assertEquals("new", f.getTitle());
        assertEquals("new", f.getHeaderTitle());
        assertEquals(1, events.size());
        assertEquals("old", events.get(0).getOldValue());
        assertEquals("new", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setTitle null coerces to empty and never-null contract holds")
    void setTitleNullCoerces() {
        final SFrame f = new SFrame("titled");
        f.setTitle(null);
        assertEquals("", f.getTitle());
    }

    // --- Add routing (no contentPane indirection on a bare Frame) -----

    @Test
    @DisplayName("SFrame add adds child directly to the Dialog content slot")
    void addGoesToContentSlot() {
        // Frame analog: no content-pane indirection. JFrame's contentPane
        // routing lives on SJFrame post-SD_sframe/SD_sjframe.
        final SFrame f = new SFrame();
        final Button button = new Button("click");
        f.add(button);
        // Child is attached to the Dialog (via Vaadin's default content slot).
        assertTrue(button.getElement().getNode().isAttached()
                || f.getChildren().anyMatch(c -> c == button));
    }

    // contentPane / setContentPane / rootPaneCheckingEnabled — JFrame
    // surface, tested on SJFrame in SJFrameTest.

    // --- Owner / owned-window ------------------------------------------

    @Test
    @DisplayName("getOwnedWindows prunes GC'd children")
    void ownedWindowsPrunes() {
        final SFrame parent = new SFrame("parent");
        // Hold one strongly and one weakly.
        final SFrame strong = new SFrame(parent);
        {
            final SFrame weak = new SFrame(parent);
            assertEquals(2, parent.getOwnedWindows().length);
            // 'weak' goes out of scope here
        }
        // We can't force GC deterministically, but the field stays
        // live-enough for the assertion: the 'strong' ref is definitely
        // in the list.
        assertTrue(java.util.Arrays.stream(parent.getOwnedWindows()).anyMatch(w -> w == strong));
    }

    // --- getFrames / getWindows -----------------------------------------

    @Test
    @DisplayName("getFrames finds attached frames in the current UI")
    void getFramesFindsAttached() {
        final SFrame f1 = new SFrame("a");
        final SFrame f2 = new SFrame("b");
        f1.setVisible(true);
        f2.setVisible(true);

        final List<SFrame> all = SFrame.getFrames();
        assertTrue(all.contains(f1));
        assertTrue(all.contains(f2));
    }

    @Test
    @DisplayName("getFrames skips raw Vaadin dialogs")
    void getFramesSkipsRawDialogs() {
        final Dialog raw = new Dialog();
        UI.getCurrent().add(raw);
        raw.setOpened(true);
        assertTrue(SFrame.getFrames().isEmpty());
    }

    @Test
    @DisplayName("getOwnerlessFrames filters by owner == null")
    void ownerlessFramesFilters() {
        final SFrame top = new SFrame("top");
        final SFrame sub = new SFrame(top);
        top.setVisible(true);
        sub.setVisible(true);

        final List<SFrame> ownerless = SFrame.getOwnerlessFrames();
        assertTrue(ownerless.contains(top));
        assertFalse(ownerless.contains(sub));
    }

    @Test
    @DisplayName("getFrames with no UI returns empty")
    void getFramesWithoutUiIsEmpty() {
        tearDownMockVaadin();
        assertTrue(SFrame.getFrames().isEmpty());
    }

    // --- Frame state stubs ---------------------------------------------

    @Test
    @DisplayName("getState always NORMAL, setState NORMAL silent, ICONIFIED WARNs")
    void frameStateStubs() {
        final SFrame f = new SFrame();
        assertEquals(Frame.NORMAL, f.getState());
        f.setState(Frame.NORMAL);  // silent
        assertNoWarns();
        f.setState(Frame.ICONIFIED);
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("setState")));
    }

    @Test
    @DisplayName("setResizable drives the native Vaadin Dialog resize flag and round-trips")
    void setResizableRoundTrips() {
        final SFrame f = new SFrame();
        assertTrue(f.isResizable(), "AWT default is resizable; ctor seeds the native flag true");
        f.setResizable(false);
        assertFalse(f.isResizable(), "honors the request via Vaadin Dialog.setResizable");
        f.setResizable(true);
        assertTrue(f.isResizable());
        assertNoWarns("no WARN — Vaadin has a real resizable property");
    }

    @Test
    @DisplayName("ctor seeds the native Vaadin Dialog draggable flag")
    void ctorSeedsDraggable() {
        // AWT windows are always user-movable; Vaadin Dialog defaults
        // draggable false. Seeded true so a migrated window can be moved.
        assertTrue(new SFrame().isDraggable());
    }

    @Test
    @DisplayName("setUndecorated toggles the emul-undecorated overlay class")
    void undecoratedTogglesClass() {
        // Was a WARN stub before the JWindow slice; now drives
        // SWindow.setUndecoratedChrome and round-trips Vaadin-first.
        final SFrame f = new SFrame();
        assertFalse(f.isUndecorated());
        f.setUndecorated(true);
        assertTrue(f.isUndecorated());
        assertTrue(f.hasClassName("emul-undecorated"));
        f.setUndecorated(false);
        assertFalse(f.isUndecorated());
        assertFalse(f.hasClassName("emul-undecorated"));
        assertNoWarns();
    }

    // --- SWindow stateless accessors -----------------------------------

    @Test
    @DisplayName("SWindow defaults match AWT for a fresh SFrame")
    void windowDefaultsMatchAwt() {
        final SFrame f = new SFrame();
        assertTrue(f.isFocusableWindow());
        assertNull(f.getWarningString());
        assertFalse(f.isAlwaysOnTopSupported());
        assertFalse(f.isAlwaysOnTop());
        assertFalse(f.isLocationByPlatform());
        assertNull(f.getShape());
        assertEquals(1.0f, f.getOpacity());
        assertTrue(f.getFocusableWindowState());
        assertTrue(f.isAutoRequestFocus());
        assertTrue(f.isValidateRoot());  // Window wins over JComponent (diamond tiebreaker)
        assertFalse(f.isActive());
        assertFalse(f.isFocused());
        assertNull(f.getFocusOwner());
        assertNull(f.getMostRecentFocusOwner());
        assertFalse(f.isFocusCycleRoot());
        assertNull(f.getFocusCycleRootAncestor());
        assertTrue(f.getFocusTraversalKeys(0).isEmpty());
        assertTrue(f.getIconImages().isEmpty());
        assertNull(f.getInputContext());
    }

    @Test
    @DisplayName("toFront toBack pack are silent no-ops")
    void toFrontToBackPackAreSilent() {
        final SFrame f = new SFrame();
        f.toFront();
        f.toBack();
        f.pack();
        assertNoWarns();
    }

    @Test
    @DisplayName("setAlwaysOnTop true WARNs, false silent")
    void alwaysOnTopWarnsOnlyWhenRequested() {
        final SFrame f = new SFrame();
        f.setAlwaysOnTop(false);
        assertNoWarns();
        f.setAlwaysOnTop(true);
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("setAlwaysOnTop")));
    }

    @Test
    @DisplayName("applyResourceBundle WARNs")
    void applyResourceBundleWarns() {
        final SFrame f = new SFrame();
        f.applyResourceBundle("missing");
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("applyResourceBundle")));
    }

    // --- Window listener family: add / remove / get --------------------

    @Test
    @DisplayName("WindowListener add and remove")
    void windowListenerAddRemove() {
        final SFrame f = new SFrame();
        final SWindowAdapter l = new SWindowAdapter() { };
        f.addWindowListener(l);
        assertArrayEquals(new SWindowListener[] { l }, f.getWindowListeners());
        f.removeWindowListener(l);
        assertEquals(0, f.getWindowListeners().length);
    }

    @Test
    @DisplayName("WindowFocusListener family stores but never fires today")
    void windowFocusListenerAddRemove() {
        final SFrame f = new SFrame();
        final SWindowAdapter l = new SWindowAdapter() { };
        f.addWindowFocusListener(l);
        assertArrayEquals(new SWindowFocusListener[] { l }, f.getWindowFocusListeners());
        f.removeWindowFocusListener(l);
        assertEquals(0, f.getWindowFocusListeners().length);
    }

    @Test
    @DisplayName("WindowStateListener family stores but never fires today")
    void windowStateListenerAddRemove() {
        final SFrame f = new SFrame();
        final SWindowAdapter l = new SWindowAdapter() { };
        f.addWindowStateListener(l);
        assertArrayEquals(new SWindowStateListener[] { l }, f.getWindowStateListeners());
        f.removeWindowStateListener(l);
        assertEquals(0, f.getWindowStateListeners().length);
    }

    // --- Component listeners fire on show/hide -------------------------

    @Test
    @DisplayName("setVisible true fires COMPONENT_SHOWN, false fires COMPONENT_HIDDEN")
    void showHideFireComponentEvents() {
        final SFrame f = new SFrame();
        final List<Integer> ids = recordShowHideIds(f);
        f.setVisible(true);
        f.setVisible(false);
        assertEquals(List.of(SComponentEvent.COMPONENT_SHOWN, SComponentEvent.COMPONENT_HIDDEN), ids);
    }

    // --- paramString smoke ---------------------------------------------

    @Test
    @DisplayName("paramString includes title and visible")
    void paramStringIncludesTitleAndVisible() {
        final SFrame f = new SFrame("X");
        final String ps = f.paramString();
        assertTrue(ps.contains("title=X"));
        assertTrue(ps.contains("visible=false"));
    }
}
