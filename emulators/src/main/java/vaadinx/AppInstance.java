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

package vaadinx;

import com.github.mvysny.vaadin.tabscope.TabScope;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Attributes;
import com.vaadin.flow.server.VaadinSession;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Storage whose lifetime is <b>one running app instance</b> — the home for what a
 * desktop Swing app kept in {@code static} fields (M1D_former_singletons). A migrated app's holder
 * is one line over it:
 *
 * <pre>{@code
 * public static FormerSingletons get() {
 *     return AppInstance.get(FormerSingletons.class, FormerSingletons::new);
 * }
 * }</pre>
 *
 * <p>Values are created on first touch, shared by everything that app does,
 * reachable from everywhere that app runs — its {@code WINDOW_CLOSING} cleanup
 * included — and gone when the app is. They survive an F5 and die with the browser
 * tab.
 *
 * <p>UI-thread-confined, with one exception: {@link #get} also resolves on the
 * request-less teardown threads, where a session is current but no UI is.
 *
 * <p>The store is {@link TabScope}, kept private so it can change: "one
 * running app instance" maps 1:1 onto a browser tab only while SB-Emulators runs one app
 * per session (D_active_ui_pointer). Not inlined to {@code TabScope.getCurrent().getValues()} at
 * the call site, tempting as that looks — {@code getCurrent()} needs a current
 * {@link UI}, which D_shutdown_lifecycle shutdown does not have, so the inlined form throws in
 * exactly the handler that flushes caches and saves preferences. That is not an
 * upstream oversight to wait out: a session may hold several tab scopes and the
 * library cannot know which is <em>the app</em>. {@link AppTab} does, which is
 * why resolution lands here.
 */
public final class AppInstance {

    private AppInstance() {
    }

    /**
     * The app instance's value of the given type, created by {@code factory} on
     * first touch and returned as-is afterwards.
     *
     * @param type    identity of the value within the app instance
     * @param factory invoked at most once per app instance; must not return
     *                {@code null}
     * @return never {@code null}
     * @throws IllegalStateException from a background thread ({@code
     *                               SwingWorker.doInBackground}, a raw {@code
     *                               Thread}), or before the app's tab scope is ready
     *                               — a {@code static} initializer, or an app that
     *                               never wired {@link vaadinx.swing.app.SwingBridgeEmulatorsBootstrap}.
     *                               The message names the likely cause.
     */
    public static <T> T get(Class<T> type, Supplier<T> factory) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        final Attributes values = values();
        final T existing = values.getAttribute(type);
        if (existing != null) {
            return existing;
        }
        final T created = Objects.requireNonNull(factory.get(),
                () -> "factory for " + type.getName() + " returned null");
        values.setAttribute(type, created);
        return created;
    }

    /**
     * As {@link #get}, but {@code null} instead of a throw when no store resolves
     * on this thread.
     *
     * <p>Library-internal, and deliberately not public: a migrator reading
     * app-instance state on the wrong thread wants {@link #get}'s message naming
     * the cause, not a silent null. The soft path exists for SB-Emulators' own bookkeeping
     * — {@code WindowRegistry}, whose write site is a {@code Window} constructor
     * that R_match_swing_errors does not license to throw.
     */
    static <T> T getIfResolvable(Class<T> type, Supplier<T> factory) {
        try {
            return get(type, factory);
        } catch (IllegalStateException noStore) {
            return null;
        }
    }

    /**
     * The app instance's value store: the tab scope's own {@link Attributes} when a
     * UI is current, else {@link AppTab}'s handle on that same object.
     *
     * <p>The UI branch re-registers the handle on every call rather than
     * once, so a first {@link #get} that beats {@link AppTab#markAppUI} still
     * ends up reachable at teardown. {@code AppTab} holds the values object and
     * not the scope on purpose: on the session-destroy path, tab-scope's destroy
     * listener and {@code JFrame}'s are two listeners whose relative order
     * nothing pins, and the scope nulls its own {@code values} reference once its
     * listeners have run.
     */
    private static Attributes values() {
        final UI ui = UI.getCurrent();
        if (ui != null) {
            final Attributes values = scopeValues();
            final AppTab app = ui.getSession() == null ? null : AppTab.forSession(ui.getSession());
            if (app != null) {
                app.rememberValues(values);
            }
            return values;
        }
        final VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            final AppTab app = AppTab.forSession(session);
            final Attributes remembered = app == null ? null : app.rememberedValues();
            if (remembered != null) {
                return remembered;
            }
            throw new IllegalStateException("AppInstance has no store for this session: the app never "
                    + "touched AppInstance while a UI was current, so there is nothing to resolve. If "
                    + "this is shutdown code, it is reading app-instance state the app never wrote.");
        }
        throw new IllegalStateException("AppInstance.get() needs the app's context and there is none on "
                + "this thread — neither a current UI nor a current VaadinSession. This is a background "
                + "thread (SwingWorker.doInBackground, a raw Thread), or a static initializer running at "
                + "class-load. Read what you need on the EDT before starting the work and pass it in.");
    }

    /** The current tab scope's values, with the two wiring mistakes named. */
    private static Attributes scopeValues() {
        try {
            return TabScope.getCurrent().getValues();
        } catch (RuntimeException e) {
            throw new IllegalStateException("AppInstance.get() could not resolve this app's tab scope. "
                    + "Either the app has not wired vaadinx.swing.app.SwingBridgeEmulatorsBootstrap (per R_no_spi_selfregister the app "
                    + "registers it, never the library), or this ran before the browser reported in — a "
                    + "static initializer or a constructor invoked at class-load rather than from UI "
                    + "code. Move the read into a frame constructor, a listener body, or mainUI().", e);
        }
    }
}
