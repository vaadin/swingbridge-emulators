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

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vaadinx.EHelper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Per-UI snapshot of the browser's client-specific display facts that
 * {@link Toolkit}'s screen accessors need but a headless server JVM cannot
 * supply: the viewport size, device-pixel ratio, and whether the client is a
 * Mac / iOS device (used to choose the menu-shortcut modifier — {@code META}
 * on macOS / iOS, {@code CTRL} elsewhere).
 *
 * <p><b>Viewport, not physical screen (D_toolkit_screen_size).</b> {@link #viewportWidth()} /
 * {@link #viewportHeight()} carry the browser window's <em>inner</em> size
 * ({@code window.innerWidth/Height} via
 * {@link ExtendedClientDetails#getWindowInnerWidth()}), not the monitor's
 * {@code screen.width/height}. The coordinate space our windows place in
 * ({@code Window.setLocation/setBounds} → Dialog overlay top/left) is the
 * viewport, so every migrated screen-math idiom — centering, edge-anchored
 * toasts, size-to-fraction-of-screen — only computes correctly against the
 * viewport. Physical monitor size has no placement use in a browser.
 *
 * <p><b>Sourced from Vaadin's {@link ExtendedClientDetails}</b> — the same
 * client-details channel {@link com.vaadin.swingbridge.surrogates.BrowserTimeZone} uses, not a
 * bespoke {@code executeJs}. {@link #get()} reads the cached
 * {@code ExtendedClientDetails} synchronously when it is already populated
 * (which it is in any app that wired {@code BrowserTimeZone.fetch()} at UI
 * init — the migration guide requires it for dates), so the screen accessors
 * work on <em>any</em> thread, including at frame-construction time on the
 * Vaadin request thread. When the details have not yet been fetched, it parks
 * the calling virtual thread on a {@code refresh} round-trip (the
 * {@link vaadinx.awt.datatransfer.WebClipboard} VT-park pattern). See D_toolkit_full_surface, D_toolkit_from_client_details.
 *
 * <p><b>Live resize tracking (D_toolkit_screen_size).</b> The viewport changes when the user
 * resizes the browser window, so the first {@link #cache} for a UI also
 * registers a {@link com.vaadin.flow.component.page.Page#addBrowserWindowResizeListener
 * browser-window resize listener}; each resize event replaces the cached
 * snapshot's viewport (dpr / mac carry over) via {@link #onBrowserResize}.
 * The registration dies with the UI — no teardown needed. Bounded staleness:
 * resizes between page load and the first {@code get()} aren't tracked (no
 * listener yet, and Vaadin doesn't auto-refresh {@code ExtendedClientDetails}),
 * so the first read reports load-time values — self-heals from the first read
 * onward, and no UI-init step is required, per D_toolkit_from_client_details.
 *
 * <p><b>Cache only real values.</b> A populated/fetched (or
 * {@link #setTestInfo test-injected}) snapshot is cached per-UI via
 * {@link ComponentUtil#setData}; the {@link #DEFAULT fallback} returned when
 * the browser details are unavailable is <em>never</em> cached, so a later call
 * with details available upgrades to the real value.
 *
 * <p><b>Quiet fallback.</b> When the details are neither populated nor
 * fetchable (no current UI, or the caller is off a virtual-thread carrier and
 * the browser has not reported in yet), {@link #get()} returns {@link #DEFAULT}
 * and logs at {@code debug} — deliberately <em>not</em>
 * {@link vaadinx.EHelper#onUnimplemented}: screen metrics <em>are</em>
 * implemented, so the default is a graceful not-yet-known degrade (R_best_effort_behaviour/R_layouts_close_enough), and
 * a WARN on a normal construction path would trip the zero-stub-WARN exit gates.
 *
 * <p><b>macOS detection is iOS/iPadOS only.</b> {@link #mac()} is read off
 * {@link ExtendedClientDetails#getNavigatorPlatform() navigator.platform} the way
 * Vaadin's {@code isIOS()} (deprecated in 25.3) did: {@code iPhone} / {@code iPod} / {@code iPad},
 * or {@code MacIntel} with a touch screen (iPadOS reports itself as a Mac). Desktop
 * Mac clients ({@code MacIntel}, no touch) fall to {@code CTRL} for the menu-shortcut
 * modifier. Accepted limitation (R_best_effort_behaviour/R_layouts_close_enough) —
 * the accelerator modifier is cosmetic; revisit if a migration target needs Cmd
 * accelerators on desktop Mac.
 */
public record BrowserToolkitInfo(int viewportWidth, int viewportHeight,
                                 double devicePixelRatio, boolean mac) {

    private static final Logger log = LoggerFactory.getLogger(BrowserToolkitInfo.class);

    /**
     * Per-UI marker recording that the browser-window resize listener is
     * already installed — {@link ComponentUtil#setData} key. Guards against
     * re-registration when the cache upgrades after a dispose/re-read cycle.
     */
    private record ResizeListenerInstalled() {
    }

    /**
     * Fallback returned when the browser details are unavailable. A common
     * desktop-viewport ballpark at DPR 1.0, non-Mac ({@link #mac()} false →
     * the menu shortcut defaults to {@code CTRL}).
     */
    public static final BrowserToolkitInfo DEFAULT = new BrowserToolkitInfo(1920, 1080, 1.0, false);

    /**
     * The current UI's browser display snapshot. Returns the cached value if
     * present; else derives it synchronously from an already-populated
     * {@link ExtendedClientDetails}; else, on a virtual-thread carrier, fetches
     * the details (parking the VT) and caches the result; else returns
     * {@link #DEFAULT} (uncached, debug-logged).
     */
    public static BrowserToolkitInfo get() {
        UI ui = UI.getCurrent();
        if (ui != null) {
            BrowserToolkitInfo cached = ComponentUtil.getData(ui, BrowserToolkitInfo.class);
            if (cached != null) return cached;

            ExtendedClientDetails details = ui.getPage().getExtendedClientDetails();
            if (isPopulated(details)) {
                return cache(ui, from(details));
            }
            if (details != null && Thread.currentThread().isVirtual()) {
                BrowserToolkitInfo fetched = fetch(details);
                if (fetched != null) return cache(ui, fetched);
            }
        }
        log.debug("Browser display info unavailable (no UI, or details not yet reported and not on a "
                + "virtual-thread carrier); returning default {}. Wire BrowserTimeZone.fetch() at UI "
                + "init, or call screen accessors from inside a EHelper.callSwing seam, to get the live "
                + "browser value.", DEFAULT);
        return DEFAULT;
    }

    /**
     * Inject a deterministic snapshot for the current UI, bypassing the browser
     * round-trip. Used by tests; also usable by a host app that already knows
     * the client's display facts. Does NOT install the resize listener —
     * injected snapshots stay exactly as injected until the next injection or
     * {@link #clearCache()}.
     */
    public static void setTestInfo(int viewportWidth, int viewportHeight,
                                   double devicePixelRatio, boolean mac) {
        ComponentUtil.setData(UI.getCurrent(), BrowserToolkitInfo.class,
                new BrowserToolkitInfo(viewportWidth, viewportHeight, devicePixelRatio, mac));
    }

    /** Drop any cached snapshot for the current UI (test convenience). */
    public static void clearCache() {
        UI ui = UI.getCurrent();
        if (ui != null) ComponentUtil.setData(ui, BrowserToolkitInfo.class, null);
    }

    /** Vaadin uses {@code screenWidth == -1} as the "not yet reported" sentinel. */
    private static boolean isPopulated(ExtendedClientDetails details) {
        return details != null && details.getScreenWidth() != -1;
    }

    private static BrowserToolkitInfo from(ExtendedClientDetails d) {
        double dpr = d.getDevicePixelRatio();
        // Viewport from window.innerWidth/Height; the screen.* fallback is
        // belt-and-braces for a client payload that carried the populated
        // sentinel but no inner size (both normally arrive together).
        int w = d.getWindowInnerWidth() > 0 ? d.getWindowInnerWidth() : d.getScreenWidth();
        int h = d.getWindowInnerHeight() > 0 ? d.getWindowInnerHeight() : d.getScreenHeight();
        return new BrowserToolkitInfo(w, h, dpr <= 0 ? 1.0 : dpr, isAppleMobile(d));
    }

    /** iOS or iPadOS, as Vaadin's deprecated {@code isIOS()} computed it. */
    private static boolean isAppleMobile(ExtendedClientDetails d) {
        String platform = d.getNavigatorPlatform();
        if (platform == null) return false;
        return platform.startsWith("iPhone") || platform.startsWith("iPod")
                || platform.startsWith("iPad")
                || (platform.equals("MacIntel") && d.isTouchDevice());
    }

    private static BrowserToolkitInfo cache(UI ui, BrowserToolkitInfo info) {
        ComponentUtil.setData(ui, BrowserToolkitInfo.class, info);
        installResizeListener(ui);
        return info;
    }

    /**
     * Register the per-UI browser-window resize listener on first cache —
     * see class javadoc §"Live resize tracking". Idempotent via the
     * {@link ResizeListenerInstalled} marker so cache upgrades don't stack
     * duplicate registrations.
     */
    private static void installResizeListener(UI ui) {
        if (ComponentUtil.getData(ui, ResizeListenerInstalled.class) != null) return;
        ComponentUtil.setData(ui, ResizeListenerInstalled.class, new ResizeListenerInstalled());
        ui.getPage().addBrowserWindowResizeListener(
                e -> onBrowserResize(ui, e.getWidth(), e.getHeight()));
    }

    /**
     * Replace the cached snapshot's viewport with a freshly-reported browser
     * size, carrying dpr / mac over from the previous snapshot (a resize
     * doesn't change either). With no prior snapshot, dpr / mac derive from a
     * populated {@link ExtendedClientDetails} if available, else
     * {@link #DEFAULT}'s. Package-private: the production caller is the
     * resize listener installed by {@link #cache}; Karibu tests drive it
     * directly since MockVaadin has no browser to fire real resize events.
     * Runs under the UI lock (Vaadin fires resize listeners locked), so the
     * plain {@link ComponentUtil#setData} write is safe.
     */
    static void onBrowserResize(UI ui, int width, int height) {
        BrowserToolkitInfo previous = ComponentUtil.getData(ui, BrowserToolkitInfo.class);
        if (previous == null) {
            ExtendedClientDetails details = ui.getPage().getExtendedClientDetails();
            previous = isPopulated(details) ? from(details) : DEFAULT;
        }
        ComponentUtil.setData(ui, BrowserToolkitInfo.class,
                new BrowserToolkitInfo(width, height, previous.devicePixelRatio(), previous.mac()));
    }

    /**
     * Park the calling virtual thread on an {@link ExtendedClientDetails#refresh
     * refresh} round-trip (same channel {@link com.vaadin.swingbridge.surrogates.BrowserTimeZone}
     * uses). The callback completes a plain {@link CompletableFuture} (no
     * {@code DeadlockDetectingCompletableFuture} — same reasoning as
     * {@link vaadinx.awt.datatransfer.WebClipboard}'s D_clipboard_future_bridge bridge): the VT
     * parks on {@code future.get()}, the carrier dismounts and releases the
     * session lock, the browser response delivers, the callback fires and
     * completes the future, the VT remounts. Returns null on failure so
     * {@link #get()} falls back to {@link #DEFAULT}.
     */
    private static BrowserToolkitInfo fetch(ExtendedClientDetails stale) {
        CompletableFuture<ExtendedClientDetails> future = new CompletableFuture<>();
        stale.refresh(future::complete);
        try {
            ExtendedClientDetails details = EHelper.awaitBrowserRoundTrip(UI.getCurrent(), future);
            return isPopulated(details) ? from(details) : null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException ee) {
            log.debug("Browser display info fetch failed before the browser responded", ee.getCause());
            return null;
        }
    }
}
