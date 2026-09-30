/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.text.SimpleDateFormat
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.text;

import com.vaadin.flow.server.VaadinSession;
import vaadinx.EmulatorContext;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.text.AttributedCharacterIterator;
import java.text.DateFormatSymbols;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Import-swap emulator for {@link java.text.SimpleDateFormat}
 * ({@code java.text.SimpleDateFormat} → {@code vaadinx.text.SimpleDateFormat}).
 *
 * <p><b>Why this exists.</b> The Swing idiom {@code static final SimpleDateFormat F =
 * new SimpleDateFormat("yyyy-MM-dd")} is safe on the desktop — one JVM, one user,
 * one EDT touches it — but breaks two ways on the server:
 * <ol>
 *   <li><b>Thread-safety.</b> {@code format}/{@code parse} mutate an internal
 *       {@code Calendar}; a static instance is now shared across users and
 *       across virtual threads, so concurrent calls corrupt each other.</li>
 *   <li><b>Zone-correctness.</b> A plain {@code new SimpleDateFormat(...)} formats
 *       in the <em>server</em> JVM zone, not the user's <em>browser</em> zone —
 *       a silent day-off bug (server UTC, user UTC+13 → yesterday). It doesn't
 *       even throw; it's just quietly wrong.</li>
 * </ol>
 *
 * <p><b>How it stays correct while staying a drop-in.</b> This class extends
 * {@link java.text.SimpleDateFormat} so it's assignable everywhere a JDK
 * {@code DateFormat}/{@code Format} is, but it holds <em>no</em> live formatting
 * state of its own. It keeps a session-independent <b>configuration template</b>
 * (a plain JDK {@code SimpleDateFormat} used only as a prototype, never to
 * format), and every {@code format}/{@code parse} operates on a per-session
 * <b>backing</b> cloned from that template and pinned to the browser's time zone.
 * The instance itself is therefore safe to share JVM-wide (as a {@code static}),
 * because all mutable state lives in the session, not on the instance.
 *
 * <p><b>Thread-safety contract.</b> Formatting/parsing from a Vaadin UI thread
 * (or an {@link vaadinx.EHelper#callSwing} continuation) is fully thread-safe and
 * browser-zoned: the backing is reached only under the Vaadin session lock, which
 * serialises all such access. On a background thread (no {@link VaadinSession}) it
 * degrades to a per-call throwaway pinned to the server default zone — race-free
 * (thread-local) and never throwing, i.e. at least as safe as the original code
 * and using the exact zone the original static instance used on that same thread.
 *
 * <p><b>Configuration / freeze model.</b> Construction and configuration
 * ({@code applyPattern}, {@code setLenient}, {@code setTimeZone}, …) with no
 * current session write the template — this is the {@code static}-initialiser
 * window and never throws or reads a zone. Configuration from a live UI thread is
 * applied to that session's backing only (session-local; no cross-user leak). The
 * template <em>freezes</em> on first use (first format/parse or first backing
 * build); after that, reconfiguring from a background thread throws
 * {@link IllegalStateException} — silently rewriting a shared formatter for every
 * user is corruption the original single-JVM app never risked, so it fails loud
 * rather than degrading.
 *
 * <p>The backing is zoned via {@link TimeZone#getTimeZone(java.time.ZoneId)} —
 * the same arithmetic the {@code DatePicker}-backed surrogates convert with, so a
 * picked date formats as the day picked in every year, pre-1900 included.
 *
 * <p>Emulator-only — no surrogate. Like {@code Timer} / {@code SwingWorker}
 * (D_timer_swingworker), {@code SimpleDateFormat} is not a Component and has no Vaadin peer; the
 * stage-3 rewrite drops it entirely for {@code java.time} / {@code SHelper}.
 *
 * @deprecated This is transitional scaffolding produced by the import-swap. It
 * faithfully preserves a Swing anti-pattern (a shared mutable formatter) so the
 * app runs; hand-written and stage-3 code should format dates with
 * {@code com.vaadin.swingbridge.surrogates.util.BrowserDateUtils.formatAsISODate}/{@code formatAsDate} or
 * {@link java.time.format.DateTimeFormatter} instead, which are stateless.
 */
@Deprecated
public class SimpleDateFormat extends java.text.SimpleDateFormat {

    /** Session attribute holding the per-emulator backing registry. */
    private static final String SESSION_KEY = "emul.SimpleDateFormat.backings";

    /**
     * Session-independent configuration prototype. Never used to format — cloned
     * per session/per background call, then zone-pinned. Guarded by {@code this}
     * until {@link #frozen}; immutable and lock-free to read afterwards.
     */
    private java.text.SimpleDateFormat template;

    /**
     * Set once the template has been used to build any backing. A {@code volatile}
     * write in the freeze establishes happens-before for lock-free template reads;
     * a {@code true} read means no further template mutation can occur.
     */
    private volatile boolean frozen = false;

    /**
     * True once the migrator explicitly set a zone on the template (pre-freeze,
     * no session). When set, backings keep the template's explicit zone instead of
     * defaulting to the browser/server zone — the migrator's intent wins.
     */
    private volatile boolean explicitTimeZone = false;

    // ===========================================================
    // Constructors — mirror java.text.SimpleDateFormat. Each calls the matching
    // super ctor (for JDK-faithful pattern validation + a real, if unused,
    // inherited object) and builds the template from the same args. No session
    // or zone is touched, so construction never throws (resolves STUMBLE-2:
    // `new SimpleDateFormat("yyyy-MM-dd")` at class-load is always safe).
    // ===========================================================

    public SimpleDateFormat() {
        super();
        this.template = new java.text.SimpleDateFormat();
    }

    public SimpleDateFormat(String pattern) {
        super(pattern);
        this.template = new java.text.SimpleDateFormat(pattern);
    }

    public SimpleDateFormat(String pattern, Locale locale) {
        super(pattern, locale);
        this.template = new java.text.SimpleDateFormat(pattern, locale);
    }

    public SimpleDateFormat(String pattern, DateFormatSymbols formatSymbols) {
        super(pattern, formatSymbols);
        this.template = new java.text.SimpleDateFormat(pattern, formatSymbols);
    }

    // ===========================================================
    // Format / parse. All JDK entry points funnel through these two non-final
    // methods (the final format(Date)/format(Object)/parse(String)/parseObject
    // convenience methods delegate here), so overriding this pair is sufficient
    // to route every operation onto the session/background backing and away from
    // the decoy inherited state.
    // ===========================================================

    @Override
    public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition pos) {
        return operate().format(date, toAppendTo, pos);
    }

    @Override
    public Date parse(String text, ParsePosition pos) {
        return operate().parse(text, pos);
    }

    @Override
    public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        return operate().formatToCharacterIterator(obj);
    }

    /**
     * The JDK formatter a format/parse call should use.
     * <ul>
     *   <li>Live session → the cached, browser-zoned, session-local backing
     *       (reached under the session lock, so single-threaded per session).
     *       Throws {@link IllegalStateException} if {@code BrowserTimeZone.fetch()}
     *       was never called (SD_browser_timezone) — a UI-thread wiring bug with a one-line fix.</li>
     *   <li>No session (background thread) → a per-call throwaway pinned to the
     *       server default zone. Race-free and never throwing.</li>
     * </ul>
     */
    private java.text.SimpleDateFormat operate() {
        if (VaadinSession.getCurrent() != null) {
            return sessionBacking();
        }
        // Background thread: a per-call throwaway, zoned by EmulatorContext
        // (propagated user zone if the work was wrapped in ctx.run/call,
        // else the server default) — see buildBacking / EmulatorContext.
        return buildBacking();
    }

    // ===========================================================
    // Configuration + getters — route to the backing (live session) or the
    // template (no session), never to the decoy inherited state.
    // ===========================================================

    @Override
    public void applyPattern(String pattern) {
        configure(sdf -> sdf.applyPattern(pattern));
    }

    @Override
    public void applyLocalizedPattern(String pattern) {
        configure(sdf -> sdf.applyLocalizedPattern(pattern));
    }

    @Override
    public String toPattern() {
        return read(java.text.SimpleDateFormat::toPattern);
    }

    @Override
    public String toLocalizedPattern() {
        return read(java.text.SimpleDateFormat::toLocalizedPattern);
    }

    @Override
    public void setDateFormatSymbols(DateFormatSymbols newFormatSymbols) {
        configure(sdf -> sdf.setDateFormatSymbols(newFormatSymbols));
    }

    @Override
    public DateFormatSymbols getDateFormatSymbols() {
        return read(java.text.SimpleDateFormat::getDateFormatSymbols);
    }

    @Override
    public void set2DigitYearStart(Date startDate) {
        configure(sdf -> sdf.set2DigitYearStart(startDate));
    }

    @Override
    public Date get2DigitYearStart() {
        return read(java.text.SimpleDateFormat::get2DigitYearStart);
    }

    @Override
    public void setLenient(boolean lenient) {
        configure(sdf -> sdf.setLenient(lenient));
    }

    @Override
    public boolean isLenient() {
        return read(java.text.SimpleDateFormat::isLenient);
    }

    @Override
    public void setCalendar(java.util.Calendar newCalendar) {
        configure(sdf -> sdf.setCalendar(newCalendar));
    }

    @Override
    public java.util.Calendar getCalendar() {
        return read(java.text.SimpleDateFormat::getCalendar);
    }

    @Override
    public void setNumberFormat(java.text.NumberFormat newNumberFormat) {
        configure(sdf -> sdf.setNumberFormat(newNumberFormat));
    }

    @Override
    public java.text.NumberFormat getNumberFormat() {
        return read(java.text.SimpleDateFormat::getNumberFormat);
    }

    @Override
    public TimeZone getTimeZone() {
        return read(java.text.SimpleDateFormat::getTimeZone);
    }

    /**
     * Specialised {@link #configure}: a no-session write also records that the
     * zone is explicit, so backings stop defaulting to browser/server zone and
     * honour what the migrator set.
     */
    @Override
    public void setTimeZone(TimeZone zone) {
        if (VaadinSession.getCurrent() != null) {
            sessionBacking().setTimeZone(zone);
            return;
        }
        synchronized (this) {
            requireUnfrozen();
            template.setTimeZone(zone);
            explicitTimeZone = true;
        }
    }

    /**
     * Apply a mutation to the correct target: the session-local backing when a UI
     * thread is current, else the template (the {@code static}-init window). A
     * background-thread mutation after freeze throws — see class javadoc.
     */
    private void configure(Consumer<java.text.SimpleDateFormat> mutation) {
        if (VaadinSession.getCurrent() != null) {
            mutation.accept(sessionBacking());
            return;
        }
        synchronized (this) {
            requireUnfrozen();
            mutation.accept(template);
        }
    }

    /**
     * Read a property from the session-local backing when a UI thread is current,
     * else from the template. Template reads are synchronised because a pre-freeze
     * write may be racing on another thread; post-freeze the lock is uncontended.
     */
    private <T> T read(Function<java.text.SimpleDateFormat, T> getter) {
        if (VaadinSession.getCurrent() != null) {
            return getter.apply(sessionBacking());
        }
        synchronized (this) {
            return getter.apply(template);
        }
    }

    private void requireUnfrozen() {
        if (frozen) {
            throw new IllegalStateException(
                    "Cannot reconfigure a shared vaadinx.text.SimpleDateFormat from a "
                            + "background thread after it has been used: rewriting a "
                            + "JVM-wide formatter would silently corrupt date formatting "
                            + "for every user. Configure it at construction / static-init "
                            + "time, or reconfigure it from a Vaadin UI thread (where the "
                            + "change stays scoped to the current session).");
        }
    }

    // ===========================================================
    // Backing management.
    // ===========================================================

    /**
     * The current session's backing for this emulator, built and cached lazily.
     * The registry is one {@link WeakIdentityHashMap} per session, keyed by
     * emulator identity with <b>weak</b> keys: a formatter the app retains (the
     * canonical {@code static final} case) stays strongly reachable so its
     * backing is cached for the session, while a formatter created transiently
     * (e.g. {@code new SimpleDateFormat(...)} in a loop) becomes unreachable
     * after use and its entry is reclaimed — so a loop can't pollute the session.
     * Only ever touched under the session lock, so it needs no synchronisation.
     */
    private java.text.SimpleDateFormat sessionBacking() {
        VaadinSession session = VaadinSession.getCurrent();
        SessionBackings holder = (SessionBackings) session.getAttribute(SESSION_KEY);
        if (holder == null) {
            holder = new SessionBackings();
            session.setAttribute(SESSION_KEY, holder);
        }
        WeakIdentityHashMap<SimpleDateFormat, java.text.SimpleDateFormat> map = holder.map();
        java.text.SimpleDateFormat backing = map.get(this);
        if (backing == null) {
            // buildBacking may throw SD_browser_timezone (zone unfetched) — don't cache a failure.
            backing = buildBacking();
            map.put(this, backing);
        }
        return backing;
    }

    /**
     * Freeze the template and clone a fresh backing from it, zone-pinned. An
     * explicit template zone (the migrator called {@code setTimeZone}) is kept
     * as-is; otherwise the zone comes from {@link EmulatorContext#currentTimeZone()},
     * which resolves to the browser zone on a UI thread (throwing SD_browser_timezone if
     * unfetched), the propagated user zone on a context-carrying background
     * thread, or the server default on a bare background thread.
     */
    private java.text.SimpleDateFormat buildBacking() {
        java.text.SimpleDateFormat backing = cloneFrozenTemplate();
        if (!explicitTimeZone) {
            backing.setTimeZone(EmulatorContext.currentTimeZone());
        }
        return backing;
    }

    /**
     * Clone the template, freezing it on first call. Once {@link #frozen} is
     * visibly {@code true} the template is immutable, so subsequent clones run
     * lock-free; the freezing clone itself is synchronised so the transition and
     * the final template state publish safely.
     */
    private java.text.SimpleDateFormat cloneFrozenTemplate() {
        if (frozen) {
            return (java.text.SimpleDateFormat) template.clone();
        }
        synchronized (this) {
            frozen = true;
            return (java.text.SimpleDateFormat) template.clone();
        }
    }

    // ===========================================================
    // Object contract.
    // ===========================================================

    /**
     * Returns an independent emulator: its own deep-cloned template, its own
     * identity (hence its own session backings), and reset to unfrozen so the
     * clone is freely reconfigurable. {@code super.clone()} shallow-copies the
     * {@code template} reference, so it is re-cloned explicitly.
     */
    @Override
    public Object clone() {
        SimpleDateFormat copy = (SimpleDateFormat) super.clone();
        synchronized (this) {
            copy.template = (java.text.SimpleDateFormat) this.template.clone();
        }
        copy.frozen = false;
        // explicitTimeZone is copied by value by super.clone() — preserved.
        return copy;
    }

    /** Equality on the configuration prototype (the emulator's effective config). */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SimpleDateFormat other)) return false;
        return readTemplate().equals(other.readTemplate());
    }

    @Override
    public int hashCode() {
        return readTemplate().hashCode();
    }

    private synchronized java.text.SimpleDateFormat readTemplate() {
        return template;
    }

    /**
     * Per-session backing registry. Serializable so a passivating container
     * doesn't choke, but the map is {@code transient}: per-session formatter
     * state is intentionally not preserved across passivation (design decision),
     * and it rebuilds lazily and empty on reactivation. Keeping it transient also
     * avoids serialising the emulator keys (the migrator's {@code static} fields).
     */
    private static final class SessionBackings implements java.io.Serializable {
        private transient WeakIdentityHashMap<SimpleDateFormat, java.text.SimpleDateFormat> map;

        WeakIdentityHashMap<SimpleDateFormat, java.text.SimpleDateFormat> map() {
            if (map == null) {
                map = new WeakIdentityHashMap<>();
            }
            return map;
        }
    }

    /**
     * Minimal weak-<em>identity</em> map: keys are compared by {@code ==} (not
     * {@code equals}, which this emulator overrides for the SDF contract) and
     * held weakly, so an entry is reclaimed once its key is unreachable. Not
     * thread-safe and not serialisable — neither is needed: instances live only
     * in a {@code transient} session attribute touched under the session lock.
     * Supports just the {@code get}/{@code put} this class uses.
     */
    private static final class WeakIdentityHashMap<K, V> {

        private final HashMap<Key, V> map = new HashMap<>();
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

        /** Weak key that hashes and compares by referent identity. */
        private static final class Key extends WeakReference<Object> {
            private final int hash;

            Key(Object referent, ReferenceQueue<Object> queue) {
                super(referent, queue);
                this.hash = System.identityHashCode(referent);
            }

            @Override
            public int hashCode() {
                return hash;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Key other)) return false;
                Object mine = get();
                return mine != null && mine == other.get();
            }
        }

        V get(K key) {
            expunge();
            // Probe key: no queue registration — it's a throwaway for lookup only.
            return map.get(new Key(key, null));
        }

        void put(K key, V value) {
            expunge();
            map.put(new Key(key, queue), value);
        }

        /**
         * Drop entries whose key has been collected. The enqueued reference is
         * the exact {@link Key} instance stored in the map, so
         * {@link HashMap#remove} finds it by identity even though its referent
         * (and thus {@link Key#equals}) is now gone.
         */
        private void expunge() {
            for (java.lang.ref.Reference<?> ref; (ref = queue.poll()) != null; ) {
                map.remove(ref);
            }
        }
    }
}
