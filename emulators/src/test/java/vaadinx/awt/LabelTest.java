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
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SLabel;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the {@code vaadinx.awt.Label} emulator — AWT 1.0 static text,
 * not {@code vaadinx.swing.JLabel}. Beyond the JDK API it re-checks what
 * {@code ButtonTest} established for the first AWT leaf: that a plain AWT widget
 * drops into a Swing container and that {@code vaadinx.awt.Component} carries a
 * leaf with no JComponent in its chain. See D_awt_lane, D_awt_label.
 */
class LabelTest extends AbstractKaribuTest {

    /** The rendered Vaadin span of the single label under test. */
    private static Span peer() {
        return LocatorJ._get(Span.class);
    }

    // --- Ctors / text -------------------------------------------------

    @Test
    @DisplayName("no-arg ctor matches AWT's empty-text LEFT-aligned default")
    void noArgCtorMatchesAwtsEmptyTextLeftAlignedDefault() {
        // JDK: public Label() { this("", LEFT); } — empty, not null.
        Label l = new Label();
        assertEquals("", l.getText());
        assertEquals(Label.LEFT, l.getAlignment());
    }

    @Test
    @DisplayName("string ctor seeds the text")
    void stringCtorSeedsTheText() {
        assertEquals("Total", new Label("Total").getText());
    }

    @Test
    @DisplayName("two-arg ctor seeds text and alignment")
    void twoArgCtorSeedsTextAndAlignment() {
        Label l = new Label("Total", Label.RIGHT);
        assertEquals("Total", l.getText());
        assertEquals(Label.RIGHT, l.getAlignment());
    }

    @Test
    @DisplayName("null text round-trips as null through the emulator's field")
    void nullTextRoundTripsAsNullThroughTheEmulatorsField() {
        // The SLabel peer reads back "" because a Vaadin element's text content
        // can't hold null; migrated code null-checking getText needs the field.
        Label l = new Label(null);
        assertNull(l.getText());
        l.setText("x");
        l.setText(null);
        assertNull(l.getText());
    }

    @Test
    @DisplayName("setText reaches the rendered span")
    void setTextReachesTheRenderedSpan() {
        Label l = new Label("before");
        JFrame frame = new JFrame();
        frame.add(l);
        frame.setVisible(true);

        l.setText("after");

        assertEquals("after", peer().getText());
    }

    // --- alignment ----------------------------------------------------

    @Test
    @DisplayName("setAlignment stores the field and pushes the peer's CSS")
    void setAlignmentStoresTheFieldAndPushesThePeersCss() {
        Label l = new Label("x");
        l.setAlignment(Label.CENTER);
        assertEquals(Label.CENTER, l.getAlignment());
        assertEquals("center", ((SLabel) l.getPeer()).getElement().getStyle().get("text-align"));
        l.setAlignment(Label.RIGHT);
        assertEquals(Label.RIGHT, l.getAlignment());
        assertEquals("right", ((SLabel) l.getPeer()).getElement().getStyle().get("text-align"));
    }

    @Test
    @DisplayName("the peer renders what the ctor stored, including through a setAlignment override")
    void thePeerRendersWhatTheCtorStored() {
        SLabel peer = (SLabel) new Label("Total", Label.RIGHT).getPeer();
        assertEquals("Total", peer.getText());
        assertEquals("right", peer.getElement().getStyle().get("text-align"));
        // An override that never reaches super leaves the JDK's LEFT default in place.
        Label sub = new Label("Total", Label.RIGHT) {
            @Override public void setAlignment(int alignment) { }
        };
        assertEquals(Label.LEFT, sub.getAlignment());
        assertEquals("Total", ((SLabel) sub.getPeer()).getText());
    }

    /**
     * Replays a script measured on JDK 25 ({@code java.awt.Label} under Xvfb, since its
     * constructor refuses a headless JVM) and asserts its output verbatim: every
     * constructor stores the text directly and routes the alignment through the public
     * {@code setAlignment}, an invalid alignment leaves the field alone, and
     * {@code paramString} reads the fields rather than the getters.
     */
    @Test
    @DisplayName("construction and alignment replay the JDK's measured script")
    void callGraphMatchesTheJdk() {
        List<String> log = new ArrayList<>();
        class L extends Label {
            L() { super(); }
            L(String t) { super(t); }
            L(String t, int a) { super(t, a); }
            @Override public void setAlignment(int a) { log.add("setAlignment(" + a + ")"); super.setAlignment(a); }
            @Override public void setText(String t) { log.add("setText(" + t + ")"); super.setText(t); }
            @Override public int getAlignment() { log.add("getAlignment"); return super.getAlignment(); }
            @Override public String getText() { log.add("getText"); return super.getText(); }
        }
        List<String> out = new ArrayList<>();
        L l = new L();
        out.add("L(): " + log); log.clear();
        out.add("  text=" + l.getText() + " align=" + l.getAlignment()); log.clear();
        new L("x");
        out.add("L(x): " + log); log.clear();
        new L("x", Label.RIGHT);
        out.add("L(x,RIGHT): " + log); log.clear();
        l = new L(null, Label.CENTER);
        out.add("L(null,CENTER): " + log + " text=" + l.getText()); log.clear();
        try {
            new L("x", 7);
        } catch (IllegalArgumentException e) {
            out.add("L(x,7): " + log + " " + e.getMessage());
        }
        log.clear();
        L x = new L("x");
        log.clear();
        try {
            x.setAlignment(-1);
        } catch (IllegalArgumentException e) {
            log.clear();
            out.add("setAlignment(-1): " + e.getMessage() + " align=" + x.getAlignment());
        }
        log.clear();
        x.setAlignment(Label.CENTER);
        out.add("setAlignment(CENTER): " + log); log.clear();
        String ps = x.paramString();
        out.add("ps: " + ps + " " + log); log.clear();
        x.setText("x");
        out.add("setText same: " + log); log.clear();
        x.setText(null);
        out.add("setText null: " + log + " text=" + x.getText()); log.clear();

        assertEquals(List.of(
                "L(): [setAlignment(0)]",
                "  text= align=0",
                "L(x): [setAlignment(0)]",
                "L(x,RIGHT): [setAlignment(2)]",
                "L(null,CENTER): [setAlignment(1)] text=null",
                "L(x,7): [setAlignment(7)] improper alignment: 7",
                "setAlignment(-1): improper alignment: -1 align=0",
                "setAlignment(CENTER): [setAlignment(1)]",
                // The JDK prefixes Component's name,bounds,validity; ours is empty.
                "ps: ,align=center,text=x []",
                "setText same: [setText(x)]",
                "setText null: [setText(null)] text=null"), out);
    }

    @Test
    @DisplayName("bad alignment throws as in AWT, from both ctor and setter")
    void badAlignmentThrowsAsInAwtFromBothCtorAndSetter() {
        assertThrows(IllegalArgumentException.class, () -> new Label("x", 42));
        Label l = new Label("x", Label.CENTER);
        assertThrows(IllegalArgumentException.class, () -> l.setAlignment(42));
        assertEquals(Label.CENTER, l.getAlignment());
    }

    @Test
    @DisplayName("alignment constants are AWT's, not SwingConstants'")
    void alignmentConstantsAreAwtsNotSwingConstants() {
        assertEquals(0, Label.LEFT);
        assertEquals(1, Label.CENTER);
        assertEquals(2, Label.RIGHT);
        // SwingConstants.RIGHT is 4 — outside AWT's set, so it throws
        // rather than silently right-aligning.
        Label l = new Label("x");
        assertThrows(IllegalArgumentException.class,
                () -> l.setAlignment(javax.swing.SwingConstants.RIGHT));
    }

    // --- Peer / R_leaf_peer_lockdown ---------------------------------------------------

    @Test
    @DisplayName("every instance peers over an SLabel per R_leaf_peer_lockdown lock-down")
    void everyInstancePeersOverAnSLabelPerRLeafPeerLockdownLockDown() {
        assertInstanceOf(SLabel.class, new Label("x").getPeer());
        // Including a user-code subclass: with no protected (peer) ctor
        // there is no seam through which the peer could be swapped.
        Label sub = new Label("x") {
        };
        assertInstanceOf(SLabel.class, sub.getPeer());
    }

    // --- The AWT-leaf-in-a-Swing-tree case ----------------------------

    @Test
    @DisplayName("an AWT Label drops into a Swing container and renders")
    void anAwtLabelDropsIntoASwingContainerAndRenders() {
        Label l = new Label("Legacy caption");
        JPanel panel = new JPanel();
        panel.add(l);
        JFrame frame = new JFrame();
        frame.add(panel);
        frame.setVisible(true);

        assertSame(panel, l.getParent());
        assertEquals("Legacy caption", peer().getText());
    }

    @Test
    @DisplayName("inherited Component surface works without JComponent in the chain")
    void inheritedComponentSurfaceWorksWithoutJComponentInTheChain() {
        Label l = new Label("x");
        l.setName("total-label");
        assertEquals("total-label", l.getName());
        l.setEnabled(false);
        assertFalse(l.isEnabled());
        l.setVisible(false);
        assertFalse(l.isVisible());
    }

    // --- toString shape / stubs ---------------------------------------

    @Test
    @DisplayName("paramString carries the JDK's align and text tail")
    void paramStringCarriesTheJdksAlignAndTextTail() {
        assertEquals(",align=right,text=Total", new Label("Total", Label.RIGHT).paramString());
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        List<String> warned = new ArrayList<>();
        Consumer<String> prior = EHelper.warnHook;
        EHelper.warnHook = warned::add;
        try {
            assertNull(new Label("x").getAccessibleContext());
        } finally {
            EHelper.warnHook = prior;
        }
        assertTrue(warned.stream().anyMatch(it -> it.contains("Label") && it.contains("getAccessibleContext")));
    }
}
