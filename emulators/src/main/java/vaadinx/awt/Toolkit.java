/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.Toolkit
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import vaadinx.EHelper;
import vaadinx.awt.datatransfer.WebClipboard;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.JobAttributes;
import java.awt.PageAttributes;
import java.awt.Point;
import java.awt.PrintJob;
import java.awt.datatransfer.Clipboard;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.font.TextAttribute;
import java.awt.im.InputMethodHighlight;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Import-swap target for {@link java.awt.Toolkit} — a full-surface emulator
 * (D_toolkit_full_surface). Migrated code rewrites {@code java.awt.Toolkit} →
 * {@code vaadinx.awt.Toolkit} and recompiles unchanged; the rule has no
 * carve-out. Unlike a real {@code Toolkit}, this does <em>not</em> extend the
 * abstract JDK class (it is the import-swap surface, not a platform toolkit),
 * so it exposes only the public API and selects per-method behaviour by tier:
 *
 * <ul>
 *   <li><b>Delegate to the headless JDK toolkit</b> where the value is
 *       client-independent and a headless JVM computes it correctly:
 *       {@link #getFontMetrics}, {@link #getFontList},
 *       {@link #getDesktopProperty}, {@link #isAlwaysOnTopSupported},
 *       {@link #getProperty} — verified to work under {@code java.awt.headless=true}.</li>
 *   <li><b>Browser-backed</b> for genuinely client-specific facts the server
 *       JVM cannot know (and which throw {@code HeadlessException} on the JDK
 *       toolkit): {@link #getScreenSize}, {@link #getScreenResolution},
 *       {@link #getScreenInsets}, {@link #getMenuShortcutKeyMaskEx} — via
 *       {@link BrowserToolkitInfo} (on-demand VT-park, D_toolkit_from_client_details).</li>
 *   <li><b>Throw, faithful to the JDK contract</b>: {@link #getLockingKeyState}
 *       / {@link #setLockingKeyState} throw {@link UnsupportedOperationException},
 *       exactly as a real {@code Toolkit} does on a host that cannot toggle
 *       locking keys (R_match_swing_errors).</li>
 *   <li><b>Fabricate a faithful constant</b> for headless-throwing,
 *       client-independent queries: {@link #getColorModel} (default RGB),
 *       {@link #getMaximumCursorColors} / {@link #getBestCursorSize} (0 —
 *       "custom cursors unsupported"), {@link #getSystemSelection} (null).</li>
 *   <li><b>WARN and ignore</b> where there is no web counterpart: {@link #beep}
 *       (web pages do not beep at users), {@link #setDynamicLayout},
 *       {@link #addAWTEventListener}, {@link #mapInputMethodHighlight},
 *       {@link #getPrintJob} (printing deferred), {@link #createDragGestureRecognizer}
 *       (DnD is the {@link vaadinx.swing.TransferHandler} path).</li>
 *   <li><b>Load images via {@link ImageIO}</b> ({@link #getImage} /
 *       {@link #createImage}) — headless-capable; the {@code ImageProducer}
 *       overload WARNs (producer pipeline is paint-scope, out of scope).</li>
 * </ul>
 *
 * <p>{@link #getDefaultToolkit()} returns a single global, stateless instance —
 * all per-UI state lives elsewhere ({@link WebClipboard} per UI,
 * {@link BrowserToolkitInfo} per UI), looked up at call time via
 * {@link UI#getCurrent()}. So obtaining the toolkit needs no UI (it works at
 * frame-construction time); only the browser-touching calls do.
 *
 * <p>See D_toolkit_full_surface (full surface), D_clipboard (clipboard slice), D_whitelist_porting (port-surface policy).
 */
public final class Toolkit {

    private static final Logger log = LoggerFactory.getLogger(Toolkit.class);

    private static final Toolkit INSTANCE = new Toolkit();

    private Toolkit() {}

    /**
     * The toolkit for this application — a single global, stateless instance
     * (per-UI clipboard / display state is resolved at call time via
     * {@link UI#getCurrent()}). Mirrors {@link java.awt.Toolkit#getDefaultToolkit()}.
     */
    public static Toolkit getDefaultToolkit() {
        return INSTANCE;
    }

    // ===========================================================
    // Clipboard (D_clipboard) — per-UI WebClipboard
    // ===========================================================

    /**
     * The system clipboard for the current Vaadin UI. Returns the same
     * {@link WebClipboard} instance for repeated calls within one UI
     * (per-UI lazy-init via {@link ComponentUtil#setData}); separate UIs get
     * separate instances. All instances ultimately route to the same browser
     * {@code navigator.clipboard}.
     *
     * <p>Throws {@link IllegalStateException} if {@link UI#getCurrent()} is null
     * — the per-UI lookup needs a UI to key on, and the WebClipboard's
     * downstream {@code executeJs} dispatch needs one anyway (D_clipboard, R_callswing_envelope).
     */
    public Clipboard getSystemClipboard() {
        UI ui = UI.getCurrent();
        if (ui == null) {
            throw new IllegalStateException(
                    "Toolkit.getSystemClipboard() called with no current UI. " +
                    "Clipboard access must run inside a Vaadin UI context " +
                    "(typically inside a peer event listener / ActionListener, " +
                    "under SwingUtilities.invokeLater, or under UI.access(...)).");
        }
        WebClipboard existing = ComponentUtil.getData(ui, WebClipboard.class);
        if (existing != null) return existing;
        // Inject the helper JS on first access for this UI (D_clipboard_helper_js). The
        // `context://` URL routes through Vaadin's static-resource handler,
        // not Vite's bundler. addJavaScript is idempotent per Page.
        ui.getPage().addJavaScript("context://emul/clipboard-helper.js");
        WebClipboard fresh = new WebClipboard();
        ComponentUtil.setData(ui, WebClipboard.class, fresh);
        return fresh;
    }

    /**
     * The selection clipboard ("primary selection", X11 middle-click). No
     * browser counterpart — returns null, which is the JDK's own behaviour on
     * platforms without a selection clipboard.
     */
    public Clipboard getSystemSelection() {
        return null;
    }

    // ===========================================================
    // Browser-backed client-specific facts (BrowserToolkitInfo)
    // ===========================================================

    /**
     * The browser <em>viewport</em> size ({@code window.innerWidth/Height}),
     * NOT the physical monitor's {@code screen.width/height} — per D_toolkit_screen_size. The
     * viewport is the coordinate space {@code Window.setLocation/setBounds}
     * places windows in, so migrated screen-math idioms (centering,
     * edge-anchored toasts, size-to-fraction-of-screen) compute positions
     * that land on the visible page. Browser-backed with live resize
     * tracking; see {@link BrowserToolkitInfo}.
     */
    public Dimension getScreenSize() {
        BrowserToolkitInfo info = BrowserToolkitInfo.get();
        return new Dimension(info.viewportWidth(), info.viewportHeight());
    }

    /** Approximate screen resolution in DPI ({@code 96 × devicePixelRatio}). Browser-backed. */
    public int getScreenResolution() {
        return (int) Math.round(96 * BrowserToolkitInfo.get().devicePixelRatio());
    }

    /**
     * Screen insets (taskbar / dock reservation). A browser has no such concept
     * — always zero. Does not consult {@link BrowserToolkitInfo} (no browser
     * round-trip needed).
     */
    public Insets getScreenInsets(GraphicsConfiguration gc) {
        return new Insets(0, 0, 0, 0);
    }

    /**
     * The accelerator modifier for menu shortcuts: {@code META} on macOS / iOS,
     * {@code CTRL} elsewhere. Browser-backed (platform from
     * {@link BrowserToolkitInfo}); defaults to {@code CTRL} until the browser
     * value is available.
     */
    public int getMenuShortcutKeyMaskEx() {
        return BrowserToolkitInfo.get().mac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    }

    /** @deprecated Legacy mask form; prefer {@link #getMenuShortcutKeyMaskEx()}. */
    @Deprecated
    @SuppressWarnings("deprecation")
    public int getMenuShortcutKeyMask() {
        return BrowserToolkitInfo.get().mac() ? InputEvent.META_MASK : InputEvent.CTRL_MASK;
    }

    // ===========================================================
    // Throw — faithful to the JDK "unsupported host" contract (R_match_swing_errors)
    // ===========================================================

    public boolean getLockingKeyState(int keyCode) {
        throw new UnsupportedOperationException(
                "Toolkit.getLockingKeyState is not supported in the browser " +
                "(the host cannot read Caps/Num/Scroll-lock state on demand) — " +
                "matches java.awt.Toolkit's contract on hosts without this capability.");
    }

    public void setLockingKeyState(int keyCode, boolean on) {
        throw new UnsupportedOperationException(
                "Toolkit.setLockingKeyState is not supported in the browser — " +
                "matches java.awt.Toolkit's contract on hosts without this capability.");
    }

    // ===========================================================
    // Fabricate faithful constants (headless-throwing, client-independent)
    // ===========================================================

    /** The default RGB color model. */
    public ColorModel getColorModel() {
        return ColorModel.getRGBdefault();
    }

    /** Custom cursors are unsupported — 0 colors, matching a minimal platform. */
    public int getMaximumCursorColors() {
        return 0;
    }

    /** Custom cursors are unsupported — {@code (0,0)}, matching a minimal platform. */
    public Dimension getBestCursorSize(int preferredWidth, int preferredHeight) {
        return new Dimension(0, 0);
    }

    public boolean isFrameStateSupported(int state) {
        return false;
    }

    /** Modal dialogs are supported (a UI fiber parks on them, {@code Dialog.parkUntilClose}). */
    public boolean isModalityTypeSupported(Dialog.ModalityType modalityType) {
        return true;
    }

    public boolean isModalExclusionTypeSupported(java.awt.Dialog.ModalExclusionType modalExclusionType) {
        return false;
    }

    public boolean isDynamicLayoutActive() {
        return false;
    }

    public boolean areExtraMouseButtonsEnabled() {
        return true;
    }

    // ===========================================================
    // Delegate to the headless JDK toolkit (works under java.awt.headless=true)
    // ===========================================================

    @SuppressWarnings("deprecation")
    public FontMetrics getFontMetrics(Font font) {
        // Verified: java.awt.Toolkit.getFontMetrics returns real metrics
        // headlessly (sun.font.FontDesignMetrics). Component.getFontMetrics
        // routes here too, so this is the single source of truth.
        return java.awt.Toolkit.getDefaultToolkit().getFontMetrics(font);
    }

    @SuppressWarnings("deprecation")
    public String[] getFontList() {
        return java.awt.Toolkit.getDefaultToolkit().getFontList();
    }

    /**
     * The system queue for this application — a {@link EventQueue} in ported
     * types (R_no_vaadin_in_api limb 1), not the JDK toolkit's real queue. Handing out the
     * JDK's was doubly wrong: the type is uncallable from import-swapped code,
     * and posting into a server JVM's AWT queue does nothing, where the ported
     * queue's static EDT primitives reach the Vaadin UI thread. The instance
     * methods that need an event pump WARN individually; see {@link EventQueue}.
     */
    public EventQueue getSystemEventQueue() {
        return EventQueue.systemQueue();
    }

    public Object getDesktopProperty(String propertyName) {
        return java.awt.Toolkit.getDefaultToolkit().getDesktopProperty(propertyName);
    }

    public boolean isAlwaysOnTopSupported() {
        return java.awt.Toolkit.getDefaultToolkit().isAlwaysOnTopSupported();
    }

    public static String getProperty(String key, String defaultValue) {
        return java.awt.Toolkit.getProperty(key, defaultValue);
    }

    // ===========================================================
    // Image loading via ImageIO (headless-capable)
    // ===========================================================

    public Image getImage(String filename) {
        return readImage(() -> ImageIO.read(new File(filename)), "getImage", filename);
    }

    public Image getImage(URL url) {
        return readImage(() -> ImageIO.read(url), "getImage", url);
    }

    public Image createImage(String filename) {
        return readImage(() -> ImageIO.read(new File(filename)), "createImage", filename);
    }

    public Image createImage(URL url) {
        return readImage(() -> ImageIO.read(url), "createImage", url);
    }

    public Image createImage(byte[] imagedata) {
        return createImage(imagedata, 0, imagedata.length);
    }

    public Image createImage(byte[] imagedata, int imageoffset, int imagelength) {
        return readImage(
                () -> ImageIO.read(new java.io.ByteArrayInputStream(imagedata, imageoffset, imagelength)),
                "createImage", "byte[" + imagelength + "]");
    }

    /** Producer-driven image creation is the paint pipeline — out of scope (R_match_swing_errors sub-bucket (b)). */
    public Image createImage(ImageProducer producer) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "createImage(ImageProducer)", producer);
        return null;
    }

    /** Images loaded via {@link ImageIO} are already fully realised — report loaded. */
    public boolean prepareImage(Image image, int width, int height, ImageObserver observer) {
        return true;
    }

    /** Images loaded via {@link ImageIO} are already fully realised — all bits present. */
    public int checkImage(Image image, int width, int height, ImageObserver observer) {
        return ImageObserver.ALLBITS;
    }

    private interface ImageReader {
        Image read() throws IOException;
    }

    private static Image readImage(ImageReader reader, String method, Object source) {
        try {
            Image img = reader.read();
            if (img == null) {
                log.debug("Toolkit.{}({}) — ImageIO returned no image", method, source);
            }
            return img;
        } catch (IOException | RuntimeException e) {
            log.debug("Toolkit.{}({}) — image load failed: {}", method, source, e.toString());
            return null;
        }
    }

    // ===========================================================
    // WARN and ignore — no web counterpart
    // ===========================================================

    /** Web pages do not beep at users. WARN and no-op. */
    public void beep() {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "beep (web pages do not beep)");
    }

    /** Nothing to flush in a server-rendered context. No-op (faithful). */
    public void sync() {
        // no-op
    }

    public void setDynamicLayout(boolean dynamic) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "setDynamicLayout", dynamic);
    }

    public void addAWTEventListener(AWTEventListener listener, long eventMask) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit",
                "addAWTEventListener (no server-side AWT event queue)", listener, eventMask);
    }

    public void removeAWTEventListener(AWTEventListener listener) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "removeAWTEventListener", listener);
    }

    public AWTEventListener[] getAWTEventListeners() {
        return new AWTEventListener[0];
    }

    public AWTEventListener[] getAWTEventListeners(long eventMask) {
        return new AWTEventListener[0];
    }

    public void addPropertyChangeListener(String name, PropertyChangeListener pcl) {
        java.awt.Toolkit.getDefaultToolkit().addPropertyChangeListener(name, pcl);
    }

    public void removePropertyChangeListener(String name, PropertyChangeListener pcl) {
        java.awt.Toolkit.getDefaultToolkit().removePropertyChangeListener(name, pcl);
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return java.awt.Toolkit.getDefaultToolkit().getPropertyChangeListeners();
    }

    public PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return java.awt.Toolkit.getDefaultToolkit().getPropertyChangeListeners(propertyName);
    }

    public Map<TextAttribute, ?> mapInputMethodHighlight(InputMethodHighlight highlight) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "mapInputMethodHighlight", highlight);
        return null;
    }

    /** Custom cursors are unsupported in the browser — returns the default cursor. */
    public Cursor createCustomCursor(Image cursor, Point hotSpot, String name) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "createCustomCursor", name);
        return Cursor.getDefaultCursor();
    }

    /** Drag-and-drop is the {@link vaadinx.swing.TransferHandler} path (D_drag_and_drop), not this SPI. */
    public <T extends DragGestureRecognizer> T createDragGestureRecognizer(
            Class<T> abstractRecognizerClass, DragSource ds, Component c, int srcActions,
            DragGestureListener dgl) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "createDragGestureRecognizer",
                abstractRecognizerClass);
        return null;
    }

    /** Printing is deferred. Returns null — the JDK's own "user cancelled" result. */
    public PrintJob getPrintJob(Frame frame, String jobtitle, Properties props) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "getPrintJob (printing deferred)", jobtitle);
        return null;
    }

    /** Printing is deferred. Returns null — the JDK's own "user cancelled" result. */
    public PrintJob getPrintJob(Frame frame, String jobtitle, JobAttributes jobAttributes,
                                PageAttributes pageAttributes) {
        EHelper.onUnimplemented("vaadinx.awt.Toolkit", "getPrintJob (printing deferred)", jobtitle);
        return null;
    }
}
