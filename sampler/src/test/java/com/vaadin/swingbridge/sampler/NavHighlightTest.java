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

import com.github.mvysny.kaributesting.v10.ComboBoxKt;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.util.prefs.VaadinPreferencesFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exactly one nav button carries the active mark, whichever affordance moved it. */
class NavHighlightTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
        VaadinPreferencesFactory.setTestMode(true);
    }

    @AfterEach
    void tearDown() {
        VaadinPreferencesFactory.setTestMode(false);
        MockVaadin.tearDown();
    }

    @Test
    void one_active_mark_after_nav_clicks() {
        UI.getCurrent().navigate(SamplerRoute.class);
        assertActive("Home");
        Navigate.to("Tables");
        assertActive("Tables");
        Navigate.to("Lists");
        assertActive("Lists");
        Navigate.to("Printing");
        assertActive("Printing");
    }

    @Test
    void one_active_mark_after_a_jump_to_pick() {
        UI.getCurrent().navigate(SamplerRoute.class);
        assertActive("Home");

        ComboBoxKt.selectByLabel(
                LocatorJ._get(ComboBox.class, spec -> spec.withId(SamplerJumpTo.COMBO_NAME)),
                "Platform › Printing");
        assertActive("Printing");
    }

    private static void assertActive(String expectedLabel) {
        List<Button> marked = LocatorJ._find(Button.class, spec -> spec.withPredicate(
                b -> b.getElement().getStyle().get("border") != null));
        assertEquals(List.of(expectedLabel), marked.stream().map(Button::getText).toList(),
                "expected exactly one active nav mark");
    }
}
