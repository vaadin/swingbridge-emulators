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

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The app instance's list of every {@link vaadinx.awt.Window} constructed and not
 * yet collected — the store behind {@code Window.getWindows()},
 * {@code Window.getOwnerlessWindows()} and {@code Frame.getFrames()}. Driven from
 * three points of AWT's own call graph:
 *
 * <pre>{@code
 * WindowRegistry r = WindowRegistry.current();     // null off any app thread
 * r.register(w);   // Window ctor        <- JDK: Window.init() -> addToWindowList()
 * r.pin(w);        // Window.addNotify   <- JDK: allWindows.add(this)
 * r.unpin(w);      // Window.removeNotify<- JDK: allWindows.remove(this)
 * r.all();         // construction order, cleared entries dropped
 * }</pre>
 *
 * <p>AWT keeps <b>two</b> registries — a strong displayability-keyed
 * {@code allWindows} and a weak construction-keyed per-{@code AppContext} list —
 * and this is both, because they are never independent: {@code allWindows}' only
 * observable effect is <em>pinning</em> entries in the weak list, and a pinned
 * entry's weak reference cannot clear. So one list of entries carrying a nullable
 * strong field expresses the pair exactly, and {@link Entry#pin} <b>is</b>
 * {@code allWindows}.
 *
 * <p><b>We replicate AWT's leak on purpose.</b> A displayable-and-never-disposed
 * Window is held strongly, so an app that forgets to {@code dispose()} accumulates
 * windows here just as it did on the desktop. Only the bound differs: AWT's
 * {@code allWindows} is a JVM static and pins for the life of the process, while
 * this list dies with the browser tab — replicating is therefore both more
 * faithful and strictly safer than the thing it emulates.
 *
 * <p>Tab scope is replication, not approximation. AWT's public registry is
 * not a JVM static either — {@code addToWindowList()} stores it as
 * {@code appContext.put(Window.class, …)}, an attribute map on a scope narrower
 * than the JVM — so a tab-scoped {@code Attributes} keyed by a class is
 * structurally the same object.
 */
final class WindowRegistry implements Serializable {

    /**
     * One registered Window: {@link #ref} is the weak construction-keyed half,
     * {@link #pin} the strong displayability-keyed half, null exactly while the
     * Window is not displayable.
     *
     * <p>{@link #pin} is never <em>read</em> — it exists only to keep the
     * Window reachable, so a "this field is unused" cleanup silently converts
     * the strong half into nothing and no test goes red until one asserts on a
     * dereferenced displayable window. Deliberately not {@link Serializable}:
     * entries are reachable only through {@link WindowRegistry#entries}, which
     * is {@code transient}.
     */
    private static final class Entry {
        final WeakReference<vaadinx.awt.Window> ref;
        vaadinx.awt.Window pin;

        Entry(vaadinx.awt.Window w) {
            this.ref = new WeakReference<>(w);
        }
    }

    /**
     * Construction-ordered, because {@code getFrames()} is (measured on JDK 25).
     *
     * <p>{@code transient} for two reasons that both bite:
     * {@link WeakReference} is not serializable, and a strong {@link Entry#pin}
     * in a session attribute would drag the whole window tree — Vaadin peers
     * included — into session replication, the hazard {@code EHelper.IdCounter}
     * records for {@code AtomicInteger}. A deserialized registry starts empty.
     */
    private transient List<Entry> entries;

    private List<Entry> entries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    /**
     * Appends a weak entry, idempotently by identity — which is what lets
     * {@link #pin} double as a late registration for a Window constructed before
     * its store could be resolved (see {@link #current()}).
     */
    void register(vaadinx.awt.Window w) {
        if (find(w) == null) {
            entries().add(new Entry(w));
        }
    }

    /** Promotes {@code w}'s entry to a strong reference; registers it if absent. */
    void pin(vaadinx.awt.Window w) {
        register(w);
        final Entry e = find(w);
        if (e != null) {
            e.pin = w;
        }
    }

    /** Demotes {@code w}'s entry back to weak, leaving the entry in place. */
    void unpin(vaadinx.awt.Window w) {
        final Entry e = find(w);
        if (e != null) {
            e.pin = null;
        }
    }

    /**
     * Every registered Window still reachable, in construction order.
     *
     * <p>Cleared entries are dropped as they are met. The JDK's own
     * {@code getWindows} merely skips them, but it has a
     * {@code sun.java2d.Disposer} record removing them eventually and we have
     * no such thread — compacting on read is what bounds a long-lived tab's
     * list.
     */
    List<vaadinx.awt.Window> all() {
        final List<vaadinx.awt.Window> out = new ArrayList<>();
        final Iterator<Entry> it = entries().iterator();
        while (it.hasNext()) {
            final vaadinx.awt.Window w = it.next().ref.get();
            if (w == null) {
                it.remove();
            } else {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * Whether any registered Window is still {@linkplain
     * vaadinx.awt.Window#isDisplayable() displayable} — AWT's "no displayable
     * peers left" half of the auto-shutdown condition ({@link AutoShutdown}).
     *
     * <p>Reads the bit, not {@link Entry#pin}, equivalent as the two are
     * today: the pin is a reference-strength device this class owns, while
     * the bit is what AWT's own condition is written against.
     */
    boolean anyDisplayable() {
        for (vaadinx.awt.Window w : all()) {
            if (w.isDisplayable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Linear identity scan. A migrated app's window count is tens, and
     * every caller is on a user-interaction path — an identity map would add a
     * second structure to keep in step with the ordered list for no gain.
     */
    private Entry find(vaadinx.awt.Window w) {
        for (Entry e : entries()) {
            if (e.ref.get() == w) {
                return e;
            }
        }
        return null;
    }

    /**
     * This app instance's registry — the tab scope's ({@link AppInstance}, so it
     * survives an F5 teleport), falling back to the current {@link UI}, or
     * {@code null} when neither resolves.
     *
     * <p>The UI fallback is reached only where tab scope is unavailable, which per
     * R_no_spi_selfregister means a browserless Karibu test that did not opt in, or an app that never
     * wired {@link vaadinx.swing.app.SwingBridgeEmulatorsBootstrap}. Answering within one UI beats
     * answering "no windows" (D_never_fail_on_gaps: queries never fail), and the degradation is
     * confined to a configuration R_no_spi_selfregister already calls incorrect.
     *
     * <p>Returning {@code null} rather than throwing rests on a property,
     * not a hope: <b>the registry is only readable where it is writable.</b> A
     * context that cannot resolve a store to register a Window in its
     * constructor cannot resolve one to read {@code getWindows()} either, so
     * the two failures are one and the reader already answers empty there. A
     * {@code Window} constructor must not throw for this — it is not on R_match_swing_errors's
     * enumerated list, and a throw would crash where the JDK builds the window
     * fine. The residual drift (constructed under the UI store, read after tab
     * scope arrived) is closed by {@link #pin} re-registering.
     *
     * @return {@code null} on a thread with no app context — a {@code static}
     *     initializer, a raw background thread
     */
    static WindowRegistry current() {
        final WindowRegistry viaTab = AppInstance.getIfResolvable(
                WindowRegistry.class, WindowRegistry::new);
        if (viaTab != null) {
            return viaTab;
        }
        final UI ui = UI.getCurrent();
        if (ui == null) {
            return null;
        }
        WindowRegistry viaUI = ComponentUtil.getData(ui, WindowRegistry.class);
        if (viaUI == null) {
            viaUI = new WindowRegistry();
            ComponentUtil.setData(ui, WindowRegistry.class, viaUI);
        }
        return viaUI;
    }
}
