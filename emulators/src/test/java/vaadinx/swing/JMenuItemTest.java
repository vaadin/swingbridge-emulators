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
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.contextmenu.MenuItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJMenuBar;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;

import javax.swing.KeyStroke;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for D_menu_tree's JMenuItem emulator. Covers:
 *
 * <ol>
 *  <li>Non-leaf — protected (Component peer) ctor stays per D_peer_ctor_injection /
 *     R_leaf_peer_lockdown.</li>
 *  <li>text / icon / accelerator / actionCommand round-trip + PCE.</li>
 *  <li>enabled round-trip + PCE.</li>
 *  <li>ActionListener fan-out via the rebuild-installed click handler;
 *     source rebound to the emulator JMenuItem.</li>
 *  <li>Detached items (no JMenuBar ancestor) skip push but field-shadow.</li>
 *  <li>Inherited AbstractButton surface preserved.</li>
 * </ol>
 */
class JMenuItemTest extends AbstractKaribuTest {

    /** Ctrl+S, spelled with an explicit int key code so the {@code (int,int)} overload is unambiguous. */
    private static final KeyStroke CTRL_S =
            KeyStroke.getKeyStroke((int) 'S', InputEvent.CTRL_DOWN_MASK);

    /** A bar holding one menu holding {@code item}, paired with its surrogate peer. */
    private record Bar(JMenuBar bar, SJMenuBar peer) {
    }

    private static Bar barWith(String menuTitle, JMenuItem item) {
        JMenuBar bar = new JMenuBar();
        JMenu m = new JMenu(menuTitle);
        bar.add(m);
        m.add(item);
        return new Bar(bar, (SJMenuBar) bar.getPeer());
    }

    /** The Vaadin MenuItem under the bar's single top-level menu — the real browser click target. */
    private static MenuItem vaadinLeaf(SJMenuBar peer) {
        return peer.getItems().get(0).getSubMenu().getItems().get(0);
    }

    @Test
    @DisplayName("non-leaf — protected (Component peer) ctor present")
    void peerTakingCtorRemains() {
        Optional<Constructor<?>> seamCtor = Arrays.stream(JMenuItem.class.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1
                        && com.vaadin.flow.component.Component.class
                                .isAssignableFrom(ctor.getParameterTypes()[0]))
                .findFirst();
        assertTrue(seamCtor.isPresent(),
                "JMenuItem is non-leaf — protected (Component peer) ctor must remain "
                        + "per D_peer_ctor_injection");
    }

    @Test
    @DisplayName("text round-trips via setText")
    void textRoundTrips() {
        JMenuItem item = new JMenuItem("Save");
        assertEquals("Save", item.getText());
        item.setText("Save As");
        assertEquals("Save As", item.getText());
    }

    @Test
    @DisplayName("setText fires PCE")
    void setTextFiresPce() {
        JMenuItem item = new JMenuItem("Save");
        Counter fired = new Counter();
        item.addPropertyChangeListener("text", e -> fired.inc());
        item.setText("Save As");
        fired.assertEquals(1);
    }

    @Test
    @DisplayName("accelerator round-trip")
    void acceleratorRoundTrips() {
        JMenuItem item = new JMenuItem("Save");
        item.setAccelerator(CTRL_S);
        assertEquals(CTRL_S, item.getAccelerator());
    }

    @Test
    @DisplayName("setAccelerator fires PCE")
    void setAcceleratorFiresPce() {
        JMenuItem item = new JMenuItem("Save");
        Counter fired = new Counter();
        item.addPropertyChangeListener("accelerator", e -> fired.inc());
        item.setAccelerator(CTRL_S);
        fired.assertEquals(1);
    }

    @Test
    @DisplayName("setAccelerator with same value is a no-op")
    void setAcceleratorWithSameValueIsANoOp() {
        JMenuItem item = new JMenuItem("Save");
        item.setAccelerator(CTRL_S);
        Counter fired = new Counter();
        item.addPropertyChangeListener("accelerator", e -> fired.inc());
        item.setAccelerator(CTRL_S);
        fired.assertEquals(0);
    }

    @Test
    @DisplayName("actionCommand round-trips and fires no bound property")
    void actionCommandFiresNoBoundProperty() {
        // AbstractButton.setActionCommand is one line —
        // getModel().setActionCommand(cmd) — and neither it nor
        // DefaultButtonModel fires anything bound. The command is read back
        // when the ActionEvent is built, which is the whole notification (D_property_fanout_audit).
        JMenuItem item = new JMenuItem("Save");
        Counter fired = new Counter();
        item.addPropertyChangeListener("actionCommand", e -> fired.inc());
        item.setActionCommand("save-cmd");
        assertEquals("save-cmd", item.getActionCommand());
        fired.assertEquals(0);
    }

    @Test
    @DisplayName("actionCommand defaults to text per AbstractButton contract")
    void actionCommandDefaultsToText() {
        assertEquals("Save", new JMenuItem("Save").getActionCommand());
    }

    @Test
    @DisplayName("setEnabled round-trip + PCE")
    void setEnabledRoundTripsAndFires() {
        JMenuItem item = new JMenuItem("Save");
        Counter fired = new Counter();
        item.addPropertyChangeListener("enabled", e -> fired.inc());
        item.setEnabled(false);
        assertFalse(item.isEnabled());
        fired.assertEquals(1);
    }

    @Test
    @DisplayName("mutations on a detached item field-shadow but skip push")
    void detachedMutationsSkipPush() {
        JMenuItem item = new JMenuItem("Save");
        item.setText("Save As");  // no parent — push skipped
        assertEquals("Save As", item.getText());
        // Just doesn't crash. The next add to a parent picks up the
        // current state via the rebuild walk.
    }

    @Test
    @DisplayName("mutations on an attached item bubble to JMenuBar push")
    void attachedMutationsBubbleToPush() {
        JMenuItem item = new JMenuItem("Save");
        Bar b = barWith("File", item);
        item.setText("Save As");
        // Tree push picked up the new text.
        assertEquals("Save As", b.peer().getCurrentTree().get(0).children().get(0).text());
    }

    @Test
    @DisplayName("ActionListener fires on simulated click via MenuNode.onClick")
    void actionListenerFiresOnSimulatedClick() {
        JMenuItem item = new JMenuItem("Save");
        AtomicReference<Object> firedSource = new AtomicReference<>();
        item.addActionListener(e -> firedSource.set(e.getSource()));
        Bar b = barWith("File", item);
        // Simulate the rebuild's click bridge by invoking the leaf's
        // onClick — the same Runnable SJMenuBar wires into Vaadin's
        // ClickEvent handler.
        b.peer().getCurrentTree().get(0).children().get(0).onClick().run();
        // ActionEvent source rebound to the emulator JMenuItem per D_menu_tree.
        assertSame(item, firedSource.get());
    }

    @Test
    @DisplayName("peer click dispatches actionPerformed on a virtual thread "
            + "per R_callswing_envelope / D_callswing_loom")
    void peerClickRunsOnAVirtualThread() {
        // Regression: SJMenuBar's installed click listener runs the
        // MenuNode.onClick Runnable through com.vaadin.swingbridge.surrogates.SHelper.callSwing
        // (inline per SD_sframe). Before the fix, JMenuItem.makeOnClick handed
        // over a bare Runnable that called fireActionPerformed directly —
        // so user actionPerformed ran on the request-handler carrier
        // thread, and any user code calling JOptionPane.showMessageDialog
        // (or other modal park) hit Dialog.parkUntilClose's
        // UI-fiber check and threw IllegalStateException.
        //
        // The fix: JMenuItem.makeOnClick wraps its body in
        // vaadinx.EHelper.callSwing — same per-seam pattern AbstractButton
        // uses for its SJButton / SJToggleButton bridge listeners. The
        // VT envelope is in place before user code runs.
        JMenuItem save = new JMenuItem("Save");
        Bar b = barWith("File", save);
        UI.getCurrent().add(b.peer());

        AtomicReference<Boolean> wasVirtual = new AtomicReference<>();
        save.addActionListener(e -> wasVirtual.set(Thread.currentThread().isVirtual()));

        // Fire a real ClickEvent on the Vaadin MenuItem peer, through Vaadin's
        // event bus — the same path that fires when the browser sends a menu
        // click. This is what the bug-stack trace exercised; bypassing via
        // onClick().run() (as the "simulated click" test above does) would skip
        // the surrogate listener envelope entirely.
        MenuItem saveItem = vaadinLeaf(b.peer());
        ComponentUtil.fireEvent(saveItem, new ClickEvent<>(saveItem));

        assertNotNull(wasVirtual.get(), "actionPerformed did not fire on peer click");
        assertTrue(wasVirtual.get(),
                "actionPerformed must run on a virtual thread per R_callswing_envelope / D_callswing_loom");
    }

    @Test
    @DisplayName("peer click dispatches JOptionPane modal park without throwing")
    void peerClickCanOpenAModalDialog() throws InterruptedException {
        // Regression: the originating bug — user actionPerformed on a menu
        // item calls JOptionPane.showMessageDialog, which calls
        // Dialog.parkUntilClose → UIFibers.checkInUIFiber().
        // Before JMenuItem.makeOnClick wrapped in vaadinx.EHelper.callSwing,
        // the user actionPerformed ran on the request-handler thread and
        // the park threw IllegalStateException. Verifies the end-to-end
        // menu → blocking-dialog path now lands.
        JMenuItem about = new JMenuItem("About");
        Bar b = barWith("Help", about);
        UI.getCurrent().add(b.peer());

        CountDownLatch finished = new CountDownLatch(1);
        about.addActionListener(e -> {
            JOptionPane.showMessageDialog(null, "v1.0", "About", JOptionPane.INFORMATION_MESSAGE);
            finished.countDown();
        });

        MenuItem aboutItem = vaadinLeaf(b.peer());
        ComponentUtil.fireEvent(aboutItem, new ClickEvent<>(aboutItem));

        // showMessageDialog parked the VT; dismiss by clicking OK from a
        // fresh callSwing (the JOptionPane test pattern).
        Button ok = assertSingle(LocatorJ._find(Button.class,
                spec -> spec.withText("OK")));
        EHelper.callSwing(() -> LocatorJ._click(ok));
        assertTrue(finished.await(5, TimeUnit.SECONDS),
                "menu→showMessageDialog did not return after OK");
    }

    @Test
    @DisplayName("ActionEvent payload carries actionCommand")
    void actionEventCarriesTheCommand() {
        JMenuItem item = new JMenuItem("Save");
        item.setActionCommand("save-cmd");
        AtomicReference<String> cmd = new AtomicReference<>();
        item.addActionListener(e -> cmd.set(e.getActionCommand()));
        Bar b = barWith("File", item);
        b.peer().getCurrentTree().get(0).children().get(0).onClick().run();
        assertEquals("save-cmd", cmd.get());
    }

    @Test
    @DisplayName("ctor with mnemonic seeds field")
    void mnemonicCtorSeedsTheField() {
        JMenuItem item = new JMenuItem("Save", KeyEvent.VK_S);
        assertEquals(KeyEvent.VK_S, item.getMnemonic());
    }

    @Test
    @DisplayName("inherited isSelected default false")
    void inheritedIsSelectedIsFalse() {
        assertFalse(new JMenuItem("X").isSelected());
    }

    @Test
    @DisplayName("getUIClassID is MenuItemUI")
    void uiClassIdIsMenuItemUI() {
        assertEquals("MenuItemUI", new JMenuItem().getUIClassID());
    }
}
