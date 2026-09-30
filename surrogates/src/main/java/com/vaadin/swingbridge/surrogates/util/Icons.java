/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates.util;

import com.vaadin.swingbridge.surrogates.SHelper;

import com.vaadin.flow.component.html.Image;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Icon → Vaadin bridge (icon-rendering Path 1). When the user's {@link Icon}
 * is an {@link ImageIcon}, extract its raster, encode as PNG, and wrap it via
 * Vaadin's byte-array {@link Image} ctor so the Swing-side icon reaches the
 * Vaadin DOM as a real {@code <img>}. Other {@code Icon} impls (custom Path 3
 * paint callbacks, Vaadin-/Lumo-glyph keys for Path 2) WARN-and-drop — Path 1
 * is the in-scope slice.
 *
 * <p>Shared across both modules in the SD_shelper_statics-allowed direction —
 * {@code :emulators} ({@code EHelper}, {@code JComboBox}) calls
 * {@link #imageIconToVaadinImage} for its own icon rendering, surrogates
 * call {@link #toVaadinIconComponent}.
 */
public final class Icons {

    private Icons() {}

    /**
     * Convert a Swing {@link Icon} to a Vaadin {@link com.vaadin.flow.component.Component}
     * suitable for {@link com.vaadin.flow.component.button.Button#setIcon(com.vaadin.flow.component.Component) Button.setIcon}.
     *
     * <ul>
     *   <li>{@code null} → {@code null} (peers interpret as "remove icon").</li>
     *   <li>{@link ImageIcon} → {@link Image} from
     *       {@link Image#Image(byte[], String)} — wraps the icon's raster
     *       PNG-encoded with an inline-disposition download handler.</li>
     *   <li>Anything else → {@link SHelper#onUnimplemented} + {@code null}: Path 2
     *       ({@code UIManager} key → {@code VaadinIcon}) and Path 3 (offscreen
     *       {@code BufferedImage} paint of arbitrary {@code Icon} impls) are
     *       deferred.</li>
     * </ul>
     *
     * <p>The {@code self} argument is the receiver instance for the WARN's
     * class name; pass the surrogate / mixin owner. Caller is responsible
     * for forwarding the result to the peer's Vaadin {@code setIcon}.
     */
    public static com.vaadin.flow.component.Component toVaadinIconComponent(Object self, Icon icon) {
        if (icon == null) return null;
        if (icon instanceof ImageIcon ii) return imageIconToVaadinImage(ii);
        SHelper.onUnimplemented(self, "setIcon", icon);
        return null;
    }

    /**
     * Encode an {@link ImageIcon}'s raster as PNG and wrap it in a Vaadin
     * {@link Image} via the {@code byte[]} ctor (auto-derives mime from
     * the {@code .png} suffix and installs an inline-disposition
     * {@code DownloadHandler} under the hood). Returns {@code null} when
     * the icon is null or has no positive dimensions (e.g. a default-
     * constructed {@code ImageIcon} that never loaded a source) — caller
     * treats that as "no icon to install."
     *
     * <p>If the icon carries a description, it overrides the default
     * filename-as-alt the ctor sets.
     */
    public static Image imageIconToVaadinImage(ImageIcon icon) {
        if (icon == null) return null;
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        if (w <= 0 || h <= 0) return null;
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics g = bi.getGraphics();
        try {
            g.drawImage(icon.getImage(), 0, 0, icon.getImageObserver());
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(bi, "png", baos);
        } catch (IOException e) {
            // ByteArrayOutputStream's IOException can't actually fire — but
            // ImageIO declares it, so wrap+rethrow rather than swallow. A
            // genuinely broken encoder would surface here as runtime, which
            // is honest signal for the caller.
            throw new UncheckedIOException(e);
        }
        Image image = new Image(baos.toByteArray(), "icon.png");
        if (icon.getDescription() != null) image.setAlt(icon.getDescription());
        return image;
    }
}
