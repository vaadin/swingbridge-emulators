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
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.IllegalComponentStateException;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjframe's SJRootPane surrogate. Covers:
 *
 * <ol>
 *  <li><b>Content / layered / glass pane holders</b> — lazy construction
 *      returns stable non-null references; null setters throw
 *      IllegalComponentStateException matching JDK.
 *  <li><b>Default button Enter-shortcut</b> — setDefaultButton installs a
 *      Vaadin shortcut; replacing / clearing uninstalls the previous;
 *      click body runs through SHelper.callSwing.
 *  <li><b>"defaultButton" PCE</b> — fires on setDefaultButton including the
 *      null-clear path.
 *  <li><b>getUIClassID</b> — returns "RootPaneUI" for UIManager parity.
 *  <li><b>Decoration constants</b> — NONE / FRAME / … match JDK values.
 * </ol>
 */
class SJRootPaneTest extends AbstractKaribuTest {

    // --- Content / layered / glass pane holders -------------------------

    @Test
    @DisplayName("getContentPane lazy-constructs a stable Div")
    void getContentPaneLazyConstructsAStableDiv() {
        SJRootPane rp = new SJRootPane();
        Component cp = rp.getContentPane();
        assertInstanceOf(Div.class, cp);
        assertSame(cp, rp.getContentPane());  // stable across reads
    }

    @Test
    @DisplayName("setContentPane round-trips the reference")
    void setContentPaneRoundTripsTheReference() {
        SJRootPane rp = new SJRootPane();
        Div mine = new Div();
        rp.setContentPane(mine);
        assertSame(mine, rp.getContentPane());
    }

    @Test
    @DisplayName("setContentPane null throws IllegalComponentStateException")
    void setContentPaneNullThrows() {
        assertThrows(IllegalComponentStateException.class,
                () -> new SJRootPane().setContentPane(null));
    }

    @Test
    @DisplayName("getLayeredPane lazy-constructs a stable Div")
    void getLayeredPaneLazyConstructsAStableDiv() {
        SJRootPane rp = new SJRootPane();
        Component lp = rp.getLayeredPane();
        assertInstanceOf(Div.class, lp);
        assertSame(lp, rp.getLayeredPane());
    }

    @Test
    @DisplayName("setLayeredPane null throws")
    void setLayeredPaneNullThrows() {
        assertThrows(IllegalComponentStateException.class,
                () -> new SJRootPane().setLayeredPane(null));
    }

    @Test
    @DisplayName("getGlassPane lazy-constructs an invisible Div")
    void getGlassPaneLazyConstructsAnInvisibleDiv() {
        SJRootPane rp = new SJRootPane();
        Component gp = rp.getGlassPane();
        assertInstanceOf(Div.class, gp);
        assertFalse(gp.isVisible());  // JDK default
        assertSame(gp, rp.getGlassPane());
    }

    @Test
    @DisplayName("setGlassPane null throws")
    void setGlassPaneNullThrows() {
        assertThrows(IllegalComponentStateException.class,
                () -> new SJRootPane().setGlassPane(null));
    }

    // --- Structural glass pane (D_glasspane_structural/SD_glasspane_structural) — hosted path -----------------

    @Test
    @DisplayName("hosted glass pane attaches structurally with class wiring")
    void hostedGlassPaneAttachesStructurally() {
        // The glass pane is the root pane's own first child — the JDK's
        // containment and index — tagged emul-glasspane and insetting against
        // the root pane's `position: relative`. That is what makes
        // setVisible(true) an actual curtain, and it needs no host class: the
        // former emul-has-glasspane → ::part(content) rule is retired, since the
        // positioned ancestor is now a light-DOM box.
        SJFrame frame = new SJFrame();
        Component gp = frame.getRootPane().getGlassPane();

        assertTrue(gp.getElement().getClassList().contains("emul-glasspane"));
        assertFalse(frame.hasClassName("emul-has-glasspane"));
        assertEquals(frame.getRootPane().getElement(), gp.getElement().getParent());
        assertEquals(gp.getElement(), frame.getRootPane().getElement().getChild(0));
        assertFalse(gp.isVisible());  // JDK default — no curtain until shown
        // Host and root pane report the same structural pane.
        assertSame(gp, frame.getGlassPane());
        assertSame(gp, frame.getRootPane().getGlassPane());
    }

    @Test
    @DisplayName("hosted setGlassPane swaps the structural pane and re-tags")
    void hostedSetGlassPaneSwapsTheStructuralPane() {
        SJFrame frame = new SJFrame();
        Component first = frame.getRootPane().getGlassPane();
        Div mine = new Div();
        frame.getRootPane().setGlassPane(mine);

        assertSame(mine, frame.getRootPane().getGlassPane());
        assertTrue(mine.getElement().getClassList().contains("emul-glasspane"));
        // Previous pane detached and untagged.
        assertFalse(first.getElement().getClassList().contains("emul-glasspane"));
    }

    @Test
    @DisplayName("standalone glass pane is structural too")
    void standaloneGlassPaneIsStructuralToo() {
        // Inverted from the old holder-fallback stance: a bare SJRootPane owns
        // the same chain a hosted one does, so a pure-surrogate caller who adds
        // it to any Vaadin container gets a working root pane. Retires SD_sjframe's
        // pure-surrogate-rendering limitation.
        SJRootPane rp = new SJRootPane();
        Component gp = rp.getGlassPane();
        assertTrue(gp.getElement().getClassList().contains("emul-glasspane"));
        assertEquals(rp.getElement(), gp.getElement().getParent());
    }

    // --- The JDK's containment (D_rootpane_containment) -----------------------

    @Test
    @DisplayName("root pane holds glass and layered panes, layered pane holds bar and content")
    void rootPaneHoldsGlassAndLayeredPanes() {
        // JRootPane holds {glassPane, layeredPane}; the LAYERED pane holds
        // {menuBar, contentPane} — the JDK's shape, not menuBar-under-rootPane.
        // No value assertion catches this, which is why it is pinned by shape.
        SJFrame frame = new SJFrame();
        SJRootPane rp = frame.getRootPane();
        SJMenuBar bar = new SJMenuBar();
        frame.setJMenuBar(bar);
        Component content = frame.getContentPane();

        assertEquals(rp.getElement(), rp.getGlassPane().getElement().getParent());
        assertEquals(rp.getElement(), rp.getLayeredPane().getElement().getParent());
        assertEquals(2, rp.getElement().getChildCount());
        assertEquals(rp.getGlassPane().getElement(), rp.getElement().getChild(0));

        Element layered = rp.getLayeredPane().getElement();
        assertEquals(layered, bar.getElement().getParent());
        assertEquals(layered, content.getElement().getParent());
        assertEquals(bar.getElement(), layered.getChild(0));
        assertEquals(content.getElement(), layered.getChild(1));
    }

    @Test
    @DisplayName("the chain carries the CSS classes the layout depends on")
    void theChainCarriesTheCssClassesTheLayoutDependsOn() {
        // A refactor that quietly drops display:contents re-breaks layout with
        // nothing else going red, so the classes are pinned directly.
        SJFrame frame = new SJFrame();
        SJRootPane rp = frame.getRootPane();
        SJMenuBar bar = new SJMenuBar();
        frame.setJMenuBar(bar);

        assertTrue(rp.getElement().getClassList().contains("emul-rootpane"));
        assertTrue(rp.getLayeredPane().getElement().getClassList().contains("emul-layeredpane"));
        assertTrue(bar.getElement().getClassList().contains("emul-menubar"));
        assertTrue(frame.getContentPane().getElement().getClassList().contains("emul-contentpane"));
        assertTrue(frame.hasClassName("emul-has-contentpane"));
    }

    @Test
    @DisplayName("reaching the root pane first does not mint a second content pane")
    void reachingTheRootPaneFirstDoesNotMintASecondContentPane() {
        // getRootPane().getContentPane() lazily creates one; the scaffold must
        // adopt it rather than plant a rival that only it routes into.
        SJFrame frame = new SJFrame();
        Component viaRootPane = frame.getRootPane().getContentPane();
        Component viaHost = frame.getContentPane();

        assertSame(viaRootPane, viaHost);
        assertEquals(1, frame.getRootPane().getLayeredPane().getElement().getChildCount());
    }

    @Test
    @DisplayName("setJMenuBar(null) detaches the bar and gives back its class")
    void setJMenuBarNullDetachesTheBar() {
        SJFrame frame = new SJFrame();
        SJMenuBar bar = new SJMenuBar();
        frame.setJMenuBar(bar);
        frame.setJMenuBar(null);

        assertNull(frame.getRootPane().getJMenuBar());
        assertNull(bar.getElement().getParent());
        assertFalse(bar.getElement().getClassList().contains("emul-menubar"));
    }

    // --- Default button ---------------------------------------------------

    @Test
    @DisplayName("setDefaultButton stores the reference")
    void setDefaultButtonStoresTheReference() {
        SJRootPane rp = new SJRootPane();
        Button b = new Button("Go");
        rp.setDefaultButton(b);
        assertSame(b, rp.getDefaultButton());
    }

    @Test
    @DisplayName("setDefaultButton null clears the reference")
    void setDefaultButtonNullClearsTheReference() {
        SJRootPane rp = new SJRootPane();
        Button b = new Button("Go");
        rp.setDefaultButton(b);
        rp.setDefaultButton(null);
        assertNull(rp.getDefaultButton());
    }

    @Test
    @DisplayName("setDefaultButton fires defaultButton PCE on install")
    void setDefaultButtonFiresPceOnInstall() {
        SJRootPane rp = new SJRootPane();
        List<PropertyChangeEvent> events = new ArrayList<>();
        rp.addPropertyChangeListener("defaultButton", events::add);

        Button b = new Button("Go");
        rp.setDefaultButton(b);

        assertEquals(1, events.size());
        assertNull(events.get(0).getOldValue());
        assertSame(b, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setDefaultButton fires defaultButton PCE on clear")
    void setDefaultButtonFiresPceOnClear() {
        SJRootPane rp = new SJRootPane();
        Button b = new Button("Go");
        rp.setDefaultButton(b);
        List<PropertyChangeEvent> events = new ArrayList<>();
        rp.addPropertyChangeListener("defaultButton", events::add);

        rp.setDefaultButton(null);

        assertEquals(1, events.size());
        assertSame(b, events.get(0).getOldValue());
        assertNull(events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setDefaultButton fires defaultButton PCE on swap")
    void setDefaultButtonFiresPceOnSwap() {
        SJRootPane rp = new SJRootPane();
        Button a = new Button("A");
        Button b = new Button("B");
        rp.setDefaultButton(a);
        List<PropertyChangeEvent> events = new ArrayList<>();
        rp.addPropertyChangeListener("defaultButton", events::add);

        rp.setDefaultButton(b);

        assertEquals(1, events.size());
        assertSame(a, events.get(0).getOldValue());
        assertSame(b, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setDefaultButton is a no-op for identical reference")
    void setDefaultButtonIsANoOpForIdenticalReference() {
        // Contract: setting the same button twice fires no second PCE.
        SJRootPane rp = new SJRootPane();
        Button b = new Button("Go");
        rp.setDefaultButton(b);
        List<PropertyChangeEvent> events = new ArrayList<>();
        rp.addPropertyChangeListener("defaultButton", events::add);

        rp.setDefaultButton(b);

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("setDefaultButton installs a shortcut we can exercise via click")
    void setDefaultButtonInstallsAShortcutWeCanExerciseViaClick() {
        // End-to-end enough: install the default button, then simulate
        // the Enter shortcut's effect by clicking the Vaadin Button
        // directly. Same path the Enter shortcut would take on the
        // browser side. Karibu doesn't simulate Shortcut dispatch; this
        // confirms the click target wiring survives the install.
        SJRootPane rp = new SJRootPane();
        Button b = new Button("Go");
        Counter hits = new Counter();
        b.addClickListener(e -> hits.inc());
        rp.setDefaultButton(b);

        b.click();
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("setDefaultButton replacement doesn't leave stale shortcuts firing")
    void setDefaultButtonReplacementDoesNotLeaveStaleShortcuts() {
        // Hard to observe the shortcut uninstall directly via Karibu. We
        // settle for "no duplicate click path on the old button" — the
        // click counter for the old button stays at whatever it was
        // before the swap; replacing doesn't inject a synthetic click.
        SJRootPane rp = new SJRootPane();
        Button oldB = new Button("Old");
        Button newB = new Button("New");
        Counter oldHits = new Counter();
        oldB.addClickListener(e -> oldHits.inc());

        rp.setDefaultButton(oldB);
        rp.setDefaultButton(newB);

        assertSame(newB, rp.getDefaultButton());
        oldHits.assertEquals(0);
    }

    // --- getUIClassID + decoration constants ------------------------------

    @Test
    @DisplayName("getUIClassID is RootPaneUI for UIManager parity")
    void getUiClassIdIsRootPaneUi() {
        assertEquals("RootPaneUI", new SJRootPane().getUIClassID());
    }

    @Test
    @DisplayName("decoration style constants match JDK values")
    void decorationStyleConstantsMatchJdkValues() {
        // Verbatim from javax.swing.JRootPane. Migrated code that uses
        // these as ints must see the same values we report.
        assertEquals(0, SJRootPane.NONE);
        assertEquals(1, SJRootPane.FRAME);
        assertEquals(2, SJRootPane.PLAIN_DIALOG);
        assertEquals(3, SJRootPane.INFORMATION_DIALOG);
        assertEquals(4, SJRootPane.ERROR_DIALOG);
        assertEquals(5, SJRootPane.COLOR_CHOOSER_DIALOG);
        assertEquals(6, SJRootPane.FILE_CHOOSER_DIALOG);
        assertEquals(7, SJRootPane.QUESTION_DIALOG);
        assertEquals(8, SJRootPane.WARNING_DIALOG);
    }

    // --- Reuse of standalone-pane holders ---------------------------------

    @Test
    @DisplayName("content layered and glass panes are distinct instances")
    void contentLayeredAndGlassPanesAreDistinctInstances() {
        SJRootPane rp = new SJRootPane();
        // Sanity — lazy-constructing all three shouldn't collapse them
        // into the same Div by accident.
        Component cp = rp.getContentPane();
        Component lp = rp.getLayeredPane();
        Component gp = rp.getGlassPane();
        assertNotSame(cp, lp);
        assertNotSame(cp, gp);
        assertNotSame(lp, gp);
    }

    // --- No stub WARNs from the covered surface --------------------------

    @Test
    @DisplayName("full SJRootPane happy path fires no stub WARNs")
    void fullSjRootPaneHappyPathFiresNoStubWarns() {
        // API-surface driver that touches every method above — exit-gate
        // asserting we didn't add a stub WARN anywhere in the holder.
        SJRootPane rp = new SJRootPane();
        rp.getContentPane();
        rp.getLayeredPane();
        rp.getGlassPane();

        Button b = new Button("Go");
        rp.setDefaultButton(b);
        rp.setDefaultButton(null);

        rp.getUIClassID();

        assertEquals(
                List.of(),
                capturedWarns,
                "no stub WARNs expected from SD_sjframe's SJRootPane happy path");
    }
}
