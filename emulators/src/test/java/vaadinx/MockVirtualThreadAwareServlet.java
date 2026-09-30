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

import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockService;
import com.github.mvysny.kaributesting.v10.mock.MockVaadinServlet;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.WrappedSession;
import com.github.mvysny.blockingdialogs.uifiber.loom.VirtualThreadAwareLock;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.Lock;

/**
 * Test servlet that routes the session lock through {@link VirtualThreadAwareLock}.
 * Required because {@link EHelper#callSwing} wraps every peer→Swing event in a
 * UI fiber (vaadin-blocking-dialogs' loom runner) — without the
 * wrapper, Vaadin's element-update path rejects writes from the VT since the session
 * {@link java.util.concurrent.locks.ReentrantLock} is held by the carrier (the
 * test thread), not by the VT itself, and a VT that takes the session lock recurses
 * until {@code StackOverflowError}. Mirror of the production
 * {@code SamplerServlet} from {@code :sampler}.
 *
 * <p>The {@code kotlin.jvm.functions.Function0} in the private ctor is
 * Karibu-Testing's own API surface showing through, not a leftover from this
 * class's Kotlin original — {@code MockService} takes a Kotlin function type for
 * the UI factory, and a Java method reference ({@code MockedUI::new}) satisfies it.
 */
public class MockVirtualThreadAwareServlet extends MockVaadinServlet {

    public MockVirtualThreadAwareServlet(@NotNull Routes routes) {
        super(routes);
    }

    @Override
    protected VaadinServletService createServletService(DeploymentConfiguration deploymentConfiguration) {
        VaadinServletService service =
                new VirtualThreadAwareMockService(this, deploymentConfiguration, getUiFactory());
        try {
            service.init();
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }
        getRoutes().register(service.getContext());
        return service;
    }

    private static class VirtualThreadAwareMockService extends MockService {
        VirtualThreadAwareMockService(@NotNull VaadinServlet servlet,
                                      @NotNull DeploymentConfiguration deploymentConfiguration,
                                      @NotNull Function0<? extends UI> uiFactory) {
            super(servlet, deploymentConfiguration, uiFactory);
        }

        @Override
        protected Lock getSessionLock(WrappedSession wrappedSession) {
            return VirtualThreadAwareLock.wrap(this, wrappedSession, super.getSessionLock(wrappedSession));
        }
    }
}
