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

package vaadinx.swing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.EventQueue;
import vaadinx.awt.Toolkit;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * R_no_vaadin_in_api limb 1's JDK-typed half, pinned: a shadowed signature must name the
 * <i>ported</i> type, not the JDK type SB-Emulators emulates.
 *
 * <p>Why these need runtime tests at all, when {@code R12ProvenanceTest} already fails
 * on the types: at several of these sites the JDK type did not merely read
 * wrong, it made the method <b>impossible to satisfy</b>, and the body had been
 * written to match — {@code getComponentIndex} could only return {@code -1},
 * {@code isMenuComponent} only {@code false}, {@code MenuElement.getComponent()} only {@code null},
 * because no import-swapped caller can produce a {@code java.awt.Component} to pass
 * or a {@code JMenuItem} that is one. Retyping is what let those bodies answer the
 * JDK's actual question, so the answers are what needs pinning; the gate cannot
 * tell a real implementation from a placeholder that happens to compile.
 *
 * <p>The signature-level property itself is {@code R12ProvenanceTest}'s job and is not
 * duplicated here.
 */
class PortedTypeSurfaceTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        Consumer<String> sink = capturedWarns::add;
        EHelper.warnHook = sink;
        SHelper.warnHook = sink;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> { /* no-op default */ };
        SHelper.warnHook = msg -> { /* no-op default */ };
    }

    // --- The three "unanswerable under the JDK type" bodies ----------------

    @Test
    @DisplayName("JMenuBar getComponentIndex finds a menu through the ported signature")
    void menuBarComponentIndexFindsAMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenu edit = new JMenu("Edit");
        bar.add(file);
        bar.add(edit);
        // The argument is typed vaadinx.awt.Component, so a JMenu satisfies it
        // and the walk can succeed. Under java.awt.Component this returned -1
        // for every input, including ones it plainly contained.
        assertEquals(0, bar.getComponentIndex(file));
        assertEquals(1, bar.getComponentIndex(edit));
        assertEquals(-1, bar.getComponentIndex(new JMenu("Absent")));
    }

    @Test
    @DisplayName("JMenu isMenuComponent sees itself, its children and its grandchildren")
    void menuIsMenuComponentRecurses() {
        JMenu file = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        JMenu recent = new JMenu("Recent");
        JMenuItem nested = new JMenuItem("a.txt");
        recent.add(nested);
        file.add(item);
        file.add(recent);

        assertTrue(file.isMenuComponent(file), "the JDK counts the menu itself");
        assertTrue(file.isMenuComponent(item));
        assertTrue(file.isMenuComponent(recent));
        assertTrue(file.isMenuComponent(nested), "the JDK recurses into submenus");
        assertFalse(file.isMenuComponent(new JMenuItem("Elsewhere")));
        assertFalse(file.isMenuComponent(null));
    }

    @Test
    @DisplayName("MenuElement getComponent returns the element itself, on both implementors")
    void menuElementGetComponentReturnsThis() {
        JMenuItem item = new JMenuItem("Open");
        JPopupMenu popup = new JPopupMenu();
        // The JDK's answer is `this`. It was null here only because the ported
        // JMenuItem is not a java.awt.Component, which the ported MenuElement
        // interface removes as an obstacle.
        assertSame(item, item.getComponent());
        assertSame(popup, popup.getComponent());
    }

    @Test
    @DisplayName("JPopupMenu getSubElements reports its menu-element children")
    void popupSubElementsReportsChildren() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        popup.add(open);
        // Reads through the ported MenuElement, so the instanceof filter in the
        // body matches what the popup actually holds.
        assertSame(open, assertSingle(popup.getSubElements()));
    }

    // --- Ported-type round-trips ------------------------------------------

    @Test
    @DisplayName("JTabbedPane icons round-trip as ported Icons and stay index-aligned")
    void tabbedPaneIconsRoundTrip() {
        JTabbedPane pane = new JTabbedPane();
        ImageIcon one = new ImageIcon(new byte[0], "one");
        ImageIcon two = new ImageIcon(new byte[0], "two");
        pane.addTab("A", one, new JPanel());
        pane.addTab("B", two, new JPanel());

        // Read back the very ported instance that went in — the surrogate only
        // ever sees the unwrapped JDK delegate, so a getter that read through
        // it could not return this object.
        assertSame(one, pane.getIconAt(0));
        assertSame(two, pane.getIconAt(1));

        // The mirror shrinks with the tab list; without that, index 0 would
        // still answer with the removed tab's icon.
        pane.removeTabAt(0);
        assertSame(two, pane.getIconAt(0));
        assertEquals(1, pane.getTabCount());

        ImageIcon three = new ImageIcon(new byte[0], "three");
        pane.insertTab("C", three, new JPanel(), null, 0);
        assertSame(three, pane.getIconAt(0));
        assertSame(two, pane.getIconAt(1));

        pane.setDisabledIconAt(1, one);
        assertSame(one, pane.getDisabledIconAt(1));
        assertNull(pane.getDisabledIconAt(0), "untouched tabs have no disabled icon");

        pane.removeAll();
        assertEquals(0, pane.getTabCount());
    }

    @Test
    @DisplayName("JInternalFrame frameIcon round-trips as a ported Icon and fires")
    void internalFrameIconRoundTrips() {
        JInternalFrame frame = new JInternalFrame("Doc");
        ImageIcon icon = new ImageIcon(new byte[0], "doc");
        List<PropertyChangeEvent> seen = new ArrayList<>();
        frame.addPropertyChangeListener(JInternalFrame.FRAME_ICON_PROPERTY, seen::add);

        frame.setFrameIcon(icon);

        assertSame(icon, frame.getFrameIcon());
        assertSame(icon, assertSingle(seen).getNewValue());
    }

    @Test
    @DisplayName("JColorChooser previewPanel round-trips as a ported JComponent and fires")
    void colorChooserPreviewPanelRoundTrips() {
        JColorChooser chooser = new JColorChooser();
        JPanel panel = new JPanel();
        List<PropertyChangeEvent> seen = new ArrayList<>();
        chooser.addPropertyChangeListener(JColorChooser.PREVIEW_PANEL_PROPERTY, seen::add);

        // A migrated JPanel is exactly what could not be passed before; the
        // value and the event are owed even though the render is declined (R_decline_effect_only).
        chooser.setPreviewPanel(panel);

        assertSame(panel, chooser.getPreviewPanel());
        PropertyChangeEvent pce = assertSingle(seen);
        assertNull(pce.getOldValue());
        assertSame(panel, pce.getNewValue());
    }

    @Test
    @DisplayName("JSpinner DateEditor getFormat hands back the ported SimpleDateFormat")
    void spinnerDateEditorFormatIsPorted() {
        JSpinner spinner = new JSpinner(new javax.swing.SpinnerDateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd");
        // Declared as the ported type — the point of the retype is that this
        // assignment compiles in migrated code. Still a java.text.SimpleDateFormat
        // by inheritance, so the pattern accessors work unchanged.
        vaadinx.text.SimpleDateFormat format = editor.getFormat();
        assertEquals("yyyy-MM-dd", format.toPattern());
    }

    @Test
    @DisplayName("Toolkit getSystemEventQueue hands out the ported queue, same instance, silently")
    void toolkitSystemEventQueueIsPortedAndStable() {
        EventQueue q = Toolkit.getDefaultToolkit().getSystemEventQueue();
        // Typed vaadinx.awt.EventQueue: the JDK toolkit's real queue would be
        // both uncallable from swapped code and inert on the server.
        EventQueue again = Toolkit.getDefaultToolkit().getSystemEventQueue();
        assertSame(q, again, "the JDK hands out one system queue; so do we");
        assertNoWarns(capturedWarns,
                "handing out the queue is not itself unimplemented; got " + capturedWarns);
    }

    // --- The registration the ported listener type unblocks ----------------

    @Test
    @DisplayName("JTable can register itself on its own column model")
    void tableRegistersOnItsOwnColumnModel() {
        JTable table = new JTable();
        // The JDK idiom. It did not compile while JTable implemented the JDK
        // TableColumnModelListener and getColumnModel() returned a ported
        // model — a whole-signature mismatch no runtime test could have found.
        table.getColumnModel().addColumnModelListener(table);
        table.getColumnModel().removeColumnModelListener(table);
    }
}
