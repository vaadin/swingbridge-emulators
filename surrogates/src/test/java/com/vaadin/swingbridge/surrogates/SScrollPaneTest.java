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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.AWTError;
import java.awt.FlowLayout;
import java.awt.ScrollPane;
import java.util.List;

import javax.swing.ScrollPaneConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the SScrollPane surrogate — AWT's {@code java.awt.ScrollPane}, not
 * {@code JScrollPane}. Covers:
 *
 * <ol>
 *  <li>The ctor's three-arm policy switch, its WARN, and its IAE message.
 *  <li>{@code setContent} replacing rather than appending, and the auto-scroll guard.
 *  <li>{@code setLayout} throwing {@code AWTError} including for null; {@code getLayout()} null.
 *  <li>The descope, pinned: no DOM listener is registered on the element.
 *  <li>Zero stub WARNs across the happy path.
 * </ol>
 *
 * <p>The {@code scrollTo} push is deliberately not asserted here — {@code executeJs} leaves
 * no server-side trace Karibu can read, the same stance {@code SListTest} and
 * {@code SJEditorPaneTest} take for their own shadow-DOM/executeJs seams. It is
 * browser-verified instead: {@code vaadin-scroller}'s shadow root is a bare {@code <slot>}
 * with {@code overflow: auto} on the host, so {@code this.scrollTop} addresses the real
 * scrolling box (probed 2026-08-25, recorded in SD_sscrollpane).
 */
class SScrollPaneTest extends AbstractKaribuTest {

    // --- Ctor + policy mapping ----------------------------------------

    @Test
    @DisplayName("AS_NEEDED maps to BOTH")
    void asNeededMapsToBoth() {
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        assertEquals(Scroller.ScrollDirection.BOTH, p.getScrollDirection());
        assertNoWarns();
    }

    @Test
    @DisplayName("NEVER maps to NONE")
    void neverMapsToNone() {
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_NEVER);
        assertEquals(Scroller.ScrollDirection.NONE, p.getScrollDirection());
        assertNoWarns();
    }

    @Test
    @DisplayName("ALWAYS falls back to BOTH and WARNs")
    void alwaysFallsBackToBothAndWarns() {
        // R_match_swing_errors sub-bucket (a), blocked-upstream: Vaadin's Scroller has no
        // force-show affordance. Same web component and same gap as D_scrollbar_policy_mapping.
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_ALWAYS);
        assertEquals(Scroller.ScrollDirection.BOTH, p.getScrollDirection());
        assertEquals(1, capturedWarns.size(), "expected exactly one WARN, got " + capturedWarns);
        assertTrue(capturedWarns.get(0).contains("SCROLLBARS_ALWAYS"), capturedWarns.get(0));
        capturedWarns.clear();
    }

    @Test
    @DisplayName("an illegal policy throws AWT's own message")
    void anIllegalPolicyThrowsAwtsOwnMessage() {
        for (int bad : List.of(3, -1, 20, 30)) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new SScrollPane(bad));
            assertEquals("illegal scrollbar display policy", e.getMessage());
        }
    }

    @Test
    @DisplayName("ScrollPaneConstants values are the safe direction and throw")
    void scrollPaneConstantsValuesAreTheSafeDirectionAndThrow() {
        // The disjointness that makes a Swing policy int loud rather than
        // silent: 20-22 / 30-32 hit no AWT arm. The unsafe direction —
        // Adjustable's 0/1/2 colliding with SCROLLBARS_* — is pinned on the
        // emulator side, where the ctor a migrator calls lives.
        assertThrows(IllegalArgumentException.class,
                () -> new SScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS));
    }

    // --- Single content slot ------------------------------------------

    @Test
    @DisplayName("setContent replaces rather than appends")
    void setContentReplacesRatherThanAppends() {
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        Span first = new Span("first");
        Span second = new Span("second");
        p.setContent(first);
        assertSame(first, p.getContent());
        p.setContent(second);
        assertSame(second, p.getContent(), "Scroller's single slot IS AWT's single-child rule");
        assertNoWarns();
    }

    // --- Auto-scroll guard (twin of SJScrollPane's) --------------------

    @Test
    @DisplayName("a Grid forces NONE and gets stretched")
    void aGridForcesNoneAndGetsStretched() {
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        Grid<String> grid = new Grid<>(String.class);
        p.setContent(grid);
        assertEquals(Scroller.ScrollDirection.NONE, p.getScrollDirection());
        assertEquals("100%", grid.getElement().getStyle().get("width"));
        assertEquals("100%", grid.getElement().getStyle().get("height"));
        assertNoWarns();
    }

    @Test
    @DisplayName("TextArea and a nested Scroller trip the guard too")
    void textAreaAndANestedScrollerTripTheGuardToo() {
        for (Component c : List.of(new TextArea(), new Scroller())) {
            SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
            p.setContent(c);
            assertEquals(Scroller.ScrollDirection.NONE, p.getScrollDirection());
        }
        assertNoWarns();
    }

    @Test
    @DisplayName("ordinary content leaves the direction alone")
    void ordinaryContentLeavesTheDirectionAlone() {
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        p.setContent(new Div());
        assertEquals(Scroller.ScrollDirection.BOTH, p.getScrollDirection());
        assertNoWarns();
    }

    @Test
    @DisplayName("null content clears the slot without tripping the guard")
    void nullContentClearsTheSlotWithoutTrippingTheGuard() {
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        p.setContent(new Span("x"));
        p.setContent(null);
        assertNull(p.getContent());
        assertEquals(Scroller.ScrollDirection.BOTH, p.getScrollDirection());
        assertNoWarns();
    }

    // --- setLayout is nailed shut --------------------------------------

    @Test
    @DisplayName("setLayout always throws AWTError, null included")
    void setLayoutAlwaysThrowsAwtErrorNullIncluded() {
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        AWTError e = assertThrows(AWTError.class, () -> p.setLayout(new FlowLayout()));
        assertEquals("ScrollPane controls layout", e.getMessage());
        // Even null: java.awt.ScrollPane.setLayout has no null arm, and the
        // ContainerMixin default would have happily stored it.
        assertThrows(AWTError.class, () -> p.setLayout(null));
        assertNull(p.getLayout(), "getLayout() answers null forever");
    }

    // --- The descope, pinned -------------------------------------------

    @Test
    @DisplayName("no DOM listener is registered — the descope, made a failing test")
    void noDomListenerIsRegistered() {
        // Server->browser only per SD_sscrollpane. If someone revives the read direction
        // they have to delete this test, which is the point: the descope should
        // not be reversible by accident. `scroll` is also the listener name to
        // look for, since it neither bubbles nor composes (probed 2026-08-25),
        // so a delegated UI-wide listener is not an alternative.
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        p.setContent(new Span("tall"));
        p.scrollTo(0, 200);
        ElementListenerMap listeners = p.getElement().getNode().getFeature(ElementListenerMap.class);
        assertTrue(
                listeners.getExpressions("scroll").isEmpty(),
                "no `scroll` DOM listener should be registered; found " + listeners.getExpressions("scroll"));
    }

    // --- Exit gate ------------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        SScrollPane p = new SScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        p.setContent(new Span("content"));
        p.scrollTo(0, 200);
        p.scrollTo(0, 0);
        p.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        p.getLayout();
        p.setEnabled(false);
        p.setEnabled(true);
        assertNoWarns("no stub WARNs expected from SScrollPane's happy path");
    }
}
