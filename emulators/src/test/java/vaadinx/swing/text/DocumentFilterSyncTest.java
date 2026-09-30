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

package vaadinx.swing.text;

import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.JTextArea;
import vaadinx.swing.JTextField;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A user-supplied filtering {@code Document} must see the browser's edit as an <i>edit</i>,
 * and must have the last word about what the field holds.
 *
 * <p>Both filters here are the shapes {@code testapps/inventory} ships: {@code JTextFieldLimit}
 * (a length cap in {@code GTextArea}) and {@code NumberFormatDocument} (a thousands-separator
 * formatter that rewrites content and refuses non-numerics). Handed the whole
 * field value on every keystroke, neither can work: a cap tested against the
 * entire value rejects an edit it would have allowed, and a single-character
 * branch never fires.
 *
 * <p>Every field fixture here constructs its Document inline in
 * {@code createDefaultModel} and captures nothing. That is not style: the JDK calls
 * that factory from {@code JTextComponent}'s constructor (see
 * {@code JTextField.createDefaultDocument}), and Java assigns an anonymous class's
 * captured locals only <em>after</em> the super constructor returns — so a captured
 * document or list would be {@code null} at the one moment the hook runs. The
 * recording fixtures reach their record through {@code getDocument()} instead.
 */
class DocumentFilterSyncTest extends AbstractKaribuTest {

    /** Simulate the browser sending its new value, as the EAGER peer listener does. */
    private static void type(JTextComponent field, String newValue) {
        ((TextField) field.getPeer()).setValue(newValue);
    }

    private static String peerValue(JTextComponent field) {
        return ((TextField) field.getPeer()).getValue();
    }

    // --- a length cap: the GTextArea / JTextFieldLimit shape ---------------

    private static class LimitDocument extends PlainDocument {

        private final int limit;

        LimitDocument(int limit) {
            this.limit = limit;
        }

        @Override
        public void insertString(int offset, String str, AttributeSet attr)
                throws BadLocationException {
            if (str == null) {
                return;
            }
            if (getLength() + str.length() <= limit) {
                super.insertString(offset, str, attr);
            }
        }
    }

    /** A field capped at 3 characters — the fixture three of the tests below share. */
    private static JTextField limit3Field() {
        return new JTextField() {
            @Override
            protected Document createDefaultModel() {
                return new LimitDocument(3);
            }
        };
    }

    @Test
    @DisplayName("a length cap sees the keystroke, not the whole value")
    void aCapSeesTheKeystroke() {
        JTextField field = limit3Field();

        // One character at a time, which is what the EAGER peer sends while typing.
        type(field, "a");
        type(field, "ab");
        type(field, "abc");
        assertEquals("abc", field.getText(), "under the cap, each keystroke goes in");

        type(field, "abcd");   // the keystroke past the cap
        assertEquals("abc", field.getText(),
                "the cap must drop the 4th character and keep the legal prefix — "
                        + "handed the whole value, the same filter refuses everything and "
                        + "empties the Document");
    }

    @Test
    @DisplayName("a paste too big for the cap is refused wholesale, as in Swing")
    void anOversizedPasteIsRefusedWholesale() {
        // Not a shortcoming: this filter's test is all-or-nothing
        // (`getLength() + str.length() <= limit`), so a 4-character paste into a
        // limit-3 field inserts nothing. Delivering the edit faithfully means
        // delivering this outcome too.
        JTextField field = limit3Field();
        type(field, "abcd");
        assertEquals("", field.getText());
        assertEquals("", peerValue(field), "and the browser is reverted to match");
    }

    @Test
    @DisplayName("a refused keystroke is pushed back so the browser matches the Document")
    void aRefusedKeystrokeIsPushedBack() {
        JTextField field = limit3Field();
        type(field, "abc");
        type(field, "abcd");

        assertEquals("abc", field.getText());
        assertEquals("abc", peerValue(field),
                "the browser must be reverted to what the Document accepted, "
                        + "not left showing text getText() would never return");
    }

    // --- edit shape: which Document method the browser's change becomes ----

    /** Logs every mutation it is handed, so a test can assert the edit's shape. */
    private static class RecordingDocument extends PlainDocument {

        final List<String> seen = new ArrayList<>();

        @Override
        public void remove(int offs, int len) throws BadLocationException {
            seen.add("remove(" + offs + "," + len + ")");
            super.remove(offs, len);
        }

        @Override
        public void insertString(int offset, String str, AttributeSet attr)
                throws BadLocationException {
            seen.add("insert(" + offset + ",'" + str + "')");
            super.insertString(offset, str, attr);
        }
    }

    /** A field over a {@link RecordingDocument}, read back through {@code getDocument()}. */
    private static class RecordingField extends JTextField {
        @Override
        protected Document createDefaultModel() {
            return new RecordingDocument();
        }
    }

    @Test
    @DisplayName("deleting is delivered as a remove, not a wholesale replace")
    void deletingArrivesAsARemove() {
        RecordingField field = new RecordingField();
        List<String> seen = ((RecordingDocument) field.getDocument()).seen;

        type(field, "hello");
        seen.clear();
        type(field, "helo");        // one character deleted in the middle

        assertEquals(List.of("remove(3,1)"), seen,
                "a single deletion must arrive as one remove at the right offset");
        assertEquals("helo", field.getText());
    }

    @Test
    @DisplayName("an insert in the middle arrives at the right offset")
    void anInsertArrivesAtTheRightOffset() {
        RecordingField field = new RecordingField();
        List<String> seen = ((RecordingDocument) field.getDocument()).seen;

        type(field, "ac");
        seen.clear();
        type(field, "abc");

        assertEquals(List.of("insert(1,'b')"), seen);
    }

    // --- a rewriting filter: the NumberFormatDocument shape ---------------

    /** Refuses non-digits, and rewrites what it accepts with thousands separators. */
    private static class GroupingDocument extends PlainDocument {

        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            if (str == null) {
                return;
            }
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (!Character.isDigit(c) && c != ',') {
                    throw new BadLocationException(str, offs);
                }
            }
            String whole = (getText(0, offs) + str + getText(offs, getLength() - offs))
                    .replace(",", "");
            super.remove(0, getLength());
            super.insertString(0, group(whole), a);
        }

        /** Kotlin's {@code reversed().chunked(3).joinToString(",").reversed()}. */
        private static String group(String digits) {
            StringBuilder out = new StringBuilder();
            for (int i = digits.length(); i > 0; i -= 3) {
                if (!out.isEmpty()) {
                    out.insert(0, ',');
                }
                out.insert(0, digits, Math.max(0, i - 3), i);
            }
            return out.toString();
        }
    }

    private static JTextField groupingField() {
        return new JTextField() {
            @Override
            protected Document createDefaultModel() {
                return new GroupingDocument();
            }
        };
    }

    @Test
    @DisplayName("a rewriting filter's output reaches the browser")
    void aRewritingFiltersOutputReachesTheBrowser() {
        JTextField field = groupingField();

        type(field, "1");
        type(field, "12");
        type(field, "123");
        type(field, "1234");

        assertEquals("1,234", field.getText(),
                "the filter's own formatting is what the Document holds");
        assertEquals("1,234", peerValue(field),
                "and it must be pushed to the browser — otherwise the field shows 1234 "
                        + "while getText() returns 1,234");
    }

    @Test
    @DisplayName("a rejected character leaves both sides on the last accepted value")
    void aRejectedCharacterLeavesBothSidesAlone() {
        JTextField field = groupingField();
        type(field, "12");
        type(field, "12x");

        assertEquals("12", field.getText());
        assertEquals("12", peerValue(field));
    }

    // --- the unfiltered path must be unchanged ----------------------------

    @Test
    @DisplayName("a plain field still round-trips without a push-back")
    void aPlainFieldRoundTrips() {
        JTextField field = new JTextField();
        type(field, "hello");
        assertEquals("hello", field.getText());
        assertEquals("hello", peerValue(field));

        type(field, "");
        assertEquals("", field.getText());
    }

    @Test
    @DisplayName("JTextArea takes the same path")
    void textAreaTakesTheSamePath() {
        JTextArea area = new JTextArea() {
            @Override
            protected Document createDefaultModel() {
                return new LimitDocument(3);
            }
        };
        com.vaadin.flow.component.textfield.TextArea peer =
                (com.vaadin.flow.component.textfield.TextArea) area.getPeer();
        peer.setValue("a");
        peer.setValue("ab");
        peer.setValue("abc");
        peer.setValue("abcd");
        assertEquals("abc", area.getText());
        assertEquals("abc", peer.getValue());
    }
}
