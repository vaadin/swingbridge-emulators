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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for the {@code vaadinx.awt.Button} emulator — the AWT 1.0 push
 * button, not {@code vaadinx.swing.JButton}. Beyond the twelve-method API this
 * is the first plain AWT leaf widget in the tree, so it doubles as the
 * check that {@code vaadinx.awt.Component} carries a non-JComponent leaf and
 * that such a leaf drops into a Swing container. See ideas/awt-widgets.md.
 *
 * <p>Java has no import alias, so the Vaadin peer type is spelled out in full
 * at every {@code _get} — {@code Button} here is unqualified for the emulator.
 */
class ButtonTest extends AbstractKaribuTest {

    /** The rendered Vaadin peer of the single button under test. */
    private static com.vaadin.flow.component.button.Button peer() {
        return LocatorJ._get(com.vaadin.flow.component.button.Button.class);
    }

    // --- Ctors / label ------------------------------------------------

    @Test
    @DisplayName("no-arg ctor labels the button empty, matching AWT's this-empty-string chain")
    void noArgCtorLabelsTheButtonEmptyMatchingAwtsThisEmptyStringChain() {
        // JDK: public Button() { this(""); } — empty, deliberately not null.
        assertEquals("", new Button().getLabel());
    }

    @Test
    @DisplayName("string ctor seeds the label")
    void stringCtorSeedsTheLabel() {
        assertEquals("Quit", new Button("Quit").getLabel());
    }

    @Test
    @DisplayName("null label round-trips as null through the emulator's field shadow")
    void nullLabelRoundTripsAsNullThroughTheEmulatorsFieldShadow() {
        // The R_swing_is_truth-over-R_vaadin_first split: the SButton peer reads back "" because
        // Vaadin's text property can't hold null, so the emulator shadows
        // the field. Migrated code null-checking getLabel() needs this.
        Button b = new Button(null);
        assertNull(b.getLabel());
        b.setLabel("x");
        b.setLabel(null);
        assertNull(b.getLabel());
    }

    @Test
    @DisplayName("setLabel reaches the rendered Vaadin caption")
    void setLabelReachesTheRenderedVaadinCaption() {
        Button b = new Button("before");
        JFrame frame = new JFrame();
        frame.add(b);
        frame.setVisible(true);

        b.setLabel("after");

        assertEquals("after", peer().getText());
    }

    // --- actionCommand ------------------------------------------------

    @Test
    @DisplayName("actionCommand falls back to the label when never set")
    void actionCommandFallsBackToTheLabelWhenNeverSet() {
        assertEquals("OK", new Button("OK").getActionCommand());
    }

    @Test
    @DisplayName("null label yields a null actionCommand, as in AWT")
    void nullLabelYieldsANullActionCommandAsInAwt() {
        // AWT really does propagate the null through — the fallback is
        // "return the label", not "return the label or empty".
        assertNull(new Button(null).getActionCommand());
    }

    @Test
    @DisplayName("setActionCommand overrides the label fallback")
    void setActionCommandOverridesTheLabelFallback() {
        Button b = new Button("Save");
        b.setActionCommand("save-file");
        assertEquals("save-file", b.getActionCommand());
    }

    @Test
    @DisplayName("setActionCommand reaches the peer so browser clicks carry it")
    void setActionCommandReachesThePeerSoBrowserClicksCarryIt() {
        // Regression guard: if setActionCommand only stored emulator-side,
        // the peer would synthesize its ActionEvent with the label as the
        // command and the bridge would forward the wrong value.
        Button b = new Button("Save");
        b.setActionCommand("save-file");
        JFrame frame = new JFrame();
        frame.add(b);
        frame.setVisible(true);
        List<String> commands = new ArrayList<>();
        b.addActionListener(e -> commands.add(e.getActionCommand()));

        LocatorJ._click(peer());

        assertEquals(List.of("save-file"), commands);
    }

    // --- ActionListener -----------------------------------------------

    @Test
    @DisplayName("user click on the peer fires ActionEvent whose source is the emulator")
    void userClickOnThePeerFiresActionEventWhoseSourceIsTheEmulator() {
        // Load-bearing: migrated code casts `(Button) e.getSource()`. If
        // the bridge forwarded the surrogate's event verbatim the source
        // would be the SButton and every such cast would CCE.
        Button b = new Button("Hello");
        JFrame frame = new JFrame();
        frame.add(b);
        frame.setVisible(true);
        List<ActionEvent> events = new ArrayList<>();
        b.addActionListener(events::add);

        LocatorJ._click(peer());

        assertEquals(1, events.size());
        assertSame(b, events.get(0).getSource());
        assertEquals("Hello", events.get(0).getActionCommand());
        assertEquals(ActionEvent.ACTION_PERFORMED, events.get(0).getID());
    }

    @Test
    @DisplayName("a browser click reaches a processEvent override, not only processActionEvent")
    void aBrowserClickReachesAProcessEventOverrideNotOnlyProcessActionEvent() {
        // Regression: the bridge used to call processActionEvent directly, so
        // this override compiled, looked wired, and never ran on a real click
        // (R_no_vaadin_in_api limb 2 — AWT routes dispatchEvent -> processEvent ->
        // processActionEvent, and a subclass may intercept at either hop).
        List<String> seen = new ArrayList<>();
        Button b = new Button("Hi") {
            @Override
            protected void processEvent(java.awt.AWTEvent e) {
                seen.add("processEvent");
                super.processEvent(e);
            }

            @Override
            protected void processActionEvent(ActionEvent e) {
                seen.add("processActionEvent");
                super.processActionEvent(e);
            }
        };
        JFrame frame = new JFrame();
        frame.add(b);
        frame.setVisible(true);

        LocatorJ._click(peer());

        assertEquals(List.of("processEvent", "processActionEvent"), seen);
    }

    @Test
    @DisplayName("listeners fire first-registered-first, as AWTEventMulticaster does")
    void listenersFireFirstRegisteredFirstAsAwtEventMulticasterDoes() {
        // Regression: EventListenerList hands listeners back last-first (the
        // Swing convention), which silently reversed the order java.awt.Button
        // guarantees. Invisible in the code and, without this test, silent.
        Button b = new Button("Go");
        List<Integer> order = new ArrayList<>();
        b.addActionListener(e -> order.add(1));
        b.addActionListener(e -> order.add(2));
        b.addActionListener(e -> order.add(3));

        b.processEvent(new ActionEvent(b, ActionEvent.ACTION_PERFORMED, "x"));

        assertEquals(List.of(1, 2, 3), order);
        // getActionListeners() reports the same order AWT would.
        assertEquals(3, b.getActionListeners().length);
    }

    @Test
    @DisplayName("addRemoveActionListener round-trips through getActionListeners")
    void addRemoveActionListenerRoundTripsThroughGetActionListeners() {
        Button b = new Button();
        ActionListener l = e -> {
        };
        assertEquals(0, b.getActionListeners().length);
        b.addActionListener(l);
        assertSame(l, assertSingle(b.getActionListeners()));
        b.removeActionListener(l);
        assertEquals(0, b.getActionListeners().length);
    }

    @Test
    @DisplayName("null listeners are ignored rather than stored")
    void nullListenersAreIgnoredRatherThanStored() {
        Button b = new Button("x");
        b.addActionListener(null);
        b.removeActionListener(null);
        assertEquals(0, b.getActionListeners().length);
    }

    @Test
    @DisplayName("getListeners reaches the ActionListeners via Component's shared list")
    void getListenersReachesTheActionListenersViaComponentsSharedList() {
        // vaadinx.awt.Component.getListeners reads the same
        // EventListenerList addActionListener writes to — the surrogate
        // can't offer this (Vaadin Component already owns the erasure),
        // so the emulator is the only place it works.
        Button b = new Button("x");
        ActionListener l = e -> {
        };
        b.addActionListener(l);
        assertSame(l, assertSingle(b.getListeners(ActionListener.class)));
    }

    @Test
    @DisplayName("processActionEvent is the single funnel a subclass can intercept")
    void processActionEventIsTheSingleFunnelASubclassCanIntercept() {
        // The AWT idiom: override processActionEvent, inspect, call super.
        // Must see browser-originated actions too, not just synthetic ones.
        List<String> seen = new ArrayList<>();
        Button b = new Button("Tap") {
            @Override
            protected void processActionEvent(ActionEvent e) {
                seen.add(e.getActionCommand());
                super.processActionEvent(e);
            }
        };
        List<String> delivered = new ArrayList<>();
        b.addActionListener(e -> delivered.add(e.getActionCommand()));
        JFrame frame = new JFrame();
        frame.add(b);
        frame.setVisible(true);

        LocatorJ._click(peer());

        assertEquals(List.of("Tap"), seen);
        assertEquals(List.of("Tap"), delivered);
    }

    @Test
    @DisplayName("processEvent routes ActionEvent to processActionEvent and the rest to super")
    void processEventRoutesActionEventToProcessActionEventAndTheRestToSuper() {
        // JDK's Button.processEvent peels ActionEvent off first. User code
        // that dispatches by hand relies on the routing.
        Button b = new Button("x");
        List<String> delivered = new ArrayList<>();
        b.addActionListener(e -> delivered.add(e.getActionCommand()));

        b.processEvent(new ActionEvent(b, ActionEvent.ACTION_PERFORMED, "manual"));

        assertEquals(List.of("manual"), delivered);
    }

    // --- The AWT-leaf-in-a-Swing-tree case ----------------------------

    @Test
    @DisplayName("an AWT Button drops into a Swing container and renders")
    void anAwtButtonDropsIntoASwingContainerAndRenders() {
        // The residue case the slice exists for: one java.awt.Button left
        // in an otherwise-Swing view must add, render and click like any
        // JComponent sibling.
        Button b = new Button("Legacy");
        JPanel panel = new JPanel();
        panel.add(b);
        JFrame frame = new JFrame();
        frame.add(panel);
        frame.setVisible(true);

        assertSame(panel, b.getParent());
        assertEquals("Legacy", peer().getText());

        List<String> hits = new ArrayList<>();
        b.addActionListener(e -> hits.add(e.getActionCommand()));
        LocatorJ._click(peer());
        assertEquals(List.of("Legacy"), hits);
    }

    @Test
    @DisplayName("inherited Component surface works without JComponent in the chain")
    void inheritedComponentSurfaceWorksWithoutJComponentInTheChain() {
        // vaadinx.awt.Component has only ever carried Swing classes and
        // windows; this asserts the base behaves for a plain AWT leaf.
        Button b = new Button("x");
        b.setName("legacy-button");
        assertEquals("legacy-button", b.getName());
        b.setEnabled(false);
        assertFalse(b.isEnabled());
        b.setVisible(false);
        assertFalse(b.isVisible());
    }

    @Test
    @DisplayName("self-updating caption end-to-end")
    void selfUpdatingCaptionEndToEnd() {
        Button b = new Button("Press me");
        b.addActionListener(e -> b.setLabel("Pressed"));
        JFrame frame = new JFrame();
        frame.add(b);
        frame.setVisible(true);

        assertEquals("Press me", peer().getText());
        LocatorJ._click(peer());
        assertEquals("Pressed", peer().getText());
        assertEquals("Pressed", b.getLabel());
    }
}
