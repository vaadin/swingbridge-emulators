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

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JTextComponentTest extends AbstractKaribuTest {

    /** Minimal concrete subclass for exercising the abstract base. */
    private static class TestTextComponent extends JTextComponent {
        TestTextComponent() {
            super(new Div());
        }
    }

    /** Records which DocumentListener method fired, in order. */
    private static List<String> recordEvents(Document doc) {
        List<String> events = new ArrayList<>();
        doc.addDocumentListener(new DocumentListener() {
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
        return events;
    }

    @Test
    @DisplayName("peer ctor installs a PlainDocument by default")
    void peerCtorInstallsAPlainDocument() {
        TestTextComponent c = new TestTextComponent();
        assertInstanceOf(PlainDocument.class, c.getDocument());
    }

    @Test
    @DisplayName("setText writes through the Document and getText reads back")
    void setTextWritesThroughTheDocument() throws BadLocationException {
        TestTextComponent c = new TestTextComponent();
        c.setText("hello");
        assertEquals("hello", c.getText());
        assertEquals("hello", c.getDocument().getText(0, c.getDocument().getLength()));
    }

    @Test
    @DisplayName("setText null normalises to empty")
    void setTextNullNormalisesToEmpty() {
        // Swing's setText tolerates null by virtue of AbstractDocument's
        // early-return; we handle it explicitly at this level.
        TestTextComponent c = new TestTextComponent();
        c.setText("something");
        c.setText(null);
        assertEquals("", c.getText());
    }

    @Test
    @DisplayName("setText fires DocumentListener insert and remove events")
    void setTextFiresInsertAndRemove() {
        // The dispatch path R_swing_is_truth relies on: Document mutations fire
        // DocumentListener events, which subclasses hook to drive
        // their peer.
        TestTextComponent c = new TestTextComponent();
        c.setText("seed");

        List<String> events = recordEvents(c.getDocument());

        c.setText("replaced");

        // Two events: remove the old content, insert the new.
        assertEquals(List.of("remove", "insert"), events);
    }

    @Test
    @DisplayName("setDocument swaps the Document and fires document PropertyChangeEvent")
    void setDocumentSwapsAndFires() {
        TestTextComponent c = new TestTextComponent();
        Document old = c.getDocument();
        Document fresh = new PlainDocument();

        List<PropertyChangeEvent> events = new ArrayList<>();
        c.addPropertyChangeListener("document", events::add);

        c.setDocument(fresh);

        assertSame(fresh, c.getDocument());
        assertNotSame(old, c.getDocument());
        PropertyChangeEvent pce = assertSingle(events);
        assertSame(old, pce.getOldValue());
        assertSame(fresh, pce.getNewValue());
    }

    @Test
    @DisplayName("setDocument to the same document is a no-op")
    void setDocumentToTheSameIsANoOp() {
        TestTextComponent c = new TestTextComponent();
        List<PropertyChangeEvent> events = new ArrayList<>();
        c.addPropertyChangeListener("document", events::add);
        c.setDocument(c.getDocument());
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("setEditable round-trips and fires editable PropertyChangeEvent")
    void setEditableRoundTripsAndFires() {
        TestTextComponent c = new TestTextComponent();
        assertTrue(c.isEditable());  // Swing default

        List<PropertyChangeEvent> events = new ArrayList<>();
        c.addPropertyChangeListener("editable", events::add);

        c.setEditable(false);

        assertFalse(c.isEditable());
        PropertyChangeEvent pce = assertSingle(events);
        assertEquals(Boolean.TRUE, pce.getOldValue());
        assertEquals(Boolean.FALSE, pce.getNewValue());
    }

    @Test
    @DisplayName("setEditable no-op (equal value) fires no event")
    void setEditableNoOpFiresNothing() {
        TestTextComponent c = new TestTextComponent();
        List<PropertyChangeEvent> events = new ArrayList<>();
        c.addPropertyChangeListener("editable", events::add);
        c.setEditable(true);  // already true
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("getText offset len reads the requested slice")
    void getTextSliceReadsTheSlice() throws BadLocationException {
        TestTextComponent c = new TestTextComponent();
        c.setText("abcdefghij");
        assertEquals("cdef", c.getText(2, 4));
    }
}
