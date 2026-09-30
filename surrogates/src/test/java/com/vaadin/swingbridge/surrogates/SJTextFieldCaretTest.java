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

import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Caret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the caret + selection mechanism on {@code JTextComponentMixin}
 * (dot/mark in {@code TextStateStore}, {@code setSelectionRange} pushed to the peer).
 * Covers:
 *
 * <ol>
 *  <li>dot/mark defaults, and setCaretPosition collapsing the selection.
 *  <li>moveCaretPosition extending it, in both directions.
 *  <li>R_match_swing_errors — IllegalArgumentException outside the Document, matching the JDK.
 *  <li>select() clamping instead of throwing, per its own JDK contract.
 *  <li>getSelectedText's null-when-nothing-selected contract.
 *  <li>replaceSelection routing through the Document (listeners + peer sync).
 *  <li>Caret from getCaret() is never null and is a live view.
 *  <li>Text changes carry the caret to the end.
 *  <li>Happy path fires zero stub WARNs.
 * </ol>
 */
class SJTextFieldCaretTest extends AbstractKaribuTest {

    /** dot paired with mark, as the CaretListener sees them. */
    private record DotMark(int dot, int mark) {
    }

    // --- dot / mark ---------------------------------------------------

    @Test
    @DisplayName("fresh field has a collapsed caret at zero")
    void freshFieldHasACollapsedCaretAtZero() {
        SJTextField f = new SJTextField();
        assertEquals(0, f.getCaretPosition());
        assertEquals(0, f.getCaretMark());
        assertEquals(0, f.getSelectionStart());
        assertEquals(0, f.getSelectionEnd());
        assertNull(f.getSelectedText());
    }

    @Test
    @DisplayName("setCaretPosition collapses the selection")
    void setCaretPositionCollapsesTheSelection() {
        SJTextField f = new SJTextField("hello world");
        f.select(2, 7);
        assertEquals(2, f.getSelectionStart());
        assertEquals(7, f.getSelectionEnd());

        f.setCaretPosition(4);
        assertEquals(4, f.getCaretPosition());
        assertEquals(4, f.getCaretMark());
        assertEquals(4, f.getSelectionStart());
        assertEquals(4, f.getSelectionEnd());
        assertNull(f.getSelectedText());
    }

    @Test
    @DisplayName("moveCaretPosition extends the selection, leaving the mark")
    void moveCaretPositionExtendsTheSelection() {
        SJTextField f = new SJTextField("hello world");
        f.setCaretPosition(3);
        f.moveCaretPosition(8);

        assertEquals(8, f.getCaretPosition());
        assertEquals(3, f.getCaretMark());
        assertEquals(3, f.getSelectionStart());
        assertEquals(8, f.getSelectionEnd());
        assertEquals("lo wo", f.getSelectedText());
    }

    @Test
    @DisplayName("a backwards selection reports start before end")
    void aBackwardsSelectionReportsStartBeforeEnd() {
        SJTextField f = new SJTextField("hello world");
        f.setCaretPosition(8);
        f.moveCaretPosition(3);

        assertEquals(3, f.getCaretPosition());
        assertEquals(8, f.getCaretMark());
        assertEquals(3, f.getSelectionStart());
        assertEquals(8, f.getSelectionEnd());
        assertEquals("lo wo", f.getSelectedText());
    }

    // --- R_match_swing_errors: the JDK throws here, so we do --------------------------

    @Test
    @DisplayName("setCaretPosition past the document throws IAE")
    void setCaretPositionPastTheDocumentThrowsIae() {
        SJTextField f = new SJTextField("abc");
        assertThrows(IllegalArgumentException.class, () -> f.setCaretPosition(4));
        assertThrows(IllegalArgumentException.class, () -> f.setCaretPosition(-1));
        // The document's own length is a valid caret position — the caret
        // sits *after* the last character.
        f.setCaretPosition(3);
        assertEquals(3, f.getCaretPosition());
    }

    @Test
    @DisplayName("moveCaretPosition past the document throws IAE")
    void moveCaretPositionPastTheDocumentThrowsIae() {
        SJTextField f = new SJTextField("abc");
        assertThrows(IllegalArgumentException.class, () -> f.moveCaretPosition(9));
    }

    // --- select() clamps rather than throwing -----------------------

    @Test
    @DisplayName("select clamps out-of-range bounds")
    void selectClampsOutOfRangeBounds() {
        SJTextField f = new SJTextField("abcde");
        f.select(-5, 99);
        assertEquals(0, f.getSelectionStart());
        assertEquals(5, f.getSelectionEnd());
        assertEquals("abcde", f.getSelectedText());
    }

    @Test
    @DisplayName("select collapses when end precedes start")
    void selectCollapsesWhenEndPrecedesStart() {
        SJTextField f = new SJTextField("abcde");
        f.select(4, 1);
        assertEquals(4, f.getSelectionStart());
        assertEquals(4, f.getSelectionEnd());
        assertNull(f.getSelectedText());
    }

    @Test
    @DisplayName("selectAll spans the document")
    void selectAllSpansTheDocument() {
        SJTextField f = new SJTextField("abcde");
        f.selectAll();
        assertEquals(0, f.getSelectionStart());
        assertEquals(5, f.getSelectionEnd());
        assertEquals("abcde", f.getSelectedText());
    }

    @Test
    @DisplayName("setSelectionStart and setSelectionEnd move one edge each")
    void setSelectionStartAndEndMoveOneEdgeEach() {
        SJTextField f = new SJTextField("hello world");
        f.select(2, 8);

        f.setSelectionStart(4);
        assertEquals(4, f.getSelectionStart());
        assertEquals(8, f.getSelectionEnd());

        f.setSelectionEnd(6);
        assertEquals(4, f.getSelectionStart());
        assertEquals(6, f.getSelectionEnd());
    }

    // --- replaceSelection ------------------------------------------

    @Test
    @DisplayName("replaceSelection swaps the selected run and lands the caret after it")
    void replaceSelectionSwapsTheSelectedRun() {
        SJTextField f = new SJTextField("hello world");
        f.select(6, 11);
        f.replaceSelection("there");

        assertEquals("hello there", f.getText());
        assertEquals(11, f.getCaretPosition());
        assertEquals(11, f.getCaretMark());
    }

    @Test
    @DisplayName("replaceSelection with no selection inserts at the caret")
    void replaceSelectionWithNoSelectionInsertsAtTheCaret() {
        SJTextField f = new SJTextField("hello");
        f.setCaretPosition(5);
        f.replaceSelection("!");

        assertEquals("hello!", f.getText());
        assertEquals(6, f.getCaretPosition());
    }

    @Test
    @DisplayName("replaceSelection with null deletes the selection")
    void replaceSelectionWithNullDeletesTheSelection() {
        SJTextField f = new SJTextField("hello world");
        f.select(5, 11);
        f.replaceSelection(null);

        assertEquals("hello", f.getText());
        assertEquals(5, f.getCaretPosition());
    }

    @Test
    @DisplayName("replaceSelection reaches the peer, so the browser sees the new value")
    void replaceSelectionReachesThePeer() {
        SJTextField f = new SJTextField("hello world");
        f.select(0, 5);
        f.replaceSelection("goodbye");

        assertEquals("goodbye world", f.getValue());
    }

    @Test
    @DisplayName("replaceSelection fires DocumentListener, since it goes through the Document")
    void replaceSelectionFiresDocumentListener() {
        SJTextField f = new SJTextField("hello world");
        Counter inserts = new Counter();
        Counter removes = new Counter();
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                inserts.inc();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                removes.inc();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        f.select(0, 5);
        f.replaceSelection("bye");

        removes.assertEquals(1);
        inserts.assertEquals(1);
    }

    // --- getCaret() -------------------------------------------------

    @Test
    @DisplayName("getCaret is never null and reads through to dot and mark")
    void getCaretIsNeverNullAndReadsThrough() {
        SJTextField f = new SJTextField("hello world");
        f.setCaretPosition(3);
        f.moveCaretPosition(8);

        Caret caret = f.getCaret();
        assertNotNull(caret);
        assertEquals(8, caret.getDot());
        assertEquals(3, caret.getMark());
    }

    @Test
    @DisplayName("Caret setDot and moveDot write through")
    void caretSetDotAndMoveDotWriteThrough() {
        SJTextField f = new SJTextField("hello world");
        f.getCaret().setDot(4);
        assertEquals(4, f.getCaretPosition());
        assertEquals(4, f.getSelectionStart());

        f.getCaret().moveDot(9);
        assertEquals(9, f.getCaretPosition());
        assertEquals(4, f.getCaretMark());
        assertEquals("o wor", f.getSelectedText());
    }

    @Test
    @DisplayName("Caret setDot enforces the same R_match_swing_errors bounds as setCaretPosition")
    void caretSetDotEnforcesTheSameBounds() {
        SJTextField f = new SJTextField("abc");
        assertThrows(IllegalArgumentException.class, () -> f.getCaret().setDot(7));
    }

    @Test
    @DisplayName("Caret answers the browser-owned presentation without warning")
    void caretAnswersTheBrowserOwnedPresentation() {
        SJTextField f = new SJTextField("abc");
        Caret caret = f.getCaret();
        assertTrue(caret.isVisible());
        assertTrue(caret.isSelectionVisible());
        assertEquals(500, caret.getBlinkRate());
        assertNoWarns("reading browser-owned caret presentation should not WARN");
    }

    // --- text changes carry the caret ------------------------------

    @Test
    @DisplayName("setText leaves the caret at the end of the new text")
    void setTextLeavesTheCaretAtTheEnd() {
        SJTextField f = new SJTextField("hello");
        f.setCaretPosition(2);
        f.setText("much longer text");

        assertEquals(16, f.getCaretPosition());
        assertEquals(16, f.getCaretMark());
    }

    @Test
    @DisplayName("a peer-side value change carries the caret to the end")
    void aPeerSideValueChangeCarriesTheCaretToTheEnd() {
        SJTextField f = new SJTextField("hello");
        f.setCaretPosition(1);
        f.setValue("typed in the browser");

        assertEquals(20, f.getCaretPosition());
    }

    @Test
    @DisplayName("a shortened document clamps a stale caret instead of reporting past the end")
    void aShortenedDocumentClampsAStaleCaret() {
        SJTextField f = new SJTextField("hello world");
        f.select(6, 11);
        // Straight to the peer, bypassing setText — the path a browser edit
        // takes. dot/mark are not read back from the browser, so they are now
        // stale beyond the new text.
        f.setValue("hi");

        assertTrue(f.getCaretPosition() <= 2, "caret " + f.getCaretPosition() + " escaped the document");
        assertTrue(f.getSelectionEnd() <= 2);
    }

    // --- Caret listeners + the browser-side selection bridge --------

    @Test
    @DisplayName("a CaretListener fires on a server-side caret move")
    void aCaretListenerFiresOnAServerSideCaretMove() {
        SJTextField f = new SJTextField("hello world");
        List<DotMark> seen = new ArrayList<>();
        f.addCaretListener(e -> seen.add(new DotMark(e.getDot(), e.getMark())));

        f.setCaretPosition(4);
        f.moveCaretPosition(9);

        assertEquals(List.of(new DotMark(4, 4), new DotMark(9, 4)), seen);
    }

    @Test
    @DisplayName("a caret move that changes nothing fires nothing")
    void aCaretMoveThatChangesNothingFiresNothing() {
        SJTextField f = new SJTextField("hello world");
        Counter fired = new Counter();
        f.addCaretListener(e -> fired.inc());

        f.setCaretPosition(4);
        f.setCaretPosition(4);
        f.select(4, 4);

        fired.assertEquals(1);
    }

    @Test
    @DisplayName("getCaretListeners round-trips and removal stops delivery")
    void getCaretListenersRoundTripsAndRemovalStopsDelivery() {
        SJTextField f = new SJTextField("abc");
        Counter fired = new Counter();
        CaretListener l = e -> fired.inc();

        f.addCaretListener(l);
        assertEquals(1, f.getCaretListeners().length);
        f.setCaretPosition(1);
        fired.assertEquals(1);

        f.removeCaretListener(l);
        assertEquals(0, f.getCaretListeners().length);
        f.setCaretPosition(2);
        fired.assertEquals(1);
    }

    @Test
    @DisplayName("a Caret ChangeListener fires alongside, sourced at the Caret as Swing does")
    void aCaretChangeListenerFiresAlongside() {
        SJTextField f = new SJTextField("hello world");
        List<Object> sources = new ArrayList<>();
        f.getCaret().addChangeListener(e -> sources.add(e.getSource()));

        f.setCaretPosition(2);

        assertEquals(1, sources.size());
        assertSame(f.getCaret(), sources.get(0));
    }

    @Test
    @DisplayName("getCaret returns the same instance every call, so identity comparisons hold")
    void getCaretReturnsTheSameInstanceEveryCall() {
        SJTextField f = new SJTextField("abc");
        assertSame(f.getCaret(), f.getCaret());
    }

    @Test
    @DisplayName("a browser-side selection change updates dot-mark and fires")
    void aBrowserSideSelectionChangeUpdatesDotMarkAndFires() {
        SJTextField f = new SJTextField("hello world");
        List<DotMark> seen = new ArrayList<>();
        f.addCaretListener(e -> seen.add(new DotMark(e.getDot(), e.getMark())));

        // What the emul-selection bridge delivers when the user drag-selects
        // "lo wo" forwards. Karibu can't run the client JS, so the DOM event is
        // synthesized at the same seam Flow would deliver it to.
        fireSelection(f, 3, 8, "forward");

        assertEquals(8, f.getCaretPosition());
        assertEquals(3, f.getCaretMark());
        assertEquals("lo wo", f.getSelectedText());
        assertEquals(List.of(new DotMark(8, 3)), seen);
    }

    @Test
    @DisplayName("a backward browser selection puts the caret at the front")
    void aBackwardBrowserSelectionPutsTheCaretAtTheFront() {
        SJTextField f = new SJTextField("hello world");
        f.addCaretListener(e -> {
        });
        fireSelection(f, 3, 8, "backward");

        assertEquals(3, f.getCaretPosition());
        assertEquals(8, f.getCaretMark());
        assertEquals(3, f.getSelectionStart());
        assertEquals(8, f.getSelectionEnd());
    }

    @Test
    @DisplayName("the bridge is only wired once a listener wants it")
    void theBridgeIsOnlyWiredOnceAListenerWantsIt() {
        SJTextField f = new SJTextField("abc");
        assertFalse(hasSelectionListener(f), "a component nobody listens to should not pay for the bridge");

        f.addCaretListener(e -> {
        });
        assertTrue(hasSelectionListener(f));
    }

    @Test
    @DisplayName("an echo of our own push fires nothing")
    void anEchoOfOurOwnPushFiresNothing() {
        SJTextField f = new SJTextField("hello world");
        Counter fired = new Counter();
        f.addCaretListener(e -> fired.inc());

        f.select(2, 7);
        fired.assertEquals(1);

        // setSelectionRange lands in the browser, which answers with a
        // selectionchange carrying the offsets we just pushed. Server-side
        // dedupe is what stops that becoming a second event — and, with a
        // listener that moved the caret, an infinite loop.
        fireSelection(f, 2, 7, "forward");
        fired.assertEquals(1);
    }

    // --- Reports for a caller with its own caret ------------------------

    @Test
    @DisplayName("a selection report reaches the caller and leaves this component's caret alone")
    void aSelectionReportLeavesTheCaretAlone() {
        SJTextField f = new SJTextField("hello world");
        f.setCaretPosition(1);
        List<String> reports = new ArrayList<>();
        Counter ownEvents = new Counter();
        f.addCaretListener(e -> ownEvents.inc());
        f.addSelectionReportListener((start, end, backward) -> reports.add(start + ".." + end + " " + backward), 0);

        fireSelection(f, "emul-selection-now", 2, 5, "backward", null);

        assertEquals(List.of("2..5 true"), reports);
        assertEquals(1, f.getCaretPosition(), "the component's own dot/mark are not the report's to move");
        ownEvents.assertEquals(0);
    }

    @Test
    @DisplayName("a held report registration takes only the moves Flow flushes, the pre-edit one takes all")
    void aHeldRegistrationTakesOnlyFlushedMoves() {
        SJTextField f = new SJTextField("hello world");
        List<String> reports = new ArrayList<>();
        f.addSelectionReportListener((start, end, backward) -> reports.add(start + ".." + end), 1000);

        fireSelection(f, "emul-selection", 1, 1, "forward", null);
        fireSelection(f, "emul-selection", 2, 2, "forward", com.vaadin.flow.dom.DebouncePhase.TRAILING);
        fireSelection(f, "emul-selection-now", 3, 3, "forward", null);

        assertEquals(List.of("2..2", "3..3"), reports);
    }

    @Test
    @DisplayName("removing the report registration stops both reports")
    void removingTheRegistrationStopsBoth() {
        SJTextField f = new SJTextField("hello world");
        Counter reports = new Counter();
        com.vaadin.flow.shared.Registration r = f.addSelectionReportListener((start, end, backward) -> reports.inc(), 0);
        r.remove();

        fireSelection(f, "emul-selection", 1, 1, "forward", null);
        fireSelection(f, "emul-selection-now", 1, 1, "forward", null);

        reports.assertEquals(0);
    }

    @Test
    @DisplayName("renderSelection pushes the range and changes nothing server-side")
    void renderSelectionIsRenderOnly() {
        SJTextField f = new SJTextField("hello world");
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        ui.add(f);
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().dumpPendingJavaScriptInvocations();
        Counter fired = new Counter();
        f.addCaretListener(e -> fired.inc());
        int caretBefore = f.getCaretPosition();

        f.renderSelection(2, 7, true);
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

        List<List<Object>> pushed = new ArrayList<>();
        for (com.vaadin.flow.component.internal.PendingJavaScriptInvocation invocation
                : ui.getInternals().dumpPendingJavaScriptInvocations()) {
            if (invocation.getInvocation().getExpression().contains("setSelectionRange")) {
                pushed.add(invocation.getInvocation().getParameters().subList(0, 3));
            }
        }
        assertEquals(List.of(List.of(2, 7, "backward")), pushed);
        assertEquals(caretBefore, f.getCaretPosition());
        fired.assertEquals(0);
    }

    /** Delivers a {@code emul-selection} DOM event the way Flow would after the client dispatches one. */
    private static void fireSelection(SJTextField f, int start, int end, String dir) {
        fireSelection(f, "emul-selection", start, end, dir, null);
    }

    /** Delivers a selection DOM event of {@code type}; {@code phase} is a held report's debounce phase. */
    private static void fireSelection(SJTextField f, String type, int start, int end, String dir,
            com.vaadin.flow.dom.DebouncePhase phase) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("event.detail.start", start);
        payload.put("event.detail.end", end);
        payload.put("event.detail.dir", dir);
        if (phase != null) {
            payload.put(com.vaadin.flow.shared.JsonConstants.EVENT_DATA_PHASE, phase.getIdentifier());
        }
        f.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(f.getElement(), type, payload));
    }

    private static boolean hasSelectionListener(SJTextField f) {
        return !f.getElement().getNode().getFeature(ElementListenerMap.class)
                .getExpressions("emul-selection").isEmpty();
    }

    // --- WARN inventory --------------------------------------------

    @Test
    @DisplayName("the caret happy path fires no stub WARNs")
    void theCaretHappyPathFiresNoStubWarns() {
        SJTextField f = new SJTextField("hello world");
        f.setCaretPosition(3);
        f.moveCaretPosition(8);
        f.getSelectedText();
        f.replaceSelection("X");
        f.selectAll();
        f.getCaret().setDot(0);
        assertNoWarns("caret + selection are implemented; none of this should WARN");
    }
}
