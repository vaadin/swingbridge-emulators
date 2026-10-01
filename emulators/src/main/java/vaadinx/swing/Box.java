/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.Box
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Style;
import vaadinx.awt.Component;
import vaadinx.awt.LayoutManager;

import java.awt.AWTError;
import java.awt.Dimension;

/**
 * Emulator for {@link javax.swing.Box}. Container with a pre-installed
 * {@link BoxLayout} plus static factories producing the canonical
 * {@link Filler} components migrators reach for: horizontal/vertical
 * glue, struts, and rigid areas.
 *
 * <p>R_leaf_peer_lockdown-locked-down — JDK {@code Box} is a leaf in the public
 * {@code javax.swing.*} hierarchy. Peer is a plain {@link Div}; the
 * protected {@code (Component peer)} ctor is omitted so user-code
 * subclasses inherit the locked Div peer.
 *
 * <p>Emulator-only — no {@code SBox} surrogate by SD_no_sjoptionpane-shape carve-out
 * (the JOptionPane "no surrogate by design" precedent). Pure-surrogate
 * users have no use for Box: Vaadin's {@code VerticalLayout} /
 * {@code HorizontalLayout} cover the same ground without the JDK ergonomic
 * lift.
 *
 * <p><b>{@link #setLayout} throws.</b> JDK Box always throws
 * {@link AWTError} from {@code setLayout} — the layout is fixed at
 * construction time and the ctor bypasses via {@code super.setLayout}.
 * R_match_swing_errors: we match the same throw, same message.
 */
public class Box extends JComponent implements javax.accessibility.Accessible {

    public Box(int axis) {
        // Pin peer at Div (R_leaf_peer_lockdown lock-down). super.setLayout bypasses our
        // overridden setLayout (which always throws per JDK contract) so
        // the BoxLayout install lands. Mirrors JDK's `super.setLayout`
        // workaround verbatim.
        super(Div.class, Div::new);
        super.setLayout(new BoxLayout(this, axis));
    }

    /**
     * JDK {@code Box.setLayout} unconditionally throws {@link AWTError}
     * to signal "use the layout the ctor installed." R_match_swing_errors — match the same
     * exception type and message.
     */
    @Override
    public void setLayout(LayoutManager l) {
        throw new AWTError("Illegal request");
    }

    public static Box createHorizontalBox() {
        return new Box(BoxLayout.X_AXIS);
    }

    public static Box createVerticalBox() {
        return new Box(BoxLayout.Y_AXIS);
    }

    public static Component createHorizontalGlue() {
        return new Filler(
                new Dimension(0, 0),
                new Dimension(0, 0),
                new Dimension(Short.MAX_VALUE, 0));
    }

    public static Component createVerticalGlue() {
        return new Filler(
                new Dimension(0, 0),
                new Dimension(0, 0),
                new Dimension(0, Short.MAX_VALUE));
    }

    public static Component createGlue() {
        return new Filler(
                new Dimension(0, 0),
                new Dimension(0, 0),
                new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
    }

    public static Component createHorizontalStrut(int width) {
        return new Filler(
                new Dimension(width, 0),
                new Dimension(width, 0),
                new Dimension(width, Short.MAX_VALUE));
    }

    public static Component createVerticalStrut(int height) {
        return new Filler(
                new Dimension(0, height),
                new Dimension(0, height),
                new Dimension(Short.MAX_VALUE, height));
    }

    public static Component createRigidArea(Dimension d) {
        return new Filler(new Dimension(d), new Dimension(d), new Dimension(d));
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        // JPanel precedent — L&F-shaped accessibility context isn't
        // wired; null-and-WARN so migration logs surface a11y-aware code.
        vaadinx.EHelper.onUnimplemented("Box", "getAccessibleContext");
        return null;
    }

    /**
     * Emulator for {@link javax.swing.Box.Filler}. Invisible JComponent
     * sized by its min/pref/max dimensions, used as the implementation of
     * Box's static glue/strut/rigid-area factories. R_leaf_peer_lockdown-locked-down on a
     * Div peer.
     *
     * <p>CSS strategy at construction (and on {@link #changeShape}):
     * <ul>
     *   <li><b>Glue</b> ({@code pref == (0,0)} with at least one max
     *       dimension at {@link Short#MAX_VALUE}): write
     *       {@code flex-grow: 1}. Container's main-axis direction
     *       determines whether it grows horizontally or vertically — the
     *       canonical pattern (horizontal glue in a row Box, vertical glue
     *       in a column Box) gives the expected JDK behaviour.</li>
     *   <li><b>Strut</b> (one pref dimension non-zero, the other zero):
     *       write the non-zero dimension as a fixed pixel size plus
     *       {@code flex-shrink: 0} so the strut doesn't collapse under
     *       container pressure.</li>
     *   <li><b>Rigid area</b> (both pref dimensions non-zero): both
     *       fixed dimensions plus {@code flex-shrink: 0}.</li>
     * </ul>
     *
     * <p><b>Cross-axis-glue caveat.</b> A horizontal glue dropped into a
     * Y_AXIS container still gets {@code flex-grow: 1} and so will grow
     * vertically rather than do nothing — JDK behavior is "no growth in
     * cross axis." Small visual divergence under R_layouts_close_enough; canonical usage
     * (matched-axis glue) renders correctly.
     */
    public static class Filler extends JComponent implements javax.accessibility.Accessible {

        private Dimension minSize;
        private Dimension prefSize;
        private Dimension maxSize;

        public Filler(Dimension min, Dimension pref, Dimension max) {
            super(Div.class, Div::new);
            // Defensive copies — JDK Filler stores references and mutates
            // them in changeShape, but migrators occasionally pass a shared
            // Dimension; cloning matches the JDK ctor's documented "the
            // field copies are made" behavior for the no-arg-mutation
            // contract.
            this.minSize = new Dimension(min);
            this.prefSize = new Dimension(pref);
            this.maxSize = new Dimension(max);
            applyFillerCss();
        }

        /**
         * JDK contract: replace all three sizes and invalidate. We mirror
         * the field swap and re-apply the CSS — invalidate() is a no-op
         * here per Component.java since Vaadin re-lays on DOM change.
         */
        public void changeShape(Dimension min, Dimension pref, Dimension max) {
            this.minSize = new Dimension(min);
            this.prefSize = new Dimension(pref);
            this.maxSize = new Dimension(max);
            applyFillerCss();
            invalidate();
        }

        private void applyFillerCss() {
            withPeer(p -> {
                Style s = p.getElement().getStyle();
                // Clear any prior write so changeShape from one shape to another
                // doesn't leak stale dimensions.
                s.remove("flex-grow");
                s.remove("flex-shrink");
                s.remove("width");
                s.remove("height");

                boolean isGlue = prefSize.width == 0 && prefSize.height == 0
                        && (maxSize.width >= Short.MAX_VALUE || maxSize.height >= Short.MAX_VALUE);

                if (isGlue) {
                    s.set("flex-grow", "1");
                    return;
                }
                if (prefSize.width > 0) {
                    s.set("width", prefSize.width + "px");
                }
                if (prefSize.height > 0) {
                    s.set("height", prefSize.height + "px");
                }
                // Struts and rigid areas resist collapse — without flex-shrink:0
                // a tight container squeezes them out, defeating the spacing
                // intent migrators reach for `createVerticalStrut(8)` to get.
                s.set("flex-shrink", "0");
            });
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(minSize);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(prefSize);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(maxSize);
        }

        public javax.accessibility.AccessibleContext getAccessibleContext() {
            vaadinx.EHelper.onUnimplemented("Box.Filler", "getAccessibleContext");
            return null;
        }
    }
}
