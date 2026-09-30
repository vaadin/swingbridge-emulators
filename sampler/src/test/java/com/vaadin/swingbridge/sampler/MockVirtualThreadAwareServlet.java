/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockService;
import com.github.mvysny.kaributesting.v10.mock.MockVaadinServlet;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.WrappedSession;
import com.github.mvysny.blockingdialogs.uifiber.loom.VirtualThreadAwareLock;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import com.vaadin.swingbridge.surrogates.BrowserTimeZone;

import java.time.ZoneOffset;
import java.util.concurrent.locks.Lock;

/**
 * Test analog of {@link SamplerServlet}: routes the session lock through
 * {@link VirtualThreadAwareLock}. Required because vaadinx.EHelper.callSwing wraps every
 * peer→Swing event in a UI fiber (vaadin-blocking-dialogs' loom runner)
 * — the runner's {@code SessionLockCheck} refuses a session without the wrapper, which
 * the fiber needs: the session ReentrantLock is held by the carrier (the test thread), not by
 * the fiber's virtual thread (D_vt_aware_session_lock). The same shape as vaadin-blocking-dialogs'
 * own (unpublished) Karibu fixture of this name.
 *
 * <p>Used by every sampler test: the standard {@code MockVaadin.setup(routes)}
 * uses a plain {@code MockVaadinServlet}; for our codebase that breaks the
 * moment a click listener fires under {@code EHelper.callSwing}. Tests should
 * call {@link #setupMockVaadin(Routes)} which combines the VT-aware servlet
 * setup with a deterministic {@link BrowserTimeZone} fixture (SD_browser_timezone).
 */
public class MockVirtualThreadAwareServlet extends MockVaadinServlet {

    public MockVirtualThreadAwareServlet(@NotNull Routes routes) {
        super(routes);
    }

    /**
     * Standard MockVaadin setup for sampler tests: VT-aware servlet plus
     * a UTC {@link BrowserTimeZone} fixture so Date-bearing components
     * (`JSpinner(SpinnerDateModel)`, `JFormattedTextField` + `DateFormatter`)
     * don't throw on {@code BrowserDateUtils.toLocalDate}/{@code toDate} (SD_browser_timezone).
     * Production wiring is {@code BrowserTimeZone.fetch()} from a UI init
     * listener, but MockVaadin has no real browser to round-trip with —
     * UTC is picked over a DST-bearing zone so tests don't break twice
     * a year.
     */
    public static void setupMockVaadin(@NotNull Routes routes) {
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(routes));
        BrowserTimeZone.setZoneId(ZoneOffset.UTC);
    }

    @Override
    protected VaadinServletService createServletService(DeploymentConfiguration deploymentConfiguration) {
        VaadinServletService service = new VirtualThreadAwareMockService(this, deploymentConfiguration, getUiFactory());
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
