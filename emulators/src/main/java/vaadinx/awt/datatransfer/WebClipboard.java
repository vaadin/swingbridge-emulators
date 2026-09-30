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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import vaadinx.EHelper;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * The {@link java.awt.Toolkit#getSystemClipboard system clipboard} for a
 * Vaadin UI. Round-trips through the browser's
 * <a href="https://developer.mozilla.org/docs/Web/API/Clipboard_API">Async
 * Clipboard API</a> via a small static-resource helper JS
 * ({@code META-INF/resources/emul/clipboard-helper.js}, injected per-UI
 * via {@code Page.addJavaScript("context://emul/clipboard-helper.js")} from
 * {@link vaadinx.awt.Toolkit#getSystemClipboard()} — see D_clipboard_helper_js. The
 * {@code context://} URL routes through Vaadin's static-resource handler,
 * not Vite's bundler).
 *
 * <p>Both {@link #setContents} and {@link #getContents} are synchronous from
 * the caller's point of view: they park the calling virtual thread on the
 * {@link PendingJavaScriptResult}'s {@code CompletableFuture} until the
 * browser settles. {@link vaadinx.EHelper#callSwing} provides the VT carrier
 * that allows parking; calls from a non-VT thread or with no current UI
 * throw {@link IllegalStateException} (R_callswing_envelope). See emulators/decisions.md §D_clipboard.
 *
 * <p><b>Throw / WARN split (D_clipboard_throw_warn_split).</b>
 * <ul>
 *   <li><b>Deployment-fixable failures throw.</b> Missing secure context
 *       (plain HTTP / non-localhost), missing {@code navigator.clipboard}
 *       (ancient browser) — IllegalStateException. The migrator's affordance
 *       is to deploy over HTTPS or upgrade the browser; silent no-op would
 *       hide a deployment bug.</li>
 *   <li><b>Per-call runtime failures WARN and degrade gracefully.</b>
 *       NotAllowedError, document-not-focused, decode failures, unexpected
 *       browser errors — log via
 *       {@link EHelper#onUnimplemented(String, String, Object...)} and return
 *       an empty {@link WebClipboardSnapshot} (read) or fire-and-forget
 *       (write). Matches Swing's "clipboard had no data" semantics for read.</li>
 * </ul>
 *
 * <p><b>Test seam (D_clipboard_test_mode).</b> {@link #setTestMode(boolean)} bypasses
 * {@code executeJs} entirely and holds the most-recently-set Transferable in
 * a server-side field. Karibu unit tests opt in; production stays on the
 * real path. Real-browser e2e validation of the {@code executeJs} round-trip
 * is a separate harness (deferred slice).
 *
 * <p>One instance per Vaadin UI; obtain via {@link vaadinx.awt.Toolkit#getSystemClipboard()}.
 */
public final class WebClipboard extends Clipboard {

    private static final Logger log = LoggerFactory.getLogger(WebClipboard.class);

    /** Soft size-WARN threshold for image payloads on the wire (Base64-encoded bytes). */
    private static final int IMAGE_SIZE_WARN_THRESHOLD = 50 * 1024 * 1024;

    private volatile boolean testMode = false;
    /** Test-mode payload. Holds whatever the test last {@code setContents}'d; reads return this. */
    private volatile Transferable testContents = WebClipboardSnapshot.EMPTY;

    public WebClipboard() {
        super("System");
    }

    /**
     * Toggle the in-process test mode. When {@code true}, both
     * {@link #setContents} and {@link #getContents} bypass {@code executeJs}
     * and round-trip through a server-side {@link Transferable} field
     * instead of the browser. Defaults to {@code false} — production code
     * leaves the toggle alone.
     */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    /** Whether {@link #setTestMode test mode} is currently engaged. */
    public boolean isTestMode() {
        return testMode;
    }

    // ===========================================================
    // Clipboard overrides
    // ===========================================================

    /**
     * {@inheritDoc}
     *
     * <p>Not {@code synchronized}, unlike the JDK's: this waits for the browser, and a
     * monitor held across that wait deadlocks the session the moment a second UI fiber of it asks
     * for the clipboard — that fiber keeps the session lock while it waits for the monitor, so the
     * browser's answer can't get in. Swing's single EDT would re-enter the monitor instead.
     */
    @Override
    public void setContents(Transferable contents, ClipboardOwner owner) {
        if (contents == null) {
            // JDK throws NullPointerException from the inherited setContents.
            throw new NullPointerException("contents");
        }

        if (testMode) {
            // Snapshot the test payload so subsequent reads see a stable view
            // independent of any mutation the caller might do on `contents`.
            this.testContents = snapshotFor(contents);
            return;
        }

        assertUiAndVt("setContents");

        String text = extractString(contents);
        String imagePngBase64 = extractImagePngBase64(contents);

        if (text == null && imagePngBase64 == null) {
            // Nothing we can ship to the browser; nothing to do. Likely the
            // caller's Transferable advertised only unsupported custom flavors.
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard", "setContents",
                    "Transferable carried no stringFlavor or imageFlavor data");
            return;
        }

        if (imagePngBase64 != null && imagePngBase64.length() > IMAGE_SIZE_WARN_THRESHOLD) {
            log.warn("Clipboard image payload is large ({} encoded bytes); browser RPC may be slow.",
                    imagePngBase64.length());
        }

        UI ui = UI.getCurrent();
        PendingJavaScriptResult pending = ui.getPage().executeJs(
                "return window.emulClipboard.writeClipboard($0, $1)",
                text, imagePngBase64);
        handleWriteResult(awaitJs(pending, "setContents"));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not {@code synchronized}, for {@link #setContents}'s reason.
     */
    @Override
    public Transferable getContents(Object requestor) {
        if (testMode) return testContents;

        assertUiAndVt("getContents");

        UI ui = UI.getCurrent();
        PendingJavaScriptResult pending = ui.getPage().executeJs(
                "return window.emulClipboard.readClipboard()");
        return handleReadResult(awaitJs(pending, "getContents"));
    }

    /**
     * Park the current UI fiber on the executeJs Promise's result.
     *
     * <p>The natural shape — {@code pending.toCompletableFuture(JsonNode.class).get()}
     * — fails inside a UI fiber: Vaadin returns a
     * {@code DeadlockDetectingCompletableFuture} whose {@code get()} refuses to
     * block while {@link com.vaadin.flow.server.VaadinSession#hasLock()
     * VaadinSession.hasLock()} is true, and {@code VirtualThreadAwareLock}
     * deliberately answers {@code true} from {@code hasLock()} inside one (so Vaadin's
     * element-write path accepts its mutations, D_vt_aware_session_lock). The deadlock
     * detector's check is correct for a platform thread holding the session lock but
     * wrong for a UI fiber, whose park releases the lock so the response RPC can deliver
     * the result (D_clipboard_future_bridge).
     *
     * <p>Bridge through a plain {@link CompletableFuture}: register a
     * {@link PendingJavaScriptResult#then(Class, com.vaadin.flow.function.SerializableConsumer, com.vaadin.flow.function.SerializableConsumer)
     * then-callback} that completes the plain future, then park on it through
     * {@link EHelper#awaitBrowserRoundTrip}: the fiber parks and the lock is released, the
     * response delivers, the {@code then} callback completes the future under the lock, and
     * the fiber resumes. The same park a blocking dialog makes ({@code Dialog.parkUntilClose}).
     */
    private static JsonNode awaitJs(PendingJavaScriptResult pending, String op) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.then(JsonNode.class,
                future::complete,
                err -> future.completeExceptionally(
                        new PendingJavaScriptResult.JavaScriptException(err)));
        try {
            return EHelper.awaitBrowserRoundTrip(UI.getCurrent(), future);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Clipboard " + op + " was interrupted", ie);
        } catch (ExecutionException ee) {
            throw new IllegalStateException("Clipboard " + op + " failed before the browser responded",
                    ee.getCause() != null ? ee.getCause() : ee);
        }
    }

    @Override
    public synchronized void addFlavorListener(FlavorListener listener) {
        // D_gap_severity_triage sub-bucket (a) blocked-upstream: no browser ships the
        // clipboardchange event today (Clipboard API spec issue, open at W3C
        // for years). Polling collides with the read-permission UX. WARN and
        // skip storage — base class would have held the reference forever.
        EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                "addFlavorListener (blocked upstream — no browser clipboardchange event)",
                listener);
    }

    @Override
    public synchronized void removeFlavorListener(FlavorListener listener) {
        EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                "removeFlavorListener (blocked upstream)", listener);
    }

    @Override
    public synchronized FlavorListener[] getFlavorListeners() {
        return new FlavorListener[0];
    }

    // ===========================================================
    // Helpers — VT / UI guard
    // ===========================================================

    private static void assertUiAndVt(String op) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            throw new IllegalStateException(
                    "WebClipboard." + op + " called with no current UI. " +
                    "Clipboard access must run inside a Vaadin UI context (typically inside a " +
                    "peer event listener / ActionListener, under SwingUtilities.invokeLater, " +
                    "or under UI.access(...)).");
        }
        if (!Thread.currentThread().isVirtual()) {
            throw new IllegalStateException(
                    "WebClipboard." + op + " called from a non-virtual thread. " +
                    "Browser clipboard round-trips park on a Promise — calling from a platform " +
                    "thread would block the request thread instead of suspending the carrier. " +
                    "Dispatch through vaadinx.EHelper.callSwing (the standard peer→Swing seam), " +
                    "SwingUtilities.invokeLater, or by enabling test mode for unit tests.");
        }
    }

    // ===========================================================
    // Helpers — Transferable → browser payload
    // ===========================================================

    private static String extractString(Transferable t) {
        if (!t.isDataFlavorSupported(DataFlavor.stringFlavor)) return null;
        try {
            Object v = t.getTransferData(DataFlavor.stringFlavor);
            return v == null ? null : v.toString();
        } catch (UnsupportedFlavorException | IOException e) {
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    "extractString (Transferable.getTransferData threw)", e.getMessage());
            return null;
        }
    }

    /** PNG-encode whatever {@link Image} the Transferable's imageFlavor returns. */
    private static String extractImagePngBase64(Transferable t) {
        // Iterate flavors to find one whose representation class is Image-typed —
        // covers DataFlavor.imageFlavor and any user-defined "image/*" flavor
        // whose representation class is BufferedImage or Image.
        DataFlavor imageFlavor = pickImageFlavor(t);
        if (imageFlavor == null) return null;

        Object raw;
        try {
            raw = t.getTransferData(imageFlavor);
        } catch (UnsupportedFlavorException | IOException e) {
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    "extractImage (Transferable.getTransferData threw)", e.getMessage());
            return null;
        }
        if (!(raw instanceof Image img)) {
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    "extractImage (representation was not an Image)", raw);
            return null;
        }

        BufferedImage buf = toBufferedImage(img);
        if (buf == null) {
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    "extractImage (Image dimensions unknown — async-load Image not supported)",
                    img);
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (!ImageIO.write(buf, "png", baos)) {
                EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                        "extractImage (no ImageIO PNG writer available)", buf);
                return null;
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    "extractImage (PNG encode failed)", e.getMessage());
            return null;
        }
    }

    private static DataFlavor pickImageFlavor(Transferable t) {
        if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) return DataFlavor.imageFlavor;
        for (DataFlavor f : t.getTransferDataFlavors()) {
            Class<?> rep = f.getRepresentationClass();
            if (rep != null && Image.class.isAssignableFrom(rep)) return f;
        }
        return null;
    }

    /**
     * Materialise a {@link BufferedImage} from any {@link Image}. Sync-loaded
     * ImageIcon-backed images and freshly-rendered BufferedImages work; an
     * async-loading {@link java.awt.Image} from a URL with no observer
     * returns null (unknown dimensions). The migrator should pre-load via
     * MediaTracker / ImageIcon before placing on the clipboard.
     */
    private static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage buf) return buf;
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        if (w <= 0 || h <= 0) return null;
        BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buf.createGraphics();
        try {
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }
        return buf;
    }

    // ===========================================================
    // Helpers — handle JS result envelope
    // ===========================================================

    private static void handleWriteResult(JsonNode result) {
        if (result == null || !result.isObject()) {
            // EHelper JS always returns an object; null / non-object means
            // executeJs surfaced something unexpected. Treat as runtime error.
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    "setContents (unexpected JS return shape)", result);
            return;
        }
        if (result.has("ok") && result.get("ok").asBoolean()) return;
        String err = result.has("error") ? result.get("error").asString() : "UNKNOWN";
        String msg = result.has("message") ? result.get("message").asString() : "";
        throwOrWarn("setContents", err, msg);
    }

    private static Transferable handleReadResult(JsonNode result) {
        if (result == null || !result.isObject()) {
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    "getContents (unexpected JS return shape)", result);
            return WebClipboardSnapshot.EMPTY;
        }
        if (result.has("error")) {
            String err = result.get("error").asString();
            String msg = result.has("message") ? result.get("message").asString() : "";
            throwOrWarn("getContents", err, msg);
            return WebClipboardSnapshot.EMPTY;
        }
        String text = result.has("text") && !result.get("text").isNull()
                ? result.get("text").asString() : null;
        String imageBase64 = result.has("imageBase64") && !result.get("imageBase64").isNull()
                ? result.get("imageBase64").asString() : null;
        BufferedImage image = imageBase64 != null ? decodeImage(imageBase64) : null;
        if (text == null && image == null) return WebClipboardSnapshot.EMPTY;
        return new WebClipboardSnapshot(text, image);
    }

    private static BufferedImage decodeImage(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                        "getContents (ImageIO could not decode browser image)", bytes.length);
            }
            return img;
        } catch (IllegalArgumentException | IOException e) {
            EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    "getContents (image decode failed)", e.getMessage());
            return null;
        }
    }

    /**
     * Route the JS error envelope to either a throw (deployment-fixable) or a
     * WARN (runtime / per-call). Per D_clipboard_throw_warn_split.
     */
    private static void throwOrWarn(String op, String err, String msg) {
        switch (err) {
            case "NO_SECURE_CONTEXT" -> throw new IllegalStateException(
                    "WebClipboard." + op + ": " + msg +
                    " Deploy over HTTPS or localhost; navigator.clipboard is not available on plain HTTP origins.");
            case "NO_CLIPBOARD_API" -> throw new IllegalStateException(
                    "WebClipboard." + op + ": " + msg +
                    " The browser does not implement the Async Clipboard API; upgrade to a modern browser " +
                    "(Chrome 76+, Firefox 127+, Safari 13.1+).");
            default -> EHelper.onUnimplemented("vaadinx.awt.datatransfer.WebClipboard",
                    op + " (browser " + err + ")", msg);
        }
    }

    // ===========================================================
    // Test seam helpers
    // ===========================================================

    /**
     * Snapshot a Transferable for test-mode storage. Pre-extracts string and
     * image payloads so subsequent reads see a stable view regardless of any
     * mutation the test code does on the original Transferable. Mirrors the
     * production flow's snapshot semantics (extract once, deliver via
     * {@link WebClipboardSnapshot}).
     */
    private static WebClipboardSnapshot snapshotFor(Transferable t) {
        String text = extractString(t);
        BufferedImage image = extractBufferedImage(t);
        if (text == null && image == null) return WebClipboardSnapshot.EMPTY;
        return new WebClipboardSnapshot(text, image);
    }

    private static BufferedImage extractBufferedImage(Transferable t) {
        DataFlavor imageFlavor = pickImageFlavor(t);
        if (imageFlavor == null) return null;
        try {
            Object raw = t.getTransferData(imageFlavor);
            if (raw instanceof Image img) return toBufferedImage(img);
        } catch (UnsupportedFlavorException | IOException ignored) {
        }
        return null;
    }
}
