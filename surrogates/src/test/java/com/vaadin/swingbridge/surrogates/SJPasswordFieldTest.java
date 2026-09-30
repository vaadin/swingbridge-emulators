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
import com.vaadin.flow.component.textfield.PasswordField;
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
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SJPasswordField. Covers:
 *
 * <ol>
 *  <li>Constructors (no-arg / String / int / String+int / Document+String+int).
 *  <li>Default Document is PlainDocument (inherited from JTextComponentMixin).
 *  <li>echoChar default '*', setEchoChar PCE, echoCharIsSet contract.
 *  <li>getText() returns cleartext (JDK-measured), unaffected by echoChar;
 *      getPassword() returns the same content as a fresh char[].
 *  <li>R_swing_is_truth sync — setText writes peer; peer setValue writes Document.
 *  <li>setEditable / isEditable inherited from mixin.
 *  <li>ActionListener on Enter (postActionEvent shortcut); ActionEvent payload
 *      is the entered text.
 *  <li>setColumns IAE on negative; writes peer width "Nch".
 *  <li>horizontalAlignment R_match_swing_errors IAE on bad axis + drop-and-WARN on non-LEADING.
 *  <li>setAction narrow propagation (enabled / tooltip / actionCommand only;
 *      NAME does NOT overwrite text).
 *  <li>copy / cut WARN-and-drop (clipboard refusal per JDK).
 *  <li>getUIClassID == "PasswordFieldUI".
 *  <li>Happy-path zero stub WARNs on the UI-functional surface.
 * </ol>
 */
class SJPasswordFieldTest extends AbstractKaribuTest {

    /** Read the Document directly, independent of the {@code getText()} path. */
    private static String cleartext(SJPasswordField f) throws BadLocationException {
        return f.getDocument().getText(0, f.getDocument().getLength());
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
        SJPasswordField f = new SJPasswordField();
        assertEquals("", f.getText());
        assertInstanceOf(PlainDocument.class, f.getDocument());
    }

    @Test
    @DisplayName("string ctor seeds Document with cleartext")
    void stringCtorSeedsDocumentWithCleartext() throws BadLocationException {
        SJPasswordField f = new SJPasswordField("hunter2");
        assertEquals("hunter2", cleartext(f));
        assertEquals("hunter2", f.getText());
        // R_swing_is_truth sync: the value reaches the peer.
        assertEquals("hunter2", ((PasswordField) f).getValue());
    }

    @Test
    @DisplayName("columns ctor stores columns and writes peer width N ch")
    void columnsCtorStoresColumnsAndWritesPeerWidth() {
        SJPasswordField f = new SJPasswordField(8);
        assertEquals(8, f.getColumns());
        assertEquals("var(--emul-layout-w, calc(8ch + 2em))", f.getElement().getStyle().get("width"));
    }

    @Test
    @DisplayName("string + columns ctor combines both")
    void stringPlusColumnsCtorCombinesBoth() throws BadLocationException {
        SJPasswordField f = new SJPasswordField("pw", 5);
        assertEquals("pw", cleartext(f));
        assertEquals(5, f.getColumns());
    }

    @Test
    @DisplayName("Document + String + columns ctor uses provided Document")
    void documentPlusStringPlusColumnsCtorUsesProvidedDocument() throws BadLocationException {
        PlainDocument doc = new PlainDocument();
        SJPasswordField f = new SJPasswordField(doc, "init", 4);
        assertSame(doc, f.getDocument());
        assertEquals("init", cleartext(f));
        assertEquals(4, f.getColumns());
    }

    @Test
    @DisplayName("negative columns in ctor throws IAE per R_match_swing_errors")
    void negativeColumnsInCtorThrowsIae() {
        assertThrows(IllegalArgumentException.class, () -> new SJPasswordField(-1));
    }

    @Test
    @DisplayName("getUIClassID is PasswordFieldUI")
    void getUiClassIdIsPasswordFieldUi() {
        assertEquals("PasswordFieldUI", new SJPasswordField().getUIClassID());
    }

    // --- echoChar (state round-trip only) ----------------------------

    @Test
    @DisplayName("default echoChar is asterisk")
    void defaultEchoCharIsAsterisk() {
        assertEquals('*', new SJPasswordField().getEchoChar());
        assertTrue(new SJPasswordField().echoCharIsSet());
    }

    @Test
    @DisplayName("getText returns cleartext, never a mask")
    void getTextReturnsCleartext() {
        // Measured against real JDK 25: `new JPasswordField(); setText("ADMIN");
        // getText()` → "ADMIN". The JDK's body is `return super.getText()` and
        // echoChar is read only by BasicPasswordFieldUI's view. SB-Emulators masked here
        // from 2026-04-22 to 2026-09-01 — see SD_sjpasswordfield's correction.
        SJPasswordField f = new SJPasswordField("abc");
        assertEquals("abc", f.getText());
    }

    @Test
    @DisplayName("echoChar does not affect getText in either direction")
    void echoCharDoesNotAffectGetText() {
        SJPasswordField f = new SJPasswordField("abc");
        f.setEchoChar((char) 0);
        assertFalse(f.echoCharIsSet());
        assertEquals("abc", f.getText());
        f.setEchoChar('*');
        assertTrue(f.echoCharIsSet());
        assertEquals("abc", f.getText());
    }

    @Test
    @DisplayName("setEchoChar fires no PropertyChangeEvent")
    void setEchoCharFiresNoPce() {
        // The JDK's setEchoChar assigns, then repaint() + revalidate(). No
        // bound property (SD_property_fanout_audit) — this asserted an invented event.
        SJPasswordField f = new SJPasswordField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("echoChar", events::add);
        f.setEchoChar('#');
        assertEquals('#', f.getEchoChar());
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("setEchoChar to zero WARNs (visual mask state divergence)")
    void setEchoCharToZeroWarns() {
        // R_match_swing_errors sub-bucket (c): Vaadin PasswordField has no server-side
        // toggle for the input element's `type` attribute, so the JDK
        // contract `echoChar=0 → input visually unmasked` can't be
        // honored. WARN so migrated apps relying on this UX discover
        // the no-op in the log.
        SJPasswordField f = new SJPasswordField("abc");
        f.setEchoChar((char) 0);
        assertEquals(1, capturedWarns.size(), "Warns: " + capturedWarns);
        assertTrue(capturedWarns.get(0).contains("setEchoChar"));
    }

    @Test
    @DisplayName("setEchoChar back to non-zero from zero also WARNs (boundary cross)")
    void setEchoCharBackToNonZeroAlsoWarns() {
        SJPasswordField f = new SJPasswordField("abc");
        f.setEchoChar((char) 0);   // boundary: '*' → 0 — WARN
        f.setEchoChar('*');        // boundary: 0 → '*' — WARN
        assertEquals(2, capturedWarns.size(), "Warns: " + capturedWarns);
    }

    @Test
    @DisplayName("setEchoChar between two non-zero glyphs is silent (glyph-only divergence)")
    void setEchoCharBetweenTwoNonZeroGlyphsIsSilent() {
        // The browser renders `<input type=password>` with its native
        // mask glyph regardless of echoChar's value, so swapping
        // between two non-zero echo chars leaves the visual identical
        // (both still masked). Documented divergence per setEchoChar's
        // javadoc; not WARNed because the user-visible impact is small.
        SJPasswordField f = new SJPasswordField("abc");
        f.setEchoChar('#');        // '*' → '#' — both non-zero, no WARN
        f.setEchoChar('@');        // '#' → '@' — both non-zero, no WARN
        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }

    @Test
    @DisplayName("a custom echoChar leaves getText cleartext")
    void customEchoCharLeavesGetTextCleartext() {
        SJPasswordField f = new SJPasswordField("xyz");
        f.setEchoChar('#');
        assertEquals("xyz", f.getText());
    }

    @Test
    @DisplayName("getText offs+len overload returns the cleartext range")
    void getTextOffsLenOverloadReturnsCleartextRange() throws BadLocationException {
        SJPasswordField f = new SJPasswordField("abcdef");
        assertEquals("bc", f.getText(1, 2));
    }

    @Test
    @DisplayName("getPassword returns cleartext char array")
    void getPasswordReturnsCleartextCharArray() {
        SJPasswordField f = new SJPasswordField("abc");
        assertEquals("abc", new String(f.getPassword()));
    }

    @Test
    @DisplayName("getPassword on empty field returns empty array")
    void getPasswordOnEmptyFieldReturnsEmptyArray() {
        assertEquals(0, new SJPasswordField().getPassword().length);
    }

    @Test
    @DisplayName("getPassword returns a fresh copy each call")
    void getPasswordReturnsAFreshCopyEachCall() {
        SJPasswordField f = new SJPasswordField("secret");
        char[] a = f.getPassword();
        char[] b = f.getPassword();
        // Different array instances — caller can zero one without affecting
        // a subsequent reader.
        assertNotSame(a, b);
        assertEquals(new String(a), new String(b));
    }

    // --- R_swing_is_truth Document↔peer sync (inherited from JTextComponentMixin) -

    @Test
    @DisplayName("setText writes peer value via R_swing_is_truth sync (cleartext)")
    void setTextWritesPeerValueCleartext() {
        SJPasswordField f = new SJPasswordField();
        f.setText("world");
        // Peer always sees cleartext per readDocument bypass.
        assertEquals("world", ((PasswordField) f).getValue());
    }

    @Test
    @DisplayName("peer setValue writes Document via R_swing_is_truth sync")
    void peerSetValueWritesDocument() throws BadLocationException {
        // Simulates browser-side typing.
        SJPasswordField f = new SJPasswordField();
        ((PasswordField) f).setValue("typed");
        assertEquals("typed", cleartext(f));
    }

    @Test
    @DisplayName("setText null normalises to empty per R_vaadin_first")
    void setTextNullNormalisesToEmpty() {
        SJPasswordField f = new SJPasswordField("seed");
        f.setText(null);
        assertEquals(0, f.getDocument().getLength());
        assertEquals("", ((PasswordField) f).getValue());
    }

    @Test
    @DisplayName("setDocument swap fires document PCE")
    void setDocumentSwapFiresDocumentPce() {
        SJPasswordField f = new SJPasswordField("first");
        PlainDocument newDoc = new PlainDocument();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("document", events::add);
        f.setDocument(newDoc);
        assertEquals(1, events.size());
        assertSame(newDoc, f.getDocument());
    }

    // --- Editable -----------------------------------------------------

    @Test
    @DisplayName("setEditable propagates to peer readOnly")
    void setEditablePropagatesToPeerReadOnly() {
        SJPasswordField f = new SJPasswordField();
        assertTrue(f.isEditable());
        f.setEditable(false);
        assertFalse(f.isEditable());
        assertTrue(((PasswordField) f).isReadOnly());
    }

    // --- ActionListener on Enter -------------------------------------

    @Test
    @DisplayName("postActionEvent fires ActionEvent with cleartext command")
    void postActionEventFiresActionEventWithCleartextCommand() {
        // JDK JTextField's actionCommand fallback, inherited unchanged by
        // JPasswordField: null actionCommand → getText(), which is the
        // entered value.
        SJPasswordField f = new SJPasswordField();
        UI.getCurrent().add(f);
        f.setText("submitted");
        List<ActionEvent> captured = new ArrayList<>();
        f.addActionListener(captured::add);

        f.postActionEvent();

        assertEquals(1, captured.size());
        assertSame(f, captured.get(0).getSource());
        assertEquals("submitted", captured.get(0).getActionCommand());
    }

    @Test
    @DisplayName("setActionCommand overrides the cleartext fallback")
    void setActionCommandOverridesTheCleartextFallback() {
        SJPasswordField f = new SJPasswordField("text-payload");
        f.setActionCommand("submit-form");
        List<ActionEvent> captured = new ArrayList<>();
        f.addActionListener(captured::add);
        f.postActionEvent();
        assertEquals("submit-form", captured.get(0).getActionCommand());
    }

    @Test
    @DisplayName("removeActionListener stops further events")
    void removeActionListenerStopsFurtherEvents() {
        SJPasswordField f = new SJPasswordField("x");
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
        SJPasswordField f = new SJPasswordField();
        assertThrows(IllegalArgumentException.class, () -> f.setColumns(-1));
    }

    @Test
    @DisplayName("setColumns writes peer width and fires no PCE")
    void setColumnsWritesPeerWidthAndFiresNoPce() {
        // columns is a layout hint, not a bound property: the JDK's
        // JTextField.setColumns (and JTextArea's) assigns and calls
        // invalidate(). This asserted an event the desktop never sent (SD_property_fanout_audit).
        SJPasswordField f = new SJPasswordField();
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
        SJPasswordField f = new SJPasswordField(5);
        f.setColumns(0);
        assertNull(f.getElement().getStyle().get("width"));
    }

    // --- Horizontal alignment (drop-and-WARN per SD_sjtextfield) --------------

    @Test
    @DisplayName("setHorizontalAlignment LEADING (default) is silent")
    void setHorizontalAlignmentLeadingIsSilent() {
        SJPasswordField f = new SJPasswordField();
        f.setHorizontalAlignment(SwingConstants.LEADING);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setHorizontalAlignment LEFT WARNs and stores")
    void setHorizontalAlignmentLeftWarnsAndStores() {
        SJPasswordField f = new SJPasswordField();
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
        SJPasswordField f = new SJPasswordField();
        assertThrows(IllegalArgumentException.class,
                () -> f.setHorizontalAlignment(SwingConstants.TOP));
    }

    // --- setAction (narrow propagation) ------------------------------

    @Test
    @DisplayName("setAction copies enabled state (does NOT copy NAME)")
    void setActionCopiesEnabledStateNotName() throws BadLocationException {
        AbstractAction a = namedAction("ActionName");
        a.setEnabled(false);

        SJPasswordField f = new SJPasswordField("preserve-this");
        f.setAction(a);

        // NAME would overwrite the user-entered password — JDK
        // JTextField intentionally doesn't propagate it.
        assertEquals("preserve-this", cleartext(f));
        assertFalse(f.isEnabled());
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

        SJPasswordField f = new SJPasswordField();
        f.setAction(a);
        f.postActionEvent();

        assertTrue(fired.get() > 0);
    }

    // --- R_vaadin_first drops ----------------------------------------------------

    @Test
    @DisplayName("copy WARNs and drops (clipboard refusal)")
    void copyWarnsAndDrops() {
        SJPasswordField f = new SJPasswordField("x");
        f.copy();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("cut WARNs and drops (clipboard refusal)")
    void cutWarnsAndDrops() {
        SJPasswordField f = new SJPasswordField("x");
        f.cut();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("paste WARNs and drops")
    void pasteWarnsAndDrops() {
        SJPasswordField f = new SJPasswordField();
        f.paste();
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("getCaretPosition is implemented, so it answers without WARNing")
    void getCaretPositionAnswersWithoutWarning() {
        // Caret + selection are a real mechanism now; SJTextFieldCaretTest is
        // its exit gate. Kept here as the guard that it stays out of the
        // drop-and-WARN bucket above.
        SJPasswordField f = new SJPasswordField();
        assertEquals(0, f.getCaretPosition());
        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }

    // --- Happy-path zero-WARN ----------------------------------------

    @Test
    @DisplayName("happy-path UI-functional surface is WARN-free")
    void happyPathUiFunctionalSurfaceIsWarnFree() {
        SJPasswordField f = new SJPasswordField("initial", 10);
        f.setText("edited");
        f.setEchoChar('#');
        f.setActionCommand("submit");
        f.setEditable(false);
        f.setEditable(true);
        f.setColumns(8);
        f.addActionListener(e -> { /* no-op */ });
        f.postActionEvent();
        // Both read paths are happy-path and must not WARN.
        f.getText();
        f.getPassword();

        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }
}
