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


package com.vaadin.swingbridge.surrogates;

import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@link DefaultTreeModel} grown from a background thread (SD_background_model_hop), the
 * lazy-loading shape: the TreeGrid refresh a node insert causes, and the Grid selection a
 * selection-model write causes, land on the tree's UI thread when it has one and run under
 * its session's lock when it does not — synchronously either way, as Swing fires a model's
 * listeners before the mutator returns.
 *
 * <p>The observers are Vaadin's own listeners (the data provider's and the Grid's selection
 * listener), since the tree hands the Swing side no fan-out of its own.
 *
 * <p>Karibu's test thread holds the session lock for the whole test, so {@link #runOnWorker}
 * releases it around the join; the worker needs it to hop.
 */
class SJTreeBackgroundModelTest extends AbstractKaribuTest {

    /** What one Vaadin-listener call observed about the thread it ran on. */
    private record Seen(String what, Thread thread, UI ui, boolean locked) {}

    private static void runOnWorker(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "tree-loader");
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

    private static List<Seen> recordPeerFanOut(SJTree tree) {
        List<Seen> seen = new CopyOnWriteArrayList<>();
        tree.getDataProvider().addDataProviderListener(e -> seen.add(new Seen(
                "refresh", Thread.currentThread(), UI.getCurrent(), sessionLocked())));
        tree.addSelectionListener(e -> seen.add(new Seen(
                "select", Thread.currentThread(), UI.getCurrent(), sessionLocked())));
        return seen;
    }

    private static boolean sessionLocked() {
        VaadinSession s = VaadinSession.getCurrent();
        return s != null && s.hasLock();
    }

    @Test
    @DisplayName("attached: the worker's insert and select reach the Grid on the UI thread, before they return")
    void attachedTreeHopsToItsUI() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("a"));
        DefaultTreeModel model = new DefaultTreeModel(root);
        SJTree tree = new SJTree(model);
        UI ui = UI.getCurrent();
        ui.add(tree);
        List<Seen> seen = recordPeerFanOut(tree);
        DefaultMutableTreeNode loaded = new DefaultMutableTreeNode("loaded");
        AtomicReference<Integer> seenWhenInsertReturned = new AtomicReference<>();
        Thread[] worker = new Thread[1];

        runOnWorker(() -> {
            worker[0] = Thread.currentThread();
            model.insertNodeInto(loaded, root, 1);
            seenWhenInsertReturned.set(seen.size());
            tree.getTreeSelectionModel().setSelectionPath(new TreePath(new Object[]{root, loaded}));
        });

        assertEquals(1, seenWhenInsertReturned.get(), "insertNodeInto returned before the Grid refreshed");
        assertTrue(seen.stream().map(Seen::what).toList().contains("select"), "the selection never reached the Grid");
        for (Seen s : seen) {
            assertSame(worker[0], s.thread(), "accessSynchronously runs the body on the calling thread");
            assertSame(ui, s.ui());
            assertTrue(s.locked(), s.what() + " ran without the session lock");
        }
        assertEquals(3, GridKt._size(tree), "root, a and the loaded node");
        assertEquals(Set.of(loaded), tree.getSelectedItems());
    }

    @Test
    @DisplayName("detached but built in a session: the fan-out runs under that session's lock, with no UI current")
    void detachedTreeRunsUnderItsSessionLock() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultTreeModel model = new DefaultTreeModel(root);
        SJTree tree = new SJTree(model);
        List<Seen> seen = recordPeerFanOut(tree);
        DefaultMutableTreeNode loaded = new DefaultMutableTreeNode("loaded");

        runOnWorker(() -> {
            model.insertNodeInto(loaded, root, 0);
            tree.getTreeSelectionModel().setSelectionPath(new TreePath(new Object[]{root, loaded}));
        });

        assertTrue(seen.stream().map(Seen::what).toList().contains("select"), "the selection never reached the Grid");
        for (Seen s : seen) {
            assertNull(s.ui(), "no UI holds the tree, so none is made current");
            assertTrue(s.locked(), "the tree knows its session, so the hop takes its lock");
        }
        assertEquals(Set.of(loaded), tree.getSelectedItems());
    }
}
