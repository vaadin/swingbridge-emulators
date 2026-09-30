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
 * This file is derived from OpenJDK's java.awt.GraphicsDevice
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

import java.awt.DisplayMode;
import java.awt.Rectangle;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;

/**
 * Import-swap target for {@link java.awt.GraphicsDevice} — the browser viewport
 * as the one screen a migrated app has (D_graphics_environment). Obtained from
 * the environment, and the usual reason to hold one is its display mode:
 *
 * <pre>{@code
 * GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
 * int w = gd.getDisplayMode().getWidth();     // viewport width
 * gd.setFullScreenWindow(frame);              // windowed-mode fallback: fills the viewport
 * }</pre>
 *
 * <p>Full-screen exclusive mode is <em>unsupported</em>, exactly as on a JDK
 * device with no exclusive-mode capability — {@link #isFullScreenSupported()} is
 * false, and {@link #setFullScreenWindow} therefore takes the JDK's windowed-mode
 * path: it resizes the window to the viewport, shows it and raises it, restoring
 * the previous bounds when full-screen is later released. That is a working
 * effect, not a stub, so no WARN is logged.
 *
 * <p>Like {@link Toolkit} this does not extend the abstract JDK class, so
 * {@link #setFullScreenWindow} takes a {@code vaadinx.awt.Window}
 * (R_no_vaadin_in_api limb 1). One instance; a browser has one viewport.
 *
 * <p>The full-screen window is held <b>per UI</b>, which is this runtime's
 * analogue of the JDK's per-{@code AppContext} scoping: two browser tabs
 * are two screens, and neither may see the other's full-screen window.
 */
public final class GraphicsDevice {

    public static final int TYPE_RASTER_SCREEN = 0;
    public static final int TYPE_PRINTER = 1;
    public static final int TYPE_IMAGE_BUFFER = 2;

    /** Kinds of per-window translucency a device may support; none are, here. */
    public enum WindowTranslucency {
        PERPIXEL_TRANSPARENT,
        TRANSLUCENT,
        PERPIXEL_TRANSLUCENT
    }

    static final GraphicsDevice INSTANCE = new GraphicsDevice();

    /** Per-UI full-screen state, keyed off this class; see the class javadoc. */
    private record FullScreenState(Window window, Rectangle windowedModeBounds) {
    }

    private GraphicsDevice() {
    }

    public int getType() {
        return TYPE_RASTER_SCREEN;
    }

    public String getIDstring() {
        return "browser-viewport";
    }

    public GraphicsConfiguration[] getConfigurations() {
        return new GraphicsConfiguration[] { GraphicsConfiguration.INSTANCE };
    }

    public GraphicsConfiguration getDefaultConfiguration() {
        return GraphicsConfiguration.INSTANCE;
    }

    /**
     * @param gct ignored — with a single configuration to choose from, the best match is it
     */
    public GraphicsConfiguration getBestConfiguration(java.awt.GraphicsConfigTemplate gct) {
        return GraphicsConfiguration.INSTANCE;
    }

    public boolean isFullScreenSupported() {
        return false;
    }

    /**
     * Enters the JDK's windowed-mode emulation of full screen for {@code w}, or leaves
     * it when {@code w} is {@code null} — restoring the bounds the window had on entry.
     *
     * @param w the window to fill the viewport with, or {@code null} to release
     */
    public void setFullScreenWindow(Window w) {
        if (w != null) {
            if (w.getShape() != null) {
                w.setShape(null);
            }
            if (w.getOpacity() < 1.0f) {
                w.setOpacity(1.0f);
            }
            if (!w.isOpaque()) {
                java.awt.Color bg = w.getBackground();
                w.setBackground(new java.awt.Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 255));
            }
        }
        FullScreenState previous = state();
        if (previous != null && previous.windowedModeBounds() != null) {
            Rectangle restore = previous.windowedModeBounds();
            // A window sent full-screen before it was realized can carry (0,0) extents,
            // which would restore to an invisible window.
            if (restore.width == 0) restore.width = 1;
            if (restore.height == 0) restore.height = 1;
            previous.window().setBounds(restore);
        }
        if (w == null) {
            state(null);
            return;
        }
        state(new FullScreenState(w, w.getBounds()));
        Rectangle screen = getDefaultConfiguration().getBounds();
        w.setBounds(screen.x, screen.y, screen.width, screen.height);
        w.setVisible(true);
        w.toFront();
    }

    /** @return this UI's full-screen window, or {@code null} if it has none */
    public Window getFullScreenWindow() {
        FullScreenState current = state();
        return current == null ? null : current.window();
    }

    public boolean isDisplayChangeSupported() {
        return false;
    }

    /**
     * @throws UnsupportedOperationException always — the browser viewport's size is the
     *         user's to change, matching a JDK device that cannot change display mode
     */
    public void setDisplayMode(DisplayMode dm) {
        throw new UnsupportedOperationException("Cannot change display mode");
    }

    /** @return the viewport extents, at the default colour model's depth and unknown refresh rate */
    public DisplayMode getDisplayMode() {
        GraphicsConfiguration gc = getDefaultConfiguration();
        Rectangle r = gc.getBounds();
        return new DisplayMode(r.width, r.height, gc.getColorModel().getPixelSize(), 0);
    }

    public DisplayMode[] getDisplayModes() {
        return new DisplayMode[] { getDisplayMode() };
    }

    /** @return always -1, the JDK's "unknown" — there is no accelerated surface to measure */
    public int getAvailableAcceleratedMemory() {
        return -1;
    }

    /**
     * @return always false — {@link Window#setShape} and {@link Window#setOpacity} have no
     *         browser counterpart, so no translucency kind is deliverable
     */
    public boolean isWindowTranslucencySupported(WindowTranslucency translucencyKind) {
        return false;
    }

    private FullScreenState state() {
        UI ui = UI.getCurrent();
        return ui == null ? null : ComponentUtil.getData(ui, FullScreenState.class);
    }

    private void state(FullScreenState value) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ComponentUtil.setData(ui, FullScreenState.class, value);
        }
    }
}
