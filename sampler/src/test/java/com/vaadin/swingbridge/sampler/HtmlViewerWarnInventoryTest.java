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
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJEditorPane;
import vaadinx.EHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HtmlViewerPanel} WARN inventory exit gate — the read-only HTML viewer
 * slice (SD_hyperlink_listener hyperlink activation, D_htmleditorkit / SD_add_css_rule stylesheet, SD_setpage_audit setPage).
 * Fails if any {@code EHelper.onUnimplemented} fires along the asserted path.
 *
 * <p>Both client-side halves of this slice are invisible to Karibu — the
 * stylesheet is injected into the peer's shadow root and the link click is a
 * delegated DOM listener, neither of which exists browserless. So the gate drives
 * the seam the browser reaches ({@code addRule} → translated peer rules; the
 * {@code emul-hyperlink} DOM event) and asserts the server-side consequences, while
 * the rendering half is browser-verified and recorded in SD_hyperlink_listener / SD_add_css_rule.
 */
class HtmlViewerWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("HtmlViewer");
        dump("Step 0b (HtmlViewerPanel swap — three panes, one setPage fetch)", warnings);

        List<SJEditorPane> panes =
                LocatorJ._find(SJEditorPane.class, spec -> spec.withCount(4));
        SJEditorPane report = panes.get(0);
        SJEditorPane viewer = panes.get(1);
        // The page is the emulator's state (its document's), not the peer's.
        vaadinx.swing.JEditorPane viewerPane = (vaadinx.swing.JEditorPane) EHelper.getEmulator(viewer);
        SJEditorPane gated = panes.get(2);

        // --- Demo 1: the stylesheet installed at construction reached the peer ---
        assertEquals(
                List.of(".ql-editor { font-family: Georgia, serif; }",
                        ".ql-editor h2 { color: #336699; }"),
                report.getCssRules(),
                "ctor-time addRule calls should have reached the peer, translated");
        dump("Step 1 (ctor stylesheet → translated peer rules)", warnings);

        // ...and a rule added later, from the demo's own console, lands too.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Add rule")));
        assertEquals(3, report.getCssRules().size(), "the Add rule click should have applied one more");
        assertTrue(report.getCssRules().get(2).startsWith(".ql-editor li"),
                "expected the console's li rule, got " + report.getCssRules().get(2));
        LocatorJ._assertOne(com.vaadin.swingbridge.surrogates.SJLabel.class, spec -> spec.withPredicate(
                label -> label.getText().startsWith("Rules applied: 3")));
        dump("Step 2 (Add rule click → addRule → peer + status label)", warnings);

        // --- Demo 2: the help page loaded, and a link navigates ---
        assertTrue(viewer.getText().contains("Getting started"),
                "setPage should have loaded the help fixture, got: " + viewer.getText());
        assertTrue(viewerPane.getPage() != null && viewerPane.getPage().toString().endsWith("index.html"),
                "getPage should report the loaded fixture, was " + viewerPane.getPage());
        dump("Step 3 (setPage loaded the classpath help fixture)", warnings);

        // Follow a link exactly as the browser hook reports it — the href as
        // authored. The demo's listener maps the http URL onto a classpath page.
        fireHyperlinkDomEvent(viewer, "https://help.sampler.local/topics.html");
        assertTrue(viewer.getText().contains("Topics"),
                "the link should have loaded topics.html, got: " + viewer.getText());
        assertTrue(viewerPane.getPage().toString().endsWith("topics.html"),
                "getPage should follow the navigation, was " + viewerPane.getPage());
        dump("Step 4 (link → HyperlinkEvent → listener maps to classpath → setPage)", warnings);

        // Back returns to the previous page — app code driven by the same events.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Back")));
        assertTrue(viewerPane.getPage().toString().endsWith("index.html"),
                "Back should have returned to index.html, was " + viewerPane.getPage());
        dump("Step 5 (Back → previous page)", warnings);

        // The ISO-8859-1 fixture: its accents survive only if the charset came from
        // the meta declaration rather than the UTF-8 default (SD_setpage_audit).
        fireHyperlinkDomEvent(viewer, "https://help.sampler.local/latin1.html");
        assertTrue(viewer.getText().contains("café naïve façade"),
                "ISO-8859-1 fixture should decode via its meta charset, got: " + viewer.getText());
        assertTrue(viewer.getText().contains("Grüße"),
                "expected German umlauts intact, got: " + viewer.getText());
        dump("Step 6 (ISO-8859-1 page decoded from its meta charset)", warnings);

        // --- Demo 3: the editable gate matches Swing ---
        fireHyperlinkDomEvent(gated, "https://vaadin.com/docs");
        assertTrue(activationsLabel().startsWith("Activations: 1"),
                "a read-only pane should activate the link, label was: " + activationsLabel());
        dump("Step 7 (read-only pane activates the link)", warnings);

        gated.setEditable(true);
        fireHyperlinkDomEvent(gated, "https://vaadin.com/docs");
        assertTrue(activationsLabel().startsWith("Activations: 1"),
                "an editable pane must not activate links — the count should have stayed at 1, "
                        + "label was: " + activationsLabel());
        dump("Step 8 (editable pane does not activate — matches Swing)", warnings);

        // --- Demo 4: the element model answers the lookups migrated code makes ---
        LocatorJ._assertOne(com.vaadin.swingbridge.surrogates.SJLabel.class, spec -> spec.withPredicate(
                label -> label.getText().startsWith("Outline: <html>")
                        && label.getText().contains("<h2>")
                        && label.getText().contains("<p>")));
        dump("Step 9 (getDefaultRootElement walked at construction)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Find element by id")));
        String found = LocatorJ._get(com.vaadin.swingbridge.surrogates.SJLabel.class, spec -> spec.withPredicate(
                label -> label.getText().startsWith("<p> at offsets"))).getText();
        assertTrue(found.contains("Total due: 1 240.00 EUR"),
                "getElement(\"total\") should resolve to the paragraph, label was: " + found);
        dump("Step 10 (getElement(id) → element offsets + rendered text)", warnings);

        WarnDump.println();
        WarnDump.println("=== htmlviewer user-path WARN total: " + warnings.size() + " ===");
    }

    /** Demo 3's running activation count, as the demo renders it. */
    private static String activationsLabel() {
        return LocatorJ._get(com.vaadin.swingbridge.surrogates.SJLabel.class, spec -> spec.withPredicate(
                label -> label.getText().startsWith("Activations:"))).getText();
    }

    /** Dispatch the CustomEvent the peer's shadow-root click hook sends. */
    private static void fireHyperlinkDomEvent(SJEditorPane pane, String href) {
        var data = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        data.put("event.detail.href", href);
        pane.getElement().getNode()
                .getFeature(com.vaadin.flow.internal.nodefeature.ElementListenerMap.class)
                .fireEvent(new com.vaadin.flow.dom.DomEvent(
                        pane.getElement(), "emul-hyperlink", data));
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the HtmlViewer exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
