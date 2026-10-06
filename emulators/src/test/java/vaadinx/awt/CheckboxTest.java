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
import com.vaadin.swingbridge.surrogates.SCheckbox;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for the {@code vaadinx.awt.Checkbox} emulator — the AWT 1.0 checkbox,
 * not {@code vaadinx.swing.JCheckBox}. Most of the file is about the two things
 * that make this widget unlike the AWT leaves before it: every programmatic
 * path is silent where Swing's fires, and the group turns a checkbox into a
 * radio button whose deselect is vetoed. {@code CheckboxGroup}'s own surface is
 * covered in {@link CheckboxGroupTest}. See D_awt_lane.
 *
 * <p>Java has no import alias, so the Vaadin peer type is spelled out in full
 * at every lookup — {@code Checkbox} here is unqualified for the emulator.
 */
class CheckboxTest extends AbstractKaribuTest {

    /** Attaches {@code boxes} to a visible frame so Karibu will accept from-client input. */
    private JFrame show(Checkbox... boxes) {
        JFrame frame = new JFrame();
        for (Checkbox box : boxes) {
            frame.add(box);
        }
        frame.setVisible(true);
        return frame;
    }

    /** The rendered Vaadin peer of the single checkbox under test. */
    private static com.vaadin.flow.component.checkbox.Checkbox peer() {
        return LocatorJ._get(com.vaadin.flow.component.checkbox.Checkbox.class);
    }

    // --- Ctors --------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor follows AWT's this-empty-false-null chain")
    void noArgCtorFollowsAwtsThisEmptyFalseNullChain() {
        Checkbox c = new Checkbox();
        assertEquals("", c.getLabel());
        assertFalse(c.getState());
        assertNull(c.getCheckboxGroup());
    }

    @Test
    @DisplayName("string ctor leaves the box unchecked and ungrouped")
    void stringCtorLeavesTheBoxUncheckedAndUngrouped() {
        Checkbox c = new Checkbox("Verbose");
        assertEquals("Verbose", c.getLabel());
        assertFalse(c.getState());
    }

    @Test
    @DisplayName("two-arg ctor seeds the state")
    void twoArgCtorSeedsTheState() {
        assertTrue(new Checkbox("Verbose", true).getState());
    }

    @Test
    @DisplayName("both three-arg ctor orders agree")
    void bothThreeArgCtorOrdersAgree() {
        CheckboxGroup g1 = new CheckboxGroup();
        CheckboxGroup g2 = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g1);
        Checkbox b = new Checkbox("b", g2, true);
        assertSame(g1, a.getCheckboxGroup());
        assertSame(g2, b.getCheckboxGroup());
        assertTrue(a.getState());
        assertTrue(b.getState());
    }

    @Test
    @DisplayName("a checked box constructed into a group becomes its selection")
    void aCheckedBoxConstructedIntoAGroupBecomesItsSelection() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox c = new Checkbox("metric", true, g);
        assertSame(c, g.getSelectedCheckbox());
    }

    @Test
    @DisplayName("two boxes constructed checked into one group — the second wins, silently")
    void twoBoxesConstructedCheckedIntoOneGroupTheSecondWinsSilently() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);
        List<ItemEvent> events = new ArrayList<>();
        a.addItemListener(events::add);

        Checkbox b = new Checkbox("b", true, g);
        b.addItemListener(events::add);

        assertSame(b, g.getSelectedCheckbox());
        assertFalse(a.getState());
        assertTrue(b.getState());
        assertEquals(List.of(), events);
    }

    // --- label --------------------------------------------------------

    @Test
    @DisplayName("null label round-trips as null through the emulator's field shadow")
    void nullLabelRoundTripsAsNullThroughTheEmulatorsFieldShadow() {
        // The R_swing_is_truth-over-R_vaadin_first split: the SCheckbox peer reads back "" because
        // Vaadin's label property can't hold null.
        Checkbox c = new Checkbox(null);
        assertNull(c.getLabel());
        c.setLabel("x");
        c.setLabel(null);
        assertNull(c.getLabel());
    }

    @Test
    @DisplayName("setLabel reaches the rendered Vaadin caption")
    void setLabelReachesTheRenderedVaadinCaption() {
        Checkbox c = new Checkbox("before");
        show(c);
        c.setLabel("after");
        assertEquals("after", peer().getLabel());
    }

    // --- state --------------------------------------------------------

    @Test
    @DisplayName("setState round-trips and reaches the peer")
    void setStateRoundTripsAndReachesThePeer() {
        Checkbox c = new Checkbox("x");
        show(c);
        c.setState(true);
        assertTrue(c.getState());
        assertTrue(peer().getValue());
        c.setState(false);
        assertFalse(c.getState());
        assertFalse(peer().getValue());
    }

    @Test
    @DisplayName("setState fires nothing — in a group or out of one")
    void setStateFiresNothingInAGroupOrOutOfOne() {
        // The AWT rule that inverts Swing: only the toolkit posts ItemEvents.
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", false, g);
        Checkbox b = new Checkbox("b", false, g);
        Checkbox loose = new Checkbox("loose");
        List<ItemEvent> events = new ArrayList<>();
        for (Checkbox box : List.of(a, b, loose)) {
            box.addItemListener(events::add);
        }

        loose.setState(true);
        a.setState(true);
        b.setState(true);      // cascades a off
        a.setState(false);     // vetoed: a is not the selection, so this sticks

        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("a grouped box cannot be turned off — setState(false) is upgraded to true")
    void aGroupedBoxCannotBeTurnedOffSetStateFalseIsUpgradedToTrue() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);

        a.setState(false);

        assertTrue(a.getState(), "AWT forces the group's selection back on");
        assertSame(a, g.getSelectedCheckbox());
    }

    @Test
    @DisplayName("setState(true) on a grouped box turns its sibling off")
    void setStateTrueOnAGroupedBoxTurnsItsSiblingOff() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);
        Checkbox b = new Checkbox("b", false, g);

        b.setState(true);

        assertFalse(a.getState());
        assertTrue(b.getState());
        assertSame(b, g.getSelectedCheckbox());
    }

    // --- Peer → AWT ---------------------------------------------------

    @Test
    @DisplayName("browser toggle fires one ItemEvent whose source is the emulator")
    void browserToggleFiresOneItemEventWhoseSourceIsTheEmulator() {
        // Load-bearing: migrated code casts `(Checkbox) e.getItemSelectable()`.
        // If the bridge forwarded the surrogate's event verbatim the source
        // would be the SCheckbox and every such cast would CCE.
        Checkbox c = new Checkbox("Verbose");
        show(c);
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);

        LocatorJ._setValue(peer(), true);

        assertEquals(1, events.size());
        assertSame(c, events.get(0).getSource());
        assertSame(c, events.get(0).getItemSelectable());
        assertEquals("Verbose", events.get(0).getItem());
        assertEquals(ItemEvent.SELECTED, events.get(0).getStateChange());
        assertTrue(c.getState());
    }

    @Test
    @DisplayName("the ItemEvent's item is the emulator's label, null included")
    void theItemEventsItemIsTheEmulatorsLabelNullIncluded() {
        Checkbox c = new Checkbox(null);
        show(c);
        List<Object> items = new ArrayList<>();
        c.addItemListener(e -> items.add(e.getItem()));

        LocatorJ._setValue(peer(), true);

        assertEquals(Arrays.asList((Object) null), items);
    }

    @Test
    @DisplayName("selecting a radio posts SELECTED on it and nothing on the sibling")
    void selectingARadioPostsSelectedOnItAndNothingOnTheSibling() {
        // Swing's ButtonGroup fires DESELECTED on the outgoing button; AWT
        // deliberately does not — XCheckboxPeer posts one event, on the box
        // that was clicked.
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);
        Checkbox b = new Checkbox("b", false, g);
        show(a, b);
        List<String> log = new ArrayList<>();
        a.addItemListener(e -> log.add("a:" + e.getStateChange()));
        b.addItemListener(e -> log.add("b:" + e.getStateChange()));

        LocatorJ._setValue(
                LocatorJ._get(com.vaadin.flow.component.checkbox.Checkbox.class, spec -> spec.withLabel("b")),
                true);

        assertEquals(List.of("b:" + ItemEvent.SELECTED), log);
        assertFalse(a.getState());
        assertTrue(b.getState());
        assertSame(b, g.getSelectedCheckbox());
    }

    @Test
    @DisplayName("re-clicking the selected radio posts nothing and bounces the peer back")
    void reClickingTheSelectedRadioPostsNothingAndBouncesThePeerBack() {
        // JDK bug 4039594's carve-out in XCheckboxPeer.action: the peer forces
        // the visual back on and returns before notifying.
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);
        show(a);
        List<Integer> log = new ArrayList<>();
        a.addItemListener(e -> log.add(e.getStateChange()));

        LocatorJ._setValue(peer(), false);

        assertEquals(List.of(), log);
        assertTrue(a.getState());
        assertTrue(peer().getValue(), "the peer must bounce back to checked");
    }

    @Test
    @DisplayName("a browser toggle reaches a processEvent override, not only processItemEvent")
    void aBrowserToggleReachesAProcessEventOverrideNotOnlyProcessItemEvent() {
        // R_no_vaadin_in_api limb 2: AWT routes dispatchEvent -> processEvent ->
        // processItemEvent, and a subclass may intercept at either hop.
        // Entering the bridge at the second hop would leave this override
        // compiling, looking wired, and never running on a real toggle.
        List<String> seen = new ArrayList<>();
        Checkbox c = new Checkbox("Hi") {
            @Override
            protected void processEvent(java.awt.AWTEvent e) {
                seen.add("processEvent");
                super.processEvent(e);
            }

            @Override
            protected void processItemEvent(ItemEvent e) {
                seen.add("processItemEvent");
                super.processItemEvent(e);
            }
        };
        show(c);

        LocatorJ._setValue(peer(), true);

        assertEquals(List.of("processEvent", "processItemEvent"), seen);
    }

    // --- ItemListener -------------------------------------------------

    @Test
    @DisplayName("addRemoveItemListener round-trips through getItemListeners")
    void addRemoveItemListenerRoundTripsThroughGetItemListeners() {
        Checkbox c = new Checkbox();
        ItemListener l = e -> {
        };
        assertEquals(0, c.getItemListeners().length);
        c.addItemListener(l);
        assertSame(l, assertSingle(c.getItemListeners()));
        c.removeItemListener(l);
        assertEquals(0, c.getItemListeners().length);
    }

    @Test
    @DisplayName("null listeners are ignored rather than stored")
    void nullListenersAreIgnoredRatherThanStored() {
        Checkbox c = new Checkbox("x");
        c.addItemListener(null);
        c.removeItemListener(null);
        assertEquals(0, c.getItemListeners().length);
    }

    @Test
    @DisplayName("listeners fire first-registered-first, as AWTEventMulticaster does")
    void listenersFireFirstRegisteredFirstAsAwtEventMulticasterDoes() {
        Checkbox c = new Checkbox("x");
        List<Integer> order = new ArrayList<>();
        c.addItemListener(e -> order.add(1));
        c.addItemListener(e -> order.add(2));
        c.addItemListener(e -> order.add(3));

        c.processEvent(new ItemEvent(c, ItemEvent.ITEM_STATE_CHANGED, "x", ItemEvent.SELECTED));

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    @DisplayName("getListeners reaches the ItemListeners via Component's shared list")
    void getListenersReachesTheItemListenersViaComponentsSharedList() {
        // The surrogate can't offer this — Vaadin Component already owns the
        // erasure — so the emulator is the only place it works.
        Checkbox c = new Checkbox("x");
        ItemListener l = e -> {
        };
        c.addItemListener(l);
        assertSame(l, assertSingle(c.getListeners(ItemListener.class)));
    }

    @Test
    @DisplayName("processEvent routes ItemEvent to processItemEvent and the rest to super")
    void processEventRoutesItemEventToProcessItemEventAndTheRestToSuper() {
        Checkbox c = new Checkbox("x");
        List<Integer> delivered = new ArrayList<>();
        c.addItemListener(e -> delivered.add(e.getStateChange()));

        c.processEvent(new ItemEvent(c, ItemEvent.ITEM_STATE_CHANGED, "x", ItemEvent.DESELECTED));

        assertEquals(List.of(ItemEvent.DESELECTED), delivered);
    }

    // --- getSelectedObjects -------------------------------------------

    @Test
    @DisplayName("getSelectedObjects is null when unchecked and single when checked")
    void getSelectedObjectsIsNullWhenUncheckedAndSingleWhenChecked() {
        Checkbox c = new Checkbox("Verbose");
        assertNull(c.getSelectedObjects());
        c.setState(true);
        assertEquals(List.of("Verbose"), Arrays.asList(c.getSelectedObjects()));
    }

    @Test
    @DisplayName("getSelectedObjects carries a null element for a null-labelled box")
    void getSelectedObjectsCarriesANullElementForANullLabelledBox() {
        // AWT hands back the raw label field, so the single element really
        // can be null. Only the emulator sees this — the peer holds "".
        Checkbox c = new Checkbox(null, true);
        assertEquals(Arrays.asList((Object) null), Arrays.asList(c.getSelectedObjects()));
    }

    // --- setCheckboxGroup ---------------------------------------------

    @Test
    @DisplayName("joining a group flips the glyph on the peer")
    void joiningAGroupFlipsTheGlyphOnThePeer() {
        Checkbox c = new Checkbox("metric");
        show(c);
        assertFalse(((SCheckbox) c.getPeer()).isRadioLook());

        c.setCheckboxGroup(new CheckboxGroup());

        assertTrue(((SCheckbox) c.getPeer()).isRadioLook());
        assertTrue(peer().getElement().getThemeList().contains("emul-radio"));
    }

    @Test
    @DisplayName("leaving a group flips the glyph back")
    void leavingAGroupFlipsTheGlyphBack() {
        Checkbox c = new Checkbox("metric", true, new CheckboxGroup());
        show(c);

        c.setCheckboxGroup(null);

        assertNull(c.getCheckboxGroup());
        assertFalse(peer().getElement().getThemeList().contains("emul-radio"));
    }

    @Test
    @DisplayName("re-homing a checked box leaves the old group with no selection")
    void reHomingACheckedBoxLeavesTheOldGroupWithNoSelection() {
        // AWT's own outcome, and easy to get wrong: the clearing call runs
        // after the box has been re-homed, so setCurrent's
        // still-one-of-mine guard skips the deselect and only the pointer
        // is dropped.
        CheckboxGroup old = new CheckboxGroup();
        CheckboxGroup fresh = new CheckboxGroup();
        Checkbox c = new Checkbox("metric", true, old);
        assertSame(c, old.getSelectedCheckbox());

        c.setCheckboxGroup(fresh);

        assertNull(old.getSelectedCheckbox());
        assertSame(c, fresh.getSelectedCheckbox());
        assertTrue(c.getState());
    }

    @Test
    @DisplayName("a checked box joining a group that already has a selection is forced off")
    void aCheckedBoxJoiningAGroupThatAlreadyHasASelectionIsForcedOff() {
        CheckboxGroup occupied = new CheckboxGroup();
        Checkbox holder = new Checkbox("holder", true, occupied);
        Checkbox c = new Checkbox("joiner", true);

        c.setCheckboxGroup(occupied);

        assertFalse(c.getState());
        assertTrue(holder.getState());
        assertSame(holder, occupied.getSelectedCheckbox());
    }

    @Test
    @DisplayName("setting the same group again is an early return")
    void settingTheSameGroupAgainIsAnEarlyReturn() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox c = new Checkbox("metric", true, g);

        c.setCheckboxGroup(g);

        assertTrue(c.getState());
        assertSame(c, g.getSelectedCheckbox());
    }

    // --- R_leaf_peer_lockdown / integration --------------------------------------------

    @Test
    @DisplayName("every instance peers over an SCheckbox, subclasses included")
    void everyInstancePeersOverAnSCheckboxSubclassesIncluded() {
        assertInstanceOf(SCheckbox.class, new Checkbox("x").getPeer());
        Checkbox sub = new Checkbox("y") {
        };
        assertInstanceOf(SCheckbox.class, sub.getPeer());
    }

    @Test
    @DisplayName("an AWT Checkbox drops into a Swing container and toggles")
    void anAwtCheckboxDropsIntoASwingContainerAndToggles() {
        // The residue case the slice exists for.
        Checkbox c = new Checkbox("Legacy");
        JPanel panel = new JPanel();
        panel.add(c);
        JFrame frame = new JFrame();
        frame.add(panel);
        frame.setVisible(true);

        assertSame(panel, c.getParent());
        assertEquals("Legacy", peer().getLabel());

        List<Object> hits = new ArrayList<>();
        c.addItemListener(e -> hits.add(e.getItem()));
        LocatorJ._setValue(peer(), true);
        assertEquals(List.of("Legacy"), hits);
    }

    @Test
    @DisplayName("inherited Component surface works without JComponent in the chain")
    void inheritedComponentSurfaceWorksWithoutJComponentInTheChain() {
        Checkbox c = new Checkbox("x");
        c.setName("verbose-box");
        assertEquals("verbose-box", c.getName());
        c.setEnabled(false);
        assertFalse(c.isEnabled());
        c.setVisible(false);
        assertFalse(c.isVisible());
    }

    @Test
    @DisplayName("paramString omits the label clause when null, unlike Button's")
    void paramStringOmitsTheLabelClauseWhenNullUnlikeButtons() {
        assertEquals(",state=false", new Checkbox(null).paramString());
        assertEquals(",label=Verbose,state=true", new Checkbox("Verbose", true).paramString());
    }

    @Test
    @DisplayName("getAccessibleContext returns null")
    void getAccessibleContextReturnsNull() {
        assertNull(new Checkbox().getAccessibleContext());
    }
}
