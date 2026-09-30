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

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.dialog.Dialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJFrame;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.awt.Component;
import vaadinx.awt.Container;
import vaadinx.awt.LayoutManager;
import vaadinx.awt.event.WindowAdapter;
import vaadinx.awt.event.WindowEvent;

import java.awt.Dimension;
import java.awt.IllegalComponentStateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.WindowConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JFrameTest extends AbstractKaribuTest {

    /** The emulator's direct children, as a list — every containment assertion below reads this. */
    private static List<Component> childrenOf(Container c) {
        return Arrays.asList(c.getComponents());
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new JFrame();
    }

    @Test
    @DisplayName("frameInit installs a non-null Container as content pane")
    void frameInitInstallsAContentPane() {
        // Regression guard for frameInit: a fresh JFrame must always have
        // a content pane so the first frame.add(...) call doesn't NPE.
        // (Content pane must be Div-backed for D_layout_css_on_content's CSS dispatch to land;
        // verified via FrameTest's CSS-writing assertions on the inherited
        // setBackground path and ContainerTest's layout coverage.)
        assertNotNull(new JFrame().getContentPane());
    }

    @Test
    @DisplayName("rootPaneCheckingEnabled is true after frameInit")
    void rootPaneCheckingEnabledAfterFrameInit() {
        // frameInit flips the flag only after the content pane is linked;
        // a subclass that overrode frameInit and looked at the flag
        // mid-build would see false, which is exactly what Swing does.
        assertTrue(new JFrame().isRootPaneCheckingEnabled());
    }

    @Test
    @DisplayName("frame add routes the child into the content pane")
    void addRoutesTheChildIntoTheContentPane() {
        // The core redirect: frame.add(comp) must land in contentPane,
        // not in the frame's own children list. frame.getComponents
        // keeps the rootPane-only shape (= [contentPane]); everything
        // user code adds shows up under contentPane.
        JFrame f = new JFrame();
        Container child = new Container();

        f.add(child);

        // The frame's one child is the root pane, as in Swing — the content pane
        // is two levels down, under the layered pane.
        assertEquals(List.of(f.getRootPane()), childrenOf(f));
        assertEquals(List.of(child), childrenOf(f.getContentPane()));
        assertSame(f.getContentPane(), child.getParent());
        assertSame(f.getRootPane().getLayeredPane(), f.getContentPane().getParent());
    }

    /** One recorded {@code addLayoutComponent} call. */
    private record LayoutAdd(String name, Component comp) {
    }

    @Test
    @DisplayName("frame add with string constraints reaches the content pane's layout")
    void addWithStringConstraintsReachesTheLayout() {
        // BorderLayout-style String constraints are the common case; the
        // 3-arg add path must carry them through frame → contentPane.add
        // so the installed LayoutManager sees them in addLayoutComponent.
        // Use a recording stub: FlowLayout etc. aren't ported yet.
        JFrame f = new JFrame();
        List<LayoutAdd> recorded = new ArrayList<>();
        f.setLayout(new RecordingLayout(recorded));
        Container child = new Container();

        f.add(child, "North");

        assertEquals(List.of(child), childrenOf(f.getContentPane()));
        LayoutAdd add = assertSingle(recorded);
        assertEquals("North", add.name());
        assertSame(child, add.comp());
    }

    @Test
    @DisplayName("frame setLayout installs on the content pane")
    void setLayoutInstallsOnTheContentPane() {
        // frame.setLayout(...) is idiomatic Swing — migrated code expects
        // that layout to drive the content pane's children, not the frame
        // itself (which has nothing user-facing to lay out).
        JFrame f = new JFrame();
        LayoutManager layout = new RecordingLayout(new ArrayList<>());

        f.setLayout(layout);

        assertSame(layout, f.getContentPane().getLayout());
    }

    @Test
    @DisplayName("frame remove deletes from the content pane")
    void removeDeletesFromTheContentPane() {
        // Symmetric with add: frame.remove(child) finds the child in
        // contentPane, not in frame's [contentPane] list. Removing the
        // wrong way would no-op silently (comp not found) — test that
        // the redirect actually fires.
        JFrame f = new JFrame();
        Container child = new Container();
        f.add(child);

        f.remove(child);

        assertTrue(childrenOf(f.getContentPane()).isEmpty());
    }

    @Test
    @DisplayName("setContentPane replaces the pane and detaches the old one")
    void setContentPaneReplacesAndDetaches() {
        JFrame f = new JFrame();
        Container original = f.getContentPane();
        Container replacement = new Container();

        f.setContentPane(replacement);

        assertSame(replacement, f.getContentPane());
        assertNotSame(original, f.getContentPane());
        // Old pane is unparented (removed from the layered pane AND the peer
        // DOM); the new one is the layered pane's sole child. The frame's own
        // child list is unchanged — it holds the root pane either way.
        assertEquals(List.of(f.getRootPane()), childrenOf(f));
        assertEquals(List.of(replacement), childrenOf(f.getRootPane().getLayeredPane()));
        assertNull(original.getParent());
    }

    @Test
    @DisplayName("setContentPane null throws IllegalComponentStateException")
    void setContentPaneNullThrows() {
        // Matches real Swing (JRootPane.setContentPane). D_never_fail_on_gaps scopes the
        // never-throw rule to incomplete emulation, not inputs Swing
        // itself rejects — null contentPane is a programming error.
        assertThrows(IllegalComponentStateException.class, () -> new JFrame().setContentPane(null));
    }

    @Test
    @DisplayName("setRootPaneCheckingEnabled false routes add to the frame directly")
    void rootPaneCheckingDisabledRoutesAddDirectly() {
        // Rare escape hatch: user code disables the flag to add something
        // *around* the content pane (custom chrome). With the flag off,
        // frame.add(x) goes to the frame's own children list — the
        // super.addImpl path — rather than the content pane.
        JFrame f = new JFrame();
        f.setRootPaneCheckingEnabled(false);
        Container bare = new Container();

        f.add(bare);

        assertEquals(List.of(f.getRootPane(), bare), childrenOf(f));
        // content pane untouched
        assertTrue(childrenOf(f.getContentPane()).isEmpty());
    }

    @Test
    @DisplayName("constructor with title seeds the title field")
    void ctorWithTitleSeedsTheTitle() {
        // JFrame delegates setTitle to Frame; the Dialog-header round-trip
        // is asserted in FrameTest. Here we just verify the ctor wiring
        // survives the frameInit changes and the title field is set
        // before frameInit runs (so subclasses reading it see the value).
        assertEquals("Hello", new JFrame("Hello").getTitle());
    }

    @Test
    @DisplayName("setVisible true displayables the content pane and its children")
    void setVisibleDisplayablesTheContentPaneAndChildren() {
        // After the frame opens its Dialog, the content pane (direct child
        // of the Dialog) and anything user code added to it must all
        // become displayable — the peer attachment chain has to reach
        // from the Dialog through the content pane down to the leaves.
        // Verifies the frameInit super.addImpl path actually lands the
        // content pane under the Dialog in the Flow state tree.
        JFrame f = new JFrame();
        Container inner = new Container();
        f.add(inner);

        assertFalse(f.isDisplayable());
        assertFalse(f.getContentPane().isDisplayable());
        assertFalse(inner.isDisplayable());

        f.setVisible(true);

        assertTrue(f.isDisplayable());
        assertTrue(f.getContentPane().isDisplayable());
        assertTrue(inner.isDisplayable());
    }

    @Test
    @DisplayName("default close operation is HIDE_ON_CLOSE")
    void defaultCloseOperationIsHideOnClose() {
        // Swing's JFrame javadoc documents this default; migrated apps that
        // don't explicitly call setDefaultCloseOperation rely on it (closing
        // the window hides it but keeps the app alive).
        assertEquals(WindowConstants.HIDE_ON_CLOSE, new JFrame().getDefaultCloseOperation());
    }

    @Test
    @DisplayName("setDefaultCloseOperation round-trips each valid value")
    void setDefaultCloseOperationRoundTripsEachValidValue() {
        JFrame f = new JFrame();
        for (int op : new int[]{
                WindowConstants.DO_NOTHING_ON_CLOSE,
                WindowConstants.HIDE_ON_CLOSE,
                WindowConstants.DISPOSE_ON_CLOSE,
                WindowConstants.EXIT_ON_CLOSE}) {
            f.setDefaultCloseOperation(op);
            assertEquals(op, f.getDefaultCloseOperation());
        }
    }

    @Test
    @DisplayName("setDefaultCloseOperation rejects an invalid value")
    void setDefaultCloseOperationRejectsAnInvalidValue() {
        // Matches Swing — D_never_fail_on_gaps scopes the never-throw rule to incomplete
        // emulation, not inputs Swing itself rejects.
        assertThrows(IllegalArgumentException.class, () -> new JFrame().setDefaultCloseOperation(42));
    }

    @Test
    @DisplayName("DO_NOTHING_ON_CLOSE disables the peer's ESC and outside-click close")
    void doNothingOnCloseDisablesEscAndOutsideClick() {
        // By the time WINDOW_CLOSING fires, the peer has already closed —
        // the only way to honor DO_NOTHING is to block the gesture at the
        // Dialog level. Switching to a dismissible op must flip the flags
        // back on so the user isn't stuck in a modal they can't close.
        ExposedJFrame f = new ExposedJFrame();
        Dialog d = f.dialogPeer();

        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        assertFalse(d.isCloseOnEsc());
        assertFalse(d.isCloseOnOutsideClick());

        f.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        assertTrue(d.isCloseOnEsc());
        assertTrue(d.isCloseOnOutsideClick());
    }

    /** One observed defaultCloseOperation transition — a record so both halves are named. */
    private record CloseOpChange(int oldValue, int newValue) {
    }

    @Test
    @DisplayName("setDefaultCloseOperation fires a PropertyChangeEvent on change")
    void setDefaultCloseOperationFiresPceOnChange() {
        JFrame f = new JFrame();
        List<CloseOpChange> changes = new ArrayList<>();
        f.addPropertyChangeListener("defaultCloseOperation",
                e -> changes.add(new CloseOpChange((Integer) e.getOldValue(), (Integer) e.getNewValue())));

        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); // no-op, no event
        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        assertEquals(
                List.of(
                        new CloseOpChange(WindowConstants.HIDE_ON_CLOSE, WindowConstants.DISPOSE_ON_CLOSE),
                        new CloseOpChange(WindowConstants.DISPOSE_ON_CLOSE, WindowConstants.DO_NOTHING_ON_CLOSE)),
                changes);
    }

    @Test
    @DisplayName("EXIT_ON_CLOSE peer-originated close throws ISE per D_gap_severity_triage")
    void exitOnClosePeerOriginatedCloseThrows() {
        // D_gap_severity_triage's second throw case applies through
        // the emulator path too — JFrame.processWindowEvent throws on the
        // close-attempt rather than silently noop'ing the migrator's
        // "terminate the JVM" intent.
        ExposedJFrame f = new ExposedJFrame();
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setVisible(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> f.dialogPeer().setOpened(false));
        assertTrue(ex.getMessage().contains("EXIT_ON_CLOSE"));
        assertTrue(ex.getMessage().contains("D_gap_severity_triage"));
    }

    @Test
    @DisplayName("DISPOSE_ON_CLOSE disposes the frame when the user closes the Dialog")
    void disposeOnCloseDisposesOnUserClose() {
        // End-to-end: user presses ESC / clicks outside → Dialog reports
        // opened=false → Window fires WINDOW_CLOSING → JFrame's
        // processWindowEvent calls dispose(), which detaches the peer and
        // fires WINDOW_CLOSED. Exercises the full path that migrated apps
        // using DISPOSE_ON_CLOSE depend on.
        ExposedJFrame f = new ExposedJFrame();
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        List<String> events = new ArrayList<>();
        f.addWindowListener(closeRecorder(events));
        f.setVisible(true);
        closeFromClient(f.dialogPeer());

        assertEquals(List.of("closing", "closed"), events);
        assertFalse(f.isDisplayable());
    }

    @Test
    @DisplayName("HIDE_ON_CLOSE keeps the frame displayable after the user closes it")
    void hideOnCloseKeepsTheFrameDisplayable() {
        // Default behavior: closing hides but does not dispose, so the app
        // can reopen the frame with setVisible(true) without re-constructing.
        // No WINDOW_CLOSED fires — that's dispose's exclusive event.
        ExposedJFrame f = new ExposedJFrame();
        List<String> events = new ArrayList<>();
        f.addWindowListener(closeRecorder(events));
        f.setVisible(true);
        closeFromClient(f.dialogPeer());

        assertEquals(List.of("closing"), events);
        assertTrue(f.isDisplayable());
        assertFalse(f.isVisible());
    }

    @Test
    @DisplayName("paramString includes defaultCloseOperation")
    void paramStringIncludesDefaultCloseOperation() {
        // Debug aid for toString chains: migrated code that prints a JFrame
        // should see the close op so misconfigured DO_NOTHING windows are
        // easy to spot. Also covers Frame.paramString's title append.
        ExposedJFrame f = new ExposedJFrame("Hi");
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        String s = f.exposedParamString();
        assertTrue(s.contains("title=Hi"), s);
        assertTrue(s.contains("defaultCloseOperation=DISPOSE_ON_CLOSE"), s);
    }

    // --- JMenuBar slot (D_menu_tree) -----------------------------------------

    @Test
    @DisplayName("setJMenuBar delegates to SJFrame and round-trips")
    void setJMenuBarDelegatesAndRoundTrips() {
        JFrame f = new JFrame();
        JMenuBar bar = new JMenuBar();
        f.setJMenuBar(bar);
        assertSame(bar, f.getJMenuBar());
        assertSame(bar.getPeer(), ((SJFrame) f.getPeer()).getJMenuBar());
    }

    @Test
    @DisplayName("setJMenuBar fires no bound property")
    void setJMenuBarFiresNoBoundProperty() {
        // Real Swing routes setJMenuBar into JRootPane and neither class fires
        // anything for it — there is no "JMenuBar" bound property anywhere in
        // java.awt/javax.swing. SB-Emulators used to invent one, handing migrated code a
        // PropertyChangeEvent it never saw on the desktop (R_decline_effect_only,
        // W_property_fanout). The menu bar itself is asserted above.
        JFrame f = new JFrame();
        Counter fired = new Counter();
        f.addPropertyChangeListener("JMenuBar", e -> fired.inc());
        f.setJMenuBar(new JMenuBar());
        fired.assertEquals(0);
    }

    @Test
    @DisplayName("setJMenuBar(null) detaches")
    void setJMenuBarNullDetaches() {
        JFrame f = new JFrame();
        f.setJMenuBar(new JMenuBar());
        f.setJMenuBar(null);
        assertNull(f.getJMenuBar());
        assertNull(((SJFrame) f.getPeer()).getJMenuBar());
    }

    @Test
    @DisplayName("getJMenuBar is null until set")
    void getJMenuBarIsNullUntilSet() {
        assertNull(new JFrame().getJMenuBar());
    }

    @Test
    @DisplayName("setJMenuBar same value is a no-op for PCE")
    void setJMenuBarSameValueIsANoOpForPce() {
        JFrame f = new JFrame();
        JMenuBar bar = new JMenuBar();
        f.setJMenuBar(bar);
        Counter fired = new Counter();
        f.addPropertyChangeListener("JMenuBar", e -> fired.inc());
        f.setJMenuBar(bar);
        fired.assertEquals(0);
    }

    // --- Helpers ---------------------------------------------------------

    /** Simulates the user closing the overlay: the peer shuts, then reports it as from-client. */
    private static void closeFromClient(Dialog peer) {
        peer.setOpened(false);
        ComponentUtil.fireEvent(peer, new Dialog.OpenedChangeEvent(peer, /* fromClient = */ true));
    }

    /** Records {@code "closing"} / {@code "closed"} into {@code events} in delivery order. */
    private static WindowAdapter closeRecorder(List<String> events) {
        return new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                events.add("closing");
            }

            @Override
            public void windowClosed(WindowEvent e) {
                events.add("closed");
            }
        };
    }

    /**
     * Minimal LayoutManager stub: records addLayoutComponent calls so we
     * can verify the frame → contentPane redirect actually reaches the
     * installed layout. preferred/minimum sizes return zero — the tests
     * here don't exercise layout geometry.
     */
    private record RecordingLayout(List<LayoutAdd> recorded) implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
            recorded.add(new LayoutAdd(name, comp));
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension();
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension();
        }

        @Override
        public void layoutContainer(Container parent) {
        }
    }

    /**
     * Exposes protected {@code paramString} and the peer Dialog so tests in this
     * package (not {@code vaadinx.awt}) can reach through Component.peer, which
     * is protected. Mirrors the WindowTest.TestWindow pattern.
     */
    private static class ExposedJFrame extends JFrame {

        ExposedJFrame() {
            this("");
        }

        ExposedJFrame(String title) {
            super(title);
        }

        Dialog dialogPeer() {
            return (Dialog) getPeer();
        }

        String exposedParamString() {
            return paramString();
        }
    }
}
