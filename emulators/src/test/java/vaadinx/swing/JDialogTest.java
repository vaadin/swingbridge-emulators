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
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.dialog.Dialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJDialog;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;
import vaadinx.awt.Component;
import vaadinx.awt.Container;
import vaadinx.awt.Frame;
import vaadinx.awt.LayoutManager;
import vaadinx.awt.event.WindowAdapter;
import vaadinx.awt.event.WindowEvent;

import java.awt.Dimension;
import java.awt.IllegalComponentStateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.WindowConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JDialogTest extends AbstractKaribuTest {

    /** The emulator's direct children, as a list — every containment assertion below reads this. */
    private static List<Component> childrenOf(Container c) {
        return Arrays.asList(c.getComponents());
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new JDialog();
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down peer is SJDialog")
    void peerIsSjDialog() {
        // R_leaf_peer_lockdown — JDialog is a leaf in the public javax.swing hierarchy;
        // every public ctor hardcodes SJDialog as the peer.
        assertInstanceOf(SJDialog.class, new JDialog().getPeer());
    }

    @Test
    @DisplayName("dialogInit installs a non-null Container as content pane")
    void dialogInitInstallsAContentPane() {
        assertNotNull(new JDialog().getContentPane());
    }

    @Test
    @DisplayName("rootPaneCheckingEnabled is true after dialogInit")
    void rootPaneCheckingEnabledAfterDialogInit() {
        assertTrue(new JDialog().isRootPaneCheckingEnabled());
    }

    // --- add / remove redirect (mirrors JFrame) -------------------------

    @Test
    @DisplayName("dialog add routes the child into the content pane")
    void addRoutesTheChildIntoTheContentPane() {
        JDialog d = new JDialog();
        Container child = new Container();
        d.add(child);
        // The dialog's one child is the root pane, as in Swing — the content
        // pane is two levels down, under the layered pane.
        assertEquals(List.of(d.getRootPane()), childrenOf(d));
        assertEquals(List.of(child), childrenOf(d.getContentPane()));
        assertSame(d.getContentPane(), child.getParent());
        assertSame(d.getRootPane().getLayeredPane(), d.getContentPane().getParent());
    }

    @Test
    @DisplayName("dialog setLayout installs on the content pane")
    void setLayoutInstallsOnTheContentPane() {
        JDialog d = new JDialog();
        LayoutManager layout = new RecordingLayout();
        d.setLayout(layout);
        assertSame(layout, d.getContentPane().getLayout());
    }

    @Test
    @DisplayName("dialog remove deletes from the content pane")
    void removeDeletesFromTheContentPane() {
        JDialog d = new JDialog();
        Container child = new Container();
        d.add(child);
        d.remove(child);
        assertTrue(childrenOf(d.getContentPane()).isEmpty());
    }

    @Test
    @DisplayName("setContentPane replaces the pane and detaches the old one")
    void setContentPaneReplacesAndDetaches() {
        JDialog d = new JDialog();
        Container original = d.getContentPane();
        Container replacement = new Container();
        d.setContentPane(replacement);
        assertSame(replacement, d.getContentPane());
        assertNotSame(original, d.getContentPane());
        assertEquals(List.of(d.getRootPane()), childrenOf(d));
        assertEquals(List.of(replacement), childrenOf(d.getRootPane().getLayeredPane()));
        assertNull(original.getParent());
    }

    @Test
    @DisplayName("setContentPane null throws IllegalComponentStateException")
    void setContentPaneNullThrows() {
        assertThrows(IllegalComponentStateException.class, () -> new JDialog().setContentPane(null));
    }

    @Test
    @DisplayName("setRootPaneCheckingEnabled false routes add to the dialog directly")
    void rootPaneCheckingDisabledRoutesAddDirectly() {
        JDialog d = new JDialog();
        d.setRootPaneCheckingEnabled(false);
        Container bare = new Container();
        d.add(bare);
        assertEquals(List.of(d.getRootPane(), bare), childrenOf(d));
    }

    // --- Constructor variations -----------------------------------------

    @Test
    @DisplayName("constructor with title seeds the title field")
    void ctorWithTitleSeedsTheTitle() {
        assertEquals("Edit", new JDialog((Frame) null, "Edit").getTitle());
    }

    @Test
    @DisplayName("constructor with Frame owner registers the owned-window link")
    void ctorWithFrameOwnerRegistersTheOwnedWindowLink() {
        JFrame frame = new JFrame();
        JDialog dialog = new JDialog(frame);
        assertSame(frame, dialog.getOwner());
        assertTrue(Arrays.stream(frame.getOwnedWindows()).anyMatch(w -> w == dialog));
    }

    @Test
    @DisplayName("constructor with Frame, title, modal sets all three")
    void ctorWithFrameTitleModalSetsAllThree() {
        JFrame frame = new JFrame();
        JDialog d = new JDialog(frame, "Title", true);
        assertEquals("Title", d.getTitle());
        assertTrue(d.isModal());
        assertSame(frame, d.getOwner());
    }

    @Test
    @DisplayName("constructor with Window owner takes a ModalityType")
    void ctorWithWindowOwnerTakesAModalityType() {
        JFrame frame = new JFrame();
        JDialog d = new JDialog(frame, vaadinx.awt.Dialog.ModalityType.DOCUMENT_MODAL);
        assertEquals(vaadinx.awt.Dialog.ModalityType.DOCUMENT_MODAL, d.getModalityType());
        assertTrue(d.isModal());
    }

    // --- Default close operation ---------------------------------------

    @Test
    @DisplayName("default close operation is HIDE_ON_CLOSE")
    void defaultCloseOperationIsHideOnClose() {
        assertEquals(WindowConstants.HIDE_ON_CLOSE, new JDialog().getDefaultCloseOperation());
    }

    @Test
    @DisplayName("setDefaultCloseOperation rejects EXIT_ON_CLOSE per JDK contract")
    void setDefaultCloseOperationRejectsExitOnClose() {
        // JDK JDialog.setDefaultCloseOperation rejects EXIT_ON_CLOSE
        // — only JFrame may terminate the JVM.
        assertThrows(IllegalArgumentException.class,
                () -> new JDialog().setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE));
    }

    @Test
    @DisplayName("setDefaultCloseOperation round-trips each valid value")
    void setDefaultCloseOperationRoundTripsEachValidValue() {
        JDialog d = new JDialog();
        for (int op : new int[]{
                WindowConstants.DO_NOTHING_ON_CLOSE,
                WindowConstants.HIDE_ON_CLOSE,
                WindowConstants.DISPOSE_ON_CLOSE}) {
            d.setDefaultCloseOperation(op);
            assertEquals(op, d.getDefaultCloseOperation());
        }
    }

    @Test
    @DisplayName("setDefaultCloseOperation rejects an invalid value")
    void setDefaultCloseOperationRejectsAnInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> new JDialog().setDefaultCloseOperation(42));
    }

    @Test
    @DisplayName("DO_NOTHING_ON_CLOSE disables peer's ESC and outside-click close")
    void doNothingOnCloseDisablesEscAndOutsideClick() {
        ExposedJDialog d = new ExposedJDialog();
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        assertFalse(d.dialogPeer().isCloseOnEsc());
        assertFalse(d.dialogPeer().isCloseOnOutsideClick());
    }

    /** One observed defaultCloseOperation transition — a record so both halves are named. */
    private record CloseOpChange(int oldValue, int newValue) {
    }

    @Test
    @DisplayName("setDefaultCloseOperation fires PCE on change")
    void setDefaultCloseOperationFiresPceOnChange() {
        JDialog d = new JDialog();
        List<CloseOpChange> changes = new ArrayList<>();
        d.addPropertyChangeListener("defaultCloseOperation",
                e -> changes.add(new CloseOpChange((Integer) e.getOldValue(), (Integer) e.getNewValue())));
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);  // no-op
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        assertEquals(
                List.of(
                        new CloseOpChange(WindowConstants.HIDE_ON_CLOSE, WindowConstants.DISPOSE_ON_CLOSE),
                        new CloseOpChange(WindowConstants.DISPOSE_ON_CLOSE, WindowConstants.DO_NOTHING_ON_CLOSE)),
                changes);
    }

    @Test
    @DisplayName("DISPOSE_ON_CLOSE disposes the dialog when the user closes it")
    void disposeOnCloseDisposesOnUserClose() {
        ExposedJDialog d = new ExposedJDialog();
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        List<String> events = new ArrayList<>();
        d.addWindowListener(closeRecorder(events));
        d.setVisible(true);
        closeFromClient(d.dialogPeer());
        assertEquals(List.of("closing", "closed"), events);
        assertFalse(d.isDisplayable());
    }

    @Test
    @DisplayName("HIDE_ON_CLOSE keeps the dialog displayable after the user closes it")
    void hideOnCloseKeepsTheDialogDisplayable() {
        ExposedJDialog d = new ExposedJDialog();
        List<String> events = new ArrayList<>();
        d.addWindowListener(closeRecorder(events));
        d.setVisible(true);
        closeFromClient(d.dialogPeer());
        assertEquals(List.of("closing"), events);
        assertTrue(d.isDisplayable());
        assertFalse(d.isVisible());
    }

    // --- Modal / modality type round-trip --------------------------------

    @Test
    @DisplayName("setModal true mirrors to peer Vaadin Dialog modality")
    void setModalTrueMirrorsToPeerModality() {
        JDialog d = new JDialog();
        d.setModal(true);
        assertEquals(ModalityMode.STRICT, ((Dialog) d.getPeer()).getModality());
    }

    @Test
    @DisplayName("setModal false mirrors to peer MODELESS")
    void setModalFalseMirrorsToPeerModeless() {
        JDialog d = new JDialog();
        d.setModal(true);
        d.setModal(false);
        assertEquals(ModalityMode.MODELESS, ((Dialog) d.getPeer()).getModality());
    }

    @Test
    @DisplayName("setModalityType DOCUMENT_MODAL maps to STRICT")
    void setModalityTypeDocumentModalMapsToStrict() {
        JDialog d = new JDialog();
        d.setModalityType(vaadinx.awt.Dialog.ModalityType.DOCUMENT_MODAL);
        assertEquals(ModalityMode.STRICT, ((Dialog) d.getPeer()).getModality());
        assertTrue(d.isModal());
    }

    @Test
    @DisplayName("setModal fires no bound property")
    void setModalFiresNoBoundProperty() {
        // Not a bound property anywhere in java.awt/javax.swing — see
        // DialogTest and D_property_fanout_audit.
        JDialog d = new JDialog();
        Counter fired = new Counter();
        d.addPropertyChangeListener("modal", e -> fired.inc());
        d.setModal(true);
        d.setModal(false);
        fired.assertEquals(0);
    }

    // --- Blocking modal park (loom integration) -------------------------

    @Test
    @DisplayName("setVisible true on modal dialog from a non-VT context throws ISE")
    void modalSetVisibleFromNonVtContextThrows() {
        // Outside a UI fiber, parking would block the request thread.
        // UIFibers.checkInUIFiber() fails fast with IllegalStateException.
        JDialog d = new JDialog();
        d.setModal(true);
        assertThrows(IllegalStateException.class, () -> d.setVisible(true));
    }

    @Test
    @DisplayName("setVisible true on modeless dialog returns immediately")
    void modelessSetVisibleReturnsImmediately() {
        // No park, no exception — modeless is the always-non-blocking path.
        JDialog d = new JDialog();
        assertFalse(d.isModal());
        d.setVisible(true);
        assertTrue(d.isVisible());
    }

    @Test
    @DisplayName("blocking modal show parks until dispose() releases")
    void blockingModalShowParksUntilDispose() throws InterruptedException {
        // End-to-end blocking-modal flow through a UI fiber: show on a VT
        // (parks), dispose() from another callSwing releases the latch, the
        // VT resumes and setVisible returns. Coordinated via a CountDownLatch
        // that the VT signals when setVisible returns; the test thread waits
        // up to 5s for that signal.
        JDialog d = new JDialog();
        d.setModal(true);
        CountDownLatch returned = new CountDownLatch(1);
        // Drive setVisible on a VT (callSwing wraps in VT + drains pending
        // tasks synchronously). When setVisible parks, the carrier completes
        // and callSwing's pending-task drain returns control to the test.
        EHelper.callSwing(() -> {
            d.setVisible(true);   // parks here on the VT
            returned.countDown(); // only reached after dispose unblocks
        });
        // At this point the VT is parked. callSwing returned control
        // because the VT unmounted.
        assertEquals(1, returned.getCount(), "VT should still be parked");
        // Dispose from another callSwing — releases the modal latch; the
        // parked VT resumes, runs returned.countDown.
        EHelper.callSwing(d::dispose);
        assertTrue(returned.await(5, TimeUnit.SECONDS),
                "modal show did not return within 5s of dispose()");
    }

    // --- paramString -----------------------------------------------------

    @Test
    @DisplayName("paramString is the JDK's: modality type, title, close operation, root pane")
    void paramStringIncludesTitleModalAndCloseOperation() {
        ExposedJDialog d = new ExposedJDialog();
        d.setTitle("Hi");
        d.setModal(true);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        String s = d.exposedParamString();
        assertTrue(s.contains(",APPLICATION_MODAL,title=Hi,defaultCloseOperation=DISPOSE_ON_CLOSE,rootPane="), s);
        assertTrue(s.endsWith(",rootPaneCheckingEnabled=true"), s);
    }

    /**
     * Logs what a constructor reaches, into a static list since the super constructor runs
     * before any field of this class is assigned.
     */
    private static final List<String> CTOR_LOG = new ArrayList<>();

    private static class LoggingJDialog extends JDialog {
        LoggingJDialog(vaadinx.awt.Frame owner, String title, boolean modal) {
            super(owner, title, modal);
        }

        LoggingJDialog(vaadinx.awt.Window owner, String title, vaadinx.awt.Dialog.ModalityType modalityType) {
            super(owner, title, modalityType);
        }

        @Override public void setTitle(String t) { CTOR_LOG.add("setTitle(" + t + ")"); super.setTitle(t); }
        @Override public void setModal(boolean b) { CTOR_LOG.add("setModal(" + b + ")"); super.setModal(b); }
        @Override public void setModalityType(vaadinx.awt.Dialog.ModalityType t) {
            CTOR_LOG.add("setModalityType(" + t + ")");
            super.setModalityType(t);
        }
        @Override protected void dialogInit() {
            CTOR_LOG.add("dialogInit title=" + getTitle() + " modal=" + getModalityType());
            super.dialogInit();
        }
    }

    /**
     * Measured on JDK 25: every constructor reaches {@code Dialog}'s, which assigns the title
     * and calls {@code setModalityType}, and only then {@code dialogInit}; {@code setTitle} /
     * {@code setModal} are never called, and a Window owner that is neither a Frame nor a
     * Dialog throws.
     */
    @Test
    @DisplayName("constructors reach setModalityType before dialogInit, as the JDK's do")
    void constructorsMatchTheJdk() {
        CTOR_LOG.clear();
        new LoggingJDialog((vaadinx.awt.Frame) null, "j", true);
        assertEquals(List.of("setModalityType(APPLICATION_MODAL)", "dialogInit title=j modal=APPLICATION_MODAL"), CTOR_LOG);
        CTOR_LOG.clear();
        LoggingJDialog untitled = new LoggingJDialog((vaadinx.awt.Window) null, null, vaadinx.awt.Dialog.ModalityType.MODELESS);
        assertEquals(List.of("setModalityType(MODELESS)", "dialogInit title=null modal=MODELESS"), CTOR_LOG);
        assertNull(untitled.getTitle());

        vaadinx.awt.Window notAFrame = new vaadinx.awt.Window(new JFrame());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new JDialog(notAFrame));
        assertEquals("Wrong parent window", e.getMessage());

        JDialog owner = new JDialog();
        assertEquals(vaadinx.awt.Dialog.ModalityType.APPLICATION_MODAL, new JDialog(owner, true).getModalityType());
    }

    // --- JMenuBar slot --------------------------------------------------

    @Test
    @DisplayName("setJMenuBar delegates to SJDialog and round-trips")
    void setJMenuBarDelegatesAndRoundTrips() {
        JDialog d = new JDialog();
        JMenuBar bar = new JMenuBar();
        d.setJMenuBar(bar);
        assertSame(bar, d.getJMenuBar());
        assertSame(bar.getPeer(), ((SJDialog) d.getPeer()).getJMenuBar());
    }

    @Test
    @DisplayName("setJMenuBar fires no JMenuBar PCE")
    void setJMenuBarFiresNoPce() {
        // The JDK's JDialog.setJMenuBar is one line — getRootPane().setJMenuBar(menu)
        // — and neither class fires a property change. JInternalFrame is the only
        // class in javax.swing that fires "JMenuBar" (its MENU_BAR_PROPERTY), so
        // the absence here is the JDK's body, not a gap. This asserted the
        // opposite, i.e. an event migrated code never received on the desktop (R_decline_effect_only).
        JDialog d = new JDialog();
        Counter fired = new Counter();
        d.addPropertyChangeListener("JMenuBar", e -> fired.inc());
        d.setJMenuBar(new JMenuBar());
        fired.assertEquals(0);
    }

    @Test
    @DisplayName("setJMenuBar(null) detaches")
    void setJMenuBarNullDetaches() {
        JDialog d = new JDialog();
        d.setJMenuBar(new JMenuBar());
        d.setJMenuBar(null);
        assertNull(d.getJMenuBar());
    }

    @Test
    @DisplayName("getJMenuBar is null until set")
    void getJMenuBarIsNullUntilSet() {
        assertNull(new JDialog().getJMenuBar());
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

    private static class RecordingLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
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

    private static class ExposedJDialog extends JDialog {

        Dialog dialogPeer() {
            return (Dialog) getPeer();
        }

        String exposedParamString() {
            return paramString();
        }
    }
}
