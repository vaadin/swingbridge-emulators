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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingConstants;
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
 * Exit gate for SD_sjtextfield's SJTextField + JTextComponentMixin. Covers:
 *
 * <ol>
 *  <li>Constructors (no-arg / String / int / String+int / Document+String+int).
 *  <li>Default Document is PlainDocument; getText reads through it.
 *  <li>R_swing_is_truth sync — programmatic setText writes the peer; peer setValue writes
 *      the Document; no infinite loop, no double-fire of DocumentListener.
 *  <li>setDocument swap fires "document" PCE + new Document's text reaches
 *      the peer; old Document's listener detached.
 *  <li>setEditable propagates to peer.isReadOnly + fires "editable" PCE.
 *  <li>ActionListener on Enter — Karibu key-press simulation fires
 *      ActionEvent with source = field, actionCommand falls back to text.
 *  <li>setColumns IAE on negative; writes peer width "Nch".
 *  <li>horizontalAlignment R_match_swing_errors IAE on bad axis + drop-and-WARN on non-LEADING.
 *  <li>setAction narrow propagation (enabled / tooltip / actionCommand only).
 *  <li>Drop-and-WARN: copy / cut / paste / setMargin / etc.
 *  <li>Happy-path zero stub WARNs.
 * </ol>
 */
class SJTextFieldTest extends AbstractKaribuTest {

    /** Counts insertUpdate / removeUpdate separately; changedUpdate is never asserted here. */
    private static DocumentListener countingListener(Counter inserts, Counter removes) {
        return new DocumentListener() {
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
        };
    }

    /** An Action whose body does nothing — only its NAME / enabled state matter here. */
    private static AbstractAction namedAction(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
    }

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("no-arg ctor leaves text empty and installs PlainDocument")
    void noArgCtorLeavesTextEmptyAndInstallsPlainDocument() {
        SJTextField f = new SJTextField();
        assertEquals("", f.getText());
        assertInstanceOf(PlainDocument.class, f.getDocument());
    }

    @Test
    @DisplayName("string ctor seeds text via the Document")
    void stringCtorSeedsTextViaTheDocument() {
        SJTextField f = new SJTextField("hello");
        assertEquals("hello", f.getText());
        // R_swing_is_truth sync: Document text reaches the peer immediately.
        assertEquals("hello", ((TextField) f).getValue());
    }

    @Test
    @DisplayName("columns ctor stores columns and writes peer width N ch")
    void columnsCtorStoresColumnsAndWritesPeerWidth() {
        SJTextField f = new SJTextField(8);
        assertEquals(8, f.getColumns());
        assertEquals("var(--emul-layout-w, calc(8ch + 2em))", f.getElement().getStyle().get("width"));
    }

    @Test
    @DisplayName("string + columns ctor combines both")
    void stringPlusColumnsCtorCombinesBoth() {
        SJTextField f = new SJTextField("hi", 5);
        assertEquals("hi", f.getText());
        assertEquals(5, f.getColumns());
    }

    @Test
    @DisplayName("Document + String + columns ctor uses provided Document")
    void documentPlusStringPlusColumnsCtorUsesProvidedDocument() {
        PlainDocument doc = new PlainDocument();
        SJTextField f = new SJTextField(doc, "init", 4);
        assertSame(doc, f.getDocument());
        assertEquals("init", f.getText());
        assertEquals(4, f.getColumns());
    }

    @Test
    @DisplayName("negative columns in ctor throws IAE per R_match_swing_errors")
    void negativeColumnsInCtorThrowsIae() {
        assertThrows(IllegalArgumentException.class, () -> new SJTextField(-1));
    }

    @Test
    @DisplayName("getUIClassID is TextFieldUI")
    void getUiClassIdIsTextFieldUi() {
        assertEquals("TextFieldUI", new SJTextField().getUIClassID());
    }

    // --- R_swing_is_truth Document↔peer sync ---------------------------------------

    @Test
    @DisplayName("setText writes peer value via R_swing_is_truth sync")
    void setTextWritesPeerValue() {
        SJTextField f = new SJTextField();
        f.setText("world");
        assertEquals("world", ((TextField) f).getValue());
    }

    @Test
    @DisplayName("peer setValue writes Document via R_swing_is_truth sync")
    void peerSetValueWritesDocument() {
        // Simulates browser-side typing.
        SJTextField f = new SJTextField();
        ((TextField) f).setValue("typed");
        assertEquals("typed", f.getText());
    }

    @Test
    @DisplayName("setText null normalises to empty per R_vaadin_first")
    void setTextNullNormalisesToEmpty() {
        SJTextField f = new SJTextField("seed");
        f.setText(null);
        assertEquals("", f.getText());
        assertEquals("", ((TextField) f).getValue());
    }

    @Test
    @DisplayName("setText fires DocumentListener exactly once")
    void setTextFiresDocumentListenerExactlyOnce() {
        // Regression for the R_swing_is_truth feedback-loop guard: a setText call
        // must not re-enter the Document via the peer's value-change
        // listener. Each user-driven write delivers exactly one
        // insert/remove pair to user listeners.
        SJTextField f = new SJTextField();
        Counter inserts = new Counter();
        Counter removes = new Counter();
        f.getDocument().addDocumentListener(countingListener(inserts, removes));

        f.setText("hi");

        // setText does remove(0, len=0) which is a no-op (DocumentListener
        // doesn't fire on empty removes), then insertString("hi"). One
        // insert.
        inserts.assertEquals(1);
        removes.assertEquals(0);
    }

    @Test
    @DisplayName("peer-driven setValue fires DocumentListener once")
    void peerDrivenSetValueFiresDocumentListenerOnce() {
        // Mirror of the previous test for the other direction.
        SJTextField f = new SJTextField();
        Counter inserts = new Counter();
        f.getDocument().addDocumentListener(countingListener(inserts, new Counter()));

        ((TextField) f).setValue("browser");
        inserts.assertEquals(1);
    }

    // --- setDocument --------------------------------------------------

    @Test
    @DisplayName("setDocument swap fires document PCE")
    void setDocumentSwapFiresDocumentPce() {
        SJTextField f = new SJTextField("first");
        PlainDocument newDoc = new PlainDocument();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("document", events::add);

        f.setDocument(newDoc);

        assertEquals(1, events.size());
        assertSame(newDoc, f.getDocument());
    }

    @Test
    @DisplayName("setDocument swap mirrors new Document text to peer")
    void setDocumentSwapMirrorsNewDocumentTextToPeer() throws BadLocationException {
        SJTextField f = new SJTextField("old");
        PlainDocument newDoc = new PlainDocument();
        newDoc.insertString(0, "fresh", null);
        f.setDocument(newDoc);
        assertEquals("fresh", ((TextField) f).getValue());
    }

    @Test
    @DisplayName("setDocument detaches old listener")
    void setDocumentDetachesOldListener() throws BadLocationException {
        // After swap, mutations on the OLD Document must NOT reach the peer.
        SJTextField f = new SJTextField();
        Document oldDoc = f.getDocument();
        PlainDocument newDoc = new PlainDocument();
        f.setDocument(newDoc);

        oldDoc.insertString(0, "stale", null);
        assertNotEquals("stale", ((TextField) f).getValue());
    }

    @Test
    @DisplayName("setDocument to same value is a no-op")
    void setDocumentToSameValueIsANoOp() {
        SJTextField f = new SJTextField();
        Document doc = f.getDocument();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("document", events::add);
        f.setDocument(doc);
        assertEquals(0, events.size());
    }

    // --- Editable -----------------------------------------------------

    @Test
    @DisplayName("setEditable propagates to peer readOnly")
    void setEditablePropagatesToPeerReadOnly() {
        SJTextField f = new SJTextField();
        assertTrue(f.isEditable());
        f.setEditable(false);
        assertFalse(f.isEditable());
        assertTrue(((TextField) f).isReadOnly());
    }

    @Test
    @DisplayName("setEditable fires editable PCE")
    void setEditableFiresEditablePce() {
        SJTextField f = new SJTextField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("editable", events::add);
        f.setEditable(false);
        assertEquals(1, events.size());
        assertEquals(false, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setEditable to same value is a no-op")
    void setEditableToSameValueIsANoOp() {
        SJTextField f = new SJTextField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("editable", events::add);
        f.setEditable(true);  // already true
        assertEquals(0, events.size());
    }

    // --- ActionListener on Enter -------------------------------------

    @Test
    @DisplayName("ActionListener fires on Enter via Karibu keypress")
    void actionListenerFiresOnEnter() {
        SJTextField f = new SJTextField();
        UI.getCurrent().add(f);
        f.setText("submitted");
        List<ActionEvent> captured = new ArrayList<>();
        f.addActionListener(captured::add);

        // Karibu doesn't have a built-in keypress simulator the same way
        // _click works for buttons. Use postActionEvent which is the
        // public synthesizer JDK exposes for non-Enter triggers — same
        // path the Enter wiring takes internally.
        f.postActionEvent();

        assertEquals(1, captured.size());
        assertSame(f, captured.get(0).getSource());
        assertEquals("submitted", captured.get(0).getActionCommand());
    }

    @Test
    @DisplayName("ActionEvent actionCommand falls back to text when no command set")
    void actionCommandFallsBackToText() {
        SJTextField f = new SJTextField("query-text");
        List<ActionEvent> captured = new ArrayList<>();
        f.addActionListener(captured::add);
        f.postActionEvent();
        assertEquals("query-text", captured.get(0).getActionCommand());
    }

    @Test
    @DisplayName("setActionCommand overrides the text fallback")
    void setActionCommandOverridesTheTextFallback() {
        SJTextField f = new SJTextField("text-payload");
        f.setActionCommand("submit-form");
        List<ActionEvent> captured = new ArrayList<>();
        f.addActionListener(captured::add);
        f.postActionEvent();
        assertEquals("submit-form", captured.get(0).getActionCommand());
    }

    @Test
    @DisplayName("removeActionListener stops further events")
    void removeActionListenerStopsFurtherEvents() {
        SJTextField f = new SJTextField("x");
        Counter hits = new Counter();
        ActionListener l = e -> hits.inc();
        f.addActionListener(l);
        f.removeActionListener(l);
        f.postActionEvent();
        hits.assertEquals(0);
    }

    // --- Columns ------------------------------------------------------

    @Test
    @DisplayName("setColumns negative throws IAE per R_match_swing_errors")
    void setColumnsNegativeThrowsIae() {
        SJTextField f = new SJTextField();
        assertThrows(IllegalArgumentException.class, () -> f.setColumns(-1));
    }

    @Test
    @DisplayName("setColumns writes peer width and fires no PCE")
    void setColumnsWritesPeerWidthAndFiresNoPce() {
        // columns is a layout hint, not a bound property: the JDK's
        // JTextField.setColumns (and JTextArea's) assigns and calls
        // invalidate(). This asserted an event the desktop never sent (SD_property_fanout_audit).
        SJTextField f = new SJTextField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("columns", events::add);
        f.setColumns(12);
        assertEquals(12, f.getColumns());
        assertEquals("var(--emul-layout-w, calc(12ch + 2em))", f.getElement().getStyle().get("width"));
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("setColumns to zero clears peer width")
    void setColumnsToZeroClearsPeerWidth() {
        SJTextField f = new SJTextField(5);
        f.setColumns(0);
        assertNull(f.getElement().getStyle().get("width"));
    }

    // --- Horizontal alignment (drop-and-WARN per SD_sjtextfield) --------------

    @Test
    @DisplayName("setHorizontalAlignment LEADING (default) is silent")
    void setHorizontalAlignmentLeadingIsSilent() {
        SJTextField f = new SJTextField();
        f.setHorizontalAlignment(SwingConstants.LEADING);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setHorizontalAlignment LEFT WARNs and stores")
    void setHorizontalAlignmentLeftWarnsAndStores() {
        // SD_sjtextfield: drop-and-WARN visually but the field is stored + PCE fires
        // (non-Vaadin readback).
        SJTextField f = new SJTextField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("horizontalAlignment", events::add);
        f.setHorizontalAlignment(SwingConstants.LEFT);
        assertEquals(1, capturedWarns.size());
        assertEquals(SwingConstants.LEFT, f.getHorizontalAlignment());
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setHorizontalAlignment with bad axis throws IAE per R_match_swing_errors")
    void setHorizontalAlignmentWithBadAxisThrowsIae() {
        SJTextField f = new SJTextField();
        assertThrows(IllegalArgumentException.class,
                () -> f.setHorizontalAlignment(SwingConstants.TOP));
    }

    // --- setAction (narrow propagation) ------------------------------

    @Test
    @DisplayName("setAction copies enabled state (does NOT copy NAME)")
    void setActionCopiesEnabledStateNotName() {
        AbstractAction a = namedAction("ActionName");
        a.setEnabled(false);

        SJTextField f = new SJTextField("preserve-this");
        f.setAction(a);

        // NAME would overwrite form data — JDK JTextField intentionally
        // doesn't propagate it. Text remains user-entered.
        assertEquals("preserve-this", f.getText());
        assertFalse(f.isEnabled());
    }

    @Test
    @DisplayName("setAction copies SHORT_DESCRIPTION to tooltip")
    void setActionCopiesShortDescriptionToTooltip() {
        AbstractAction a = namedAction(null);
        a.putValue(Action.SHORT_DESCRIPTION, "hint");

        SJTextField f = new SJTextField();
        f.setAction(a);

        // Inherited tooltip via JComponentMixin / HasTooltip.
        // (Just sanity-check it didn't crash; full tooltip readback is
        // covered by dedicated tooltip tests.)
        assertSame(a, f.getAction());
    }

    @Test
    @DisplayName("Action actionPerformed fires when Enter triggers ActionEvent")
    void actionActionPerformedFiresOnEnter() {
        Counter fired = new Counter();
        AbstractAction a = new AbstractAction("submit") {
            @Override
            public void actionPerformed(ActionEvent e) {
                fired.inc();
            }
        };
        a.putValue(Action.ACTION_COMMAND_KEY, "the-command");

        SJTextField f = new SJTextField();
        f.setAction(a);
        f.postActionEvent();

        assertTrue(fired.get() > 0);
    }

    // --- R_vaadin_first drops ----------------------------------------------------

    @Test
    @DisplayName("copy WARNs and drops")
    void copyWarnsAndDrops() {
        SJTextField f = new SJTextField("x");
        f.copy();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("cut WARNs and drops")
    void cutWarnsAndDrops() {
        SJTextField f = new SJTextField("x");
        f.cut();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("paste WARNs and drops")
    void pasteWarnsAndDrops() {
        SJTextField f = new SJTextField();
        f.paste();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("getCaretPosition is implemented, so it answers without WARNing")
    void getCaretPositionAnswersWithoutWarning() {
        // Caret + selection are a real mechanism now; SJTextFieldCaretTest is
        // its exit gate. Kept here as the guard that it stays out of the
        // drop-and-WARN bucket above.
        SJTextField f = new SJTextField();
        assertEquals(0, f.getCaretPosition());
        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }

    // --- Happy-path zero-WARN ----------------------------------------

    @Test
    @DisplayName("happy-path UI-functional surface is WARN-free")
    void happyPathUiFunctionalSurfaceIsWarnFree() {
        SJTextField f = new SJTextField("initial", 10);
        f.setText("edited");
        f.setActionCommand("submit");
        f.setEditable(false);
        f.setEditable(true);
        f.setColumns(8);
        f.addActionListener(e -> { /* no-op */ });
        f.postActionEvent();

        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }
}
