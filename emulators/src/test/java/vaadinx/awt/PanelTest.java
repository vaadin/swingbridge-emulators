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

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.event.ContainerEvent;
import vaadinx.awt.event.ContainerListener;
import vaadinx.swing.JComponent;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the {@code vaadinx.awt.Panel} emulator — AWT's generic container,
 * not {@code vaadinx.swing.JPanel}. The class itself is four lines over
 * {@code vaadinx.awt.Container}, so this suite pins the four things that are
 * genuinely Panel's (the default FlowLayout, the null-layout case, the R_leaf_peer_lockdown
 * Div lock, the kept addNotify) and one structural layout case per R_layouts_close_enough;
 * the inherited container surface is {@code ContainerTest}'s. See D_awt_panel / SD_no_spanel.
 */
class PanelTest extends AbstractKaribuTest {

    private Label leaf() {
        return new Label("x");
    }

    // --- The one real difference from Container: a default layout ------

    @Test
    @DisplayName("no-arg ctor installs a FlowLayout, as AWT does")
    void noArgCtorInstallsAFlowLayoutAsAwtDoes() {
        // JDK: public Panel() { this(new FlowLayout()); } — the only
        // functional difference between java.awt.Panel and java.awt.Container.
        Panel p = new Panel();
        assertInstanceOf(FlowLayout.class, p.getLayout(), "expected FlowLayout, got " + p.getLayout());
    }

    @Test
    @DisplayName("null layout installs nothing and is not substituted")
    void nullLayoutInstallsNothingAndIsNotSubstituted() {
        // JDK stores null verbatim; children then render in the peer div's
        // default block flow. Substituting a FlowLayout here would silently
        // change how a migrated Panel(null) lays out.
        assertNull(new Panel(null).getLayout());
    }

    @Test
    @DisplayName("ctor layout is stored as given")
    void ctorLayoutIsStoredAsGiven() {
        BorderLayout bl = new BorderLayout(8, 4);
        assertSame(bl, new Panel(bl).getLayout());
    }

    @Test
    @DisplayName("ctor reaches setLayout virtually, as the JDK's does")
    void ctorReachesSetLayoutVirtuallyAsTheJdksDoes() {
        // R_no_vaadin_in_api limb 2: the JDK's Panel(LayoutManager) calls setLayout, so a
        // user override must run during construction — an exposed hook the
        // internal path never reached would compile and silently never fire.
        List<LayoutManager> seen = new ArrayList<>();
        Panel p = new Panel(new GridLayout(2, 2)) {
            @Override
            public void setLayout(LayoutManager mgr) {
                seen.add(mgr);
                super.setLayout(mgr);
            }
        };
        assertEquals(1, seen.size(), "ctor did not route through setLayout");
        assertInstanceOf(GridLayout.class, seen.get(0));
        assertInstanceOf(GridLayout.class, p.getLayout());
    }

    // --- Peer / R_leaf_peer_lockdown ---------------------------------------------------

    @Test
    @DisplayName("every instance peers over a Div per R_leaf_peer_lockdown lock-down")
    void everyInstancePeersOverADivPerRLeafPeerLockdownLockDown() {
        assertInstanceOf(Div.class, new Panel().getPeer());
        // Including a user-code subclass — the commonest AWT-era idiom.
        // With no protected (peer) ctor there is no seam to swap the peer.
        Panel sub = new Panel() {
        };
        assertInstanceOf(Div.class, sub.getPeer());
        // The Div is also the element children land in, which is what makes
        // the layout CSS land where the children actually are.
        Panel p = new Panel();
        Label child = leaf();
        p.add(child);
        // assertEquals, not assertSame: Element is a per-call wrapper around
        // the state node, so two handles to the same element are equal but
        // never identical.
        assertEquals(p.peerContentElement(), child.getPeer().getElement().getParent());
    }

    // --- Layout CSS actually lands (R_layouts_close_enough structural correctness) --------

    @Test
    @DisplayName("default FlowLayout CSS lands on attach")
    void defaultFlowLayoutCssLandsOnAttach() {
        // addNotify → doLayout is what writes container CSS, so a Panel built
        // and added into a live tree lays itself out with no explicit validate.
        JFrame frame = new JFrame();
        Panel p = new Panel();
        p.add(leaf());
        frame.add(p);
        frame.setVisible(true);

        var style = p.getPeer().getElement().getStyle();
        assertEquals("flex", style.get("display"));
        assertEquals("wrap", style.get("flex-wrap"));
        assertEquals("5px 5px", style.get("gap"));
    }

    @Test
    @DisplayName("a null-layout Panel gets no layout CSS")
    void aNullLayoutPanelGetsNoLayoutCss() {
        JFrame frame = new JFrame();
        Panel p = new Panel(null);
        p.add(leaf());
        frame.add(p);
        frame.setVisible(true);

        assertNull(p.getPeer().getElement().getStyle().get("display"));
    }

    @Test
    @DisplayName("BorderLayout puts children in the right regions")
    void borderLayoutPutsChildrenInTheRightRegions() {
        // R_layouts_close_enough promises structural correctness (right children, right regions,
        // filling as the manager dictates) even though pixels are deferred.
        Panel p = new Panel(new BorderLayout(8, 4));
        Label north = leaf();
        Label center = leaf();
        p.add(north, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        p.validate();

        assertEquals("grid", p.getPeer().getElement().getStyle().get("display"));
        assertEquals("north", north.getPeer().getElement().getStyle().get("grid-area"));
        assertEquals("center", center.getPeer().getElement().getStyle().get("grid-area"));
    }

    // --- ContainerListener: real here, onNoop on a pure surrogate -----

    @Test
    @DisplayName("ContainerListener fires on add and remove with the container settled")
    void containerListenerFiresOnAddAndRemoveWithTheContainerSettled() {
        Panel p = new Panel();
        Label child = leaf();
        List<String> events = new ArrayList<>();
        p.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                events.add("added:" + (e.getChild() == child) + ":" + p.getComponentCount());
            }

            @Override
            public void componentRemoved(ContainerEvent e) {
                events.add("removed:" + (e.getChild() == child) + ":" + p.getComponentCount());
            }
        });
        p.add(child);
        p.remove(child);
        // Post-mutation state in both directions — the count already reflects
        // the add when componentAdded runs, and the removal when it is gone.
        assertEquals(List.of("added:true:1", "removed:true:0"), events);
    }

    // --- An all-AWT subtree, and the mirror case ----------------------

    @Test
    @DisplayName("an all-AWT subtree has no JComponent in its chain")
    void anAllAwtSubtreeHasNoJComponentInItsChain() {
        Panel p = new Panel(new BorderLayout());
        p.add(new Label("caption"), BorderLayout.NORTH);
        p.add(new Button("Go"), BorderLayout.SOUTH);

        assertEquals(2, p.getComponentCount());
        Container c = p;
        while (c != null) {
            assertFalse(c instanceof JComponent, "found a JComponent in an all-AWT subtree: " + c);
            c = c.getParent();
        }
    }

    @Test
    @DisplayName("an AWT Panel drops into a Swing container")
    void anAwtPanelDropsIntoASwingContainer() {
        JFrame frame = new JFrame();
        JPanel swing = new JPanel();
        Panel p = new Panel();
        p.add(new Label("Legacy"));
        swing.add(p);
        frame.add(swing);
        frame.setVisible(true);

        assertSame(swing, p.getParent());
        assertTrue(p.isDisplayable());
    }

    // --- Inherited surface / stubs ------------------------------------

    @Test
    @DisplayName("getInsets is zero and a fresh instance each call")
    void getInsetsIsZeroAndAFreshInstanceEachCall() {
        // AWT hands out a mutable Insets; callers that adjust it must not be
        // editing the container's own state.
        Panel p = new Panel();
        assertEquals(new Insets(0, 0, 0, 0), p.getInsets());
        assertNotSame(p.getInsets(), p.getInsets());
    }

    @Test
    @DisplayName("toString carries Container's layout tail")
    void toStringCarriesContainersLayoutTail() {
        // Container.paramString appends ",layout=<mgr>", and Component.toString
        // is its only root caller (D_awt_dead_hooks) — so the tail is observable here.
        Panel p = new Panel();
        assertTrue(p.toString().contains("layout="), p.toString());
    }

    @Test
    @DisplayName("addNotify chains to super and lays the subtree out")
    void addNotifyChainsToSuperAndLaysTheSubtreeOut() {
        List<String> calls = new ArrayList<>();
        JFrame frame = new JFrame();
        Panel p = new Panel() {
            @Override
            public void addNotify() {
                calls.add("addNotify");
                super.addNotify();
            }
        };
        p.add(leaf());
        frame.add(p);
        frame.setVisible(true);

        assertFalse(calls.isEmpty(), "addNotify was never reached on peer attach");
        assertEquals("flex", p.getPeer().getElement().getStyle().get("display"));
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        List<String> warned = new ArrayList<>();
        Consumer<String> prior = EHelper.warnHook;
        EHelper.warnHook = warned::add;
        try {
            assertNull(new Panel().getAccessibleContext());
        } finally {
            EHelper.warnHook = prior;
        }
        assertTrue(warned.stream().anyMatch(it -> it.contains("Panel") && it.contains("getAccessibleContext")));
    }

    @Test
    @DisplayName("construct-add-validate-remove is WARN-free")
    void constructAddValidateRemoveIsWarnFree() {
        List<String> warned = new ArrayList<>();
        Consumer<String> prior = EHelper.warnHook;
        EHelper.warnHook = warned::add;
        try {
            Panel p = new Panel(new BorderLayout(4, 4));
            Label child = leaf();
            p.add(child, BorderLayout.CENTER);
            p.validate();
            p.remove(child);
            p.removeAll();
            assertEquals(0, p.getComponentCount());
        } finally {
            EHelper.warnHook = prior;
        }
        assertEquals(List.of(), warned, "happy path should not WARN");
    }
}
