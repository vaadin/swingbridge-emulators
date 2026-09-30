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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJPanel;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.FlowLayout;
import vaadinx.awt.LayoutManager;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JPanelTest extends AbstractKaribuTest {

    @Test
    @DisplayName("can instantiate with Div peer")
    void peerIsADiv() {
        JPanel p = new JPanel();
        // SJPanel IS-A Div per SD_sjpanel — Karibu locators that find by
        // Div.class still resolve.
        assertInstanceOf(Div.class, p.getPeer());
    }

    @Test
    @DisplayName("peer is an SJPanel surrogate")
    void peerIsAnSJPanel() {
        JPanel p = new JPanel();
        assertInstanceOf(SJPanel.class, p.getPeer());
    }

    @Test
    @DisplayName("inherited surrogate FlowLayout dispatch writes display flex on the SJPanel host")
    void surrogateFlowLayoutCssIsOnTheHost() {
        // SJPanel's own ctor calls its inherited mixin setLayout(new
        // FlowLayout()) which writes display:flex on the host element
        // via LayoutCss.flowLayoutCss. Since the emulator's JPanel uses
        // SJPanel as peer, this CSS is observable through peer.element
        // pre-validate. Sanity-check that the thin shell picks up the
        // surrogate's CSS dispatch even before the emulator's own
        // validate-time CSS writes kick in.
        JPanel p = new JPanel();
        assertEquals("flex", p.getPeer().getElement().getStyle().get("display"));
        assertEquals("wrap", p.getPeer().getElement().getStyle().get("flex-wrap"));
    }

    @Test
    @DisplayName("no-arg ctor installs FlowLayout as default layout")
    void noArgCtorInstallsFlowLayout() {
        // Real Swing JPanel sets FlowLayout as its default — migrated
        // code that does `new JPanel().add(x).add(y)` expects flex-wrap
        // flow, not raw block stacking.
        assertInstanceOf(FlowLayout.class, new JPanel().getLayout());
    }

    @Test
    @DisplayName("LayoutManager-taking ctor uses the supplied manager")
    void layoutManagerCtorUsesSuppliedManager() {
        FlowLayout fl = new FlowLayout(FlowLayout.RIGHT, 10, 2);
        JPanel p = new JPanel(fl);
        assertSame(fl, p.getLayout());
    }

    @Test
    @DisplayName("null LayoutManager ctor disables the default flow")
    void nullLayoutManagerCtorDisablesDefaultFlow() {
        // Real Swing JPanel(null) still respects the user's explicit
        // null — no layout means children render under the peer's own
        // CSS (browser block-flow for a Div). The cast disambiguates
        // from the peer-taking ctor.
        JPanel p = new JPanel((LayoutManager) null);
        assertNull(p.getLayout());
    }

    @Test
    @DisplayName("opaque defaults to true — Swing flips JComponent's false")
    void opaqueDefaultsToTrue() {
        // JComponent's default is opaque=false (transparent); JPanel
        // bumps to true in its ctor via setUIProperty. Field round-trip
        // is the honest observable even though CSS backgrounds aren't
        // rendered from this flag (R_layouts_close_enough).
        assertTrue(new JPanel().isOpaque());
    }

    @Test
    @DisplayName("getUIClassID is PanelUI for UIManager compatibility")
    void uiClassIdIsPanelUI() {
        assertEquals("PanelUI", new JPanel().getUIClassID());
    }

    @Test
    @DisplayName("add routes a JButton into the panel's peer Div")
    void addRoutesButtonIntoPeerDiv() {
        JPanel p = new JPanel();
        JButton btn = new JButton("click");

        p.add(btn);

        assertSame(p, btn.getParent());
        assertEquals(btn.getPeer().getElement(), p.getPeer().getElement().getChild(0));
    }

    @Test
    @DisplayName("FlowLayout CSS lands on the peer Div after frame setVisible validate pass")
    void flowLayoutCssLandsAfterValidate() {
        // The end-to-end goal: a JPanel with default FlowLayout,
        // added to a JFrame, gets `display: flex` on its Div peer once the
        // frame's setVisible(true) runs its implicit validate(). If this
        // passes, the D_layout_css_on_content dispatch path is live.
        JPanel panel = new JPanel();
        panel.add(new JButton("one"));
        panel.add(new JButton("two"));

        JFrame frame = new JFrame("panel smoke");
        frame.add(panel);
        frame.setVisible(true);

        Style style = panel.getPeer().getElement().getStyle();
        assertEquals("flex", style.get("display"));
        assertEquals("wrap", style.get("flex-wrap"));
        assertEquals("center", style.get("justify-content"));
        // Two Vaadin Buttons reachable from the UI tree — the panel is
        // really hosting them, not just holding references.
        LocatorJ._assert(Button.class, 2);
    }

    @Test
    @DisplayName("setPreferredSize with zero axis clears that axis instead of collapsing it")
    void preferredSizeZeroAxisClearsThatAxis() {
        // The common Swing idiom
        //   c.setPreferredSize(new Dimension(W, c.getPreferredSize().height))
        // pulls through getPreferredSize().height as 0 on this emulator
        // (no font measurement). Writing a hard `height:0px` would
        // collapse the element; the migrator's intent is "lock width,
        // leave height to the layout". So 0/negative on an axis clears
        // the CSS slot for that axis. A positive value writes through as the
        // fallback of its D_layout_owns_child_sizing variable, so a parent
        // layout that owns the axis can still override it.
        JPanel p = new JPanel();
        Style style = p.getPeer().getElement().getStyle();
        p.setPreferredSize(new Dimension(110, 0));
        assertEquals("var(--emul-layout-w, 110px)", style.get("width"));
        assertNull(style.get("height"));

        p.setPreferredSize(new Dimension(0, 20));
        assertNull(style.get("width"));
        assertEquals("var(--emul-layout-h, 20px)", style.get("height"));

        p.setPreferredSize(new Dimension(120, 24));
        assertEquals("var(--emul-layout-w, 120px)", style.get("width"));
        assertEquals("var(--emul-layout-h, 24px)", style.get("height"));

        p.setPreferredSize(null);
        assertNull(style.get("width"));
        assertNull(style.get("height"));
    }
}
