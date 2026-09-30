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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.sampler.SamplerCatalogue.Demo;
import vaadinx.EHelper;
import vaadinx.util.prefs.VaadinPreferencesFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-panel navigation gate. Walks every {@link SamplerCatalogue} entry and
 * asserts the corresponding panel constructs with zero
 * {@code EHelper.onUnimplemented} / {@code onUnsupportedPeerShape} WARNs. The
 * per-category {@code *WarnInventoryTest}s drive the user paths themselves; this
 * is the cheap "every nav target still loads" check that catches a new panel
 * silently regressing.
 *
 * <p>The roster is derived, not restated: a demo added to the catalogue is
 * covered here the moment it lands.
 */
class NavigationSmokeTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        // Custom servlet routes the session lock through VirtualThreadAwareLock — needed
        // because vaadinx.EHelper.callSwing runs every peer→Swing event as a UI fiber
        // (D_callswing_funnel). See the MockVirtualThreadAwareServlet javadoc.
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
        // The Preferences nav target does real pref I/O in its ctor; test mode
        // keeps the per-UI cache in-memory so no executeJs browser round-trip
        // (MockVaadin has no localStorage to warm from). See PreferencesPanel.
        VaadinPreferencesFactory.setTestMode(true);
    }

    @AfterEach
    void tearDown() {
        VaadinPreferencesFactory.setTestMode(false);
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
    }

    @Test
    void every_nav_target_loads_without_stub_warns() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // Navigate to the Sampler shell — constructs SamplerFrame + its nav
        // components + HomePanel as the default content.
        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        assertNoWarns("Sampler shell + HomePanel", warnings);

        for (Demo demo : SamplerCatalogue.ALL) {
            Navigate.to(demo.label());
            assertNoWarns(demo.label(), warnings);
        }

        // Round-trip back to Home: panels installed above had AncestorListener
        // teardown, and Home must still construct cleanly after they detach.
        Navigate.to("Home");
        assertNoWarns("Home (round-trip)", warnings);
    }

    private static void assertNoWarns(String label, List<String> warnings) {
        if (!warnings.isEmpty()) {
            String msg = "[" + label + "] " + warnings.size()
                    + " stub WARN(s) fired during nav swap: " + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
    }
}
