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
import com.vaadin.flow.component.shared.HasTooltip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Action;
import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * AbstractButton-level tests. The abstract class itself is exercised
 * via JButton since we can't instantiate AbstractButton directly —
 * every test here would equally apply to JToggleButton / JCheckBox
 * because the Action wiring lives on the common base.
 */
class AbstractButtonTest extends AbstractKaribuTest {

    /** An AbstractAction whose actionPerformed runs {@code body} — the Kotlin lane's local factory. */
    private static Action action(String name, Consumer<ActionEvent> body) {
        return new javax.swing.AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.accept(e);
            }
        };
    }

    /** An AbstractAction that does nothing when performed. */
    private static Action action(String name) {
        return action(name, e -> {
        });
    }

    /** The Alt+key stroke {@code setMnemonic} installs. */
    private static KeyStroke altStroke(int keyCode) {
        return KeyStroke.getKeyStroke(keyCode, InputEvent.ALT_DOWN_MASK);
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new AbstractButton() {
        };
    }

    // --- setAction — full-fat property grab + PCE sync + listener routing

    @Test
    @DisplayName("setAction propagates NAME to the button text")
    void setActionPropagatesName() {
        Action a = action("Apply");
        JButton jb = new JButton();
        jb.setAction(a);
        assertEquals("Apply", jb.getText());
    }

    @Test
    @DisplayName("setAction propagates ACTION_COMMAND_KEY")
    void setActionPropagatesActionCommandKey() {
        Action a = action("Save");
        a.putValue(Action.ACTION_COMMAND_KEY, "save-file");
        JButton jb = new JButton();
        jb.setAction(a);
        assertEquals("save-file", jb.getActionCommand());
    }

    @Test
    @DisplayName("setAction propagates MNEMONIC_KEY")
    void setActionPropagatesMnemonicKey() {
        Action a = action("Save");
        a.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_S);
        JButton jb = new JButton();
        jb.setAction(a);
        assertEquals(KeyEvent.VK_S, jb.getMnemonic());
    }

    @Test
    @DisplayName("setAction propagates enabled false")
    void setActionPropagatesEnabledFalse() {
        Action a = action("Go");
        a.setEnabled(false);
        JButton jb = new JButton();
        jb.setAction(a);
        assertFalse(jb.isEnabled());
    }

    @Test
    @DisplayName("action setEnabled change propagates to button via PCE")
    void actionSetEnabledPropagatesViaPce() {
        // Regression for the PCE listener installed by setAction.
        // Mutating the Action after it's attached must reach the button.
        Action a = action("Go");
        JButton jb = new JButton();
        jb.setAction(a);
        assertTrue(jb.isEnabled());
        a.setEnabled(false);
        assertFalse(jb.isEnabled());
        a.setEnabled(true);
        assertTrue(jb.isEnabled());
    }

    @Test
    @DisplayName("action putValue NAME propagates to button text via PCE")
    void actionPutValueNamePropagatesViaPce() {
        Action a = action("before");
        JButton jb = new JButton();
        jb.setAction(a);
        assertEquals("before", jb.getText());
        a.putValue(Action.NAME, "after");
        assertEquals("after", jb.getText());
    }

    @Test
    @DisplayName("action putValue ACTION_COMMAND_KEY propagates via PCE")
    void actionPutValueActionCommandKeyPropagatesViaPce() {
        Action a = action("Save");
        a.putValue(Action.ACTION_COMMAND_KEY, "save-1");
        JButton jb = new JButton();
        jb.setAction(a);
        assertEquals("save-1", jb.getActionCommand());
        a.putValue(Action.ACTION_COMMAND_KEY, "save-2");
        assertEquals("save-2", jb.getActionCommand());
    }

    @Test
    @DisplayName("click dispatches to Action.actionPerformed")
    void clickDispatchesToActionPerformed() {
        // setAction wires the Action as an ActionListener — click routes
        // through fireActionPerformed which iterates the listenerList.
        List<String> hits = new ArrayList<>();
        Action a = action("Hit", e -> hits.add(e.getActionCommand()));
        JButton jb = new JButton();
        jb.setAction(a);

        jb.doClick();

        assertEquals(List.of("Hit"), hits);
    }

    @Test
    @DisplayName("user click on peer dispatches to Action.actionPerformed")
    void userClickOnPeerDispatchesToActionPerformed() {
        // End-to-end: browser click → peer ClickEvent → Swing
        // fireActionPerformed → Action.actionPerformed.
        List<String> hits = new ArrayList<>();
        Action a = action("Hello", e -> hits.add(e.getActionCommand()));
        JButton jb = new JButton();
        jb.setAction(a);
        JFrame frame = new JFrame();
        frame.add(jb);
        frame.setVisible(true);

        LocatorJ._click(LocatorJ._get(Button.class));

        assertEquals(List.of("Hello"), hits);
    }

    @Test
    @DisplayName("replacing an Action detaches the old PCE listener")
    void replacingAnActionDetachesTheOldPceListener() {
        // Mutations on the now-detached Action must not reach the button
        // anymore — otherwise a shared Action between N buttons can't be
        // cleanly swapped out.
        Action oldAction = action("Old");
        Action newAction = action("New");
        JButton jb = new JButton();
        jb.setAction(oldAction);
        jb.setAction(newAction);
        assertEquals("New", jb.getText());

        oldAction.putValue(Action.NAME, "Old!");
        assertEquals("New", jb.getText());  // stale update ignored
    }

    @Test
    @DisplayName("replacing an Action removes the old ActionListener")
    void replacingAnActionRemovesTheOldActionListener() {
        // The old Action must not still fire on clicks after replacement.
        vaadinx.Counter oldHits = new vaadinx.Counter();
        Action oldAction = action("Old", e -> oldHits.inc());
        Action newAction = action("New");
        JButton jb = new JButton();
        jb.setAction(oldAction);
        jb.setAction(newAction);

        jb.doClick();

        oldHits.assertEquals(0);
    }

    @Test
    @DisplayName("setAction null clears action-driven state")
    void setActionNullClearsActionDrivenState() {
        Action a = action("Go");
        a.setEnabled(false);
        JButton jb = new JButton();
        jb.setAction(a);
        assertFalse(jb.isEnabled());

        jb.setAction(null);
        assertNull(jb.getAction());
        // Null action ⇒ enabled goes back to the button's own default (true).
        assertTrue(jb.isEnabled());
    }

    @Test
    @DisplayName("setAction same action is a no-op")
    void setActionSameActionIsANoOp() {
        // Guard against double-adding the ActionListener on a repeat call.
        Action a = action("Go");
        JButton jb = new JButton();
        jb.setAction(a);
        jb.setAction(a);
        assertEquals(1, jb.getActionListeners().length);
    }

    @Test
    @DisplayName("setAction fires an action PropertyChangeEvent")
    void setActionFiresAnActionPropertyChangeEvent() {
        Action a = action("Go");
        List<PropertyChangeEvent> events = new ArrayList<>();
        JButton jb = new JButton();
        jb.addPropertyChangeListener("action", events::add);

        jb.setAction(a);

        PropertyChangeEvent e = assertSingle(events);
        assertNull(e.getOldValue());
        assertSame(a, e.getNewValue());
    }

    @Test
    @DisplayName("getAction returns the installed action")
    void getActionReturnsTheInstalledAction() {
        Action a = action("Go");
        JButton jb = new JButton();
        assertNull(jb.getAction());
        jb.setAction(a);
        assertSame(a, jb.getAction());
    }

    @Test
    @DisplayName("setAction does not double-register if user already added the Action")
    void setActionDoesNotDoubleRegister() {
        // User idiom: addActionListener(action) first, then setAction(action).
        // JDK's isListener guard prevents a second registration → only one
        // dispatch per click.
        vaadinx.Counter hits = new vaadinx.Counter();
        Action a = action("Go", e -> hits.inc());
        JButton jb = new JButton();
        jb.addActionListener(a);
        jb.setAction(a);

        jb.doClick();

        hits.assertEquals(1);
    }

    @Test
    @DisplayName("setAction propagates SHORT_DESCRIPTION to peer tooltip")
    void setActionPropagatesShortDescriptionToPeerTooltip() {
        // Action.SHORT_DESCRIPTION → JComponent.setToolTipText →
        // HasTooltip on the peer. End-to-end verification that migrated
        // code using the idiomatic "Action with tooltip" pattern sees
        // the hover text reach the browser.
        Action a = action("Go");
        a.putValue(Action.SHORT_DESCRIPTION, "Commit the form");

        JButton jb = new JButton(a);

        assertEquals("Commit the form", jb.getToolTipText());
        assertEquals("Commit the form", ((HasTooltip) jb.getPeer()).getTooltip().getText());
    }

    @Test
    @DisplayName("ctor JButton(Action) routes through setAction")
    void ctorJButtonActionRoutesThroughSetAction() {
        // JButton(Action) constructor chains to this() then setAction. The
        // full property grab must run from the ctor so a freshly-
        // constructed button already reflects its Action's state.
        Action a = action("From ctor");
        a.putValue(Action.ACTION_COMMAND_KEY, "cmd");
        a.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_F);

        JButton jb = new JButton(a);

        assertEquals("From ctor", jb.getText());
        assertEquals("cmd", jb.getActionCommand());
        assertEquals(KeyEvent.VK_F, jb.getMnemonic());
        assertSame(a, jb.getAction());
    }

    // --- mnemonic auto-installs Alt+X accelerator

    @Test
    @DisplayName("setMnemonic installs an Alt+X WHEN_IN_FOCUSED_WINDOW accelerator")
    void setMnemonicInstallsAnAcceleratorInTheFocusedWindowMap() {
        // Mnemonics aren't just decorative — in a real form, Alt+S is
        // expected to click the Submit button. Verify the keybinding
        // actually lands in the WHEN_IN_FOCUSED_WINDOW map.
        JButton jb = new JButton("Save");
        jb.setMnemonic(KeyEvent.VK_S);

        assertEquals(JComponent.WHEN_IN_FOCUSED_WINDOW,
                jb.getConditionForKeyStroke(altStroke(KeyEvent.VK_S)));
    }

    @Test
    @DisplayName("setMnemonic zero removes the accelerator")
    void setMnemonicZeroRemovesTheAccelerator() {
        JButton jb = new JButton("Save");
        jb.setMnemonic(KeyEvent.VK_S);
        jb.setMnemonic(0);

        assertEquals(JComponent.UNDEFINED_CONDITION,
                jb.getConditionForKeyStroke(altStroke(KeyEvent.VK_S)));
    }

    @Test
    @DisplayName("changing mnemonic swaps the accelerator")
    void changingMnemonicSwapsTheAccelerator() {
        JButton jb = new JButton("x");
        jb.setMnemonic(KeyEvent.VK_S);
        jb.setMnemonic(KeyEvent.VK_O);

        assertEquals(JComponent.UNDEFINED_CONDITION,
                jb.getConditionForKeyStroke(altStroke(KeyEvent.VK_S)));
        assertEquals(JComponent.WHEN_IN_FOCUSED_WINDOW,
                jb.getConditionForKeyStroke(altStroke(KeyEvent.VK_O)));
    }

    @Test
    @DisplayName("mnemonic accelerator fires doClick (dispatches ActionEvent)")
    void mnemonicAcceleratorFiresDoClick() {
        // End-to-end: the accelerator's installed ActionListener calls
        // doClick, which fires ActionEvent on registered listeners. We
        // can't cleanly trigger a browser key event from here, but the
        // ActionMap's resolved Action IS that listener — invoking it
        // directly proves the wire.
        List<ActionEvent> hits = new ArrayList<>();
        JButton jb = new JButton("Go");
        jb.addActionListener(hits::add);
        jb.setMnemonic(KeyEvent.VK_G);

        java.awt.event.ActionListener accel = jb.getActionForKeyStroke(altStroke(KeyEvent.VK_G));
        assertNotNull(accel);
        accel.actionPerformed(new ActionEvent(jb, ActionEvent.ACTION_PERFORMED, "accel"));

        assertEquals(1, hits.size());
    }

    @Test
    @DisplayName("Action.MNEMONIC_KEY propagates through setAction to install accelerator")
    void actionMnemonicKeyPropagatesThroughSetAction() {
        // Full stack: Action.MNEMONIC_KEY → configurePropertiesFromAction
        // → setMnemonic → Vaadin shortcut. If this breaks, migrated
        // code that uses Action.MNEMONIC_KEY loses its accelerator.
        Action a = action("Submit");
        a.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_S);

        JButton jb = new JButton(a);

        assertEquals(JComponent.WHEN_IN_FOCUSED_WINDOW,
                jb.getConditionForKeyStroke(altStroke(KeyEvent.VK_S)));
    }
}
