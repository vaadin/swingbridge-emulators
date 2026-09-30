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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.SJTabbedPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EmulatorContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@code JTabbedPane} changed from a {@code doInBackground()}-shaped worker. The pane owns
 * its selection model and is that model's listener, as the JDK's is, so its
 * {@code ChangeListener}s hear a worker's change on the worker; the peer write hops onto the
 * UI thread (D_attach_aware_hop).
 */
class JTabbedPaneBackgroundModelTest extends AbstractKaribuTest {

    /** Runs {@code body} on a thread carrying this session's {@link EmulatorContext}, off the Karibu lock. */
    private static void runOnWorker(Runnable body) {
        EmulatorContext ctx = EmulatorContext.get();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> ctx.run(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "tabs-worker");
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
        if (t.isAlive()) fail("the worker never returned");
        if (failure.get() != null) throw new AssertionError("worker failed", failure.get());
    }

    @Test
    @DisplayName("constructed and filled on a worker, never attached: fires on the worker, as the JDK does")
    void detachedPaneBuiltOnWorker() {
        AtomicReference<JTabbedPane> built = new AtomicReference<>();
        List<Integer> seen = new CopyOnWriteArrayList<>();
        List<UI> uis = new CopyOnWriteArrayList<>();

        runOnWorker(() -> {
            JTabbedPane pane = new JTabbedPane();
            pane.addChangeListener(e -> {
                seen.add(pane.getSelectedIndex());
                uis.add(UI.getCurrent());
            });
            pane.addTab("A", new JPanel());
            pane.addTab("B", new JPanel());
            pane.setSelectedIndex(1);
            built.set(pane);
        });

        assertEquals(List.of(0, 1), seen);
        assertNull(uis.get(0));
        assertEquals(1, ((SJTabbedPane) built.get().getPeer()).getSelectedIndex());
    }

    @Test
    @DisplayName("attached, switched on a worker through the model: fires on the worker, the peer write still lands")
    void attachedPaneSwitchedThroughModel() {
        JTabbedPane pane = new JTabbedPane();
        UI.getCurrent().add(pane.getPeer());
        pane.addTab("A", new JPanel());
        pane.addTab("B", new JPanel());
        pane.addTab("C", new JPanel());
        List<Integer> seen = new CopyOnWriteArrayList<>();
        List<UI> uis = new CopyOnWriteArrayList<>();
        pane.addChangeListener(e -> {
            seen.add(pane.getSelectedIndex());
            uis.add(UI.getCurrent());
        });

        runOnWorker(() -> {
            pane.getModel().setSelectedIndex(2);
            pane.insertTab("D", null, new JPanel(), null, 0);
        });

        assertEquals(List.of(2, 3), seen);
        assertNull(uis.get(0), "the JDK fires model events on the mutating thread");
        assertNull(uis.get(1));
        assertEquals(3, ((SJTabbedPane) pane.getPeer()).getSelectedIndex());
        assertEquals(4, ((SJTabbedPane) pane.getPeer()).getTabCount());
    }
}
