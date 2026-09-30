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
import com.vaadin.swingbridge.surrogates.SJToolBar;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.SwingConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for vaadinx.swing.JToolBar (D_jtoolbar). Pairs with
 * SJToolBarTest — this layer covers the thin shell's forwarding behaviour:
 *
 * <ol>
 *  <li><b>Ctor variants</b> — all four converge on the (name, orientation)
 *     root; SJToolBar peer always installed; orientation validation
 *     bubbles up from the peer per R_match_swing_errors.
 *  <li><b>Orientation forwarding</b> — setOrientation flips the peer's CSS;
 *     getOrientation reads back through the peer.
 *  <li><b>Emulator-only field shadows</b> — floatable / rollover /
 *     borderPainted round-trip via emulator field storage, fire PCE on
 *     change, no peer touch.
 *  <li><b>Margin forwarding</b> — setMargin writes through to the peer's
 *     CSS padding round-trip.
 *  <li><b>addSeparator forwarding</b> — both no-arg and Dimension overloads
 *     reach the peer and push a separator child.
 *  <li><b>add(Action) convenience</b> — wraps the Action in a JButton wired
 *     via setAction, adds it, returns the JButton.
 *  <li><b>getComponentIndex / getComponentAtIndex</b> — walk the inherited
 *     AWT children array.
 *  <li><b>L&amp;F surface</b> — getUIClassID == "ToolBarUI"; getUI / setUI WARN.
 * </ol>
 */
class JToolBarTest extends AbstractKaribuTest {

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

    private static SJToolBar peerOf(JToolBar tb) {
        return (SJToolBar) tb.getPeer();
    }

    // --- Constructors -------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor wires SJToolBar peer with HORIZONTAL default")
    void noArgCtorWiresPeerWithHorizontalDefault() {
        JToolBar tb = new JToolBar();
        assertInstanceOf(SJToolBar.class, tb.getPeer());
        assertEquals(SwingConstants.HORIZONTAL, tb.getOrientation());
        assertTrue(tb.isFloatable());  // JDK default
        assertFalse(tb.isRollover());
        assertTrue(tb.isBorderPainted());
        assertNoWarns(capturedWarns, "default ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("int-arg ctor accepts VERTICAL and passes through to peer")
    void intArgCtorAcceptsVertical() {
        JToolBar tb = new JToolBar(SwingConstants.VERTICAL);
        assertEquals(SwingConstants.VERTICAL, tb.getOrientation());
        assertEquals(SwingConstants.VERTICAL, peerOf(tb).getOrientation());
    }

    @Test
    @DisplayName("String-arg ctor sets the name (Component name)")
    void stringArgCtorSetsTheName() {
        JToolBar tb = new JToolBar("Edit");
        assertEquals("Edit", tb.getName());
        assertEquals(SwingConstants.HORIZONTAL, tb.getOrientation());
    }

    @Test
    @DisplayName("String-int ctor sets both name and orientation")
    void stringIntCtorSetsNameAndOrientation() {
        JToolBar tb = new JToolBar("Edit", SwingConstants.VERTICAL);
        assertEquals("Edit", tb.getName());
        assertEquals(SwingConstants.VERTICAL, tb.getOrientation());
    }

    @Test
    @DisplayName("int-arg ctor throws IAE on garbage orientation")
    void intArgCtorThrowsOnGarbageOrientation() {
        assertThrows(IllegalArgumentException.class, () -> new JToolBar(42));
    }

    // --- Orientation forwarding --------------------------------------------

    @Test
    @DisplayName("setOrientation forwards to peer (CSS flex-direction flip)")
    void setOrientationForwardsToPeer() {
        JToolBar tb = new JToolBar();
        tb.setOrientation(SwingConstants.VERTICAL);
        SJToolBar peer = peerOf(tb);
        assertEquals(SwingConstants.VERTICAL, peer.getOrientation());
        assertEquals("column", peer.getElement().getStyle().get("flex-direction"));
    }

    // --- Emulator-only field shadows (floatable / rollover / borderPainted)

    @Test
    @DisplayName("setFloatable round-trips via emulator field (no peer touch)")
    void setFloatableRoundTrips() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);   // JLawyer's common path
        assertFalse(tb.isFloatable());
        tb.setFloatable(true);
        assertTrue(tb.isFloatable());
        assertNoWarns(capturedWarns,
                "setFloatable round-trip should be WARN-free (emulator-only shadow): " + capturedWarns);
    }

    @Test
    @DisplayName("setFloatable fires PCE on change")
    void setFloatableFiresPceOnChange() {
        JToolBar tb = new JToolBar();
        List<PropertyChangeEvent> events = new ArrayList<>();
        tb.addPropertyChangeListener("floatable", events::add);
        tb.setFloatable(false);
        PropertyChangeEvent e = assertSingle(events);
        assertEquals(true, e.getOldValue());
        assertEquals(false, e.getNewValue());

        events.clear();
        tb.setFloatable(false);  // no-op
        assertTrue(events.isEmpty(), "no-op on equal floatable should fire no PCE");
    }

    @Test
    @DisplayName("setRollover round-trips via emulator field")
    void setRolloverRoundTrips() {
        JToolBar tb = new JToolBar();
        tb.setRollover(true);   // JLawyer's common path
        assertTrue(tb.isRollover());
        assertNoWarns(capturedWarns,
                "setRollover round-trip should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("setBorderPainted round-trips via emulator field")
    void setBorderPaintedRoundTrips() {
        JToolBar tb = new JToolBar();
        tb.setBorderPainted(false);
        assertFalse(tb.isBorderPainted());
        tb.setBorderPainted(true);
        assertTrue(tb.isBorderPainted());
        assertNoWarns(capturedWarns);
    }

    // --- Margin forwarding -------------------------------------------------

    @Test
    @DisplayName("setMargin forwards to peer CSS padding")
    void setMarginForwardsToPeerCssPadding() {
        JToolBar tb = new JToolBar();
        tb.setMargin(new Insets(2, 4, 2, 4));
        assertEquals(new Insets(2, 4, 2, 4), tb.getMargin());
        assertEquals("2px 4px 2px 4px", peerOf(tb).getElement().getStyle().get("padding"));
    }

    @Test
    @DisplayName("setMargin null clears the peer's padding")
    void setMarginNullClearsThePeerPadding() {
        JToolBar tb = new JToolBar();
        tb.setMargin(new Insets(2, 4, 2, 4));
        tb.setMargin(null);
        String padding = peerOf(tb).getElement().getStyle().get("padding");
        assertTrue(padding == null || padding.isEmpty());
    }

    // --- addSeparator forwarding -------------------------------------------

    @Test
    @DisplayName("addSeparator adds a JToolBar.Separator child, rendered as a bar across the row")
    void addSeparatorAddsASeparatorChild() {
        JToolBar tb = new JToolBar();
        tb.addSeparator();
        JToolBar.Separator sep = assertInstanceOf(JToolBar.Separator.class, tb.getComponent(0));
        assertEquals(1, tb.getComponentCount());
        assertSame(sep.getPeer(), peerOf(tb).getChildren().findFirst().orElseThrow());
        assertEquals("1px", sep.getPeer().getElement().getStyle().get("min-width"));

        // The toolbar turning vertical turns the bar with it.
        tb.setOrientation(SwingConstants.VERTICAL);
        assertEquals("1px", sep.getPeer().getElement().getStyle().get("min-height"));
        assertNoWarns(capturedWarns);
    }

    @Test
    @DisplayName("addSeparator with Dimension stores the size on the separator, WARN-free")
    void addSeparatorWithDimensionStoresTheSize() {
        JToolBar tb = new JToolBar();
        tb.addSeparator(new Dimension(20, 10));
        JToolBar.Separator sep = (JToolBar.Separator) tb.getComponent(0);
        assertEquals(new Dimension(20, 10), sep.getSeparatorSize());
        assertEquals(new Dimension(20, 10), sep.getPreferredSize());
        // R_layouts_close_enough accept-drop of the rendering, not WARN.
        assertNoWarns(capturedWarns,
                "addSeparator(Dimension) is R_layouts_close_enough accept-drop: " + capturedWarns);
    }

    // --- add(Action) convenience -------------------------------------------

    @Test
    @DisplayName("add(Action) wraps in JButton, sets action, adds to toolbar")
    void addActionWrapsInJButton() {
        JToolBar tb = new JToolBar();
        AbstractAction action = new AbstractAction("Save") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        JButton button = tb.add(action);
        assertNotNull(button);
        assertSame(action, button.getAction());
        // JButton landed as a Container child.
        assertEquals(1, tb.getComponentCount());
        assertSame(button, tb.getComponent(0));
    }

    @Test
    @DisplayName("createActionComponent hides an icon Action's text and takes createActionChangeListener's listener")
    void createActionComponentIsTheJdks() {
        List<JButton> asked = new ArrayList<>();
        java.beans.PropertyChangeListener custom = e -> { };
        JToolBar tb = new JToolBar() {
            @Override
            protected java.beans.PropertyChangeListener createActionChangeListener(JButton b) {
                asked.add(b);
                return custom;
            }
        };
        AbstractAction iconAction = new AbstractAction("Save") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        iconAction.putValue(javax.swing.Action.SMALL_ICON, new Object());
        JButton b = tb.createActionComponent(iconAction);
        assertTrue(b.getHideActionText());
        assertEquals(SwingConstants.CENTER, b.getHorizontalTextPosition());
        assertEquals(SwingConstants.BOTTOM, b.getVerticalTextPosition());

        AbstractAction textAction = new AbstractAction("Open") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        JButton added = tb.add(textAction);
        assertFalse(added.getHideActionText());
        assertSame(added, assertSingle(asked));
        assertTrue(java.util.Arrays.asList(textAction.getPropertyChangeListeners()).contains(custom));
    }

    // --- getComponentIndex / getComponentAtIndex ---------------------------

    @Test
    @DisplayName("getComponentIndex returns position of direct child or -1")
    void getComponentIndexReturnsPositionOrMinusOne() {
        JToolBar tb = new JToolBar();
        JButton b1 = new JButton("a");
        JButton b2 = new JButton("b");
        tb.add(b1);
        tb.add(b2);
        assertEquals(0, tb.getComponentIndex(b1));
        assertEquals(1, tb.getComponentIndex(b2));
        assertEquals(-1, tb.getComponentIndex(new JButton("orphan")));
    }

    @Test
    @DisplayName("getComponentAtIndex mirrors getComponent")
    void getComponentAtIndexMirrorsGetComponent() {
        JToolBar tb = new JToolBar();
        JButton b = new JButton("hi");
        tb.add(b);
        assertSame(b, tb.getComponentAtIndex(0));
    }

    @Test
    @DisplayName("getComponentAtIndex answers null out of bounds, as the JDK's does")
    void getComponentAtIndexOutOfBoundsIsNull() {
        JToolBar tb = new JToolBar();
        assertNull(tb.getComponentAtIndex(0));
        assertNull(tb.getComponentAtIndex(-1));
    }

    // --- The JDK's own sequence ----------------------------------------------

    @Test
    @DisplayName("margin, separators and orientation match a JDK 25 run")
    void toolBarMatchesTheJdk() {
        // Measured with javax.swing.JToolBar on JDK 25, headless, the default L&F.
        List<String> log = new ArrayList<>();
        JToolBar tb = new JToolBar();
        log.add("name=" + tb.getName() + " margin=" + tb.getMargin()
                + " fresh=" + (tb.getMargin() != tb.getMargin()));
        tb.addPropertyChangeListener(e -> log.add("tb " + e.getPropertyName() + " "
                + e.getOldValue() + " -> " + e.getNewValue()));
        tb.setMargin(null);
        Insets m = new Insets(1, 2, 3, 4);
        tb.setMargin(m);
        log.add("same=" + (tb.getMargin() == m));
        tb.setMargin(new Insets(1, 2, 3, 4));
        tb.setMargin(null);

        JButton b = new JButton("b");
        log.add("defaultCapable=" + b.isDefaultCapable());
        tb.add(b);
        log.add("defaultCapable=" + b.isDefaultCapable());
        tb.addSeparator();
        tb.addSeparator(new Dimension(20, 10));
        log.add("count=" + tb.getComponentCount() + " index=" + tb.getComponentIndex(tb.getComponent(1)));
        for (vaadinx.awt.Component c : tb.getComponents()) {
            if (c instanceof JToolBar.Separator s) {
                s.addPropertyChangeListener("orientation", e -> log.add("sep orientation "
                        + e.getOldValue() + " -> " + e.getNewValue()));
                log.add("sep ui=" + s.getUIClassID() + " orient=" + s.getOrientation()
                        + " size=" + s.getSeparatorSize() + " focusable=" + s.isFocusable());
            }
        }
        tb.setOrientation(SwingConstants.VERTICAL);
        for (vaadinx.awt.Component c : tb.getComponents()) {
            if (c instanceof JToolBar.Separator s) {
                log.add("sep orient=" + s.getOrientation() + " size=" + s.getSeparatorSize());
            }
        }
        tb.setOrientation(SwingConstants.VERTICAL);
        tb.addSeparator();
        log.add("last orient=" + ((JSeparator) tb.getComponent(3)).getOrientation());

        assertEquals(List.of(
                "name=null margin=java.awt.Insets[top=0,left=0,bottom=0,right=0] fresh=true",
                "tb margin null -> null",
                "tb margin null -> java.awt.Insets[top=1,left=2,bottom=3,right=4]",
                "same=true",
                "tb margin java.awt.Insets[top=1,left=2,bottom=3,right=4] -> null",
                "defaultCapable=true",
                "defaultCapable=false",
                "count=3 index=1",
                "sep ui=ToolBarSeparatorUI orient=1 size=java.awt.Dimension[width=10,height=10] focusable=false",
                "sep ui=ToolBarSeparatorUI orient=1 size=java.awt.Dimension[width=20,height=10] focusable=false",
                "sep orientation 1 -> 0",
                "sep orientation 1 -> 0",
                "tb orientation 0 -> 1",
                "sep orient=0 size=java.awt.Dimension[width=10,height=10]",
                "sep orient=0 size=java.awt.Dimension[width=10,height=20]",
                "last orient=0"), log);
        assertThrows(IllegalArgumentException.class, () -> tb.setOrientation(99));
    }

    @Test
    @DisplayName("a lone Separator is horizontal with the L&F's 10x10 size, which a null never resets")
    void aLoneSeparatorHasTheLookAndFeelSize() {
        // The desktop L&F's default loses its UIResource marker on the way in, so
        // it is a plain Dimension and nothing tells it apart from an app's size.
        JToolBar.Separator s = new JToolBar.Separator();
        assertEquals(SwingConstants.HORIZONTAL, s.getOrientation());
        assertSame(Dimension.class, s.getSeparatorSize().getClass());
        assertEquals(new Dimension(10, 10), s.getSeparatorSize());

        Dimension mine = new Dimension(4, 4);
        s.setSeparatorSize(mine);
        s.setSeparatorSize(null);
        assertSame(mine, s.getSeparatorSize());
    }

    // --- L&F surface --------------------------------------------------------

    @Test
    @DisplayName("getUIClassID returns ToolBarUI")
    void getUiClassIdReturnsToolBarUi() {
        assertEquals("ToolBarUI", new JToolBar().getUIClassID());
    }

    @Test
    @DisplayName("getUI WARNs (no UI delegate)")
    void getUiWarns() {
        new JToolBar().getUI();
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("getUI")));
    }

    @Test
    @DisplayName("setUI WARNs (L&F surgery not modelled)")
    void setUiWarns() {
        new JToolBar().setUI(null);
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("setUI")));
    }

    @Test
    @DisplayName("setRollover stores under the client-property key and the PCE is putClientProperty's")
    void setRolloverStoresUnderTheClientPropertyKey() {
        // JToolBar.setRollover is one line — putClientProperty(
        // "JToolBar.isRollover", Boolean) — and isRollover() reads it back.
        // So the PCE is putClientProperty's, which is why the first old value
        // is null rather than Boolean.FALSE, and why both values are boxed
        // Booleans (D_property_fanout_audit, R_no_vaadin_in_api limb 2).
        JToolBar tb = new JToolBar();
        List<PropertyChangeEvent> events = new ArrayList<>();
        tb.addPropertyChangeListener("JToolBar.isRollover", events::add);

        tb.setRollover(true);
        assertEquals(Boolean.TRUE, tb.getClientProperty("JToolBar.isRollover"));
        assertEquals(1, events.size());
        assertEquals(null, events.get(0).getOldValue());
        assertEquals(Boolean.TRUE, events.get(0).getNewValue());

        tb.setRollover(false);
        assertEquals(2, events.size());
        assertEquals(Boolean.TRUE, events.get(1).getOldValue());
        assertEquals(Boolean.FALSE, events.get(1).getNewValue());
        assertFalse(tb.isRollover());
    }

    @Test
    @DisplayName("paramString ends with the JDK's four fields, in its order")
    void paramStringEndsWithTheJdksFields() {
        JToolBar tb = new JToolBar(SwingConstants.VERTICAL);
        tb.setFloatable(false);
        tb.setBorderPainted(false);
        tb.setRollover(true);
        tb.setMargin(new Insets(5, 6, 7, 8));
        assertTrue(tb.paramString().endsWith(",floatable=false,margin=java.awt.Insets[top=5,left=6,bottom=7,right=8]"
                + ",orientation=VERTICAL,paintBorder=false"), tb.paramString());
        tb.setMargin(null);
        assertTrue(tb.paramString().endsWith(",floatable=false,margin=,orientation=VERTICAL,paintBorder=false"),
                tb.paramString());
    }
}
