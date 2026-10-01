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

package vaadinx;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.SJLabel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.swing.ImageIcon;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import javax.swing.SwingConstants;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A lazy emulator builds its Vaadin peer only once a UI is current: an emulator constructed and
 * configured on a worker runs no Vaadin code there, and its writes reach the peer when it is
 * attached (ideas/vaadin-ui-thread-only.md § "The mechanism").
 */
class LazyPeerTest extends AbstractKaribuTest {

    /** Runs {@code body} on a thread with no UI and no context, off the Karibu lock. */
    private static void onBareThread(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "lazy-peer");
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        session.unlock();
        try {
            t.start();
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
        if (t.isAlive()) fail("the thread did not finish");
        if (failure.get() != null) throw new AssertionError("the thread failed", failure.get());
    }

    @Test
    @DisplayName("a JLabel configured on a worker builds no Vaadin component there, and renders once attached")
    void jLabelBuiltOnWorker() {
        int builtOffThreadBefore = EHelper.peersBuiltOffUIThread();
        AtomicReference<JLabel> built = new AtomicReference<>();

        onBareThread(() -> {
            JLabel label = new JLabel("Name", new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)),
                    SwingConstants.RIGHT);
            label.setForeground(Color.RED);
            label.setToolTipText("tip");
            label.setText("Full name");
            built.set(label);
        });

        assertEquals(builtOffThreadBefore, EHelper.peersBuiltOffUIThread(),
                "no peer was reached on the worker, so none was built there");
        JLabel label = built.get();
        assertEquals("Full name", label.getText(), "the emulator answers from its own state meanwhile");

        UI.getCurrent().add(label.getPeer());
        SJLabel peer = assertInstanceOf(SJLabel.class, label.getPeer());
        assertEquals("Full name", peer.getText(), "the queued writes reached the peer, in order");
        assertInstanceOf(Image.class, peer.getIcon(), "and the icon became an Image, with a UI current");
    }

    /** Every lazy emulator, constructed and configured the way a worker typically leaves one. */
    private static java.util.Map<String, java.util.function.Supplier<? extends vaadinx.awt.Component>> lazyEmulators() {
        java.util.Map<String, java.util.function.Supplier<? extends vaadinx.awt.Component>> m = new java.util.LinkedHashMap<>();
        m.put("JLabel", () -> new JLabel("x"));
        m.put("JPanel", JPanel::new);
        m.put("JPanel(BorderLayout)", () -> {
            JPanel p = new JPanel(new vaadinx.awt.BorderLayout());
            p.add(new JLabel("north"), vaadinx.awt.BorderLayout.NORTH);
            return p;
        });
        m.put("JSeparator", vaadinx.swing.JSeparator::new);
        m.put("JScrollBar", vaadinx.swing.JScrollBar::new);
        m.put("JViewport", vaadinx.swing.JViewport::new);
        m.put("JLayeredPane", vaadinx.swing.JLayeredPane::new);
        m.put("JTableHeader", vaadinx.swing.table.JTableHeader::new);
        m.put("Box", () -> {
            vaadinx.swing.Box box = vaadinx.swing.Box.createHorizontalBox();
            box.add(vaadinx.swing.Box.createHorizontalStrut(5));
            box.add(new JLabel("after the strut"));
            return box;
        });
        m.put("AWT Label", () -> new vaadinx.awt.Label("x", vaadinx.awt.Label.RIGHT));
        m.put("AWT Button", () -> {
            vaadinx.awt.Button b = new vaadinx.awt.Button("go");
            b.setActionCommand("cmd");
            return b;
        });
        m.put("AWT Checkbox", () -> new vaadinx.awt.Checkbox("check", true, new vaadinx.awt.CheckboxGroup()));
        m.put("AWT Choice", () -> {
            vaadinx.awt.Choice c = new vaadinx.awt.Choice();
            c.add("one");
            c.add("two");
            c.select(1);
            return c;
        });
        m.put("AWT List", () -> {
            vaadinx.awt.List l = new vaadinx.awt.List(3, true);
            l.add("one");
            l.add("two");
            l.select(0);
            return l;
        });
        m.put("AWT Scrollbar", () -> {
            vaadinx.awt.Scrollbar s = new vaadinx.awt.Scrollbar(vaadinx.awt.Scrollbar.HORIZONTAL, 10, 5, 0, 100);
            s.setValue(20);
            return s;
        });
        m.put("AWT Panel", () -> {
            vaadinx.awt.Panel p = new vaadinx.awt.Panel();
            p.add(new vaadinx.awt.Label("inside"));
            return p;
        });
        m.put("JButton", () -> {
            vaadinx.swing.JButton b = new vaadinx.swing.JButton("go");
            b.setActionCommand("cmd");
            b.setMnemonic('G');
            b.doClick();
            return b;
        });
        m.put("JToggleButton", () -> new vaadinx.swing.JToggleButton("t", true));
        m.put("JCheckBox", () -> {
            vaadinx.swing.JCheckBox c = new vaadinx.swing.JCheckBox("c");
            c.doClick();
            return c;
        });
        m.put("JRadioButton", () -> {
            vaadinx.swing.ButtonGroup g = new vaadinx.swing.ButtonGroup();
            vaadinx.swing.JRadioButton r = new vaadinx.swing.JRadioButton("r", true);
            g.add(r);
            g.add(new vaadinx.swing.JRadioButton("other"));
            return r;
        });
        m.put("JMenuItem", () -> new vaadinx.swing.JMenuItem("item"));
        m.put("JCheckBoxMenuItem", () -> new vaadinx.swing.JCheckBoxMenuItem("check", true));
        m.put("JRadioButtonMenuItem", () -> new vaadinx.swing.JRadioButtonMenuItem("radio", true));
        m.put("AWT ScrollPane", () -> {
            vaadinx.awt.ScrollPane sp = new vaadinx.awt.ScrollPane(vaadinx.awt.ScrollPane.SCROLLBARS_NEVER);
            sp.add(new vaadinx.awt.Label("first"));
            sp.setScrollPosition(0, 40);
            sp.add(new vaadinx.awt.Label("replaces the first"));
            return sp;
        });
        return m;
    }

    @org.junit.jupiter.api.TestFactory
    @DisplayName("a lazy emulator configured on a worker builds no Vaadin component there")
    java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> builtOnWorker() {
        return lazyEmulators().entrySet().stream().map(e -> org.junit.jupiter.api.DynamicTest.dynamicTest(
                e.getKey(), () -> assertBuiltOnWorkerWithoutVaadin(e.getKey(), e.getValue())));
    }

    private static void assertBuiltOnWorkerWithoutVaadin(String name,
            java.util.function.Supplier<? extends vaadinx.awt.Component> factory) {
        int builtOffThreadBefore = EHelper.peersBuiltOffUIThread();
        AtomicReference<vaadinx.awt.Component> built = new AtomicReference<>();

        onBareThread(() -> {
            vaadinx.awt.Component c = factory.get();
            c.setName("named");
            c.setEnabled(false);
            c.setBackground(Color.YELLOW);
            if (c instanceof vaadinx.swing.JComponent jc) jc.setToolTipText("tip");
            built.set(c);
        });

        assertEquals(builtOffThreadBefore, EHelper.peersBuiltOffUIThread(),
                name + " reached its peer on the worker; the WARN's stack names the reach");
        UI.getCurrent().add(built.get().getPeer());
        assertEquals(true, built.get().getPeer().isAttached(), "and it renders once attached");
    }

    @Test
    @DisplayName("a JCheckBox selected on a worker shows checked once attached, and a click reaches its listeners")
    void jCheckBoxSelectedOnWorker() {
        AtomicReference<vaadinx.swing.JCheckBox> built = new AtomicReference<>();
        onBareThread(() -> {
            vaadinx.swing.JCheckBox c = new vaadinx.swing.JCheckBox("c");
            c.setSelected(true);
            built.set(c);
        });

        vaadinx.swing.JCheckBox box = built.get();
        java.util.List<String> events = new java.util.ArrayList<>();
        box.addItemListener(e -> events.add("item " + e.getStateChange()));
        box.addActionListener(e -> events.add("action " + e.getActionCommand()));
        UI.getCurrent().add(box.getPeer());
        com.vaadin.swingbridge.surrogates.SJCheckBox peer =
                assertInstanceOf(com.vaadin.swingbridge.surrogates.SJCheckBox.class, box.getPeer());
        assertEquals(true, peer.getValue(), "the surrogate renders the emulator's model");

        com.github.mvysny.kaributesting.v10.LocatorJ._setValue(peer, false);
        assertEquals(false, box.isSelected());
        assertEquals(java.util.List.of("item " + java.awt.event.ItemEvent.DESELECTED, "action c"), events);
    }

    @Test
    @DisplayName("an AWT ScrollPane whose child was replaced on a worker shows the second child once attached")
    void scrollPaneChildSwapOnWorker() {
        AtomicReference<vaadinx.awt.ScrollPane> built = new AtomicReference<>();
        AtomicReference<vaadinx.awt.Label> second = new AtomicReference<>();

        onBareThread(() -> {
            vaadinx.awt.ScrollPane sp = new vaadinx.awt.ScrollPane();
            sp.add(new vaadinx.awt.Label("first"));
            second.set(new vaadinx.awt.Label("second"));
            sp.add(second.get());
            built.set(sp);
        });

        UI.getCurrent().add(built.get().getPeer());
        com.vaadin.swingbridge.surrogates.SScrollPane peer =
                assertInstanceOf(com.vaadin.swingbridge.surrogates.SScrollPane.class, built.get().getPeer());
        assertEquals(second.get().getPeer(), peer.getContent(), "the queued content swaps drained in order");
    }

    @Test
    @DisplayName("a label added to a panel on a worker is built when the panel's add runs with a UI")
    void jLabelInPanelBuiltOnWorker() {
        AtomicReference<JPanel> built = new AtomicReference<>();

        onBareThread(() -> {
            JPanel panel = new JPanel();
            panel.add(new JLabel("inside"));
            built.set(panel);
        });

        UI.getCurrent().add(built.get().getPeer());
        JLabel label = (JLabel) built.get().getComponent(0);
        SJLabel peer = assertInstanceOf(SJLabel.class, label.getPeer());
        assertEquals(built.get().getPeer(), peer.getParent().orElseThrow(), "the add put the label's peer in the panel's");
        assertEquals("inside", peer.getText());
    }
}
