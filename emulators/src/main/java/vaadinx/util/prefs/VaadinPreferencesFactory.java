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

package vaadinx.util.prefs;

import com.github.mvysny.blockingdialogs.UIFibers;
import com.vaadin.flow.server.VaadinSession;

import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Plugs vaadinx's {@code localStorage}-backed preferences into the JDK via the
 * standard {@link PreferencesFactory} SPI. Registered in
 * {@code META-INF/services/java.util.prefs.PreferencesFactory}, so the JDK
 * selects it JVM-wide with zero host configuration — migrated code keeps calling
 * {@code Preferences.userRoot()} / {@code userNodeForPackage(...)} unchanged,
 * with no import swap ({@code java.util.prefs} is not {@code javax.swing}).
 *
 * <p>The JDK consults the {@code -Djava.util.prefs.PreferencesFactory} system
 * property <i>before</i> this ServiceLoader entry, so a deployment that shares
 * its JVM with non-UI code using {@code Preferences} for real persistence can
 * override us for free. See D_preferences for the transparent-SPI-with-override rationale.
 *
 * <p><b>App-scoped vs. JVM-scoped (D_prefs_scope_split).</b> The SPI is selected JVM-wide, so
 * this factory also fields {@code Preferences} calls that have nothing to do with
 * a migrated Swing app — most concretely Vaadin's commercial-component license
 * checker, which reads {@code userNodeForPackage(...)} off any UI when an
 * RTE-backed {@code JEditorPane} renders. {@link #userRoot()} classifies by
 * whether an {@link vaadinx.EmulatorContext} is active on the calling thread:
 * present ⇒ migrated app code, whose prefs live in the browser's
 * {@code localStorage} ({@link PrefsCache}); absent ⇒ a JVM-scoped caller, routed
 * to a process-local {@link JvmLocalPreferences} that needs no UI and never
 * throws.
 *
 * <p>Must be {@code public} with a public no-arg constructor for
 * {@link java.util.ServiceLoader}.
 */
public final class VaadinPreferencesFactory implements PreferencesFactory {

    /**
     * {@inheritDoc} App-scoped ⇒ per-UI, backed by the browser's
     * {@code localStorage}; JVM-scoped ⇒ the in-memory {@link JvmLocalPreferences}.
     * See D_prefs_scope_split. The discriminator is <b>whether we can safely serve
     * localStorage</b>, i.e. whether the session lock is held:
     *
     * <ul>
     *   <li><b>A current session whose lock we hold</b> — real UI-thread access
     *       or an {@code EHelper.callSwing} continuation. App-scoped ⇒ {@link PrefsCache}.</li>
     *   <li><b>A current session we do <i>not</i> hold the lock on</b> — the RTE
     *       license checker (observed: a Jetty request-pool thread with the session
     *       set but no {@code UI} and no lock) and any other library that touches
     *       {@code Preferences} on a session-associated but unlocked thread.
     *       Anything that reads session/UI state here — even
     *       {@code EmulatorContext.getOrNull()}, which reads {@code BrowserTimeZone}
     *       off the session — throws Vaadin's "cannot access state … without
     *       locking", so this branch must decide on {@code hasLock()} alone (which
     *       needs no lock) and never touch the context. JVM-scoped ⇒
     *       {@link JvmLocalPreferences}.</li>
     *   <li><b>No current session but a propagated {@link vaadinx.EmulatorContext}</b>
     *       — app background code (a {@code SwingWorker}). App-scoped ⇒
     *       {@link BridgedPreferences}, which reaches localStorage through the
     *       session's live UI via {@link vaadinx.EHelper#callOnLiveUISync}
     *       (throws loud if no single live UI is reachable — D_prefs_scope_split).</li>
     *   <li><b>Nothing</b> — JVM init / a library off any Vaadin thread. JVM-scoped
     *       ⇒ {@link JvmLocalPreferences}.</li>
     * </ul>
     */
    @Override
    public Preferences userRoot() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            // Session current: app-scoped only if we hold its lock (real UI
            // thread / callSwing continuation). Unlocked ⇒ a non-app caller
            // (license checker) that must not be routed into PrefsCache — that
            // would throw "cannot access state ... without locking".
            if (!session.hasLock()) {
                return JvmLocalPreferences.root();
            }
            final PrefsCache cache = PrefsCache.current();
            // Warm before any node exists, outside the AbstractPreferences lock its *Spi calls
            // hold (see PrefsCache.ensureLoaded). Only inside a UI fiber, which can park: elsewhere
            // the first read throws, as it always has.
            if (UIFibers.isInUIFiber()) {
                cache.ensureLoaded();
            }
            return cache.root();
        }
        // No current session: a propagated EmulatorContext means app background
        // code (a SwingWorker), routed to a BridgedPreferences that reaches the
        // browser's localStorage through the session's live UI (D_prefs_scope_split Part 3);
        // nothing propagated means a JVM-scoped caller.
        vaadinx.EmulatorContext ctx = vaadinx.EmulatorContext.getOrNull();
        return ctx != null
                ? new BridgedPreferences(ctx.session())
                : JvmLocalPreferences.root();
    }

    /** {@inheritDoc} Deferred, inert node — see {@link SystemPreferences} / D_preferences. */
    @Override
    public Preferences systemRoot() {
        return SystemPreferences.ROOT;
    }

    // ===========================================================
    // Test seam
    // ===========================================================

    /**
     * Toggle in-memory test mode for Karibu unit tests: the per-UI cache treats
     * itself as warm-and-empty and every write stays server-side, bypassing the
     * {@code executeJs} browser round-trip (and its virtual-thread requirement).
     * A current UI is still required, so the off-context throw contract stays
     * testable. Mirrors {@code WebClipboard.setTestMode}. Production leaves it
     * alone.
     */
    public static void setTestMode(boolean testMode) {
        PrefsCache.testMode = testMode;
    }

    /** Whether {@link #setTestMode test mode} is currently engaged. */
    public static boolean isTestMode() {
        return PrefsCache.testMode;
    }
}
