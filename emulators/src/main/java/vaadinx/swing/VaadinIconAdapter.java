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

/**
 * {@link vaadinx.swing.Icon} implementation that wraps a Vaadin
 * {@link com.vaadin.flow.component.icon.VaadinIcon} glyph. The bridge
 * type that lets {@link UIManager#getIcon(Object)} return a Swing
 * {@code Icon} whose payload is a Vaadin icon — no PNG raster, no
 * server-side paint.
 *
 * <p>Path 2 of the icon-rendering plan: hand-picked
 * {@code UIManager.getIcon(String)} keys map to {@code VaadinIcon}
 * glyphs through this adapter, so dialog-heavy migrated code that
 * pre-fetches an icon by key can install it on the same emulators that
 * accept ImageIcon (Path 1) — JOptionPane / JLabel / JButton.
 *
 * <p>{@link #paintIcon} is intentionally a {@link vaadinx.EHelper#onUnimplemented}
 * stub: the adapter has no server-side raster to paint into a
 * {@link java.awt.Graphics}, and Path 3 (offscreen-paint of arbitrary
 * Icon impls) is permanently deferred per R_match_swing_errors sub-bucket (b). The data
 * path runs through {@link vaadinx.EHelper#toVaadinIconComponent}, which
 * recognizes this adapter and forwards a fresh
 * {@code glyph.create()} {@link com.vaadin.flow.component.icon.Icon}
 * to the peer's {@code setIcon} slot.
 *
 * <p>{@link #getIconWidth} / {@link #getIconHeight} report 16 — the
 * BasicLookAndFeel small-icon convention. The actual rendered size
 * comes from CSS on the produced Vaadin Icon component, not these
 * values.
 */
public final class VaadinIconAdapter implements vaadinx.swing.Icon {

    private final com.vaadin.flow.component.icon.VaadinIcon glyph;

    public VaadinIconAdapter(com.vaadin.flow.component.icon.VaadinIcon glyph) {
        this.glyph = java.util.Objects.requireNonNull(glyph, "glyph");
    }

    /** The wrapped Vaadin icon enum value. */
    public com.vaadin.flow.component.icon.VaadinIcon getVaadinIcon() {
        return glyph;
    }

    /**
     * Build a fresh Vaadin {@link com.vaadin.flow.component.icon.Icon}
     * component for the wrapped glyph. A new instance per call — Vaadin
     * components can only sit at one place in the DOM, so callers that
     * install on multiple peers need independent components.
     */
    public com.vaadin.flow.component.Component createComponent() {
        return glyph.create();
    }

    @Override
    public int getIconWidth() { return 16; }

    @Override
    public int getIconHeight() { return 16; }

    @Override
    public void paintIcon(vaadinx.awt.Component c, java.awt.Graphics g, int x, int y) {
        vaadinx.EHelper.onUnimplemented("VaadinIconAdapter", "paintIcon");
    }

    @Override
    public String toString() {
        return "VaadinIconAdapter[" + glyph + "]";
    }
}
