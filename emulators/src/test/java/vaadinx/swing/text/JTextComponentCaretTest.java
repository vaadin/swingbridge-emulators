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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.DebouncePhase;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.JsonConstants;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;
import vaadinx.swing.JEditorPane;
import vaadinx.swing.JTextArea;
import vaadinx.swing.JTextField;

import javax.swing.event.CaretListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.NavigationFilter;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Caret + selection on the emulator side: the component's methods are the JDK's bodies
 * over its installed {@link DefaultCaret}, the caret follows the Document under its
 * update policy, and the peer's input renders dot/mark and reports the browser's own
 * caret moves back (D_emulator_caret).
 */
class JTextComponentCaretTest extends AbstractKaribuTest {

    private final List<String> warns = new ArrayList<>();

    private void captureWarns() {
        EHelper.warnHook = warns::add;
        SHelper.warnHook = warns::add;
    }

    @AfterEach
    void resetHooks() {
        EHelper.warnHook = msg -> { };
        SHelper.warnHook = msg -> { };
    }

    /** One (dot, mark) reading from a CaretEvent — named beats a Pair at the assertion. */
    private record Caretpos(int dot, int mark) {
    }

    // --- The regression this whole mechanism exists for --------------

    @Test
    @DisplayName("getCaret is non-null so the setDot-from-a-Document-filter idiom does not NPE")
    void getCaretIsNeverNull() {
        // Regression guard for the inventory testapp's NumberFormatDocument:
        // insertString reformats the text, then does
        // target.getCaret().setDot(dot) to put the caret back where the user
        // was typing.
        JTextField tf = new JTextField("1,234");
        tf.getCaret().setDot(3);
        assertEquals(3, tf.getCaretPosition());
    }

    @Test
    @DisplayName("a Document filter can reposition the caret after reformatting")
    void aFilterCanRepositionTheCaret() {
        JTextField tf = new JTextField();
        tf.setDocument(new PlainDocument() {
            @Override
            public void insertString(int offs, String str, AttributeSet a)
                    throws BadLocationException {
                super.insertString(offs, str == null ? null : str.toUpperCase(Locale.ROOT), a);
                tf.getCaret().setDot(getLength());
            }
        });
        tf.setText("abc");

        assertEquals("ABC", tf.getText());
        assertEquals(3, tf.getCaretPosition());
    }

    @Test
    @DisplayName("the installed caret is a DefaultCaret, so the ALWAYS_UPDATE idiom compiles and works")
    void theCaretIsADefaultCaret() {
        // The log-window idiom: make the caret follow appends made off the UI thread.
        JTextArea log = new JTextArea();
        DefaultCaret caret = (DefaultCaret) log.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        runOffUiThread(() -> log.append("line one\n"));

        assertEquals(9, log.getCaretPosition());
        assertEquals(500, caret.getBlinkRate());
    }

    @Test
    @DisplayName("under the default policy an edit off the UI thread only clamps the caret")
    void theDefaultPolicyIgnoresOffThreadEdits() {
        JTextArea log = new JTextArea("abc");
        assertEquals(3, log.getCaretPosition());

        runOffUiThread(() -> log.append("def"));
        assertEquals(3, log.getCaretPosition());

        // The remove clamps the caret to 0 and the insert leaves it there.
        runOffUiThread(() -> log.setText("x"));
        assertEquals(0, log.getCaretPosition());
    }

    // --- The JDK's own call graph ---------------------------------

    /**
     * Replays a script measured on JDK 25 ({@code JTextField} headless, on the EDT and on a
     * plain thread) and asserts its output verbatim: which listeners each caret method
     * and each Document edit reaches, in which order, and where dot and mark end up.
     */
    @Test
    @DisplayName("caret and selection replay the JDK's measured script on the UI thread")
    void caretScriptMatchesTheJdkOnTheUiThread() {
        assertEquals(List.of(
                "fresh: [] dot=0 mark=0 sel=0..0 text=null",
                "setText hello world: [ins@0, S2, S1, C2, C1(11,11)] dot=11 mark=11 sel=11..11 text=null",
                "setText same: [rem@11, S2, S1, C2, C1(0,0), ins@0, S2, S1, C2, C1(11,11)] dot=11 mark=11 sel=11..11 text=null",
                "setCaretPosition 5: [S2, S1, C2, C1(5,5)] dot=5 mark=5 sel=5..5 text=null",
                "setCaretPosition 5 again: [] dot=5 mark=5 sel=5..5 text=null",
                "moveCaretPosition 8: [S2, S1, C2, C1(8,5)] dot=8 mark=5 sel=5..8 text= wo",
                "moveCaretPosition 8 again: [] dot=8 mark=5 sel=5..8 text= wo",
                "moveCaretPosition 2: [S2, S1, C2, C1(2,5)] dot=2 mark=5 sel=2..5 text=llo",
                "insert 'XX' at 0: [ins@2, S2, S1, C2, C1(7,7), S2, S1, C2, C1(4,7)] dot=4 mark=7 sel=4..7 text=llo",
                "insert 'Y' at 4 (=dot): [ins@4, S2, S1, C2, C1(8,8), S2, S1, C2, C1(5,8)] dot=5 mark=8 sel=5..8 text=llo",
                "insert 'Z' at end: [ins@5] dot=5 mark=8 sel=5..8 text=llo",
                "remove 0,3: [rem@5, S2, S1, C2, C1(5,5), S2, S1, C2, C1(2,5)] dot=2 mark=5 sel=2..5 text=llo",
                "remove across selection 2,4: [rem@2, S2, S1, C2, C1(2,2)] dot=2 mark=2 sel=2..2 text=null",
                "select 1,4: [S2, S1, C2, C1(1,1), S2, S1, C2, C1(4,1)] dot=4 mark=1 sel=1..4 text=Ywo",
                "select 5,2: [S2, S1, C2, C1(5,5)] dot=5 mark=5 sel=5..5 text=null",
                "select -3,99: [S2, S1, C2, C1(0,0), S2, S1, C2, C1(8,0)] dot=8 mark=0 sel=0..8 text=eYworldZ",
                "setSelectionStart 3: [S2, S1, C2, C1(3,3), S2, S1, C2, C1(8,3)] dot=8 mark=3 sel=3..8 text=orldZ",
                "setSelectionEnd 1: [S2, S1, C2, C1(3,3)] dot=3 mark=3 sel=3..3 text=null",
                "selectAll: [S2, S1, C2, C1(0,0), S2, S1, C2, C1(8,0)] dot=8 mark=0 sel=0..8 text=eYworldZ",
                "replaceSelection ab: [rem@8, S2, S1, C2, C1(0,0), ins@0, S2, S1, C2, C1(2,2)] dot=2 mark=2 sel=2..2 text=null",
                "setCaretPosition 1: [S2, S1, C2, C1(1,1)] dot=1 mark=1 sel=1..1 text=null",
                "replaceSelection null: [] dot=1 mark=1 sel=1..1 text=null",
                "moveCaretPosition 2: [S2, S1, C2, C1(2,1)] dot=2 mark=1 sel=1..2 text=b",
                "replaceSelection empty: [rem@2, S2, S1, C2, C1(1,1)] dot=1 mark=1 sel=1..1 text=null",
                "setCaretPosition -1: IllegalArgumentException bad position: -1 [] dot=1 mark=1 sel=1..1 text=null",
                "setCaretPosition 99: IllegalArgumentException bad position: 99 [] dot=1 mark=1 sel=1..1 text=null",
                "moveCaretPosition 99: IllegalArgumentException bad position: 99 [] dot=1 mark=1 sel=1..1 text=null",
                "caret.setDot 99: [] dot=1 mark=1 sel=1..1 text=null",
                "caret.setDot -5: [S2, S1, C2, C1(0,0)] dot=0 mark=0 sel=0..0 text=null",
                "caret.moveDot 99: [S2, S1, C2, C1(99,0)] dot=99 mark=0 sel=0..99 text=IllegalArgumentException:Invalid location",
                "setText abcdef: [rem@99, S2, S1, C2, C1(0,0), S2, S1, C2, C1(98,0), S2, S1, C2, C1(0,0), ins@0, S2, S1, C2, C1(6,6)] dot=6 mark=6 sel=6..6 text=null",
                "setCaretPosition 3: [S2, S1, C2, C1(3,3)] dot=3 mark=3 sel=3..3 text=null",
                "setDocument new: [S2, S1, C2, C1(0,0)] dot=0 mark=0 sel=0..0 text=null",
                "setCaretPosition 7: [S2, S1, C2, C1(7,7)] dot=7 mark=7 sel=7..7 text=null",
                "setEnabled false; moveCaretPosition 9: [S2, S1, C2, C1(9,9)] dot=9 mark=9 sel=9..9 text=null",
                "setEnabled true; moveCaretPosition 9: [] dot=9 mark=9 sel=9..9 text=null",
                "setText null: [S2, S1, C2, C1(0,0)] dot=0 mark=0 sel=0..0 text=null",
                "getCaret same: [true] dot=0 mark=0 sel=0..0 text=null"), new CaretScript().run());
    }

    @Test
    @DisplayName("caret and selection replay the JDK's measured script off the UI thread")
    void caretScriptMatchesTheJdkOffTheUiThread() {
        AtomicReference<List<String>> out = new AtomicReference<>();
        runOffUiThread(() -> out.set(new CaretScript().run()));
        assertEquals(List.of(
                "fresh: [] dot=0 mark=0 sel=0..0 text=null",
                "setText hello world: [ins@0] dot=0 mark=0 sel=0..0 text=null",
                "setText same: [rem@0, ins@0] dot=0 mark=0 sel=0..0 text=null",
                "setCaretPosition 5: [S2, S1, C2, C1(5,5)] dot=5 mark=5 sel=5..5 text=null",
                "setCaretPosition 5 again: [] dot=5 mark=5 sel=5..5 text=null",
                "moveCaretPosition 8: [S2, S1, C2, C1(8,5)] dot=8 mark=5 sel=5..8 text= wo",
                "moveCaretPosition 8 again: [] dot=8 mark=5 sel=5..8 text= wo",
                "moveCaretPosition 2: [S2, S1, C2, C1(2,5)] dot=2 mark=5 sel=2..5 text=llo",
                "insert 'XX' at 0: [ins@2] dot=2 mark=5 sel=2..5 text=hel",
                "insert 'Y' at 4 (=dot): [ins@2] dot=2 mark=5 sel=2..5 text=heY",
                "insert 'Z' at end: [ins@2] dot=2 mark=5 sel=2..5 text=heY",
                "remove 0,3: [rem@2] dot=2 mark=5 sel=2..5 text=llo",
                "remove across selection 2,4: [rem@2] dot=2 mark=5 sel=2..5 text=wor",
                "select 1,4: [S2, S1, C2, C1(1,1), S2, S1, C2, C1(4,1)] dot=4 mark=1 sel=1..4 text=Ywo",
                "select 5,2: [S2, S1, C2, C1(5,5)] dot=5 mark=5 sel=5..5 text=null",
                "select -3,99: [S2, S1, C2, C1(0,0), S2, S1, C2, C1(8,0)] dot=8 mark=0 sel=0..8 text=eYworldZ",
                "setSelectionStart 3: [S2, S1, C2, C1(3,3), S2, S1, C2, C1(8,3)] dot=8 mark=3 sel=3..8 text=orldZ",
                "setSelectionEnd 1: [S2, S1, C2, C1(3,3)] dot=3 mark=3 sel=3..3 text=null",
                "selectAll: [S2, S1, C2, C1(0,0), S2, S1, C2, C1(8,0)] dot=8 mark=0 sel=0..8 text=eYworldZ",
                "replaceSelection ab: [rem@8, ins@0] dot=0 mark=0 sel=0..0 text=null",
                "setCaretPosition 1: [S2, S1, C2, C1(1,1)] dot=1 mark=1 sel=1..1 text=null",
                "replaceSelection null: [] dot=1 mark=1 sel=1..1 text=null",
                "moveCaretPosition 2: [S2, S1, C2, C1(2,1)] dot=2 mark=1 sel=1..2 text=b",
                "replaceSelection empty: [rem@2] dot=1 mark=1 sel=1..1 text=null",
                "setCaretPosition -1: IllegalArgumentException bad position: -1 [] dot=1 mark=1 sel=1..1 text=null",
                "setCaretPosition 99: IllegalArgumentException bad position: 99 [] dot=1 mark=1 sel=1..1 text=null",
                "moveCaretPosition 99: IllegalArgumentException bad position: 99 [] dot=1 mark=1 sel=1..1 text=null",
                "caret.setDot 99: [] dot=1 mark=1 sel=1..1 text=null",
                "caret.setDot -5: [S2, S1, C2, C1(0,0)] dot=0 mark=0 sel=0..0 text=null",
                "caret.moveDot 99: [S2, S1, C2, C1(99,0)] dot=99 mark=0 sel=0..99 text=IllegalArgumentException:Invalid location",
                "setText abcdef: [rem@99, ins@0] dot=0 mark=0 sel=0..0 text=null",
                "setCaretPosition 3: [S2, S1, C2, C1(3,3)] dot=3 mark=3 sel=3..3 text=null",
                "setDocument new: [S2, S1, C2, C1(0,0)] dot=0 mark=0 sel=0..0 text=null",
                "setCaretPosition 7: [S2, S1, C2, C1(7,7)] dot=7 mark=7 sel=7..7 text=null",
                "setEnabled false; moveCaretPosition 9: [S2, S1, C2, C1(9,9)] dot=9 mark=9 sel=9..9 text=null",
                "setEnabled true; moveCaretPosition 9: [] dot=9 mark=9 sel=9..9 text=null",
                "setText null: [] dot=0 mark=0 sel=0..0 text=null",
                "getCaret same: [true] dot=0 mark=0 sel=0..0 text=null"), out.get());
    }

    /** The measured JDK script, verbatim, against the emulator. */
    private static final class CaretScript {
        private final List<String> log = new ArrayList<>();
        private final List<String> out = new ArrayList<>();
        private JTextField f;

        private String selText() {
            try {
                return f.getSelectedText();
            } catch (RuntimeException e) {
                return e.getClass().getSimpleName() + ":" + e.getMessage();
            }
        }

        private String st() {
            return "dot=" + f.getCaretPosition() + " mark=" + f.getCaret().getMark() + " sel="
                    + f.getSelectionStart() + ".." + f.getSelectionEnd() + " text=" + selText();
        }

        private void step(String label, Runnable r) {
            log.clear();
            try {
                r.run();
                out.add(label + ": " + log + " " + st());
            } catch (RuntimeException e) {
                out.add(label + ": " + e.getClass().getSimpleName() + " " + e.getMessage() + " " + log + " " + st());
            }
        }

        private void insert(int offset, String text) {
            try {
                f.getDocument().insertString(offset, text, null);
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
        }

        private void remove(int offset, int length) {
            try {
                f.getDocument().remove(offset, length);
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
        }

        List<String> run() {
            f = new JTextField();
            f.addCaretListener(e -> log.add("C1(" + e.getDot() + "," + e.getMark() + ")"));
            f.addCaretListener(e -> log.add("C2"));
            f.getCaret().addChangeListener(e -> log.add("S1"));
            f.getCaret().addChangeListener(e -> log.add("S2"));
            f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { log.add("ins@" + f.getCaretPosition()); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { log.add("rem@" + f.getCaretPosition()); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { }
            });
            step("fresh", () -> { });
            step("setText hello world", () -> f.setText("hello world"));
            step("setText same", () -> f.setText("hello world"));
            step("setCaretPosition 5", () -> f.setCaretPosition(5));
            step("setCaretPosition 5 again", () -> f.setCaretPosition(5));
            step("moveCaretPosition 8", () -> f.moveCaretPosition(8));
            step("moveCaretPosition 8 again", () -> f.moveCaretPosition(8));
            step("moveCaretPosition 2", () -> f.moveCaretPosition(2));
            step("insert 'XX' at 0", () -> insert(0, "XX"));
            step("insert 'Y' at 4 (=dot)", () -> insert(4, "Y"));
            step("insert 'Z' at end", () -> insert(f.getDocument().getLength(), "Z"));
            step("remove 0,3", () -> remove(0, 3));
            step("remove across selection 2,4", () -> remove(2, 4));
            step("select 1,4", () -> f.select(1, 4));
            step("select 5,2", () -> f.select(5, 2));
            step("select -3,99", () -> f.select(-3, 99));
            step("setSelectionStart 3", () -> f.setSelectionStart(3));
            step("setSelectionEnd 1", () -> f.setSelectionEnd(1));
            step("selectAll", () -> f.selectAll());
            step("replaceSelection ab", () -> f.replaceSelection("ab"));
            step("setCaretPosition 1", () -> f.setCaretPosition(1));
            step("replaceSelection null", () -> f.replaceSelection(null));
            step("moveCaretPosition 2", () -> f.moveCaretPosition(2));
            step("replaceSelection empty", () -> f.replaceSelection(""));
            step("setCaretPosition -1", () -> f.setCaretPosition(-1));
            step("setCaretPosition 99", () -> f.setCaretPosition(99));
            step("moveCaretPosition 99", () -> f.moveCaretPosition(99));
            step("caret.setDot 99", () -> f.getCaret().setDot(99));
            step("caret.setDot -5", () -> f.getCaret().setDot(-5));
            step("caret.moveDot 99", () -> f.getCaret().moveDot(99));
            step("setText abcdef", () -> f.setText("abcdef"));
            step("setCaretPosition 3", () -> f.setCaretPosition(3));
            step("setDocument new", () -> {
                PlainDocument d = new PlainDocument();
                try {
                    d.insertString(0, "0123456789", null);
                } catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
                f.setDocument(d);
            });
            step("setCaretPosition 7", () -> f.setCaretPosition(7));
            step("setEnabled false; moveCaretPosition 9", () -> {
                f.setEnabled(false);
                f.moveCaretPosition(9);
            });
            step("setEnabled true; moveCaretPosition 9", () -> {
                f.setEnabled(true);
                f.moveCaretPosition(9);
            });
            step("setText null", () -> f.setText(null));
            step("getCaret same", () -> log.add(String.valueOf(f.getCaret() == f.getCaret())));
            return out;
        }
    }

    /**
     * Runs {@code body} on a bare thread — no UI, so not the UI thread — with the test's
     * session unlocked, as a request thread would leave it for a worker's writes to hop.
     */
    private static void runOffUiThread(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "off-ui-thread");
        t.setDaemon(true);
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        session.unlock();
        try {
            t.start();
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
        if (t.isAlive()) fail("the off-UI thread did not finish");
        if (failure.get() != null) throw new AssertionError("the off-UI body threw", failure.get());
    }

    // --- JDK contracts the script does not reach -------------------

    @Test
    @DisplayName("setCaretPosition outside the document throws IAE, as Swing does")
    void setCaretPositionRejectsOutOfRange() {
        JTextField tf = new JTextField("abc");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> tf.setCaretPosition(4));
        assertEquals("bad position: 4", e.getMessage());
    }

    @Test
    @DisplayName("a custom Document still filters an edit routed through replaceSelection")
    void aCustomDocumentFiltersReplaceSelection() {
        JTextField tf = new JTextField("hello");
        tf.setDocument(new PlainDocument() {
            @Override
            public void insertString(int offs, String str, AttributeSet a)
                    throws BadLocationException {
                super.insertString(offs, str == null ? null : stripDigits(str), a);
            }
        });
        tf.selectAll();
        tf.replaceSelection("a1b2c3");

        assertEquals("abc", tf.getText());
    }

    /** Kotlin's {@code str.filter { !it.isDigit() }}. */
    private static String stripDigits(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                out.append(s.charAt(i));
            }
        }
        return out.toString();
    }

    @Test
    @DisplayName("setText hands a DocumentFilter one replace, as the JDK does")
    void setTextIsOneReplace() {
        JTextField tf = new JTextField("old");
        List<String> seen = new ArrayList<>();
        ((PlainDocument) tf.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                seen.add("replace(" + offset + "," + length + "," + text + ")");
                super.replace(fb, offset, length, text, attrs);
            }
        });
        tf.setText("new text");
        assertEquals(List.of("replace(0,3,new text)"), seen);
        assertEquals("new text", tf.getText());
    }

    @Test
    @DisplayName("JTextArea inherits the same mechanism")
    void textAreaInheritsTheMechanism() {
        JTextArea ta = new JTextArea("line one\nline two");
        ta.select(0, 8);
        assertEquals("line one", ta.getSelectedText());
    }

    @Test
    @DisplayName("a non-text peer's caret still follows its Document, fires, and WARNs nothing")
    void aNonTextPeerStillHasAWorkingCaret() {
        captureWarns();
        // JEditorPane peers on the RTE surrogate, which has no input element to render
        // the caret onto; the model is the emulator's all the same.
        JEditorPane pane = new JEditorPane();
        pane.setText("hello");
        List<Caretpos> seen = new ArrayList<>();
        pane.addCaretListener(e -> seen.add(new Caretpos(e.getDot(), e.getMark())));

        pane.select(1, 3);

        assertEquals("el", pane.getSelectedText());
        assertEquals(List.of(new Caretpos(1, 1), new Caretpos(3, 1)), seen);
        assertNoWarns(warns);
    }

    @Test
    @DisplayName("a NavigationFilter sees every caret move")
    void aNavigationFilterSeesEveryMove() {
        // A classic prompt field: the caret may not enter the "> " prefix.
        JTextField tf = new JTextField("> command");
        tf.setNavigationFilter(new NavigationFilter() {
            @Override
            public void setDot(FilterBypass fb, int dot, Position.Bias bias) {
                fb.setDot(Math.max(dot, 2), bias);
            }

            @Override
            public void moveDot(FilterBypass fb, int dot, Position.Bias bias) {
                fb.moveDot(Math.max(dot, 2), bias);
            }
        });
        tf.setCaretPosition(0);
        assertEquals(2, tf.getCaretPosition());

        fireSelection(tf, "emul-selection-now", 1, 1, "forward", null);
        assertEquals(2, tf.getCaretPosition(), "the browser's own move goes through the filter too");
    }

    // --- Caret listeners ------------------------------------------

    @Test
    @DisplayName("a CaretListener fires with the emulator as source, not the surrogate underneath")
    void caretListenerSourceIsTheEmulator() {
        JTextField tf = new JTextField("hello world");
        List<Object> sources = new ArrayList<>();
        List<Caretpos> seen = new ArrayList<>();
        tf.addCaretListener(e -> {
            sources.add(e.getSource());
            seen.add(new Caretpos(e.getDot(), e.getMark()));
        });

        tf.setCaretPosition(4);
        tf.moveCaretPosition(9);

        assertEquals(List.of(new Caretpos(4, 4), new Caretpos(9, 4)), seen);
        assertTrue(sources.stream().allMatch(s -> s == tf),
                "expected every source to be the emulator, got " + sources);
    }

    @Test
    @DisplayName("a browser drag-select reaches a CaretListener once, as a mouse drag does")
    void aBrowserSelectionReachesTheListenerOnce() {
        JTextField tf = new JTextField("hello world");
        List<Caretpos> seen = new ArrayList<>();
        List<String> changes = new ArrayList<>();
        tf.addCaretListener(e -> seen.add(new Caretpos(e.getDot(), e.getMark())));
        tf.getCaret().addChangeListener(e -> changes.add("dot=" + tf.getCaret().getDot()));

        fireSelection(tf, "emul-selection", 3, 8, "forward", null);

        assertEquals(8, tf.getCaretPosition());
        assertEquals("lo wo", tf.getSelectedText());
        assertEquals(List.of(new Caretpos(8, 3)), seen);
        assertEquals(List.of("dot=3", "dot=8"), changes, "the caret itself moves as setDot then moveDot");
    }

    @Test
    @DisplayName("a browser shift-extend moves only the dot")
    void aBrowserExtendMovesOnlyTheDot() {
        JTextField tf = new JTextField("hello world");
        tf.setCaretPosition(3);
        List<Caretpos> seen = new ArrayList<>();
        tf.addCaretListener(e -> seen.add(new Caretpos(e.getDot(), e.getMark())));

        fireSelection(tf, "emul-selection", 1, 3, "backward", null);

        assertEquals(List.of(new Caretpos(1, 3)), seen);
        assertEquals(1, tf.getSelectionStart());
        assertEquals(3, tf.getSelectionEnd());
    }

    @Test
    @DisplayName("removeCaretListener stops delivery")
    void removeCaretListenerStopsDelivery() {
        JTextField tf = new JTextField("abc");
        Counter fired = new Counter();
        CaretListener l = e -> fired.inc();
        tf.addCaretListener(l);
        tf.setCaretPosition(1);
        fired.assertEquals(1);

        tf.removeCaretListener(l);
        tf.setCaretPosition(2);
        fired.assertEquals(1);
    }

    @Test
    @DisplayName("getCaret returns the same instance every call, and setCaret replaces it")
    void getCaretIsStable() {
        JTextField tf = new JTextField("abc");
        assertSame(tf.getCaret(), tf.getCaret());
        assertInstanceOf(DefaultCaret.class, tf.getCaret());

        DefaultCaret mine = new DefaultCaret();
        tf.setCaret(mine);
        assertSame(mine, tf.getCaret());
        assertEquals(0, tf.getCaretPosition(), "install resets the new caret to 0, as the JDK's does");
        tf.setCaretPosition(2);
        assertEquals(2, mine.getDot());
    }

    // --- Browser reports ------------------------------------------

    @Test
    @DisplayName("caret moves are held until nothing listens, then reported at once")
    void movesAreHeldUntilSomethingListens() {
        JTextField tf = new JTextField("hello world");

        fireSelection(tf, "emul-selection", 2, 2, "forward", null);
        assertEquals(11, tf.getCaretPosition(), "an immediate report reaches no held registration");
        fireSelection(tf, "emul-selection", 2, 2, "forward", DebouncePhase.TRAILING);
        assertEquals(2, tf.getCaretPosition(), "the held report, as Flow flushes it");

        tf.addCaretListener(e -> { });
        fireSelection(tf, "emul-selection", 4, 4, "forward", null);
        assertEquals(4, tf.getCaretPosition(), "with a listener, every move is reported at once");
    }

    @Test
    @DisplayName("the pre-edit report puts the caret where the user typed before the filter runs")
    void thePreEditReportPositionsTheCaret() {
        JTextField tf = new JTextField();
        List<Integer> dotSeenByFilter = new ArrayList<>();
        tf.setDocument(new PlainDocument() {
            @Override
            public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
                dotSeenByFilter.add(tf.getCaret().getDot());
                super.insertString(offs, str, a);
            }
        });
        tf.setText("abc");
        UI.getCurrent().add(tf.getPeer());
        dotSeenByFilter.clear();

        // The user clicked between a and b (a held report), then typed X.
        fireSelection(tf, "emul-selection-now", 1, 1, "forward", null);
        LocatorJ._setValue(LocatorJ._get(TextField.class), "aXbc");

        assertEquals(List.of(1), dotSeenByFilter);
        assertEquals("aXbc", tf.getText());
        assertEquals(2, tf.getCaretPosition(), "the caret follows the insert, as on the desktop");
    }

    // --- Rendering onto the input ----------------------------------

    @Test
    @DisplayName("a caret change is rendered once, at the end of the request, with its final state")
    void aCaretChangeRendersOnce() {
        JTextField tf = new JTextField("hello world");
        UI.getCurrent().add(tf.getPeer());
        flushRenders();

        tf.setCaretPosition(3);
        tf.moveCaretPosition(8);
        tf.moveCaretPosition(6);

        assertEquals(List.of(List.of(3, 6, "forward")), flushRenders());
    }

    @Test
    @DisplayName("setText on the UI thread renders no selection: the input puts its caret at the end itself")
    void setTextRendersNoSelection() {
        JTextField tf = new JTextField("abc");
        UI.getCurrent().add(tf.getPeer());
        flushRenders();

        tf.setText("hello");

        assertEquals(List.of(), flushRenders());
        assertEquals(5, tf.getCaretPosition());
    }

    @Test
    @DisplayName("a browser report is not echoed back, unless a filter changed it")
    void aBrowserReportIsNotEchoed() {
        JTextField tf = new JTextField("> command");
        UI.getCurrent().add(tf.getPeer());
        flushRenders();

        fireSelection(tf, "emul-selection-now", 5, 5, "forward", null);
        assertEquals(List.of(), flushRenders());

        tf.setNavigationFilter(new NavigationFilter() {
            @Override
            public void setDot(FilterBypass fb, int dot, Position.Bias bias) {
                fb.setDot(Math.max(dot, 2), bias);
            }
        });
        fireSelection(tf, "emul-selection-now", 0, 0, "forward", null);
        assertEquals(List.of(List.of(2, 2, "forward")), flushRenders());
    }

    /** Runs the pending before-response flushes and returns the selections they pushed. */
    private static List<List<Object>> flushRenders() {
        UI ui = UI.getCurrent();
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        List<List<Object>> pushed = new ArrayList<>();
        for (PendingJavaScriptInvocation invocation : ui.getInternals().dumpPendingJavaScriptInvocations()) {
            if (!invocation.getInvocation().getExpression().contains("setSelectionRange")) continue;
            // Element.executeJs appends the element after the call's own three arguments.
            List<Object> params = invocation.getInvocation().getParameters();
            pushed.add(List.of(((Number) params.get(0)).intValue(), ((Number) params.get(1)).intValue(),
                    params.get(2)));
        }
        return pushed;
    }

    /**
     * Delivers a selection report on the peer, as Flow would after the client dispatches
     * one; {@code phase} is the debounce phase a held report arrives with.
     */
    private static void fireSelection(JTextField tf, String type, int start, int end, String dir,
            DebouncePhase phase) {
        tools.jackson.databind.node.ObjectNode payload =
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put("event.detail.start", start);
        payload.put("event.detail.end", end);
        payload.put("event.detail.dir", dir);
        if (phase != null) {
            payload.put(JsonConstants.EVENT_DATA_PHASE, phase.getIdentifier());
        }
        Element element = ((Component) tf.getPeer()).getElement();
        element.getNode().getFeature(ElementListenerMap.class).fireEvent(new DomEvent(element, type, payload));
    }

    // --- WARN inventory -------------------------------------------

    @Test
    @DisplayName("the caret happy path fires no stub WARNs")
    void theHappyPathIsWarnFree() {
        captureWarns();
        JTextField tf = new JTextField("hello world");
        tf.setCaretPosition(3);
        tf.moveCaretPosition(8);
        tf.getSelectedText();
        tf.replaceSelection("X");
        tf.selectAll();
        tf.getCaret().setDot(0);
        tf.setNavigationFilter(new NavigationFilter());
        tf.setCaretPosition(1);
        assertNoWarns(warns, "caret + selection are implemented; none of this should WARN");
    }
}
