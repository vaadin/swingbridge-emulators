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

package vaadinx.swing.app;

import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.WrappedSession;
import com.github.mvysny.blockingdialogs.uifiber.loom.VirtualThreadAwareLock;

import java.util.concurrent.locks.Lock;

/**
 * The servlet a migrated app deploys on a plain servlet container, carrying the one piece of
 * container wiring SB-Emulators requires — a virtual-thread-aware session lock:
 *
 * <pre>{@code
 * @WebServlet(urlPatterns = "/*", asyncSupported = true)
 * public class AppServlet extends SwingBridgeVaadinServlet {}
 * }</pre>
 *
 * <p>That empty subclass is the whole host-app servlet. It exists only to carry the app's own
 * {@code @WebServlet} mapping, which is the app's to choose.
 *
 * <p><b>Why the lock has to be wrapped.</b> {@code EHelper.callSwing} runs every peer→Swing
 * callback as a UI fiber — a virtual thread — so a modal dialog can park, and the session lock is
 * held by the *carrier* platform thread rather than by the virtual thread mounted on it — so a
 * virtual thread taking that lock would deadlock its session. {@link VirtualThreadAwareLock} makes
 * both the check and the acquisition work; the loom runner's {@code SessionLockCheck} refuses a
 * deployment that skipped it, at each session's first request (R_match_swing_errors case (9),
 * D_vt_aware_session_lock). This class is how an app stops having to know that.
 *
 * <p><b>It deliberately installs no {@code ErrorHandler}</b> — that is app policy, not runtime
 * plumbing. Install one from a {@link com.vaadin.flow.server.SessionInitListener}, which reaches
 * the same place as overriding {@code createVaadinSession} because
 * {@code ErrorEvent.findErrorHandler} reads the session's handler when the error happens
 * (D_library_servlet).
 *
 * <p><b>On Spring, do not use this class</b> — the servlet has to extend {@code SpringServlet} so
 * Vaadin's own bean-based route and instantiator discovery keeps working. See
 * {@code :emulators-spring}, which auto-configures the equivalent.
 *
 * <p><b>An app needing its own {@code VaadinServletService} overrides
 * {@link #createSwingBridgeService}, not {@code createServletService}</b> — which is {@code final}
 * here, so the wrap cannot be dropped and {@code service.init()} cannot be forgotten.
 * {@code SamplerServlet} is the worked example. An app whose service must extend something else
 * entirely cannot use this class at all: extend {@code VaadinServlet} and apply
 * {@link VirtualThreadAwareLock#wrap} in your own {@code getSessionLock}, which is public for
 * exactly that case.
 */
public class SwingBridgeVaadinServlet extends VaadinServlet {

    /**
     * {@code final} on purpose: this is the seam through which the session-lock wrap could be lost
     * silently, and losing it surfaces later and elsewhere as a {@code StackOverflowError} in
     * Vaadin internals. Override {@link #createSwingBridgeService} instead.
     */
    @Override
    protected final VaadinServletService createServletService(DeploymentConfiguration configuration)
            throws ServiceException {
        VaadinServletService service = createSwingBridgeService(configuration);
        // Not optional, and the step most easily lost when this class is hand-written: without it
        // the service has no instantiator, no router and no dependency filters, and the first
        // request fails far from the cause.
        service.init();
        return service;
    }

    /**
     * The service this servlet runs on, before {@code init()}. Override to add your own service
     * behaviour; the return type is what keeps the session-lock wrap in place whatever you return.
     *
     * @return a new instance, uninitialized — {@link #createServletService} calls {@code init()}
     */
    protected SwingBridgeVaadinServletService createSwingBridgeService(DeploymentConfiguration configuration)
            throws ServiceException {
        return new SwingBridgeVaadinServletService(this, configuration);
    }

    /**
     * A {@link VaadinServletService} whose session lock is virtual-thread-aware. Public because an
     * app with its own service behaviour has to extend it rather than replace it — see
     * {@link #createSwingBridgeService}.
     */
    public static class SwingBridgeVaadinServletService extends VaadinServletService {

        public SwingBridgeVaadinServletService(VaadinServlet servlet, DeploymentConfiguration configuration) {
            super(servlet, configuration);
        }

        @Override
        protected final Lock getSessionLock(WrappedSession wrappedSession) {
            return VirtualThreadAwareLock.wrap(this, wrappedSession, super.getSessionLock(wrappedSession));
        }
    }
}
