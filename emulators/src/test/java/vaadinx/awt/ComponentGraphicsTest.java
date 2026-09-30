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

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentGraphicsTest extends AbstractKaribuTest {

    private Component newComponent() {
        return new Component(new Div()) {
        };
    }

    @Test
    @DisplayName("getToolkit returns the vaadinx emulator toolkit, not the headless java.awt one")
    void getToolkitReturnsTheVaadinxEmulatorToolkitNotTheHeadlessJavaAwtOne() {
        Component c = newComponent();
        assertNotNull(c.getToolkit());
        // D_toolkit_full_surface: component.getToolkit() returns the vaadinx full-surface emulator
        // (global singleton), so component.getToolkit().getScreenSize() works in
        // the browser instead of throwing HeadlessException.
        assertInstanceOf(Toolkit.class, c.getToolkit());
        assertEquals(Toolkit.getDefaultToolkit(), c.getToolkit());
    }

    @Test
    @DisplayName("getColorModel returns default ARGB")
    void getColorModelReturnsDefaultArgb() {
        Component c = newComponent();
        assertEquals(java.awt.image.ColorModel.getRGBdefault(), c.getColorModel());
    }

    @Test
    @DisplayName("getGraphics returns a working Graphics2D")
    void getGraphicsReturnsAWorkingGraphics2D() {
        Component c = newComponent();
        Graphics g = c.getGraphics();
        assertNotNull(g);
        assertInstanceOf(Graphics2D.class, g);

        // Exercise a handful of methods — they should run without NPE.
        g.setColor(Color.RED);
        g.drawRect(0, 0, 10, 10);
        g.fillRect(0, 0, 10, 10);
        assertNotNull(g.getFontMetrics());
        g.dispose();
    }

    @Test
    @DisplayName("createImage returns a real BufferedImage of the requested size")
    void createImageReturnsARealBufferedImageOfTheRequestedSize() {
        Component c = newComponent();
        Image img = c.createImage(40, 30);
        assertInstanceOf(BufferedImage.class, img);
        assertEquals(40, img.getWidth(null));
        assertEquals(30, img.getHeight(null));
    }

    @Test
    @DisplayName("createImage clamps non-positive sizes to 1")
    void createImageClampsNonPositiveSizesTo1() {
        Component c = newComponent();
        Image img = c.createImage(0, 0);
        assertEquals(1, img.getWidth(null));
        assertEquals(1, img.getHeight(null));
    }

    @Test
    @DisplayName("createImage with ImageProducer is unsupported and returns null")
    void createImageWithImageProducerIsUnsupportedAndReturnsNull() {
        Component c = newComponent();
        assertNull(c.createImage((ImageProducer) null));
    }

    @Test
    @DisplayName("createVolatileImage is unsupported and returns null")
    void createVolatileImageIsUnsupportedAndReturnsNull() throws AWTException {
        Component c = newComponent();
        assertNull(c.createVolatileImage(10, 10));
        assertNull(c.createVolatileImage(10, 10, null));
    }

    @Test
    @DisplayName("prepareImage always reports ready")
    void prepareImageAlwaysReportsReady() {
        Component c = newComponent();
        assertTrue(c.prepareImage(null, null));
        assertTrue(c.prepareImage(null, 10, 10, null));
    }

    @Test
    @DisplayName("checkImage reports ALLBITS")
    void checkImageReportsAllbits() {
        Component c = newComponent();
        assertEquals(ImageObserver.ALLBITS, c.checkImage(null, null));
        assertEquals(ImageObserver.ALLBITS, c.checkImage(null, 10, 10, null));
    }

    @Test
    @DisplayName("imageUpdate stops the observer by returning false")
    void imageUpdateStopsTheObserverByReturningFalse() {
        Component c = newComponent();
        assertFalse(c.imageUpdate(null, 0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("getGraphicsConfiguration is the viewport's, never null")
    void getGraphicsConfigurationIsTheViewports() {
        Component c = newComponent();
        assertSame(GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration(),
                c.getGraphicsConfiguration());
    }

    @Test
    @DisplayName("getFontMetrics returns real metrics for a non-null font")
    void getFontMetricsReturnsRealMetricsForANonNullFont() {
        Component c = newComponent();
        FontMetrics fm = c.getFontMetrics(new Font("Dialog", Font.PLAIN, 12));
        assertNotNull(fm);
        assertTrue(fm.stringWidth("hello") > 0, "real metrics return a non-zero width");
    }

    @Test
    @DisplayName("getFontMetrics null-safe for the smoke probe")
    void getFontMetricsNullSafeForTheSmokeProbe() {
        Component c = newComponent();
        assertNull(c.getFontMetrics(null));
    }
}
