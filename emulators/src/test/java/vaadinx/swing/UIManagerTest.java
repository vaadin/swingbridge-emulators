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
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import javax.swing.plaf.UIResource;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UIManagerTest extends AbstractKaribuTest {

    /**
     * Runs {@code body} with the WARN hook capturing, restoring whatever hook was
     * installed before. Returns what was captured, so a test asserts on the
     * returned list rather than on a field — the save/restore/try-finally shape
     * repeated at seven call sites in this class.
     */
    private static List<String> capturingWarns(Runnable body) {
        List<String> warns = new ArrayList<>();
        Consumer<String> prev = EHelper.warnHook;
        EHelper.warnHook = warns::add;
        try {
            body.run();
        } finally {
            EHelper.warnHook = prev;
        }
        return warns;
    }

    // ------- Hand-picked OptionPane.* keys → VaadinIcon glyphs -------

    @Test
    @DisplayName("errorIcon key resolves to EXCLAMATION_CIRCLE glyph")
    void errorIconKeyResolves() {
        VaadinIconAdapter adapter = (VaadinIconAdapter) UIManager.getIcon("OptionPane.errorIcon");
        assertEquals(VaadinIcon.EXCLAMATION_CIRCLE, adapter.getVaadinIcon());
    }

    @Test
    @DisplayName("informationIcon key resolves to INFO_CIRCLE glyph")
    void informationIconKeyResolves() {
        VaadinIconAdapter adapter =
                (VaadinIconAdapter) UIManager.getIcon("OptionPane.informationIcon");
        assertEquals(VaadinIcon.INFO_CIRCLE, adapter.getVaadinIcon());
    }

    @Test
    @DisplayName("warningIcon key resolves to WARNING glyph")
    void warningIconKeyResolves() {
        VaadinIconAdapter adapter = (VaadinIconAdapter) UIManager.getIcon("OptionPane.warningIcon");
        assertEquals(VaadinIcon.WARNING, adapter.getVaadinIcon());
    }

    @Test
    @DisplayName("questionIcon key resolves to QUESTION_CIRCLE glyph")
    void questionIconKeyResolves() {
        VaadinIconAdapter adapter = (VaadinIconAdapter) UIManager.getIcon("OptionPane.questionIcon");
        assertEquals(VaadinIcon.QUESTION_CIRCLE, adapter.getVaadinIcon());
    }

    @Test
    @DisplayName("Locale-overload delegates to single-arg")
    void localeOverloadDelegates() {
        // Locale variant is a passthrough — same instance back.
        assertSame(UIManager.getIcon("OptionPane.errorIcon"),
                UIManager.getIcon("OptionPane.errorIcon", Locale.GERMAN));
    }

    // ------- Unknown key — null + WARN -------

    @Test
    @DisplayName("unknown key returns null and warns")
    void unknownKeyReturnsNullAndWarns() {
        List<String> warns = capturingWarns(
                () -> assertNull(UIManager.getIcon("Tree.someUnknownIconKey")));
        assertTrue(warns.stream().anyMatch(w -> w.contains("UIManager.getIcon")
                && w.contains("Tree.someUnknownIconKey")));
    }

    // ------- End-to-end: UIManager-sourced icon reaches a peer via
    // ------- EHelper.toVaadinIconComponent -------

    @Test
    @DisplayName("EHelper.toVaadinIconComponent renders VaadinIconAdapter as a Vaadin Icon")
    void toVaadinIconComponentRendersAnIcon() {
        com.vaadin.flow.component.Component rendered =
                EHelper.toVaadinIconComponent("UIManagerTest", UIManager.getIcon("OptionPane.errorIcon"));
        assertNotNull(rendered);
        assertInstanceOf(Icon.class, rendered,
                "expected com.vaadin.flow.component.icon.Icon, got " + rendered.getClass().getName());
    }

    @Test
    @DisplayName("EHelper.toVaadinIconComponent returns a fresh component per call")
    void toVaadinIconComponentIsFreshPerCall() {
        // Vaadin components can only sit at one place in the DOM, so each
        // call must produce a fresh instance.
        vaadinx.swing.Icon icon = UIManager.getIcon("OptionPane.warningIcon");
        com.vaadin.flow.component.Component a = EHelper.toVaadinIconComponent("UIManagerTest", icon);
        com.vaadin.flow.component.Component b = EHelper.toVaadinIconComponent("UIManagerTest", icon);
        assertNotNull(a);
        assertNotNull(b);
        assertNotSame(a, b);
    }

    @Test
    @DisplayName("JOptionPane setIcon with UIManager-sourced icon renders on the dialog")
    void userSuppliedIconReachesTheDialog() throws InterruptedException {
        // The integration scenario the public Path 2 surface unlocks:
        // pre-fetch by key, hand to a JOptionPane factory that takes an
        // explicit Icon, render through EHelper.toVaadinIconComponent.
        CountDownLatch finished = new CountDownLatch(1);
        vaadinx.swing.Icon errorIcon = UIManager.getIcon("OptionPane.errorIcon");
        EHelper.callSwing(() -> {
            JOptionPane.showMessageDialog(
                    null,
                    "Disk failure",
                    "Error",
                    JOptionPane.PLAIN_MESSAGE, // PLAIN suppresses internal glyph; user-supplied icon wins
                    errorIcon);
            finished.countDown();
        });
        // The error glyph reached the rendered dialog as a Vaadin Icon.
        // Vaadin Dialog ships a built-in close-button Icon, so filter by
        // glyph attribute to identify ours specifically. PLAIN_MESSAGE
        // suppresses the messageType-internal glyph so the only
        // exclamation-circle here is our user-supplied one.
        List<Icon> icons = LocatorJ._find(Icon.class);
        assertTrue(icons.stream().anyMatch(
                        i -> "vaadin:exclamation-circle".equals(i.getElement().getAttribute("icon"))),
                "expected exclamation-circle Vaadin Icon on the dialog; saw: "
                        + icons.stream().map(i -> i.getElement().getAttribute("icon")).toList());

        // Click OK to release the modal park so the test cleanly tears down.
        Button ok = LocatorJ._get(Button.class, spec -> spec.withText("OK"));
        EHelper.callSwing(() -> LocatorJ._click(ok));
        assertTrue(finished.await(5, TimeUnit.SECONDS));
    }

    // ------- Other typed-getters stub out and warn -------

    @Test
    @DisplayName("getColor answers Metal's value for a covered key, silently")
    void coveredColorKeyAnswersMetalSilently() {
        // D_theme_is_lookandfeel installs Metal's colour table, so the keys it
        // covers stop being stubs. The returned instance is a ColorUIResource,
        // which is what makes passing it back into setBackground mean "reset me
        // to the default" rather than "pin this literal".
        List<String> warns = capturingWarns(() -> {
            Color bg = UIManager.getColor("Panel.background");
            assertEquals(new Color(238, 238, 238), bg);
            assertInstanceOf(UIResource.class, bg);
            assertEquals(new Color(255, 255, 255), UIManager.getColor("TextField.background"));
            assertEquals(new Color(238, 238, 238), UIManager.getColor("control"));
        });
        assertTrue(warns.isEmpty(), "a covered key must not WARN, got " + warns);
    }

    @Test
    @DisplayName("getColor still stubs to null with WARN for an uncovered key")
    void uncoveredColorKeyStubsWithWarn() {
        // The table is deliberately partial: a migrator reaching for a key SB-Emulators
        // does not model still has a real unmet expectation and must see it.
        List<String> warns = capturingWarns(
                () -> assertNull(UIManager.getColor("Tree.selectionBorderColor")));
        assertTrue(warns.stream().anyMatch(w -> w.contains("UIManager.getColor")));
    }

    @Test
    @DisplayName("getInt stubs to zero with WARN")
    void getIntStubsToZeroWithWarn() {
        List<String> warns = capturingWarns(
                () -> assertEquals(0, UIManager.getInt("Some.intKey")));
        assertTrue(warns.stream().anyMatch(w -> w.contains("UIManager.getInt")));
    }

    @Test
    @DisplayName("setLookAndFeel is a WARN noop")
    void setLookAndFeelIsAWarnNoop() {
        List<String> warns = capturingWarns(
                () -> UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel"));
        assertTrue(warns.stream().anyMatch(w -> w.contains("UIManager.setLookAndFeel")));
    }
}
