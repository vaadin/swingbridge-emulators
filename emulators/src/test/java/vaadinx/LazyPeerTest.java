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
