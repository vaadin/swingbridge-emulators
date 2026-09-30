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

import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.FlowLayout;

import java.awt.AWTError;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoxTest extends AbstractKaribuTest {

    /** {@code Short.MAX_VALUE} as an int — the JDK's "unbounded" axis size. */
    private static final int UNBOUNDED = Short.MAX_VALUE;

    /** The Box's installed layout, asserted to be a BoxLayout. */
    private static BoxLayout boxLayoutOf(Box box) {
        return assertInstanceOf(BoxLayout.class, box.getLayout());
    }

    private static Style styleOf(vaadinx.awt.Component c) {
        return c.getPeer().getElement().getStyle();
    }

    @Test
    @DisplayName("Box(axis) installs a BoxLayout pointed at itself")
    void boxCtorInstallsBoxLayout() {
        Box box = new Box(BoxLayout.Y_AXIS);
        BoxLayout layout = boxLayoutOf(box);
        assertEquals(BoxLayout.Y_AXIS, layout.getAxis());
        // BoxLayout.target == the Box itself — the ctor passed `this`.
        assertEquals(box, layout.getTarget());
    }

    @Test
    @DisplayName("setLayout always throws AWTError per JDK contract")
    void setLayoutAlwaysThrows() {
        Box box = new Box(BoxLayout.Y_AXIS);
        // JDK Box.setLayout unconditionally throws — even with a BoxLayout.
        // Migrators that try to swap layouts get the JDK-shaped error.
        assertThrows(AWTError.class, () -> box.setLayout(new FlowLayout()));
        assertThrows(AWTError.class, () -> box.setLayout(new BoxLayout(box, BoxLayout.X_AXIS)));
    }

    @Test
    @DisplayName("createHorizontalBox and createVerticalBox pick the right axis")
    void factoryBoxesPickTheRightAxis() {
        assertEquals(BoxLayout.X_AXIS, boxLayoutOf(Box.createHorizontalBox()).getAxis());
        assertEquals(BoxLayout.Y_AXIS, boxLayoutOf(Box.createVerticalBox()).getAxis());
    }

    @Test
    @DisplayName("createHorizontalGlue produces flex-grow CSS")
    void horizontalGlueProducesFlexGrow() {
        Box.Filler glue = (Box.Filler) Box.createHorizontalGlue();
        assertEquals(new Dimension(0, 0), glue.getMinimumSize());
        assertEquals(new Dimension(0, 0), glue.getPreferredSize());
        assertEquals(new Dimension(UNBOUNDED, 0), glue.getMaximumSize());

        Style style = styleOf(glue);
        assertEquals("1", style.get("flex-grow"));
        // Glue: no fixed width/height, no flex-shrink:0 — those are for struts/rigid.
        assertNull(style.get("width"));
        assertNull(style.get("height"));
        assertNull(style.get("flex-shrink"));
    }

    @Test
    @DisplayName("createVerticalGlue produces flex-grow CSS")
    void verticalGlueProducesFlexGrow() {
        Box.Filler glue = (Box.Filler) Box.createVerticalGlue();
        assertEquals(new Dimension(0, UNBOUNDED), glue.getMaximumSize());
        assertEquals("1", styleOf(glue).get("flex-grow"));
    }

    @Test
    @DisplayName("createGlue (omni) produces flex-grow CSS")
    void omniGlueProducesFlexGrow() {
        Box.Filler glue = (Box.Filler) Box.createGlue();
        assertEquals(new Dimension(UNBOUNDED, UNBOUNDED), glue.getMaximumSize());
        assertEquals("1", styleOf(glue).get("flex-grow"));
    }

    @Test
    @DisplayName("createHorizontalStrut writes fixed width + flex-shrink 0")
    void horizontalStrutWritesFixedWidth() {
        Box.Filler strut = (Box.Filler) Box.createHorizontalStrut(12);
        assertEquals(new Dimension(12, 0), strut.getMinimumSize());
        assertEquals(new Dimension(12, 0), strut.getPreferredSize());
        assertEquals(new Dimension(12, UNBOUNDED), strut.getMaximumSize());

        Style style = styleOf(strut);
        assertEquals("12px", style.get("width"));
        assertNull(style.get("height")); // pref.height == 0 → no height write
        assertEquals("0", style.get("flex-shrink"));
        assertNull(style.get("flex-grow"));
    }

    @Test
    @DisplayName("createVerticalStrut writes fixed height + flex-shrink 0")
    void verticalStrutWritesFixedHeight() {
        Box.Filler strut = (Box.Filler) Box.createVerticalStrut(8);
        assertEquals(new Dimension(0, 8), strut.getPreferredSize());

        Style style = styleOf(strut);
        assertEquals("8px", style.get("height"));
        assertNull(style.get("width"));
        assertEquals("0", style.get("flex-shrink"));
        assertNull(style.get("flex-grow"));
    }

    @Test
    @DisplayName("createRigidArea writes both fixed dimensions + flex-shrink 0")
    void rigidAreaWritesBothDimensions() {
        Box.Filler rigid = (Box.Filler) Box.createRigidArea(new Dimension(10, 20));
        assertEquals(new Dimension(10, 20), rigid.getMinimumSize());
        assertEquals(new Dimension(10, 20), rigid.getPreferredSize());
        assertEquals(new Dimension(10, 20), rigid.getMaximumSize());

        Style style = styleOf(rigid);
        assertEquals("10px", style.get("width"));
        assertEquals("20px", style.get("height"));
        assertEquals("0", style.get("flex-shrink"));
        assertNull(style.get("flex-grow"));
    }

    @Test
    @DisplayName("Filler defensive-copies its dimensions (JDK no-arg-mutation contract)")
    void fillerDefensiveCopiesDimensions() {
        Dimension pref = new Dimension(5, 5);
        Box.Filler filler = new Box.Filler(new Dimension(0, 0), pref, new Dimension(5, 5));
        // Mutate the caller's Dimension — the Filler's stored value mustn't follow.
        pref.width = 999;
        assertEquals(5, filler.getPreferredSize().width);
    }

    @Test
    @DisplayName("changeShape rewrites CSS — strut becomes glue")
    void changeShapeStrutToGlue() {
        Box.Filler filler = (Box.Filler) Box.createHorizontalStrut(20);
        // Before: strut shape.
        assertEquals("20px", styleOf(filler).get("width"));
        assertEquals("0", styleOf(filler).get("flex-shrink"));

        // Reshape to glue.
        filler.changeShape(
                new Dimension(0, 0), new Dimension(0, 0),
                new Dimension(UNBOUNDED, 0));

        // After: glue shape — stale width cleared, flex-grow set.
        Style style = styleOf(filler);
        assertNull(style.get("width"));
        assertNull(style.get("flex-shrink"));
        assertEquals("1", style.get("flex-grow"));
        assertEquals(new Dimension(0, 0), filler.getPreferredSize());
    }

    @Test
    @DisplayName("changeShape rewrites CSS — glue becomes rigid area")
    void changeShapeGlueToRigid() {
        Box.Filler filler = (Box.Filler) Box.createGlue();
        assertEquals("1", styleOf(filler).get("flex-grow"));

        filler.changeShape(new Dimension(7, 7), new Dimension(7, 7), new Dimension(7, 7));

        Style style = styleOf(filler);
        assertNull(style.get("flex-grow"));
        assertEquals("7px", style.get("width"));
        assertEquals("7px", style.get("height"));
        assertEquals("0", style.get("flex-shrink"));
    }

    @Test
    @DisplayName("getMinimumSize and getPreferredSize and getMaximumSize return clones")
    void sizeGettersReturnClones() {
        // Mutating the returned Dimension must not corrupt the Filler's state.
        Box.Filler filler = (Box.Filler) Box.createVerticalStrut(8);
        Dimension pref = filler.getPreferredSize();
        pref.height = 999;
        assertNotEquals(999, filler.getPreferredSize().height);
    }

    @Test
    @DisplayName("Box ctor accepts X_AXIS, Y_AXIS, LINE_AXIS, PAGE_AXIS")
    void boxCtorAcceptsEveryAxis() {
        // Smoke — bad axis throws at the BoxLayout ctor (see BoxLayoutTest);
        // Box's ctor is just a thin wrapper.
        new Box(BoxLayout.X_AXIS);
        new Box(BoxLayout.Y_AXIS);
        new Box(BoxLayout.LINE_AXIS);
        new Box(BoxLayout.PAGE_AXIS);
        assertThrows(AWTError.class, () -> new Box(99));
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null (JPanel precedent)")
    void accessibleContextIsNull() {
        Box box = new Box(BoxLayout.Y_AXIS);
        assertNull(box.getAccessibleContext());

        Box.Filler filler = (Box.Filler) Box.createGlue();
        assertNull(filler.getAccessibleContext());
    }

    @Test
    @DisplayName("Box installed inside a JPanel writes flex-direction column on the panel")
    void boxValidateWritesFlexColumn() {
        // End-to-end smoke: a Box with a Y_AXIS layout, when validated,
        // writes flex-direction: column to its own peer element. Mirrors
        // BoxLayoutTest's "setLayout + validate" but with the canonical
        // Box(int) ctor path.
        Box box = new Box(BoxLayout.Y_AXIS);
        box.validate();

        Style style = styleOf(box);
        assertEquals("flex", style.get("display"));
        assertEquals("column", style.get("flex-direction"));
    }

    @Test
    @DisplayName("Box adds children to its own peer (BoxLayout doesn't touch them)")
    void boxAddsChildrenWithoutCssLeak() {
        // Regression: BoxLayout.addLayoutComponent is a no-op, so a child
        // added to a Box should land on the peer without any per-child
        // CSS leak.
        Box box = new Box(BoxLayout.Y_AXIS);
        JPanel child = new JPanel();
        box.add(child);

        assertNotNull(child.getPeer().getElement().getParent());
        assertNull(styleOf(child).get("flex-grow"));
        assertNull(styleOf(child).get("grid-area"));
    }
}
