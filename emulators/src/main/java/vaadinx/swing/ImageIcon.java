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
 * This file is derived from OpenJDK's javax.swing.ImageIcon
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
 * Emulator for {@link javax.swing.ImageIcon}. Holds a JDK-side
 * {@link javax.swing.ImageIcon} delegate so all the load-from-file /
 * load-from-bytes / load-from-URL / load-from-Image construction shapes
 * Just Work, and so {@link #getImage()} hands back a real
 * {@link java.awt.Image} the icon-rendering bridge can encode.
 *
 * <p>Implements {@link vaadinx.swing.Icon} (paintIcon's signature
 * references {@link vaadinx.awt.Component}, which is why we can't reuse
 * {@code javax.swing.ImageIcon} directly under D_whitelist_porting's port rule). The
 * paintIcon body itself is a {@link vaadinx.EHelper#onUnimplemented} stub
 * — Path 3 (offscreen-paint of arbitrary {@code Icon} impls) is deferred;
 * ImageIcon's data path is Path 1 and
 * lives in the icon-rendering bridge, not in this class.
 *
 * <p>Construction shapes match {@code javax.swing.ImageIcon} verbatim,
 * so post-import-swap user code (e.g. {@code new ImageIcon("logo.png")})
 * compiles unchanged.
 */
public class ImageIcon implements vaadinx.swing.Icon, java.io.Serializable, javax.accessibility.Accessible {

    private final javax.swing.ImageIcon delegate;

    public ImageIcon() {
        this.delegate = new javax.swing.ImageIcon();
    }

    public ImageIcon(byte[] imageData) {
        this.delegate = new javax.swing.ImageIcon(imageData);
    }

    public ImageIcon(byte[] imageData, java.lang.String description) {
        this.delegate = new javax.swing.ImageIcon(imageData, description);
    }

    public ImageIcon(java.awt.Image image) {
        this.delegate = new javax.swing.ImageIcon(image);
    }

    public ImageIcon(java.awt.Image image, java.lang.String description) {
        this.delegate = new javax.swing.ImageIcon(image, description);
    }

    public ImageIcon(java.lang.String filename) {
        this.delegate = new javax.swing.ImageIcon(filename);
    }

    public ImageIcon(java.lang.String filename, java.lang.String description) {
        this.delegate = new javax.swing.ImageIcon(filename, description);
    }

    public ImageIcon(java.net.URL location) {
        this.delegate = new javax.swing.ImageIcon(location);
    }

    public ImageIcon(java.net.URL location, java.lang.String description) {
        this.delegate = new javax.swing.ImageIcon(location, description);
    }

    /**
     * Adapter ctor: wrap an existing {@link javax.swing.ImageIcon}. Used
     * by the icon-rendering bridge to forward the raster without
     * re-loading from source.
     */
    public ImageIcon(javax.swing.ImageIcon delegate) {
        this.delegate = delegate != null ? delegate : new javax.swing.ImageIcon();
    }

    public java.awt.Image getImage() {
        return delegate.getImage();
    }

    public void setImage(java.awt.Image image) {
        delegate.setImage(image);
    }

    public java.lang.String getDescription() {
        return delegate.getDescription();
    }

    public void setDescription(java.lang.String description) {
        delegate.setDescription(description);
    }

    public int getImageLoadStatus() {
        return delegate.getImageLoadStatus();
    }

    public java.awt.image.ImageObserver getImageObserver() {
        return delegate.getImageObserver();
    }

    public void setImageObserver(java.awt.image.ImageObserver observer) {
        delegate.setImageObserver(observer);
    }

    @Override
    public int getIconWidth() {
        return delegate.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return delegate.getIconHeight();
    }

    @Override
    public void paintIcon(vaadinx.awt.Component c, java.awt.Graphics g, int x, int y) {
        // Path 3 territory: arbitrary paint to a server-side Graphics has
        // no Vaadin counterpart. The data path for ImageIcon goes through
        // the icon-rendering bridge in vaadinx.EHelper, which encodes the
        // raster as PNG and pushes a Vaadin Image to the peer.
        vaadinx.EHelper.onUnimplemented("ImageIcon", "paintIcon");
    }

    /**
     * Accessor for the underlying JDK {@link javax.swing.ImageIcon}. The
     * icon-rendering bridge in {@link vaadinx.EHelper} uses this to hand
     * the raw raster to the surrogate-layer encoder; user code can also
     * call it when migrating away from the {@code vaadinx.swing.ImageIcon}
     * port back to JDK types.
     */
    public javax.swing.ImageIcon asJdk() {
        return delegate;
    }

    @Override
    public java.lang.String toString() {
        return delegate.toString();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("ImageIcon", "getAccessibleContext");
        return null;
    }
}
