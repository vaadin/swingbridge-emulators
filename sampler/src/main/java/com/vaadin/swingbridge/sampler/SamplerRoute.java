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

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import vaadinx.swing.app.MainWindowRoute;

/**
 * Bootstraps the Sampler shell at the root path. Single route in the
 * app: the {@link SamplerFrame} {@code @MainWindow} JFrame renders
 * inline as the route content; nav-button clicks swap demo {@link
 * vaadinx.swing.JPanel}s into its content area.
 *
 * <p>{@code @PreserveOnRefresh}: the constructed {@link SamplerFrame}
 * keeps its content-swap state across page refreshes. {@code bootstrap()}
 * runs once per route instance, on initial attach (see
 * {@link MainWindowRoute}).
 */
@Route("")
@PageTitle("Swing-on-Vaadin Sampler")
@PreserveOnRefresh
public class SamplerRoute extends MainWindowRoute {

    @Override
    protected void bootstrap() {
        new SamplerFrame().setVisible(true);
    }
}
