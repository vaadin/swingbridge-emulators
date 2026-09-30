/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.EventQueue
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

import vaadinx.EHelper;

import java.lang.reflect.InvocationTargetException;

/**
 * Emulator for {@link java.awt.EventQueue}. Three EDT primitives —
 * {@link #invokeLater}, {@link #invokeAndWait}, {@link #isDispatchThread} —
 * delegate to {@link vaadinx.swing.SwingUtilities} and bottom out in
 * {@code UI.getCurrent() != null} (the Vaadin UI-thread proxy);
 * {@link #getMostRecentEventTime} returns {@link System#currentTimeMillis()}.
 * The remainder of the JDK surface (instance dispatch machinery,
 * {@code postEvent}, {@code peekEvent}, {@code getNextEvent},
 * {@code dispatchEvent}, {@code getCurrentEvent}, {@code push} / {@code pop},
 * {@code createSecondaryLoop}) is present so migrated code compiles, but
 * each method WARNs via {@link EHelper#onUnimplemented} and returns a safe
 * default — no AWT EventQueue dispatch thread exists server-side, so the
 * dispatch contract can't be honoured. This matches R_match_swing_errors / D_never_fail_on_gaps: log the gap,
 * never crash the host app.
 *
 * <p><b>Why ported despite not being Component-shaped.</b> The JDK
 * {@code EventQueue} bottoms out in an AWT EventQueue dispatch thread
 * that doesn't exist server-side; {@code EventQueue.invokeLater(...)}
 * against the JDK class would silently queue the runnable and never run
 * it. The porting rule is "port what would silently fail server-side;
 * leave what works unchanged on the JDK" — EventQueue's static EDT
 * methods fall on the port side, and once the class is present the rest
 * of its API surface is added as WARN-stubs to avoid compile errors on
 * incidental references.
 */
public class EventQueue {

    public EventQueue() {
        EHelper.onUnimplemented("EventQueue", "<init>");
    }

    /** Marker ctor for {@link #systemQueue()} — see there for why it does not WARN. */
    private EventQueue(boolean system) {}

    private static final EventQueue SYSTEM = new EventQueue(true);

    /**
     * The single queue {@link Toolkit#getSystemEventQueue()} hands out.
     *
     * <p>Silent where the public constructor WARNs: a migrator asking the
     * toolkit for the system queue has done nothing unimplemented — the
     * static EDT primitives on it work — so the WARN belongs on the instance
     * methods that need an event pump, which have their own.
     */
    static EventQueue systemQueue() {
        return SYSTEM;
    }

    /**
     * @return {@code true} when called from a Vaadin UI thread context.
     * @see vaadinx.swing.SwingUtilities#isEventDispatchThread()
     */
    public static boolean isDispatchThread() {
        return vaadinx.swing.SwingUtilities.isEventDispatchThread();
    }

    /**
     * Posts the runnable to the Vaadin UI thread asynchronously. See
     * {@link vaadinx.swing.SwingUtilities#invokeLater} for the full
     * dispatch contract, including the background-thread case.
     *
     * @throws IllegalStateException on a thread with neither a current UI nor an
     *         {@link vaadinx.EmulatorContext}.
     */
    public static void invokeLater(Runnable runnable) {
        vaadinx.swing.SwingUtilities.invokeLater(runnable);
    }

    /**
     * Posts the runnable to the Vaadin UI thread and blocks the calling
     * thread until it has run. See {@link vaadinx.swing.SwingUtilities#invokeAndWait}
     * for the full dispatch contract; in particular, calling from the
     * EDT throws {@link IllegalStateException} (D_gap_severity_triage case 2: the loom
     * rescue trigger).
     *
     * @throws IllegalStateException when called from the EDT, or on a thread with
     *         no {@link vaadinx.EmulatorContext}.
     * @throws InvocationTargetException if the runnable throws.
     * @throws InterruptedException if the wait is interrupted.
     * @throws vaadinx.BrowserSessionClosedError if the session has no live UI to run
     *         the runnable on.
     */
    public static void invokeAndWait(Runnable runnable)
            throws InterruptedException, InvocationTargetException {
        vaadinx.swing.SwingUtilities.invokeAndWait(runnable);
    }

    /**
     * Approximation of "time of the most recently dispatched AWT event."
     * We have no AWT event pump, so we return the current wall-clock
     * time; migrated code that uses this for event-recency comparisons
     * sees a monotonically advancing value, which is the closest faithful
     * behaviour without a real event queue.
     */
    public static long getMostRecentEventTime() {
        return System.currentTimeMillis();
    }

    public static java.awt.AWTEvent getCurrentEvent() {
        EHelper.onUnimplemented("EventQueue", "getCurrentEvent");
        return null;
    }

    public void postEvent(java.awt.AWTEvent event) {
        EHelper.onUnimplemented("EventQueue", "postEvent", event);
    }

    public java.awt.AWTEvent getNextEvent() throws InterruptedException {
        EHelper.onUnimplemented("EventQueue", "getNextEvent");
        return null;
    }

    public java.awt.AWTEvent peekEvent() {
        EHelper.onUnimplemented("EventQueue", "peekEvent");
        return null;
    }

    public java.awt.AWTEvent peekEvent(int id) {
        EHelper.onUnimplemented("EventQueue", "peekEvent", id);
        return null;
    }

    protected void dispatchEvent(java.awt.AWTEvent event) {
        EHelper.onUnimplemented("EventQueue", "dispatchEvent", event);
    }

    public void push(EventQueue newEventQueue) {
        EHelper.onUnimplemented("EventQueue", "push", newEventQueue);
    }

    /**
     * Real {@link java.awt.EventQueue#pop()} throws
     * {@link java.util.EmptyStackException} when called on a non-pushed
     * queue. Without an event-pump stack to interrogate, we WARN and
     * return — pop is a paired operation with {@link #push}, and push
     * already WARNs.
     */
    protected void pop() {
        EHelper.onUnimplemented("EventQueue", "pop");
    }

    public java.awt.SecondaryLoop createSecondaryLoop() {
        EHelper.onUnimplemented("EventQueue", "createSecondaryLoop");
        return null;
    }
}
