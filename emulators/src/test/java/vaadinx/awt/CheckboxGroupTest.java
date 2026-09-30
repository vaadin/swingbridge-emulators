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

import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for {@code vaadinx.awt.CheckboxGroup}. The class is six members over
 * one pointer, so the file is mostly about the two things that are easy to
 * get wrong: the asymmetric setter pair inside {@code setCurrent} (public one way,
 * package-private the other), and the fact that nothing here fires or throws.
 * The {@code Checkbox} side of the interaction is covered in {@link CheckboxTest}; this
 * follows {@code vaadinx/swing/ButtonGroupTest}.
 */
class CheckboxGroupTest extends AbstractKaribuTest {

    @Test
    @DisplayName("an empty group has no selection")
    void anEmptyGroupHasNoSelection() {
        assertNull(new CheckboxGroup().getSelectedCheckbox());
    }

    @Test
    @DisplayName("setSelectedCheckbox moves the selection and turns the old box off")
    void setSelectedCheckboxMovesTheSelectionAndTurnsTheOldBoxOff() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);
        Checkbox b = new Checkbox("b", false, g);

        g.setSelectedCheckbox(b);

        assertSame(b, g.getSelectedCheckbox());
        assertFalse(a.getState());
        assertTrue(b.getState());
    }

    @Test
    @DisplayName("the cascade fires no ItemEvent on either box")
    void theCascadeFiresNoItemEventOnEitherBox() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);
        Checkbox b = new Checkbox("b", false, g);
        List<ItemEvent> events = new ArrayList<>();
        a.addItemListener(events::add);
        b.addItemListener(events::add);

        g.setSelectedCheckbox(b);

        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("a box belonging to another group is a silent no-op")
    void aBoxBelongingToAnotherGroupIsASilentNoOp() {
        // The temptation to raise IllegalArgumentException must be resisted:
        // AWT documents the no-op and migrated code may rely on it (R_match_swing_errors).
        CheckboxGroup g = new CheckboxGroup();
        Checkbox mine = new Checkbox("mine", true, g);
        Checkbox foreign = new Checkbox("foreign", false, new CheckboxGroup());

        g.setSelectedCheckbox(foreign);

        assertSame(mine, g.getSelectedCheckbox());
        assertFalse(foreign.getState());
    }

    @Test
    @DisplayName("an ungrouped box is a silent no-op too")
    void anUngroupedBoxIsASilentNoOpToo() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox loose = new Checkbox("loose");

        g.setSelectedCheckbox(loose);

        assertNull(g.getSelectedCheckbox());
        assertFalse(loose.getState());
    }

    @Test
    @DisplayName("setSelectedCheckbox(null) deselects everything")
    void setSelectedCheckboxNullDeselectsEverything() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);

        g.setSelectedCheckbox(null);

        assertNull(g.getSelectedCheckbox());
        assertFalse(a.getState());
    }

    @Test
    @DisplayName("re-selecting the current box leaves it on and changes nothing")
    void reSelectingTheCurrentBoxLeavesItOnAndChangesNothing() {
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);

        g.setSelectedCheckbox(a);

        assertSame(a, g.getSelectedCheckbox());
        assertTrue(a.getState());
    }

    @Test
    @DisplayName("the deprecated pair carries the implementation, so overriding it intercepts both")
    void theDeprecatedPairCarriesTheImplementationSoOverridingItInterceptsBoth() {
        // R_no_vaadin_in_api limb 2 on the deprecated names: in the JDK
        // getSelectedCheckbox/setSelectedCheckbox delegate to
        // getCurrent/setCurrent, not the other way round. Wiring it the
        // intuitive way round would leave this override silently dead.
        List<String> seen = new ArrayList<>();
        CheckboxGroup g = new CheckboxGroup() {
            @Override
            public synchronized void setCurrent(Checkbox box) {
                seen.add("setCurrent");
                super.setCurrent(box);
            }

            @Override
            public Checkbox getCurrent() {
                seen.add("getCurrent");
                return super.getCurrent();
            }
        };
        Checkbox a = new Checkbox("a", false, g);

        g.setSelectedCheckbox(a);
        g.getSelectedCheckbox();

        assertEquals(List.of("setCurrent", "getCurrent"), seen);
        assertTrue(a.getState());
    }

    @Test
    @DisplayName("the incoming box bypasses the group check while the outgoing box goes through it")
    void theIncomingBoxBypassesTheGroupCheckWhileTheOutgoingBoxGoesThroughIt() {
        // The ordering that makes setCurrent terminate: the pointer moves
        // first, so the outgoing box's public setState(false) no longer sees
        // itself as the selection and its cannot-deselect veto stays quiet;
        // the incoming box uses the package-private setter, which consults no
        // group at all. Either setter used for both directions recurses or
        // leaves a stale selection.
        CheckboxGroup g = new CheckboxGroup();
        Checkbox a = new Checkbox("a", true, g);
        Checkbox b = new Checkbox("b", false, g);
        List<String> setStateCalls = new ArrayList<>();
        Checkbox instrumented = new Checkbox("c", false, g) {
            @Override
            public void setState(boolean state) {
                setStateCalls.add("setState(" + state + ")");
                super.setState(state);
            }
        };

        g.setSelectedCheckbox(instrumented);   // in: no public setState
        assertEquals(List.of(), setStateCalls);
        assertTrue(instrumented.getState());

        g.setSelectedCheckbox(b);              // out: through the public setState
        assertEquals(List.of("setState(false)"), setStateCalls);
        assertFalse(instrumented.getState());
        assertFalse(a.getState());
    }

    @Test
    @DisplayName("toString reports the selected box in the JDK's shape")
    void toStringReportsTheSelectedBoxInTheJdksShape() {
        CheckboxGroup g = new CheckboxGroup();
        assertEquals("vaadinx.awt.CheckboxGroup[selectedCheckbox=null]", g.toString());
        Checkbox a = new Checkbox("a", true, g);
        assertTrue(g.toString().startsWith("vaadinx.awt.CheckboxGroup[selectedCheckbox="));
        assertTrue(g.toString().contains(a.toString()));
    }
}
