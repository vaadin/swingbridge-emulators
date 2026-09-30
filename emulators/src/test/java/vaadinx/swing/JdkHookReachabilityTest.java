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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.BorderLayout;
import vaadinx.swing.table.TableCellEditor;

import javax.swing.table.DefaultTableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R_no_vaadin_in_api's second limb (D_r12_provenance): a JDK hook the emulator exposes must be
 * <i>reached</i> by the internal path that reaches it in Swing. Each test here overrides one hook
 * and asserts the framework ran it — the regression these guard against is
 * silent, since an unreachable hook still compiles and still looks wired.
 *
 * <p>The dead-hook sweep behind this file is tabulated in D_r12_provenance; hooks left
 * deliberately unreachable (painting, L&amp;F dispatch, low-level key dispatch) are
 * out of scope by policy and are not listed here.
 */
class JdkHookReachabilityTest extends AbstractKaribuTest {

    // --- JTextField / JTextArea: createDefaultModel ----------------------

    /** A filtering Document, the reason {@code createDefaultModel} exists in the JDK. */
    private static class UpperCaseDocument extends PlainDocument {
        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            // Locale.ROOT to match Kotlin's locale-independent uppercase(), so a
            // Turkish-locale JVM doesn't turn "abc" into "ABC" with a dotted İ.
            super.insertString(offs, str == null ? null : str.toUpperCase(Locale.ROOT), a);
        }
    }

    /** The (row, column) pair a hook was called with — named fields beat a Pair at the assertion. */
    private record Cell(int row, int column) {
    }

    @Test
    @DisplayName("JTextField createDefaultModel override installs the custom Document")
    void jTextFieldCreateDefaultModelIsReached() {
        // The shape of testapps/inventory's NumberTextField, which casts
        // getDocument() to its own type — a plain PlainDocument here would be a
        // ClassCastException on the app's first setMaxLength call.
        JTextField field = new JTextField() {
            @Override
            protected Document createDefaultModel() {
                return new UpperCaseDocument();
            }
        };
        assertInstanceOf(UpperCaseDocument.class, field.getDocument());

        field.setText("abc");
        assertEquals("ABC", field.getText(),
                "the custom Document must be the live one, not a bypassed copy");
    }

    @Test
    @DisplayName("JTextArea createDefaultModel override installs the custom Document")
    void jTextAreaCreateDefaultModelIsReached() {
        JTextArea area = new JTextArea() {
            @Override
            protected Document createDefaultModel() {
                return new UpperCaseDocument();
            }
        };
        assertInstanceOf(UpperCaseDocument.class, area.getDocument());
        area.setText("xy");
        assertEquals("XY", area.getText());
    }

    @Test
    @DisplayName("default JTextField document is still a plain PlainDocument")
    void defaultDocumentIsUnchanged() {
        // The routing must not change the un-overridden default.
        assertEquals(PlainDocument.class, new JTextField().getDocument().getClass());
        assertEquals(PlainDocument.class, new JTextArea().getDocument().getClass());
    }

    // --- JTable: prepareEditor -------------------------------------------

    @Test
    @DisplayName("prepareEditor override runs on the edit path")
    void prepareEditorIsReached() {
        List<Cell> calls = new ArrayList<>();
        JTable table = new JTable(
                new DefaultTableModel(new Object[][] {{"a"}}, new Object[] {"c0"})) {
            @Override
            public vaadinx.awt.Component prepareEditor(TableCellEditor editor, int row, int column) {
                calls.add(new Cell(row, column));
                return super.prepareEditor(editor, row, column);
            }
        };

        assertTrue(table.editCellAt(0, 0, null), "the cell should open for editing");
        assertEquals(List.of(new Cell(0, 0)), calls, "editCellAt must call through prepareEditor");
    }

    // --- JFrame family: isRootPaneCheckingEnabled ------------------------

    @Test
    @DisplayName("isRootPaneCheckingEnabled override diverts add away from the content pane")
    void rootPaneCheckingOffDivertsAdd() {
        // Swing's documented seam for "add directly to me, not my content pane".
        JFrame frame = new JFrame() {
            @Override
            protected boolean isRootPaneCheckingEnabled() {
                return false;
            }
        };
        JButton button = new JButton("b");
        frame.add(button);

        assertEquals(0, frame.getContentPane().getComponentCount(),
                "with checking off, add must not reach the content pane");
        assertSame(button, frame.getComponent(frame.getComponentCount() - 1));
    }

    @Test
    @DisplayName("the default frame still redirects add into the content pane")
    void defaultFrameRedirectsAddToContentPane() {
        JFrame frame = new JFrame();
        JButton button = new JButton("b");
        frame.add(button, BorderLayout.CENTER);
        assertEquals(1, frame.getContentPane().getComponentCount());
        assertSame(button, frame.getContentPane().getComponent(0));
    }
}
