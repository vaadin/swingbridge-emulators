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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjpanel's SJPanel surrogate. Covers:
 *
 * <ol>
 *  <li>Constructors — no-arg defaults to FlowLayout; boolean / layout /
 *      layout+boolean variants funnel through the two-arg form.
 *  <li>UIClassID is "PanelUI"; setUI WARNs.
 *  <li>R_vaadin_first lossy isOpaque() — fresh panel reports false (no shadow store);
 *      setOpaque(true) + setBackground(red) round-trips to true.
 *  <li>setLayout dispatches via inherited mixin (FlowLayout / BorderLayout
 *      CSS lands on host element); custom LayoutManager stores but
 *      doesn't dispatch (no CSS).
 *  <li>setBorder via inherited mixin round-trips.
 *  <li>add/remove children via inherited HasComponents.
 *  <li>Happy-path zero stub WARNs.
 * </ol>
 */
class SJPanelTest extends AbstractKaribuTest {

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("no-arg ctor installs default FlowLayout")
    void noArgCtorInstallsDefaultFlowLayout() {
        SJPanel p = new SJPanel();
        assertInstanceOf(FlowLayout.class, p.getLayout());
    }

    @Test
    @DisplayName("no-arg ctor writes inline-flex CSS via inherited mixin dispatch")
    void noArgCtorWritesInlineFlexCssViaInheritedMixinDispatch() {
        // ContainerMixin.setLayout dispatches to
        // LayoutCss.flowLayoutCss which writes display:flex / flex-wrap:wrap /
        // justify-content / gap on the host. Sanity-check that the inherited
        // dispatch reaches SJPanel's <div> host.
        SJPanel p = new SJPanel();
        Style style = p.getElement().getStyle();
        assertEquals("flex", style.get("display"));
        assertEquals("wrap", style.get("flex-wrap"));
    }

    @Test
    @DisplayName("boolean ctor accepts and drops isDoubleBuffered")
    void booleanCtorAcceptsAndDropsIsDoubleBuffered() {
        // Per R_layouts_close_enough, no Vaadin counterpart for AWT's double-buffer flag.
        // Both true and false constructors land identical output.
        SJPanel pt = new SJPanel(true);
        SJPanel pf = new SJPanel(false);
        assertInstanceOf(FlowLayout.class, pt.getLayout());
        assertInstanceOf(FlowLayout.class, pf.getLayout());
    }

    @Test
    @DisplayName("layout ctor stores given layout")
    void layoutCtorStoresGivenLayout() {
        BorderLayout bl = new BorderLayout(5, 7);
        SJPanel p = new SJPanel(bl);
        assertSame(bl, p.getLayout());
    }

    @Test
    @DisplayName("layout + boolean ctor stores given layout")
    void layoutPlusBooleanCtorStoresGivenLayout() {
        BorderLayout bl = new BorderLayout();
        SJPanel p = new SJPanel(bl, false);
        assertSame(bl, p.getLayout());
    }

    @Test
    @DisplayName("peer is a Vaadin Div")
    void peerIsAVaadinDiv() {
        // SJPanel IS-A Div per SD_sjpanel — locator-by-Div paths still resolve.
        assertInstanceOf(Div.class, new SJPanel());
    }

    // --- L&F surface -------------------------------------------------

    @Test
    @DisplayName("getUIClassID is PanelUI")
    void getUiClassIdIsPanelUi() {
        assertEquals("PanelUI", new SJPanel().getUIClassID());
    }

    @Test
    @DisplayName("setUI WARNs and drops")
    void setUiWarnsAndDrops() {
        SJPanel p = new SJPanel();
        p.setUI(null);  // any value triggers the WARN per the surrogate's stance
        assertEquals(1, capturedWarns.size());
    }

    // --- R_vaadin_first lossy opaque ---------------------------------------------

    @Test
    @DisplayName("fresh SJPanel reports isOpaque false per R_vaadin_first lossy direction")
    void freshSjPanelReportsIsOpaqueFalse() {
        // Documented divergence — JDK fresh JPanel reports true (L&F
        // installs opaque=true at construction); surrogate reads CSS
        // background-color which is unset → false. R_vaadin_first-accepted lossy
        // direction; emulator JPanel preserves JDK true via field shadow.
        assertFalse(new SJPanel().isOpaque());
    }

    @Test
    @DisplayName("setOpaque(true) plus setBackground rounds-trip isOpaque to true")
    void setOpaqueTruePlusSetBackgroundRoundTripsIsOpaqueToTrue() {
        // The mixin's setOpaque(true) writes background-color from
        // getBackground(). With a real background set, the readback finds
        // the CSS rule and isOpaque() returns true.
        SJPanel p = new SJPanel();
        p.setBackground(Color.RED);
        p.setOpaque(true);
        assertTrue(p.isOpaque());
    }

    @Test
    @DisplayName("setOpaque(false) writes transparent CSS")
    void setOpaqueFalseWritesTransparentCss() {
        SJPanel p = new SJPanel();
        p.setBackground(Color.BLUE);
        p.setOpaque(true);
        p.setOpaque(false);
        // Per the mixin: setOpaque(false) writes background-color: transparent
        assertEquals("transparent", p.getElement().getStyle().get("background-color"));
    }

    // --- Layout dispatch (inherited mixin) ---------------------------

    @Test
    @DisplayName("BorderLayout dispatch writes grid CSS via inherited mixin")
    void borderLayoutDispatchWritesGridCssViaInheritedMixin() {
        SJPanel p = new SJPanel(new BorderLayout());
        Style style = p.getElement().getStyle();
        assertEquals("grid", style.get("display"));
        assertNotNull(style.get("grid-template-columns"));
        assertNotNull(style.get("grid-template-rows"));
    }

    @Test
    @DisplayName("custom LayoutManager stores but does not dispatch CSS")
    void customLayoutManagerStoresButDoesNotDispatchCss() {
        // Per SD_sjpanel §"LayoutManager handling": only built-in JDK
        // FlowLayout / BorderLayout dispatch CSS; other layouts (custom
        // user subclasses, GridLayout etc.) store but render as block
        // flow. Same gap the emulator has.
        LayoutManager custom = new LayoutManager() {
            @Override
            public void addLayoutComponent(String name, Component comp) {
            }

            @Override
            public void removeLayoutComponent(Component comp) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                return new Dimension(0, 0);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(0, 0);
            }

            @Override
            public void layoutContainer(Container parent) {
            }
        };
        SJPanel p = new SJPanel(custom);
        assertSame(custom, p.getLayout());
        // No FlowLayout/BorderLayout dispatch happened — host stays at the
        // Div's default (no display: flex / grid).
        String display = p.getElement().getStyle().get("display");
        assertTrue(display == null || display.equals("block"),
                "Expected unset / block display for custom LayoutManager, got '" + display + "'");
    }

    @Test
    @DisplayName("GridLayout (unrecognised) stores but does not dispatch CSS")
    void gridLayoutStoresButDoesNotDispatchCss() {
        // GridLayout isn't in the recognised set today — store-and-no-CSS
        // per SD_sjpanel's "Custom LayoutManager subclasses don't dispatch CSS"
        // limitation (extends to unrecognised JDK layouts too).
        GridLayout gl = new GridLayout(2, 2);
        SJPanel p = new SJPanel(gl);
        assertSame(gl, p.getLayout());
        String display = p.getElement().getStyle().get("display");
        assertTrue(display == null || display.equals("block"),
                "Expected unset / block display for GridLayout, got '" + display + "'");
    }

    // --- Border (inherited JComponentMixin) --------------------------

    @Test
    @DisplayName("setBorder LineBorder round-trips via inherited mixin")
    void setBorderLineBorderRoundTripsViaInheritedMixin() {
        SJPanel p = new SJPanel();
        Border border = BorderFactory.createLineBorder(Color.GREEN, 2);
        p.setBorder(border);
        // Mixin writes border CSS; reading back reconstructs via the
        // SD_border_css_lossy lossy CSS-parse path. Identity isn't preserved (new
        // LineBorder instance), but type + visible properties survive.
        Border readBack = p.getBorder();
        assertInstanceOf(LineBorder.class, readBack);
    }

    // --- HasComponents / add+remove ----------------------------------

    @Test
    @DisplayName("add and remove children via inherited HasComponents")
    void addAndRemoveChildrenViaInheritedHasComponents() {
        SJPanel p = new SJPanel();
        Span child = new Span("hello");
        p.add(child);
        assertEquals(1, p.getElement().getChildCount());
        p.remove(child);
        assertEquals(0, p.getElement().getChildCount());
    }

    // --- Happy-path zero-WARN ----------------------------------------

    @Test
    @DisplayName("happy-path UI-functional surface is WARN-free")
    void happyPathUiFunctionalSurfaceIsWarnFree() {
        SJPanel p = new SJPanel(new BorderLayout(4, 4));
        p.setBackground(Color.WHITE);
        p.setOpaque(true);
        p.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        p.add(new Span("first"));
        p.add(new Span("second"));
        // setLayout to FlowLayout via setter (re-dispatch) — should still
        // be silent.
        p.setLayout(new FlowLayout(FlowLayout.LEADING, 2, 2));

        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }
}
