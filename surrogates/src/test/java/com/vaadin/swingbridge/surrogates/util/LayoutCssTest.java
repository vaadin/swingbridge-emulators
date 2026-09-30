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

package com.vaadin.swingbridge.surrogates.util;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the container-CSS undo added for {@code setLayout} — and the drift guard that
 * keeps {@link LayoutCss#containerKeys} the true union of what the builders write.
 */
class LayoutCssTest {

    /**
     * The guard the undo depends on: a builder that starts writing a property
     * absent from CONTAINER_KEYS would leave that property behind on every
     * subsequent {@code setLayout}, which is exactly the bug the reset exists to fix.
     */
    @Test
    @DisplayName("every builder writes only keys resetContainerCss clears")
    void everyBuilderWritesOnlyKeysResetContainerCssClears() {
        Set<String> union = Set.copyOf(LayoutCss.containerKeys());
        Map<String, Map<String, String>> builders = new LinkedHashMap<>();
        builders.put("verticalFallbackCss", LayoutCss.verticalFallbackCss());
        builders.put("flowLayoutCss", LayoutCss.flowLayoutCss(FlowLayout.LEFT, 5, 5));
        builders.put("boxLayoutCss", LayoutCss.boxLayoutCss(1));
        builders.put("borderLayoutCss", LayoutCss.borderLayoutCss(4, 8));
        builders.put("gridBagLayoutCss", LayoutCss.gridBagLayoutCss(new double[]{1.0}, new double[]{1.0}));
        builders.put("gridLayoutCss", LayoutCss.gridLayoutCss(2, 2, 3, 3));
        builders.put("groupLayoutCss", LayoutCss.groupLayoutCss("1fr", "auto", 6));

        builders.forEach((name, css) -> {
            Set<String> stray = new LinkedHashSet<>(css.keySet());
            stray.removeAll(union);
            assertTrue(stray.isEmpty(), name + " writes " + stray + ", absent from LayoutCss.containerKeys()");
        });
    }

    @Test
    @DisplayName("resetContainerCss drops every container-side property")
    void resetContainerCssDropsEveryContainerSideProperty() {
        Div div = new Div();
        LayoutCss.applyContainerCss(div.getElement(), "BorderLayout", LayoutCss.borderLayoutCss(4, 8));
        assertEquals("grid", div.getElement().getStyle().get("display"));

        LayoutCss.resetContainerCss(div.getElement());
        for (String key : LayoutCss.containerKeys()) {
            assertNull(div.getElement().getStyle().get(key), key + " survived the reset");
        }
    }

    @Test
    @DisplayName("resetContainerCss leaves non-layout properties alone")
    void resetContainerCssLeavesNonLayoutPropertiesAlone() {
        Div div = new Div();
        div.getElement().getStyle().set("color", "red");
        LayoutCss.applyContainerCss(div.getElement(), "FlowLayout", LayoutCss.flowLayoutCss(FlowLayout.CENTER, 5, 5));

        LayoutCss.resetContainerCss(div.getElement());
        assertEquals("red", div.getElement().getStyle().get("color"));
    }

    /** Non-div hosts never received layout CSS, so the undo is a silent no-op, not an unsupported-shape error. */
    @Test
    @DisplayName("resetContainerCss is a silent no-op on a non-div host")
    void resetContainerCssIsASilentNoOpOnANonDivHost() {
        Span span = new Span();
        span.getElement().getStyle().set("display", "inline-block");
        LayoutCss.resetContainerCss(span.getElement());
        assertEquals("inline-block", span.getElement().getStyle().get("display"));
    }
}
