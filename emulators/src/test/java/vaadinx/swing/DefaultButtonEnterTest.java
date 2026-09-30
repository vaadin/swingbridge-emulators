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

import com.github.mvysny.kaributesting.v10.ShortcutsKt;
import com.vaadin.flow.component.Key;
import com.vaadin.swingbridge.surrogates.FocusTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The crud edit dialog's shape — a window, a default button, and the field that
 * has focus when Enter is pressed. The JDK gives the key to the focused field's
 * own binding first, and to the default button only if that binding is disabled
 * (SD_enter_claims); the commit-by-edit rule is the surrogate's, and is tested there.
 */
class DefaultButtonEnterTest extends AbstractKaribuTest {

    private JFrame frame;
    private final AtomicInteger okClicks = new AtomicInteger();

    @BeforeEach
    void showFrameWithDefaultButton() {
        frame = new JFrame();
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> okClicks.incrementAndGet());
        frame.add(ok, vaadinx.awt.BorderLayout.SOUTH);
        frame.getRootPane().setDefaultButton(ok);
        frame.setVisible(true);
    }

    private int enterIn(vaadinx.awt.Component focused) {
        frame.add(focused, vaadinx.awt.BorderLayout.CENTER);
        FocusTracker.setFocusOwner(focused.getPeer());
        okClicks.set(0);
        ShortcutsKt.fireShortcut(Key.ENTER);
        return okClicks.get();
    }

    @Test
    @DisplayName("a plain JTextField lets Enter through to the default button")
    void plainTextFieldLetsEnterThrough() {
        assertEquals(1, enterIn(new JTextField()));
    }

    @Test
    @DisplayName("a JTextField with an ActionListener takes Enter for itself — and still fires it")
    void textFieldWithActionListenerKeepsEnter() {
        JTextField field = new JTextField();
        field.addActionListener(e -> { });
        assertEquals(0, enterIn(field));
    }

    @Test
    @DisplayName("an Action counts as an ActionListener, as JTextField.setAction installs one")
    void textFieldWithActionKeepsEnter() {
        JTextField field = new JTextField();
        field.setAction(new javax.swing.AbstractAction("go") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
            }
        });
        assertEquals(0, enterIn(field));
    }

    @Test
    @DisplayName("a JPasswordField follows the JTextField rule")
    void passwordFieldFollowsTheTextFieldRule() {
        JPasswordField field = new JPasswordField();
        field.addActionListener(e -> { });
        assertEquals(0, enterIn(field));
    }

    @Test
    @DisplayName("a JFormattedTextField's ActionListener does not take Enter — only an edit does")
    void formattedFieldIgnoresItsActionListeners() {
        JFormattedTextField field = new JFormattedTextField();
        field.addActionListener(e -> { });
        assertEquals(1, enterIn(field));
    }

    @Test
    @DisplayName("an editable JTextArea keeps Enter for its newline; a read-only one lets it through")
    void textAreaKeepsEnterWhileEditable() {
        JTextArea area = new JTextArea(4, 24);
        assertEquals(0, enterIn(area));
        area.setEditable(false);
        assertEquals(1, enterIn(area));
    }
}
