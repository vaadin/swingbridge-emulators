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
import com.vaadin.flow.component.textfield.PasswordField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJPasswordField;
import vaadinx.AbstractKaribuTest;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JPasswordFieldTest extends AbstractKaribuTest {

    /**
     * The document's whole content, read directly — so a two-layer sync
     * assertion names the Document it means rather than routing through
     * whichever layer {@code getText()} happens to consult.
     */
    private static String cleartext(Document doc) throws BadLocationException {
        return doc.getText(0, doc.getLength());
    }

    @Test
    @DisplayName("can instantiate with PasswordField peer")
    void peerIsAPasswordField() {
        JPasswordField pf = new JPasswordField();
        // SJPasswordField IS-A PasswordField — Karibu
        // locators that find by PasswordField.class still resolve.
        assertInstanceOf(PasswordField.class, pf.getPeer());
    }

    @Test
    @DisplayName("peer is an SJPasswordField surrogate")
    void peerIsAnSJPasswordField() {
        JPasswordField pf = new JPasswordField();
        assertInstanceOf(SJPasswordField.class, pf.getPeer());
    }

    @Test
    @DisplayName("two-layer R_swing_is_truth sync delivers consistent cleartext on both layers")
    void twoLayerSyncKeepsBothDocumentsInStep() throws BadLocationException {
        // SD_sjtextfield two-layer pattern, lifted from JTextField: emulator's
        // Document and surrogate's Document are independent objects,
        // both reach the same text via the two-layer R_swing_is_truth sync.
        // The check goes through each Document directly so a failure names
        // the layer that drifted.
        JPasswordField pf = new JPasswordField();
        pf.setText("from-emulator");

        // Emulator-side Document holds cleartext.
        assertEquals("from-emulator", cleartext(pf.getDocument()));
        // Surrogate-side Document holds cleartext too.
        SJPasswordField sjp = (SJPasswordField) pf.getPeer();
        assertEquals("from-emulator", cleartext(sjp.getDocument()));
        // Vaadin peer value also in sync.
        assertEquals("from-emulator", ((PasswordField) pf.getPeer()).getValue());
    }

    @Test
    @DisplayName("peer value change reaches both layers' Documents (browser-typed)")
    void peerValueChangeReachesBothDocuments() throws BadLocationException {
        JPasswordField pf = new JPasswordField();
        ((PasswordField) pf.getPeer()).setValue("browser-typed");
        assertEquals("browser-typed", cleartext(pf.getDocument()));
        SJPasswordField sjp = (SJPasswordField) pf.getPeer();
        assertEquals("browser-typed", cleartext(sjp.getDocument()));
    }

    @Test
    @DisplayName("string ctor seeds document text")
    void stringCtorSeedsDocument() throws BadLocationException {
        JPasswordField pf = new JPasswordField("hunter2");
        assertEquals("hunter2", cleartext(pf.getDocument()));
    }

    @Test
    @DisplayName("setText mirrors into peer value via inherited R_swing_is_truth sync")
    void setTextMirrorsIntoPeerValue() {
        // Proves the sync lifted into JTextComponent works for
        // PasswordField (a TextFieldBase) without JPasswordField
        // re-installing the listener.
        JPasswordField pf = new JPasswordField();
        pf.setText("s3cret");
        assertEquals("s3cret", ((PasswordField) pf.getPeer()).getValue());
    }

    @Test
    @DisplayName("peer value change mirrors back into document")
    void peerValueChangeMirrorsIntoDocument() throws BadLocationException {
        JPasswordField pf = new JPasswordField("old");
        ((PasswordField) pf.getPeer()).setValue("new");
        assertEquals("new", cleartext(pf.getDocument()));
    }

    @Test
    @DisplayName("getPassword returns document characters")
    void getPasswordReturnsDocumentCharacters() {
        JPasswordField pf = new JPasswordField("abc");
        assertArrayEquals(new char[] {'a', 'b', 'c'}, pf.getPassword());
    }

    @Test
    @DisplayName("getPassword on empty field returns empty array")
    void getPasswordOnEmptyFieldIsEmpty() {
        assertEquals(0, new JPasswordField().getPassword().length);
    }

    @Test
    @DisplayName("getText returns cleartext, never a mask")
    @SuppressWarnings("deprecation")
    void getTextReturnsCleartext() {
        // Measured against real JDK 25: `new JPasswordField(); setText("ADMIN");
        // getText()` → "ADMIN". The JDK's body is `return super.getText()`; it
        // exists only to carry @Deprecated, and echoChar reaches no getter.
        // Regression pin: masking here silently broke every migrated login
        // screen that used the (deprecated but ubiquitous) getText() idiom.
        JPasswordField pf = new JPasswordField("abc");
        assertEquals("abc", pf.getText());
    }

    @Test
    @DisplayName("echoChar does not affect getText in either direction")
    @SuppressWarnings("deprecation")
    void echoCharDoesNotAffectGetText() {
        JPasswordField pf = new JPasswordField("abc");
        pf.setEchoChar((char) 0);
        assertEquals("abc", pf.getText());
        pf.setEchoChar('#');
        assertEquals("abc", pf.getText());
    }

    @Test
    @DisplayName("getText reads this layer's own Document, not the surrogate's")
    @SuppressWarnings("deprecation")
    void getTextReadsOwnDocument() throws BadLocationException {
        // getText() goes through super (JTextComponent), so the
        // BadLocationException bounds belong to the Document the caller holds.
        JPasswordField pf = new JPasswordField("abcdef");
        assertEquals("bc", pf.getText(1, 2));
        assertThrows(BadLocationException.class, () -> pf.getText(0, 99));
    }

    @Test
    @DisplayName("setEchoChar fires no bound property")
    void setEchoCharFiresNoBoundProperty() {
        // JPasswordField.setEchoChar assigns, then repaint() + revalidate();
        // "echoChar" is not a bound property at either layer
        // (D_property_fanout_audit, SD_property_fanout_audit).
        JPasswordField pf = new JPasswordField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        pf.addPropertyChangeListener("echoChar", events::add);
        pf.setEchoChar('#');
        assertEquals('#', pf.getEchoChar());
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("echoCharIsSet reflects zero vs non-zero")
    void echoCharIsSetReflectsZero() {
        JPasswordField pf = new JPasswordField();
        assertTrue(pf.echoCharIsSet());
        pf.setEchoChar((char) 0);
        assertFalse(pf.echoCharIsSet());
    }

    @Test
    @DisplayName("setEditable propagates to peer readOnly via inherited sync")
    void setEditablePropagatesToPeerReadOnly() {
        // Proves the lifted setEditable override works against the
        // PasswordField peer (TextFieldBase.setReadOnly).
        JPasswordField pf = new JPasswordField();
        pf.setEditable(false);
        assertTrue(((PasswordField) pf.getPeer()).isReadOnly());
    }

    @Test
    @DisplayName("columns ctor sets peer width via inherited applyColumnsToPeer")
    void columnsCtorSetsPeerWidth() {
        JPasswordField pf = new JPasswordField(20);
        assertEquals(20, pf.getColumns());
        assertEquals("var(--emul-layout-w, calc(20ch + 2em))", ((PasswordField) pf.getPeer()).getWidth());
    }

    @Test
    @DisplayName("ctor throws IAE on negative columns")
    void ctorRejectsNegativeColumns() {
        assertThrows(IllegalArgumentException.class, () -> new JPasswordField(null, null, -1));
    }

    @Test
    @DisplayName("setText while hosted reaches the rendered PasswordField")
    void setTextWhileHostedReachesTheRenderedField() {
        JPasswordField pf = new JPasswordField("before");
        JFrame frame = new JFrame();
        frame.add(pf);
        frame.setVisible(true);

        assertEquals("before", LocatorJ._get(PasswordField.class).getValue());
        pf.setText("after");
        assertEquals("after", LocatorJ._get(PasswordField.class).getValue());
    }

    @Test
    @DisplayName("copy logs onUnsupported and does not throw")
    void copyIsALoggedNoOp() {
        // Swing beeps and declines; we log + no-op. R_match_swing_errors: never throw
        // just because the behaviour is intentionally refused.
        new JPasswordField("secret").copy();
    }

    @Test
    @DisplayName("getUIClassID is PasswordFieldUI")
    void uiClassIdIsPasswordFieldUI() {
        assertEquals("PasswordFieldUI", new JPasswordField().getUIClassID());
    }

    @Test
    @DisplayName("getPassword reads this layer's own Document, and answers null when it refuses")
    void getPasswordReadsOwnDocument() {
        // Measured on JDK 25: a Document whose getText throws makes getPassword return null.
        JPasswordField pf = new JPasswordField("abc");
        pf.setDocument(new javax.swing.text.PlainDocument() {
            @Override
            public void getText(int offset, int length, javax.swing.text.Segment txt) throws BadLocationException {
                throw new BadLocationException("refused", 0);
            }
        });
        assertEquals(null, pf.getPassword());
    }

    @Test
    @DisplayName("setEchoChar pushes on to the surrogate, and paramString prints the raw char")
    void echoCharPushesToSurrogate() {
        JPasswordField pf = new JPasswordField();
        pf.setEchoChar('#');
        assertEquals('#', ((SJPasswordField) pf.getPeer()).getEchoChar());
        assertTrue(pf.toString().endsWith(",echoChar=#]"), pf.toString());
    }
}
