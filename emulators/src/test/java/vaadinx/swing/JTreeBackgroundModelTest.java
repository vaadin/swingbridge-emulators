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
import com.vaadin.swingbridge.surrogates.SJTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EmulatorContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@code JTree} whose model a {@code doInBackground()}-shaped worker grows and prunes
 * (SD_background_model_hop). The tree's own {@code TreeModelHandler} runs on the worker, as
 * the JDK's does, so a removed node leaves the selection there and the migrator's
 * {@code TreeSelectionListener} hears it on the worker; the surrogate hops the TreeGrid refresh.
 */
class JTreeBackgroundModelTest extends AbstractKaribuTest {

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
        }), "tree-worker");
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

    /** root → {a, b}, with the root expanded (the JDK default for a visible root). */
    private record Fixture(DefaultMutableTreeNode root, DefaultMutableTreeNode a,
                           DefaultMutableTreeNode b, DefaultTreeModel model) {
        static Fixture create() {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
            DefaultMutableTreeNode a = new DefaultMutableTreeNode("a");
            DefaultMutableTreeNode b = new DefaultMutableTreeNode("b");
            root.add(a);
            root.add(b);
            return new Fixture(root, a, b, new DefaultTreeModel(root));
        }

        TreePath pathTo(DefaultMutableTreeNode node) {
            return new TreePath(node.getPath());
        }
    }

    /** Records the selection events: the selection path after each, and the UI current. */
    private static List<TreePath> recordSelection(JTree tree, List<UI> uis) {
        List<TreePath> seen = new CopyOnWriteArrayList<>();
        tree.addTreeSelectionListener(e -> {
            seen.add(tree.getSelectionPath());
            uis.add(UI.getCurrent());
        });
        return seen;
    }

    private static SJTree peerOf(JTree tree) {
        return (SJTree) tree.getPeer();
    }

    @Test
    @DisplayName("constructed and pruned on a worker, never attached: fires on the worker, as the JDK does")
    void detachedTreeBuiltOnWorker() {
        AtomicReference<JTree> built = new AtomicReference<>();
        List<UI> uis = new CopyOnWriteArrayList<>();
        AtomicReference<List<TreePath>> seen = new AtomicReference<>();

        runOnWorker(() -> {
            Fixture f = Fixture.create();
            JTree tree = new JTree(f.model());
            tree.setSelectionPath(f.pathTo(f.b()));
            seen.set(recordSelection(tree, uis));
            f.model().removeNodeFromParent(f.b());
            built.set(tree);
        });

        assertEquals(1, seen.get().size());
        assertNull(seen.get().get(0), "the removed node leaves the selection");
        assertNull(uis.get(0));
        assertNull(built.get().getSelectionPath());
        assertEquals(2, GridKt._size(peerOf(built.get())), "root and a");
        assertTrue(peerOf(built.get()).getSelectedItems().isEmpty());
    }

    @Test
    @DisplayName("attached, grown and pruned on a worker through the model: fires on the worker, the TreeGrid still follows")
    void attachedTreeChangedThroughModel() {
        Fixture f = Fixture.create();
        JTree tree = new JTree(f.model());
        UI.getCurrent().add(tree.getPeer());
        tree.setSelectionPath(f.pathTo(f.b()));
        List<UI> uis = new CopyOnWriteArrayList<>();
        List<TreePath> seen = recordSelection(tree, uis);
        DefaultMutableTreeNode loaded = new DefaultMutableTreeNode("loaded");

        runOnWorker(() -> {
            f.model().insertNodeInto(loaded, f.a(), 0);
            f.model().removeNodeFromParent(f.b());
        });

        assertEquals(1, seen.size());
        assertNull(seen.get(0), "the removed node leaves the selection");
        assertNull(uis.get(0), "the JDK fires model events on the mutating thread");
        assertNull(tree.getSelectionPath());
        assertTrue(peerOf(tree).getSelectedItems().isEmpty(), "the Grid follows the cleared selection");
        assertEquals(2, GridKt._size(peerOf(tree)), "root and a, still collapsed");
        tree.expandPath(f.pathTo(f.a()));
        assertEquals(3, GridKt._size(peerOf(tree)), "the worker's node is under a");
        assertEquals(Set.of(), peerOf(tree).getSelectedItems());
    }
}
