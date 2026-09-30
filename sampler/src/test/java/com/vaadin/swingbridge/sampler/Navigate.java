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
import com.vaadin.flow.component.button.Button;
import com.vaadin.swingbridge.sampler.SamplerCatalogue.Demo;

import java.util.List;

/**
 * Drives the Sampler shell's nav the way a user does — one call per demo,
 * whatever depth it sits at:
 *
 * <pre>{@code
 * UI.getCurrent().navigate(SamplerRoute.class);
 * Navigate.to("Tables");   // expands the "Data" group, then clicks the leaf
 * }</pre>
 *
 * <p>The label is the {@link SamplerCatalogue} key, so a test never needs to
 * know which category a demo was filed under.
 */
final class Navigate {

    private Navigate() {}

    /**
     * Expands the demo's category if it is collapsed, then clicks its leaf
     * button.
     *
     * @param label a {@link Demo#label()} from {@link SamplerCatalogue#ALL}
     * @throws IllegalArgumentException if no demo carries that label
     */
    static void to(String label) {
        Demo demo = SamplerCatalogue.byLabel(label);
        if (demo.category() != null) {
            // Only click a header that reads as collapsed: the header toggles, so
            // clicking an already-expanded group would close it and hide the leaf.
            List<Button> collapsed = LocatorJ._find(Button.class,
                    spec -> spec.withText(SamplerNav.categoryLabel(demo.category(), false))
                            .withCount(0, 1));
            if (!collapsed.isEmpty()) {
                LocatorJ._click(collapsed.get(0));
            }
        }
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText(label)));
    }
}
