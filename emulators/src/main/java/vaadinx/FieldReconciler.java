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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Detects and repairs <b>direct writes to the JDK-shaped protected fields</b> the emulators
 * carry per D_instance_field_surface — {@code slider.sliderModel = m}, {@code this.orientation
 * = VERTICAL} in a migrated subclass — which bypass the setter and therefore never reach the
 * Vaadin peer.
 *
 * <p><b>Why this exists at all:</b> in real Swing the JDK's method bodies read those fields at
 * use time, so a direct write mostly takes effect at the next repaint (minus the listener
 * rewiring the setter would have done — the idiom is partially broken on the desktop too). The
 * emulator's peer has no repaint that re-reads fields, so without detection a direct write
 * would do <i>nothing</i>, which is worse than the JDK. The reconcile maps "next repaint" to
 * SB-Emulators' equivalents:
 *
 * <ul>
 *   <li><b>Steady state:</b> {@link EHelper#callSwing} reconciles the session's registered
 *       emulators in its epilogue (outermost envelope only) — every peer→Swing event, Timer
 *       fire, SwingWorker callback and post-park continuation funnels through callSwing
 *       (R_callswing_envelope), so the write is repaired inside the very roundtrip that made
 *       it, before the response flushes.</li>
 *   <li><b>Bootstrap:</b> {@link #register} arms a <em>one-shot</em>
 *       {@code beforeClientResponse} on the current UI, catching field writes made while
 *       the app builds its UI during navigation attach, before any callSwing has run.
 *       <b>Never re-armed from inside the callback:</b> Flow runs registrations made during
 *       the flush in the same pass (StateTree loops to a fixpoint), so a self-rearming
 *       callback livelocks — measured 2026-08-31, see D_field_write_reconcile.</li>
 * </ul>
 *
 * <p>A detected write logs a loud ERROR (not the {@link EHelper#onUnimplemented} WARN lane —
 * this is not an SB-Emulators coverage gap but an app antipattern that causes stale data on the desktop
 * as well) and then repairs by re-running the setter-equivalent push, which is deliberately
 * <em>more</em> than the JDK does (the JDK leaves the setter's listener rewiring broken
 * forever) — the accepted R_no_silent_improvements wobble the ERROR names.
 *
 * <p>The registry is <b>session</b>-scoped (surviving the F5 {@code @PreserveOnRefresh}
 * teleport, like D_session_scoped_pools) and weakly referenced, so disposed frames drop out on
 * their own. All entry points run under the session lock — callSwing's epilogue and Flow's
 * beforeClientResponse both hold it — so a plain WeakHashMap-backed set suffices.
 */
public final class FieldReconciler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FieldReconciler.class);

    private FieldReconciler() {
    }

    /** One emulator's reconcile hook; implemented by each class carrying reconciled fields. */
    public interface Reconcilable {
        /**
         * Compare each JDK-shaped protected field against what was last pushed to the peer
         * (last-pushed shadow for values, the peer's own reference for models); on mismatch,
         * push and {@link #reportDirectWrite report}. Runs under the session lock.
         */
        void reconcileFields();
    }

    private static final String KEY = FieldReconciler.class.getName();

    /**
     * Adds the emulator to its session's reconcile set and arms the one-shot bootstrap check
     * for the current UI's next flush. Call once, from the emulator's root constructor. No-ops
     * without a current session (bare unit construction) — in production and under Karibu one
     * is always current. Anchored on the UI, not the peer, so a peer not built yet still gets
     * its check.
     */
    public static void register(Reconcilable emulator) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return;
        }
        registryOf(session).add(emulator);
        UI ui = UI.getCurrent();
        if (ui != null) {
            // One-shot: consumed at the first flush after construction, never re-armed
            // (see the class doc's livelock note). Steady state belongs to callSwing.
            ui.beforeClientResponse(ui, ctx -> emulator.reconcileFields());
        }
    }

    /** Reconciles every live registered emulator of this session. Requires the session lock. */
    @SuppressWarnings("unchecked")
    public static void reconcileAll(VaadinSession session) {
        Set<Reconcilable> registry = (Set<Reconcilable>) session.getAttribute(KEY);
        if (registry == null || registry.isEmpty()) {
            return;
        }
        // Snapshot: a reconcile's peer push may construct components that register.
        List<Reconcilable> snapshot = new ArrayList<>(registry);
        for (Reconcilable r : snapshot) {
            r.reconcileFields();
        }
    }

    /**
     * The loud half of a repaired direct write. One call per detected field per reconcile —
     * the repair itself resets the comparison, so a single write logs once.
     */
    public static void reportDirectWrite(Object emulator, String field, String setter) {
        log.error("{}.{} was assigned directly, bypassing {}(). Direct writes to this protected "
                        + "field skip validation, PropertyChangeEvents and listener rewiring in real Swing "
                        + "too (the UI goes stale until the next repaint there) — SB-Emulators has repaired the peer "
                        + "for this write, but the skipped notifications are gone. Call {}() instead.",
                emulator.getClass().getName(), field, setter, setter);
    }

    @SuppressWarnings("unchecked")
    private static Set<Reconcilable> registryOf(VaadinSession session) {
        Set<Reconcilable> registry = (Set<Reconcilable>) session.getAttribute(KEY);
        if (registry == null) {
            registry = Collections.newSetFromMap(new WeakHashMap<>());
            session.setAttribute(KEY, registry);
        }
        return registry;
    }
}
