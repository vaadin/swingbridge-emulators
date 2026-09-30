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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SD_reverse_fanout_rows's sweep, pinned: the fourth class SD_property_fanout_audit named and deliberately left — a
 * property the surrogate really implements, whose JDK setter fires, and which
 * stayed silent. The {@link SFrame#setResizable} fix in SD_property_fanout_audit was the first of these;
 * these fifteen are the rest.
 *
 * <p>Deliberately <b>not</b> a mirror of {@code :emulators}' {@code PropertyFanoutTest}. That file's
 * first half asserts silence where the JDK is silent, which on this side is mostly
 * R_vaadin_first doing its job rather than a bug class — a surrogate may decline a whole
 * property, and 55 of the audit's 161 rows are exactly that. What needs holding
 * down here is the narrow set where declining was <em>not</em> available, plus the value
 * shapes, which are where the JDK is unguessable: a {@code -1} sentinel that reports
 * which tab changed rather than what to, a {@code null} old value standing for
 * "never set", a field name that is not the setter's, one setter firing two names.
 */
class PropertyFanoutTest extends AbstractKaribuTest {

    /** Every PCE the surrogate fires, whatever the name. */
    private static List<PropertyChangeEvent> record(ComponentMixin c) {
        List<PropertyChangeEvent> events = new ArrayList<>();
        c.addPropertyChangeListener(events::add);
        return events;
    }

    private static List<PropertyChangeEvent> named(List<PropertyChangeEvent> events, String name) {
        return events.stream().filter(it -> name.equals(it.getPropertyName())).toList();
    }

    /** Kotlin's {@code single()} — exactly one, or the assertion fails naming the surplus. */
    private static PropertyChangeEvent single(List<PropertyChangeEvent> events, String name) {
        List<PropertyChangeEvent> matching = named(events, name);
        assertEquals(1, matching.size(), "expected exactly one \"" + name + "\" PCE, got " + matching);
        return matching.get(0);
    }

    // --- ComponentMixin: the three sizes that collapse onto one Vaadin value --

    @Test
    @DisplayName("each size setter fires its own name only, and null means never set")
    void eachSizeSetterFiresItsOwnNameOnlyAndNullMeansNeverSet() {
        SJPanel panel = new SJPanel();
        List<PropertyChangeEvent> events = record(panel);

        panel.setPreferredSize(new Dimension(120, 40));

        PropertyChangeEvent first = single(events, "preferredSize");
        // AWT keeps a prefSizeSet flag purely so the first event's old value is
        // null. An unset CSS dimension already reads back null, so Vaadin's own
        // property absence is that flag — no shadow store needed (R_vaadin_first).
        assertNull(first.getOldValue(), "nothing was set before, so AWT reports null");
        assertEquals(new Dimension(120, 40), first.getNewValue());

        // "preferred" / "minimum" / "maximum" all collapse onto the one Vaadin size,
        // so this call also changed what getMinimumSize answers — and still fires
        // only the one name AWT fires. Firing the others would be inventing events
        // (SD_no_invented_events), which is the trap this whole audit exists to avoid.
        assertEquals(new Dimension(120, 40), panel.getMinimumSize());
        assertTrue(named(events, "minimumSize").isEmpty());
        assertTrue(named(events, "maximumSize").isEmpty());

        events.clear();
        panel.setMinimumSize(new Dimension(60, 20));
        PropertyChangeEvent second = single(events, "minimumSize");
        assertEquals(new Dimension(120, 40), second.getOldValue(), "now a size was set, so it is reported");

        events.clear();
        panel.setMaximumSize(new Dimension(300, 90));
        assertEquals(1, named(events, "maximumSize").size());
    }

    // --- JComponentMixin ------------------------------------------------------

    @Test
    @DisplayName("setComponentPopupMenu fires — SD_property_fanout_audit filed it as out of scope by misreading it")
    void setComponentPopupMenuFires() {
        SJPanel panel = new SJPanel();
        List<PropertyChangeEvent> events = record(panel);
        SJPopupMenu popup = new SJPopupMenu();

        panel.setComponentPopupMenu(popup);

        // The method really installs the menu on the Vaadin target, so the property
        // is implemented and the event is owed; "popup internals, out of scope" was
        // a misclassification of this body, not of the property.
        assertEquals(popup, panel.getComponentPopupMenu());
        assertEquals(popup, single(events, "componentPopupMenu").getNewValue());

        events.clear();
        panel.setComponentPopupMenu(null);
        assertEquals(popup, single(events, "componentPopupMenu").getOldValue());
    }

    // --- SJInternalFrame: six header/pane properties -------------------------

    @Test
    @DisplayName("internal-frame header flags fire, and iconifiable fires under the field name")
    void internalFrameHeaderFlagsFireAndIconifiableFiresUnderTheFieldName() {
        SJInternalFrame frame = new SJInternalFrame();
        List<PropertyChangeEvent> events = record(frame);

        // Closable starts on, so turning it off is a change; maximizable and
        // iconifiable start off, matching the JDK's own defaults, so those are
        // toggled *on*. A no-change set delivers nothing either way —
        // PropertyChangeSupport filters equal values before dispatch, so the
        // direction of each toggle here is load-bearing, not cosmetic.
        frame.setClosable(false);
        assertEquals(1, named(events, "closable").size());

        frame.setMaximizable(true);
        assertEquals(1, named(events, "maximizable").size());

        frame.setIconifiable(true);
        // "iconable", not "iconifiable" — the JDK fires under the field's name, not
        // the setter's. D_property_fanout_audit found the same trap on the emulator side, independently.
        assertEquals(1, named(events, "iconable").size());
        assertTrue(named(events, "iconifiable").isEmpty());

        events.clear();
        frame.setIconifiable(true);
        assertTrue(named(events, "iconable").isEmpty(), "unchanged — the filter swallows it");
    }

    @Test
    @DisplayName("internal-frame pane swaps fire with the outgoing pane as the old value")
    void internalFramePaneSwapsFireWithTheOutgoingPaneAsTheOldValue() {
        SJInternalFrame frame = new SJInternalFrame();
        List<PropertyChangeEvent> events = record(frame);

        Component oldContent = frame.getContentPane();
        Div newContent = new Div();
        frame.setContentPane(newContent);
        PropertyChangeEvent e = single(events, "contentPane");
        assertEquals(oldContent, e.getOldValue());
        assertEquals(newContent, e.getNewValue());

        Component layered = new Div();
        frame.setLayeredPane(layered);
        assertEquals(layered, single(events, "layeredPane").getNewValue());

        Component glass = new Div();
        frame.setGlassPane(glass);
        assertEquals(glass, single(events, "glassPane").getNewValue());
    }

    // --- SJTable: one setter, two names --------------------------------------

    @Test
    @DisplayName("setRowSorter fires both of the JDK's names")
    void setRowSorterFiresBothOfTheJdksNames() {
        SJTable table = new SJTable();
        table.setModel(new DefaultTableModel(new Object[][]{{"x"}}, new Object[]{"c"}));
        List<PropertyChangeEvent> events = record(table);

        TableRowSorter<?> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);

        // The JDK's back-compat doubling: dropping the legacy "sorter" name silently
        // breaks code that registered under it.
        assertEquals(sorter, single(events, "rowSorter").getNewValue());
        assertEquals(sorter, single(events, "sorter").getNewValue());
    }

    // --- SJTabbedPane: the -1 sentinels --------------------------------------

    @Test
    @DisplayName("tab title and component events report which tab changed, not what to")
    void tabTitleAndComponentEventsReportWhichTabChangedNotWhatTo() {
        SJTabbedPane pane = new SJTabbedPane();
        pane.insertTab("one", null, new Div(), null, 0);
        List<PropertyChangeEvent> events = record(pane);

        pane.setTitleAt(0, "renamed");

        PropertyChangeEvent e = single(events, "indexForTitle");
        // A sentinel old value and the index as the new: the event says *which* tab,
        // so a listener has to re-read the pane for the title itself.
        assertEquals(-1, e.getOldValue());
        assertEquals(0, e.getNewValue());

        // Reference inequality, as the JDK guards it: an equal-but-distinct String
        // fires, an identical reference does not.
        events.clear();
        String same = pane.getTitleAt(0);
        pane.setTitleAt(0, same);
        assertTrue(named(events, "indexForTitle").isEmpty(), "identical reference — silent");
        pane.setTitleAt(0, new StringBuilder(same).toString());
        assertEquals(1, named(events, "indexForTitle").size(), "equal but distinct — fires");

        events.clear();
        pane.setTabComponentAt(0, new Span("hdr"));
        assertEquals(-1, single(events, "indexForTabComponent").getOldValue());
    }

    @Test
    @DisplayName("insertTab fires indexForNullComponent only for a null component")
    void insertTabFiresIndexForNullComponentOnlyForANullComponent() {
        SJTabbedPane pane = new SJTabbedPane();
        List<PropertyChangeEvent> events = record(pane);

        pane.insertTab("with content", null, new Div(), null, 0);
        assertTrue(named(events, "indexForNullComponent").isEmpty(), "non-null component — silent");

        pane.insertTab("no content", null, null, null, 1);
        PropertyChangeEvent e = single(events, "indexForNullComponent");
        assertEquals(-1, e.getOldValue());
        assertEquals(1, e.getNewValue());
    }
}
