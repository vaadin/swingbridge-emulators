/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.GraphicsEnvironment
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Import-swap target for {@link java.awt.GraphicsEnvironment} — the browser
 * viewport presented as a local display, so the maximize-to-screen idiom ports
 * unchanged:
 *
 * <pre>{@code
 * GraphicsEnvironment e = GraphicsEnvironment.getLocalGraphicsEnvironment();
 * frame.setMaximizedBounds(e.getMaximumWindowBounds());   // (0, 0, viewport)
 * frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
 * }</pre>
 *
 * <p><b>{@link #isHeadless()} is false.</b> The JDK's answer on a server is
 * {@code true}, which silently sends a migrated app down its no-display branch —
 * skipping the UI it was about to build. A browser is a display, so this reports
 * one, and every screen query below answers rather than throwing
 * {@code HeadlessException}.
 *
 * <p>Like {@link Toolkit} this does not extend the abstract JDK class — it is the
 * import-swap surface, not a platform environment — and selects per-method
 * behaviour by the same tiers:
 *
 * <ul>
 *   <li><b>Browser-backed</b>, via the default device's configuration:
 *       {@link #getMaximumWindowBounds}, {@link #getCenterPoint},
 *       {@link #getScreenDevices}, {@link #getDefaultScreenDevice}.</li>
 *   <li><b>Delegated to the headless JDK environment</b>, which computes these
 *       correctly and is authoritative for them because they describe the
 *       <em>server's</em> fonts — the same fonts {@link Toolkit#getFontMetrics}
 *       measures with: {@link #createGraphics}, {@link #getAllFonts},
 *       {@link #getAvailableFontFamilyNames}, {@link #registerFont},
 *       {@link #preferLocaleFonts}, {@link #preferProportionalFonts}.</li>
 * </ul>
 *
 * <p>No method WARNs: every one of them has a real answer here.
 *
 * <p>Font delegation is what keeps {@code getAvailableFontFamilyNames()}
 * consistent with what a {@code FontMetrics} measurement will actually
 * report, which is the only sense in which font queries can be right at
 * all when the glyphs are ultimately rendered by the browser.
 */
public final class GraphicsEnvironment {

    private static final GraphicsEnvironment INSTANCE = new GraphicsEnvironment();

    private GraphicsEnvironment() {
    }

    public static GraphicsEnvironment getLocalGraphicsEnvironment() {
        return INSTANCE;
    }

    /** @return always false; see the class doc for why the JDK's {@code true} is the wrong answer */
    public static boolean isHeadless() {
        return false;
    }

    /** @return always false — see {@link #isHeadless()} */
    public boolean isHeadlessInstance() {
        return false;
    }

    public GraphicsDevice[] getScreenDevices() {
        return new GraphicsDevice[] { GraphicsDevice.INSTANCE };
    }

    public GraphicsDevice getDefaultScreenDevice() {
        return GraphicsDevice.INSTANCE;
    }

    /** The area a maximized window should fill: the viewport, less any screen insets. */
    public Rectangle getMaximumWindowBounds() {
        return usableBounds(getDefaultScreenDevice());
    }

    public Point getCenterPoint() {
        Rectangle usable = usableBounds(getDefaultScreenDevice());
        return new Point((usable.width / 2) + usable.x, (usable.height / 2) + usable.y);
    }

    public Graphics2D createGraphics(BufferedImage img) {
        return jdk().createGraphics(img);
    }

    public Font[] getAllFonts() {
        return jdk().getAllFonts();
    }

    public String[] getAvailableFontFamilyNames() {
        return jdk().getAvailableFontFamilyNames();
    }

    public String[] getAvailableFontFamilyNames(Locale l) {
        return jdk().getAvailableFontFamilyNames(l);
    }

    /**
     * @return false if {@code font} conflicts with a family already present — the JDK's
     *         own contract, delegated rather than reimplemented
     * @throws NullPointerException if {@code font} is null
     */
    public boolean registerFont(Font font) {
        return jdk().registerFont(font);
    }

    public void preferLocaleFonts() {
        jdk().preferLocaleFonts();
    }

    public void preferProportionalFonts() {
        jdk().preferProportionalFonts();
    }

    /** The JDK's own {@code SunGraphicsEnvironment.getUsableBounds} body, over our device. */
    private static Rectangle usableBounds(GraphicsDevice gd) {
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle bounds = gc.getBounds();
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= (insets.left + insets.right);
        bounds.height -= (insets.top + insets.bottom);
        return bounds;
    }

    private static java.awt.GraphicsEnvironment jdk() {
        return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
    }
}
