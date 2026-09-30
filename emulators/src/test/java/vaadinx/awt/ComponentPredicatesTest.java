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
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.Point;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class ComponentPredicatesTest extends AbstractKaribuTest {

    private Component newComponent() {
        return new Component(new Div()) {
        };
    }

    @Test
    @DisplayName("predicates return documented defaults")
    void predicatesReturnDocumentedDefaults() {
        Component c = newComponent();
        assertTrue(c.isOpaque());
        assertTrue(c.isLightweight());
        assertTrue(c.isValid());
        assertFalse(c.isDoubleBuffered());
    }

    @Test
    @DisplayName("isDisplayable and isShowing track peer attachment")
    void isDisplayableAndIsShowingTrackPeerAttachment() {
        Component c = newComponent();
        assertFalse(c.isDisplayable(), "detached peer is not displayable");
        assertFalse(c.isShowing());

        UI.getCurrent().getElement().appendChild(c.getPeer().getElement());
        assertTrue(c.isDisplayable());
        assertTrue(c.isShowing());

        c.setVisible(false);
        assertTrue(c.isDisplayable(), "displayable unchanged by visibility");
        assertFalse(c.isShowing(), "invisible is never showing");
    }

    @Test
    @DisplayName("numeric defaults match AWT contract")
    void numericDefaultsMatchAwtContract() {
        Component c = newComponent();
        assertEquals(0.5f, c.getAlignmentX());
        assertEquals(0.5f, c.getAlignmentY());
        assertEquals(-1, c.getBaseline(100, 20));
    }

    @Test
    @DisplayName("getTreeLock returns a stable non-null singleton across instances")
    void getTreeLockReturnsAStableNonNullSingletonAcrossInstances() {
        Component a = newComponent();
        Component b = newComponent();
        assertNotNull(a.getTreeLock());
        assertSame(a.getTreeLock(), b.getTreeLock());
    }

    @Test
    @DisplayName("contains and inside are null-safe and return false")
    void containsAndInsideAreNullSafeAndReturnFalse() {
        Component c = newComponent();
        assertFalse(c.contains(0, 0));
        assertFalse(c.contains(new Point(5, 5)));
        assertFalse(c.contains(null));
        assertFalse(c.inside(10, 10));
    }

    @Test
    @DisplayName("list prints toString with indentation")
    void listPrintsToStringWithIndentation() {
        Component c = newComponent();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        c.list(new PrintStream(baos), 3);
        String out = baos.toString().stripTrailing();
        assertEquals("   " + c, out);
    }

    @Test
    @DisplayName("deprecated AWT 1.0 callbacks return false")
    void deprecatedAwt10CallbacksReturnFalse() {
        Component c = newComponent();
        assertFalse(c.mouseDown(null, 0, 0));
        assertFalse(c.keyDown(null, 0));
        assertFalse(c.gotFocus(null, null));
        assertFalse(c.action(null, null));
        assertFalse(c.handleEvent(null));
    }
}
