/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.JLayeredPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

/**
 * Minimal port of {@link javax.swing.JLayeredPane}. The JDK class
 * manages z-ordered children across named layers
 * (DEFAULT / PALETTE / MODAL / POPUP / DRAG); our 4d port exposes the
 * constants + add/setLayer surface for API parity, but doesn't actually
 * z-order in the browser — children render at their insertion order
 * (R_best_effort_behaviour/R_infra_not_surface best-effort). Suitable for migrated code that instantiates a
 * layered pane and reads back its layers; not suitable for apps that
 * actually rely on layer-based overlay rendering, which a later slice
 * would need to hook into Vaadin's dialog/popover overlay machinery.
 */
public class JLayeredPane extends JComponent implements javax.accessibility.Accessible {

    // JDK layer constants — identical Integer values for API parity.
    public static final Integer DEFAULT_LAYER = Integer.valueOf(0);
    public static final Integer PALETTE_LAYER = Integer.valueOf(100);
    public static final Integer MODAL_LAYER = Integer.valueOf(200);
    public static final Integer POPUP_LAYER = Integer.valueOf(300);
    public static final Integer DRAG_LAYER = Integer.valueOf(400);
    public static final Integer FRAME_CONTENT_LAYER = Integer.valueOf(-30000);

    // JDK client-property key for per-component layer tag — string-keyed
    // since we just round-trip the attribute.
    public static final String LAYER_PROPERTY = "layeredContainerLayer";

    public JLayeredPane() {
        super(new com.vaadin.flow.component.html.Div());
    }

    public void setLayer(vaadinx.awt.Component c, int layer) {
        // Store the layer as a client property on the Component — matches
        // JDK's contract that getLayer reads the stored tag back. Actual
        // z-ordering is deferred: Vaadin renders children in DOM order,
        // and our ordering matches Swing's add order.
        setLayer(c, layer, -1);
    }

    public void setLayer(vaadinx.awt.Component c, int layer, int position) {
        // JDK stores the layer via SwingUtilities.putClientProperty, then
        // may reorder children to match. We skip the reorder (no real
        // layering) and round-trip the value for user code that reads it.
        if (c instanceof JComponent jc) {
            jc.putClientProperty(LAYER_PROPERTY, Integer.valueOf(layer));
        }
    }

    public int getLayer(vaadinx.awt.Component c) {
        if (c instanceof JComponent jc) {
            Object v = jc.getClientProperty(LAYER_PROPERTY);
            if (v instanceof Integer i) return i;
        }
        return DEFAULT_LAYER;
    }

    public static int getLayer(JComponent c) {
        Object v = c.getClientProperty(LAYER_PROPERTY);
        if (v instanceof Integer i) return i;
        return DEFAULT_LAYER;
    }

    public int getIndexOf(vaadinx.awt.Component c) {
        return getComponentZOrder(c);
    }

    public int getComponentCountInLayer(int layer) {
        // Count children whose layer tag matches. Lazy: users rarely read
        // this, and the sum walks the children list.
        int count = 0;
        for (vaadinx.awt.Component child : getComponents()) {
            if (child instanceof JComponent jc) {
                Object v = jc.getClientProperty(LAYER_PROPERTY);
                if (v instanceof Integer i && i == layer) count++;
            }
        }
        return count;
    }

    public vaadinx.awt.Component[] getComponentsInLayer(int layer) {
        java.util.List<vaadinx.awt.Component> out = new java.util.ArrayList<>();
        for (vaadinx.awt.Component child : getComponents()) {
            if (child instanceof JComponent jc) {
                Object v = jc.getClientProperty(LAYER_PROPERTY);
                if (v instanceof Integer i && i == layer) out.add(child);
            }
        }
        return out.toArray(new vaadinx.awt.Component[0]);
    }

    public int highestLayer() {
        int h = Integer.MIN_VALUE;
        for (vaadinx.awt.Component child : getComponents()) {
            if (child instanceof JComponent jc) {
                Object v = jc.getClientProperty(LAYER_PROPERTY);
                if (v instanceof Integer i && i > h) h = i;
            }
        }
        return h == Integer.MIN_VALUE ? 0 : h;
    }

    public int lowestLayer() {
        int l = Integer.MAX_VALUE;
        for (vaadinx.awt.Component child : getComponents()) {
            if (child instanceof JComponent jc) {
                Object v = jc.getClientProperty(LAYER_PROPERTY);
                if (v instanceof Integer i && i < l) l = i;
            }
        }
        return l == Integer.MAX_VALUE ? 0 : l;
    }

    public int getPosition(vaadinx.awt.Component c) {
        // JDK: index within the component's layer (0 = topmost). Our
        // no-z-ordering model collapses this to the child-list index.
        return getComponentZOrder(c);
    }

    public void moveToFront(vaadinx.awt.Component c) {
        // No z-order, no-op. Would need a real stacking model to honor.
        vaadinx.EHelper.onNoop("JLayeredPane", "moveToFront");
    }

    public void moveToBack(vaadinx.awt.Component c) {
        vaadinx.EHelper.onNoop("JLayeredPane", "moveToBack");
    }

    public java.lang.String getUIClassID() {
        return "LayeredPaneUI";
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JLayeredPane", "getAccessibleContext");
        return null;
    }
}
