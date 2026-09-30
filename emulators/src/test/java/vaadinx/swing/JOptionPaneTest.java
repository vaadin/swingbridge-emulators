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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JOptionPaneTest extends AbstractKaribuTest {

    // ------- Constants sanity (re-declared by us; guard JDK-faithfulness) -------

    @Test
    @DisplayName("constants match JDK values")
    void constantsMatchJdkValues() {
        assertEquals(0, JOptionPane.ERROR_MESSAGE);
        assertEquals(1, JOptionPane.INFORMATION_MESSAGE);
        assertEquals(2, JOptionPane.WARNING_MESSAGE);
        assertEquals(3, JOptionPane.QUESTION_MESSAGE);
        assertEquals(-1, JOptionPane.PLAIN_MESSAGE);
        assertEquals(-1, JOptionPane.DEFAULT_OPTION);
        assertEquals(0, JOptionPane.YES_NO_OPTION);
        assertEquals(1, JOptionPane.YES_NO_CANCEL_OPTION);
        assertEquals(2, JOptionPane.OK_CANCEL_OPTION);
        assertEquals(0, JOptionPane.YES_OPTION);
        assertEquals(0, JOptionPane.OK_OPTION);
        assertEquals(1, JOptionPane.NO_OPTION);
        assertEquals(2, JOptionPane.CANCEL_OPTION);
        assertEquals(-1, JOptionPane.CLOSED_OPTION);
    }

    // ------- Instance API: ctors, getters/setters, PCE -------

    @Test
    @DisplayName("default ctor seeds sensible defaults")
    void defaultCtorSeedsSensibleDefaults() {
        JOptionPane p = new JOptionPane();
        assertEquals("JOptionPane message", p.getMessage());
        assertEquals(JOptionPane.PLAIN_MESSAGE, p.getMessageType());
        assertEquals(JOptionPane.DEFAULT_OPTION, p.getOptionType());
        assertSame(JOptionPane.UNINITIALIZED_VALUE, p.getValue());
    }

    @Test
    @DisplayName("setMessage fires MESSAGE_PROPERTY PCE")
    void setMessageFiresMessagePropertyPce() {
        JOptionPane p = new JOptionPane("first");
        Counter fired = new Counter();
        p.addPropertyChangeListener(JOptionPane.MESSAGE_PROPERTY, e -> {
            assertEquals("first", e.getOldValue());
            assertEquals("second", e.getNewValue());
            fired.inc();
        });
        p.setMessage("second");
        assertTrue(fired.get() > 0);
    }

    @Test
    @DisplayName("setSelectionValues forces wantsInput true per JDK contract")
    void setSelectionValuesForcesWantsInput() {
        JOptionPane p = new JOptionPane();
        assertFalse(p.getWantsInput());
        p.setSelectionValues(new Object[]{"a", "b", "c"});
        assertTrue(p.getWantsInput());
    }

    @Test
    @DisplayName("bad messageType throws a bare RuntimeException, as javax.swing does")
    void badMessageTypeThrows() {
        // R_match_swing_errors wants Swing's own exception *type*, and Swing's is a bare
        // RuntimeException here — not the IllegalArgumentException the message implies
        // (measured on JDK 25).
        RuntimeException e = assertThrows(RuntimeException.class, () -> new JOptionPane("msg", 99));
        assertFalse(e instanceof IllegalArgumentException);
    }

    @Test
    @DisplayName("bad optionType throws a bare RuntimeException, as javax.swing does")
    void badOptionTypeThrows() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> new JOptionPane("msg", JOptionPane.PLAIN_MESSAGE, 42));
        assertFalse(e instanceof IllegalArgumentException);
    }

    // ------- createDialog: shape and population -------

    @Test
    @DisplayName("createDialog returns a modal JDialog with this pane as content")
    void createDialogReturnsAModalDialogHoldingThePane() {
        JOptionPane p = new JOptionPane("hi", JOptionPane.INFORMATION_MESSAGE);
        JDialog d = p.createDialog(null, "Title");
        assertTrue(d.isModal());
        assertEquals("Title", d.getTitle());
        // The pane is plonked into the dialog's contentPane.
        assertTrue(Arrays.asList(d.getContentPane().getComponents()).contains(p));
    }

    /**
     * Shows a freshly-built dialog non-modally so content-shape tests can
     * inspect rendered children via Karibu without burning a VT carrier.
     * createDialog sets the dialog modal per JDK contract; the modal-park
     * end-to-end flow is exercised separately in the show*Dialog tests.
     */
    private static void showNonModally(JDialog d) {
        d.setModal(false);
        d.setVisible(true);
    }

    @Test
    @DisplayName("createDialog populates default OK button for DEFAULT_OPTION")
    void createDialogPopulatesOkForDefaultOption() {
        JOptionPane p = new JOptionPane("Done", JOptionPane.INFORMATION_MESSAGE);
        JDialog d = p.createDialog(null, "Info");
        showNonModally(d);
        try {
            // Button row should contain a single "OK" button — _get asserts
            // exactly one Button in the tree and dumps it on mismatch.
            assertEquals("OK", LocatorJ._get(Button.class).getText());
        } finally {
            d.dispose();
        }
    }

    @Test
    @DisplayName("createDialog populates Yes No Cancel for YES_NO_CANCEL_OPTION")
    void createDialogPopulatesYesNoCancel() {
        JOptionPane p = new JOptionPane("Sure?", JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION);
        JDialog d = p.createDialog(null, "Confirm");
        showNonModally(d);
        try {
            Set<String> labels = LocatorJ._find(Button.class).stream()
                    .map(Button::getText).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("Yes", "No", "Cancel"), labels);
        } finally {
            d.dispose();
        }
    }

    @Test
    @DisplayName("messageType drives icon dispatch — ERROR adds a glyph PLAIN does not")
    void messageTypeDrivesIconDispatch() {
        // Relative assertion (not absolute "no icons") because the JDialog
        // chrome contributes its own header-close Icon. We compare PLAIN
        // against ERROR — the only difference between the two should be
        // the messageType-driven icon glyph in the option pane header.
        JDialog plainDialog = new JOptionPane("plain", JOptionPane.PLAIN_MESSAGE).createDialog(null, "P");
        showNonModally(plainDialog);
        int plainCount = LocatorJ._find(Icon.class).size();
        plainDialog.dispose();

        JDialog errDialog = new JOptionPane("boom", JOptionPane.ERROR_MESSAGE).createDialog(null, "E");
        showNonModally(errDialog);
        int errCount = LocatorJ._find(Icon.class).size();
        errDialog.dispose();

        assertEquals(plainCount + 1, errCount, "ERROR_MESSAGE should add exactly one icon vs PLAIN_MESSAGE");
    }

    // ------- Static factory: non-VT context throws -------

    @Test
    @DisplayName("showConfirmDialog from non-VT context throws ISE via modal park")
    void showConfirmDialogFromNonVtContextThrows() {
        // R_callswing_envelope: blocking dialogs must be reached from an EHelper.callSwing
        // UI fiber. A direct call from the platform request thread fails fast at
        // parkUntilClose's UIFibers.checkInUIFiber().
        assertThrows(IllegalStateException.class, () -> JOptionPane.showConfirmDialog(null, "Hi"));
    }

    // ------- Static factory: end-to-end park + click + return -------

    @Test
    @DisplayName("showConfirmDialog YES_NO returns YES_OPTION on Yes click")
    void showConfirmDialogReturnsYesOnYesClick() throws InterruptedException {
        int[] ret = {-99};
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            ret[0] = JOptionPane.showConfirmDialog(null, "Sure?", "Confirm", JOptionPane.YES_NO_OPTION);
            finished.countDown();
        });
        // VT parked. Find the Yes button and click it from another callSwing.
        clickButton("Yes");
        assertTrue(finished.await(5, TimeUnit.SECONDS), "showConfirmDialog did not return after Yes click");
        assertEquals(JOptionPane.YES_OPTION, ret[0]);
    }

    @Test
    @DisplayName("showConfirmDialog YES_NO returns NO_OPTION on No click")
    void showConfirmDialogReturnsNoOnNoClick() throws InterruptedException {
        int[] ret = {-99};
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            ret[0] = JOptionPane.showConfirmDialog(null, "Sure?", "Confirm", JOptionPane.YES_NO_OPTION);
            finished.countDown();
        });
        clickButton("No");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(JOptionPane.NO_OPTION, ret[0]);
    }

    @Test
    @DisplayName("showConfirmDialog returns CLOSED_OPTION when dialog disposed without click")
    void showConfirmDialogReturnsClosedOnDispose() throws InterruptedException {
        int[] ret = {-99};
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            ret[0] = JOptionPane.showConfirmDialog(null, "Sure?", "Confirm", JOptionPane.YES_NO_OPTION);
            finished.countDown();
        });
        // Simulate user closing via X / outside-click — find the Vaadin
        // Dialog peer and close it, which fires WINDOW_CLOSING and then
        // DISPOSE_ON_CLOSE disposes (releasing the modal latch).
        Dialog dialog = LocatorJ._get(Dialog.class);
        EHelper.callSwing(dialog::close);
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(JOptionPane.CLOSED_OPTION, ret[0]);
    }

    @Test
    @DisplayName("showMessageDialog returns after OK click")
    void showMessageDialogReturnsAfterOkClick() throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            JOptionPane.showMessageDialog(null, "Done", "Info", JOptionPane.INFORMATION_MESSAGE);
            finished.countDown();
        });
        clickButton("OK");
        assertTrue(finished.await(5, TimeUnit.SECONDS), "showMessageDialog did not return after OK");
    }

    @Test
    @DisplayName("showInputDialog returns text from input field on OK click")
    void showInputDialogReturnsTypedText() throws InterruptedException {
        String[] ret = new String[1];
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            ret[0] = JOptionPane.showInputDialog(null, "Your name?");
            finished.countDown();
        });
        // Type into the text field, then click OK.
        TextField field = LocatorJ._get(TextField.class);
        EHelper.callSwing(() -> field.setValue("Alice"));
        clickButton("OK");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals("Alice", ret[0]);
    }

    @Test
    @DisplayName("showInputDialog returns null on Cancel")
    void showInputDialogReturnsNullOnCancel() throws InterruptedException {
        String[] ret = {"sentinel"};
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            ret[0] = JOptionPane.showInputDialog(null, "Your name?");
            finished.countDown();
        });
        clickButton("Cancel");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertNull(ret[0]);
    }

    @Test
    @DisplayName("showInputDialog with selectionValues returns selected combo item")
    void showInputDialogWithSelectionValuesReturnsComboItem() throws InterruptedException {
        Object[] ret = new Object[1];
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            ret[0] = JOptionPane.showInputDialog(
                    null,
                    "Pick one",
                    "Choose",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new Object[]{"apple", "banana", "cherry"},
                    "banana");
            finished.countDown();
        });
        // Combo defaulted to "banana"; pick "cherry" then OK.
        @SuppressWarnings("unchecked")
        ComboBox<Object> combo = LocatorJ._get(ComboBox.class);
        EHelper.callSwing(() -> combo.setValue("cherry"));
        clickButton("OK");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals("cherry", ret[0]);
    }

    @Test
    @DisplayName("showOptionDialog with custom options returns clicked index")
    void showOptionDialogReturnsClickedIndex() throws InterruptedException {
        int[] ret = {-99};
        CountDownLatch finished = new CountDownLatch(1);
        Object[] opts = {"Save", "Discard", "Keep editing"};
        EHelper.callSwing(() -> {
            ret[0] = JOptionPane.showOptionDialog(
                    null,
                    "You have unsaved changes.",
                    "Confirm",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    opts,
                    opts[0]);
            finished.countDown();
        });
        clickButton("Discard");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(1, ret[0]);
    }

    @Test
    @DisplayName("paramString includes messageType and optionType")
    void paramStringIncludesMessageAndOptionType() {
        ExposedJOptionPane p = new ExposedJOptionPane("hi", JOptionPane.WARNING_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        String s = p.exposedParamString();
        assertTrue(s.contains("messageType=2"), s);
        assertTrue(s.contains("optionType=2"), s);
    }

    /** Locates the button by label and clicks it inside its own callSwing envelope. */
    private static void clickButton(String text) {
        Button b = LocatorJ._get(Button.class, spec -> spec.withText(text));
        EHelper.callSwing(() -> LocatorJ._click(b));
    }

    private static class ExposedJOptionPane extends JOptionPane {

        ExposedJOptionPane(Object msg, int mt, int ot) {
            super(msg, mt, ot);
        }

        String exposedParamString() {
            return paramString();
        }
    }
}
