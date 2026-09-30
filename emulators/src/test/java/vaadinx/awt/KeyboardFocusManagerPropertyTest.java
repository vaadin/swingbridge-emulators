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

package vaadinx.awt;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.swing.FocusManager;
import vaadinx.swing.JDialog;
import vaadinx.swing.JFrame;
import vaadinx.swing.JTextField;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The focus manager's bound properties (D_focus_property_registry): the four the
 * pointer can back, the three that stay silent, and the per-UI scoping of the
 * registry that holds the listeners.
 *
 * <p>Every "focus moved" here arrives through {@code requestFocus}'s optimistic
 * pointer write, the same path {@code FocusManagerTest} uses — Karibu registers
 * the UI-wide {@code focusin} listener without ever dispatching it.
 */
class KeyboardFocusManagerPropertyTest extends AbstractKaribuTest {

    private final List<PropertyChangeEvent> fired = new ArrayList<>();
    private final List<String> warns = new ArrayList<>();

    private KeyboardFocusManager listening() {
        KeyboardFocusManager fm = FocusManager.getCurrentManager();
        fm.addPropertyChangeListener(fired::add);
        return fm;
    }

    /** The property names fired so far, in order. */
    private List<String> names() {
        return fired.stream().map(PropertyChangeEvent::getPropertyName).toList();
    }

    private void captureWarns() {
        EHelper.warnHook = warns::add;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = warn -> { };
        // Process-wide by design, so leave a fresh instance rather than whichever
        // one a test asserted on. Not null: the JDK rejects that.
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .setDefaultFocusTraversalPolicy(newPolicy());
    }

    private static java.awt.FocusTraversalPolicy newPolicy() {
        return new java.awt.FocusTraversalPolicy() {
            @Override public java.awt.Component getComponentAfter(java.awt.Container c, java.awt.Component a) { return null; }
            @Override public java.awt.Component getComponentBefore(java.awt.Container c, java.awt.Component a) { return null; }
            @Override public java.awt.Component getFirstComponent(java.awt.Container c) { return null; }
            @Override public java.awt.Component getLastComponent(java.awt.Container c) { return null; }
            @Override public java.awt.Component getDefaultComponent(java.awt.Container c) { return null; }
        };
    }

    // --- The four properties the pointer backs -----------------------

    @Test
    void gainingFocusFiresTheOwnerPairAndBothWindowProperties() {
        KeyboardFocusManager fm = listening();
        JFrame frame = new JFrame();
        JTextField field = new JTextField();
        frame.add(field);

        field.requestFocus();

        // Window activation precedes the owner pair, as DefaultKeyboardFocusManager
        // dispatches WINDOW_ACTIVATED / WINDOW_GAINED_FOCUS before FOCUS_GAINED.
        assertEquals(List.of("activeWindow", "focusedWindow", "focusOwner", "permanentFocusOwner"),
                names());
        assertNull(fired.get(2).getOldValue());
        assertSame(field, fired.get(2).getNewValue());
        assertSame(frame, fired.get(0).getNewValue());
        assertSame(fm, fired.get(0).getSource());
    }

    @Test
    void movingWithinOneWindowFiresOnlyTheOwnerPair() {
        listening();
        JFrame frame = new JFrame();
        JTextField first = new JTextField();
        JTextField second = new JTextField();
        frame.add(first);
        frame.add(second);
        first.requestFocus();
        fired.clear();

        second.requestFocus();

        // focusedWindow / activeWindow are unchanged, and firePropertyChange's
        // identity guard drops them — the desktop is silent here too.
        assertEquals(List.of("focusOwner", "permanentFocusOwner"),
                names());
        assertSame(first, fired.get(0).getOldValue());
        assertSame(second, fired.get(0).getNewValue());
    }

    @Test
    void crossingIntoADialogFiresTheWindowPairToo() {
        listening();
        JFrame frame = new JFrame();
        JTextField inFrame = new JTextField();
        frame.add(inFrame);
        JDialog dialog = new JDialog(frame);
        JTextField inDialog = new JTextField();
        dialog.add(inDialog);
        inFrame.requestFocus();
        fired.clear();

        inDialog.requestFocus();

        assertEquals(List.of("activeWindow", "focusedWindow", "focusOwner", "permanentFocusOwner"),
                names());
        assertSame(frame, fired.get(0).getOldValue());
        assertSame(dialog, fired.get(0).getNewValue());
    }

    @Test
    void clearFocusOwnerFiresTheLoss() {
        listening();
        JFrame frame = new JFrame();
        JTextField field = new JTextField();
        frame.add(field);
        field.requestFocus();
        fired.clear();

        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();

        assertEquals(List.of("activeWindow", "focusedWindow", "focusOwner", "permanentFocusOwner"),
                names());
        assertNull(fired.get(2).getNewValue());
        assertSame(field, fired.get(2).getOldValue());
    }

    @Test
    void reFocusingTheSameComponentFiresNothing() {
        listening();
        JFrame frame = new JFrame();
        JTextField field = new JTextField();
        frame.add(field);
        field.requestFocus();
        fired.clear();

        field.requestFocus();

        assertEquals(List.of(), names());
    }

    @Test
    void theOwnerReadBackInsideTheCallbackIsTheOneTheEventAnnounces() {
        // The no-null-intermediate contract: the desktop's FOCUS_LOST half would
        // report (old, null) while getFocusOwner() already answers the new owner.
        // Firing that pair here would announce a transition the getter denies.
        JFrame frame = new JFrame();
        JTextField first = new JTextField();
        JTextField second = new JTextField();
        frame.add(first);
        frame.add(second);
        first.requestFocus();

        List<Object> readBack = new ArrayList<>();
        KeyboardFocusManager fm = FocusManager.getCurrentManager();
        fm.addPropertyChangeListener("focusOwner", e -> {
            fired.add(e);
            readBack.add(fm.getFocusOwner());
        });

        second.requestFocus();

        assertEquals(1, fired.size());
        assertSame(second, fired.get(0).getNewValue());
        assertEquals(List.of(second), readBack);
    }

    // --- The registry itself -----------------------------------------

    @Test
    void aNamedRegistrationHearsOnlyThatProperty() {
        KeyboardFocusManager fm = FocusManager.getCurrentManager();
        fm.addPropertyChangeListener("focusOwner", fired::add);
        JFrame frame = new JFrame();
        JTextField field = new JTextField();
        frame.add(field);

        field.requestFocus();

        assertEquals(List.of("focusOwner"),
                names());
    }

    @Test
    void listenersRoundTripAndUnregister() {
        KeyboardFocusManager fm = FocusManager.getCurrentManager();
        PropertyChangeListener listener = fired::add;
        fm.addPropertyChangeListener(listener);
        assertEquals(1, fm.getPropertyChangeListeners().length);

        fm.removePropertyChangeListener(listener);
        assertEquals(0, fm.getPropertyChangeListeners().length);

        JFrame frame = new JFrame();
        JTextField field = new JTextField();
        frame.add(field);
        field.requestFocus();
        assertEquals(List.of(), names());
    }

    @Test
    void bothFacadesShareTheOneRegistry() {
        // FocusManager extends KeyboardFocusManager and both hand out a singleton;
        // a migrator reaching for either static must not get a second registry.
        FocusManager.getCurrentManager().addPropertyChangeListener(fired::add);
        assertEquals(1, KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getPropertyChangeListeners().length);
    }

    @Test
    void theRegistryIsPerUiSoAnotherTabsFocusIsNotHeard() {
        listening();
        UI firstUi = UI.getCurrent();
        UI secondUi = new UI();
        secondUi.getInternals().setSession(firstUi.getSession());
        UI.setCurrent(secondUi);
        try {
            KeyboardFocusManager fm = FocusManager.getCurrentManager();
            assertEquals(0, fm.getPropertyChangeListeners().length,
                    "the other tab's listener must not be visible here");
            JFrame frame = new JFrame();
            JTextField field = new JTextField();
            frame.add(field);
            field.requestFocus();
        } finally {
            UI.setCurrent(firstUi);
        }
        assertEquals(List.of(), names(), "the first tab heard the second tab's focus change");
    }

    @Test
    void registeringWithNoCurrentUiWarnsRatherThanPretending() {
        captureWarns();
        UI firstUi = UI.getCurrent();
        UI.setCurrent(null);
        try {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(fired::add);
        } finally {
            UI.setCurrent(firstUi);
        }
        assertEquals(1, warns.size(), () -> "expected one no-current-UI WARN: " + warns);
        assertTrue(warns.get(0).contains("no-current-UI"), warns.get(0));
    }

    // --- The three that stay silent ----------------------------------

    @Test
    void theDefaultTraversalPolicyFiresAndRejectsNullAsTheJdkDoes() {
        listening();
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        java.awt.FocusTraversalPolicy old = fm.getDefaultFocusTraversalPolicy();
        java.awt.FocusTraversalPolicy policy = newPolicy();

        fm.setDefaultFocusTraversalPolicy(policy);

        assertEquals(List.of("defaultFocusTraversalPolicy"), names());
        assertSame(old, fired.get(0).getOldValue());
        assertSame(policy, fired.get(0).getNewValue());

        // Re-setting the same instance is silent: firePropertyChange's identity
        // guard is the JDK's own.
        fired.clear();
        fm.setDefaultFocusTraversalPolicy(policy);
        assertEquals(List.of(), names());

        assertThrows(IllegalArgumentException.class, () -> fm.setDefaultFocusTraversalPolicy(null));
    }

    @Test
    void managingFocusAndTheFocusCycleRootStaySilent() {
        listening();
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        // Neither getter would agree with the event: the manager is not swapped,
        // and there is no cycle-root state to report.
        KeyboardFocusManager.setCurrentKeyboardFocusManager(fm);
        fm.setGlobalCurrentFocusCycleRoot(new JFrame());
        fm.setDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, java.util.Set.of());

        assertEquals(List.of(), names());
    }

    @Test
    void vetoableListenersAreStillDeclined() {
        captureWarns();
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        fm.addVetoableChangeListener(evt -> { });
        assertEquals(0, fm.getVetoableChangeListeners().length);
        assertEquals(1, warns.size(), () -> "the vetoable registry declines out loud: " + warns);
    }
}
