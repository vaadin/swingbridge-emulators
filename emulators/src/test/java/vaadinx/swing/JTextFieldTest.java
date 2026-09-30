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
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJTextField;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JTextFieldTest extends AbstractKaribuTest {

    /** An AbstractAction whose actionPerformed does nothing — every Action test below wants one. */
    private static Action action(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
    }

    /** The peer, as the Vaadin TextField every value assertion below reads through. */
    private static TextField peerOf(JTextField tf) {
        return (TextField) tf.getPeer();
    }

    /** A DocumentListener that bumps {@code n} on every one of the three callbacks. */
    private static DocumentListener countingListener(Counter n) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                n.inc();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                n.inc();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                n.inc();
            }
        };
    }

    @Test
    @DisplayName("can instantiate with TextField peer")
    void canInstantiateWithTextFieldPeer() {
        JTextField tf = new JTextField();
        // SJTextField IS-A TextField per SD_sjtextfield — Karibu locators that
        // find by TextField.class still resolve.
        assertInstanceOf(TextField.class, tf.getPeer());
    }

    @Test
    @DisplayName("getFont is non-null so the NetBeans deriveFont idiom does not NPE")
    void getFontIsNonNullForTheDeriveFontIdiom() {
        // Regression guard for the jlawyer-shape crash: initComponents() ran
        // txtName.setFont(txtName.getFont().deriveFont(txtName.getFont()
        // .getStyle() | BOLD, txtName.getFont().getSize()-2)) and NPE'd
        // because getFont() returned null on a freshly-constructed field.
        JTextField tf = new JTextField();
        tf.setFont(tf.getFont().deriveFont(tf.getFont().getStyle() | Font.BOLD, tf.getFont().getSize() - 2f));
        assertTrue(tf.getFont().isBold());
        assertEquals(10, tf.getFont().getSize());
    }

    @Test
    @DisplayName("peer is an SJTextField surrogate")
    void peerIsAnSjTextFieldSurrogate() {
        JTextField tf = new JTextField();
        assertInstanceOf(SJTextField.class, tf.getPeer());
    }

    @Test
    @DisplayName("two-layer R_swing_is_truth sync delivers consistent text on both layers")
    void twoLayerSyncDeliversConsistentText() {
        // SD_sjtextfield: emulator's Document and surrogate's Document are
        // independent objects, both reach the same value via the
        // two-layer R_swing_is_truth sync. Programmatic emulator setText writes the
        // emulator's Document; emulator's docToPeer pushes to peer.value;
        // surrogate's ValueChangeListener mirrors to surrogate's Document;
        // both ends report the same text. Vaadin's idempotent setValue
        // short-circuits the surrogate's docToPeer echo back to the peer
        // (no infinite loop).
        JTextField tf = new JTextField();
        tf.setText("from-emulator");

        // Emulator-side getText reads emulator's Document.
        assertEquals("from-emulator", tf.getText());
        // Surrogate-side getText reads surrogate's Document.
        assertEquals("from-emulator", ((SJTextField) tf.getPeer()).getText());
        // Vaadin peer value is also in sync.
        assertEquals("from-emulator", peerOf(tf).getValue());
    }

    @Test
    @DisplayName("peer value change reaches both layers' Documents")
    void peerValueChangeReachesBothDocuments() {
        // Browser-side typing simulation: setting peer.value should
        // reach BOTH the emulator's Document AND the surrogate's Document.
        // Each layer's ValueChangeListener fires independently.
        JTextField tf = new JTextField();
        peerOf(tf).setValue("browser-typed");
        assertEquals("browser-typed", tf.getText());
        assertEquals("browser-typed", ((SJTextField) tf.getPeer()).getText());
    }

    @Test
    @DisplayName("string ctor seeds text through the Document")
    void stringCtorSeedsTextThroughTheDocument() throws BadLocationException {
        JTextField tf = new JTextField("hello");
        assertEquals("hello", tf.getText());
        assertEquals("hello", tf.getDocument().getText(0, tf.getDocument().getLength()));
    }

    @Test
    @DisplayName("setText mirrors into the peer's value")
    void setTextMirrorsIntoThePeerValue() {
        // R_swing_is_truth setter→peer path: Document mutates, internal
        // DocumentListener pushes into peer.setValue under
        // preventPeerEvents so no feedback loop.
        JTextField tf = new JTextField();
        tf.setText("hello");
        assertEquals("hello", peerOf(tf).getValue());
    }

    @Test
    @DisplayName("peer value change mirrors into the Document")
    void peerValueChangeMirrorsIntoTheDocument() {
        // R_swing_is_truth peer→setter path: setting the peer's value fires
        // ValueChangeEvent (EAGER mode), which our listener uses to
        // mutate the Document. getText then reflects the change.
        JTextField tf = new JTextField("old");
        peerOf(tf).setValue("new");
        assertEquals("new", tf.getText());
    }

    @Test
    @DisplayName("user DocumentListener fires on peer-initiated value change")
    void userDocumentListenerFiresOnPeerInitiatedChange() {
        // The R_swing_is_truth point: a migrated app registering a DocumentListener
        // on the JTextField's Document sees events regardless of
        // whether the mutation came from setText or from the user
        // typing in the browser.
        JTextField tf = new JTextField();
        List<String> events = new ArrayList<>();
        tf.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                events.add("insert");
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                events.add("remove");
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                events.add("changed");
            }
        });

        peerOf(tf).setValue("typed");

        // Starts empty; remove is no-op (length 0), insert fires for
        // the new text. Whether remove also fires depends on PlainDocument's
        // zero-length behavior — assert that at least the insert landed.
        assertTrue(events.contains("insert"), "expected insert event; got " + events);
    }

    @Test
    @DisplayName("setText does not cause infinite feedback loop")
    void setTextDoesNotCauseInfiniteFeedbackLoop() {
        // Regression for the preventPeerEvents guard. setText mutates
        // Document → DocumentListener pushes to peer → peer fires
        // ValueChangeEvent synchronously → our peer listener would
        // round-trip back into the Document and fire again. The flag
        // breaks the loop.
        JTextField tf = new JTextField();
        Counter mutations = new Counter();
        tf.getDocument().addDocumentListener(countingListener(mutations));

        tf.setText("once");

        // Exactly 1 insert (the document started empty; remove is a
        // no-op on length-0 and does not fire). If the feedback loop
        // were unguarded, we'd see mutations > 1.
        mutations.assertEquals(1);
    }

    @Test
    @DisplayName("setDocument re-hooks the peer listener on the new Document")
    void setDocumentReHooksThePeerListener() throws BadLocationException {
        // After setDocument, mutations on the new Document must still
        // propagate to the peer, and the old Document must not.
        JTextField tf = new JTextField("initial");
        PlainDocument newDoc = new PlainDocument();
        newDoc.insertString(0, "from-new-doc", null);

        tf.setDocument(newDoc);

        // setDocument pushes the new Document's text to the peer.
        assertEquals("from-new-doc", peerOf(tf).getValue());

        // Further mutations on the new doc mirror to the peer.
        newDoc.remove(0, newDoc.getLength());
        newDoc.insertString(0, "mutated", null);
        assertEquals("mutated", peerOf(tf).getValue());
    }

    @Test
    @DisplayName("setEditable propagates to peer readOnly")
    void setEditablePropagatesToPeerReadOnly() {
        JTextField tf = new JTextField();
        tf.setEditable(false);
        assertTrue(peerOf(tf).isReadOnly());
        tf.setEditable(true);
        assertFalse(peerOf(tf).isReadOnly());
    }

    @Test
    @DisplayName("columns ctor stores the columns and sets peer width in ch")
    void columnsCtorStoresColumnsAndSetsPeerWidth() {
        JTextField tf = new JTextField(20);
        assertEquals(20, tf.getColumns());
        assertEquals("var(--emul-layout-w, calc(20ch + 2em))", peerOf(tf).getWidth());
    }

    @Test
    @DisplayName("setColumns throws IAE on negative value")
    void setColumnsThrowsOnNegative() {
        JTextField tf = new JTextField();
        assertThrows(IllegalArgumentException.class, () -> tf.setColumns(-1));
    }

    @Test
    @DisplayName("ctor throws IAE on negative columns")
    void ctorThrowsOnNegativeColumns() {
        assertThrows(IllegalArgumentException.class, () -> new JTextField(null, null, -5));
    }

    @Test
    @DisplayName("setHorizontalAlignment validates")
    void setHorizontalAlignmentValidates() {
        JTextField tf = new JTextField();
        tf.setHorizontalAlignment(SwingConstants.LEFT);
        assertEquals(SwingConstants.LEFT, tf.getHorizontalAlignment());
        assertThrows(IllegalArgumentException.class, () -> tf.setHorizontalAlignment(SwingConstants.TOP));
    }

    @Test
    @DisplayName("fireActionPerformed constructs ActionEvent with this as source")
    void fireActionPerformedUsesTheEmulatorAsSource() {
        // Source must be the emulator so migrated code can cast
        // `(JTextField) e.getSource()`. If the listener ever forwarded
        // the Vaadin ClickEvent / ValueChangeEvent source verbatim,
        // the cast would fail.
        JTextField tf = new JTextField("hello");
        AtomicReference<Object> source = new AtomicReference<>();
        AtomicReference<String> command = new AtomicReference<>();
        tf.addActionListener(e -> {
            source.set(e.getSource());
            command.set(e.getActionCommand());
        });

        tf.postActionEvent();

        assertSame(tf, source.get());
        assertEquals("hello", command.get());  // actionCommand falls back to text
    }

    @Test
    @DisplayName("setActionCommand overrides the text fallback")
    void setActionCommandOverridesTheTextFallback() {
        JTextField tf = new JTextField("ignored");
        tf.setActionCommand("submit");
        List<String> commands = new ArrayList<>();
        tf.addActionListener(e -> commands.add(e.getActionCommand()));
        tf.postActionEvent();
        assertEquals(List.of("submit"), commands);
    }

    @Test
    @DisplayName("action listeners fire in LIFO order matching Swing")
    void actionListenersFireInLifoOrder() {
        JTextField tf = new JTextField();
        List<String> hits = new ArrayList<>();
        tf.addActionListener(e -> hits.add("first"));
        tf.addActionListener(e -> hits.add("second"));

        tf.postActionEvent();

        assertEquals(List.of("second", "first"), hits);
    }

    @Test
    @DisplayName("removeActionListener stops further events")
    void removeActionListenerStopsFurtherEvents() {
        JTextField tf = new JTextField();
        ActionListener l = e -> { /* noop */ };
        tf.addActionListener(l);
        tf.removeActionListener(l);
        assertEquals(0, tf.getActionListeners().length);
    }

    @Test
    @DisplayName("setText while hosted in a frame updates the rendered TextField")
    void setTextWhileHostedUpdatesTheRenderedField() {
        // End-to-end: JTextField inside a visible JFrame renders via
        // the Vaadin TextField peer; setText after show propagates.
        JTextField tf = new JTextField("before");
        JFrame frame = new JFrame("textfield smoke");
        frame.add(tf);
        frame.setVisible(true);

        assertEquals("before", LocatorJ._get(TextField.class).getValue());

        tf.setText("after");

        assertEquals("after", LocatorJ._get(TextField.class).getValue());
    }

    @Test
    @DisplayName("peer value change while hosted reaches user DocumentListener")
    void peerValueChangeWhileHostedReachesTheDocument() {
        // The full R_swing_is_truth loop in a UI context: simulate a user typing
        // (setValue on the peer) and verify the Document reflects it
        // and the ActionEvent payload uses the new text.
        JTextField tf = new JTextField("initial");
        JFrame frame = new JFrame("textfield smoke 2");
        frame.add(tf);
        frame.setVisible(true);

        List<String> actionCommands = new ArrayList<>();
        tf.addActionListener(e -> actionCommands.add(e.getActionCommand()));

        LocatorJ._setValue(LocatorJ._get(TextField.class), "user-typed");

        assertEquals("user-typed", tf.getText());
        // postActionEvent (simulating Enter) sees the updated text.
        tf.postActionEvent();
        assertEquals(List.of("user-typed"), actionCommands);
    }

    @Test
    @DisplayName("getUIClassID is TextFieldUI for UIManager compatibility")
    void getUiClassIdIsTextFieldUi() {
        assertEquals("TextFieldUI", new JTextField().getUIClassID());
    }

    // --- setAction — narrower than AbstractButton (no NAME-to-text,
    // no mnemonic, no icon). Enter → Action.actionPerformed end-to-end.

    @Test
    @DisplayName("setAction does not copy NAME into text")
    void setActionDoesNotCopyNameIntoText() {
        // Text is user-entered form data; replacing it from Action.NAME
        // would wipe the field on every Action mutation.
        JTextField tf = new JTextField("user input");
        tf.setAction(action("label-value"));
        assertEquals("user input", tf.getText());
    }

    @Test
    @DisplayName("setAction propagates ACTION_COMMAND_KEY into Enter's ActionEvent")
    void setActionPropagatesActionCommandKey() {
        Action a = action("ignored");
        a.putValue(Action.ACTION_COMMAND_KEY, "submit-1");
        JTextField tf = new JTextField("hi");
        tf.setAction(a);

        List<String> commands = new ArrayList<>();
        tf.addActionListener(e -> commands.add(e.getActionCommand()));
        tf.postActionEvent();

        assertEquals(List.of("submit-1"), commands);
    }

    @Test
    @DisplayName("setAction propagates enabled false")
    void setActionPropagatesEnabledFalse() {
        Action a = action("x");
        a.setEnabled(false);
        JTextField tf = new JTextField();
        tf.setAction(a);
        assertFalse(tf.isEnabled());
    }

    @Test
    @DisplayName("action setEnabled change propagates via PCE")
    void actionSetEnabledPropagatesViaPce() {
        Action a = action("x");
        JTextField tf = new JTextField();
        tf.setAction(a);
        assertTrue(tf.isEnabled());
        a.setEnabled(false);
        assertFalse(tf.isEnabled());
        a.setEnabled(true);
        assertTrue(tf.isEnabled());
    }

    @Test
    @DisplayName("Enter key dispatches to Action.actionPerformed")
    void enterKeyDispatchesToActionPerformed() {
        // End-to-end: JTextField's Enter wiring fires ActionEvent → all
        // listeners (including the attached Action).
        List<String> hits = new ArrayList<>();
        Action a = new AbstractAction("x") {
            @Override
            public void actionPerformed(ActionEvent e) {
                hits.add(e.getActionCommand());
            }
        };
        JTextField tf = new JTextField("typed");
        tf.setAction(a);

        tf.postActionEvent();

        // No ACTION_COMMAND_KEY on the Action ⇒ field's fallback to
        // getText() kicks in, carrying the current text as the command.
        assertEquals(List.of("typed"), hits);
    }

    @Test
    @DisplayName("replacing an Action detaches the old PCE listener")
    void replacingAnActionDetachesTheOldPceListener() {
        Action oldAction = action("old");
        oldAction.setEnabled(true);
        Action newAction = action("new");

        JTextField tf = new JTextField();
        tf.setAction(oldAction);
        tf.setAction(newAction);

        // Flipping the old action's enabled should not reach the field —
        // the new action took over.
        oldAction.setEnabled(false);
        assertTrue(tf.isEnabled());
    }

    @Test
    @DisplayName("setAction null clears action-driven enabled state")
    void setActionNullClearsActionDrivenEnabledState() {
        Action a = action("x");
        a.setEnabled(false);
        JTextField tf = new JTextField();
        tf.setAction(a);
        assertFalse(tf.isEnabled());

        tf.setAction(null);
        assertTrue(tf.isEnabled());  // back to the field's own default
    }

    @Test
    @DisplayName("setAction same action is a no-op")
    void setActionSameActionIsANoOp() {
        Action a = action("x");
        JTextField tf = new JTextField();
        tf.setAction(a);
        tf.setAction(a);
        assertEquals(1, tf.getActionListeners().length);
    }

    @Test
    @DisplayName("getAction returns the installed action")
    void getActionReturnsTheInstalledAction() {
        Action a = action("x");
        JTextField tf = new JTextField();
        tf.setAction(a);
        assertSame(a, tf.getAction());
    }
}
