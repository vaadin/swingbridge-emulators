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

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two custom-layout paths of M1D_custom_layoutmanager:
 * Option A (a hand-rolled pixel {@link LayoutManager} → WARN + vertical fallback) and
 * Option B (a migrator-implemented {@link CssEmittingLayoutManager} → containerCss /
 * childCss applied by the framework). The six built-in layouts have their own
 * per-layout suites; this one exercises the seam itself.
 */
class CssEmittingLayoutManagerTest extends AbstractKaribuTest {

    private Component component() {
        return new Component(new Div()) {
        };
    }

    /** A hand-rolled pixel layout — the thing Option A can't translate. */
    private static final class PixelLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(1, 1);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(1, 1);
        }

        @Override
        public void layoutContainer(Container parent) {
            /* pixel setBounds — emits no CSS */
        }
    }

    /** A migrator layout that needs only container CSS. */
    private static final class ContainerOnlyLayout implements CssEmittingLayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(1, 1);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(1, 1);
        }

        @Override
        public Map<String, String> containerCss(Container parent) {
            return Map.of("display", "grid", "grid-template-columns", "auto 1fr", "gap", "4px 8px");
        }
    }

    /** A migrator layout with per-child CSS too. */
    private static final class PerChildLayout implements CssEmittingLayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(1, 1);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(1, 1);
        }

        @Override
        public Map<String, String> containerCss(Container parent) {
            return Map.of("display", "flex", "flex-direction", "row");
        }

        @Override
        public Map<String, String> childCss(Container parent, Component child) {
            return Map.of("flex", "1 1 0", "margin", "2px");
        }
    }

    // ---- Option A: custom pixel layout fallback ----------------------------

    @Test
    @DisplayName("custom LayoutManager warns once and applies a vertical-stack fallback")
    void customLayoutManagerWarnsOnceAndAppliesAVerticalStackFallback() {
        List<String> warns = new ArrayList<>();
        Consumer<String> saved = EHelper.warnHook;
        try {
            EHelper.warnHook = warns::add;
            Container c = new Container();
            c.setLayout(new PixelLayout());
            c.doLayout();

            var style = c.getPeer().getElement().getStyle();
            assertEquals("flex", style.get("display"));
            assertEquals("column", style.get("flex-direction"));
            assertEquals("stretch", style.get("align-items"));

            assertEquals(1, warns.size(), "expected exactly one custom-layout WARN; got " + warns);
            assertTrue(warns.get(0).contains("custom LayoutManager"), warns.get(0));

            // Warn-once: a re-layout (revalidate/attach) must not spam.
            c.doLayout();
            assertEquals(1, warns.size(), "re-layout must not warn again; got " + warns);
        } finally {
            EHelper.warnHook = saved;
        }
    }

    @Test
    @DisplayName("setLayout re-arms the custom-layout warning for a new manager")
    void setLayoutReArmsTheCustomLayoutWarningForANewManager() {
        List<String> warns = new ArrayList<>();
        Consumer<String> saved = EHelper.warnHook;
        try {
            EHelper.warnHook = warns::add;
            Container c = new Container();
            c.setLayout(new PixelLayout());
            c.doLayout();
            assertEquals(1, warns.size());
            c.setLayout(new PixelLayout());
            c.doLayout();
            assertEquals(2, warns.size(), "a newly installed custom layout should warn again; got " + warns);
        } finally {
            EHelper.warnHook = saved;
        }
    }

    @Test
    @DisplayName("vertical fallback clears a prior grid layout's named areas")
    void verticalFallbackClearsAPriorGridLayoutsNamedAreas() {
        // Switching BorderLayout (which writes grid-template-areas) → a custom
        // layout must reset the named areas, else children stay pinned to Border
        // cells under the fallback. Exercises the null-value removal semantics.
        Container c = new Container();
        c.setLayout(new BorderLayout());
        c.doLayout();
        assertNotNull(c.getPeer().getElement().getStyle().get("grid-template-areas"));

        c.setLayout(new PixelLayout());
        c.doLayout();
        assertNull(c.getPeer().getElement().getStyle().get("grid-template-areas"),
                "vertical fallback must clear the named grid areas");
    }

    // ---- Option B: CssEmittingLayoutManager --------------------------------

    @Test
    @DisplayName("containerCss is applied and does not trip the custom-layout WARN")
    void containerCssIsAppliedAndDoesNotTripTheCustomLayoutWarn() {
        List<String> warns = new ArrayList<>();
        Consumer<String> saved = EHelper.warnHook;
        try {
            EHelper.warnHook = warns::add;
            Container c = new Container();
            c.setLayout(new ContainerOnlyLayout());
            c.doLayout();

            var style = c.getPeer().getElement().getStyle();
            assertEquals("grid", style.get("display"));
            assertEquals("auto 1fr", style.get("grid-template-columns"));
            assertEquals("4px 8px", style.get("gap"));
            assertEquals(List.of(), warns,
                    "a CssEmittingLayoutManager is a recognized layout — no WARN");
        } finally {
            EHelper.warnHook = saved;
        }
    }

    @Test
    @DisplayName("childCss is applied to every child")
    void childCssIsAppliedToEveryChild() {
        Container c = new Container();
        c.setLayout(new PerChildLayout());
        Component a = component();
        Component b = component();
        c.add(a);
        c.add(b);

        c.doLayout();

        assertEquals("flex", c.getPeer().getElement().getStyle().get("display"));
        for (Component child : List.of(a, b)) {
            assertEquals("1 1 0", child.getPeer().getElement().getStyle().get("flex"));
            assertEquals("2px", child.getPeer().getElement().getStyle().get("margin"));
        }
    }

    @Test
    @DisplayName("a container-only layout leaves children untouched (childCss defaults to null)")
    void aContainerOnlyLayoutLeavesChildrenUntouchedChildCssDefaultsToNull() {
        Container c = new Container();
        c.setLayout(new ContainerOnlyLayout());
        Component a = component();
        c.add(a);

        c.doLayout();

        assertNull(a.getPeer().getElement().getStyle().get("flex"));
        assertNull(a.getPeer().getElement().getStyle().get("margin"));
    }
}
