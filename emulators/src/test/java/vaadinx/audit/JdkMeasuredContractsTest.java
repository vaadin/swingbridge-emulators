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

package vaadinx.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.JButton;
import vaadinx.swing.JComboBox;
import vaadinx.swing.JComponent;
import vaadinx.swing.JDialog;
import vaadinx.swing.JEditorPane;
import vaadinx.swing.JFrame;
import vaadinx.swing.JInternalFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JList;
import vaadinx.swing.JMenu;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.MenuElement;
import vaadinx.swing.MenuSelectionManager;
import vaadinx.swing.JOptionPane;
import vaadinx.swing.JPanel;
import vaadinx.swing.JPopupMenu;
import vaadinx.swing.JRootPane;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JTextField;
import vaadinx.swing.JTree;
import vaadinx.swing.JViewport;
import vaadinx.swing.JWindow;
import vaadinx.swing.TransferHandler;

import javax.swing.DropMode;
import javax.swing.tree.TreePath;

import java.awt.Point;
import java.awt.event.ItemEvent;
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
 * Pins the emulator answers that {@link JdkReturnValueDiffer} caught disagreeing with a
 * real JDK, to the value the JDK actually gave.
 *
 * <p>Every expectation here was <b>measured, not recalled</b> — read off JDK 25 through the
 * differ or a throwaway probe beside it, which is the point of the whole exercise: a suite
 * written from the implementation's own premise restates the premise instead of testing it
 * (SD_sjpasswordfield). Each test therefore names the JDK's answer in its assertion
 * message, so a future maintainer can tell a measurement from a guess.
 *
 * <p>These live together rather than in each component's own test class because their
 * oracle is one thing — a JDK run — and the ratchet only reads as a ratchet in one place.
 */
class JdkMeasuredContractsTest extends AbstractKaribuTest {

    @Test
    @DisplayName("JLabel.setDisplayedMnemonic(char) upper-cases into the int form instead of recursing")
    void displayedMnemonicCharOverload() {
        JLabel label = new JLabel("File");
        label.setDisplayedMnemonic('x');
        assertEquals(88, label.getDisplayedMnemonic(), "JDK 25 answers 88 ('X') for 'x'");
        label.setDisplayedMnemonic('Y');
        assertEquals(89, label.getDisplayedMnemonic(), "JDK 25 answers 89 for 'Y'");
    }

    @Test
    @DisplayName("JMenu keeps the submenu delay it is given, and rejects a negative one as Swing does")
    void menuDelayIsStored() {
        JMenu menu = new JMenu("File");
        assertEquals(0, menu.getDelay());
        menu.setDelay(3);
        assertEquals(3, menu.getDelay(), "the effect may go, the state may not (R_decline_effect_only)");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> menu.setDelay(-1));
        assertEquals("Delay must be a positive integer", e.getMessage(), "JDK 25's own message");
    }

    @Test
    @DisplayName("JTextField keeps the scroll offset it is given")
    void scrollOffsetIsStored() {
        JTextField field = new JTextField();
        field.setScrollOffset(3);
        assertEquals(3, field.getScrollOffset());
    }

    @Test
    @DisplayName("JPopupMenu keeps the lightweight-popup flag, defaulting true")
    void lightWeightPopupFlagIsStored() {
        JPopupMenu popup = new JPopupMenu();
        assertTrue(popup.isLightWeightPopupEnabled(), "JDK default");
        popup.setLightWeightPopupEnabled(false);
        assertFalse(popup.isLightWeightPopupEnabled());
    }

    @Test
    @DisplayName("JList keeps the prototype cell value even though the surrogate drops it")
    void prototypeCellValueIsStored() {
        JList<String> list = new JList<>(new String[]{"a", "b"});
        list.setPrototypeCellValue("ADMIN");
        assertEquals("ADMIN", list.getPrototypeCellValue());
    }

    @Test
    @DisplayName("JList accepts the four drop modes the JDK accepts and rejects the other four")
    void listDropModes() {
        JList<String> list = new JList<>(new String[]{"a"});
        for (DropMode accepted : new DropMode[]{
                DropMode.USE_SELECTION, DropMode.ON, DropMode.INSERT, DropMode.ON_OR_INSERT}) {
            list.setDropMode(accepted);
            assertEquals(accepted, list.getDropMode(), "JDK 25 accepts " + accepted + " on a JList");
        }
        for (DropMode rejected : new DropMode[]{
                DropMode.INSERT_ROWS, DropMode.INSERT_COLS,
                DropMode.ON_OR_INSERT_ROWS, DropMode.ON_OR_INSERT_COLS}) {
            assertThrows(IllegalArgumentException.class, () -> list.setDropMode(rejected),
                    "JDK 25 rejects " + rejected + " on a JList");
        }
    }

    @Test
    @DisplayName("JViewport stores a scroll mode outside its own constants, because the JDK does")
    void scrollModeIsNotValidated() {
        JViewport viewport = new JViewport();
        viewport.setScrollMode(99);
        assertEquals(99, viewport.getScrollMode(),
                "JDK 25 answers 99 — validating here would be an unrequested improvement");
    }

    @Test
    @DisplayName("JOptionPane.setOptionType throws a bare RuntimeException, not IllegalArgumentException")
    void optionTypeThrowsWhatSwingThrows() {
        JOptionPane pane = new JOptionPane();
        RuntimeException e = assertThrows(RuntimeException.class, () -> pane.setOptionType(3));
        assertFalse(e instanceof IllegalArgumentException,
                "javax.swing.JOptionPane throws a bare RuntimeException here (measured on JDK 25)");
    }

    @Test
    @DisplayName("a plain JButton carries selection state and fires ItemEvent then ChangeEvent")
    void plainButtonIsSelectable() {
        JButton button = new JButton("go");
        List<String> fired = new ArrayList<>();
        button.addItemListener(e -> fired.add("item:" + e.getStateChange()));
        button.addChangeListener(e -> fired.add("change"));
        button.addActionListener(e -> fired.add("action"));

        assertFalse(button.isSelected());
        button.setSelected(true);
        assertTrue(button.isSelected(), "JDK 25: new JButton().setSelected(true) then isSelected() is true");
        assertEquals(List.of("item:" + ItemEvent.SELECTED, "change"), fired,
                "DefaultButtonModel fires ItemEvent then ChangeEvent, and no ActionEvent");

        fired.clear();
        button.setSelected(true);
        assertEquals(List.of(), fired, "no event when the flag does not move");

        button.setSelected(false);
        assertEquals(List.of("item:" + ItemEvent.DESELECTED, "change"), fired);
    }

    @Test
    @DisplayName("setCaretPosition rejects a position outside the document, with Swing's message")
    void caretPositionIsBoundsChecked() {
        JTextField field = new JTextField();
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> field.setCaretPosition(3));
        assertEquals("bad position: 3", e.getMessage(), "JDK 25's own message");
        assertThrows(IllegalArgumentException.class, () -> field.setCaretPosition(-1));
        field.setText("hello");
        field.setCaretPosition(3);
        field.setCaretPosition(5);
        assertThrows(IllegalArgumentException.class, () -> field.setCaretPosition(6),
                "the document length itself is valid, one past it is not");
    }

    @Test
    @DisplayName("a text component keeps dragEnabled, and takes only the two drop modes Swing takes")
    void textComponentDragAndDropState() {
        JTextField field = new JTextField();
        assertFalse(field.getDragEnabled());
        field.setDragEnabled(true);
        assertTrue(field.getDragEnabled());

        assertEquals(DropMode.USE_SELECTION, field.getDropMode(), "JDK default");
        field.setDropMode(DropMode.INSERT);
        assertEquals(DropMode.INSERT, field.getDropMode());
        for (DropMode rejected : new DropMode[]{
                DropMode.ON, DropMode.ON_OR_INSERT, DropMode.INSERT_ROWS, DropMode.INSERT_COLS,
                DropMode.ON_OR_INSERT_ROWS, DropMode.ON_OR_INSERT_COLS}) {
            assertThrows(IllegalArgumentException.class, () -> field.setDropMode(rejected),
                    "JDK 25 rejects " + rejected + " on a text component");
        }
    }

    @Test
    @DisplayName("a non-editable JComboBox refuses an item its model does not hold")
    void comboSelectionIsRejectedWhenNotInTheModel() {
        JComboBox<String> empty = new JComboBox<>();
        empty.setSelectedItem("ADMIN");
        assertNull(empty.getSelectedItem(),
                "JDK 25: a non-editable JComboBox ignores an item that is not in the model");

        JComboBox<String> combo = new JComboBox<>(new String[]{"a", "b"});
        combo.setSelectedItem("ADMIN");
        assertEquals("a", combo.getSelectedItem(), "the refused item leaves the selection where it was");
        combo.setSelectedItem("b");
        assertEquals("b", combo.getSelectedItem());
        combo.setSelectedItem(null);
        assertNull(combo.getSelectedItem(), "null bypasses the check, as in the JDK");

        combo.setEditable(true);
        combo.setSelectedItem("ADMIN");
        assertEquals("ADMIN", combo.getSelectedItem(), "editable takes any value");
    }

    @Test
    @DisplayName("JEditorPane answers the kit's content type, so an unknown one reads back as text/plain")
    void contentTypeResolvesThroughTheKitRegistry() {
        JEditorPane pane = new JEditorPane();
        assertEquals("text/plain", pane.getContentType(), "JDK default");
        pane.setContentType("ADMIN");
        assertEquals("text/plain", pane.getContentType(),
                "JDK 25 resolves an unregistered type to the plain kit");
        pane.setContentType("text/html; charset=utf-8");
        assertEquals("text/html", pane.getContentType(), "the parameter list never survives");
        pane.setContentType("text/rtf");
        assertEquals("text/rtf", pane.getContentType(), "one of the four types with a default kit");
        pane.setContentType("TEXT/HTML");
        assertEquals("text/plain", pane.getContentType(),
                "the JDK's kit lookup is exact-string, so a mis-cased type finds no kit");
    }

    @Test
    @DisplayName("a viewport holding no view drops viewPosition / viewSize and answers the origin")
    void viewportWithNoViewHoldsNothing() {
        // The JDK keeps no field for either: it writes them onto the view's own
        // location (negated) / size, so with nothing in the viewport there is
        // nowhere to put the value and both getters answer zero.
        JViewport viewport = new JScrollPane().getViewport();
        viewport.setViewPosition(new Point(7, 11));
        viewport.setViewSize(new java.awt.Dimension(120, 40));
        assertEquals(new Point(0, 0), viewport.getViewPosition(), "JDK 25 answers (0,0)");
        assertEquals(new java.awt.Dimension(0, 0), viewport.getViewSize(), "JDK 25 answers (0,0)");
    }

    @Test
    @DisplayName("a combo box popup cannot open while the combo box is not showing")
    void popupNeedsAShowingComboBox() {
        // Swing reaches this a layer down: BasicComboPopup.show() fires, then asks the
        // combo box for getLocationOnScreen(), which throws unless the component is
        // showing. The hide path never asks for a location, so it stays silent.
        JComboBox<String> combo = new JComboBox<>(new String[]{"a", "b"});
        List<String> events = recordPopupEvents(combo);
        java.awt.IllegalComponentStateException e = assertThrows(
                java.awt.IllegalComponentStateException.class, () -> combo.setPopupVisible(true));
        assertEquals("component must be showing on the screen to determine its location",
                e.getMessage(), "java.awt.Component's own message");
        assertEquals(List.of("visible(false)"), events, "JDK 25 notifies, then throws");
        assertThrows(java.awt.IllegalComponentStateException.class, combo::showPopup);
        combo.hidePopup();
        combo.setPopupVisible(false);
        assertFalse(combo.isPopupVisible());
        assertEquals(List.of("visible(false)", "visible(false)"), events,
                "JDK 25 fires nothing on hiding a popup that never opened");
    }

    @Test
    @DisplayName("combo box popup events fire before the popup changes, last listener first")
    void comboPopupEventsFireBeforeTheChange() {
        // Measured on JDK 25 under xvfb: BasicComboPopup.show() fires unconditionally,
        // before the popup is visible; hide() reaches JPopupMenu.setVisible(false),
        // which fires while still visible and returns early on a hidden popup.
        JComboBox<String> combo = new JComboBox<>(new String[]{"a", "b"});
        com.vaadin.flow.component.UI.getCurrent().getElement()
                .appendChild(combo.getPeer().getElement());
        List<String> events = new ArrayList<>();
        combo.addPopupMenuListener(recorder(combo, events, "first:"));
        combo.addPopupMenuListener(recorder(combo, events, "second:"));

        combo.showPopup();
        assertEquals(List.of("second:visible(false)", "first:visible(false)"), events,
                "JDK 25 fires last-added first, before the popup is visible");
        events.clear();
        combo.showPopup();
        assertEquals(List.of("second:visible(true)", "first:visible(true)"), events,
                "JDK 25 fires again on an open popup");
        events.clear();
        combo.hidePopup();
        assertEquals(List.of("second:invisible(true)", "first:invisible(true)"), events,
                "JDK 25 fires while the popup is still visible");
        events.clear();
        combo.hidePopup();
        assertEquals(List.of(), events, "JDK 25 fires nothing on hiding a hidden popup");
    }

    /** Records each popup event with what {@code isPopupVisible()} answered inside it. */
    private static javax.swing.event.PopupMenuListener recorder(
            JComboBox<?> combo, List<String> events, String tag) {
        return new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                events.add(tag + "visible(" + combo.isPopupVisible() + ")");
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                events.add(tag + "invisible(" + combo.isPopupVisible() + ")");
            }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                events.add(tag + "canceled");
            }
        };
    }

    private static List<String> recordPopupEvents(JComboBox<?> combo) {
        List<String> events = new ArrayList<>();
        combo.addPopupMenuListener(recorder(combo, events, ""));
        return events;
    }

    @Test
    @DisplayName("every window answers AWT's default BorderLayout, and emits no CSS for it")
    void windowsCarryAwtsDefaultLayout() {
        // java.awt.Window.init():507 ends in setLayout(new BorderLayout()), so getLayout()
        // answers one on the desktop for all three of these. Measured under xvfb, since
        // none of them constructs headless — which is why the gate cannot see this pair
        // and only the on-demand sweep found it.
        for (vaadinx.awt.Window window : List.of(new JFrame(), new JDialog(), new JWindow())) {
            assertInstanceOf(vaadinx.awt.BorderLayout.class, window.getLayout(),
                    window.getClass().getSimpleName() + " should carry AWT's ctor default");
            // The regression guard for where that manager is kept. It lives in Window's own
            // slot, not Container's, because Container's is what doLayout dispatches to CSS
            // — and a window's peer is a <vaadin-dialog>, which LayoutCss' <div> gate
            // refuses with an unsupported-peer-shape ERROR. Moving it into Container's slot reddens here.
            assertNull(window.getPeer().getElement().getStyle().get("display"),
                    "no layout CSS may be emitted onto the overlay element");
        }
    }

    @Test
    @DisplayName("a frame's own layout survives setLayout, which redirects to the content pane")
    void frameSetLayoutRedirectsAndLeavesItsOwnLayoutAlone() {
        // The JDK's JFrame.setLayout goes to the content pane while root-pane checking is
        // on, so the frame's own layout stays the Window ctor's BorderLayout and it is the
        // content pane that changes. Both layers, identically.
        JFrame frame = new JFrame();
        vaadinx.awt.LayoutManager installed = new vaadinx.awt.FlowLayout();
        frame.setLayout(installed);
        assertSame(installed, frame.getContentPane().getLayout(), "redirected");
        assertInstanceOf(vaadinx.awt.BorderLayout.class, frame.getLayout(),
                "the frame's own slot still holds AWT's default");
    }

    @Test
    @DisplayName("a scroll pane refuses any layout but a ScrollPaneLayout")
    void scrollPaneRejectsForeignLayouts() {
        // JScrollPane.setLayout takes a ScrollPaneLayout or null and throws for anything
        // else. SB-Emulators ports no ScrollPaneLayout, so the accept-branch is
        // unreachable — which is fine, because after the import swap a migrator holds no
        // type that would satisfy the JDK's either.
        JScrollPane pane = new JScrollPane();
        ClassCastException e = assertThrows(ClassCastException.class,
                () -> pane.setLayout(new vaadinx.awt.FlowLayout()));
        assertEquals("layout of JScrollPane must be a ScrollPaneLayout", e.getMessage(),
                "the JDK's own message");
        pane.setLayout(null);
        assertNull(pane.getLayout(), "null is the JDK's other accepted argument");
    }

    @Test
    @DisplayName("an unattached viewport keeps its view, where the JDK keeps it as a child")
    void unboundViewportKeepsItsView() {
        // JViewport.getView() is its own getComponent(0) in the JDK, so a viewport
        // nothing has attached to a scroll pane still holds what you gave it. This
        // dropped the view and answered null: the scrolling is the effect and may go,
        // the view is state and may not (R_decline_effect_only).
        JViewport viewport = new JViewport();
        JLabel view = new JLabel("probe");
        viewport.setView(view);
        assertSame(view, viewport.getView());
        assertSame(view, viewport.getComponent(0), "the JDK's store is the child list");

        viewport.setView(null);
        assertNull(viewport.getView());
        assertEquals(0, viewport.getComponentCount(), "the JDK removes every child first");
    }

    @Test
    @DisplayName("a tree's lead path is its own store, and selection listeners see the stale one")
    void treeLeadPathIsNotTheSelectionModels() {
        // Measured on JDK 25 under a real L&F, which is what makes this testable at
        // all: BasicTreeUI is what keeps anchor and lead in step, from some thirty
        // sites. Two facts, and the emulator reproduces both.
        JTree tree = new JTree();
        List<String> log = new ArrayList<>();
        tree.addTreeSelectionListener(e -> log.add("listener sees lead=" + tree.getLeadSelectionPath()));
        tree.addPropertyChangeListener(JTree.LEAD_SELECTION_PATH_PROPERTY, e -> log.add("lead PCE"));
        tree.addPropertyChangeListener(JTree.ANCHOR_SELECTION_PATH_PROPERTY, e -> log.add("anchor PCE"));

        TreePath row1 = tree.getPathForRow(1);
        tree.setSelectionPath(row1);
        assertEquals(row1, tree.getLeadSelectionPath(), "the UI's job, done by the selection bridge");
        assertEquals(row1, tree.getAnchorSelectionPath());
        assertEquals(List.of("listener sees lead=null", "anchor PCE", "lead PCE"), log,
                "JDK 25: the TreeSelectionListener runs FIRST and reads the previous lead; "
                        + "the two bound properties arrive after it, anchor before lead");

        // Fact two: JTree.leadPath and the selection model's lead are separate stores.
        // Measured — setting a path that is in no model moves one and not the other.
        TreePath alien = new TreePath(new javax.swing.tree.DefaultMutableTreeNode("alien"));
        tree.setLeadSelectionPath(alien);
        assertEquals(alien, tree.getLeadSelectionPath());
        assertEquals(row1, tree.getSelectionModel().getLeadSelectionPath(),
                "the model's lead is untouched by JTree's own setter");
        assertEquals(-1, tree.getLeadSelectionRow(), "no row for a path outside the tree");
    }

    @Test
    @DisplayName("an installed EditorKit and NavigationFilter read back, effects declined or not")
    void installedCollaboratorsReadBack() {
        // Both were pure onUnimplemented + a null getter. The JDK's setNavigationFilter
        // body is a bare field write with no event at all, and setEditorKit stores
        // before it installs — so in both cases the state was owed and only the effect
        // was ever deferrable (R_decline_effect_only).
        JEditorPane pane = new JEditorPane();
        javax.swing.text.EditorKit kit = new javax.swing.text.DefaultEditorKit();
        pane.setEditorKit(kit);
        assertSame(kit, pane.getEditorKit());
        assertEquals("text/plain", pane.getContentType(),
                "getContentType reads the peer, not the kit — so storing one moves nothing else");

        javax.swing.text.NavigationFilter filter = new javax.swing.text.NavigationFilter();
        pane.setNavigationFilter(filter);
        assertSame(filter, pane.getNavigationFilter());
    }

    @Test
    @DisplayName("a popup menu goes visible with no invoker and no displayability check")
    void popupMenuVisibilityHasNoGate() throws Exception {
        // Measured on JDK 25: JPopupMenu.isVisible() is `popup != null`, the object
        // PopupFactory hands back, and setVisible checks nothing but b == isVisible().
        // A bare popup with no invoker, never added anywhere, reports true.
        JPopupMenu popup = new JPopupMenu();
        popup.add("item");
        List<String> fired = new ArrayList<>();
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { fired.add("visible"); }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { fired.add("invisible"); }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { fired.add("canceled"); }
        });
        popup.addPropertyChangeListener("visible", e -> fired.add("pce " + e.getOldValue() + "->" + e.getNewValue()));

        assertFalse(popup.isVisible(), "JDK default: popup == null");
        popup.setVisible(true);
        assertTrue(popup.isVisible(), "JDK 25 answers true with no invoker and nothing showing");
        assertEquals(List.of("visible", "pce false->true"), fired,
                "the JDK fires the PopupMenuListener event, then the bound property");

        fired.clear();
        popup.setVisible(true);
        assertEquals(List.of(), fired, "b == isVisible() is the JDK's only gate, and it is silent");

        popup.setVisible(false);
        assertFalse(popup.isVisible());
        assertEquals(List.of("invisible", "pce true->false"), fired);

        fired.clear();
        popup.setVisible(false);
        assertEquals(List.of(), fired,
                "hiding an already-hidden popup fires nothing at all — not even popupMenuCanceled, "
                        + "which the JDK reaches only through the client property its L&F sets");
    }

    @Test
    @DisplayName("an internal frame cannot be selected while not showing, but can be deselected")
    void internalFrameSelectionIsGatedAsymmetrically() throws Exception {
        // Measured on JDK 25: the gate is `selected && !isShowing()`, so select is
        // refused and deselect goes through — and a refused select fires nothing,
        // not even the vetoable change.
        JInternalFrame frame = new JInternalFrame("f", true, true, true, true);
        List<String> fired = new ArrayList<>();
        frame.addVetoableChangeListener(e -> fired.add("vce " + e.getPropertyName()));
        frame.addPropertyChangeListener(JInternalFrame.IS_SELECTED_PROPERTY,
                e -> fired.add("pce " + e.getOldValue() + "->" + e.getNewValue()));

        assertFalse(frame.isShowing(), "never shown, never added");
        frame.setSelected(true);
        assertFalse(frame.isSelected(), "JDK 25 refuses to select a frame that is not showing");
        assertEquals(List.of(), fired, "a refused select is silent — the gate precedes the veto");

        frame.setSelected(false);
        assertFalse(frame.isSelected());
        assertEquals(List.of(), fired, "isSelected == selected short-circuits first");
    }

    @Test
    @DisplayName("TransferHandler's drag-image offset round-trips, defaults to (0,0) and copies on read")
    void dragImageOffset() {
        TransferHandler handler = new TransferHandler("text");
        assertEquals(new Point(0, 0), handler.getDragImageOffset(), "JDK default");
        handler.setDragImageOffset(new Point(7, 11));
        assertEquals(new Point(7, 11), handler.getDragImageOffset());
        assertNotSame(handler.getDragImageOffset(), handler.getDragImageOffset(),
                "the JDK hands out a fresh Point per read");
        assertThrows(NullPointerException.class, () -> handler.setDragImageOffset(null),
                "the JDK's own new Point(p.x, p.y) NPEs on null");
    }

    @Test
    @DisplayName("alignment is stored and clamped, and the per-class ctor defaults differ")
    void alignmentIsStoredClampedAndDefaulted() {
        JLabel label = new JLabel("x");
        assertEquals(0.0f, label.getAlignmentX(), "JDK 25: JLabel's ctor ends in setAlignmentX(LEFT_ALIGNMENT)");
        assertEquals(0.5f, label.getAlignmentY(), "unset, so Component's CENTER_ALIGNMENT");

        label.setAlignmentX(2.0f);
        assertEquals(1.0f, label.getAlignmentX(), "JDK 25 clamps to 1.0f");
        label.setAlignmentX(-3.0f);
        assertEquals(0.0f, label.getAlignmentX(), "JDK 25 clamps to 0.0f");
        label.setAlignmentY(0.25f);
        assertEquals(0.25f, label.getAlignmentY());

        JButton button = new JButton("go");
        assertEquals(0.0f, button.getAlignmentX(), "JDK 25: AbstractButton.init ends in setAlignmentX(LEFT)");
        assertEquals(0.5f, button.getAlignmentY(), "JDK 25: … and setAlignmentY(CENTER)");
        assertEquals(0.5f, new JPanel().getAlignmentX(), "JPanel's ctor sets neither, so Component answers");
    }

    @Test
    @DisplayName("double buffering is stored, and JPanel / JRootPane turn it on from their constructors")
    void doubleBufferingIsStored() {
        assertFalse(new JLabel("x").isDoubleBuffered(), "JDK 25: a plain JComponent is not double-buffered");
        assertTrue(new JPanel().isDoubleBuffered(), "JDK 25: JPanel's ctor calls setDoubleBuffered(true)");
        assertFalse(new JPanel(new vaadinx.awt.FlowLayout(), false).isDoubleBuffered(),
                "JDK 25: the ctor argument reaches the flag");
        assertTrue(new JRootPane().isDoubleBuffered(), "JDK 25: JRootPane's ctor calls setDoubleBuffered(true)");

        JLabel label = new JLabel("x");
        label.setDoubleBuffered(true);
        assertTrue(label.isDoubleBuffered(), "the buffer may go, the flag may not (R_decline_effect_only)");
    }

    @Test
    @DisplayName("debugGraphicsOptions stores a positive mask, ignores 0 and clears on a negative")
    void debugGraphicsOptionsThreeBranches() {
        JLabel label = new JLabel("x");
        assertEquals(0, label.getDebugGraphicsOptions());
        label.setDebugGraphicsOptions(3);
        assertEquals(3, label.getDebugGraphicsOptions());
        label.setDebugGraphicsOptions(1);
        assertEquals(1, label.getDebugGraphicsOptions(), "a later positive value replaces, it does not OR");
        label.setDebugGraphicsOptions(0);
        assertEquals(1, label.getDebugGraphicsOptions(),
                "JDK 25: DebugGraphicsInfo returns early on 0, so NONE_OPTION leaves the mask standing");
        label.setDebugGraphicsOptions(-1);
        assertEquals(0, label.getDebugGraphicsOptions(), "JDK 25: a negative removes the entry, so reads answer 0");
    }

    @Test
    @DisplayName("autoscrolls and requestFocusEnabled round-trip, with the JDK's defaults")
    void flagsRoundTrip() {
        JLabel label = new JLabel("x");
        assertFalse(label.getAutoscrolls(), "JDK default");
        assertTrue(label.isRequestFocusEnabled(), "JDK default — the flag is stored inverted, as REQUEST_FOCUS_DISABLED");
        label.setAutoscrolls(true);
        label.setRequestFocusEnabled(false);
        assertTrue(label.getAutoscrolls());
        assertFalse(label.isRequestFocusEnabled());
    }

    @Test
    @DisplayName("nextFocusableComponent round-trips as the JDK's own client property, PCE included")
    void nextFocusableComponentIsAClientProperty() {
        JLabel label = new JLabel("x");
        JLabel target = new JLabel("t");
        List<String> fired = new ArrayList<>();
        label.addPropertyChangeListener("nextFocus", e -> fired.add(String.valueOf(e.getNewValue() != null)));

        assertNull(label.getNextFocusableComponent(), "JDK default");
        label.setNextFocusableComponent(target);
        assertSame(target, label.getNextFocusableComponent());
        assertSame(target, label.getClientProperty("nextFocus"),
                "JDK 25 stores it under the \"nextFocus\" client property, which is why the PCE fires");
        label.setNextFocusableComponent(null);
        assertNull(label.getNextFocusableComponent(), "null clears it");
        assertEquals(List.of("true", "false"), fired);
    }

    @Test
    @DisplayName("a window keeps autoRequestFocus, locationByPlatform and opacity, with AWT's gates")
    void windowFlagsAreStoredBehindAwtsOwnGates() {
        JWindow window = new JWindow();
        assertTrue(window.isAutoRequestFocus(), "AWT default");
        assertFalse(window.isLocationByPlatform(), "AWT default");
        assertEquals(1.0f, window.getOpacity(), "AWT default");

        window.setAutoRequestFocus(false);
        window.setLocationByPlatform(true);
        window.setOpacity(0.5f);
        assertFalse(window.isAutoRequestFocus());
        assertTrue(window.isLocationByPlatform());
        assertEquals(0.5f, window.getOpacity(), "the peer push drops, the value does not");

        assertThrows(IllegalArgumentException.class, () -> window.setOpacity(1.5f),
                "AWT 25 rejects anything outside [0, 1]");
        assertThrows(IllegalArgumentException.class, () -> window.setOpacity(-0.1f));
        assertEquals(0.5f, window.getOpacity(), "a refused write leaves the previous value");
    }

    @Test
    @DisplayName("setLocationByPlatform(true) on a showing window throws, as AWT does before storing")
    void locationByPlatformIsRefusedWhileShowing() {
        JFrame frame = new JFrame("f");
        frame.setVisible(true);
        assertTrue(frame.isShowing());
        assertThrows(java.awt.IllegalComponentStateException.class, () -> frame.setLocationByPlatform(true),
                "AWT 25: \"The window is showing on screen.\"");
        assertFalse(frame.isLocationByPlatform(), "the gate precedes the write");
        frame.setLocationByPlatform(false);
        assertFalse(frame.isLocationByPlatform(), "false is not gated — the gate is on true alone");
        frame.dispose();
    }

    @Test
    @DisplayName("a Frame answers null for an AWT menu bar it was never given")
    void awtMenuBarDefaultsToNull() {
        // The round-trip cannot be asserted here: java.awt.MenuBar's constructor is
        // headless-hostile, and this suite runs -Djava.awt.headless=true on purpose. The
        // xvfb sweep covers it, which is the division of labour D_return_value_audit sets out.
        vaadinx.awt.Frame frame = new JFrame("f");
        assertNull(frame.getMenuBar(), "AWT default");
        frame.setMenuBar(null);
        assertNull(frame.getMenuBar(), "AWT's own early return on an unchanged bar");
    }

    @Test
    @DisplayName("FileDialog keeps the directory hint, normalising \"\" to null as AWT does")
    void fileDialogDirectoryIsStored() {
        vaadinx.awt.FileDialog dialog = new vaadinx.awt.FileDialog((vaadinx.awt.Frame) null, "Open");
        assertNull(dialog.getDirectory(), "AWT default");
        dialog.setDirectory("/tmp");
        assertEquals("/tmp", dialog.getDirectory());
        dialog.setDirectory("");
        assertNull(dialog.getDirectory(), "AWT 25 stores the empty string as null");
        dialog.setDirectory(null);
        assertNull(dialog.getDirectory());
    }

    @Test
    @DisplayName("a combo box keeps an installed KeySelectionManager, though nothing here calls it")
    void keySelectionManagerIsStored() {
        JComboBox<String> combo = new JComboBox<>(new String[] {"a", "b"});
        assertNull(combo.getKeySelectionManager(),
                "JDK 25 answers a BasicComboBoxUI$DefaultKeySelectionManager here — an L&F object, "
                        + "and L&F dispatch is out of scope (R_match_swing_errors sub-bucket (b))");
        JComboBox.KeySelectionManager manager = (key, model) -> -1;
        combo.setKeySelectionManager(manager);
        assertSame(manager, combo.getKeySelectionManager());
    }

    @Test
    @DisplayName("MenuSelectionManager keeps the selected path, notifies each element, and fires every time")
    void selectedPathIsKeptAndNotified() {
        MenuSelectionManager manager = new MenuSelectionManager();
        List<String> fired = new ArrayList<>();
        manager.addChangeListener(e -> fired.add("change"));
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        menu.add(item);

        assertEquals(0, manager.getSelectedPath().length, "JDK default: an empty path, not null");
        manager.setSelectedPath(new MenuElement[] {menu, item});
        assertEquals(2, manager.getSelectedPath().length);
        assertTrue(manager.isComponentPartOfCurrentMenu(menu), "the path's own root");
        assertFalse(manager.isComponentPartOfCurrentMenu(new JMenuItem("elsewhere")));
        assertFalse(manager.isComponentPartOfCurrentMenu(item),
                "the JDK reaches it by walking the root's sub-elements, and a JMenu here has none "
                        + "— it owns no JPopupMenu, the same structural gap as setPopupMenuVisible");

        manager.setSelectedPath(new MenuElement[] {menu});
        assertEquals(1, manager.getSelectedPath().length, "the shared prefix stays, the tail leaves");
        manager.clearSelectedPath();
        assertEquals(0, manager.getSelectedPath().length);
        manager.setSelectedPath(null);
        assertEquals(0, manager.getSelectedPath().length, "JDK 25 treats null as an empty path");
        assertEquals(4, fired.size(), "JDK 25 fires unconditionally — even for a no-change path");
    }

    @Test
    @DisplayName("the default locale is stored per UI, and null resets it to the browser's")
    void defaultLocaleIsStoredPerUI() {
        java.util.Locale browser = com.vaadin.flow.component.UI.getCurrent().getLocale();
        assertEquals(browser, JComponent.getDefaultLocale(), "unset, so the browser's locale");
        JComponent.setDefaultLocale(java.util.Locale.GERMANY);
        assertEquals(java.util.Locale.GERMANY, JComponent.getDefaultLocale(),
                "JDK 25: a set default is what getDefaultLocale answers");
        JComponent.setDefaultLocale(null);
        assertEquals(browser, JComponent.getDefaultLocale(),
                "JDK 25: null resets — there to the JVM default, here to the browser's");
    }
}
