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

import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.SJList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EmulatorContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.DefaultListModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@code JList} filled from a {@code doInBackground()}-shaped worker
 * (SD_background_model_hop). Shifting the selection across an insert is {@code BasicListUI}'s
 * job, which the emulator does on the thread the model fired on, as the JDK's UI does: the
 * list's {@code ListSelectionListener}s hear the shift on the worker, and the surrogate only
 * renders it, inside its hop.
 */
class JListBackgroundModelTest extends AbstractKaribuTest {

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
        }), "list-worker");
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

    /** Records the non-adjusting selection events: the selected index, and the UI current. */
    private static List<Integer> recordSelection(JList<String> list, List<UI> uis) {
        List<Integer> seen = new CopyOnWriteArrayList<>();
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            seen.add(list.getSelectedIndex());
            uis.add(UI.getCurrent());
        });
        return seen;
    }

    private static DefaultListModel<String> abc() {
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addAll(List.of("a", "b", "c"));
        return model;
    }

    @SuppressWarnings("unchecked")
    private static SJList<String> peerOf(JList<String> list) {
        return (SJList<String>) list.getPeer();
    }

    @Test
    @DisplayName("constructed and filled on a worker, never attached: fires on the worker, as the JDK does")
    void detachedListBuiltOnWorker() {
        AtomicReference<JList<String>> built = new AtomicReference<>();
        List<UI> uis = new CopyOnWriteArrayList<>();
        AtomicReference<List<Integer>> seen = new AtomicReference<>();

        runOnWorker(() -> {
            DefaultListModel<String> model = abc();
            JList<String> list = new JList<>(model);
            list.setSelectedIndex(2);
            seen.set(recordSelection(list, uis));
            model.remove(0);
            model.addElement("d");
            built.set(list);
        });

        assertEquals(List.of(1), seen.get());
        assertNull(uis.get(0));
        JList<String> list = built.get();
        assertEquals("c", list.getSelectedValue());
        assertEquals(3, GridKt._size(peerOf(list)));
        assertEquals(Set.of(1), peerOf(list).getSelectedItems());
    }

    @Test
    @DisplayName("attached, filled on a worker through the model: the selection shift fires on the UI thread")
    void attachedListFilledThroughModel() {
        DefaultListModel<String> model = abc();
        JList<String> list = new JList<>(model);
        UI ui = UI.getCurrent();
        ui.add(list.getPeer());
        list.setSelectedIndex(1);
        List<UI> uis = new CopyOnWriteArrayList<>();
        List<Integer> seen = recordSelection(list, uis);

        runOnWorker(() -> {
            model.add(0, "z");
            model.addElement("y");
        });

        assertEquals(List.of(2), seen);
        assertNull(uis.get(0), "the shift is the emulator's, on the worker, as the JDK's UI does");
        assertEquals("b", list.getSelectedValue());
        assertEquals(5, GridKt._size(peerOf(list)));
        assertEquals(Set.of(2), peerOf(list).getSelectedItems(), "the Grid follows the shifted selection");
    }
}
