/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.GraphicsConfiguration
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.VolatileImage;
import java.awt.image.WritableRaster;

import vaadinx.EHelper;

/**
 * Import-swap target for {@link java.awt.GraphicsConfiguration} — the browser
 * viewport's single display configuration (D_graphics_environment). Reached the
 * JDK way, through the environment and its default device:
 *
 * <pre>{@code
 * GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
 *         .getDefaultScreenDevice().getDefaultConfiguration();
 * Rectangle screen = gc.getBounds();   // (0, 0, viewportWidth, viewportHeight)
 * }</pre>
 *
 * <p>Like {@link Toolkit} this does <em>not</em> extend the abstract JDK class —
 * it is the import-swap surface, not a platform configuration — so
 * {@link #getDevice} returns the {@code vaadinx} device rather than the JDK's
 * (R_no_vaadin_in_api limb 1). There is exactly one instance,
 * {@link GraphicsDevice#getDefaultConfiguration()}: a browser has one viewport.
 *
 * <p>Bounds track the live viewport through {@link BrowserToolkitInfo}, so a
 * value read before a browser resize goes stale — re-read rather than caching.
 *
 * <p>The image-creation and capability bodies are the JDK's own, which
 * are pure raster maths and run correctly headless. Only the
 * {@code VolatileImage} family is dropped: it needs a real accelerated
 * surface, which a server JVM has not got.
 */
public final class GraphicsConfiguration {

    static final GraphicsConfiguration INSTANCE = new GraphicsConfiguration();

    private GraphicsConfiguration() {
    }

    public GraphicsDevice getDevice() {
        return GraphicsDevice.INSTANCE;
    }

    /** The viewport rectangle, always origin-anchored — a browser has no multi-monitor offset. */
    public Rectangle getBounds() {
        BrowserToolkitInfo info = BrowserToolkitInfo.get();
        return new Rectangle(0, 0, info.viewportWidth(), info.viewportHeight());
    }

    public ColorModel getColorModel() {
        return ColorModel.getRGBdefault();
    }

    /**
     * @return the model for {@code transparency}, or {@code null} if it is not one of
     *         {@link Transparency}'s three constants — the contract
     *         {@link #createCompatibleImage(int, int, int)} turns into an
     *         {@code IllegalArgumentException}
     */
    public ColorModel getColorModel(int transparency) {
        return switch (transparency) {
            case Transparency.OPAQUE -> new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
            case Transparency.BITMASK, Transparency.TRANSLUCENT -> ColorModel.getRGBdefault();
            default -> null;
        };
    }

    public AffineTransform getDefaultTransform() {
        return new AffineTransform();
    }

    /**
     * The user-space-to-72dpi transform, scaled by the browser's reported resolution
     * — so on a HiDPI client this is a {@code devicePixelRatio} scale, not the identity.
     */
    public AffineTransform getNormalizingTransform() {
        double scale = Toolkit.getDefaultToolkit().getScreenResolution() / 72.0;
        return AffineTransform.getScaleInstance(scale, scale);
    }

    public BufferedImage createCompatibleImage(int width, int height) {
        ColorModel model = getColorModel();
        WritableRaster raster = model.createCompatibleWritableRaster(width, height);
        return new BufferedImage(model, raster, model.isAlphaPremultiplied(), null);
    }

    /**
     * @throws IllegalArgumentException if {@code transparency} is not a {@link Transparency} constant
     */
    public BufferedImage createCompatibleImage(int width, int height, int transparency) {
        if (getColorModel().getTransparency() == transparency) {
            return createCompatibleImage(width, height);
        }
        ColorModel cm = getColorModel(transparency);
        if (cm == null) {
            throw new IllegalArgumentException("Unknown transparency: " + transparency);
        }
        WritableRaster wr = cm.createCompatibleWritableRaster(width, height);
        return new BufferedImage(cm, wr, cm.isAlphaPremultiplied(), null);
    }

    /** @return always {@code null} — a server JVM has no accelerated surface to back one. */
    public VolatileImage createCompatibleVolatileImage(int width, int height) {
        EHelper.onUnimplemented("GraphicsConfiguration", "createCompatibleVolatileImage", width, height);
        return null;
    }

    /** @return always {@code null} — see {@link #createCompatibleVolatileImage(int, int)}. */
    public VolatileImage createCompatibleVolatileImage(int width, int height, int transparency) {
        EHelper.onUnimplemented("GraphicsConfiguration", "createCompatibleVolatileImage",
                width, height, transparency);
        return null;
    }

    /** @return always {@code null} — see {@link #createCompatibleVolatileImage(int, int)}. */
    public VolatileImage createCompatibleVolatileImage(int width, int height,
                                                       java.awt.ImageCapabilities caps) {
        EHelper.onUnimplemented("GraphicsConfiguration", "createCompatibleVolatileImage",
                width, height, caps);
        return null;
    }

    /** @return always {@code null} — see {@link #createCompatibleVolatileImage(int, int)}. */
    public VolatileImage createCompatibleVolatileImage(int width, int height,
                                                       java.awt.ImageCapabilities caps,
                                                       int transparency) {
        EHelper.onUnimplemented("GraphicsConfiguration", "createCompatibleVolatileImage",
                width, height, caps, transparency);
        return null;
    }

    public java.awt.ImageCapabilities getImageCapabilities() {
        return new java.awt.ImageCapabilities(false);
    }

    public java.awt.BufferCapabilities getBufferCapabilities() {
        java.awt.ImageCapabilities caps = getImageCapabilities();
        return new java.awt.BufferCapabilities(caps, caps, null);
    }

    public boolean isTranslucencyCapable() {
        return false;
    }
}
