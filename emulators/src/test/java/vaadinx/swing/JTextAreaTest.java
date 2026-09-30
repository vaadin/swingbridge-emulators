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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.swingbridge.surrogates.SJTextArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.PlainDocument;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JTextAreaTest extends AbstractKaribuTest {

    @Test
    @DisplayName("can instantiate with TextArea peer")
    void peerIsATextArea() {
        JTextArea ta = new JTextArea();
        assertInstanceOf(TextArea.class, ta.getPeer());
    }

    @Test
    @DisplayName("string ctor seeds document and peer value")
    void stringCtorSeedsDocumentAndPeer() {
        JTextArea ta = new JTextArea("hello");
        assertEquals("hello", ta.getText());
        assertEquals("hello", ((TextArea) ta.getPeer()).getValue());
    }

    @Test
    @DisplayName("rows and columns ctor stores dimensions and sizes the peer")
    void rowsAndColumnsCtorSizesThePeer() {
        JTextArea ta = new JTextArea(5, 30);
        assertEquals(5, ta.getRows());
        assertEquals(30, ta.getColumns());
        assertEquals("var(--emul-layout-w, calc(30ch + 2em))", ((TextArea) ta.getPeer()).getWidth());
        assertEquals(5, ((TextArea) ta.getPeer()).getMinRows());
    }

    @Test
    @DisplayName("setText mirrors into peer value via inherited R_swing_is_truth sync")
    void setTextMirrorsIntoPeer() {
        // JTextArea extends JTextComponent directly — the lifted sync
        // should cover it without any override in this class.
        JTextArea ta = new JTextArea();
        ta.setText("typed");
        assertEquals("typed", ((TextArea) ta.getPeer()).getValue());
    }

    @Test
    @DisplayName("peer value change mirrors back into document")
    void peerValueMirrorsIntoDocument() {
        JTextArea ta = new JTextArea("old");
        ((TextArea) ta.getPeer()).setValue("new");
        assertEquals("new", ta.getText());
    }

    @Test
    @DisplayName("append adds at the end of the document")
    void appendAddsAtTheEnd() {
        JTextArea ta = new JTextArea("hello ");
        ta.append("world");
        assertEquals("hello world", ta.getText());
    }

    @Test
    @DisplayName("append null is a no-op matching JDK")
    void appendNullIsANoOp() {
        JTextArea ta = new JTextArea("stable");
        ta.append(null);
        assertEquals("stable", ta.getText());
    }

    @Test
    @DisplayName("insert places text at the given offset")
    void insertPlacesTextAtOffset() {
        JTextArea ta = new JTextArea("ac");
        ta.insert("b", 1);
        assertEquals("abc", ta.getText());
    }

    @Test
    @DisplayName("insert with out-of-range pos throws IAE")
    void insertRejectsOutOfRange() {
        JTextArea ta = new JTextArea("abc");
        assertThrows(IllegalArgumentException.class, () -> ta.insert("x", -1));
        assertThrows(IllegalArgumentException.class, () -> ta.insert("x", 99));
    }

    @Test
    @DisplayName("replaceRange swaps a region of the document")
    void replaceRangeSwapsARegion() {
        JTextArea ta = new JTextArea("hello world");
        ta.replaceRange("there", 6, 11);
        assertEquals("hello there", ta.getText());
    }

    @Test
    @DisplayName("replaceRange with null deletes the range")
    void replaceRangeWithNullDeletes() {
        // JDK: null str means "just delete" — same as
        // doc.remove(start, end-start).
        JTextArea ta = new JTextArea("hello world");
        ta.replaceRange(null, 5, 11);
        assertEquals("hello", ta.getText());
    }

    @Test
    @DisplayName("replaceRange with bad bounds throws IAE")
    void replaceRangeRejectsBadBounds() {
        JTextArea ta = new JTextArea("abc");
        assertThrows(IllegalArgumentException.class, () -> ta.replaceRange("x", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> ta.replaceRange("x", 2, 1));
        assertThrows(IllegalArgumentException.class, () -> ta.replaceRange("x", 1, 99));
    }

    @Test
    @DisplayName("getLineCount counts document lines")
    void lineCountCountsLines() {
        JTextArea ta = new JTextArea("one\ntwo\nthree");
        assertEquals(3, ta.getLineCount());
    }

    @Test
    @DisplayName("getLineOfOffset finds the containing line")
    void lineOfOffsetFindsTheLine() throws BadLocationException {
        JTextArea ta = new JTextArea("one\ntwo\nthree");
        assertEquals(0, ta.getLineOfOffset(0));
        assertEquals(1, ta.getLineOfOffset(5));  // mid-line 'two'
        assertEquals(2, ta.getLineOfOffset(9));  // mid-line 'three'
    }

    @Test
    @DisplayName("getLineStartOffset and getLineEndOffset match PlainDocument lines")
    void lineStartAndEndOffsets() throws BadLocationException {
        JTextArea ta = new JTextArea("one\ntwo\nthree");
        assertEquals(0, ta.getLineStartOffset(0));
        assertEquals(4, ta.getLineStartOffset(1));  // "one\n" is 4 chars
        assertEquals(8, ta.getLineStartOffset(2));
        // End offsets include the trailing newline (or EOF for the
        // last line), matching PlainDocument's element spec.
        assertEquals(4, ta.getLineEndOffset(0));
    }

    @Test
    @DisplayName("getLineOfOffset with bad offset throws BadLocationException")
    void lineOfOffsetRejectsBadOffsets() {
        JTextArea ta = new JTextArea("abc");
        assertThrows(BadLocationException.class, () -> ta.getLineOfOffset(-1));
        assertThrows(BadLocationException.class, () -> ta.getLineOfOffset(99));
    }

    @Test
    @DisplayName("setLineWrap fires PCE")
    void setLineWrapFiresPce() {
        JTextArea ta = new JTextArea();
        List<PropertyChangeEvent> events = new ArrayList<>();
        ta.addPropertyChangeListener("lineWrap", events::add);
        ta.setLineWrap(true);
        assertEquals(Boolean.TRUE, assertSingle(events).getNewValue());
    }

    @Test
    @DisplayName("setTabSize fires PCE")
    void setTabSizeFiresPce() {
        JTextArea ta = new JTextArea();
        List<PropertyChangeEvent> events = new ArrayList<>();
        ta.addPropertyChangeListener("tabSize", events::add);
        ta.setTabSize(4);
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setEditable propagates to peer readOnly via inherited sync")
    void setEditablePropagatesToPeer() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        assertTrue(((TextArea) ta.getPeer()).isReadOnly());
    }

    @Test
    @DisplayName("setRows throws IAE on negative")
    void setRowsRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new JTextArea().setRows(-1));
    }

    @Test
    @DisplayName("setDocument swaps and mirrors new content to peer")
    void setDocumentSwapsAndMirrors() throws BadLocationException {
        JTextArea ta = new JTextArea("initial");
        PlainDocument newDoc = new PlainDocument();
        newDoc.insertString(0, "replaced", null);

        ta.setDocument(newDoc);

        assertEquals("replaced", ((TextArea) ta.getPeer()).getValue());

        newDoc.remove(0, newDoc.getLength());
        newDoc.insertString(0, "mutated", null);
        assertEquals("mutated", ((TextArea) ta.getPeer()).getValue());
    }

    @Test
    @DisplayName("setText while hosted reaches the rendered TextArea")
    void setTextWhileHostedReachesTheRenderedArea() {
        JTextArea ta = new JTextArea("before");
        JFrame frame = new JFrame();
        frame.add(ta);
        frame.setVisible(true);

        assertEquals("before", LocatorJ._get(TextArea.class).getValue());
        ta.setText("after");
        assertEquals("after", LocatorJ._get(TextArea.class).getValue());
    }

    @Test
    @DisplayName("getUIClassID is TextAreaUI")
    void uiClassIdIsTextAreaUI() {
        assertEquals("TextAreaUI", new JTextArea().getUIClassID());
    }

    @Test
    @DisplayName("getScrollableTracksViewportWidth tracks lineWrap")
    void scrollableTracksViewportWidthTracksLineWrap() {
        // Swing: true when lineWrap is on. Best-effort carries through.
        JTextArea ta = new JTextArea();
        assertFalse(ta.getScrollableTracksViewportWidth());
        ta.setLineWrap(true);
        assertTrue(ta.getScrollableTracksViewportWidth());
    }

    @Test
    @DisplayName("tabSize lives on the Document, and the rest match the JDK's script")
    void stateMatchesTheJdk() {
        // Replays a headless JDK 25 script; every expected line below is what it printed.
        JTextArea ta = new JTextArea("hello", 3, 10);
        List<String> log = new ArrayList<>();
        ta.addPropertyChangeListener(e -> {
            if (List.of("tabSize", "lineWrap", "wrapStyleWord").contains(e.getPropertyName())) {
                log.add(e.getPropertyName() + " " + e.getOldValue() + "->" + e.getNewValue());
            }
        });
        log.add("tab=" + ta.getTabSize() + " wrap=" + ta.getLineWrap() + " word=" + ta.getWrapStyleWord()
                + " rows=" + ta.getRows() + " cols=" + ta.getColumns());
        ta.setTabSize(4);
        ta.setTabSize(4);
        log.add("tab=" + ta.getTabSize() + " docprop=" + ta.getDocument().getProperty(PlainDocument.tabSizeAttribute));
        ta.getDocument().putProperty(PlainDocument.tabSizeAttribute, 2);
        log.add("after doc putProperty tab=" + ta.getTabSize());
        ta.setDocument(new PlainDocument());
        log.add("after new PlainDocument tab=" + ta.getTabSize());
        ta.setDocument(new DefaultStyledDocument());
        log.add("after DefaultStyledDocument tab=" + ta.getTabSize());
        ta.setLineWrap(true);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setRows(5);
        ta.setColumns(7);
        ta.setLineWrap(false);
        String ps = ta.toString();
        log.add("ps=" + ps.substring(ps.indexOf(",columns=")));

        assertEquals(List.of(
                "tab=8 wrap=false word=false rows=3 cols=10",
                "tabSize 8->4",
                "tab=4 docprop=4",
                "after doc putProperty tab=2",
                "after new PlainDocument tab=8",
                "after DefaultStyledDocument tab=8",
                "lineWrap false->true",
                "wrapStyleWord false->true",
                "lineWrap true->false",
                "ps=,columns=7,columWidth=0,rows=5,rowHeight=0,word=true,wrap=false]"), log);
    }

    @Test
    @DisplayName("setters push their value on to the surrogate")
    void settersPushToTheSurrogate() {
        JTextArea ta = new JTextArea(2, 4);
        SJTextArea surrogate = (SJTextArea) ta.getPeer();
        assertEquals(2, surrogate.getRows());
        assertEquals(4, surrogate.getColumns());
        ta.setRows(6);
        ta.setColumns(9);
        ta.setTabSize(3);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        assertEquals(6, surrogate.getRows());
        assertEquals(9, surrogate.getColumns());
        assertEquals(3, surrogate.getTabSize());
        assertTrue(surrogate.getLineWrap());
        assertTrue(surrogate.getWrapStyleWord());
    }

    @Test
    @DisplayName("the ctor assigns rows and columns without calling the overridable setters")
    void ctorDoesNotCallSetters() {
        // The JDK's ctor writes the fields directly.
        List<String> calls = new ArrayList<>();
        JTextArea ta = new JTextArea(2, 4) {
            @Override
            public void setRows(int rows) {
                calls.add("setRows");
                super.setRows(rows);
            }

            @Override
            public void setColumns(int columns) {
                calls.add("setColumns");
                super.setColumns(columns);
            }
        };
        assertEquals(List.of(), calls);
        assertEquals(2, ta.getRows());
        assertEquals(4, ta.getColumns());
    }
}
