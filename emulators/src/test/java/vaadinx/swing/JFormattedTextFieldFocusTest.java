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

import com.vaadin.flow.component.BlurNotifier;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focus-lost commit contract for JFormattedTextField (bug: typing then tabbing
 * out left the value stale — only Enter committed). Real Swing commits on focus
 * loss per {@code focusLostBehavior} (COMMIT_OR_REVERT default). The peer's blur
 * is the browser-side trigger; committing re-pushes the formatted text through
 * the R_swing_is_truth Document sync, so a user DocumentListener fires and downstream recompute
 * logic runs — exactly the JLawyer InvoicePositionEntryPanel "Menge → Gesamt"
 * chain that was broken.
 *
 * <p>The panel builds the field as {@code new JFormattedTextField()} + a NumberFormatter
 * factory, which pins DefaultFormattedStrategy (peer = SJFormattedTextField, a
 * Vaadin TextField) — reproduced here.
 */
class JFormattedTextFieldFocusTest extends AbstractKaribuTest {

    private static JFormattedTextField numberField() {
        JFormattedTextField f = new JFormattedTextField();
        f.setFormatterFactory(new DefaultFormatterFactory(
                new NumberFormatter(new DecimalFormat("#0.00", new DecimalFormatSymbols(Locale.US)))));
        return f;
    }

    private static void fireBlur(JFormattedTextField f) {
        TextField peer = (TextField) f.getPeer();
        ComponentUtil.fireEvent(peer, new BlurNotifier.BlurEvent<>(peer, true));
    }

    @Test
    @DisplayName("focus-lost commits the typed text, reformats, and fires DocumentListener "
            + "(COMMIT_OR_REVERT default)")
    void focusLostCommitsAndReformats() {
        JFormattedTextField f = numberField();
        f.setValue(1);

        List<String> docTexts = new ArrayList<>();
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                docTexts.add(f.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                docTexts.add(f.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        // Simulate typing "4" into the browser field: the peer value changes,
        // the EAGER Document sync mirrors it into getText(), but the Object value
        // does NOT commit until Enter or focus loss.
        ((TextField) f.getPeer()).setValue("4");
        assertEquals(Integer.valueOf(1), f.getValue());

        // Tab out (focus lost) → COMMIT_OR_REVERT commits.
        fireBlur(f);

        assertEquals(4L, ((Number) f.getValue()).longValue());
        assertEquals("4.00", f.getText());             // reformatted to the formatter's shape
        assertTrue(docTexts.contains("4.00"));        // the commit fired the DocumentListener
    }

    @Test
    @DisplayName("PERSIST focus-lost behavior leaves the value uncommitted on blur")
    void persistLeavesValueUncommitted() {
        JFormattedTextField f = numberField();
        f.setValue(1);
        f.setFocusLostBehavior(JFormattedTextField.PERSIST);

        ((TextField) f.getPeer()).setValue("7");
        fireBlur(f);

        // PERSIST: the typed text stays, the Object value is not committed.
        assertEquals(Integer.valueOf(1), f.getValue());
    }
}
