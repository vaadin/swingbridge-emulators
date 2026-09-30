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

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.datatransfer.WebClipboard;

import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.im.InputMethodHighlight;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.MemoryImageSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Karibu unit tests for the full-surface {@link Toolkit} emulator (D_toolkit_full_surface). The
 * browser-backed screen accessors are driven through {@link BrowserToolkitInfo}'s
 * test-injection seam; the real {@code executeJs} VT-park round-trip is not
 * unit-testable without a browser (deferred e2e, as with WebClipboard).
 */
class ToolkitTest extends AbstractKaribuTest {

    private final java.util.List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        EHelper.warnHook = capturedWarns::add;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> {
        };
    }

    private Toolkit tk() {
        return Toolkit.getDefaultToolkit();
    }

    /**
     * Establishes the "browser has not reported in" precondition the DEFAULT-fallback
     * tests assert against, instead of inheriting it from the fixture. Karibu fakes
     * {@link com.vaadin.flow.component.page.ExtendedClientDetails} whenever something fetches
     * them, so whether they are populated depends on what else the session wired — which
     * makes these tests read the <em>populated</em> branch and assert real viewport pixels.
     */
    private void withoutBrowserDetails() {
        BrowserToolkitInfo.clearCache();
        UI.getCurrent().getInternals().setExtendedClientDetails(null);
    }

    // --- Singleton + clipboard ------------------------------------------

    @Test
    @DisplayName("getDefaultToolkit is a global singleton across calls and UIs")
    void getDefaultToolkitIsAGlobalSingletonAcrossCallsAndUIs() {
        Toolkit a = Toolkit.getDefaultToolkit();
        Toolkit b = Toolkit.getDefaultToolkit();
        assertSame(a, b);
        // Global, not per-UI: survives a UI teardown + re-setup.
        MockVaadin.tearDown();
        setupKaribu();
        assertSame(a, Toolkit.getDefaultToolkit());
    }

    @Test
    @DisplayName("getSystemClipboard returns the per-UI WebClipboard")
    void getSystemClipboardReturnsThePerUiWebClipboard() {
        java.awt.datatransfer.Clipboard cb = tk().getSystemClipboard();
        assertInstanceOf(WebClipboard.class, cb);
        assertSame(cb, tk().getSystemClipboard());
    }

    @Test
    @DisplayName("getSystemClipboard with no UI throws")
    void getSystemClipboardWithNoUiThrows() {
        MockVaadin.tearDown();
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> tk().getSystemClipboard());
            assertTrue(ex.getMessage().contains("no current UI"));
        } finally {
            setupKaribu();
        }
    }

    @Test
    @DisplayName("component getToolkit returns the singleton")
    void componentGetToolkitReturnsTheSingleton() {
        Component c = new Component(new Div()) {
        };
        assertSame(Toolkit.getDefaultToolkit(), c.getToolkit());
    }

    // --- Bucket A: delegate to headless JDK / faithful, no WARN ----------

    @Test
    @DisplayName("getFontMetrics returns real metrics")
    void getFontMetricsReturnsRealMetrics() {
        FontMetrics fm = tk().getFontMetrics(new Font("SansSerif", Font.PLAIN, 12));
        assertNotNull(fm);
        assertTrue(fm.getHeight() > 0);
        assertTrue(fm.stringWidth("Hello") > 0);
        assertNoWarns(capturedWarns, "getFontMetrics must not WARN; got " + capturedWarns);
    }

    @Test
    @DisplayName("getFontList is non-empty")
    void getFontListIsNonEmpty() {
        assertTrue(tk().getFontList().length > 0);
    }

    @Test
    @DisplayName("faithful constants, no WARN")
    void faithfulConstantsNoWarn() {
        assertEquals(ColorModel.getRGBdefault(), tk().getColorModel());
        assertNull(tk().getSystemSelection());
        assertEquals(0, tk().getMaximumCursorColors());
        assertEquals(new java.awt.Dimension(0, 0), tk().getBestCursorSize(16, 16));
        // isAlwaysOnTopSupported delegates to the platform AWT toolkit, so its
        // value is environment-dependent (true on most desktops, false when
        // headless). Assert faithful delegation rather than pinning a value.
        assertEquals(java.awt.Toolkit.getDefaultToolkit().isAlwaysOnTopSupported(),
                tk().isAlwaysOnTopSupported());
        assertEquals("fallback", Toolkit.getProperty("no.such.awt.key", "fallback"));
        assertNotNull(tk().getSystemEventQueue());
        assertNoWarns(capturedWarns, "faithful accessors must not WARN; got " + capturedWarns);
    }

    // --- Bucket D: fabricated capability flags --------------------------

    @Test
    @DisplayName("capability flags")
    void capabilityFlags() {
        // The ported ModalityType, not java.awt's — R_no_vaadin_in_api limb 1: the JDK's own
        // enum is unproducible from import-swapped code.
        assertTrue(tk().isModalityTypeSupported(Dialog.ModalityType.APPLICATION_MODAL),
                "modal dialogs are supported via UI fibers");
        assertFalse(tk().isFrameStateSupported(java.awt.Frame.MAXIMIZED_BOTH));
        // Stays java.awt's: SB-Emulators ports no ModalExclusionType, so there is
        // nothing for limb 1 to swap it for.
        assertFalse(tk().isModalExclusionTypeSupported(
                java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE));
        assertFalse(tk().isDynamicLayoutActive());
        assertTrue(tk().areExtraMouseButtonsEnabled());
    }

    // --- Bucket C: throw, faithful to JDK contract ----------------------

    @Test
    @DisplayName("locking key state throws UnsupportedOperationException")
    void lockingKeyStateThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> tk().getLockingKeyState(KeyEvent.VK_CAPS_LOCK));
        assertThrows(UnsupportedOperationException.class,
                () -> tk().setLockingKeyState(KeyEvent.VK_CAPS_LOCK, true));
    }

    // --- Browser-backed via BrowserToolkitInfo test seam ----------------

    @Test
    @DisplayName("screen accessors read injected browser info")
    void screenAccessorsReadInjectedBrowserInfo() {
        // The injected pair is the VIEWPORT (window.innerWidth/Height) per
        // D_toolkit_screen_size — getScreenSize reports the placement coordinate space, not
        // the physical monitor.
        BrowserToolkitInfo.setTestInfo(2560, 1440, 2.0, true); // mac/iOS
        assertEquals(new java.awt.Dimension(2560, 1440), tk().getScreenSize());
        assertEquals(192, tk().getScreenResolution()); // 96 * 2.0
        assertEquals(InputEvent.META_DOWN_MASK, tk().getMenuShortcutKeyMaskEx()); // Mac
        assertEquals(new java.awt.Insets(0, 0, 0, 0), tk().getScreenInsets(null));
    }

    @Test
    @DisplayName("non-Mac platform uses CTRL menu shortcut")
    void nonMacPlatformUsesCtrlMenuShortcut() {
        BrowserToolkitInfo.setTestInfo(1920, 1080, 1.0, false); // non-Mac
        assertEquals(InputEvent.CTRL_DOWN_MASK, tk().getMenuShortcutKeyMaskEx());
    }

    /** Menu-shortcut mask for a browser reporting {@code platform} as {@code navigator.platform}. */
    private int menuShortcutMaskFor(String platform, boolean touch) {
        UI ui = UI.getCurrent();
        ui.getInternals().setExtendedClientDetails(new ExtendedClientDetails(ui,
                "1920", "1080", "1920", "1080", "1920", "1080",
                "0", "0", "0", "false", "UTC", null, Boolean.toString(touch),
                "1.0", "w", platform, null, null));
        BrowserToolkitInfo.clearCache();
        return tk().getMenuShortcutKeyMaskEx();
    }

    @Test
    @DisplayName("iOS and iPadOS platforms use META, as Vaadin's isIOS() classified them")
    void appleMobilePlatformsUseMeta() {
        assertEquals(InputEvent.META_DOWN_MASK, menuShortcutMaskFor("iPhone", true));
        assertEquals(InputEvent.META_DOWN_MASK, menuShortcutMaskFor("iPod touch", true));
        assertEquals(InputEvent.META_DOWN_MASK, menuShortcutMaskFor("iPad", true));
        // iPadOS Safari reports a desktop Mac; the touch screen gives it away.
        assertEquals(InputEvent.META_DOWN_MASK, menuShortcutMaskFor("MacIntel", true));
    }

    @Test
    @DisplayName("desktop Mac and other platforms use CTRL")
    void desktopPlatformsUseCtrl() {
        assertEquals(InputEvent.CTRL_DOWN_MASK, menuShortcutMaskFor("MacIntel", false));
        assertEquals(InputEvent.CTRL_DOWN_MASK, menuShortcutMaskFor("Linux x86_64", false));
        assertEquals(InputEvent.CTRL_DOWN_MASK, menuShortcutMaskFor("Win32", true));
        assertEquals(InputEvent.CTRL_DOWN_MASK, menuShortcutMaskFor(null, false));
    }

    @Test
    @DisplayName("browser resize updates the cached viewport, preserving dpr and mac")
    void browserResizeUpdatesTheCachedViewportPreservingDprAndMac() {
        BrowserToolkitInfo.setTestInfo(2560, 1440, 2.0, true);
        BrowserToolkitInfo.onBrowserResize(UI.getCurrent(), 1000, 700);
        assertEquals(new java.awt.Dimension(1000, 700), tk().getScreenSize());
        assertEquals(192, tk().getScreenResolution()); // dpr 2.0 carried over
        assertEquals(InputEvent.META_DOWN_MASK, tk().getMenuShortcutKeyMaskEx()); // mac carried over
    }

    @Test
    @DisplayName("browser resize with no cached snapshot seeds viewport plus DEFAULT dpr and mac")
    void browserResizeWithNoCachedSnapshotSeedsViewportPlusDefaultDprAndMac() {
        // No setTestInfo and no browser details, so dpr/mac fall to DEFAULT's —
        // the resize event's size still wins.
        withoutBrowserDetails();
        BrowserToolkitInfo.onBrowserResize(UI.getCurrent(), 800, 600);
        assertEquals(new java.awt.Dimension(800, 600), tk().getScreenSize());
        assertEquals(96, tk().getScreenResolution()); // DEFAULT dpr 1.0
        assertEquals(InputEvent.CTRL_DOWN_MASK, tk().getMenuShortcutKeyMaskEx()); // DEFAULT non-Mac
        assertNoWarns(capturedWarns, "resize seeding must stay WARN-free; got " + capturedWarns);
    }

    @Test
    @DisplayName("uncached off-VT screen accessors return the default, no WARN")
    void uncachedOffVtScreenAccessorsReturnTheDefaultNoWarn() {
        // No setTestInfo, no browser details, and the test thread is a platform
        // thread (not a VT carrier), so BrowserToolkitInfo cannot fetch — falls
        // back to DEFAULT.
        withoutBrowserDetails();
        assertEquals(new java.awt.Dimension(1920, 1080), tk().getScreenSize());
        assertEquals(InputEvent.CTRL_DOWN_MASK, tk().getMenuShortcutKeyMaskEx());
        assertNoWarns(capturedWarns,
                "fallback is a debug-level degrade, not an onUnimplemented WARN; got " + capturedWarns);
    }

    // --- Bucket A images via ImageIO ------------------------------------

    @Test
    @DisplayName("createImage decodes real PNG bytes")
    void createImageDecodesRealPngBytes() throws IOException {
        byte[] png;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", baos);
            png = baos.toByteArray();
        }
        Image img = tk().createImage(png);
        assertNotNull(img);
        assertEquals(4, img.getWidth(null));
    }

    @Test
    @DisplayName("createImage on garbage bytes returns null without throwing")
    void createImageOnGarbageBytesReturnsNullWithoutThrowing() {
        assertNull(tk().createImage(new byte[]{1, 2, 3, 4}));
    }

    @Test
    @DisplayName("prepareImage and checkImage report loaded")
    void prepareImageAndCheckImageReportLoaded() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        assertTrue(tk().prepareImage(img, 2, 2, null));
        assertEquals(ImageObserver.ALLBITS, tk().checkImage(img, 2, 2, null));
    }

    @Test
    @DisplayName("createImage from ImageProducer WARNs and returns null")
    void createImageFromImageProducerWarnsAndReturnsNull() {
        MemoryImageSource src = new MemoryImageSource(1, 1, new int[1], 0, 1);
        assertNull(tk().createImage(src));
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("createImage(ImageProducer)")),
                "got " + capturedWarns);
    }

    // --- Bucket E: WARN + ignore ----------------------------------------

    @Test
    @DisplayName("beep WARNs and does nothing")
    void beepWarnsAndDoesNothing() {
        tk().beep();
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("beep")), "got " + capturedWarns);
    }

    @Test
    @DisplayName("sync is a silent no-op")
    void syncIsASilentNoOp() {
        tk().sync();
        assertNoWarns(capturedWarns, "sync must not WARN; got " + capturedWarns);
    }

    @Test
    @DisplayName("getPrintJob returns null and WARNs")
    void getPrintJobReturnsNullAndWarns() {
        assertNull(tk().getPrintJob(null, "job", new Properties()));
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("getPrintJob")), "got " + capturedWarns);
    }

    @Test
    @DisplayName("createCustomCursor returns default cursor and WARNs")
    void createCustomCursorReturnsDefaultCursorAndWarns() {
        Cursor cur = tk().createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "x");
        assertEquals(Cursor.getDefaultCursor(), cur);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("createCustomCursor")),
                "got " + capturedWarns);
    }

    @Test
    @DisplayName("mapInputMethodHighlight returns null and WARNs")
    void mapInputMethodHighlightReturnsNullAndWarns() {
        assertNull(tk().mapInputMethodHighlight(
                new InputMethodHighlight(true, InputMethodHighlight.RAW_TEXT)));
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("mapInputMethodHighlight")),
                "got " + capturedWarns);
    }

    @Test
    @DisplayName("AWT event listeners are a WARN no-op with empty getters")
    void awtEventListenersAreAWarnNoOpWithEmptyGetters() {
        tk().addAWTEventListener(event -> {
        }, -1);
        assertEquals(0, tk().getAWTEventListeners().length);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("addAWTEventListener")),
                "got " + capturedWarns);
    }
}
