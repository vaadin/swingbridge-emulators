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

import java.awt.DisplayMode;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D_graphics_environment — the viewport-as-display trio. */
class GraphicsEnvironmentTest extends AbstractKaribuTest {

    private final java.util.List<String> warns = new java.util.ArrayList<>();

    @BeforeEach
    void seedViewportAndWarnHook() {
        BrowserToolkitInfo.setTestInfo(1200, 800, 1.0, false);
        warns.clear();
        EHelper.warnHook = warns::add;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> {
        };
        BrowserToolkitInfo.clearCache();
    }

    private GraphicsEnvironment ge() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment();
    }

    @Nested
    @DisplayName("GraphicsEnvironment")
    class Environment {

        @Test
        @DisplayName("getLocalGraphicsEnvironment is a singleton")
        void singleton() {
            assertSame(GraphicsEnvironment.getLocalGraphicsEnvironment(),
                    GraphicsEnvironment.getLocalGraphicsEnvironment());
        }

        @Test
        @DisplayName("isHeadless is false — the JDK's true silently disables a migrated app's UI branch")
        void notHeadless() {
            assertFalse(GraphicsEnvironment.isHeadless());
            assertFalse(ge().isHeadlessInstance());
            assertTrue(java.awt.GraphicsEnvironment.isHeadless(),
                    "the JDK's answer under test is the one we deliberately diverge from");
        }

        @Test
        @DisplayName("getMaximumWindowBounds is the viewport, origin-anchored")
        void maximumWindowBounds() {
            assertEquals(new Rectangle(0, 0, 1200, 800), ge().getMaximumWindowBounds());
        }

        @Test
        @DisplayName("getMaximumWindowBounds tracks a viewport change")
        void maximumWindowBoundsTracksViewport() {
            BrowserToolkitInfo.setTestInfo(640, 480, 1.0, false);
            assertEquals(new Rectangle(0, 0, 640, 480), ge().getMaximumWindowBounds());
        }

        @Test
        @DisplayName("getCenterPoint halves the usable bounds")
        void centerPoint() {
            assertEquals(new Point(600, 400), ge().getCenterPoint());
        }

        @Test
        @DisplayName("one screen device, and it is the default")
        void screenDevices() {
            assertEquals(1, ge().getScreenDevices().length);
            assertSame(ge().getDefaultScreenDevice(), ge().getScreenDevices()[0]);
        }

        @Test
        @DisplayName("font queries delegate to the headless JDK, matching what FontMetrics measures")
        void fontsDelegate() {
            java.awt.GraphicsEnvironment jdk = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            assertArrayEquals(jdk.getAvailableFontFamilyNames(), ge().getAvailableFontFamilyNames());
            assertArrayEquals(jdk.getAvailableFontFamilyNames(Locale.FRENCH),
                    ge().getAvailableFontFamilyNames(Locale.FRENCH));
            assertEquals(jdk.getAllFonts().length, ge().getAllFonts().length);
        }

        @Test
        @DisplayName("createGraphics returns a real Graphics2D that draws")
        void createGraphics() {
            BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = ge().createGraphics(img);
            assertNotNull(g);
            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, 8, 8);
            g.dispose();
            assertEquals(java.awt.Color.RED.getRGB(), img.getRGB(4, 4));
        }

        @Test
        @DisplayName("registerFont keeps the JDK's null contract")
        void registerFontNull() {
            assertThrows(NullPointerException.class, () -> ge().registerFont(null));
        }

        @Test
        @DisplayName("no method WARNs — every one has a real answer")
        void noWarns() {
            ge().getMaximumWindowBounds();
            ge().getCenterPoint();
            ge().getScreenDevices();
            ge().getDefaultScreenDevice();
            ge().getAvailableFontFamilyNames();
            ge().preferLocaleFonts();
            ge().preferProportionalFonts();
            assertTrue(warns.isEmpty(), () -> "unexpected WARNs: " + warns);
        }
    }

    @Nested
    @DisplayName("GraphicsDevice")
    class Device {

        private GraphicsDevice gd() {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        }

        @Test
        @DisplayName("is a raster screen with one configuration")
        void shape() {
            assertEquals(GraphicsDevice.TYPE_RASTER_SCREEN, gd().getType());
            assertEquals(1, gd().getConfigurations().length);
            assertSame(gd().getDefaultConfiguration(), gd().getConfigurations()[0]);
            assertSame(gd().getDefaultConfiguration(), gd().getBestConfiguration(null));
        }

        @Test
        @DisplayName("getDisplayMode reports the viewport at the default colour depth")
        void displayMode() {
            DisplayMode dm = gd().getDisplayMode();
            assertEquals(1200, dm.getWidth());
            assertEquals(800, dm.getHeight());
            assertEquals(32, dm.getBitDepth());
            assertArrayEquals(new DisplayMode[] { dm }, gd().getDisplayModes());
        }

        @Test
        @DisplayName("display change is unsupported, and setDisplayMode throws as the JDK's does")
        void displayChange() {
            assertFalse(gd().isDisplayChangeSupported());
            assertThrows(UnsupportedOperationException.class,
                    () -> gd().setDisplayMode(gd().getDisplayMode()));
        }

        @Test
        @DisplayName("no translucency kind is supported")
        void translucency() {
            for (GraphicsDevice.WindowTranslucency kind : GraphicsDevice.WindowTranslucency.values()) {
                assertFalse(gd().isWindowTranslucencySupported(kind), kind.name());
            }
        }

        @Test
        @DisplayName("setFullScreenWindow fills the viewport, and releasing restores the old bounds")
        void fullScreenRoundTrip() {
            Window w = new Window((Window) null);
            w.setBounds(10, 20, 300, 200);
            assertNull(gd().getFullScreenWindow());

            gd().setFullScreenWindow(w);
            assertSame(w, gd().getFullScreenWindow());
            assertEquals(new Rectangle(0, 0, 1200, 800), w.getBounds());
            assertTrue(w.isVisible());

            gd().setFullScreenWindow(null);
            assertNull(gd().getFullScreenWindow());
            assertEquals(new Rectangle(10, 20, 300, 200), w.getBounds());
        }

        @Test
        @DisplayName("full-screen is windowed-mode emulation, so it never WARNs")
        void fullScreenIsNotAStub() {
            assertFalse(gd().isFullScreenSupported());
            Window w = new Window((Window) null);
            // Bounds first: an unpositioned Component.getBounds() is its own dummy-bounds
            // WARN, which would mask what this test is actually about.
            w.setBounds(0, 0, 100, 100);
            gd().setFullScreenWindow(w);
            gd().setFullScreenWindow(null);
            assertTrue(warns.isEmpty(), () -> "unexpected WARNs: " + warns);
        }

        @Test
        @DisplayName("accelerated memory is the JDK's -1 'unknown', not 0")
        void acceleratedMemory() {
            assertEquals(-1, gd().getAvailableAcceleratedMemory());
        }
    }

    @Nested
    @DisplayName("GraphicsConfiguration")
    class Configuration {

        private GraphicsConfiguration gc() {
            return GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
        }

        @Test
        @DisplayName("bounds are the viewport and getDevice round-trips")
        void boundsAndDevice() {
            assertEquals(new Rectangle(0, 0, 1200, 800), gc().getBounds());
            assertSame(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice(),
                    gc().getDevice());
        }

        @Test
        @DisplayName("createCompatibleImage produces a usable image at both arities")
        void compatibleImage() {
            BufferedImage a = gc().createCompatibleImage(4, 6);
            assertEquals(4, a.getWidth());
            assertEquals(6, a.getHeight());
            BufferedImage b = gc().createCompatibleImage(4, 6, Transparency.OPAQUE);
            assertEquals(Transparency.OPAQUE, b.getColorModel().getTransparency());
        }

        @Test
        @DisplayName("an unknown transparency is the JDK's IllegalArgumentException")
        void unknownTransparency() {
            assertThrows(IllegalArgumentException.class, () -> gc().createCompatibleImage(4, 4, 99));
        }

        @Test
        @DisplayName("normalizing transform scales by the browser's reported resolution")
        void normalizingTransform() {
            BrowserToolkitInfo.setTestInfo(1200, 800, 2.0, false);
            assertEquals(192 / 72.0, gc().getNormalizingTransform().getScaleX(), 1e-9);
            assertEquals(1.0, gc().getDefaultTransform().getScaleX(), 1e-9);
        }

        @Test
        @DisplayName("VolatileImage is the one dropped family, and it WARNs")
        void volatileImageWarns() {
            assertNull(gc().createCompatibleVolatileImage(4, 4));
            assertEquals(1, warns.size(), () -> String.valueOf(warns));
        }
    }

    @Nested
    @DisplayName("wiring")
    class Wiring {

        @Test
        @DisplayName("Component.getGraphicsConfiguration reaches the same configuration")
        void componentConfiguration() {
            Window w = new Window((Window) null);
            assertSame(GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getDefaultScreenDevice().getDefaultConfiguration(),
                    w.getGraphicsConfiguration());
            assertTrue(warns.isEmpty(), () -> "unexpected WARNs: " + warns);
        }

        @Test
        @DisplayName("Toolkit.getScreenInsets accepts the ported configuration")
        void toolkitTakesPortedConfiguration() {
            assertEquals(new java.awt.Insets(0, 0, 0, 0),
                    Toolkit.getDefaultToolkit().getScreenInsets(
                            GraphicsEnvironment.getLocalGraphicsEnvironment()
                                    .getDefaultScreenDevice().getDefaultConfiguration()));
        }

        @Test
        @DisplayName("the maximize-to-screen idiom the inventory probe hit ports verbatim")
        void maximizeIdiom() {
            vaadinx.swing.JFrame frame = new vaadinx.swing.JFrame();
            GraphicsEnvironment e = GraphicsEnvironment.getLocalGraphicsEnvironment();
            frame.setMaximizedBounds(e.getMaximumWindowBounds());
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
            assertEquals(new Rectangle(0, 0, 1200, 800), frame.getMaximizedBounds());
        }
    }
}
