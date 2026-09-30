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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;

import java.awt.IllegalComponentStateException;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Exit gate for SJDialog (CRUD edit-form host). Mirrors SJFrameTest
 * but covers JDialog-specific divergences:
 *
 * <ol>
 *  <li><b>Lazy rootPane construction</b> + <b>Content-pane mirror invariant</b> —
 *      same shape as SJFrame.
 *  <li><b>Inherited SFrame behaviour</b> — title / owner / setVisible / dispose
 *      all work unchanged (SJDialog extends SFrame).
 *  <li><b>EXIT_ON_CLOSE rejected at set-time</b> — JDK contract; only JFrame
 *      may terminate the JVM.
 *  <li><b>No close-time throw</b> — since set-time rejects EXIT_ON_CLOSE,
 *      the close-time switch never sees it.
 *  <li><b>getUIClassID is "DialogUI"</b> — JDK contract.
 *  <li><b>JMenuBar slot</b> — JDialog supports setJMenuBar (mirrors SJFrame).
 * </ol>
 */
class SJDialogTest extends AbstractKaribuTest {

    // --- Lazy rootPane construction ---------------------------------------

    @Test
    @DisplayName("getRootPane lazy-constructs a stable SJRootPane")
    void getRootPaneLazyConstructsAStableSjRootPane() {
        SJDialog d = new SJDialog();
        SJRootPane rp = d.getRootPane();
        assertNotNull(rp);
        assertSame(rp, d.getRootPane());
    }

    @Test
    @DisplayName("getRootPane seeds contentPane from the dialog")
    void getRootPaneSeedsContentPaneFromTheDialog() {
        SJDialog d = new SJDialog();
        assertSame(d.getContentPane(), d.getRootPane().getContentPane());
    }

    // --- Content-pane mirror invariant ------------------------------------

    @Test
    @DisplayName("setContentPane after getRootPane mirrors the swap into the root pane")
    void setContentPaneAfterGetRootPaneMirrorsTheSwap() {
        SJDialog d = new SJDialog();
        d.getRootPane();
        Div fresh = new Div();
        d.setContentPane(fresh);
        assertSame(fresh, d.getContentPane());
        assertSame(fresh, d.getRootPane().getContentPane());
    }

    @Test
    @DisplayName("setContentPane null still throws")
    void setContentPaneNullStillThrows() {
        assertThrows(IllegalComponentStateException.class,
                () -> new SJDialog().setContentPane(null));
    }

    // --- Layered / glass pass-throughs ------------------------------------

    @Test
    @DisplayName("getLayeredPane delegates to root pane")
    void getLayeredPaneDelegatesToRootPane() {
        SJDialog d = new SJDialog();
        var lp = d.getLayeredPane();
        assertNotNull(lp);
        assertSame(lp, d.getRootPane().getLayeredPane());
    }

    @Test
    @DisplayName("getGlassPane delegates to root pane")
    void getGlassPaneDelegatesToRootPane() {
        SJDialog d = new SJDialog();
        var gp = d.getGlassPane();
        assertNotNull(gp);
        assertSame(gp, d.getRootPane().getGlassPane());
    }

    // --- createRootPane factory hook --------------------------------------

    /** A distinguishable SJRootPane so the factory hook is observable. */
    private static class CustomRP extends SJRootPane {
    }

    /** Overrides the factory hook the way a migrated subclass would. */
    private static class Fixture extends SJDialog {
        @Override
        protected SJRootPane createRootPane() {
            return new CustomRP();
        }
    }

    @Test
    @DisplayName("subclasses can override createRootPane to supply a custom SJRootPane")
    void subclassesCanOverrideCreateRootPane() {
        Fixture d = new Fixture();
        assertInstanceOf(CustomRP.class, d.getRootPane());
    }

    @Test
    @DisplayName("getDefaultButton reads through the root pane")
    void getDefaultButtonReadsThroughTheRootPane() {
        SJDialog d = new SJDialog();
        Button b = new Button("Go");
        d.getRootPane().setDefaultButton(b);
        assertSame(b, d.getDefaultButton());
    }

    // --- Inherited SFrame behaviour -------------------------------------

    @Test
    @DisplayName("SJDialog title round-trips via SFrame")
    void sjDialogTitleRoundTripsViaSFrame() {
        SJDialog d = new SJDialog("Edit");
        assertEquals("Edit", d.getTitle());
        assertEquals("Edit", d.getHeaderTitle());
    }

    @Test
    @DisplayName("SJDialog content-pane routing redirects add into contentPane")
    void sjDialogContentPaneRoutingRedirectsAdd() {
        SJDialog d = new SJDialog();
        Div child = new Div();
        d.add(child);
        assertTrue(d.getContentPane().getChildren().anyMatch(it -> it == child));
    }

    @Test
    @DisplayName("SJDialog with owner registers in owner's owned-window list")
    void sjDialogWithOwnerRegistersInOwnersOwnedWindowList() {
        SFrame owner = new SFrame();
        SJDialog d = new SJDialog("child", owner);
        assertSame(owner, d.getOwner());
        assertTrue(Arrays.stream(owner.getOwnedWindows()).anyMatch(it -> it == d));
    }

    // --- Default close operation ----------------------------------------

    @Test
    @DisplayName("default ctor installs HIDE_ON_CLOSE")
    void defaultCtorInstallsHideOnClose() {
        assertEquals(WindowConstants.HIDE_ON_CLOSE, new SJDialog().getDefaultCloseOperation());
    }

    @Test
    @DisplayName("setDefaultCloseOperation rejects EXIT_ON_CLOSE per JDK contract")
    void setDefaultCloseOperationRejectsExitOnClose() {
        // JDK JDialog.setDefaultCloseOperation rejects EXIT_ON_CLOSE
        // — only JFrame may terminate the JVM. SJDialog matches.
        SJDialog d = new SJDialog();
        assertThrows(IllegalArgumentException.class,
                () -> d.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE));
    }

    @Test
    @DisplayName("setDefaultCloseOperation rejects unknown values")
    void setDefaultCloseOperationRejectsUnknownValues() {
        SJDialog d = new SJDialog();
        assertThrows(IllegalArgumentException.class, () -> d.setDefaultCloseOperation(99));
    }

    @Test
    @DisplayName("setDefaultCloseOperation DO_NOTHING disables closeOnEsc and closeOnOutsideClick")
    void setDefaultCloseOperationDoNothingDisablesCloseGestures() {
        SJDialog d = new SJDialog();
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        assertFalse(d.isCloseOnEsc());
        assertFalse(d.isCloseOnOutsideClick());
    }

    @Test
    @DisplayName("setDefaultCloseOperation back to HIDE re-enables closeOnEsc")
    void setDefaultCloseOperationBackToHideReEnablesCloseOnEsc() {
        SJDialog d = new SJDialog();
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        d.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        assertTrue(d.isCloseOnEsc());
        assertTrue(d.isCloseOnOutsideClick());
    }

    @Test
    @DisplayName("setDefaultCloseOperation fires PCE")
    void setDefaultCloseOperationFiresPce() {
        SJDialog d = new SJDialog();
        List<PropertyChangeEvent> events = new ArrayList<>();
        d.addPropertyChangeListener("defaultCloseOperation", events::add);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        assertEquals(1, events.size());
        assertEquals(WindowConstants.HIDE_ON_CLOSE, events.get(0).getOldValue());
        assertEquals(WindowConstants.DISPOSE_ON_CLOSE, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("DISPOSE_ON_CLOSE on peer close detaches and fires both CLOSING and CLOSED")
    void disposeOnCloseOnPeerCloseDetachesAndFiresBoth() {
        SJDialog d = new SJDialog();
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        List<Integer> ids = new ArrayList<>();
        d.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowClosing(SWindowEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void windowClosed(SWindowEvent e) {
                ids.add(e.getID());
            }
        });
        d.setVisible(true);
        d.setOpened(false);
        assertEquals(
                List.of(
                        SWindowEvent.WINDOW_CLOSING,
                        SWindowEvent.WINDOW_CLOSED),
                ids);
        assertFalse(d.getElement().getNode().isAttached());
    }

    @Test
    @DisplayName("HIDE_ON_CLOSE via the header close-X actually hides the dialog (not a no-op)")
    void hideOnCloseViaTheHeaderCloseXActuallyHides() {
        // The header close-X fires WINDOW_CLOSING through processWindowEvent
        // WITHOUT first closing the overlay (unlike the native ESC / outside-
        // click path, where onPeerOpenedChanged has already flipped visible).
        // HIDE must perform the real hide here, not assume it already happened.
        SJDialog d = new SJDialog(); // default HIDE_ON_CLOSE
        d.setVisible(true);
        assertTrue(d.isOpened());
        d.processWindowEvent(new SWindowEvent(d, SWindowEvent.WINDOW_CLOSING));
        // isOpened (the Vaadin overlay state) is the observable close signal —
        // the SWindow `visible` shadow isn't exposed as Component.isVisible().
        assertFalse(d.isOpened());
        // HIDE, not DISPOSE: the dialog stays attached so it can be shown again.
        assertTrue(d.getElement().getNode().isAttached());
    }

    @Test
    @DisplayName("HIDE_ON_CLOSE via the native close path stays idempotent (single CLOSING, no re-close)")
    void hideOnCloseViaTheNativeClosePathStaysIdempotent() {
        SJDialog d = new SJDialog(); // default HIDE_ON_CLOSE
        List<Integer> ids = new ArrayList<>();
        d.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowClosing(SWindowEvent e) {
                ids.add(e.getID());
            }
        });
        d.setVisible(true);
        // Native close: overlay closes, onPeerOpenedChanged flips the visible
        // shadow →false then routes WINDOW_CLOSING. The HIDE branch's
        // setVisible(false) must no-op (already false) rather than fire again.
        d.setOpened(false);
        assertFalse(d.isOpened());
        // Exactly one WINDOW_CLOSING — the idempotent HIDE didn't re-close.
        assertEquals(List.of(SWindowEvent.WINDOW_CLOSING), ids);
    }

    // --- paramString + getUIClassID ------------------------------------

    @Test
    @DisplayName("paramString includes title, visible, defaultCloseOperation")
    void paramStringIncludesTitleVisibleDefaultCloseOperation() {
        SJDialog d = new SJDialog("X");
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        String ps = d.paramString();
        assertTrue(ps.contains("title=X"));
        assertTrue(ps.contains("visible=false"));
        assertTrue(ps.contains("DISPOSE_ON_CLOSE"));
    }

    @Test
    @DisplayName("getUIClassID is DialogUI")
    void getUiClassIdIsDialogUi() {
        assertEquals("DialogUI", new SJDialog().getUIClassID());
    }

    // --- JMenuBar slot --------------------------------------------------

    @Test
    @DisplayName("getJMenuBar is null until set")
    void getJMenuBarIsNullUntilSet() {
        assertNull(new SJDialog().getJMenuBar());
    }

    @Test
    @DisplayName("setJMenuBar attaches the menubar as a Dialog child")
    void setJMenuBarAttachesTheMenubarAsADialogChild() {
        SJDialog d = new SJDialog();
        SJMenuBar bar = new SJMenuBar();
        d.setJMenuBar(bar);
        assertSame(bar, d.getJMenuBar());
        assertTrue(bar.getElement().getNode().isAttached() || bar.getElement().getParent() != null);
    }

    @Test
    @DisplayName("setJMenuBar(null) detaches the menubar")
    void setJMenuBarNullDetachesTheMenubar() {
        SJDialog d = new SJDialog();
        SJMenuBar bar = new SJMenuBar();
        d.setJMenuBar(bar);
        d.setJMenuBar(null);
        assertNull(d.getJMenuBar());
        assertFalse(bar.getElement().getNode().isAttached());
    }

    // --- No stub WARNs on happy path ------------------------------------

    @Test
    @DisplayName("full SJDialog happy path fires no stub WARNs")
    void fullSjDialogHappyPathFiresNoStubWarns() {
        SJDialog d = new SJDialog("Edit");
        d.getRootPane();
        d.getLayeredPane();
        d.getGlassPane();
        d.setContentPane(new Div());
        d.getRootPane().setDefaultButton(new Button("OK"));
        d.getDefaultButton();
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setJMenuBar(new SJMenuBar());
        assertEquals(
                List.of(),
                capturedWarns,
                "no stub WARNs expected on SJDialog happy path");
    }

    // --- A window is never content (D_window_split) ----------------------

    @Test
    @DisplayName("an SWindow child nests under the dialog element, not in the content pane")
    void windowChildNestsUnderTheDialogNotTheContentPane() {
        SJDialog d = new SJDialog("host");
        long contentChildren = d.getContentPane().getChildren().count();

        SJWindow window = new SJWindow();
        d.add(window);

        assertEquals(contentChildren, d.getContentPane().getChildren().count(),
                "the content pane is untouched — a window is not content");
        assertEquals(d.getElement(), window.getElement().getParent(),
                "the window nests directly under the host element");
    }

    @Test
    @DisplayName("removing an SWindow child detaches it from the dialog element")
    void removingAWindowChildDetachesItFromTheDialog() {
        SJDialog d = new SJDialog("host");
        SJWindow window = new SJWindow();
        d.add(window);

        d.remove(window);

        assertNull(window.getElement().getParent());
    }

    @Test
    @DisplayName("a mixed add splits: content to the content pane, window under the host")
    void mixedAddSplitsContentFromWindow() {
        SJDialog d = new SJDialog("host");
        Div content = new Div();
        SJWindow window = new SJWindow();

        d.add(content, window);

        assertEquals(d.getContentPane().getElement(), content.getElement().getParent());
        assertEquals(d.getElement(), window.getElement().getParent());
    }

    // --- Distinct from SJFrame ------------------------------------------

    @Test
    @DisplayName("SJDialog and SJFrame have independent SJRootPanes")
    void sjDialogAndSjFrameHaveIndependentRootPanes() {
        SJFrame frame = new SJFrame();
        SJDialog dialog = new SJDialog();
        assertNotSame(frame.getRootPane(), dialog.getRootPane());
    }
}
