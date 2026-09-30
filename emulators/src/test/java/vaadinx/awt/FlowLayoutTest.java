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

import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowLayoutTest extends AbstractKaribuTest {

    @Test
    @DisplayName("defaults match Swing — align CENTER, 5px gaps")
    void defaultsMatchSwingAlignCenter5pxGaps() {
        FlowLayout fl = new FlowLayout();
        assertEquals(FlowLayout.CENTER, fl.getAlignment());
        assertEquals(5, fl.getHgap());
        assertEquals(5, fl.getVgap());
        assertFalse(fl.getAlignOnBaseline());
    }

    @Test
    @DisplayName("layoutContainer writes flex CSS to the container peer element")
    void layoutContainerWritesFlexCssToTheContainerPeerElement() {
        Container c = new Container();

        new FlowLayout().layoutContainer(c);

        var style = c.getPeer().getElement().getStyle();
        assertEquals("flex", style.get("display"));
        assertEquals("wrap", style.get("flex-wrap"));
        assertEquals("center", style.get("justify-content"));
        assertEquals("5px 5px", style.get("gap"));
    }

    @Test
    @DisplayName("LEADING and LEFT both map to flex-start")
    void leadingAndLeftBothMapToFlexStart() {
        // Swing's LEFT is explicit LTR; LEADING flips with
        // ComponentOrientation. Both land on flex-start because the
        // flex container respects the `dir` attribute Component.setComponentOrientation
        // writes — the browser handles the flip for us.
        Container c = new Container();
        new FlowLayout(FlowLayout.LEADING).layoutContainer(c);
        assertEquals("flex-start", c.getPeer().getElement().getStyle().get("justify-content"));

        Container c2 = new Container();
        new FlowLayout(FlowLayout.LEFT).layoutContainer(c2);
        assertEquals("flex-start", c2.getPeer().getElement().getStyle().get("justify-content"));
    }

    @Test
    @DisplayName("TRAILING and RIGHT both map to flex-end")
    void trailingAndRightBothMapToFlexEnd() {
        Container c = new Container();
        new FlowLayout(FlowLayout.TRAILING).layoutContainer(c);
        assertEquals("flex-end", c.getPeer().getElement().getStyle().get("justify-content"));

        Container c2 = new Container();
        new FlowLayout(FlowLayout.RIGHT).layoutContainer(c2);
        assertEquals("flex-end", c2.getPeer().getElement().getStyle().get("justify-content"));
    }

    @Test
    @DisplayName("unknown alignment falls through to flex-start")
    void unknownAlignmentFallsThroughToFlexStart() {
        // JDK FlowLayout silently accepts any int via setAlignment —
        // we match that permissive behavior rather than erroring, with
        // the CSS mapping defaulting to flex-start for safety.
        Container c = new Container();
        FlowLayout fl = new FlowLayout();
        fl.setAlignment(999);
        fl.layoutContainer(c);
        assertEquals("flex-start", c.getPeer().getElement().getStyle().get("justify-content"));
    }

    @Test
    @DisplayName("setHgap and setVgap feed into the next CSS write")
    void setHgapAndSetVgapFeedIntoTheNextCssWrite() {
        // CSS gap shorthand is row-gap then column-gap: hgap is between
        // components on a row (column-gap), vgap is between wrapped rows
        // (row-gap). Regression against accidentally swapping them.
        Container c = new Container();
        FlowLayout fl = new FlowLayout();
        fl.setHgap(20);
        fl.setVgap(8);

        fl.layoutContainer(c);

        assertEquals("8px 20px", c.getPeer().getElement().getStyle().get("gap"));
    }

    @Test
    @DisplayName("non-Div peer gets no CSS — onUnsupportedPeerShape guard per D_layout_css_on_content")
    void nonDivPeerGetsNoCssUnsupportedPeerShapeGuardPerDLayoutCssOnContent() {
        // Container with a Span peer (non-Div) — applyContainerCss
        // refuses to write, so no `display` style lands. The ERROR log
        // itself isn't asserted here (would need a log appender); we
        // verify the observable outcome: style map stays clean.
        Container c = new Container(new Span()) {
        };

        new FlowLayout().layoutContainer(c);

        assertNull(c.getPeer().getElement().getStyle().get("display"));
        assertNull(c.getPeer().getElement().getStyle().get("justify-content"));
    }

    @Test
    @DisplayName("setAlignOnBaseline round-trips even though CSS ignores it")
    void setAlignOnBaselineRoundTripsEvenThoughCssIgnoresIt() {
        // Field round-trip only — CSS align-items:baseline applies across
        // the whole flex container, not per wrapped row (R_layouts_close_enough).
        FlowLayout fl = new FlowLayout();
        fl.setAlignOnBaseline(true);
        assertTrue(fl.getAlignOnBaseline());
    }
}
