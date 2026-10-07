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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.html.Image;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJLabel;
import vaadinx.AbstractKaribuTest;

import javax.swing.SwingConstants;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static vaadinx.TestAssertions.assertSingle;

class JLabelTest extends AbstractKaribuTest {

    /** A minimal non-ImageIcon Icon — enough to prove the field round-trips. */
    private static Icon plainIcon() {
        return new Icon() {
            @Override
            public void paintIcon(vaadinx.awt.Component c, Graphics g, int x, int y) {
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }

    private static SJLabel peerOf(JLabel l) {
        return (SJLabel) l.getPeer();
    }

    @Test
    @DisplayName("no-arg ctor seeds empty text and LEADING alignment")
    void noArgCtorSeedsEmptyTextAndLeading() {
        // Swing's JLabel() → init("", null, LEADING). Empty-string, not
        // null, so getText never returns null on a default-constructed
        // label.
        JLabel l = new JLabel();
        assertEquals("", l.getText());
        assertEquals(SwingConstants.LEADING, l.getHorizontalAlignment());
    }

    @Test
    @DisplayName("icon-only ctor defaults alignment to CENTER")
    void iconOnlyCtorCentres() {
        // JLabel(Icon) is documented to CENTER the icon, unlike text
        // labels which LEADING. Regression against a copy-paste of the
        // text-only ctor's LEADING default.
        JLabel l = new JLabel((Icon) null);
        assertEquals(SwingConstants.CENTER, l.getHorizontalAlignment());
    }

    @Test
    @DisplayName("string ctor seeds text")
    void stringCtorSeedsText() {
        JLabel l = new JLabel("hello");
        assertEquals("hello", l.getText());
    }

    @Test
    @DisplayName("peer is an SJLabel surrogate and setText updates it")
    void peerIsAnSJLabel() {
        // Peer is SJLabel (NativeLabel-based) rather than a bare Span,
        // so icon + text can coexist on a single label DOM.
        JLabel l = new JLabel("before");
        assertInstanceOf(SJLabel.class, l.getPeer());
        l.setText("after");
        assertEquals("after", peerOf(l).getText());
    }

    @Test
    @DisplayName("setText null stores null in field but peer sees empty string")
    void setTextNullKeepsNullOnTheEmulator() {
        // Null at the Swing layer round-trips on the emulator's own
        // shadow field so user code that null-checks text gets what it
        // stored; SJLabel's text-bearing inner Span sees "" since
        // Vaadin can't represent a null text node.
        JLabel l = new JLabel("set");
        l.setText(null);
        assertNull(l.getText());
        assertEquals("", peerOf(l).getText());
    }

    @Test
    @DisplayName("setText fires text PropertyChangeEvent with old and new")
    void setTextFiresPce() {
        JLabel l = new JLabel("old");
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("text", events::add);

        l.setText("new");

        PropertyChangeEvent pce = assertSingle(events);
        assertEquals("old", pce.getOldValue());
        assertEquals("new", pce.getNewValue());
    }

    @Test
    @DisplayName("setText no-op (equal value) fires no event")
    void setTextNoOpFiresNothing() {
        // Swing's property-change short-circuit — listeners should not
        // see spurious events for same-value writes.
        JLabel l = new JLabel("same");
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("text", events::add);

        l.setText("same");

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("setHorizontalAlignment throws on invalid value")
    void horizontalAlignmentRejectsVerticalKeys() {
        // D_never_fail_on_gaps: Swing throws IAE for unknown alignment keys; we match.
        // Valid horizontal keys: LEFT, CENTER, RIGHT, LEADING, TRAILING.
        JLabel l = new JLabel();
        assertThrows(IllegalArgumentException.class,
                () -> l.setHorizontalAlignment(SwingConstants.TOP));
    }

    @Test
    @DisplayName("setHorizontalAlignment accepts LEFT, CENTER, RIGHT, LEADING, TRAILING")
    void horizontalAlignmentAcceptsHorizontalKeys() {
        JLabel l = new JLabel();
        for (int key : List.of(SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.RIGHT,
                SwingConstants.LEADING, SwingConstants.TRAILING)) {
            l.setHorizontalAlignment(key);
            assertEquals(key, l.getHorizontalAlignment());
        }
    }

    @Test
    @DisplayName("setVerticalAlignment throws on invalid value")
    void verticalAlignmentRejectsHorizontalKeys() {
        // Valid vertical keys: TOP, CENTER, BOTTOM.
        JLabel l = new JLabel();
        assertThrows(IllegalArgumentException.class,
                () -> l.setVerticalAlignment(SwingConstants.LEADING));
    }

    @Test
    @DisplayName("setDisplayedMnemonicIndex -1 is always allowed")
    void mnemonicIndexMinusOneIsAlwaysAllowed() {
        // -1 means "no underline" — legal regardless of text state.
        new JLabel().setDisplayedMnemonicIndex(-1);
        new JLabel((String) null).setDisplayedMnemonicIndex(-1);
    }

    @Test
    @DisplayName("setDisplayedMnemonicIndex past text length throws")
    void mnemonicIndexPastTextLengthThrows() {
        // Swing validates against the current text length.
        JLabel l = new JLabel("abc");
        assertThrows(IllegalArgumentException.class, () -> l.setDisplayedMnemonicIndex(3));
    }

    @Test
    @DisplayName("setDisplayedMnemonicIndex on null text rejects everything except -1")
    void mnemonicIndexOnNullTextRejectsNonNegative() {
        JLabel l = new JLabel();
        l.setText(null);
        assertThrows(IllegalArgumentException.class, () -> l.setDisplayedMnemonicIndex(0));
    }

    @Test
    @DisplayName("setIcon round-trips and fires icon PropertyChangeEvent")
    void setIconRoundTripsAndFires() {
        JLabel l = new JLabel();
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("icon", events::add);

        Icon icon = plainIcon();
        l.setIcon(icon);

        assertSame(icon, l.getIcon());
        PropertyChangeEvent pce = assertSingle(events);
        assertNull(pce.getOldValue());
        assertSame(icon, pce.getNewValue());
    }

    @Test
    @DisplayName("setLabelFor round-trips")
    void setLabelForRoundTrips() {
        JLabel l = new JLabel("name:");
        JButton target = new JButton();
        l.setLabelFor(target);
        assertSame(target, l.getLabelFor());
    }

    @Test
    @DisplayName("getUIClassID is LabelUI for UIManager compatibility")
    void uiClassIdIsLabelUI() {
        assertEquals("LabelUI", new JLabel().getUIClassID());
    }

    @Test
    @DisplayName("defaults match Swing — text position TRAILING, vertical CENTER, iconTextGap 4")
    void defaultsMatchSwing() {
        JLabel l = new JLabel();
        assertEquals(SwingConstants.TRAILING, l.getHorizontalTextPosition());
        assertEquals(SwingConstants.CENTER, l.getVerticalTextPosition());
        assertEquals(SwingConstants.CENTER, l.getVerticalAlignment());
        assertEquals(4, l.getIconTextGap());
        assertEquals(-1, l.getDisplayedMnemonicIndex());
    }

    @Test
    @DisplayName("text positions reach the peer, whatever order they are set in")
    void textPositionsReachThePeer() {
        // The toolbar idiom: icon above, text centred below it.
        JLabel l = new JLabel("HOME");
        l.setHorizontalTextPosition(SwingConstants.CENTER);
        l.setVerticalTextPosition(SwingConstants.BOTTOM);
        assertEquals(SwingConstants.CENTER, peerOf(l).getHorizontalTextPosition());
        assertEquals(SwingConstants.BOTTOM, peerOf(l).getVerticalTextPosition());

        // Back side by side: the horizontal half survives the stacked shape.
        l.setHorizontalTextPosition(SwingConstants.LEADING);
        l.setVerticalTextPosition(SwingConstants.CENTER);
        assertEquals(SwingConstants.LEADING, peerOf(l).getHorizontalTextPosition());
        assertEquals(SwingConstants.CENTER, peerOf(l).getVerticalTextPosition());

        l.setVerticalTextPosition(SwingConstants.TOP);
        assertEquals(SwingConstants.TOP, peerOf(l).getVerticalTextPosition());
    }

    @Test
    @DisplayName("alignment and icon-text gap reach the peer")
    void alignmentAndGapReachThePeer() {
        JLabel l = new JLabel("Total:", SwingConstants.RIGHT);
        assertEquals(SwingConstants.RIGHT, peerOf(l).getHorizontalAlignment());

        l.setVerticalAlignment(SwingConstants.TOP);
        assertEquals(SwingConstants.TOP, peerOf(l).getVerticalAlignment());

        l.setIconTextGap(10);
        assertEquals(10, peerOf(l).getIconTextGap());
    }

    @Test
    @DisplayName("setText reaches the rendered SJLabel when hosted in a frame")
    void setTextReachesTheRenderedLabel() {
        // End-to-end: a JLabel inside a visible JFrame renders via its
        // SJLabel peer, and setText after show updates the rendered text.
        JLabel l = new JLabel("before");
        JFrame frame = new JFrame("label smoke");
        frame.add(l);
        frame.setVisible(true);

        assertEquals("before", LocatorJ._get(SJLabel.class).getText());

        l.setText("after");

        assertEquals("after", LocatorJ._get(SJLabel.class).getText());
    }

    @Test
    @DisplayName("setIcon ImageIcon installs Vaadin Image on the SJLabel peer per Path 1")
    void imageIconInstallsAVaadinImage() {
        // SD_sjlabel closes the JLabel-icon-deferral from D_icon_rendering: vaadinx.swing.
        // ImageIcon's raster reaches the rendered <label> as a Vaadin
        // Image child of the SJLabel peer.
        BufferedImage raster = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        JLabel l = new JLabel(new ImageIcon(raster), SwingConstants.CENTER);

        // Field round-trip preserved on the emulator side.
        assertNotNull(l.getIcon());
        assertSame(raster, ((ImageIcon) l.getIcon()).getImage());

        // Peer-side Vaadin Image installed.
        JFrame frame = new JFrame("icon smoke");
        frame.add(l);
        frame.setVisible(true);
        SJLabel peer = LocatorJ._get(SJLabel.class);
        assertNotNull(peer.getIcon());
        assertInstanceOf(Image.class, peer.getIcon());
    }

    @Test
    @DisplayName("setIcon null clears the SJLabel peer's icon slot")
    void setIconNullClearsThePeerSlot() {
        JLabel l = new JLabel(
                new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)),
                SwingConstants.CENTER);
        JFrame frame = new JFrame();
        frame.add(l);
        frame.setVisible(true);
        assertNotNull(peerOf(l).getIcon());

        l.setIcon(null);

        assertNull(l.getIcon());
        assertNull(peerOf(l).getIcon());
    }

    @Test
    @DisplayName("setText after setIcon keeps both rendered on the peer")
    void setTextAfterSetIconKeepsBoth() {
        // Regression for the "Element.setText clobbers children" hazard
        // that drove the SJLabel design — emulator JLabel.setText must
        // route through SJLabel's text-Span without removing the icon
        // child.
        JLabel l = new JLabel(
                new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)),
                SwingConstants.CENTER);
        JFrame frame = new JFrame();
        frame.add(l);
        frame.setVisible(true);

        l.setText("Caption");

        SJLabel peer = peerOf(l);
        assertEquals("Caption", peer.getText());
        assertNotNull(peer.getIcon(),
                "icon should survive a subsequent setText through the emulator layer");
    }
}
