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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Image;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JButtonTest extends AbstractKaribuTest {

    /** The button, hosted in a shown frame — the shape every end-to-end test below needs. */
    private static JButton shown(JButton jb) {
        JFrame frame = new JFrame();
        frame.add(jb);
        frame.setVisible(true);
        return jb;
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new JButton();
    }

    @Test
    @DisplayName("no-arg ctor leaves text null matching AWT")
    void noArgCtorLeavesTextNull() {
        // AWT's AbstractButton.getText returns null until setText runs —
        // we keep the same post-construction nullity so apps that
        // null-check text (rather than "".equals) see the expected value.
        assertNull(new JButton().getText());
    }

    @Test
    @DisplayName("string ctor seeds text and actionCommand falls back to it")
    void stringCtorSeedsTextAndCommand() {
        JButton jb = new JButton("OK");
        assertEquals("OK", jb.getText());
        // AWT: getActionCommand falls back to text when no explicit
        // actionCommand is installed — ActionEvents from a freshly-
        // constructed button carry a meaningful command out of the box.
        assertEquals("OK", jb.getActionCommand());
    }

    @Test
    @DisplayName("setActionCommand overrides the text fallback")
    void setActionCommandOverridesTheFallback() {
        JButton jb = new JButton("Save");
        jb.setActionCommand("save-file");
        assertEquals("save-file", jb.getActionCommand());
    }

    @Test
    @DisplayName("doClick fires ActionEvent with button as source and actionCommand")
    void doClickFiresActionEvent() {
        // doClick bypasses the peer — fires ActionEvent directly on
        // registered listeners. Source must be the JButton (not the
        // Vaadin peer), since migrated code casts getSource() to JButton.
        List<ActionEvent> events = new ArrayList<>();
        JButton jb = new JButton("Go");
        jb.addActionListener(events::add);

        jb.doClick();

        ActionEvent e = assertSingle(events);
        assertSame(jb, e.getSource());
        assertEquals("Go", e.getActionCommand());
        assertEquals(ActionEvent.ACTION_PERFORMED, e.getID());
    }

    @Test
    @DisplayName("doClick fires on every listener in Swing's LIFO order")
    void doClickFiresInLifoOrder() {
        // Swing's EventListenerList iterates last-added first — user code
        // that relies on this ordering (e.g. a late-registered gate that
        // swallows the event by flipping state) needs it preserved. Our
        // fireActionPerformed walks getListeners which reads the list
        // back-to-front, matching real Swing's AbstractButton.
        List<String> hits = new ArrayList<>();
        JButton jb = new JButton();
        jb.addActionListener(e -> hits.add("first"));
        jb.addActionListener(e -> hits.add("second"));

        jb.doClick();

        assertEquals(List.of("second", "first"), hits);
    }

    @Test
    @DisplayName("removeActionListener stops further events")
    void removeActionListenerStopsEvents() {
        List<ActionEvent> events = new ArrayList<>();
        JButton jb = new JButton();
        ActionListener l = events::add;
        jb.addActionListener(l);
        jb.removeActionListener(l);

        jb.doClick();

        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("null ActionListener is silently ignored on add and remove")
    void nullActionListenerIsIgnored() {
        // EventListenerList contract — matches AWT's own null-tolerance.
        JButton jb = new JButton();
        jb.addActionListener(null);
        jb.removeActionListener(null);
        assertEquals(0, jb.getActionListeners().length);
    }

    @Test
    @DisplayName("setText updates peer text and fires text PropertyChangeEvent")
    void setTextFiresPce() {
        // R_swing_is_truth mirror: Swing field → peer. Verify via PropertyChangeEvent
        // (observable without reaching for the peer) that the write
        // happens and the event carries the new value.
        JButton jb = new JButton("old");
        List<PropertyChangeEvent> events = new ArrayList<>();
        jb.addPropertyChangeListener("text", events::add);

        jb.setText("new");

        assertEquals("new", jb.getText());
        PropertyChangeEvent pce = assertSingle(events);
        assertEquals("old", pce.getOldValue());
        assertEquals("new", pce.getNewValue());
    }

    @Test
    @DisplayName("setText null stores null and does not crash the peer")
    void setTextNullStoresNull() {
        // AWT: setText(null) stores null, getText returns null. The peer
        // gets "" to avoid Vaadin HasText.setText's NPE — user code that
        // null-checks text still sees null on our side.
        JButton jb = new JButton("initial");
        jb.setText(null);
        assertNull(jb.getText());
    }

    @Test
    @DisplayName("deprecated label aliases delegate to text")
    void labelAliasesDelegateToText() {
        // setLabel/getLabel are AWT 1.0 legacy — route through setText/getText
        // so user overrides and the shared peer write path both fire.
        JButton jb = new JButton();
        jb.setLabel("Done");
        assertEquals("Done", jb.getText());
        assertEquals("Done", jb.getLabel());
    }

    @Test
    @DisplayName("user click on the peer fires ActionEvent to Swing listeners")
    void userClickFiresActionEvent() {
        // The core of the first-slice goal: a JButton attached to a
        // visible JFrame, clicked in the browser, drives a registered
        // Swing ActionListener. _click is the Karibu helper that asserts
        // the button is visible+enabled and then fires a from-client
        // click via our ClickNotifier wiring.
        JButton jb = shown(new JButton("Hello"));

        List<String> hits = new ArrayList<>();
        jb.addActionListener(e -> hits.add(e.getActionCommand()));

        LocatorJ._click(LocatorJ._get(Button.class));

        assertEquals(List.of("Hello"), hits);
    }

    @Test
    @DisplayName("user click fires ActionEvent whose source is the JButton emulator")
    void userClickSourceIsTheEmulator() {
        // Regression guard: if the click listener forwarded the Vaadin
        // ClickEvent verbatim, getSource() would return the Vaadin Button
        // and every `(JButton) e.getSource()` cast in migrated code would
        // ClassCastException. The emulator-as-source is load-bearing.
        JButton jb = shown(new JButton("Click me"));
        AtomicReference<Object> source = new AtomicReference<>();
        jb.addActionListener(e -> source.set(e.getSource()));

        LocatorJ._click(LocatorJ._get(Button.class));

        assertSame(jb, source.get());
    }

    @Test
    @DisplayName("setText while shown updates the rendered Vaadin Button")
    void setTextWhileShownUpdatesThePeer() {
        // End-to-end: the text mirror lands on the peer even after the
        // frame is visible — matches the first-slice goal of "button
        // whose caption changes when clicked".
        JButton jb = shown(new JButton("before"));

        jb.setText("after");

        assertEquals("after", LocatorJ._get(Button.class).getText());
    }

    @Test
    @DisplayName("button click self-updating caption end-to-end")
    void selfUpdatingCaptionEndToEnd() {
        // The first-slice goal verbatim: a JFrame containing a JButton
        // whose caption changes when clicked. If this passes, the core
        // round-trip (user click → ActionEvent → Swing handler → text
        // update → peer re-renders) works.
        JButton jb = new JButton("Press me");
        jb.addActionListener(e -> jb.setText("Pressed"));
        shown(jb);

        assertEquals("Press me", LocatorJ._get(Button.class).getText());
        LocatorJ._click(LocatorJ._get(Button.class));
        assertEquals("Pressed", LocatorJ._get(Button.class).getText());
        assertEquals("Pressed", jb.getText());
    }

    @Test
    @DisplayName("addActionListener stores on the shared EventListenerList")
    void listenersLiveOnTheSharedList() {
        // Regression: getActionListeners must reflect add/remove through
        // the shared EventListenerList — not null, not a separate list.
        JButton jb = new JButton();
        assertEquals(0, jb.getActionListeners().length);
        ActionListener l = e -> { /* noop */ };
        jb.addActionListener(l);
        assertSame(l, assertSingle(jb.getActionListeners()));
    }

    @Test
    @DisplayName("setIcon ImageIcon installs Vaadin Image on peer per Path 1")
    void imageIconInstallsAVaadinImage() {
        // AbstractButton.setIcon binds Vaadin-
        // first per SD_vaadin_first_binding. ImageIcon's raster encodes as PNG and lands as a
        // Vaadin <img> child of the Vaadin Button peer.
        JButton jb = new JButton();
        BufferedImage raster = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        jb.setIcon(new ImageIcon(raster));

        // Field round-trip preserved.
        assertNotNull(jb.getIcon());
        assertSame(raster, ((ImageIcon) jb.getIcon()).getImage());

        // Peer-side Image installed.
        shown(jb);
        Button peer = LocatorJ._get(Button.class);
        assertNotNull(peer.getIcon());
        assertInstanceOf(Image.class, peer.getIcon());
    }

    @Test
    @DisplayName("setIcon null clears the peer icon slot")
    void setIconNullClearsThePeerSlot() {
        JButton jb = new JButton();
        jb.setIcon(new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)));
        shown(jb);
        assertNotNull(LocatorJ._get(Button.class).getIcon());

        jb.setIcon(null);

        assertNull(jb.getIcon());
        assertNull(LocatorJ._get(Button.class).getIcon());
    }
}
