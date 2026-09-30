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

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJTextArea;
import vaadinx.EHelper;
import vaadinx.awt.Toolkit;
import vaadinx.awt.datatransfer.WebClipboard;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.datatransfer.WebClipboard} WARN
 * inventory exit gate. Two tests fail if any
 * {@code EHelper.onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "Clipboard", enables {@code WebClipboard.setTestMode(true)}
 *       so the in-process Transferable seam stands in for the browser
 *       (Karibu has no real {@code navigator.clipboard}), then drives the
 *       four demos: text round-trip, image round-trip, combined
 *       text+image, and the probe (empty-clipboard + UnsupportedFlavor).
 *       Each step asserts no stub WARN fires.</li>
 *   <li>{@link #inventory_clipboard_api_surface} — micro-driver over the
 *       {@link vaadinx.awt.datatransfer.WebClipboard} API in test mode
 *       (round-trip text, image, combined; getAvailableDataFlavors,
 *       isDataFlavorAvailable, getData, snapshot semantics). Then
 *       exercises the intentional-WARN paths
 *       ({@code addFlavorListener} / {@code removeFlavorListener},
 *       blocked-upstream per D_gap_severity_triage sub-bucket (a)) and asserts they fire
 *       exactly the expected number of WARNs with the expected blocked-
 *       upstream rationale. Finally exercises the deployment-fixable
 *       guard throws ({@code IllegalStateException} when called off-VT)
 *       to lock in the D_clipboard_throw_warn_split throw site.</li>
 * </ol>
 *
 * <p>The {@code executeJs} round-trip itself is not unit-testable without
 * a real browser; e2e validation of the production path is a deferred
 * slice (D_clipboard validation note). This exit gate covers the server-side
 * seams comprehensively.
 *
 * <p>Both surrogate and emulator warnHooks are wired to a single sink so
 * a stub fire from either layer surfaces.
 */
class ClipboardWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Clipboard");
        dump("Step 0b (ClipboardPanel swap)", warnings);

        // Karibu has no real navigator.clipboard. Enable test mode on the
        // per-UI WebClipboard so the demo's setContents / getContents run
        // through the in-process Transferable seam (D_clipboard_test_mode). Toolkit returns
        // the same per-UI instance to the panel and to the test.
        WebClipboard cb = (WebClipboard) Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setTestMode(true);
        dump("Step 1 (WebClipboard test mode enabled)", warnings);

        // -- Demo 1: text round-trip ---------------------------------------

        // Source JTextArea is pre-seeded with "hello clipboard" in the ctor.
        // Click "Copy text" → ActionListener (inside EHelper.callSwing VT) →
        // setContents(StringSelection). Status label should reflect the copy.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Copy text")));
        Transferable held = cb.getContents(null);
        if (!held.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            throw new AssertionError("Clipboard should have stringFlavor after Copy text");
        }
        try {
            String s = (String) held.getTransferData(DataFlavor.stringFlavor);
            if (!"hello clipboard".equals(s)) {
                throw new AssertionError("Expected 'hello clipboard', got: " + s);
            }
        } catch (UnsupportedFlavorException | java.io.IOException ex) {
            throw new AssertionError("Unexpected exception reading clipboard", ex);
        }
        dump("Step 2 (Copy text → clipboard holds 'hello clipboard')", warnings);

        // Click "Paste text" → reads clipboard, writes into target JTextArea.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Paste text")));
        SJTextArea targetPeer = LocatorJ._get(SJTextArea.class,
                spec -> spec.withId("clipboard-text-target"));
        if (!"hello clipboard".equals(targetPeer.getValue())) {
            throw new AssertionError(
                    "Paste target should have 'hello clipboard', got: " + targetPeer.getValue());
        }
        dump("Step 3 (Paste text → target JTextArea reflects clipboard)", warnings);

        // -- Demo 2: image round-trip --------------------------------------

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Generate 32×32 red square + copy")));
        Transferable imageHeld = cb.getContents(null);
        if (!imageHeld.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            throw new AssertionError("Clipboard should have imageFlavor after Copy image");
        }
        try {
            Image img = (Image) imageHeld.getTransferData(DataFlavor.imageFlavor);
            if (img.getWidth(null) != 32 || img.getHeight(null) != 32) {
                throw new AssertionError("Expected 32×32 image, got " +
                        img.getWidth(null) + "×" + img.getHeight(null));
            }
        } catch (UnsupportedFlavorException | java.io.IOException ex) {
            throw new AssertionError("Unexpected exception reading image", ex);
        }
        dump("Step 4 (Copy image → clipboard holds 32×32 image)", warnings);

        // Click "Paste image" → reads + updates the preview label.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Paste image")));
        dump("Step 5 (Paste image → preview label updated)", warnings);

        // -- Demo 3: combined text + image ---------------------------------

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Copy text + image together")));
        Transferable bothHeld = cb.getContents(null);
        if (!bothHeld.isDataFlavorSupported(DataFlavor.stringFlavor)
                || !bothHeld.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            throw new AssertionError("Combined copy should hold both stringFlavor and imageFlavor");
        }
        if (bothHeld.getTransferDataFlavors().length != 2) {
            throw new AssertionError("Combined snapshot should advertise exactly 2 flavors, got " +
                    bothHeld.getTransferDataFlavors().length);
        }
        dump("Step 6 (Combined copy → clipboard holds both flavors)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Paste both")));
        dump("Step 7 (Combined paste → readout label updated)", warnings);

        // -- Demo 4: probes ------------------------------------------------

        // Inspect — should report 2 flavors (text + image from the previous
        // combined copy still on the clipboard).
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Inspect current flavors")));
        dump("Step 8 (Inspect → reports flavor list)", warnings);

        // Probe unsupported flavor — the snapshot rejects javaFileListFlavor
        // per JDK contract; the panel catches and writes a status message
        // (no WARN — UnsupportedFlavorException is the expected JDK shape,
        // not an emulation incompleteness).
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Probe unsupported flavor")));
        dump("Step 9 (Probe javaFileListFlavor → UnsupportedFlavorException caught)", warnings);

        WarnDump.println();
        WarnDump.println("=== clipboard user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_clipboard_api_surface() throws UnsupportedFlavorException, java.io.IOException {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Toolkit entry point ------------------------------------------

        WebClipboard cb = (WebClipboard) Toolkit.getDefaultToolkit().getSystemClipboard();
        if (cb != Toolkit.getDefaultToolkit().getSystemClipboard()) {
            throw new AssertionError("Toolkit.getDefaultToolkit().getSystemClipboard() should be per-UI singleton");
        }
        cb.setTestMode(true);
        dump("Clipboard  Toolkit.getSystemClipboard + setTestMode", warnings);

        // -- Round-trip: text ---------------------------------------------

        cb.setContents(new StringSelection("hello"), null);
        Transferable t = cb.getContents(null);
        if (!"hello".equals(t.getTransferData(DataFlavor.stringFlavor))) {
            throw new AssertionError("Text round-trip lost the payload");
        }
        dump("Clipboard  text setContents / getContents round-trip", warnings);

        // -- Round-trip: image --------------------------------------------

        BufferedImage red = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = red.createGraphics();
        try {
            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, 8, 8);
        } finally {
            g.dispose();
        }
        cb.setContents(new InlineImageTransferable(red), null);
        Transferable imageT = cb.getContents(null);
        Image out = (Image) imageT.getTransferData(DataFlavor.imageFlavor);
        if (out.getWidth(null) != 8 || out.getHeight(null) != 8) {
            throw new AssertionError("Image dimensions lost on round-trip");
        }
        dump("Clipboard  image setContents / getContents round-trip", warnings);

        // -- Round-trip: combined text + image ----------------------------

        cb.setContents(new InlineCombinedTransferable("caption", red), null);
        Transferable combined = cb.getContents(null);
        if (combined.getTransferDataFlavors().length != 2) {
            throw new AssertionError("Combined snapshot should advertise 2 flavors");
        }
        dump("Clipboard  combined text+image setContents / getContents round-trip", warnings);

        // -- Inherited Clipboard surface -----------------------------------

        DataFlavor[] available = cb.getAvailableDataFlavors();
        if (available.length != 2) {
            throw new AssertionError("getAvailableDataFlavors mismatch: " + available.length);
        }
        if (!cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            throw new AssertionError("isDataFlavorAvailable(stringFlavor) should be true");
        }
        if (!cb.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            throw new AssertionError("isDataFlavorAvailable(imageFlavor) should be true");
        }
        if (cb.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
            throw new AssertionError("isDataFlavorAvailable(fileListFlavor) should be false (deferred)");
        }
        if (!"caption".equals(cb.getData(DataFlavor.stringFlavor))) {
            throw new AssertionError("getData(stringFlavor) read-through broken");
        }
        dump("Clipboard  getAvailableDataFlavors / isDataFlavorAvailable / getData", warnings);

        // -- Snapshot semantics: empty after clearing ---------------------

        cb.setContents(new EmptyAdvertiseTransferable(), null);
        Transferable empty = cb.getContents(null);
        if (empty.getTransferDataFlavors().length != 0) {
            throw new AssertionError("EmptyAdvertise should snapshot to zero flavors");
        }
        dump("Clipboard  unsupported-only Transferable snapshots to empty", warnings);

        // -- Snapshot UnsupportedFlavorException (JDK contract, not a WARN) -

        try {
            empty.getTransferData(DataFlavor.stringFlavor);
            throw new AssertionError("Empty snapshot should throw UnsupportedFlavorException");
        } catch (UnsupportedFlavorException expected) {
            // JDK-faithful — not a WARN.
        }
        dump("Clipboard  UnsupportedFlavorException on missing flavor (JDK-faithful)", warnings);

        // -- Clipboard.getName ---------------------------------------------

        if (!"System".equals(cb.getName())) {
            throw new AssertionError("Clipboard name should be 'System', got: " + cb.getName());
        }
        dump("Clipboard  getName", warnings);

        // -- Intentional-WARN paths: addFlavorListener (blocked-upstream) ---
        //    Asserted separately — these are designed to WARN, so they break
        //    the dump() WARN-clearance protocol; we count + check, not zero.

        java.awt.datatransfer.FlavorListener listener = e -> { /* ignored */ };
        cb.addFlavorListener(listener);
        if (warnings.size() != 1) {
            throw new AssertionError("addFlavorListener should fire exactly one WARN, got " + warnings);
        }
        if (!warnings.get(0).contains("addFlavorListener") || !warnings.get(0).contains("blocked upstream")) {
            throw new AssertionError("addFlavorListener WARN should name 'blocked upstream'; got: " + warnings);
        }
        warnings.clear();

        cb.removeFlavorListener(listener);
        if (warnings.size() != 1) {
            throw new AssertionError("removeFlavorListener should fire exactly one WARN, got " + warnings);
        }
        if (!warnings.get(0).contains("removeFlavorListener")) {
            throw new AssertionError("removeFlavorListener WARN should name the method; got: " + warnings);
        }
        warnings.clear();

        if (cb.getFlavorListeners().length != 0) {
            throw new AssertionError("getFlavorListeners should return empty array (storage dropped)");
        }
        WarnDump.println();
        WarnDump.println("--- Clipboard  addFlavorListener / removeFlavorListener fired exactly one WARN each (blocked-upstream D_gap_severity_triage sub-bucket (a) — expected) ---");

        // -- Guard throws (D_clipboard_throw_warn_split production-path throws) --------------------
        //    Re-disable test mode so the guards engage; we invoke from the
        //    test thread (non-VT) and assert IllegalStateException.

        cb.setTestMode(false);
        boolean threw = false;
        try {
            cb.getContents(null);
        } catch (IllegalStateException ex) {
            threw = true;
            if (!ex.getMessage().contains("non-virtual thread")) {
                throw new AssertionError("Guard throw should name non-virtual thread; got: " + ex.getMessage());
            }
        }
        if (!threw) throw new AssertionError("getContents from test thread should throw IllegalStateException");

        threw = false;
        try {
            cb.setContents(new StringSelection("x"), null);
        } catch (IllegalStateException ex) {
            threw = true;
        }
        if (!threw) throw new AssertionError("setContents from test thread should throw IllegalStateException");

        // Guard throws don't fire WARNs — they're production-path
        // IllegalStateExceptions, not stub fires.
        dump("Clipboard  off-VT guard throws (IllegalStateException, no WARN)", warnings);

        WarnDump.println();
        WarnDump.println("=== WebClipboard API-surface WARN total across buckets above ===");
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the Clipboard exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }

    /** Inline Transferable advertising only imageFlavor. */
    private static final class InlineImageTransferable implements Transferable {
        private final BufferedImage image;
        InlineImageTransferable(BufferedImage image) { this.image = image; }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] { DataFlavor.imageFlavor }; }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }

    /** Inline Transferable advertising both string and image. */
    private static final class InlineCombinedTransferable implements Transferable {
        private final String text;
        private final BufferedImage image;
        InlineCombinedTransferable(String text, BufferedImage image) {
            this.text = text; this.image = image;
        }
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.stringFlavor, DataFlavor.imageFlavor };
        }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
        }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (DataFlavor.stringFlavor.equals(flavor)) return text;
            if (DataFlavor.imageFlavor.equals(flavor)) return image;
            throw new UnsupportedFlavorException(flavor);
        }
    }

    /** Inline Transferable advertising no usable flavors — snapshots to EMPTY. */
    private static final class EmptyAdvertiseTransferable implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[0]; }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return false; }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
