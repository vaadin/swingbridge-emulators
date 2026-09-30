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

/**
 * A thread about to <b>block on a browser round-trip</b> found no browser left to reach —
 * the tab is closed, or the session is going away.
 *
 * <p>Raised only where the caller's next move is to wait for an answer:
 * {@link EHelper#runInUIThread(boolean, Runnable)} with {@code callerPlansToBlockAfterwards} set.
 * An ordinary Swing→peer write never sees it and drops the effect instead
 * (R_decline_effect_only) — its state is stored and its event fires regardless, so there is
 * nothing to abort.
 *
 * <p>An {@code Error} on purpose, so {@code catch (Exception e)} — which migrated worker
 * bodies are full of — cannot swallow it and let the thread run on against a browser
 * that is gone. {@code SwingWorker.run} catches {@code Throwable} as the JDK's
 * {@code FutureTask} does, so this is still captured into the future and
 * D_uncaught_handler_chain is untouched. It is a deliberate carve-out from
 * R_match_swing_errors' {@code IllegalStateException} fallback.
 */
public class BrowserSessionClosedError extends java.awt.AWTError {

    private static final long serialVersionUID = 1L;

    public BrowserSessionClosedError(String message) {
        super(message);
    }

    /**
     * @param cause what revealed the session was gone — typically what
     *              {@code VaadinSession.accessSynchronously} threw, which is otherwise lost
     */
    public BrowserSessionClosedError(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
