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

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exit gate for SD_sjscrollpane's SJScrollPane surrogate. Covers:
 *
 * <ol>
 *  <li>Constructors — no-arg / Component / Component+ScrollDirection.
 *  <li>UIClassID is "ScrollPaneUI"; setUI WARNs.
 *  <li>Auto-scroll guard fires on Grid / TextArea / Scroller content
 *      (forces NONE) and stays inactive for plain Div content (default
 *      BOTH stands).
 *  <li>setContent(null) clears the slot without errors.
 *  <li>Post-content setScrollDirection overrides the guard's NONE
 *      (opt-out works).
 *  <li>Happy-path zero stub WARNs.
 * </ol>
 */
class SJScrollPaneTest extends AbstractKaribuTest {

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("no-arg ctor produces an empty SJScrollPane")
    void noArgCtorProducesAnEmptySjScrollPane() {
        SJScrollPane sp = new SJScrollPane();
        assertNull(sp.getContent());
        // Vaadin Scroller default direction is BOTH.
        assertEquals(Scroller.ScrollDirection.BOTH, sp.getScrollDirection());
    }

    @Test
    @DisplayName("single-arg ctor wraps the content")
    void singleArgCtorWrapsTheContent() {
        Span span = new Span("hello");
        SJScrollPane sp = new SJScrollPane(span);
        assertEquals(span, sp.getContent());
        // Plain Span isn't an auto-scrolling type — direction stays at default.
        assertEquals(Scroller.ScrollDirection.BOTH, sp.getScrollDirection());
    }

    @Test
    @DisplayName("two-arg ctor sets direction first then content")
    void twoArgCtorSetsDirectionFirstThenContent() {
        Span span = new Span("hello");
        SJScrollPane sp = new SJScrollPane(span, Scroller.ScrollDirection.VERTICAL);
        assertEquals(span, sp.getContent());
        assertEquals(Scroller.ScrollDirection.VERTICAL, sp.getScrollDirection());
    }

    @Test
    @DisplayName("peer is a Vaadin Scroller")
    void peerIsAVaadinScroller() {
        // Karibu locator-by-Scroller.class still resolves.
        assertInstanceOf(Scroller.class, new SJScrollPane());
    }

    // --- L&F surface -------------------------------------------------

    @Test
    @DisplayName("getUIClassID is ScrollPaneUI")
    void getUiClassIdIsScrollPaneUi() {
        assertEquals("ScrollPaneUI", new SJScrollPane().getUIClassID());
    }

    @Test
    @DisplayName("setUI WARNs and drops")
    void setUiWarnsAndDrops() {
        SJScrollPane sp = new SJScrollPane();
        sp.setUI(null);
        assertEquals(1, capturedWarns.size());
    }

    // --- Auto-scroll guard -------------------------------------------

    @Test
    @DisplayName("wrapping a Grid forces ScrollDirection NONE per the auto-scroll guard")
    void wrappingAGridForcesScrollDirectionNone() {
        Grid<String> grid = new Grid<>();
        SJScrollPane sp = new SJScrollPane(grid);
        assertEquals(Scroller.ScrollDirection.NONE, sp.getScrollDirection());
    }

    @Test
    @DisplayName("wrapping a TextArea forces ScrollDirection NONE per the auto-scroll guard")
    void wrappingATextAreaForcesScrollDirectionNone() {
        TextArea ta = new TextArea();
        SJScrollPane sp = new SJScrollPane(ta);
        assertEquals(Scroller.ScrollDirection.NONE, sp.getScrollDirection());
    }

    @Test
    @DisplayName("wrapping a Scroller forces ScrollDirection NONE per the auto-scroll guard")
    void wrappingAScrollerForcesScrollDirectionNone() {
        Scroller inner = new Scroller();
        SJScrollPane sp = new SJScrollPane(inner);
        assertEquals(Scroller.ScrollDirection.NONE, sp.getScrollDirection());
    }

    @Test
    @DisplayName("wrapping a plain Div does NOT trigger the auto-scroll guard")
    void wrappingAPlainDivDoesNotTriggerTheAutoScrollGuard() {
        Div div = new Div();
        SJScrollPane sp = new SJScrollPane(div);
        // Default Scroller direction stays in place.
        assertEquals(Scroller.ScrollDirection.BOTH, sp.getScrollDirection());
    }

    @Test
    @DisplayName("wrapping a SJTable (Grid subtype) forces NONE — instanceof catches subclasses")
    void wrappingASjTableForcesNone() {
        SJTable sjt = new SJTable();
        SJScrollPane sp = new SJScrollPane(sjt);
        assertEquals(Scroller.ScrollDirection.NONE, sp.getScrollDirection());
    }

    // --- Content swap behaviour --------------------------------------

    @Test
    @DisplayName("setContent(null) clears the slot")
    void setContentNullClearsTheSlot() {
        SJScrollPane sp = new SJScrollPane(new Span("hi"));
        sp.setContent(null);
        assertNull(sp.getContent());
    }

    @Test
    @DisplayName("setContent swap from Grid to plain Div leaves direction at NONE — guard fires only on auto-scroll")
    void setContentSwapFromGridToPlainDivLeavesDirectionAtNone() {
        // The guard fires when the content IS auto-scrolling. Going from
        // auto-scrolling (Grid forced NONE) to non-auto-scrolling (Div) leaves
        // whatever direction was last set — the guard doesn't restore a prior
        // direction. R_vaadin_first-accepted lossy direction; document.
        SJScrollPane sp = new SJScrollPane(new Grid<String>());
        assertEquals(Scroller.ScrollDirection.NONE, sp.getScrollDirection());
        sp.setContent(new Div());
        assertEquals(Scroller.ScrollDirection.NONE, sp.getScrollDirection());
    }

    @Test
    @DisplayName("post-content setScrollDirection overrides the guard's NONE — opt-out works")
    void postContentSetScrollDirectionOverridesTheGuardsNone() {
        // The guard fires once on content swap. Re-asserting setScrollDirection
        // after setContent gets the user's value back; double-scrollbars are
        // opt-in but not the default.
        SJScrollPane sp = new SJScrollPane(new Grid<String>());
        assertEquals(Scroller.ScrollDirection.NONE, sp.getScrollDirection());
        sp.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        assertEquals(Scroller.ScrollDirection.VERTICAL, sp.getScrollDirection());
    }

    // --- Happy-path zero-WARN ----------------------------------------

    @Test
    @DisplayName("happy-path UI-functional surface is WARN-free")
    void happyPathUiFunctionalSurfaceIsWarnFree() {
        SJScrollPane sp = new SJScrollPane();
        sp.setContent(new Span("body"));
        sp.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        sp.setContent(new Grid<String>());  // guard fires silently — no WARN
        sp.setContent(null);
        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }
}
