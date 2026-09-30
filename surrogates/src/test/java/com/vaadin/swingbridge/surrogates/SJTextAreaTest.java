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

import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SJTextArea. Covers:
 *
 * <ol>
 *  <li>Constructors (no-arg / String / rows+cols / String+rows+cols /
 *      Document / Document+String+rows+cols).
 *  <li>Default Document is PlainDocument (inherited from JTextComponentMixin).
 *  <li>R_swing_is_truth sync — programmatic setText writes the peer; peer setValue writes
 *      the Document; no infinite loop, no double-fire of DocumentListener.
 *  <li>setDocument swap fires "document" PCE; new doc text reaches peer.
 *  <li>setEditable propagates to peer.isReadOnly + fires "editable" PCE
 *      (inherited from JTextComponentMixin).
 *  <li>setRows / setColumns IAE on negative; write peer width "Nch" /
 *      minRows; no PCE fired (matches JDK).
 *  <li>setTabSize / setLineWrap / setWrapStyleWord fire PCE (field-only).
 *  <li>append / insert / replaceRange — including null no-op, null delete,
 *      IAE on out-of-range bounds.
 *  <li>getLineCount / getLineOfOffset / getLineStartOffset / getLineEndOffset
 *      — including BadLocationException on bad input.
 *  <li>getUIClassID == "TextAreaUI".
 *  <li>NO ActionListener-on-Enter wiring (JTextArea Enter inserts newline).
 *  <li>Drop-and-WARN: copy / cut / paste / setMargin / etc. (inherited).
 *  <li>Happy-path zero stub WARNs.
 * </ol>
 */
class SJTextAreaTest extends AbstractKaribuTest {

    /** The peer's own {@code getValue()}, reached past the surrogate's Swing API. */
    private static String peerValue(SJTextArea ta) {
        return ((TextArea) ta).getValue();
    }

    /** Collects PCEs for one named property. */
    private static List<PropertyChangeEvent> recordPces(SJTextArea ta, String property) {
        final List<PropertyChangeEvent> events = new ArrayList<>();
        ta.addPropertyChangeListener(property, events::add);
        return events;
    }

    /**
     * Tallies insert/remove notifications on the given Document. {@code changedUpdate}
     * is deliberately untracked — a PlainDocument never fires it.
     */
    private static void countEdits(Document doc, Counter inserts, Counter removes) {
        doc.addDocumentListener(new DocumentListener() {
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
    }

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("no-arg ctor leaves text empty and installs PlainDocument")
    void noArgCtorInstallsPlainDocument() {
        final SJTextArea ta = new SJTextArea();
        assertEquals("", ta.getText());
        assertInstanceOf(PlainDocument.class, ta.getDocument());
    }

    @Test
    @DisplayName("string ctor seeds text via the Document")
    void stringCtorSeedsText() {
        final SJTextArea ta = new SJTextArea("hello");
        assertEquals("hello", ta.getText());
        // R_swing_is_truth sync: Document text reaches the peer immediately.
        assertEquals("hello", peerValue(ta));
    }

    @Test
    @DisplayName("rows+cols ctor stores dimensions and writes peer CSS")
    void rowsColsCtorWritesCss() {
        final SJTextArea ta = new SJTextArea(5, 30);
        assertEquals(5, ta.getRows());
        assertEquals(30, ta.getColumns());
        assertEquals("var(--emul-layout-w, calc(30ch + 2em))", ta.getElement().getStyle().get("width"));
        assertEquals(5, ta.getMinRows());
    }

    @Test
    @DisplayName("string + rows + cols ctor combines all three")
    void stringRowsColsCtor() {
        final SJTextArea ta = new SJTextArea("hi", 3, 10);
        assertEquals("hi", ta.getText());
        assertEquals(3, ta.getRows());
        assertEquals(10, ta.getColumns());
    }

    @Test
    @DisplayName("Document ctor uses provided Document")
    void documentCtorUsesProvidedDocument() throws BadLocationException {
        final PlainDocument doc = new PlainDocument();
        doc.insertString(0, "preset", null);
        final SJTextArea ta = new SJTextArea(doc);
        assertSame(doc, ta.getDocument());
        assertEquals("preset", ta.getText());
        // R_swing_is_truth sync mirrors the preset content into the peer.
        assertEquals("preset", peerValue(ta));
    }

    @Test
    @DisplayName("Document + string + rows + cols ctor uses provided Document")
    void documentStringRowsColsCtor() {
        final PlainDocument doc = new PlainDocument();
        final SJTextArea ta = new SJTextArea(doc, "init", 4, 20);
        assertSame(doc, ta.getDocument());
        assertEquals("init", ta.getText());
        assertEquals(4, ta.getRows());
        assertEquals(20, ta.getColumns());
    }

    @Test
    @DisplayName("negative rows in ctor throws IAE per R_match_swing_errors")
    void negativeRowsCtorThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SJTextArea(-1, 0));
    }

    @Test
    @DisplayName("negative columns in ctor throws IAE per R_match_swing_errors")
    void negativeColumnsCtorThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SJTextArea(0, -1));
    }

    @Test
    @DisplayName("getUIClassID is TextAreaUI")
    void uiClassIdIsTextAreaUi() {
        assertEquals("TextAreaUI", new SJTextArea().getUIClassID());
    }

    // --- R_swing_is_truth Document↔peer sync (inherited from JTextComponentMixin) -

    @Test
    @DisplayName("setText writes peer value via R_swing_is_truth sync")
    void setTextWritesPeer() {
        final SJTextArea ta = new SJTextArea();
        ta.setText("world");
        assertEquals("world", peerValue(ta));
    }

    @Test
    @DisplayName("peer setValue writes Document via R_swing_is_truth sync")
    void peerSetValueWritesDocument() {
        // Simulates browser-side typing.
        final SJTextArea ta = new SJTextArea();
        ((TextArea) ta).setValue("typed");
        assertEquals("typed", ta.getText());
    }

    @Test
    @DisplayName("setText null normalises to empty per R_vaadin_first")
    void setTextNullNormalises() {
        final SJTextArea ta = new SJTextArea("seed");
        ta.setText(null);
        assertEquals("", ta.getText());
        assertEquals("", peerValue(ta));
    }

    @Test
    @DisplayName("setText fires DocumentListener exactly once")
    void setTextFiresDocumentListenerOnce() {
        // Regression for the R_swing_is_truth feedback-loop guard: setText must not
        // re-enter the Document via the peer's value-change listener.
        final SJTextArea ta = new SJTextArea();
        final Counter inserts = new Counter();
        final Counter removes = new Counter();
        countEdits(ta.getDocument(), inserts, removes);

        ta.setText("hi");

        inserts.assertEquals(1);
        removes.assertEquals(0);
    }

    @Test
    @DisplayName("peer-driven setValue fires DocumentListener once")
    void peerSetValueFiresDocumentListenerOnce() {
        final SJTextArea ta = new SJTextArea();
        final Counter inserts = new Counter();
        countEdits(ta.getDocument(), inserts, new Counter());

        ((TextArea) ta).setValue("browser");
        inserts.assertEquals(1);
    }

    // --- setDocument --------------------------------------------------

    @Test
    @DisplayName("setDocument swap fires document PCE")
    void setDocumentFiresPce() {
        final SJTextArea ta = new SJTextArea("first");
        final PlainDocument newDoc = new PlainDocument();
        final List<PropertyChangeEvent> events = recordPces(ta, "document");

        ta.setDocument(newDoc);

        assertEquals(1, events.size());
        assertSame(newDoc, ta.getDocument());
    }

    @Test
    @DisplayName("setDocument swap mirrors new content to peer")
    void setDocumentMirrorsContent() throws BadLocationException {
        final SJTextArea ta = new SJTextArea("old");
        final PlainDocument newDoc = new PlainDocument();
        newDoc.insertString(0, "fresh", null);
        ta.setDocument(newDoc);
        assertEquals("fresh", peerValue(ta));
    }

    @Test
    @DisplayName("setDocument detaches old listener")
    void setDocumentDetachesOldListener() throws BadLocationException {
        final SJTextArea ta = new SJTextArea();
        final Document oldDoc = ta.getDocument();
        ta.setDocument(new PlainDocument());

        oldDoc.insertString(0, "stale", null);
        assertNotEquals("stale", peerValue(ta));
    }

    // --- Editable -----------------------------------------------------

    @Test
    @DisplayName("setEditable propagates to peer readOnly")
    void setEditablePropagates() {
        final SJTextArea ta = new SJTextArea();
        assertTrue(ta.isEditable());
        ta.setEditable(false);
        assertFalse(ta.isEditable());
        assertTrue(((TextArea) ta).isReadOnly());
    }

    @Test
    @DisplayName("setEditable fires editable PCE")
    void setEditableFiresPce() {
        final SJTextArea ta = new SJTextArea();
        final List<PropertyChangeEvent> events = recordPces(ta, "editable");
        ta.setEditable(false);
        assertEquals(1, events.size());
    }

    // --- Rows ---------------------------------------------------------

    @Test
    @DisplayName("setRows negative throws IAE per R_match_swing_errors")
    void setRowsNegativeThrows() {
        final SJTextArea ta = new SJTextArea();
        assertThrows(IllegalArgumentException.class, () -> ta.setRows(-1));
    }

    @Test
    @DisplayName("setRows writes peer minRows")
    void setRowsWritesMinRows() {
        final SJTextArea ta = new SJTextArea();
        ta.setRows(7);
        assertEquals(7, ta.getRows());
        assertEquals(7, ta.getMinRows());
    }

    @Test
    @DisplayName("setRows fires no PCE per JDK")
    void setRowsFiresNoPce() {
        // JDK JTextArea fires no PCE for rows — just stores + invalidates.
        final SJTextArea ta = new SJTextArea();
        final List<PropertyChangeEvent> events = recordPces(ta, "rows");
        ta.setRows(10);
        assertEquals(0, events.size());
    }

    // --- Columns ------------------------------------------------------

    @Test
    @DisplayName("setColumns negative throws IAE per R_match_swing_errors")
    void setColumnsNegativeThrows() {
        final SJTextArea ta = new SJTextArea();
        assertThrows(IllegalArgumentException.class, () -> ta.setColumns(-1));
    }

    @Test
    @DisplayName("setColumns writes peer width and is silent on PCE")
    void setColumnsWritesWidth() {
        final SJTextArea ta = new SJTextArea();
        final List<PropertyChangeEvent> events = recordPces(ta, "columns");
        ta.setColumns(12);
        assertEquals(12, ta.getColumns());
        assertEquals("var(--emul-layout-w, calc(12ch + 2em))", ta.getElement().getStyle().get("width"));
        // Matches JDK JTextArea: no PCE fired for columns.
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("setColumns to zero clears peer width")
    void setColumnsZeroClearsWidth() {
        final SJTextArea ta = new SJTextArea("", 0, 5);
        ta.setColumns(0);
        assertNull(ta.getElement().getStyle().get("width"));
    }

    // --- tabSize / lineWrap / wrapStyleWord (PCE-bearing) ------------

    @Test
    @DisplayName("setTabSize fires PCE")
    void setTabSizeFiresPce() {
        final SJTextArea ta = new SJTextArea();
        final List<PropertyChangeEvent> events = recordPces(ta, "tabSize");
        ta.setTabSize(4);
        assertEquals(1, events.size());
        assertEquals(8, events.get(0).getOldValue());
        assertEquals(4, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("default tabSize is 8")
    void defaultTabSizeIsEight() {
        assertEquals(8, new SJTextArea().getTabSize());
    }

    @Test
    @DisplayName("setLineWrap fires PCE")
    void setLineWrapFiresPce() {
        final SJTextArea ta = new SJTextArea();
        final List<PropertyChangeEvent> events = recordPces(ta, "lineWrap");
        ta.setLineWrap(true);
        assertEquals(1, events.size());
        assertEquals(true, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("default lineWrap is false")
    void defaultLineWrapIsFalse() {
        assertFalse(new SJTextArea().getLineWrap());
    }

    @Test
    @DisplayName("setWrapStyleWord fires PCE")
    void setWrapStyleWordFiresPce() {
        final SJTextArea ta = new SJTextArea();
        final List<PropertyChangeEvent> events = recordPces(ta, "wrapStyleWord");
        ta.setWrapStyleWord(true);
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("default wrapStyleWord is false")
    void defaultWrapStyleWordIsFalse() {
        assertFalse(new SJTextArea().getWrapStyleWord());
    }

    // --- append / insert / replaceRange ------------------------------

    @Test
    @DisplayName("append adds at end of document and mirrors to peer")
    void appendAddsAtEnd() {
        final SJTextArea ta = new SJTextArea("hello ");
        ta.append("world");
        assertEquals("hello world", ta.getText());
        assertEquals("hello world", peerValue(ta));
    }

    @Test
    @DisplayName("append null is a no-op matching JDK")
    void appendNullIsNoop() {
        final SJTextArea ta = new SJTextArea("stable");
        ta.append(null);
        assertEquals("stable", ta.getText());
    }

    @Test
    @DisplayName("insert places text at the given offset")
    void insertAtOffset() {
        final SJTextArea ta = new SJTextArea("ac");
        ta.insert("b", 1);
        assertEquals("abc", ta.getText());
    }

    @Test
    @DisplayName("insert null is a no-op matching JDK")
    void insertNullIsNoop() {
        final SJTextArea ta = new SJTextArea("stable");
        ta.insert(null, 0);
        assertEquals("stable", ta.getText());
    }

    @Test
    @DisplayName("insert with out-of-range pos throws IAE")
    void insertOutOfRangeThrows() {
        final SJTextArea ta = new SJTextArea("abc");
        assertThrows(IllegalArgumentException.class, () -> ta.insert("x", -1));
        assertThrows(IllegalArgumentException.class, () -> ta.insert("x", 99));
    }

    @Test
    @DisplayName("replaceRange swaps a region of the document")
    void replaceRangeSwapsRegion() {
        final SJTextArea ta = new SJTextArea("hello world");
        ta.replaceRange("there", 6, 11);
        assertEquals("hello there", ta.getText());
    }

    @Test
    @DisplayName("replaceRange with null deletes the range")
    void replaceRangeNullDeletes() {
        // JDK: null str means "just delete" — same as
        // doc.remove(start, end-start).
        final SJTextArea ta = new SJTextArea("hello world");
        ta.replaceRange(null, 5, 11);
        assertEquals("hello", ta.getText());
    }

    @Test
    @DisplayName("replaceRange with bad bounds throws IAE")
    void replaceRangeBadBoundsThrows() {
        final SJTextArea ta = new SJTextArea("abc");
        assertThrows(IllegalArgumentException.class, () -> ta.replaceRange("x", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> ta.replaceRange("x", 2, 1));
        assertThrows(IllegalArgumentException.class, () -> ta.replaceRange("x", 1, 99));
    }

    // --- Line-oriented Document queries ------------------------------

    @Test
    @DisplayName("getLineCount counts document lines")
    void lineCountCountsLines() {
        final SJTextArea ta = new SJTextArea("one\ntwo\nthree");
        assertEquals(3, ta.getLineCount());
    }

    @Test
    @DisplayName("getLineOfOffset finds the containing line")
    void lineOfOffsetFindsLine() throws BadLocationException {
        final SJTextArea ta = new SJTextArea("one\ntwo\nthree");
        assertEquals(0, ta.getLineOfOffset(0));
        assertEquals(1, ta.getLineOfOffset(5));  // mid-line 'two'
        assertEquals(2, ta.getLineOfOffset(9));  // mid-line 'three'
    }

    @Test
    @DisplayName("getLineStartOffset and getLineEndOffset match PlainDocument lines")
    void lineStartAndEndOffsets() throws BadLocationException {
        final SJTextArea ta = new SJTextArea("one\ntwo\nthree");
        assertEquals(0, ta.getLineStartOffset(0));
        assertEquals(4, ta.getLineStartOffset(1));  // "one\n" is 4 chars
        assertEquals(8, ta.getLineStartOffset(2));
        // End offsets include the trailing newline (or EOF for the
        // last line), matching PlainDocument's element spec.
        assertEquals(4, ta.getLineEndOffset(0));
    }

    @Test
    @DisplayName("getLineOfOffset with bad offset throws BadLocationException")
    void lineOfOffsetBadOffsetThrows() {
        final SJTextArea ta = new SJTextArea("abc");
        assertThrows(BadLocationException.class, () -> ta.getLineOfOffset(-1));
        assertThrows(BadLocationException.class, () -> ta.getLineOfOffset(99));
    }

    @Test
    @DisplayName("getLineStartOffset with bad line throws BadLocationException")
    void lineStartOffsetBadLineThrows() {
        final SJTextArea ta = new SJTextArea("abc");
        assertThrows(BadLocationException.class, () -> ta.getLineStartOffset(-1));
        assertThrows(BadLocationException.class, () -> ta.getLineStartOffset(99));
    }

    @Test
    @DisplayName("getLineEndOffset with bad line throws BadLocationException")
    void lineEndOffsetBadLineThrows() {
        final SJTextArea ta = new SJTextArea("abc");
        assertThrows(BadLocationException.class, () -> ta.getLineEndOffset(-1));
        assertThrows(BadLocationException.class, () -> ta.getLineEndOffset(99));
    }

    // --- R_vaadin_first drops (inherited from JTextComponentMixin) ---------------

    @Test
    @DisplayName("copy WARNs and drops")
    void copyWarns() {
        final SJTextArea ta = new SJTextArea("x");
        ta.copy();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("cut WARNs and drops")
    void cutWarns() {
        final SJTextArea ta = new SJTextArea("x");
        ta.cut();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("paste WARNs and drops")
    void pasteWarns() {
        final SJTextArea ta = new SJTextArea();
        ta.paste();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("getCaretPosition is implemented, so it answers without WARNing")
    void caretPositionDoesNotWarn() {
        // Caret + selection are a real mechanism now; SJTextFieldCaretTest is
        // its exit gate. Kept here as the guard that it stays out of the
        // drop-and-WARN bucket above.
        final SJTextArea ta = new SJTextArea();
        assertEquals(0, ta.getCaretPosition());
        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }

    // --- Happy-path zero-WARN ----------------------------------------

    @Test
    @DisplayName("happy-path UI-functional surface is WARN-free")
    void happyPathIsWarnFree() throws BadLocationException {
        final SJTextArea ta = new SJTextArea("initial", 5, 20);
        ta.setText("edited\nmulti\nline");
        ta.append("\nmore");
        ta.insert("X", 0);
        ta.replaceRange("Y", 0, 1);
        ta.setTabSize(4);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);
        ta.setEditable(true);
        ta.setRows(8);
        ta.setColumns(30);
        ta.getLineCount();
        ta.getLineOfOffset(0);
        ta.getLineStartOffset(0);
        ta.getLineEndOffset(0);

        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }
}
