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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;

import java.awt.IllegalComponentStateException;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.WindowConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjframe's SJFrame surrogate. Covers:
 *
 * <ol>
 *  <li><b>Lazy rootPane construction</b> — getRootPane() builds on first
 *      read via the createRootPane factory hook; stable thereafter.
 *  <li><b>Content-pane mirror invariant</b> — setContentPane mirrors into
 *      the already-constructed SJRootPane so
 *      {@code frame.getRootPane().getContentPane() == frame.getContentPane()}.
 *  <li><b>Layered / glass pass-throughs</b> — delegate to SJRootPane;
 *      reading them forces lazy construction of both the SJRootPane and
 *      the pane itself.
 *  <li><b>createRootPane factory hook</b> — subclasses can override to
 *      supply a custom SJRootPane.
 *  <li><b>Inherited SFrame behaviour</b> — SJFrame IS-A SFrame, so title /
 *      defaultCloseOperation / content-pane routing all work unchanged.
 *  <li><b>Default button convenience accessor</b> — getDefaultButton()
 *      reads through getRootPane().
 * </ol>
 */
class SJFrameTest extends AbstractKaribuTest {

    /** A distinguishable SJRootPane so the factory hook is observable. */
    private static class CustomRP extends SJRootPane {
    }

    /** Overrides the factory hook the way a migrated subclass would. */
    private static class Fixture extends SJFrame {
        @Override
        protected SJRootPane createRootPane() {
            return new CustomRP();
        }
    }

    // --- Lazy rootPane construction ---------------------------------------

    @Test
    @DisplayName("getRootPane lazy-constructs a stable SJRootPane")
    void getRootPaneLazyConstructsAStableSjRootPane() {
        SJFrame f = new SJFrame();
        SJRootPane rp = f.getRootPane();
        assertNotNull(rp);
        assertSame(rp, f.getRootPane());  // stable across reads
    }

    @Test
    @DisplayName("getRootPane seeds contentPane from the frame")
    void getRootPaneSeedsContentPaneFromTheFrame() {
        // The invariant migrated code relies on: reading through the root
        // pane's contentPane accessor returns the same Div as reading
        // through the frame directly.
        SJFrame f = new SJFrame();
        assertSame(f.getContentPane(), f.getRootPane().getContentPane());
    }

    // --- Content-pane mirror invariant ------------------------------------

    @Test
    @DisplayName("setContentPane after getRootPane mirrors the swap into the root pane")
    void setContentPaneAfterGetRootPaneMirrorsTheSwap() {
        SJFrame f = new SJFrame();
        f.getRootPane();  // force lazy construction
        Div fresh = new Div();

        f.setContentPane(fresh);

        assertSame(fresh, f.getContentPane());
        assertSame(fresh, f.getRootPane().getContentPane());
    }

    @Test
    @DisplayName("setContentPane before getRootPane doesn't eagerly construct")
    void setContentPaneBeforeGetRootPaneDoesNotEagerlyConstruct() {
        // Lazy contract: setContentPane on its own mustn't force the root
        // pane to materialise. When the caller later reads the root pane,
        // the lazy seed picks up the current (post-swap) contentPane.
        SJFrame f = new SJFrame();
        Div fresh = new Div();
        f.setContentPane(fresh);

        // First getRootPane triggers construction + seeds with the
        // current frame contentPane.
        SJRootPane rp = f.getRootPane();
        assertSame(fresh, rp.getContentPane());
    }

    @Test
    @DisplayName("setContentPane null still throws at the SFrame layer")
    void setContentPaneNullStillThrows() {
        // Inherited behaviour — SFrame.setContentPane rejects null per JDK;
        // SJFrame's override adds the rootPane mirror, doesn't soften the
        // null rule.
        assertThrows(IllegalComponentStateException.class,
                () -> new SJFrame().setContentPane(null));
    }

    // --- Layered / glass pass-throughs ------------------------------------

    @Test
    @DisplayName("getLayeredPane delegates to root pane")
    void getLayeredPaneDelegatesToRootPane() {
        SJFrame f = new SJFrame();
        Component lp = f.getLayeredPane();
        assertNotNull(lp);
        assertSame(lp, f.getRootPane().getLayeredPane());
    }

    @Test
    @DisplayName("getGlassPane delegates to root pane")
    void getGlassPaneDelegatesToRootPane() {
        SJFrame f = new SJFrame();
        Component gp = f.getGlassPane();
        assertNotNull(gp);
        assertSame(gp, f.getRootPane().getGlassPane());
    }

    @Test
    @DisplayName("setLayeredPane delegates to root pane")
    void setLayeredPaneDelegatesToRootPane() {
        SJFrame f = new SJFrame();
        Div mine = new Div();
        f.setLayeredPane(mine);
        assertSame(mine, f.getRootPane().getLayeredPane());
    }

    @Test
    @DisplayName("setGlassPane delegates to root pane")
    void setGlassPaneDelegatesToRootPane() {
        SJFrame f = new SJFrame();
        Div mine = new Div();
        f.setGlassPane(mine);
        assertSame(mine, f.getRootPane().getGlassPane());
    }

    // --- createRootPane factory hook --------------------------------------

    @Test
    @DisplayName("subclasses can override createRootPane to supply a custom SJRootPane")
    void subclassesCanOverrideCreateRootPane() {
        Fixture f = new Fixture();
        assertInstanceOf(CustomRP.class, f.getRootPane());
    }

    // --- Default button convenience accessor ------------------------------

    @Test
    @DisplayName("getDefaultButton reads through the root pane")
    void getDefaultButtonReadsThroughTheRootPane() {
        SJFrame f = new SJFrame();
        Button b = new Button("Go");
        f.getRootPane().setDefaultButton(b);
        assertSame(b, f.getDefaultButton());
    }

    // --- Inherited SFrame behaviour remains intact ------------------------

    @Test
    @DisplayName("SJFrame title round-trips via SFrame")
    void sjFrameTitleRoundTripsViaSFrame() {
        SJFrame f = new SJFrame("Hello");
        assertEquals("Hello", f.getTitle());
        assertEquals("Hello", f.getHeaderTitle());
    }

    @Test
    @DisplayName("SJFrame content-pane routing - add redirects into contentPane")
    void sjFrameContentPaneRoutingAddRedirects() {
        SJFrame f = new SJFrame();
        Div child = new Div();
        f.add(child);
        // SFrame's routing: child lands in contentPane, not Dialog directly.
        assertTrue(f.getContentPane().getChildren().anyMatch(it -> it == child));
    }

    // --- No stub WARNs from the covered surface --------------------------

    @Test
    @DisplayName("full SJFrame happy path fires no stub WARNs")
    void fullSjFrameHappyPathFiresNoStubWarns() {
        SJFrame f = new SJFrame("Hello");
        f.getRootPane();
        f.getLayeredPane();
        f.getGlassPane();
        f.setContentPane(new Div());
        f.getRootPane().setDefaultButton(new Button("Go"));
        f.getDefaultButton();  // convenience accessor

        assertEquals(
                List.of(),
                capturedWarns,
                "no stub WARNs expected from SD_sjframe's SJFrame happy path");
    }

    // --- Distinctness from other top-level surrogate ---------------------

    @Test
    @DisplayName("two SJFrames have independent SJRootPanes")
    void twoSjFramesHaveIndependentRootPanes() {
        SJFrame a = new SJFrame();
        SJFrame b = new SJFrame();
        assertNotSame(a.getRootPane(), b.getRootPane());
    }

    // --- Default close operation (moved from SFrame post-SD_sframe/SD_sjframe) -----

    @Test
    @DisplayName("default ctor installs HIDE_ON_CLOSE")
    void defaultCtorInstallsHideOnClose() {
        SJFrame f = new SJFrame();
        assertEquals(WindowConstants.HIDE_ON_CLOSE, f.getDefaultCloseOperation());
    }

    @Test
    @DisplayName("setDefaultCloseOperation validates unknown values")
    void setDefaultCloseOperationValidatesUnknownValues() {
        SJFrame f = new SJFrame();
        assertThrows(IllegalArgumentException.class, () -> f.setDefaultCloseOperation(99));
    }

    @Test
    @DisplayName("setDefaultCloseOperation DO_NOTHING disables closeOnEsc and closeOnOutsideClick")
    void setDefaultCloseOperationDoNothingDisablesCloseGestures() {
        SJFrame f = new SJFrame();
        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        assertFalse(f.isCloseOnEsc());
        assertFalse(f.isCloseOnOutsideClick());
    }

    @Test
    @DisplayName("setDefaultCloseOperation back to HIDE re-enables closeOnEsc")
    void setDefaultCloseOperationBackToHideReEnablesCloseOnEsc() {
        SJFrame f = new SJFrame();
        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        f.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        assertTrue(f.isCloseOnEsc());
        assertTrue(f.isCloseOnOutsideClick());
    }

    @Test
    @DisplayName("setDefaultCloseOperation EXIT_ON_CLOSE is silent at set-time")
    void setDefaultCloseOperationExitOnCloseIsSilentAtSetTime() {
        // D_gap_severity_triage: setting EXIT is silent; the throw trigger fires at close-time.
        SJFrame f = new SJFrame();
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        assertNoWarns("EXIT_ON_CLOSE set should not WARN");
    }

    @Test
    @DisplayName("EXIT_ON_CLOSE peer-originated close throws IllegalStateException per D_gap_severity_triage")
    void exitOnClosePeerOriginatedCloseThrows() {
        // D_gap_severity_triage's second throw case. A peer-originated close-attempt while
        // EXIT_ON_CLOSE is set throws ISE rather than letting the
        // migrator's "terminate" intent silently noop.
        SJFrame f = new SJFrame();
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setVisible(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.setOpened(false));
        assertTrue(ex.getMessage().contains("EXIT_ON_CLOSE"));
        assertTrue(ex.getMessage().contains("D_gap_severity_triage"));
    }

    @Test
    @DisplayName("DISPOSE_ON_CLOSE on peer close detaches, fires both CLOSING and CLOSED")
    void disposeOnCloseOnPeerCloseDetaches() {
        SJFrame f = new SJFrame();
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        List<Integer> ids = new ArrayList<>();
        f.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowClosing(SWindowEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void windowClosed(SWindowEvent e) {
                ids.add(e.getID());
            }
        });

        f.setVisible(true);
        f.setOpened(false);

        assertEquals(
                List.of(
                        SWindowEvent.WINDOW_CLOSING,
                        SWindowEvent.WINDOW_CLOSED),
                ids);
        assertFalse(f.getElement().getNode().isAttached());
    }

    @Test
    @DisplayName("setDefaultCloseOperation fires PCE")
    void setDefaultCloseOperationFiresPce() {
        SJFrame f = new SJFrame();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("defaultCloseOperation", events::add);

        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        assertEquals(1, events.size());
        assertEquals(WindowConstants.HIDE_ON_CLOSE, events.get(0).getOldValue());
        assertEquals(WindowConstants.DISPOSE_ON_CLOSE, events.get(0).getNewValue());

        // Same-value set: skip-on-equal, no PCE.
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        assertEquals(1, events.size());
    }

    // --- Content pane (moved from SFrame post-SD_sframe/SD_sjframe) ----------------

    @Test
    @DisplayName("add routes into lazy content pane")
    void addRoutesIntoLazyContentPane() {
        SJFrame f = new SJFrame();
        Div child = new Div();
        f.add(child);

        Component pane = f.getContentPane();
        assertNotNull(pane);
        assertTrue(pane.getChildren().anyMatch(it -> it == child));
        assertFalse(f.getChildren().anyMatch(it -> it == child));
    }

    @Test
    @DisplayName("content pane is reused across multiple adds")
    void contentPaneIsReusedAcrossMultipleAdds() {
        SJFrame f = new SJFrame();
        f.add(new Div());
        Component firstPane = f.getContentPane();
        f.add(new Div());
        assertSame(firstPane, f.getContentPane());
        assertEquals(2L, firstPane.getChildren().count());
    }

    @Test
    @DisplayName("remove routes into content pane")
    void removeRoutesIntoContentPane() {
        SJFrame f = new SJFrame();
        Div child = new Div();
        f.add(child);
        f.remove(child);
        assertEquals(0L, f.getContentPane().getChildren().count());
    }

    @Test
    @DisplayName("removeAll routes into content pane")
    void removeAllRoutesIntoContentPane() {
        SJFrame f = new SJFrame();
        f.add(new Div());
        f.add(new Div());
        f.removeAll();
        assertEquals(0L, f.getContentPane().getChildren().count());
    }

    @Test
    @DisplayName("getContentPane lazy-creates and returns a Div")
    void getContentPaneLazyCreatesAndReturnsADiv() {
        SJFrame f = new SJFrame();
        Component pane = f.getContentPane();
        assertNotNull(pane);
        assertSame(pane, f.getContentPane());
    }

    @Test
    @DisplayName("setContentPane swaps in new pane and detaches old")
    void setContentPaneSwapsInNewPaneAndDetachesOld() {
        SJFrame f = new SJFrame();
        Component old = f.getContentPane();
        Div fresh = new Div();
        f.setContentPane(fresh);
        assertSame(fresh, f.getContentPane());
        assertFalse(old.getElement().getNode().isAttached());
    }

    // --- paramString + getUIClassID ------------------------------------

    @Test
    @DisplayName("paramString includes title, visible, defaultCloseOperation")
    void paramStringIncludesTitleVisibleDefaultCloseOperation() {
        SJFrame f = new SJFrame("X");
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        String ps = f.paramString();
        assertTrue(ps.contains("title=X"));
        assertTrue(ps.contains("visible=false"));
        assertTrue(ps.contains("DISPOSE_ON_CLOSE"));
    }

    @Test
    @DisplayName("getUIClassID is FrameUI")
    void getUiClassIdIsFrameUi() {
        assertEquals("FrameUI", new SJFrame().getUIClassID());
    }

    // --- JMenuBar slot (SD_sjmenubar / D_menu_tree) ----------------------------------

    @Test
    @DisplayName("getJMenuBar is null until set")
    void getJMenuBarIsNullUntilSet() {
        assertNull(new SJFrame().getJMenuBar());
    }

    @Test
    @DisplayName("setJMenuBar attaches the menubar as a Dialog child")
    void setJMenuBarAttachesTheMenubar() {
        SJFrame f = new SJFrame();
        SJMenuBar bar = new SJMenuBar();
        f.setJMenuBar(bar);
        assertSame(bar, f.getJMenuBar());
        assertTrue(bar.getElement().getNode().isAttached() || bar.getElement().getParent() != null);
    }

    @Test
    @DisplayName("setJMenuBar(null) detaches the menubar")
    void setJMenuBarNullDetachesTheMenubar() {
        SJFrame f = new SJFrame();
        SJMenuBar bar = new SJMenuBar();
        f.setJMenuBar(bar);
        f.setJMenuBar(null);
        assertNull(f.getJMenuBar());
        assertFalse(bar.getElement().getNode().isAttached());
    }

    @Test
    @DisplayName("setJMenuBar swaps the previous menubar")
    void setJMenuBarSwapsThePreviousMenubar() {
        SJFrame f = new SJFrame();
        SJMenuBar bar1 = new SJMenuBar();
        SJMenuBar bar2 = new SJMenuBar();
        f.setJMenuBar(bar1);
        f.setJMenuBar(bar2);
        assertSame(bar2, f.getJMenuBar());
        assertFalse(bar1.getElement().getNode().isAttached());
    }

    @Test
    @DisplayName("setJMenuBar(same) is a no-op")
    void setJMenuBarSameIsANoOp() {
        SJFrame f = new SJFrame();
        SJMenuBar bar = new SJMenuBar();
        f.setJMenuBar(bar);
        f.setJMenuBar(bar);
        assertSame(bar, f.getJMenuBar());
    }

    @Test
    @DisplayName("menubar coexists with contentPane after content already created")
    void menubarCoexistsWithContentPane() {
        SJFrame f = new SJFrame();
        ((Div) f.getContentPane()).add(new Div());  // force lazy creation first
        SJMenuBar bar = new SJMenuBar();
        f.setJMenuBar(bar);
        // Both are reachable on the SJFrame; both attached as
        // Dialog-direct children (Dialog defers DOM attach until
        // open, but the element parent links are set up immediately).
        assertSame(bar, f.getJMenuBar());
        assertNotNull(bar.getElement().getParent());
        assertNotNull(f.getContentPane().getElement().getParent());
    }
}
