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

package vaadinx.awt;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.event.HierarchyEvent;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Displayability below the top-level Window — {@code Q_displayable_for_components},
 * the follow-on to {@code D_window_displayable}, which gave the bit to
 * {@link Window} alone and left every other component reading Vaadin attachment.
 *
 * <p>Every expectation here was measured against JDK 25 on a real display before
 * being written, because the two candidate designs — a bit maintained by AWT's
 * cascade, or an answer derived by walking to the top-level ancestor — agree on
 * every <em>getter</em> and differ only in what they notify. The walk is the one
 * that notifies nothing, which is why these tests lean on call order and on
 * {@code DISPLAYABILITY_CHANGED} rather than on {@code isDisplayable()} alone.
 */
public class ComponentDisplayableTest extends AbstractKaribuTest {

    /** Records the realise/unrealise calls and hierarchy events it receives. */
    static class Traced extends JPanel {
        final String id;
        final List<String> log;

        Traced(String id, List<String> log) {
            this.id = id;
            this.log = log;
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                    log.add("displayability:" + id + "=" + isDisplayable()
                            + ":changed=" + ((Traced) e.getChanged()).id);
                }
            });
        }

        @Override public void addNotify() {
            log.add("add>" + id);
            super.addNotify();
            log.add("add<" + id);
        }

        @Override public void removeNotify() {
            log.add("remove>" + id);
            super.removeNotify();
            log.add("remove<" + id);
        }
    }

    /** A frame with a `mid` panel holding a `leaf` panel, assembled but not realised. */
    private record Tree(JFrame frame, Traced mid, Traced leaf, List<String> log) {}

    private Tree tree() {
        List<String> log = new ArrayList<>();
        JFrame frame = new JFrame("t");
        Traced mid = new Traced("mid", log);
        Traced leaf = new Traced("leaf", log);
        mid.add(leaf);
        frame.getContentPane().add(mid, BorderLayout.CENTER);
        log.clear();  // drop the PARENT_CHANGED noise from assembly
        return new Tree(frame, mid, leaf, log);
    }

    // --- the states -----------------------------------------------------

    @Test
    public void assembledButUnrealisedTreeIsUndisplayable() {
        Tree t = tree();
        assertFalse(t.frame.isDisplayable());
        assertFalse(t.frame.getContentPane().isDisplayable());
        assertFalse(t.mid.isDisplayable());
        assertFalse(t.leaf.isDisplayable());
    }

    @Test
    public void packRealisesTheWholeSubtreeNotJustTheWindow() {
        // The measurement that killed the derived-walk design's simpler
        // sibling: pack() is where a never-shown window's children become
        // displayable, with no DOM anywhere. Vaadin attachment cannot express
        // this state at all.
        Tree t = tree();
        t.frame.pack();
        assertTrue(t.frame.isDisplayable());
        assertTrue(t.frame.getContentPane().isDisplayable());
        assertTrue(t.mid.isDisplayable(), "pack() realises descendants, not only the window");
        assertTrue(t.leaf.isDisplayable());
    }

    @Test
    public void anOrphanTreeIsUndisplayableEvenWithChildren() {
        Traced orphan = new Traced("orphan", new ArrayList<>());
        Traced kid = new Traced("kid", new ArrayList<>());
        orphan.add(kid);
        assertFalse(orphan.isDisplayable());
        assertFalse(kid.isDisplayable());
    }

    @Test
    public void hidingTheWindowLeavesDescendantsDisplayable() {
        // AWT's rule for the Window, now inherited by everything under it:
        // hiding does not undisplayable.
        Tree t = tree();
        t.frame.setVisible(true);
        t.frame.setVisible(false);
        assertTrue(t.mid.isDisplayable());
        assertTrue(t.leaf.isDisplayable());
    }

    @Test
    public void disposeUnrealisesTheWholeSubtree() {
        Tree t = tree();
        t.frame.setVisible(true);
        t.frame.dispose();
        assertFalse(t.mid.isDisplayable());
        assertFalse(t.leaf.isDisplayable());
    }

    // --- the cascade, which is the half a derived walk cannot supply -----

    @Test
    public void packCascadesAddNotifyTopDown() {
        // JDK 25: add>mid, add>leaf, add<leaf, add<mid. A parent is realised
        // before its children are asked, so a child's addNotify already sees
        // its parent displayable.
        Tree t = tree();
        t.frame.pack();
        assertEquals(List.of(
                "add>mid", "displayability:mid=true:changed=mid",
                "add>leaf", "displayability:leaf=true:changed=leaf",
                "add<leaf", "add<mid"),
                t.log);
    }

    @Test
    public void disposeEntersTopDownAndFlipsTheFlagBottomUp() {
        // JDK 25: remove>mid, remove>leaf, leaf flips, remove<leaf, mid flips,
        // remove<mid — Container unrealises its children *before* chaining to
        // super, so entry order and flag order are opposites.
        Tree t = tree();
        t.frame.pack();
        t.log.clear();
        t.frame.dispose();
        assertEquals(List.of(
                "remove>mid", "remove>leaf",
                "displayability:leaf=false:changed=leaf", "remove<leaf",
                "displayability:mid=false:changed=mid", "remove<mid"),
                t.log);
    }

    @Test
    public void eachComponentFiresItsOwnDisplayabilityEventNotTheAncestorsS() {
        // DISPLAYABILITY_CHANGED does not recurse the way PARENT_CHANGED does:
        // the cascade already visits every descendant, and each event's
        // `changed` is the component itself. Recursing would deliver one event
        // per ancestor per descendant.
        Tree t = tree();
        t.frame.pack();
        assertEquals(2, t.log.stream().filter(s -> s.startsWith("displayability:")).count());
        assertTrue(t.log.contains("displayability:leaf=true:changed=leaf"));
    }

    @Test
    public void addToADisplayableContainerRealisesImmediatelyAndRemoveUnrealises() {
        Tree t = tree();
        t.frame.pack();
        Traced late = new Traced("late", t.log);
        t.log.clear();

        t.mid.add(late);
        assertTrue(late.isDisplayable());
        assertEquals(List.of("add>late", "displayability:late=true:changed=late", "add<late"), t.log);

        t.log.clear();
        t.mid.remove(late);
        assertFalse(late.isDisplayable());
        assertEquals(List.of("remove>late", "displayability:late=false:changed=late", "remove<late"), t.log);
    }

    @Test
    public void removingASubtreeUnrealisesItsDescendantsToo() {
        Tree t = tree();
        t.frame.pack();
        t.frame.getContentPane().remove(t.mid);
        assertFalse(t.mid.isDisplayable());
        assertFalse(t.leaf.isDisplayable(), "the whole detached subtree goes undisplayable");
    }

    // --- isShowing is derived, never stored ------------------------------

    @Test
    public void hidingAnIntermediateContainerLeavesTheChildsVisibleFlagAlone() {
        // AWT never writes a descendant's `visible` field — measured on JDK 25.
        // Only the derived isShowing() changes, which is why it has to walk.
        Tree t = tree();
        t.frame.setVisible(true);
        assertTrue(t.leaf.isShowing());

        t.mid.setVisible(false);
        assertTrue(t.leaf.isVisible(), "the child's own flag is untouched");
        assertFalse(t.leaf.isShowing(), "but it is not showing through a hidden ancestor");

        t.mid.setVisible(true);
        assertTrue(t.leaf.isShowing());
    }

    @Test
    public void hidingTheWindowStopsDescendantsShowingWithoutTouchingTheirFlags() {
        Tree t = tree();
        t.frame.setVisible(true);
        t.frame.setVisible(false);
        assertTrue(t.leaf.isVisible());
        assertFalse(t.leaf.isShowing());
    }

    // --- mixed mode: an emulator under a vanilla Vaadin parent -----------

    @Test
    public void aRootlessEmulatorAttachedToAVaadinLayoutRealisesItself() {
        // The mixed shape: no Swing ancestor to inherit displayability from, so
        // the peer entering the live UI is the only realisation signal there
        // is. Same rule that already realises a @MainWindow JFrame from a
        // route, generalised past Window.
        List<String> log = new ArrayList<>();
        Traced root = new Traced("root", log);
        Traced kid = new Traced("kid", log);
        root.add(kid);
        log.clear();

        UI.getCurrent().getElement().appendChild(root.getPeer().getElement());

        assertTrue(root.isDisplayable());
        assertTrue(kid.isDisplayable(), "the cascade runs from the mixed root too");
        assertTrue(root.isShowing());
        assertEquals(List.of("add>root", "displayability:root=true:changed=root",
                "add>kid", "displayability:kid=true:changed=kid",
                "add<kid", "add<root"),
                log);
    }

    @Test
    public void detachingAMixedRootUnrealisesItsSubtree() {
        Traced root = new Traced("root", new ArrayList<>());
        Traced kid = new Traced("kid", new ArrayList<>());
        root.add(kid);
        UI.getCurrent().getElement().appendChild(root.getPeer().getElement());

        root.getPeer().getElement().removeFromParent();

        assertFalse(root.isDisplayable());
        assertFalse(kid.isDisplayable());
    }

    @Test
    public void aNonRootDoesNotRealiseItselfFromItsOwnVaadinAttach() {
        // Everything below a realisation root is realised by the cascade. If a
        // child took its own attach event as a realisation signal, the order
        // would be Vaadin's bottom-up one and pack() could not work at all.
        Tree t = tree();
        t.frame.setVisible(true);
        t.log.clear();
        // The subtree is already attached; re-attaching the leaf's peer must
        // not produce a second addNotify.
        t.leaf.getPeer().getElement().removeFromParent();
        t.mid.getPeer().getElement().appendChild(t.leaf.getPeer().getElement());
        assertEquals(List.of(), t.log);
    }

    @Test
    public void aRealisedMixedRootAdoptedIntoASwingContainerIsRealisedOnlyOnce() {
        // SB-Emulators-only state — AWT cannot be displayable without a parent unless it
        // is a Window — so addImpl unrealises first, mirroring what a reparent
        // from a real parent would have done.
        List<String> log = new ArrayList<>();
        Traced root = new Traced("root", log);
        UI.getCurrent().getElement().appendChild(root.getPeer().getElement());
        assertTrue(root.isDisplayable());

        Tree t = tree();
        t.frame.pack();
        log.clear();

        t.mid.add(root);

        assertTrue(root.isDisplayable());
        assertEquals(List.of("remove>root", "displayability:root=false:changed=root", "remove<root",
                "add>root", "displayability:root=true:changed=root", "add<root"),
                log);
    }
}
