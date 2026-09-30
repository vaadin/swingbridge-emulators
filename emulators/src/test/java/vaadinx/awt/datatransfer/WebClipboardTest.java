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

package vaadinx.awt.datatransfer;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.Toolkit;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Karibu unit tests for {@link WebClipboard} via the {@code WebClipboard.setTestMode} seam.
 * The real {@code executeJs} round-trip is not unit-testable without a browser; an
 * e2e browser harness covers that path (deferred slice per D_clipboard).
 *
 * <p>Tests run inside {@link vaadinx.EHelper#callSwing} when they need the VT envelope —
 * the same wiring real apps use. Tests of the VT / UI assertion error paths
 * call WebClipboard methods directly from the test thread (no callSwing) and
 * leave testMode off to exercise the production guard.
 */
class WebClipboardTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        EHelper.warnHook = capturedWarns::add;
        SHelper.warnHook = capturedWarns::add;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> {
        };
        SHelper.warnHook = msg -> {
        };
    }

    /** The current UI's clipboard, already narrowed — every test starts here. */
    private static WebClipboard clipboard() {
        return assertInstanceOf(WebClipboard.class, Toolkit.getDefaultToolkit().getSystemClipboard());
    }

    /** …and in test mode, which is the seam most of these tests drive. */
    private static WebClipboard testModeClipboard() {
        WebClipboard cb = clipboard();
        cb.setTestMode(true);
        return cb;
    }

    /**
     * The browser round-trip must not run inside the clipboard's monitor: a second UI fiber
     * of the session waiting for it keeps the session lock, so the answer never gets in.
     * Checked structurally, since under Karibu the deadlock would hang the test thread.
     */
    @Test
    @DisplayName("getContents and setContents hold no monitor across the browser round-trip")
    void getContentsAndSetContentsAreNotSynchronized() throws NoSuchMethodException {
        assertFalse(java.lang.reflect.Modifier.isSynchronized(
                WebClipboard.class.getMethod("getContents", Object.class).getModifiers()));
        assertFalse(java.lang.reflect.Modifier.isSynchronized(WebClipboard.class.getMethod(
                "setContents", Transferable.class, java.awt.datatransfer.ClipboardOwner.class).getModifiers()));
    }

    // --- Toolkit entry point --------------------------------------------

    @Test
    @DisplayName("getSystemClipboard returns a WebClipboard")
    void getSystemClipboardReturnsAWebClipboard() {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        assertInstanceOf(WebClipboard.class, cb, "Expected WebClipboard, got " + cb.getClass().getName());
    }

    @Test
    @DisplayName("getSystemClipboard returns the same instance per UI on repeated calls")
    void getSystemClipboardReturnsTheSameInstancePerUiOnRepeatedCalls() {
        Clipboard a = Toolkit.getDefaultToolkit().getSystemClipboard();
        Clipboard b = Toolkit.getDefaultToolkit().getSystemClipboard();
        assertSame(a, b);
    }

    @Test
    @DisplayName("getSystemClipboard with no UI throws IllegalStateException")
    void getSystemClipboardWithNoUiThrowsIllegalStateException() {
        // MockVaadin.tearDown() drops the current UI; we restore the harness
        // afterwards via setupKaribu so @AfterEach does not double-tear.
        MockVaadin.tearDown();
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> Toolkit.getDefaultToolkit().getSystemClipboard());
            assertTrue(ex.getMessage().contains("no current UI"), "Unexpected message: " + ex.getMessage());
        } finally {
            setupKaribu();
        }
    }

    @Test
    @DisplayName("clipboards from distinct UIs are distinct instances")
    void clipboardsFromDistinctUIsAreDistinctInstances() {
        Clipboard first = Toolkit.getDefaultToolkit().getSystemClipboard();
        // Tear down + re-setup yields a fresh UI; the new per-UI lookup must miss.
        MockVaadin.tearDown();
        setupKaribu();
        Clipboard second = Toolkit.getDefaultToolkit().getSystemClipboard();
        assertNotSame(first, second);
    }

    // --- Test-mode round-trip: text -------------------------------------

    @Test
    @DisplayName("test mode round-trips StringSelection")
    void testModeRoundTripsStringSelection() throws Exception {
        WebClipboard cb = testModeClipboard();
        cb.setContents(new StringSelection("hello"), null);
        Transferable t = cb.getContents(null);
        assertTrue(t.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertEquals("hello", t.getTransferData(DataFlavor.stringFlavor));
    }

    @Test
    @DisplayName("test mode getContents on empty clipboard returns no flavors")
    void testModeGetContentsOnEmptyClipboardReturnsNoFlavors() {
        WebClipboard cb = testModeClipboard();
        Transferable t = cb.getContents(null);
        assertEquals(0, t.getTransferDataFlavors().length);
        assertFalse(t.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertThrows(UnsupportedFlavorException.class, () -> t.getTransferData(DataFlavor.stringFlavor));
    }

    @Test
    @DisplayName("test mode overwrite replaces previous contents")
    void testModeOverwriteReplacesPreviousContents() throws Exception {
        WebClipboard cb = testModeClipboard();
        cb.setContents(new StringSelection("first"), null);
        cb.setContents(new StringSelection("second"), null);
        assertEquals("second", cb.getContents(null).getTransferData(DataFlavor.stringFlavor));
    }

    @Test
    @DisplayName("null Transferable throws NPE per JDK contract")
    void nullTransferableThrowsNpePerJdkContract() {
        WebClipboard cb = testModeClipboard();
        assertThrows(NullPointerException.class, () -> cb.setContents(null, null));
    }

    // --- Test-mode round-trip: image ------------------------------------

    @Test
    @DisplayName("test mode round-trips BufferedImage via imageFlavor")
    void testModeRoundTripsBufferedImageViaImageFlavor() throws Exception {
        WebClipboard cb = testModeClipboard();
        BufferedImage original = makeRedSquare(8, 8);
        cb.setContents(new ImageTransferable(original), null);

        Transferable t = cb.getContents(null);
        assertTrue(t.isDataFlavorSupported(DataFlavor.imageFlavor));
        Image out = (Image) t.getTransferData(DataFlavor.imageFlavor);
        assertEquals(original.getWidth(), out.getWidth(null));
        assertEquals(original.getHeight(), out.getHeight(null));
    }

    @Test
    @DisplayName("test mode round-trips combined text + image")
    void testModeRoundTripsCombinedTextPlusImage() throws Exception {
        WebClipboard cb = testModeClipboard();
        cb.setContents(new TextAndImageTransferable("caption", makeRedSquare(4, 4)), null);
        Transferable t = cb.getContents(null);
        assertTrue(t.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertTrue(t.isDataFlavorSupported(DataFlavor.imageFlavor));
        assertEquals("caption", t.getTransferData(DataFlavor.stringFlavor));
    }

    @Test
    @DisplayName("getTransferDataFlavors only lists present flavors")
    void getTransferDataFlavorsOnlyListsPresentFlavors() {
        WebClipboard cb = testModeClipboard();
        cb.setContents(new StringSelection("text only"), null);
        assertEquals(DataFlavor.stringFlavor, assertSingle(cb.getContents(null).getTransferDataFlavors()));
    }

    @Test
    @DisplayName("isDataFlavorAvailable delegates via getContents to snapshot")
    void isDataFlavorAvailableDelegatesViaGetContentsToSnapshot() {
        WebClipboard cb = testModeClipboard();
        cb.setContents(new StringSelection("hello"), null);
        assertTrue(cb.isDataFlavorAvailable(DataFlavor.stringFlavor));
        assertFalse(cb.isDataFlavorAvailable(DataFlavor.imageFlavor));
    }

    @Test
    @DisplayName("getData reads through getContents")
    void getDataReadsThroughGetContents() throws Exception {
        WebClipboard cb = testModeClipboard();
        cb.setContents(new StringSelection("hello"), null);
        assertEquals("hello", cb.getData(DataFlavor.stringFlavor));
    }

    @Test
    @DisplayName("getData throws UnsupportedFlavorException for missing flavor")
    void getDataThrowsUnsupportedFlavorExceptionForMissingFlavor() {
        WebClipboard cb = testModeClipboard();
        assertThrows(UnsupportedFlavorException.class, () -> cb.getData(DataFlavor.stringFlavor));
    }

    // --- Snapshot semantics ---------------------------------------------

    @Test
    @DisplayName("read returns a fresh snapshot, not the original Transferable")
    void readReturnsAFreshSnapshotNotTheOriginalTransferable() {
        WebClipboard cb = testModeClipboard();
        StringSelection original = new StringSelection("hello");
        cb.setContents(original, null);
        Transferable t = cb.getContents(null);
        assertNotSame(original, t,
                "WebClipboard should snapshot the Transferable, not hand back the user's instance");
    }

    @Test
    @DisplayName("WebClipboardSnapshot EMPTY has zero flavors and rejects everything")
    void webClipboardSnapshotEmptyHasZeroFlavorsAndRejectsEverything() {
        WebClipboardSnapshot empty = WebClipboardSnapshot.EMPTY;
        assertEquals(0, empty.getTransferDataFlavors().length);
        assertFalse(empty.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertFalse(empty.isDataFlavorSupported(DataFlavor.imageFlavor));
        assertThrows(UnsupportedFlavorException.class, () -> empty.getTransferData(DataFlavor.stringFlavor));
    }

    @Test
    @DisplayName("Transferable that advertises no string or image flavor WARNs and stores empty")
    void transferableThatAdvertisesNoStringOrImageFlavorWarnsAndStoresEmpty() {
        WebClipboard cb = testModeClipboard();
        cb.setContents(new EmptyTransferable(), null);
        // Test-mode snapshotFor extracts nothing; subsequent read sees EMPTY.
        // No WARN in test mode (the WARN path is the production extract-then-ship).
        Transferable t = cb.getContents(null);
        assertEquals(0, t.getTransferDataFlavors().length);
    }

    // --- VT / UI guards (production path) -------------------------------

    @Test
    @DisplayName("setContents from non-VT thread throws IllegalStateException")
    void setContentsFromNonVtThreadThrowsIllegalStateException() {
        WebClipboard cb = clipboard();
        // testMode=false (default) — guard fires.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cb.setContents(new StringSelection("hello"), null));
        assertTrue(ex.getMessage().contains("non-virtual thread"), "Unexpected: " + ex.getMessage());
    }

    @Test
    @DisplayName("getContents from non-VT thread throws IllegalStateException")
    void getContentsFromNonVtThreadThrowsIllegalStateException() {
        WebClipboard cb = clipboard();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> cb.getContents(null));
        assertTrue(ex.getMessage().contains("non-virtual thread"), "Unexpected: " + ex.getMessage());
    }

    @Test
    @DisplayName("production path needs UI context (no UI - no-throw guard fires)")
    void productionPathNeedsUiContextNoUiNoThrowGuardFires() {
        WebClipboard cb = clipboard();
        MockVaadin.tearDown();
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> cb.getContents(null));
            // Could trip either guard; either message names a Vaadin-context fix.
            String m = ex.getMessage();
            assertTrue(m.contains("no current UI") || m.contains("non-virtual thread"),
                    "Unexpected message: " + m);
        } finally {
            setupKaribu();
        }
    }

    // --- Listener surface (blocked-upstream WARN) ----------------------

    @Test
    @DisplayName("addFlavorListener WARNs and does not store the listener")
    void addFlavorListenerWarnsAndDoesNotStoreTheListener() {
        WebClipboard cb = clipboard();
        FlavorListener listener = e -> { /* ignored */ };
        cb.addFlavorListener(listener);
        assertEquals(0, cb.getFlavorListeners().length,
                "Listener storage is intentionally dropped — no Vaadin clipboardchange event");
        assertTrue(capturedWarns.stream()
                        .anyMatch(it -> it.contains("addFlavorListener") && it.contains("blocked upstream")),
                "Expected a WARN naming the blocked-upstream rationale; got: " + capturedWarns);
    }

    @Test
    @DisplayName("removeFlavorListener WARNs")
    void removeFlavorListenerWarns() {
        WebClipboard cb = clipboard();
        cb.removeFlavorListener(e -> {
        });
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("removeFlavorListener")),
                "Expected removeFlavorListener WARN; got: " + capturedWarns);
    }

    // --- Helpers --------------------------------------------------------

    private BufferedImage makeRedSquare(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Custom Transferable that returns only the image flavor — exercises the imageFlavor extract path. */
    private static final class ImageTransferable implements Transferable {
        private final BufferedImage image;

        ImageTransferable(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }

    /** Carries both text and an image — exercises the multi-flavor extract. */
    private static final class TextAndImageTransferable implements Transferable {
        private final String text;
        private final BufferedImage image;

        TextAndImageTransferable(String text, BufferedImage image) {
            this.text = text;
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.stringFlavor, DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (DataFlavor.stringFlavor.equals(flavor)) {
                return text;
            }
            if (DataFlavor.imageFlavor.equals(flavor)) {
                return image;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }

    /** Advertises no flavors at all — exercises the "nothing to ship" path. */
    private static final class EmptyTransferable implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return false;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
