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

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code W_frameinit_shape} — the last front of the JDK call-graph audit;
 * {@code D_frameinit_shape} in {@code emulators/decisions.md}.
 *
 * <p>The three {@code *Init} hooks are constructors' call graphs, so nothing
 * these tests pin is observable from a value: the whole claim is <em>which
 * overridable methods a bare subclass sees called, and in what order</em>. A
 * migrator's {@code setLocale} / {@code setBackground} override compiles and
 * looks wired whether or not the constructor reaches it (R_no_vaadin_in_api
 * limb 2), which is why the negatives here matter as much as the positives —
 * JWindow must reach <em>neither</em> extra hook, because JDK's
 * {@code windowInit} doesn't.
 */
public class WindowFrameInitTest extends AbstractKaribuTest {

    /** Records every ctor-reachable hook the JDK's *Init calls, in order. */
    private final List<String> calls = new ArrayList<>();

    /**
     * Compact, stable rendering of a recorded colour. {@code Color.toString()}
     * would spell out the concrete class, which makes these order assertions
     * churn whenever the L&amp;F table's value type changes without the call
     * graph — the thing they actually pin — moving at all.
     */
    private static String tag(Color c) {
        return c == null ? "null" : "#" + String.format("%06x", c.getRGB() & 0xFFFFFF);
    }

    private class SpyFrame extends JFrame {
        @Override public void setLocale(Locale l) { calls.add("setLocale:" + l); super.setLocale(l); }
        @Override public void setBackground(Color c) { calls.add("setBackground:" + tag(c)); super.setBackground(c); }
        @Override public JRootPane createRootPane() { calls.add("createRootPane"); return super.createRootPane(); }
    }

    private class SpyDialog extends JDialog {
        @Override public void setLocale(Locale l) { calls.add("setLocale:" + l); super.setLocale(l); }
        @Override public void setBackground(Color c) { calls.add("setBackground:" + tag(c)); super.setBackground(c); }
    }

    private class SpyWindow extends JWindow {
        @Override public void setLocale(Locale l) { calls.add("setLocale:" + l); super.setLocale(l); }
        @Override public void setBackground(Color c) { calls.add("setBackground:" + tag(c)); super.setBackground(c); }
    }

    // --- the call graph -----------------------------------------------

    @Test
    public void frameInitReachesSetLocaleThenRootPaneThenSetBackground() {
        // JDK order (JFrame.java:255): setLocale precedes setRootPane, and
        // setBackground follows it — a subclass override that reads
        // getRootPane() from setBackground must find it planted.
        UI.getCurrent().setLocale(Locale.FRENCH);
        new SpyFrame();
        assertEquals(
                List.of("setLocale:fr", "createRootPane", "setBackground:#eeeeee"),
                calls);
    }

    @Test
    public void dialogInitReachesBothHooks() {
        UI.getCurrent().setLocale(Locale.FRENCH);
        new SpyDialog();
        assertEquals(List.of("setLocale:fr", "setBackground:#eeeeee"), calls);
    }

    @Test
    public void windowInitReachesSetLocaleAndNotSetBackground() {
        // JWindow.windowInit (JWindow.java:260) is three lines where the other
        // two are five. Regularising it would fire a migrator's setBackground
        // override where the desktop never does.
        UI.getCurrent().setLocale(Locale.FRENCH);
        new SpyWindow();
        assertEquals(List.of("setLocale:fr"), calls);
    }

    // --- the locale that lands ----------------------------------------

    @Test
    public void windowsAreBornWithTheBrowserLocale() {
        UI.getCurrent().setLocale(Locale.JAPANESE);
        assertEquals(Locale.JAPANESE, JComponent.getDefaultLocale());
        assertEquals(Locale.JAPANESE, new JFrame().getLocale());
        assertEquals(Locale.JAPANESE, new JDialog().getLocale());
        assertEquals(Locale.JAPANESE, new JWindow().getLocale());
    }

    @Test
    public void theLocaleReachesTheDomAsLang() {
        UI.getCurrent().setLocale(Locale.forLanguageTag("cs-CZ"));
        JFrame f = new JFrame();
        assertEquals("cs-CZ", f.getPeer().getElement().getAttribute("lang"));
    }

    @Test
    public void anAwtWindowStillFallsBackToTheBrowserLocale() {
        // vaadinx.awt.Frame has no *Init hook, so no explicit locale is ever
        // stored and Window.getLocale's orphan-catch is what answers.
        UI.getCurrent().setLocale(Locale.ITALIAN);
        assertEquals(Locale.ITALIAN, new vaadinx.awt.Frame().getLocale());
    }

    // --- the two deliberate nothings ----------------------------------

    @Test
    public void theBackgroundThatLandsIsMetalsControlColour() {
        // Inverted deliberately by D_theme_is_lookandfeel, which overturns
        // D_frameinit_shape's controlColor()-returns-null: the colour table is
        // installed, so frameInit stores a real value. It arrives as a
        // ColorUIResource, which is what keeps the theme showing through — the
        // marker tells the renderer this is a default it may substitute, so
        // getBackground() answers Metal's grey while no CSS pins it.
        assertEquals(new java.awt.Color(238, 238, 238), UIManager.controlColor());
        assertTrue(UIManager.controlColor() instanceof javax.swing.plaf.UIResource);

        JFrame f = new JFrame();
        assertEquals(new java.awt.Color(238, 238, 238), f.getBackground());
        assertNull(f.getPeer().getElement().getStyle().get("background-color"));
    }

    @Test
    public void constructingAWindowWarnsAboutNothing() {
        // The gate the whole design of step 5 turns on: enableEvents and the
        // control-colour lookup both run, and neither reports a gap, so
        // Sampler's WarnInventoryTests stay at zero.
        List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            new JFrame();
            new JDialog();
            new JWindow();
        } finally {
            EHelper.warnHook = msg -> { };
        }
        assertNoWarns(warns);
    }

    @Test
    public void disableEventsStillWarns() {
        // The asymmetry: unconditional dispatch grants every enable request and
        // honours no disable one, so only the latter is a gap worth reporting.
        List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            new JFrame() {{ disableEvents(java.awt.AWTEvent.KEY_EVENT_MASK); }};
        } finally {
            EHelper.warnHook = msg -> { };
        }
        assertEquals(1, warns.size(), warns::toString);
        assertTrue(warns.get(0).contains("disableEvents"), warns.get(0));
    }
}
