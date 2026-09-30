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

import com.vaadin.flow.component.BlurNotifier;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.FocusNotifier;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.FocusTracker;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;
import vaadinx.awt.event.FocusEvent;
import vaadinx.awt.event.FocusListener;
import vaadinx.swing.FocusManager;
import vaadinx.swing.JButton;
import vaadinx.swing.JDialog;
import vaadinx.swing.JFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;
import vaadinx.swing.text.JTextComponent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tiers 1 and 2 of the focus manager (D_focus_managers): who has focus, and moving it on.
 *
 * <p>The browser's own {@code focusin} is not reachable from Karibu, so every "focus
 * moved" here arrives the way it does server-side in production — through
 * {@code requestFocus}'s optimistic pointer update. That is the path the auto-advance
 * idiom actually takes: a Document filter calls {@code transferFocus} and reads the
 * owner back in the same round trip.
 */
class FocusManagerTest extends AbstractKaribuTest {

    private final List<String> warns = new ArrayList<>();

    private void captureWarns() {
        EHelper.warnHook = warns::add;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> {
        };
    }

    /** A traversal policy that answers nothing — the value under test is its identity. */
    private java.awt.FocusTraversalPolicy noOpPolicy() {
        return new java.awt.FocusTraversalPolicy() {
            @Override
            public java.awt.Component getComponentAfter(java.awt.Container c, java.awt.Component a) {
                return null;
            }

            @Override
            public java.awt.Component getComponentBefore(java.awt.Container c, java.awt.Component a) {
                return null;
            }

            @Override
            public java.awt.Component getFirstComponent(java.awt.Container c) {
                return null;
            }

            @Override
            public java.awt.Component getLastComponent(java.awt.Container c) {
                return null;
            }

            @Override
            public java.awt.Component getDefaultComponent(java.awt.Container c) {
                return null;
            }
        };
    }

    // --- The managers themselves -------------------------------------

    @Test
    @DisplayName("both statics hand out a stateless facade")
    void bothStaticsHandOutAStatelessFacade() {
        assertSame(KeyboardFocusManager.getCurrentKeyboardFocusManager(),
                KeyboardFocusManager.getCurrentKeyboardFocusManager());
        assertSame(FocusManager.getCurrentManager(), FocusManager.getCurrentManager());
        // The Swing class is the AWT class's subclass, so migrated code reaching
        // for either static gets the same surface.
        assertInstanceOf(KeyboardFocusManager.class, FocusManager.getCurrentManager());
    }

    @Test
    @DisplayName("a cached facade still answers per UI")
    void aCachedFacadeStillAnswersPerUi() {
        // The hazard the stateless-facade decision exists for: migrated code
        // stashing the manager in a static field. The instance holds no UI, so
        // the answer follows UI.getCurrent().
        KeyboardFocusManager cached = FocusManager.getCurrentManager();
        JTextField field = new JTextField();
        new JFrame().add(field);
        field.requestFocus();
        assertSame(field, cached.getFocusOwner());

        UI firstUi = UI.getCurrent();
        UI secondUi = new UI();
        secondUi.getInternals().setSession(firstUi.getSession());
        UI.setCurrent(secondUi);
        try {
            assertNull(cached.getFocusOwner());
        } finally {
            UI.setCurrent(firstUi);
        }
    }

    // --- Tier 1: who has focus ---------------------------------------

    @Test
    @DisplayName("nothing focused by default")
    void nothingFocusedByDefault() {
        assertNull(FocusManager.getCurrentManager().getFocusOwner());
        assertNull(FocusManager.getCurrentManager().getPermanentFocusOwner());
        assertNull(FocusManager.getCurrentManager().getFocusedWindow());
        assertNull(FocusManager.getCurrentManager().getActiveWindow());
    }

    @Test
    @DisplayName("requestFocus moves the owner")
    void requestFocusMovesTheOwner() {
        captureWarns();
        JFrame frame = new JFrame();
        JTextField first = new JTextField();
        JTextField second = new JTextField();
        frame.add(first);
        frame.add(second);

        first.requestFocus();
        assertSame(first, FocusManager.getCurrentManager().getFocusOwner());
        assertTrue(first.isFocusOwner());
        assertTrue(first.hasFocus());
        assertFalse(second.isFocusOwner());

        second.requestFocus();
        assertSame(second, FocusManager.getCurrentManager().getFocusOwner());
        assertFalse(first.isFocusOwner());
        assertNoWarns(warns);
    }

    @Test
    @DisplayName("focus on an inner peer resolves to the owning emulator")
    void focusOnAnInnerPeerResolvesToTheOwningEmulator() {
        // What the focusin bridge delivers for a composite peer: the browser
        // focuses an inner Vaadin field, and the answer must be the emulator
        // that owns the composite.
        JTextField field = new JTextField();
        new JFrame().add(field);
        TextField inner = new TextField();
        field.getPeer().getElement().appendChild(inner.getElement());
        FocusTracker.setFocusOwner(inner);
        assertSame(field, FocusManager.getCurrentManager().getFocusOwner());
    }

    @Test
    @DisplayName("focus outside vaadinx answers null rather than failing")
    void focusOutsideVaadinxAnswersNullRatherThanFailing() {
        FocusTracker.setFocusOwner(new Div());
        assertNull(FocusManager.getCurrentManager().getFocusOwner());
    }

    @Test
    @DisplayName("focusedWindow walks up to the nearest Window")
    void focusedWindowWalksUpToTheNearestWindow() {
        JFrame frame = new JFrame();
        JTextField inFrame = new JTextField();
        JPanel panel = new JPanel();
        panel.add(inFrame);
        frame.add(panel);

        JDialog dialog = new JDialog(frame);
        JTextField inDialog = new JTextField();
        dialog.add(inDialog);

        inFrame.requestFocus();
        assertSame(frame, FocusManager.getCurrentManager().getFocusedWindow());
        assertSame(frame, FocusManager.getCurrentManager().getActiveWindow());

        // A dialog wins over the frame behind it — the walk stops at the first
        // Window it meets, which is what a Swing app asking "which window is
        // focused" means by the question.
        inDialog.requestFocus();
        assertSame(dialog, FocusManager.getCurrentManager().getFocusedWindow());
    }

    @Test
    @DisplayName("focusedWindow is null for an unparented focus owner")
    void focusedWindowIsNullForAnUnparentedFocusOwner() {
        JTextField orphan = new JTextField();
        orphan.requestFocus();
        assertSame(orphan, FocusManager.getCurrentManager().getFocusOwner());
        assertNull(FocusManager.getCurrentManager().getFocusedWindow());
    }

    @Test
    @DisplayName("clearFocusOwner drops the pointer")
    void clearFocusOwnerDropsThePointer() {
        JTextField field = new JTextField();
        new JFrame().add(field);
        field.requestFocus();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
        assertNull(FocusManager.getCurrentManager().getFocusOwner());
        assertFalse(field.isFocusOwner());
    }

    // --- Tier 2: traversal -------------------------------------------

    @Test
    @DisplayName("transferFocus advances to the next focusable component")
    void transferFocusAdvancesToTheNextFocusableComponent() {
        captureWarns();
        JFrame frame = new JFrame();
        JTextField first = new JTextField();
        JTextField second = new JTextField();
        JButton third = new JButton("go");
        frame.add(first);
        frame.add(second);
        frame.add(third);

        first.requestFocus();
        first.transferFocus();
        assertSame(second, FocusManager.getCurrentManager().getFocusOwner());
        second.transferFocus();
        assertSame(third, FocusManager.getCurrentManager().getFocusOwner());
        assertNoWarns(warns);
    }

    @Test
    @DisplayName("traversal crosses container boundaries depth-first")
    void traversalCrossesContainerBoundariesDepthFirst() {
        JFrame frame = new JFrame();
        JTextField outer = new JTextField();
        JTextField nested = new JTextField();
        JTextField after = new JTextField();
        JPanel panel = new JPanel();
        panel.add(nested);
        frame.add(outer);
        frame.add(panel);
        frame.add(after);

        outer.transferFocus();
        assertSame(nested, FocusManager.getCurrentManager().getFocusOwner());
        nested.transferFocus();
        assertSame(after, FocusManager.getCurrentManager().getFocusOwner());
    }

    @Test
    @DisplayName("traversal wraps around at the end")
    void traversalWrapsAroundAtTheEnd() {
        JFrame frame = new JFrame();
        JTextField first = new JTextField();
        JTextField last = new JTextField();
        frame.add(first);
        frame.add(last);
        last.transferFocus();
        assertSame(first, FocusManager.getCurrentManager().getFocusOwner());
    }

    @Test
    @DisplayName("traversal skips disabled, invisible, non-focusable and non-focusable-peer components")
    void traversalSkipsDisabledInvisibleNonFocusableAndNonFocusablePeerComponents() {
        captureWarns();
        JFrame frame = new JFrame();
        JTextField start = new JTextField();
        JTextField disabled = new JTextField();
        disabled.setEnabled(false);
        JTextField invisible = new JTextField();
        invisible.setVisible(false);
        JTextField optedOut = new JTextField();
        optedOut.setFocusable(false);
        JLabel label = new JLabel("not focusable in the browser either");
        JTextField target = new JTextField();
        for (Component c : List.of(start, disabled, invisible, optedOut, label, target)) {
            frame.add(c);
        }

        start.transferFocus();
        assertSame(target, FocusManager.getCurrentManager().getFocusOwner());
        assertNoWarns(warns);
    }

    @Test
    @DisplayName("an invisible container hides its whole subtree from traversal")
    void anInvisibleContainerHidesItsWholeSubtreeFromTraversal() {
        JFrame frame = new JFrame();
        JTextField start = new JTextField();
        JPanel hidden = new JPanel();
        hidden.setVisible(false);
        JTextField buried = new JTextField();
        hidden.add(buried);
        JTextField target = new JTextField();
        frame.add(start);
        frame.add(hidden);
        frame.add(target);

        start.transferFocus();
        assertSame(target, FocusManager.getCurrentManager().getFocusOwner());
    }

    @Test
    @DisplayName("transferFocusBackward and nextFocus")
    @SuppressWarnings("deprecation") // nextFocus is the AWT 1.0 alias under test
    void transferFocusBackwardAndNextFocus() {
        JFrame frame = new JFrame();
        JTextField first = new JTextField();
        JTextField second = new JTextField();
        frame.add(first);
        frame.add(second);

        second.transferFocusBackward();
        assertSame(first, FocusManager.getCurrentManager().getFocusOwner());
        first.nextFocus();
        assertSame(second, FocusManager.getCurrentManager().getFocusOwner());
    }

    @Test
    @DisplayName("the manager's no-arg traversal operates on the current owner")
    void theManagersNoArgTraversalOperatesOnTheCurrentOwner() {
        JFrame frame = new JFrame();
        JTextField first = new JTextField();
        JTextField second = new JTextField();
        frame.add(first);
        frame.add(second);
        first.requestFocus();

        FocusManager.getCurrentManager().focusNextComponent();
        assertSame(second, FocusManager.getCurrentManager().getFocusOwner());
        FocusManager.getCurrentManager().focusPreviousComponent();
        assertSame(first, FocusManager.getCurrentManager().getFocusOwner());
    }

    @Test
    @DisplayName("a lone focusable component stays put")
    void aLoneFocusableComponentStaysPut() {
        // Swing does nothing here too — no WARN, because nothing is missing.
        captureWarns();
        JFrame frame = new JFrame();
        JTextField only = new JTextField();
        frame.add(only);
        only.requestFocus();
        only.transferFocus();
        assertSame(only, FocusManager.getCurrentManager().getFocusOwner());
        assertNoWarns(warns);
    }

    @Test
    @DisplayName("setFocusable fires the AWT property change")
    void setFocusableFiresTheAwtPropertyChange() {
        JTextField field = new JTextField();
        List<String> seen = new ArrayList<>();
        field.addPropertyChangeListener(
                it -> seen.add(it.getPropertyName() + ":" + it.getOldValue() + "->" + it.getNewValue()));
        field.setFocusable(false);
        assertEquals(List.of("focusable:true->false"), seen);
        assertFalse(field.isFocusable());
    }

    // --- The motivating call site -----------------------------------

    @Test
    @DisplayName("the auto-advance idiom works end to end")
    void theAutoAdvanceIdiomWorksEndToEnd() {
        // NumberFormatDocument's shape, verbatim: from inside a Document, ask
        // who has focus, check it is the component this Document belongs to,
        // and move on. Every one of the three pieces was a stub before D_focus_managers.
        captureWarns();
        JFrame frame = new JFrame();
        JTextField areaCode = new JTextField();
        JTextField number = new JTextField();
        frame.add(areaCode);
        frame.add(number);
        areaCode.requestFocus();

        KeyboardFocusManager fm = FocusManager.getCurrentManager();
        JTextComponent owner = assertInstanceOf(JTextComponent.class, fm.getFocusOwner());
        assertSame(areaCode.getDocument(), owner.getDocument());
        owner.transferFocus();

        assertSame(number, fm.getFocusOwner());
        assertNoWarns(warns);
    }

    // --- The AWT FocusEvent bridge ----------------------------------
    //
    // Separate mechanism from the pointer above: this one delivers
    // FOCUS_GAINED / FOCUS_LOST to a component's own listeners, off the peer's
    // Vaadin focus/blur events.

    @Test
    @DisplayName("addFocusListener receives the peer's focus and blur")
    void addFocusListenerReceivesThePeersFocusAndBlur() {
        captureWarns();
        JTextField field = new JTextField();
        new JFrame().add(field);
        List<String> seen = new ArrayList<>();
        field.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                seen.add("gained:" + (e.getSource() == field));
            }

            @Override
            public void focusLost(FocusEvent e) {
                seen.add("lost:" + (e.getSource() == field));
            }
        });

        ComponentUtil.fireEvent(field.getPeer(), new FocusNotifier.FocusEvent<>(field.getPeer(), false));
        ComponentUtil.fireEvent(field.getPeer(), new BlurNotifier.BlurEvent<>(field.getPeer(), false));

        assertEquals(List.of("gained:true", "lost:true"), seen);
        assertNoWarns(warns);
    }

    @Test
    @DisplayName("one peer subscription drives every registered listener")
    void onePeerSubscriptionDrivesEveryRegisteredListener() {
        JTextField field = new JTextField();
        new JFrame().add(field);
        Counter count = new Counter();
        for (int i = 0; i < 3; i++) {
            field.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    count.inc();
                }

                @Override
                public void focusLost(FocusEvent e) {
                }
            });
        }
        ComponentUtil.fireEvent(field.getPeer(), new FocusNotifier.FocusEvent<>(field.getPeer(), false));
        // Three listeners, one event each — not nine, which is what a
        // per-listener subscription would produce.
        count.assertEquals(3);
    }

    @Test
    @DisplayName("a focus listener on a peer that cannot report focus says so")
    void aFocusListenerOnAPeerThatCannotReportFocusSaysSo() {
        captureWarns();
        new JPanel().addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
            }

            @Override
            public void focusLost(FocusEvent e) {
            }
        });
        assertEquals(1, warns.size(), "expected one non-focusable-peer WARN: " + warns);
    }

    // --- Tier 3 stays honest ----------------------------------------

    @Test
    @DisplayName("tier 3 WARNs rather than pretending")
    void tier3WarnsRatherThanPretending() {
        captureWarns();
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        fm.upFocusCycle(new JTextField());
        fm.downFocusCycle(new JPanel());
        fm.getCurrentFocusCycleRoot();
        fm.processKeyEvent(new JTextField(), null);
        fm.addVetoableChangeListener(evt -> {
        });
        KeyboardFocusManager.setCurrentKeyboardFocusManager(fm);
        FocusManager.setCurrentManager(FocusManager.getCurrentManager());
        assertEquals(7, warns.size(), "each tier-3 call should log exactly one WARN: " + warns);
    }

    @Test
    @DisplayName("the default traversal policy round-trips, and only its setter WARNs")
    void theDefaultTraversalPolicyRoundTripsAndOnlyItsSetterWarns() {
        // D_reverse_fanout_rows: the effect is declined (nothing dispatches on a policy), so the
        // setter WARNs — but the value is state, and a getter that answered null
        // after a set was the R_decline_effect_only violation. The getter is now silent.
        // The property event it fires is D_focus_property_registry's business.
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        java.awt.FocusTraversalPolicy policy = noOpPolicy();
        captureWarns();
        fm.setDefaultFocusTraversalPolicy(policy);
        assertEquals(1, warns.size(), "the setter declines the effect: " + warns);

        warns.clear();
        assertEquals(policy, fm.getDefaultFocusTraversalPolicy());
        assertEquals(0, warns.size(), "the getter answers from state, silently: " + warns);

        // Process-wide by design (it mirrors the JDK's per-AppContext default), so
        // leave a fresh instance behind rather than the one this test asserted on.
        // Not null: the JDK throws IllegalArgumentException on a null policy.
        fm.setDefaultFocusTraversalPolicy(noOpPolicy());
    }

    @Test
    @DisplayName("traversal keys report the honest empty answer without WARNing")
    void traversalKeysReportTheHonestEmptyAnswerWithoutWarning() {
        captureWarns();
        assertTrue(KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS).isEmpty());
        assertNoWarns(warns);
    }
}
