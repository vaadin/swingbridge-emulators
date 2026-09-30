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

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.event.ComponentAdapter;
import vaadinx.awt.event.ComponentEvent;
import vaadinx.awt.event.WindowAdapter;
import vaadinx.awt.event.WindowEvent;
import vaadinx.awt.event.WindowFocusListener;
import vaadinx.awt.event.WindowListener;

import java.awt.Color;
import java.awt.Cursor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
// Single-type-import: shadows the vaadinx.awt.List emulator in this package.
import java.util.List;
import java.util.Locale;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static vaadinx.TestAssertions.assertSingle;

class WindowTest extends AbstractKaribuTest {

    /** A traversal policy that answers nothing — the value under test is its identity. */
    private static java.awt.FocusTraversalPolicy noOpPolicy() {
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

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new Window(new Frame());
    }

    @Test
    @DisplayName("boolean state defaults are silent false")
    void booleanStateDefaultsAreSilentFalse() {
        // Regression: these used to call onUnimplemented and log WARN on
        // every query. We don't model window activation / always-on-top /
        // platform-positioning (R_infra_not_surface, D_single_ui_per_session), so false is the
        // honest post-construction answer — not a gap. Setters remain stubs.
        Frame w = new Frame();
        assertFalse(w.isActive());
        assertFalse(w.isFocused());
        assertFalse(w.isAlwaysOnTop());
        assertFalse(w.isAlwaysOnTopSupported());
        assertFalse(w.isLocationByPlatform());
    }

    @Test
    @DisplayName("isFocusCycleRoot is AWT's unconditional true, not a silent false")
    void isFocusCycleRootIsAwtsUnconditionalTrueNotASilentFalse() {
        // The one boolean above that is NOT a "we don't model it, so false"
        // case: AWT's Window returns true unconditionally, because every
        // Window is a focus-cycle root, and its setter is an empty no-op.
        // Answering false was a judgement about which default surprises less
        // — exactly what R_decline_effect_only rules out when the JDK's body is unambiguous
        // (D_reverse_fanout_rows). It is also load-bearing now: Container.getFocusTraversalPolicy
        // gates on it, so a policy installed on a window reads back.
        Frame w = new Frame();
        assertTrue(w.isFocusCycleRoot());
        w.setFocusCycleRoot(false); // AWT ignores it; no WARN, nothing stored
        assertTrue(w.isFocusCycleRoot());

        java.awt.FocusTraversalPolicy policy = noOpPolicy();
        w.setFocusTraversalPolicy(policy);
        assertEquals(policy, w.getFocusTraversalPolicy());
        assertTrue(w.isFocusTraversalPolicySet());
    }

    @Test
    @DisplayName("a plain Container is no cycle root, so it reports no policy however one is installed")
    void aPlainContainerIsNoCycleRootSoItReportsNoPolicyHoweverOneIsInstalled() {
        // AWT's own gate, reproduced: the policy is stored and the bound
        // property fires, but the getter answers null until the container is
        // a cycle root or a policy provider.
        Container c = new Container();
        java.awt.FocusTraversalPolicy policy = noOpPolicy();
        List<String> seen = new ArrayList<>();
        c.addPropertyChangeListener(
                it -> seen.add(it.getPropertyName() + ":" + it.getOldValue() + "->" + it.getNewValue()));

        c.setFocusTraversalPolicy(policy);
        assertTrue(c.isFocusTraversalPolicySet(), "the field is set even though the getter gates on cycle-root");
        assertNull(c.getFocusTraversalPolicy(), "not a cycle root and not a provider — AWT answers null");

        c.setFocusCycleRoot(true);
        assertEquals(policy, c.getFocusTraversalPolicy(), "now a cycle root, so the installed policy reads back");

        c.setFocusTraversalPolicyProvider(true);
        assertTrue(c.isFocusTraversalPolicyProvider());

        assertEquals(
                List.of(
                        "focusTraversalPolicy:null->" + policy,
                        "focusCycleRoot:false->true",
                        "focusTraversalPolicyProvider:false->true"),
                seen);
    }

    @Test
    @DisplayName("null and empty getters are silent")
    void nullAndEmptyGettersAreSilent() {
        // Pairs with the booleans above: when the corresponding setter
        // hasn't run (and never will, in the current slice), "nothing here"
        // is the honest return — null for single values, empty for
        // collections. Never-null collection contracts preserved per AWT.
        Frame w = new Frame();
        assertNull(w.getOwner());
        assertNull(w.getShape());
        assertNull(w.getWarningString());
        assertNull(w.getFocusOwner());
        assertNull(w.getMostRecentFocusOwner());
        assertNull(w.getFocusCycleRootAncestor());
        assertTrue(w.getIconImages().isEmpty());
        assertEquals(0, w.getOwnedWindows().length);
        // `w` itself is listed — the registry is creation-based, so a Window is
        // enumerable from construction. Its own owner is null, so it shows up
        // in both. (This asserted 0/0 while the registry walked the UI graph.)
        assertSame(w, assertSingle(Window.getWindows()));
        assertSame(w, assertSingle(Window.getOwnerlessWindows()));
    }

    @Test
    @DisplayName("honest-true defaults for opaque and focus-capability getters")
    void honestTrueDefaultsForOpaqueAndFocusCapabilityGetters() {
        // Regression: these used to onUnimplemented and return false, which
        // contradicted AWT contract and the paired setters' behavior. AWT's
        // true-by-default Window getters; we don't model the subsystems
        // behind them (R_infra_not_surface) but the defaults are observable through getter
        // calls in migrated code (e.g. "if (frame.isOpaque())...").
        Frame w = new Frame();
        assertTrue(w.isOpaque());
        assertTrue(w.isFocusableWindow());
        assertTrue(w.getFocusableWindowState());
        assertTrue(w.isAutoRequestFocus());
    }

    @Test
    @DisplayName("silent no-op setters for z-order and nontrivial focus state")
    void silentNoOpSettersForZOrderAndNontrivialFocusState() {
        // z-order (toFront/toBack) doesn't map to a Vaadin tab (R_layouts_close_enough/D_pixel_layout_not_planned); we
        // accept silently so apps that pair setVisible(true) + toFront()
        // don't generate WARN log churn. Focus-state setters warn only on
        // a *change* request (match setAlwaysOnTop precedent) — the default
        // direction is silent. This test asserts "no exception on call";
        // log-noise regressions would be caught by a log-capturing test
        // which we don't have yet (logs aren't part of the API).
        Frame w = new Frame();
        w.toFront();
        w.toBack();
        w.setFocusableWindowState(true);   // default — silent
        w.setAutoRequestFocus(true);       // default — silent
        w.setLocationByPlatform(false);    // default — silent
        w.setFocusCycleRoot(false);        // matches our getter — silent
    }

    @Test
    @DisplayName("opacity default is fully opaque")
    void opacityDefaultIsFullyOpaque() {
        // Regression: generator default was 0 (fully transparent) — any
        // caller branching on opacity would have seen the wrong sentinel.
        // AWT's real default is 1.0f.
        assertEquals(1.0f, new Frame().getOpacity());
    }

    @Test
    @DisplayName("super-delegated overrides restore Component behavior")
    void superDelegatedOverridesRestoreComponentBehavior() {
        // Regression: these used to onUnimplemented, masking real
        // Component/Container logic that does the right thing on our
        // peer (CSS cursor, CSS background, min-width/min-height, default
        // headless toolkit). Use Window directly — Frame re-stubs
        // setBackground/setCursor(int)/addNotify, which would mask the
        // super-delegates we're verifying here.
        Window w = new Window(new Frame());
        Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        w.setCursor(hand);
        assertSame(hand, w.getCursor());

        w.setBackground(Color.RED);
        assertEquals(Color.RED, w.getBackground());

        // D_toolkit_full_surface: the vaadinx full-surface emulator toolkit (global singleton),
        // same object Component.getToolkit() returns — not java.awt's headless one.
        assertSame(Toolkit.getDefaultToolkit(), w.getToolkit());
    }

    @Test
    @DisplayName("getLocale inherits browser locale via UI when orphan")
    void getLocaleInheritsBrowserLocaleViaUiWhenOrphan() {
        // Component.getLocale throws on an orphan; Window overrides to
        // fall back. AWT falls back to Locale.getDefault; we prefer the
        // Vaadin UI locale (browser Accept-Language) so the window sees
        // what the user asked for, not the server JVM's default.
        UI.getCurrent().setLocale(Locale.FRENCH);
        assertEquals(Locale.FRENCH, new Frame().getLocale());
    }

    @Test
    @DisplayName("getLocale honors explicit setLocale over UI locale")
    void getLocaleHonorsExplicitSetLocaleOverUiLocale() {
        // Component.setLocale writes the field; super.getLocale's field
        // check runs before the orphan fallback, so a user-set locale wins.
        UI.getCurrent().setLocale(Locale.FRENCH);
        Frame w = new Frame();
        w.setLocale(Locale.GERMAN);
        assertEquals(Locale.GERMAN, w.getLocale());
    }

    @Test
    @DisplayName("getOwner returns the Window passed to the owner constructor")
    void getOwnerReturnsTheWindowPassedToTheOwnerConstructor() {
        Frame owner = new Frame();
        Window child = new Window(owner);
        assertSame(owner, child.getOwner());
        // Frame() owner-less — owner field stays null, matching AWT.
        assertNull(owner.getOwner());
    }

    @Test
    @DisplayName("getOwner with Window(Window, GC) ignores the GC but keeps the owner")
    void getOwnerWithWindowWindowGcIgnoresTheGcButKeepsTheOwner() {
        // GraphicsConfiguration is accepted-and-ignored per R_layouts_close_enough (no pixel
        // geometry); the owner link must still be recorded.
        Frame owner = new Frame();
        Window child = new Window(owner, null);
        assertSame(owner, child.getOwner());
    }

    @Test
    @DisplayName("EHelper getDialogs finds opened Dialog peers attached to the UI")
    void eHelperGetDialogsFindsOpenedDialogPeersAttachedToTheUi() {
        // Unopened Dialogs aren't in the UI graph and must not be reported.
        Frame frame = new Frame();
        assertEquals(0, EHelper.getDialogs().size());

        frame.setVisible(true);
        assertSame(frame.getPeer(), assertSingle(EHelper.getDialogs()));
    }

    @Test
    @DisplayName("Window getWindows maps opened dialogs back to their emulator")
    void windowGetWindowsMapsOpenedDialogsBackToTheirEmulator() {
        Frame frame = new Frame();
        frame.setVisible(true);

        assertSame(frame, assertSingle(Window.getWindows()));
    }

    @Test
    @DisplayName("getOwnerlessWindows filters by null owner")
    void getOwnerlessWindowsFiltersByNullOwner() {
        Frame frame = new Frame();
        Window owned = new Window(frame);
        frame.setVisible(true);
        owned.setVisible(true);

        assertSame(frame, assertSingle(Window.getOwnerlessWindows()));
    }

    @Test
    @DisplayName("getOwnedWindows returns Windows constructed with this owner")
    void getOwnedWindowsReturnsWindowsConstructedWithThisOwner() {
        // Owner link is logical (set at construction time), not derived
        // from peer attachment state — so no setVisible(true) needed.
        // Pairs AWT's Window.ownedWindowList semantics: present from the
        // moment the owner-taking ctor runs.
        Frame frame = new Frame();
        Window a = new Window(frame);
        Window b = new Window(frame);
        Window c = new Window(new Frame()); // owned by a *different* Frame

        List<Window> owned = Arrays.asList(frame.getOwnedWindows());
        assertEquals(2, owned.size());
        assertTrue(owned.contains(a));
        assertTrue(owned.contains(b));
        assertFalse(owned.contains(c));
    }

    @Test
    @DisplayName("getWindows lists an owned Window that was never opened")
    void getWindowsListsAnOwnedWindowThatWasNeverOpened() {
        // Still true, but for a different reason than it used to be: the owned
        // Window registered itself at construction, so nothing has to descend
        // the owner tree to find it. That descent is gone — it was redundant
        // the moment the registry became creation-based.
        Frame outer = new Frame();
        Window inner = new Window(outer);
        outer.setVisible(true);

        List<Window> all = Arrays.asList(Window.getWindows());
        assertTrue(all.contains(outer));
        assertTrue(all.contains(inner));
    }

    @Test
    @DisplayName("getWindows is creation-based and construction-ordered")
    void getWindowsIsCreationBasedAndConstructionOrdered() {
        // AWT's registry is keyed on *construction*, not visibility: every
        // Window built and not yet collected is listed, shown or not, disposed
        // or not. Measured on JDK 25 (three frames built a/b/c enumerate
        // a/b/c). The old stance here — traverse the graph, retain nothing —
        // answered "no windows" for a frame the app was still holding.
        Frame never = new Frame();
        Frame shown = new Frame();
        Frame disposed = new Frame();
        shown.setVisible(true);
        disposed.pack();
        disposed.dispose();

        assertEquals(List.of(never, shown, disposed), Arrays.asList(Window.getWindows()));
    }

    @Test
    @DisplayName("a displayable Window is pinned strongly, an undisplayable one weakly")
    void aDisplayableWindowIsPinnedStronglyAnUndisplayableOneWeakly() {
        // The reference-strength half of the registry, which is what makes the
        // leak AWT's leak: displayable ⟹ the registry itself keeps the Window
        // alive (allWindows), undisplayable ⟹ only a WeakReference remains.
        // Absence-after-collection is deliberately NOT asserted — there is no
        // forced-GC seam here and a heap-pressure test is flaky by
        // construction, so the weak half rests on the structure.
        Frame f = new Frame();
        assertFalse(f.isDisplayable(), "a fresh Window is not displayable");

        f.setVisible(true);
        assertTrue(f.isDisplayable(), "shown ⟹ displayable ⟹ pinned");

        f.setVisible(false);
        assertTrue(f.isDisplayable(), "hiding does NOT undisplayable — still pinned");
        assertTrue(Arrays.asList(Window.getWindows()).contains(f),
                "a hidden-but-undisposed Window stays listed; AWT lists it too");

        f.dispose();
        assertFalse(f.isDisplayable(), "only dispose() undisplayables — back to weak");
        assertTrue(Arrays.asList(Window.getWindows()).contains(f),
                "and it stays listed while the test still references it");
    }

    @Test
    @DisplayName("the registry's own reference to an unpinned Window is weak, and resolves while the app holds it")
    void theRegistrysOwnReferenceToAnUnpinnedWindowIsWeakAndResolvesWhileTheAppHoldsIt() {
        // The weak half made observable without a forced-GC seam: hold the Frame
        // both strongly (the app's own reference) and weakly (this test's), and
        // check the three agree — the app's ref, ours, and getFrames(). A
        // never-shown Frame is unpinned, so the registry contributes no strength
        // of its own here and `f` is listed purely because the app can still name
        // it. Absence-after-collection stays unasserted: a heap-pressure test is
        // flaky by construction.
        Frame f = new Frame();
        WeakReference<Frame> weak = new WeakReference<>(f);

        assertFalse(f.isDisplayable(), "never shown ⟹ unpinned ⟹ weak-only in the registry");
        assertSame(f, weak.get(), "strongly held, so no reference of any strength may clear");
        assertTrue(Arrays.asList(Frame.getFrames()).contains(f),
                "and the registry lists it through its weak entry");

        f.pack();
        assertTrue(f.isDisplayable(), "pack() pins it — now the registry holds it too");
        assertSame(f, weak.get());
        assertTrue(Arrays.asList(Frame.getFrames()).contains(f));
    }

    @Test
    @DisplayName("a Window is enumerable from inside its own constructor")
    void aWindowIsEnumerableFromInsideItsOwnConstructor() {
        // The JDK registers in Window.init(), off the constructor, so this
        // horrible-but-legal idiom passes on the desktop (measured) — and it is
        // the shape a migrated app uses when it drops a `static AppFrame
        // _instance` in favour of the registry walk.
        boolean[] seenSelf = {false};
        boolean[] seenOnce = {false};
        new Frame() {
            {
                seenSelf[0] = Arrays.asList(Window.getWindows()).contains(this);
                seenOnce[0] = Arrays.stream(Window.getWindows()).filter(it -> it == this).count() == 1;
            }
        };
        assertTrue(seenSelf[0], "registration must precede the subclass initializer");
        assertTrue(seenOnce[0], "registered exactly once");
    }

    @Test
    @DisplayName("Frame getFrames filters getWindows by type")
    void frameGetFramesFiltersGetWindowsByType() {
        Frame f = new Frame();
        Window plainWindow = new Window(f);
        f.setVisible(true);
        plainWindow.setVisible(true);

        assertSame(f, assertSingle(Frame.getFrames()));
    }

    /**
     * Test-only subclass that exposes the protected fire* helpers so tests
     * can invoke them without a real peer-event path. Once setVisible /
     * peer listeners drive fire*, tests use those entry points instead.
     */
    private static final class TestWindow extends Window {
        TestWindow(Frame owner) {
            super(owner);
        }

        void exposedFireWindowEvent(int id) {
            fireWindowEvent(id);
        }

        void exposedFireWindowFocusEvent(int id, Window opposite) {
            fireWindowFocusEvent(id, opposite);
        }

        void exposedFireWindowStateEvent(int oldState, int newState) {
            fireWindowStateEvent(oldState, newState);
        }

        void exposedProcessEvent(java.awt.AWTEvent e) {
            processEvent(e);
        }
    }

    /** One recorded {@code WindowListener} callback: which one, and with what event. */
    private record Fired(String kind, WindowEvent event) {
    }

    private static final class RecordingWindowListener implements WindowListener {
        final List<Fired> events = new ArrayList<>();

        @Override
        public void windowOpened(WindowEvent e) {
            events.add(new Fired("opened", e));
        }

        @Override
        public void windowClosing(WindowEvent e) {
            events.add(new Fired("closing", e));
        }

        @Override
        public void windowClosed(WindowEvent e) {
            events.add(new Fired("closed", e));
        }

        @Override
        public void windowIconified(WindowEvent e) {
            events.add(new Fired("iconified", e));
        }

        @Override
        public void windowDeiconified(WindowEvent e) {
            events.add(new Fired("deiconified", e));
        }

        @Override
        public void windowActivated(WindowEvent e) {
            events.add(new Fired("activated", e));
        }

        @Override
        public void windowDeactivated(WindowEvent e) {
            events.add(new Fired("deactivated", e));
        }

        /** Just the callback names, in order — for the sequence-shaped assertions. */
        List<String> kinds() {
            return events.stream().map(Fired::kind).toList();
        }
    }

    @Test
    @DisplayName("addWindowListener stores listener and getWindowListeners returns it")
    void addWindowListenerStoresListenerAndGetWindowListenersReturnsIt() {
        Frame w = new Frame();
        assertEquals(0, w.getWindowListeners().length);

        RecordingWindowListener l = new RecordingWindowListener();
        w.addWindowListener(l);
        assertSame(l, assertSingle(w.getWindowListeners()));
    }

    @Test
    @DisplayName("removeWindowListener stops further events")
    void removeWindowListenerStopsFurtherEvents() {
        TestWindow w = new TestWindow(new Frame());
        RecordingWindowListener l = new RecordingWindowListener();
        w.addWindowListener(l);
        w.removeWindowListener(l);

        w.exposedFireWindowEvent(WindowEvent.WINDOW_OPENED);

        assertEquals(0, l.events.size());
    }

    @Test
    @DisplayName("null listener is silently ignored on add and remove")
    void nullListenerIsSilentlyIgnoredOnAddAndRemove() {
        // Matches AWT and Container's own null-tolerance.
        Frame w = new Frame();
        w.addWindowListener(null);
        w.addWindowFocusListener(null);
        w.addWindowStateListener(null);
        w.removeWindowListener(null);
        w.removeWindowFocusListener(null);
        w.removeWindowStateListener(null);
        assertEquals(0, w.getWindowListeners().length);
        assertEquals(0, w.getWindowFocusListeners().length);
        assertEquals(0, w.getWindowStateListeners().length);
    }

    @Test
    @DisplayName("fireWindowEvent routes each id to its WindowListener callback")
    void fireWindowEventRoutesEachIdToItsWindowListenerCallback() {
        TestWindow w = new TestWindow(new Frame());
        RecordingWindowListener l = new RecordingWindowListener();
        w.addWindowListener(l);

        w.exposedFireWindowEvent(WindowEvent.WINDOW_OPENED);
        w.exposedFireWindowEvent(WindowEvent.WINDOW_CLOSING);
        w.exposedFireWindowEvent(WindowEvent.WINDOW_CLOSED);
        w.exposedFireWindowEvent(WindowEvent.WINDOW_ICONIFIED);
        w.exposedFireWindowEvent(WindowEvent.WINDOW_DEICONIFIED);
        w.exposedFireWindowEvent(WindowEvent.WINDOW_ACTIVATED);
        w.exposedFireWindowEvent(WindowEvent.WINDOW_DEACTIVATED);

        assertEquals(
                List.of("opened", "closing", "closed", "iconified", "deiconified", "activated", "deactivated"),
                l.kinds());
        // Source is always the firing Window.
        assertTrue(l.events.stream().allMatch(it -> it.event().getWindow() == w));
    }

    @Test
    @DisplayName("fireWindowFocusEvent carries opposite window")
    void fireWindowFocusEventCarriesOppositeWindow() {
        TestWindow w = new TestWindow(new Frame());
        Frame other = new Frame();
        List<WindowEvent> gained = new ArrayList<>();
        List<WindowEvent> lost = new ArrayList<>();
        w.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                gained.add(e);
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                lost.add(e);
            }
        });

        w.exposedFireWindowFocusEvent(WindowEvent.WINDOW_GAINED_FOCUS, other);
        w.exposedFireWindowFocusEvent(WindowEvent.WINDOW_LOST_FOCUS, null);

        assertSame(other, assertSingle(gained).getOppositeWindow());
        assertNull(assertSingle(lost).getOppositeWindow());
    }

    @Test
    @DisplayName("fireWindowStateEvent carries old and new state")
    void fireWindowStateEventCarriesOldAndNewState() {
        TestWindow w = new TestWindow(new Frame());
        List<WindowEvent> events = new ArrayList<>();
        w.addWindowStateListener(events::add);

        w.exposedFireWindowStateEvent(0, 1);

        WindowEvent e = assertSingle(events);
        assertEquals(WindowEvent.WINDOW_STATE_CHANGED, e.getID());
        assertEquals(0, e.getOldState());
        assertEquals(1, e.getNewState());
    }

    @Test
    @DisplayName("WindowAdapter receives events across all three listener types")
    void windowAdapterReceivesEventsAcrossAllThreeListenerTypes() {
        // AWT's adapter implements WindowListener + WindowFocusListener +
        // WindowStateListener. Registering it to all three paths confirms
        // storage is per-interface (not per-instance) and the dispatch
        // routes each id family to the right interface method.
        List<String> hits = new ArrayList<>();
        WindowAdapter adapter = new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                hits.add("opened");
            }

            @Override
            public void windowGainedFocus(WindowEvent e) {
                hits.add("gainedFocus");
            }

            @Override
            public void windowStateChanged(WindowEvent e) {
                hits.add("stateChanged");
            }
        };
        TestWindow w = new TestWindow(new Frame());
        w.addWindowListener(adapter);
        w.addWindowFocusListener(adapter);
        w.addWindowStateListener(adapter);

        w.exposedFireWindowEvent(WindowEvent.WINDOW_OPENED);
        w.exposedFireWindowFocusEvent(WindowEvent.WINDOW_GAINED_FOCUS, null);
        w.exposedFireWindowStateEvent(0, 1);

        assertEquals(List.of("opened", "gainedFocus", "stateChanged"), hits);
    }

    @Test
    @DisplayName("multiple listeners all receive the event")
    void multipleListenersAllReceiveTheEvent() {
        TestWindow w = new TestWindow(new Frame());
        RecordingWindowListener a = new RecordingWindowListener();
        RecordingWindowListener b = new RecordingWindowListener();
        w.addWindowListener(a);
        w.addWindowListener(b);

        w.exposedFireWindowEvent(WindowEvent.WINDOW_OPENED);

        assertEquals(1, a.events.size());
        assertEquals(1, b.events.size());
    }

    @Test
    @DisplayName("listener that removes itself during dispatch does not CME")
    void listenerThatRemovesItselfDuringDispatchDoesNotCme() {
        // Same invariant ContainerTest exercises: the shared EventListenerList
        // must tolerate in-dispatch mutation.
        TestWindow w = new TestWindow(new Frame());
        WindowAdapter oneShot = new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                w.removeWindowListener(this);
            }
        };
        w.addWindowListener(oneShot);

        w.exposedFireWindowEvent(WindowEvent.WINDOW_OPENED);
        w.exposedFireWindowEvent(WindowEvent.WINDOW_OPENED); // oneShot already unregistered

        assertEquals(0, w.getWindowListeners().length);
    }

    @Test
    @DisplayName("fire short-circuits when nobody is listening")
    void fireShortCircuitsWhenNobodyIsListening() {
        // No WindowListener / WindowFocusListener / WindowStateListener
        // registered — fire* must not allocate an event. Hard to observe
        // directly; we settle for "no listener receives anything and no
        // exception is thrown" and trust the getListenerCount guard.
        TestWindow w = new TestWindow(new Frame());
        w.exposedFireWindowEvent(WindowEvent.WINDOW_OPENED);
        w.exposedFireWindowFocusEvent(WindowEvent.WINDOW_GAINED_FOCUS, null);
        w.exposedFireWindowStateEvent(0, 1);
    }

    @Test
    @DisplayName("processEvent routes WindowEvent ids to the right process method")
    void processEventRoutesWindowEventIdsToTheRightProcessMethod() {
        // Direct dispatchEvent-style invocation: user code (or a subclass
        // override) calls processEvent(AWTEvent) and expects AWT's id-based
        // routing. Verify each id family lands in the corresponding listener.
        TestWindow w = new TestWindow(new Frame());
        RecordingWindowListener wl = new RecordingWindowListener();
        List<WindowEvent> focus = new ArrayList<>();
        List<WindowEvent> state = new ArrayList<>();
        w.addWindowListener(wl);
        w.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                focus.add(e);
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                focus.add(e);
            }
        });
        w.addWindowStateListener(state::add);

        w.exposedProcessEvent(new WindowEvent(w, WindowEvent.WINDOW_OPENED));
        w.exposedProcessEvent(new WindowEvent(w, WindowEvent.WINDOW_GAINED_FOCUS, null));
        w.exposedProcessEvent(new WindowEvent(w, WindowEvent.WINDOW_STATE_CHANGED, 0, 1));

        assertEquals(List.of("opened"), wl.kinds());
        assertEquals(1, focus.size());
        assertEquals(1, state.size());
    }

    @Test
    @DisplayName("processEvent delegates non-WindowEvent to super")
    void processEventDelegatesNonWindowEventToSuper() {
        // A ComponentEvent passed to Window.processEvent must reach
        // Component's ComponentListener dispatch — verifies the super call
        // in the else branch of Window.processEvent.
        TestWindow w = new TestWindow(new Frame());
        List<ComponentEvent> shown = new ArrayList<>();
        w.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                shown.add(e);
            }
        });

        w.exposedProcessEvent(new ComponentEvent(w, ComponentEvent.COMPONENT_SHOWN));

        assertSame(w, assertSingle(shown).getSource());
    }

    @Test
    @DisplayName("getListeners returns listeners of the requested type")
    void getListenersReturnsListenersOfTheRequestedType() {
        // Regression: getListeners was previously a stub returning null.
        // Now it's inherited from Component which reads the same
        // listenerList our add*Listener methods write to.
        Frame w = new Frame();
        RecordingWindowListener wl = new RecordingWindowListener();
        w.addWindowListener(wl);

        assertSame(wl, assertSingle(w.getListeners(WindowListener.class)));

        // Different listener type — empty array, never null.
        assertEquals(0, w.getListeners(WindowFocusListener.class).length);
    }

    @Test
    @DisplayName("Window starts invisible regardless of Component default")
    void windowStartsInvisibleRegardlessOfComponentDefault() {
        // AWT's Window default is invisible; Component's default is visible.
        // Window's ctor flips the package-private field. Regression guard
        // so future Component refactors don't silently re-introduce the
        // true default for Windows.
        assertFalse(new Frame().isVisible());
    }

    @Test
    @DisplayName("setVisible true attaches peer and opens Dialog")
    void setVisibleTrueAttachesPeerAndOpensDialog() {
        Frame f = new Frame();
        Dialog dialog = (Dialog) f.getPeer();
        assertFalse(dialog.isOpened());
        assertFalse(dialog.getElement().getNode().isAttached());

        f.setVisible(true);

        assertTrue(f.isVisible());
        assertTrue(dialog.isOpened());
        assertTrue(dialog.getElement().getNode().isAttached());
        assertTrue(f.isShowing());  // Component's visible && isDisplayable
    }

    @Test
    @DisplayName("setVisible false closes Dialog but leaves it attached")
    void setVisibleFalseClosesDialogButLeavesItAttached() {
        // setOpened(false) hides the overlay without detaching — dispose()
        // is the one that fully tears down. isShowing flips to false via
        // the visible flag alone; isDisplayable stays true.
        Frame f = new Frame();
        f.setVisible(true);
        f.setVisible(false);

        assertFalse(f.isVisible());
        assertFalse(((Dialog) f.getPeer()).isOpened());
        assertTrue(f.isDisplayable());
        assertFalse(f.isShowing());
    }

    @Test
    @DisplayName("setVisible true with no current UI but a reachable session lands on the app's UI")
    void setVisibleTrueWithNoCurrentUiHopsToTheLiveUi() {
        // Since D_modal_from_background the peer write goes through EHelper.runInUIThread,
        // which treats "no current UI" as the background-thread case rather than a dead
        // end: it takes the session off the EmulatorContext and hops to the app's live UI.
        // That is R_tolerate_off_ui_thread limb 3 — make it work where it is cheap — and it
        // is what lets a SwingWorker show a window at all.
        UI.setCurrent(null);
        Frame f = new Frame();
        f.setVisible(true);
        assertTrue(f.isVisible(), "a reachable session is enough to show a window");
    }

    @Test
    @DisplayName("setVisible true with no session at all still throws IllegalStateException")
    void setVisibleTrueWithNoSessionThrowsIllegalStateException() {
        // The carve-out that survives: a thread carrying no EmulatorContext has no session
        // to reach a UI through, which is a migrator configuration error with a one-line
        // fix at the submit site (D_no_context_throws, R_match_swing_errors case (7)) — not
        // incomplete emulation, so not D_never_fail_on_gaps' WARN-and-continue.
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                new Frame().setVisible(true);
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "no-context-window-show");
        t.setDaemon(true);
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
        assertInstanceOf(IllegalStateException.class, failure.get(),
                "with no session there is no UI to reach and nothing to degrade toward");
    }

    @Test
    @DisplayName("setVisible fires WINDOW_OPENED only on the first show")
    void setVisibleFiresWindowOpenedOnlyOnTheFirstShow() {
        Frame f = new Frame();
        RecordingWindowListener l = new RecordingWindowListener();
        f.addWindowListener(l);

        f.setVisible(true);
        f.setVisible(false);
        f.setVisible(true);

        // Exactly one WINDOW_OPENED across the whole cycle. WINDOW_CLOSED
        // is dispose()'s job — setVisible(false) doesn't fire it.
        assertEquals(List.of("opened"), l.kinds());
    }

    @Test
    @DisplayName("setVisible fires COMPONENT_SHOWN and COMPONENT_HIDDEN")
    void setVisibleFiresComponentShownAndComponentHidden() {
        Frame f = new Frame();
        List<String> hits = new ArrayList<>();
        f.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                hits.add("shown");
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                hits.add("hidden");
            }
        });

        f.setVisible(true);
        f.setVisible(false);

        assertEquals(List.of("shown", "hidden"), hits);
    }

    @Test
    @DisplayName("setVisible is idempotent — no events when state unchanged")
    void setVisibleIsIdempotentNoEventsWhenStateUnchanged() {
        Frame f = new Frame();
        RecordingWindowListener wl = new RecordingWindowListener();
        List<String> comp = new ArrayList<>();
        f.addWindowListener(wl);
        f.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                comp.add("shown");
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                comp.add("hidden");
            }
        });

        f.setVisible(false);  // already false from ctor
        f.setVisible(true);
        f.setVisible(true);   // second show — skip
        f.setVisible(false);
        f.setVisible(false);  // second hide — skip

        assertEquals(List.of("opened"), wl.kinds());
        assertEquals(List.of("shown", "hidden"), comp);
    }

    @Test
    @DisplayName("dispose detaches peer and fires WINDOW_CLOSED")
    void disposeDetachesPeerAndFiresWindowClosed() {
        Frame f = new Frame();
        RecordingWindowListener l = new RecordingWindowListener();
        f.addWindowListener(l);
        f.setVisible(true);
        // Drop the prior "opened" event — we're asserting dispose behaviour.
        l.events.clear();

        f.dispose();

        Dialog d = (Dialog) f.getPeer();
        assertFalse(d.isOpened());
        assertFalse(d.getElement().getNode().isAttached());
        assertFalse(f.isVisible());
        assertFalse(f.isDisplayable());
        assertFalse(f.isShowing());
        // dispose on a visible Window fires COMPONENT_HIDDEN (via the
        // visible→false transition) AND WINDOW_CLOSED. No WINDOW_CLOSING —
        // that's for user-initiated close only.
        assertEquals(List.of("closed"), l.kinds());
    }

    @Test
    @DisplayName("dispose on a never-displayable Window fires nothing at all")
    void disposeOnANeverDisplayableWindowFiresNothingAtAll() {
        // The JDK gates WINDOW_CLOSED on isDisplayable() read *before* the
        // dispose work (`boolean fireWindowClosedEvent = isDisplayable()`), so a
        // Window that was never shown and never packed fires no WINDOW_CLOSED.
        // Measured on JDK 25 with the EventQueue drained — an unflushed read
        // reports the opposite, because postWindowEvent is asynchronous.
        // R_decline_effect_only: inventing an event real Swing never fires is the same class of
        // bug as dropping one.
        Frame f = new Frame();
        RecordingWindowListener wl = new RecordingWindowListener();
        List<String> comp = new ArrayList<>();
        f.addWindowListener(wl);
        f.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                comp.add("hidden");
            }
        });

        f.dispose();

        assertEquals(List.of(), wl.kinds());
        assertTrue(comp.isEmpty());
    }

    @Test
    @DisplayName("dispose on a displayable Window does fire WINDOW_CLOSED")
    void disposeOnADisplayableWindowDoesFireWindowClosed() {
        // The other half of the gate: pack() alone is enough to reach
        // addNotify(), so a packed-but-never-shown Window is displayable and
        // its dispose does fire. No COMPONENT_HIDDEN — it was never visible.
        Frame f = new Frame();
        RecordingWindowListener wl = new RecordingWindowListener();
        List<String> comp = new ArrayList<>();
        f.pack();
        f.addWindowListener(wl);
        f.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                comp.add("hidden");
            }
        });

        f.dispose();

        assertEquals(List.of("closed"), wl.kinds());
        assertTrue(comp.isEmpty());

        // And a second dispose is silent, the bit being already clear.
        wl.events.clear();
        f.dispose();
        assertEquals(List.of(), wl.kinds());
    }

    @Test
    @DisplayName("dispose then setVisible true refires WINDOW_OPENED")
    void disposeThenSetVisibleTrueRefiresWindowOpened() {
        // AWT: dispose destroys the peer; a subsequent show is a brand-new
        // first-show. We emulate by resetting the everShown latch in
        // dispose() — the Dialog peer is the same instance, but the Swing
        // contract is honored.
        Frame f = new Frame();
        RecordingWindowListener l = new RecordingWindowListener();
        f.addWindowListener(l);

        f.setVisible(true);
        f.dispose();
        f.setVisible(true);

        assertEquals(List.of("opened", "closed", "opened"), l.kinds());
    }

    @Test
    @DisplayName("user-initiated Dialog close fires WINDOW_CLOSING on the Window")
    void userInitiatedDialogCloseFiresWindowClosingOnTheWindow() {
        // Simulates ESC / outside-click: the Dialog peer fires
        // OpenedChangeEvent with isOpened=false from the client, and the
        // Window's subscribed listener mirrors that back into Swing state.
        // WINDOW_CLOSING is the right id — WINDOW_CLOSED is dispose's job;
        // whether to actually dispose belongs to defaultCloseOperation
        // (JFrame, later slice).
        Frame f = new Frame();
        RecordingWindowListener l = new RecordingWindowListener();
        List<String> comp = new ArrayList<>();
        f.addWindowListener(l);
        f.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                comp.add("hidden");
            }
        });
        f.setVisible(true);
        l.events.clear();

        // Karibu's Dialog testing helper would normally drive this; we
        // reach into the peer and fire a client-side event directly to
        // avoid a helper dependency and keep the test focused on routing.
        Dialog dialog = (Dialog) f.getPeer();
        ComponentUtil.fireEvent(dialog, new Dialog.OpenedChangeEvent(dialog, /* fromClient = */ true));
        // Vaadin's client-side close also sets opened=false on the peer
        // before firing. Mirror that so subsequent isOpened reads agree
        // with what the client would have observed.
        dialog.setOpened(false);

        assertFalse(f.isVisible());
        assertEquals(List.of("closing"), l.kinds());
        assertEquals(List.of("hidden"), comp);
    }

    @Test
    @DisplayName("pack is a silent no-op")
    void packIsASilentNoOp() {
        // Dialog auto-sizes to content; pack has no pixel numbers to
        // reconcile under R_layouts_close_enough. Just verify the call doesn't log WARN or
        // throw — the behavior we care about is "apps that pack() at
        // startup still work".
        new Frame().pack();
    }
}
