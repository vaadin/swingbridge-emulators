/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultSingleSelectionModel;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SJTabbedPane (SD_sjtabbedpane). Covers:
 *
 * <ol>
 *  <li><b>Ctors + validation</b> — no-arg defaults to TOP / WRAP; placement
 *      and layout-policy ints validated per JDK (IAE on garbage).
 *  <li><b>Tab add / insert / remove</b> — addTab variants, insertTab with
 *      bounds check, removeTabAt with bounds check, removeAll.
 *  <li><b>Selection bridge</b> — setSelectedIndex (through model), bounds
 *      check, getSelectedComponent/setSelectedComponent.
 *  <li><b>ChangeListener fan-out</b> — fires on tabbedPane.setSelectedIndex,
 *      fires on model.setSelectedIndex direct, dedupes on same-value.
 *  <li><b>removeTabAt selection shift</b> — middle / last / below / above /
 *      only-tab: matches JDK (Vaadin-confirmed via design probe).
 *  <li><b>Per-tab metadata</b> — title/icon/tooltip/enabled/tabComponent
 *      round-trip; children-based render verified via tab.children;
 *      setAriaLabel for a11y.
 *  <li><b>setTabPlacement</b> — TOP silent, LEFT/RIGHT/BOTTOM WARN, all
 *      round-trip.
 *  <li><b>setTabLayoutPolicy</b> — SCROLL silent (no WARN, just onNoop debug),
 *      WRAP WARNs.
 *  <li><b>R_vaadin_first drop-and-WARN</b> — setModel, setMnemonicAt, setBackgroundAt,
 *      setForegroundAt, setDisabledIconAt, getBoundsAt, indexAtLocation.
 *  <li><b>L&amp;F surface</b> — getUIClassID, updateUI, setUI.
 *  <li><b>data-swing-class stamp</b> + JComponentMixin smoke.
 * </ol>
 */
class SJTabbedPaneTest extends AbstractKaribuTest {

    /** Minimal 1x1 PNG bytes — passes ImageIcon's mime-derive-from-suffix. */
    private static byte[] onePixelPng() {
        // 1x1 transparent PNG
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82,
        };
    }

    private static List<Component> tabChildren(SJTabbedPane tp, int index) {
        return tp.getTabAt(index).getChildren().toList();
    }

    /** Records the selected index at each ChangeListener fire. */
    private static List<Integer> recordSelectionFires(SJTabbedPane tp) {
        final List<Integer> fires = new ArrayList<>();
        tp.addChangeListener(e -> fires.add(tp.getSelectedIndex()));
        return fires;
    }

    // --- Ctors + validation --------------------------------------------------

    @Test
    @DisplayName("no-arg ctor defaults are TOP + WRAP, no WARN")
    void noArgCtorDefaults() {
        final SJTabbedPane tp = new SJTabbedPane();
        assertEquals(SwingConstants.TOP, tp.getTabPlacement());
        assertEquals(SJTabbedPane.WRAP_TAB_LAYOUT, tp.getTabLayoutPolicy());
        assertEquals(0, tp.getTabCount());
        assertEquals(-1, tp.getSelectedIndex());
        assertNoWarns("no-arg ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("placement-only ctor accepts TOP without WARN")
    void placementCtorTopIsSilent() {
        new SJTabbedPane(SwingConstants.TOP);
        assertNoWarns();
    }

    @Test
    @DisplayName("placement-only ctor accepts BOTTOM but stays silent (WARN deferred to explicit setter)")
    void placementCtorBottomIsSilent() {
        final SJTabbedPane tp = new SJTabbedPane(SwingConstants.BOTTOM);
        assertEquals(SwingConstants.BOTTOM, tp.getTabPlacement());
        // Ctor doesn't WARN — explicit user intent at construction is
        // ambiguous (matches JDK default? being explicit?). Setter WARNs.
        assertNoWarns();
    }

    @Test
    @DisplayName("ctor throws IAE on garbage placement")
    void ctorGarbagePlacementThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SJTabbedPane(42));
    }

    @Test
    @DisplayName("ctor throws IAE on garbage layout policy")
    void ctorGarbageLayoutPolicyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SJTabbedPane(SwingConstants.TOP, 99));
    }

    // --- Tab add / insert / remove ------------------------------------------

    @Test
    @DisplayName("addTab String + Component appends")
    void addTabAppends() {
        final SJTabbedPane tp = new SJTabbedPane();
        final Div content = new Div(new Span("a"));
        tp.addTab("A", content);
        assertEquals(1, tp.getTabCount());
        assertSame(content, tp.getComponentAt(0));
        assertEquals("A", tp.getTitleAt(0));
    }

    @Test
    @DisplayName("addTab variants — icon, tip, all four positional")
    void addTabVariants() {
        final SJTabbedPane tp = new SJTabbedPane();
        final ImageIcon icon = new ImageIcon(new byte[0]);
        tp.addTab("A", icon, new Div());
        tp.addTab("B", icon, new Div(), "tip-B");
        assertEquals(2, tp.getTabCount());
        assertEquals("tip-B", tp.getToolTipTextAt(1));
        assertSame(icon, tp.getIconAt(0));
    }

    @Test
    @DisplayName("insertTab places at given index")
    void insertTabPlacesAtIndex() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("C", new Div());
        tp.insertTab("B", null, new Div(), null, 1);
        assertEquals(3, tp.getTabCount());
        assertEquals("A", tp.getTitleAt(0));
        assertEquals("B", tp.getTitleAt(1));
        assertEquals("C", tp.getTitleAt(2));
    }

    @Test
    @DisplayName("insertTab throws IOOBE on negative index (Vaadin would have appended)")
    void insertTabNegativeThrows() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        assertThrows(IndexOutOfBoundsException.class,
                () -> tp.insertTab("X", null, new Div(), null, -1));
    }

    @Test
    @DisplayName("insertTab throws IOOBE past end")
    void insertTabPastEndThrows() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        assertThrows(IndexOutOfBoundsException.class,
                () -> tp.insertTab("X", null, new Div(), null, 5));
    }

    @Test
    @DisplayName("insertTab allows index == tabCount as append shorthand")
    void insertTabAtCountAppends() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.insertTab("B", null, new Div(), null, 1); // 1 == tabCount
        assertEquals(2, tp.getTabCount());
        assertEquals("B", tp.getTitleAt(1));
    }

    @Test
    @DisplayName("removeTabAt drops the tab + metadata")
    void removeTabAtDropsMetadata() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.removeTabAt(1);
        assertEquals(2, tp.getTabCount());
        assertEquals("A", tp.getTitleAt(0));
        assertEquals("C", tp.getTitleAt(1));
    }

    @Test
    @DisplayName("removeTabAt throws IOOBE out of bounds")
    void removeTabAtOutOfBoundsThrows() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        assertThrows(IndexOutOfBoundsException.class, () -> tp.removeTabAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> tp.removeTabAt(1));
    }

    @Test
    @DisplayName("removeAll clears tabs and resets selection to -1")
    void removeAllResetsSelection() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        assertEquals(0, tp.getSelectedIndex());
        tp.removeAll();
        assertEquals(0, tp.getTabCount());
        assertEquals(-1, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("removeAll fires one ChangeEvent, as the JDK's does")
    void removeAllFiresOnce() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.setSelectedIndex(1);
        final List<Integer> fires = new ArrayList<>();
        tp.addChangeListener(e -> fires.add(tp.getModel().getSelectedIndex()));
        tp.removeAll();
        assertEquals(List.of(-1), fires);
    }

    @Test
    @DisplayName("removing the selected tab next to a disabled one does not throw")
    void removeSelectedTabBesideADisabledOne() {
        // Vaadin moves the selection onto the disabled neighbour, refuses it, and falls back
        // to the removed tab: "Tab to select must be a child".
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.setEnabledAt(1, false);
        tp.removeTabAt(0);
        assertEquals(2, tp.getTabCount());
        assertEquals(-1, tp.getSelectedIndex(), "Vaadin cannot show the disabled tab");
        assertEquals(-1, tp.getModel().getSelectedIndex());

        tp.setSelectedIndex(1);
        tp.setEnabledAt(0, false);
        tp.removeAll();
        assertEquals(0, tp.getTabCount());
    }

    @Test
    @DisplayName("setComponentAt on the selected tab keeps it selected and fires nothing")
    void setComponentAtKeepsTheSelection() {
        // Vaadin's addTabAtIndex moves the selection past the re-inserted tab.
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.setSelectedIndex(1);
        final List<Integer> fires = new ArrayList<>();
        tp.addChangeListener(e -> fires.add(tp.getModel().getSelectedIndex()));
        final Div replacement = new Div();
        tp.setComponentAt(1, replacement);
        assertEquals(1, tp.getSelectedIndex());
        assertSame(replacement, tp.getSelectedComponent());
        assertEquals(1, tp.getModel().getSelectedIndex());
        assertEquals(List.of(), fires);
    }

    // --- Selection bridge ----------------------------------------------------

    @Test
    @DisplayName("addTab autoselects first added")
    void addTabAutoselectsFirst() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        assertEquals(0, tp.getSelectedIndex());
        tp.addTab("B", new Div());
        assertEquals(0, tp.getSelectedIndex()); // subsequent adds don't shift
    }

    @Test
    @DisplayName("setSelectedIndex moves selection")
    void setSelectedIndexMoves() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.setSelectedIndex(1);
        assertEquals(1, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("setSelectedIndex -1 deselects")
    void setSelectedIndexMinusOneDeselects() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.setSelectedIndex(-1);
        assertEquals(-1, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("setSelectedIndex throws IOOBE past end")
    void setSelectedIndexOutOfBoundsThrows() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        assertThrows(IndexOutOfBoundsException.class, () -> tp.setSelectedIndex(5));
        assertThrows(IndexOutOfBoundsException.class, () -> tp.setSelectedIndex(-2));
    }

    @Test
    @DisplayName("getSelectedComponent matches getComponentAt of selectedIndex")
    void selectedComponentMatchesIndex() {
        final SJTabbedPane tp = new SJTabbedPane();
        final Div a = new Div();
        final Div b = new Div();
        tp.addTab("A", a);
        tp.addTab("B", b);
        tp.setSelectedIndex(1);
        assertSame(b, tp.getSelectedComponent());
    }

    @Test
    @DisplayName("setSelectedComponent picks the right index")
    void setSelectedComponentPicksIndex() {
        final SJTabbedPane tp = new SJTabbedPane();
        final Div a = new Div();
        final Div b = new Div();
        final Div c = new Div();
        tp.addTab("A", a);
        tp.addTab("B", b);
        tp.addTab("C", c);
        tp.setSelectedComponent(c);
        assertEquals(2, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("setSelectedComponent throws IAE for non-member")
    void setSelectedComponentNonMemberThrows() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        assertThrows(IllegalArgumentException.class, () -> tp.setSelectedComponent(new Div()));
    }

    // --- ChangeListener fan-out ---------------------------------------------

    @Test
    @DisplayName("addChangeListener fires on tabbedPane setSelectedIndex")
    void changeListenerFiresOnSetSelectedIndex() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        final List<Integer> fires = recordSelectionFires(tp);
        tp.setSelectedIndex(1);
        assertEquals(List.of(1), fires);
    }

    @Test
    @DisplayName("addChangeListener fires once for model-direct setSelectedIndex")
    void changeListenerFiresOnceForModelDirect() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        final List<Integer> fires = recordSelectionFires(tp);
        tp.getModel().setSelectedIndex(1);
        assertEquals(List.of(1), fires);
        assertEquals(1, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("addChangeListener does NOT fire for same-value setSelectedIndex (peer dedupe)")
    void changeListenerDedupesSameValue() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.setSelectedIndex(1);
        final List<Integer> fires = recordSelectionFires(tp);
        tp.setSelectedIndex(1);
        assertTrue(fires.isEmpty(), "same-value set should not fire: " + fires);
    }

    @Test
    @DisplayName("removeChangeListener stops the fan-out")
    void removeChangeListenerStopsFanout() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        final List<Integer> fires = new ArrayList<>();
        final ChangeListener l = e -> fires.add(tp.getSelectedIndex());
        tp.addChangeListener(l);
        tp.removeChangeListener(l);
        tp.setSelectedIndex(1);
        assertTrue(fires.isEmpty());
    }

    // --- removeTabAt selection-shift (JDK-compat, probe-verified) ------------

    @Test
    @DisplayName("removeTabAt — middle while selected — stays at index (now next tab)")
    void removeSelectedMiddleStaysAtIndex() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.setSelectedIndex(1);
        tp.removeTabAt(1);
        assertEquals(1, tp.getSelectedIndex()); // index 1 now is former C
        assertEquals("C", tp.getTitleAt(1));
    }

    @Test
    @DisplayName("removeTabAt — last while selected — shifts to i-1")
    void removeSelectedLastShiftsBack() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.setSelectedIndex(2);
        tp.removeTabAt(2);
        assertEquals(1, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("removeTabAt — below selected — decrements")
    void removeBelowSelectedDecrements() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.setSelectedIndex(2);
        tp.removeTabAt(0);
        assertEquals(1, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("removeTabAt — above selected — unchanged")
    void removeAboveSelectedUnchanged() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.setSelectedIndex(0);
        tp.removeTabAt(2);
        assertEquals(0, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("removeTabAt — only tab — selectedIndex becomes -1")
    void removeOnlyTabDeselects() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.removeTabAt(0);
        assertEquals(-1, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("removeTabAt fires ChangeListener if selection moved")
    void removeTabAtFiresWhenSelectionMoves() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.addTab("C", new Div());
        tp.setSelectedIndex(2);
        final List<Integer> fires = recordSelectionFires(tp);
        tp.removeTabAt(2); // selected last → shifts to 1
        assertEquals(List.of(1), fires);
    }

    // --- Per-tab metadata + children render ---------------------------------

    @Test
    @DisplayName("setTitleAt round-trips and rebuilds tab children (no setLabel)")
    void setTitleAtRebuildsChildren() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        final Tab tab = tp.getTabAt(0);
        tp.setTitleAt(0, "Renamed");
        assertEquals("Renamed", tp.getTitleAt(0));
        // Children-based render: single Span with the title text.
        final List<Component> children = tabChildren(tp, 0);
        assertEquals(1, children.size());
        final Span span = assertInstanceOf(Span.class, children.get(0));
        assertEquals("Renamed", span.getText());
        // a11y: ariaLabel reflects the title.
        assertEquals("Renamed", tab.getElement().getAttribute("aria-label"));
    }

    @Test
    @DisplayName("setIconAt round-trips the Icon")
    void setIconAtRoundTrips() {
        final SJTabbedPane tp = new SJTabbedPane();
        final ImageIcon ii = new ImageIcon(onePixelPng());
        tp.addTab("A", null, new Div());
        tp.setIconAt(0, ii);
        assertSame(ii, tp.getIconAt(0));
        // Title-rendering side: with a valid icon + title, the Tab should
        // carry at least one child Span(title) and an aria-label. The Image
        // child only materialises when AWT can decode the ImageIcon raster,
        // which is environment-dependent in headless tests — we don't assert
        // its presence here. Visual verification lives in the Sampler route.
        final List<Component> children = tabChildren(tp, 0);
        assertTrue(children.stream().anyMatch(c -> c instanceof Span s && "A".equals(s.getText())),
                "expected title Span among children: " + children);
        assertEquals("A", tp.getTabAt(0).getElement().getAttribute("aria-label"));
    }

    @Test
    @DisplayName("setIconAt(null) clears icon and re-renders title-only")
    void setIconAtNullRendersTitleOnly() {
        final SJTabbedPane tp = new SJTabbedPane();
        final ImageIcon ii = new ImageIcon(onePixelPng());
        tp.addTab("A", ii, new Div());
        tp.setIconAt(0, null);
        assertNull(tp.getIconAt(0));
        // Title-only render: a single Span.
        final List<Component> children = tabChildren(tp, 0);
        assertEquals(1, children.size());
        assertInstanceOf(Span.class, children.get(0));
    }

    @Test
    @DisplayName("setTabComponentAt — custom owns header, title stays in shadow for ariaLabel")
    void setTabComponentAtOwnsHeader() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        final Button custom = new Button("close");
        tp.setTabComponentAt(0, custom);
        final List<Component> children = tabChildren(tp, 0);
        assertEquals(1, children.size());
        assertSame(custom, children.get(0));
        // Shadow title preserved + ariaLabel set.
        assertEquals("A", tp.getTitleAt(0));
        assertEquals("A", tp.getTabAt(0).getElement().getAttribute("aria-label"));
    }

    @Test
    @DisplayName("setTabComponentAt(null) reverts to title-only render")
    void setTabComponentAtNullReverts() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.setTabComponentAt(0, new Button("close"));
        tp.setTabComponentAt(0, null);
        final List<Component> children = tabChildren(tp, 0);
        assertEquals(1, children.size());
        final Span span = assertInstanceOf(Span.class, children.get(0));
        assertEquals("A", span.getText());
    }

    @Test
    @DisplayName("setToolTipTextAt forwards to Vaadin tab tooltip")
    void setToolTipTextAtForwards() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.setToolTipTextAt(0, "hover me");
        assertEquals("hover me", tp.getToolTipTextAt(0));
    }

    @Test
    @DisplayName("setEnabledAt + isEnabledAt round-trip via Vaadin tab")
    void enabledAtRoundTrips() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        tp.setEnabledAt(1, false);
        assertFalse(tp.isEnabledAt(1));
        assertTrue(tp.isEnabledAt(0));
    }

    // --- Index lookups -------------------------------------------------------

    @Test
    @DisplayName("indexOfTab finds by title")
    void indexOfTabByTitle() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        assertEquals(1, tp.indexOfTab("B"));
        assertEquals(-1, tp.indexOfTab("Missing"));
    }

    @Test
    @DisplayName("indexOfComponent finds by content identity")
    void indexOfComponentByIdentity() {
        final SJTabbedPane tp = new SJTabbedPane();
        final Div a = new Div();
        final Div b = new Div();
        tp.addTab("A", a);
        tp.addTab("B", b);
        assertEquals(0, tp.indexOfComponent(a));
        assertEquals(-1, tp.indexOfComponent(new Div()));
    }

    @Test
    @DisplayName("indexOfTabComponent finds by custom-header identity")
    void indexOfTabComponentByIdentity() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.addTab("B", new Div());
        final Span custom = new Span("custom");
        tp.setTabComponentAt(1, custom);
        assertEquals(1, tp.indexOfTabComponent(custom));
        assertEquals(-1, tp.indexOfTabComponent(new Span("other")));
    }

    // --- setTabPlacement / setTabLayoutPolicy --------------------------------

    @Test
    @DisplayName("setTabPlacement(TOP) is silent")
    void setTabPlacementTopIsSilent() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.setTabPlacement(SwingConstants.TOP);
        assertNoWarns();
    }

    @Test
    @DisplayName("setTabPlacement(BOTTOM) WARNs but round-trips")
    void setTabPlacementBottomWarns() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.setTabPlacement(SwingConstants.BOTTOM);
        assertEquals(SwingConstants.BOTTOM, tp.getTabPlacement());
        assertEquals(1, capturedWarns.size(), "expected one WARN: " + capturedWarns);
        assertTrue(capturedWarns.get(0).contains("setTabPlacement"));
    }

    @Test
    @DisplayName("setTabPlacement throws IAE on garbage")
    void setTabPlacementGarbageThrows() {
        final SJTabbedPane tp = new SJTabbedPane();
        assertThrows(IllegalArgumentException.class, () -> tp.setTabPlacement(42));
    }

    @Test
    @DisplayName("setTabLayoutPolicy(SCROLL) is silent (Vaadin already auto-scrolls)")
    void setTabLayoutPolicyScrollIsSilent() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.setTabLayoutPolicy(SJTabbedPane.SCROLL_TAB_LAYOUT);
        assertEquals(SJTabbedPane.SCROLL_TAB_LAYOUT, tp.getTabLayoutPolicy());
        assertNoWarns("SCROLL_TAB_LAYOUT should be silent: " + capturedWarns);
    }

    @Test
    @DisplayName("setTabLayoutPolicy(WRAP) WARNs but round-trips")
    void setTabLayoutPolicyWrapWarns() {
        final SJTabbedPane tp = new SJTabbedPane(SwingConstants.TOP, SJTabbedPane.SCROLL_TAB_LAYOUT);
        tp.setTabLayoutPolicy(SJTabbedPane.WRAP_TAB_LAYOUT);
        assertEquals(SJTabbedPane.WRAP_TAB_LAYOUT, tp.getTabLayoutPolicy());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setTabLayoutPolicy"));
    }

    @Test
    @DisplayName("setTabLayoutPolicy throws IAE on garbage")
    void setTabLayoutPolicyGarbageThrows() {
        final SJTabbedPane tp = new SJTabbedPane();
        assertThrows(IllegalArgumentException.class, () -> tp.setTabLayoutPolicy(99));
    }

    // --- R_vaadin_first drop-and-WARN surface --------------------------------------------

    @Test
    @DisplayName("setModel swaps the model: rewires the internal listener, adopts the new selection, no WARN")
    void setModelSwaps() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("one", new Div());
        tp.addTab("two", new Div());
        final DefaultSingleSelectionModel replacement = new DefaultSingleSelectionModel();
        replacement.setSelectedIndex(1);

        tp.setModel(replacement);

        assertEquals(0, capturedWarns.size());
        assertSame(replacement, tp.getModel());
        // Handoff synced the peer to the new model's selection.
        assertEquals(1, tp.getSelectedIndex());
        // The internal listener moved: driving the new model drives the peer.
        replacement.setSelectedIndex(0);
        assertEquals(0, tp.getSelectedIndex());
    }

    @Test
    @DisplayName("getModel returns the default DefaultSingleSelectionModel")
    void getModelReturnsDefault() {
        final SJTabbedPane tp = new SJTabbedPane();
        assertNotNull(tp.getModel());
        assertInstanceOf(DefaultSingleSelectionModel.class, tp.getModel());
    }

    @Test
    @DisplayName("setMnemonicAt WARNs")
    void setMnemonicAtWarns() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.setMnemonicAt(0, KeyEvent.VK_A);
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setMnemonicAt"));
    }

    @Test
    @DisplayName("setBackgroundAt + setForegroundAt + setDisabledIconAt all WARN")
    void perTabColorAndDisabledIconWarn() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.setBackgroundAt(0, Color.RED);
        tp.setForegroundAt(0, Color.BLUE);
        tp.setDisabledIconAt(0, new ImageIcon(new byte[0]));
        assertEquals(3, capturedWarns.size());
    }

    @Test
    @DisplayName("getBoundsAt + indexAtLocation WARN (no server-side coords)")
    void geometryQueriesWarn() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.addTab("A", new Div());
        tp.getBoundsAt(0);
        tp.indexAtLocation(10, 10);
        assertEquals(2, capturedWarns.size());
    }

    @Test
    @DisplayName("getTabRunCount returns 1 for non-empty, 0 for empty")
    void tabRunCount() {
        final SJTabbedPane tp = new SJTabbedPane();
        assertEquals(0, tp.getTabRunCount());
        tp.addTab("A", new Div());
        assertEquals(1, tp.getTabRunCount());
    }

    // --- L&F surface ---------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is TabbedPaneUI")
    void uiClassIdIsTabbedPaneUi() {
        assertEquals("TabbedPaneUI", new SJTabbedPane().getUIClassID());
    }

    @Test
    @DisplayName("updateUI is silent no-op")
    void updateUiIsSilent() {
        new SJTabbedPane().updateUI();
        assertNoWarns();
    }

    @Test
    @DisplayName("setUI WARNs")
    void setUiWarns() {
        new SJTabbedPane().setUI(null);
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setUI"));
    }

    // --- Smoke: data-swing-class + JComponentMixin ---------------------------

    @Test
    @DisplayName("data-swing-class attribute stamped on host element")
    void dataSwingClassStamped() {
        final SJTabbedPane tp = new SJTabbedPane();
        assertEquals("SJTabbedPane", tp.getElement().getAttribute("data-swing-class"));
    }

    @Test
    @DisplayName("JComponentMixin name round-trips")
    void nameRoundTrips() {
        final SJTabbedPane tp = new SJTabbedPane();
        tp.setName("my-tabs");
        assertEquals("my-tabs", tp.getName());
    }
}
