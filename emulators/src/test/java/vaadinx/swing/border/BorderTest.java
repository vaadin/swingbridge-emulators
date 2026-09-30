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

package vaadinx.swing.border;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.JButton;
import vaadinx.swing.JPanel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Border CSS on the peer, insets propagation, and the
 * BorderFactory surface. Each concrete border's CSS output is asserted
 * against the peer element's inline-style map so regressions show up
 * immediately. Per-test borders install on a fresh JPanel so state
 * doesn't leak across assertions.
 */
class BorderTest extends AbstractKaribuTest {

    private static String style(JPanel p, String prop) {
        return p.getPeer().getElement().getStyle().get(prop);
    }

    // --- setBorder / getBorder / PCE ---

    @Test
    @DisplayName("setBorder stores the field and getBorder returns it")
    void setBorderStoresTheFieldAndGetBorderReturnsIt() {
        var p = new JPanel();
        assertNull(p.getBorder());
        var b = BorderFactory.createLineBorder(Color.BLACK);
        p.setBorder(b);
        assertSame(b, p.getBorder());
    }

    @Test
    @DisplayName("setBorder fires a border PropertyChangeEvent")
    void setBorderFiresABorderPropertyChangeEvent() {
        var p = new JPanel();
        List<java.beans.PropertyChangeEvent> events = new ArrayList<>();
        p.addPropertyChangeListener("border", it -> events.add(it));

        var b = BorderFactory.createLineBorder(Color.RED);
        p.setBorder(b);

        assertEquals(1, events.size());
        assertSame(b, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setBorder equal value is a no-op")
    void setBorderEqualValueIsANoOp() {
        // Same-instance identity is enough to dedup — no need to run
        // applyCss twice on an unchanged install.
        var p = new JPanel();
        var b = BorderFactory.createLineBorder(Color.BLACK);
        p.setBorder(b);
        List<java.beans.PropertyChangeEvent> events = new ArrayList<>();
        p.addPropertyChangeListener("border", it -> events.add(it));
        p.setBorder(b);  // same instance
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("setBorder null clears previously-written CSS")
    void setBorderNullClearsPreviouslyWrittenCss() {
        // Regression: installing LineBorder then null must strip the
        // border CSS so the old visual doesn't linger.
        var p = new JPanel();
        p.setBorder(BorderFactory.createLineBorder(Color.BLACK, 3));
        assertTrue(style(p, "border") != null);

        p.setBorder(null);
        assertNull(style(p, "border"));
    }

    @Test
    @DisplayName("setBorder replacement clears the previous border's CSS")
    void setBorderReplacementClearsThePreviousBordersCss() {
        // Regression: Line then Matte should end up with MatteBorder's
        // per-side keys and no stale 'border' shorthand. Matte ctor is
        // (top, left, bottom, right) — so left=2 in the per-side map.
        var p = new JPanel();
        p.setBorder(BorderFactory.createLineBorder(Color.BLACK, 3));
        p.setBorder(BorderFactory.createMatteBorder(1, 2, 3, 4, Color.RED));

        assertNull(style(p, "border"));  // shorthand gone
        assertEquals("1px solid rgb(255,0,0)", style(p, "border-top"));
        assertEquals("2px solid rgb(255,0,0)", style(p, "border-left"));
    }

    @Test
    @DisplayName("custom Border impl doesn't render but round-trips")
    void customBorderImplDoesntRenderButRoundTrips() {
        // Users can extend Border directly; we can't render such borders
        // (no applyCss extension point), but the field stores cleanly so
        // getBorder reports what was installed + WARN surfaces in logs.
        var p = new JPanel();
        var custom = new Border() {
            @Override
            public void paintBorder(vaadinx.awt.Component c, java.awt.Graphics g, int x, int y, int w, int h) {}
            @Override
            public java.awt.Insets getBorderInsets(vaadinx.awt.Component c) { return new java.awt.Insets(1, 1, 1, 1); }
            @Override
            public boolean isBorderOpaque() { return true; }
        };
        p.setBorder(custom);
        assertSame(custom, p.getBorder());
    }

    // --- EmptyBorder ---

    @Test
    @DisplayName("EmptyBorder writes padding with top-right-bottom-left order")
    void emptyBorderWritesPaddingWithTopRightBottomLeftOrder() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createEmptyBorder(1, 2, 3, 4));
        // AWT Insets(top=1,left=2,bottom=3,right=4) → CSS padding "1 4 3 2".
        assertEquals("1px 4px 3px 2px", style(p, "padding"));
    }

    @Test
    @DisplayName("EmptyBorder insets round-trip through getBorderInsets")
    void emptyBorderInsetsRoundTripThroughGetBorderInsets() {
        var b = new EmptyBorder(1, 2, 3, 4);
        var ins = b.getBorderInsets(null);
        assertEquals(1, ins.top);
        assertEquals(2, ins.left);
        assertEquals(3, ins.bottom);
        assertEquals(4, ins.right);
    }

    @Test
    @DisplayName("BorderFactory_createEmptyBorder returns a shared zero-sized singleton")
    void borderFactoryCreateEmptyBorderReturnsASharedZeroSizedSingleton() {
        assertSame(BorderFactory.createEmptyBorder(), BorderFactory.createEmptyBorder());
    }

    // --- LineBorder ---

    @Test
    @DisplayName("LineBorder writes border shorthand with color and thickness")
    void lineBorderWritesBorderShorthandWithColorAndThickness() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createLineBorder(Color.BLUE, 3));
        assertEquals("3px solid rgb(0,0,255)", style(p, "border"));
        assertNull(style(p, "border-radius"));
    }

    @Test
    @DisplayName("LineBorder rounded adds border-radius")
    void lineBorderRoundedAddsBorderRadius() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2, true));
        assertEquals("2px solid rgb(0,0,0)", style(p, "border"));
        assertEquals("4px", style(p, "border-radius"));  // 2 * thickness
    }

    @Test
    @DisplayName("LineBorder null color uses currentcolor")
    void lineBorderNullColorUsesCurrentcolor() {
        var p = new JPanel();
        p.setBorder(new LineBorder(null, 1, false));
        assertEquals("1px solid currentcolor", style(p, "border"));
    }

    // --- MatteBorder ---

    @Test
    @DisplayName("MatteBorder writes per-side border rules")
    void matteBorderWritesPerSideBorderRules() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createMatteBorder(1, 2, 3, 4, Color.RED));
        assertEquals("1px solid rgb(255,0,0)", style(p, "border-top"));
        assertEquals("4px solid rgb(255,0,0)", style(p, "border-right"));
        assertEquals("3px solid rgb(255,0,0)", style(p, "border-bottom"));
        assertEquals("2px solid rgb(255,0,0)", style(p, "border-left"));
    }

    @Test
    @DisplayName("MatteBorder skips sides with zero thickness")
    void matteBorderSkipsSidesWithZeroThickness() {
        // Only top edge — common "divider above" idiom.
        var p = new JPanel();
        p.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, Color.GRAY));
        assertEquals("2px solid rgb(128,128,128)", style(p, "border-top"));
        assertNull(style(p, "border-right"));
        assertNull(style(p, "border-bottom"));
        assertNull(style(p, "border-left"));
    }

    // --- CompoundBorder ---

    @Test
    @DisplayName("CompoundBorder stacks LineBorder + EmptyBorder as border + padding")
    void compoundBorderStacksLineBorderPlusEmptyBorderAsBorderPlusPadding() {
        // Classic "outlined box with padding" idiom.
        var p = new JPanel();
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        assertEquals("1px solid rgb(0,0,0)", style(p, "border"));
        assertEquals("10px 10px 10px 10px", style(p, "padding"));
    }

    @Test
    @DisplayName("CompoundBorder cumulative padding when outer is EmptyBorder")
    void compoundBorderCumulativePaddingWhenOuterIsEmptyBorder() {
        // Outer EmptyBorder(2,2,2,2) + inner EmptyBorder(3,3,3,3) →
        // total padding "5px 5px 5px 5px" so layouts see the right
        // total insets.
        var p = new JPanel();
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 2, 2, 2),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)
        ));
        assertEquals("5px 5px 5px 5px", style(p, "padding"));
    }

    @Test
    @DisplayName("CompoundBorder insets sum across both sides")
    void compoundBorderInsetsSumAcrossBothSides() {
        var b = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(1, 2, 3, 4),
                BorderFactory.createEmptyBorder(5, 6, 7, 8)
        );
        var ins = b.getBorderInsets(null);
        assertEquals(6, ins.top);
        assertEquals(8, ins.left);
        assertEquals(10, ins.bottom);
        assertEquals(12, ins.right);
    }

    // --- TitledBorder ---

    @Test
    @DisplayName("TitledBorder sets data attribute (rule auto-injected)")
    void titledBorderSetsDataAttributeRuleAutoInjected() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder("Registration"));
        // Unified attribute name across both
        // layers, and the ::before pseudo-element rule is auto-injected
        // once per UI by com.vaadin.swingbridge.surrogates.util.BorderCss.ensureTitledBorderStyleInjected.
        assertEquals("Registration", p.getPeer().getElement().getAttribute("data-emul-border-title"));
        // Default 1px solid line gives the title something to sit on
        // even when no inner border is supplied.
        assertEquals("1px solid currentcolor", style(p, "border"));
    }

    @Test
    @DisplayName("TitledBorder applies the inside border's CSS")
    void titledBorderAppliesTheInsideBordersCss() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                "Outer"
        ));
        assertEquals("1px solid rgb(0,0,0)", style(p, "border"));
        assertEquals("Outer", p.getPeer().getElement().getAttribute("data-emul-border-title"));
    }

    @Test
    @DisplayName("TitledBorder rejects invalid position")
    void titledBorderRejectsInvalidPosition() {
        assertThrows(IllegalArgumentException.class,
                () -> new TitledBorder(null, "x", TitledBorder.DEFAULT_JUSTIFICATION, 99));
    }

    // --- BevelBorder ---

    @Test
    @DisplayName("BevelBorder RAISED uses CSS outset")
    void bevelBorderRaisedUsesCssOutset() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createRaisedBevelBorder());
        var b = style(p, "border");
        assertTrue(b != null && b.contains("outset") && b.startsWith("2px "), b);
    }

    @Test
    @DisplayName("BevelBorder LOWERED uses CSS inset")
    void bevelBorderLoweredUsesCssInset() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createLoweredBevelBorder());
        var b = style(p, "border");
        assertTrue(b != null && b.contains("inset"), b);
    }

    // --- EtchedBorder ---

    @Test
    @DisplayName("EtchedBorder RAISED uses CSS ridge")
    void etchedBorderRaisedUsesCssRidge() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
        var b = style(p, "border");
        assertTrue(b != null && b.contains("ridge"), b);
    }

    @Test
    @DisplayName("EtchedBorder LOWERED uses CSS groove")
    void etchedBorderLoweredUsesCssGroove() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createEtchedBorder());
        var b = style(p, "border");
        assertTrue(b != null && b.contains("groove"), b);
    }

    // --- SoftBevelBorder ---

    @Test
    @DisplayName("SoftBevelBorder adds border-radius on top of BevelBorder CSS")
    void softBevelBorderAddsBorderRadiusOnTopOfBevelBorderCss() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createRaisedSoftBevelBorder());
        var border = style(p, "border");
        assertTrue((border != null ? border : "").contains("outset"));
        assertEquals("3px", style(p, "border-radius"));
    }

    // --- JComponent.getInsets reads from border ---

    @Test
    @DisplayName("getInsets reads from the installed border")
    void getInsetsReadsFromTheInstalledBorder() {
        var p = new JPanel();
        p.setBorder(BorderFactory.createEmptyBorder(5, 6, 7, 8));
        var ins = p.getInsets();
        assertEquals(5, ins.top);
        assertEquals(6, ins.left);
        assertEquals(7, ins.bottom);
        assertEquals(8, ins.right);
    }

    // --- Border works on leaf components (JButton) ---

    @Test
    @DisplayName("setBorder on JButton writes CSS to the Button peer")
    void setBorderOnJButtonWritesCssToTheButtonPeer() {
        // Regression: borders should work on any JComponent, not just
        // container-shaped ones. JButton's peer is a Vaadin Button, a
        // non-Div element — border CSS applies to it directly regardless.
        var b = new JButton("Go");
        b.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
        assertEquals("2px solid rgb(255,0,0)", b.getPeer().getElement().getStyle().get("border"));
    }
}
