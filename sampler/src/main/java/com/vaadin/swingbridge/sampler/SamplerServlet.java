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

import com.github.mvysny.vaadin.tabscope.TabScope;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.RequestHandler;
import com.vaadin.flow.server.ServiceException;
import jakarta.servlet.annotation.WebServlet;
import vaadinx.swing.app.SwingBridgeVaadinServlet;

import java.util.ArrayList;
import java.util.List;

/**
 * Vaadin servlet for Sampler. The session-lock wrap every SB-Emulators deployment needs comes from
 * {@link SwingBridgeVaadinServlet}; what is left here is Sampler's own wiring — vaadin-tab-scope's
 * tab-close beacon handler ({@link TabScope#installTabCloseBeacon}), so a real last-tab close
 * reaches {@code WINDOW_CLOSING} promptly (D_shutdown_lifecycle) instead of at the session
 * idle-timeout.
 *
 * <p>Beacon handler: the app route is {@code @PreserveOnRefresh}
 * ({@code MainWindowRoute}), for which Flow ignores the unload beacon so an F5 can
 * re-adopt the UI — which also leaves the sole tab's scope un-orphaned until
 * idle-timeout. The handler starts tab-scope's ~60&nbsp;s orphan clock from the
 * beacon without closing the UI (F5 still re-adopts), so the reaper fires
 * {@code AppTab.onTabClosed} → {@code session.close()}. The app wires it, never
 * the library (R_no_spi_selfregister).
 */
@WebServlet(name = "sampler", urlPatterns = {"/*"}, asyncSupported = true)
public class SamplerServlet extends SwingBridgeVaadinServlet {

    @Override
    protected SwingBridgeVaadinServletService createSwingBridgeService(DeploymentConfiguration configuration)
            throws ServiceException {
        return new SwingBridgeVaadinServletService(this, configuration) {
            @Override
            protected List<RequestHandler> createRequestHandlers() throws ServiceException {
                // Copy into a mutable list: installTabCloseBeacon swaps the stock
                // UidlRequestHandler in place via List.set, and Flow's returned list
                // is not guaranteed writable.
                List<RequestHandler> handlers = new ArrayList<>(super.createRequestHandlers());
                TabScope.installTabCloseBeacon(handlers);
                return handlers;
            }
        };
    }
}
