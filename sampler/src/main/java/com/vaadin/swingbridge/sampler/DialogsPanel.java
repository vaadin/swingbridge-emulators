/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.sampler;

import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JDialog;
import vaadinx.swing.JFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JMenu;
import vaadinx.swing.JMenuBar;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.JPanel;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JTable;
import vaadinx.swing.JTextField;
import vaadinx.swing.Timer;

import javax.swing.WindowConstants;

/**
 * Dialogs demo. Exercises a
 * secondary {@link JFrame}'s lifecycle and the close-X chrome
 * SD_sframe/SD_sjframe added on the Dialog header. Closing the frame via the X
 * header button or ESC fires WINDOW_CLOSING and the configured
 * {@code defaultCloseOperation} (DISPOSE here) detaches the Dialog.
 *
 * <p>Under the {@code @MainWindow} shell, secondary {@link JFrame}s
 * (no annotation) take the {@code DialogStrategy} path and peer over
 * {@code SJFrame} (Dialog-backed) — they open as overlays on top of
 * the InlineStrategy SamplerFrame. The "Reopen frame" button rebuilds a
 * fresh frame after the previous one was disposed.
 *
 * <p>The {@code JOptionPane.showXxxDialog} family lives in
 * {@link OptionPanesPanel}. Modality appears here only in "Modal + owned
 * modeless window", which demos the window <em>attach point</em>
 * (D_owned_window_attach): a window owned by a parked modal must stay
 * interactive, a nested modal must curtain the owner it demotes. Both are
 * client-side facts — Flow's inert curtain and the overlay's focus trap —
 * that Karibu cannot see, so this route doubles as their browser probe.
 */
public class DialogsPanel extends JPanel {

    public DialogsPanel() {
        super(new BorderLayout(8, 8));

        JPanel guidance = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        guidance.add(new JLabel(
                "Click the close (✕) icon in the dialog header — or press ESC, or "
                        + "click outside the overlay — to dismiss."));
        guidance.add(new JLabel(
                "With DISPOSE_ON_CLOSE, the frame detaches and "
                        + "'Reopen' rebuilds it from scratch."));

        JButton reopen = new JButton("Reopen frame");
        reopen.addActionListener(e -> openFrame());
        guidance.add(reopen);

        JButton bounded = new JButton("Bounded frame — CENTER fills");
        bounded.addActionListener(e -> openBoundedFrame(false));
        guidance.add(bounded);

        JButton boundedMenu = new JButton("Bounded frame + menu bar");
        boundedMenu.addActionListener(e -> openBoundedFrame(true));
        guidance.add(boundedMenu);

        JButton ownedByModal = new JButton("Modal + owned modeless window");
        ownedByModal.addActionListener(e -> openModalWithOwnedWindow());
        guidance.add(ownedByModal);

        add(guidance, BorderLayout.NORTH);

        // Open the demo frame on first show.
        openFrame();
    }

    private static void openFrame() {
        JFrame frame = new JFrame("Dialogs demo");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JLabel hint = new JLabel("Use the ✕ in the header (or ESC / outside-click) to close.");
        frame.add(hint);

        JButton closeNow = new JButton("Close from inside");
        closeNow.addActionListener(e -> frame.dispose());
        frame.add(closeNow);

        // Glass-pane busy-curtain demo (D_glasspane_structural): "Run task" installs a
        // "Working…" panel as the frame's glass pane and makes it visible.
        // A visible glass pane fills the window body and — being the topmost
        // element — swallows all mouse input beneath it, so the buttons
        // above can't be clicked while the task runs. A one-shot Timer
        // stands in for a SwingWorker and hides the curtain when done.
        JButton runTask = new JButton("Run task (busy overlay 2s)");
        runTask.addActionListener(e -> runBusyTask(frame));
        frame.add(runTask);

        frame.setVisible(true);
    }

    /**
     * Opens a {@code setSize}d frame whose {@code BorderLayout} CENTER holds an
     * intrinsically-tall subtree, so NORTH/SOUTH take their intrinsic heights
     * and the scroll pane absorbs the rest of the 320px window.
     *
     * <p>The two variants exist because the menu bar is a sibling of the content
     * pane inside the overlay body (SD_sjmenubar/D_menu_tree), so it competes with the content
     * pane for the same span — a content pane that fills correctly without a bar
     * can still overflow with one.
     *
     * @param withMenuBar adds a {@link JMenuBar}, the harder of the two spans
     */
    private static void openBoundedFrame(boolean withMenuBar) {
        JFrame frame = new JFrame(withMenuBar ? "Bounded + menu bar" : "Bounded");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        if (withMenuBar) {
            JMenuBar bar = new JMenuBar();
            JMenu file = new JMenu("File");
            file.add(new JMenuItem("Close"));
            bar.add(file);
            frame.setJMenuBar(bar);
        }

        frame.getContentPane().setLayout(new BorderLayout(0, 4));
        frame.getContentPane().add(new JLabel("NORTH — intrinsic height"), BorderLayout.NORTH);

        Object[][] rows = new Object[40][2];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new Object[] {"row " + i, "value " + i};
        }
        frame.getContentPane().add(
                new JScrollPane(new JTable(rows, new String[] {"key", "value"})),
                BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        JButton close = new JButton("Close");
        close.addActionListener(e -> frame.dispose());
        south.add(close);
        frame.getContentPane().add(south, BorderLayout.SOUTH);

        frame.setSize(520, 320);
        frame.setVisible(true);
    }

    /**
     * Blocking modal, plus a modeless window it owns. {@code modal.setVisible(true)}
     * parks the calling virtual thread until the modal closes; every button
     * below is clicked while that park is still in effect, so the whole demo
     * exercises the owned-window attach point rather than the modal itself.
     */
    private static void openModalWithOwnedWindow() {
        JDialog modal = new JDialog((vaadinx.awt.Frame) null, "Parked modal", true);
        modal.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JLabel status = new JLabel("Owned window: not opened yet.");
        modal.add(status);

        // One-slot holder so the reshow button can reach the window the open
        // button created — the two listeners are siblings, not nested.
        JDialog[] owned = new JDialog[1];

        JButton openOwned = new JButton("Open owned modeless window");
        openOwned.addActionListener(e -> owned[0] = openOwnedWindow(modal, status));
        modal.add(openOwned);

        JButton nested = new JButton("Open nested modal");
        nested.addActionListener(e -> openNestedModal(modal, status));
        modal.add(nested);

        JButton cycle = new JButton("Hide + reshow this modal");
        cycle.addActionListener(e -> hideAndReshow(modal, owned[0]));
        modal.add(cycle);

        JButton close = new JButton("Close modal");
        close.addActionListener(e -> modal.dispose());
        modal.add(close);

        modal.setVisible(true);
    }

    /**
     * The interactivity probe: a field to type in and a button whose
     * ActionListener must fire, both inside a window owned by the still-parked
     * modal. Its echo lands on the modal's own label, so one glance shows that
     * both windows are live.
     */
    private static JDialog openOwnedWindow(JDialog modal, JLabel status) {
        JDialog window = new JDialog(modal, "Owned by the parked modal", false);
        window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextField message = new JTextField("type here", 18);
        window.add(message);

        JButton ping = new JButton("Ping the owner");
        ping.addActionListener(e -> status.setText("Owned window says: " + message.getText()));
        window.add(ping);

        window.setVisible(true);
        return window;
    }

    /**
     * The other side of the attach rule: a nested modal demotes its owner, so
     * the owner and everything it owns must go dead behind it — AWT blocks an
     * owner's chain the same way. Pinging from either window writes the same
     * status label, so a demoted owner still answering shows up at a glance.
     */
    private static void openNestedModal(JDialog owner, JLabel status) {
        JDialog nested = new JDialog(owner, "Nested modal", true);
        nested.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        nested.add(new JLabel("The owner below is now curtained."));

        JButton ping = new JButton("Ping from the nested modal");
        ping.addActionListener(e -> status.setText("Owned window says: nested modal pinged"));
        nested.add(ping);

        JButton close = new JButton("Close nested modal");
        close.addActionListener(e -> nested.dispose());
        nested.add(close);

        nested.setVisible(true);
    }

    /**
     * Owner-lifecycle probe: the owned window has to find its way back under
     * the re-opened modal, or it renders nowhere.
     *
     * <p>Two one-shot Timers rather than straight-line code, because
     * {@code modal.setVisible(false)} unparks the modal's own
     * {@code setVisible(true)} and the re-show below parks afresh — so
     * anything sequenced after it would not run until the modal closes.
     */
    private static void hideAndReshow(JDialog modal, JDialog owned) {
        modal.setVisible(false);

        Timer reshowModal = new Timer(1200, e -> modal.setVisible(true));
        reshowModal.setRepeats(false);
        reshowModal.start();

        if (owned == null) return;
        Timer reshowOwned = new Timer(1800, e -> owned.setVisible(true));
        reshowOwned.setRepeats(false);
        reshowOwned.start();
    }

    /** The busy-curtain idiom: show glass pane → work → hide glass pane. */
    private static void runBusyTask(JFrame frame) {
        JPanel busy = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        busy.add(new JLabel("Working… (input blocked)"));
        frame.setGlassPane(busy);
        frame.getGlassPane().setVisible(true);

        Timer done = new Timer(2000, e -> frame.getGlassPane().setVisible(false));
        done.setRepeats(false);
        done.start();
    }
}
