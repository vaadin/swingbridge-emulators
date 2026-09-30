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
import vaadinx.EHelper;
import vaadinx.swing.JEditorPane;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JEditorPane} WARN inventory exit gate. Both tests fail if any
 * {@code EHelper.onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell, clicks
 *       "EditorPanes", asserts both demos construct WARN-free, drives an
 *       editable value-change on Demo 2's RTE peer to verify the peer→Document
 *       R_swing_is_truth sync + DocumentListener echo run WARN-free end-to-end, fires
 *       Demo 1's hyperlink activation through the same seam the browser hook
 *       reaches, and checks Demo 1's {@code HTMLEditorKit} stylesheet landed on
 *       the peer translated.</li>
 *   <li>{@link #inventory_jeditorpane_api_surface} — micro-driver over the
 *       WARN-free supported JEditorPane / SJEditorPane surface, including the
 *       HTMLEditorKit stylesheet idiom in both call orders. The expected-WARN
 *       paths (non-text/html contentTypes, URL ctors, a foreign EditorKit) are NOT
 *       exercised here — those are covered in the per-class unit tests.</li>
 *   <li>{@link #inventory_jtextpane_api_surface} — micro-driver over the
 *       WARN-free supported JTextPane surface (styled runs, alignment,
 *       ImageIcon embed, named styles). The drop-and-WARN / throw paths are
 *       covered in JTextPaneTest.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks are wired to a single sink so a stub
 * fire from either layer surfaces.
 */
class EditorPanesWarnInventoryTest {

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

        Navigate.to("EditorPanes");
        dump("Step 0b (EditorPanesPanel swap)", warnings);

        // Four SJEditorPane peers should now be in the DOM (JEditorPane Demo 1 +
        // Demo 2, and JTextPane Demo 3's editable pane + its read-only mirror — both
        // JTextPanes peer over SJEditorPane). withCount(4) fails the locate if a
        // future refactor drops a demo.
        java.util.List<com.vaadin.swingbridge.surrogates.SJEditorPane> panes =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJEditorPane.class, spec -> spec.withCount(4));
        dump("Step 1 (4 SJEditorPane peers found)", warnings);

        // Demo 1 is read-only; Demo 2 (JEditorPane) and Demo 3 (JTextPane) are both
        // editable. Grab the first editable pane in DOM order — Demo 2 — to drive
        // its HTML peer→Document sync.
        com.vaadin.swingbridge.surrogates.SJEditorPane editable = panes.stream()
                .filter(com.vaadin.swingbridge.surrogates.SJEditorPane::isEditable)
                .findFirst().orElseThrow();
        dump("Step 2 (editable JEditorPane located)", warnings);

        // Simulate a client edit: set the RTE value, which fans out through the
        // emulator's value-change listener → callSwing envelope → Document
        // mirror → DocumentListener echo → status JLabel write, all WARN-free.
        editable.setValue("<p>edited in the browser</p>");
        dump("Step 3 (editable value-change → Document sync → status JLabel update)", warnings);

        // Demo 1's read-only pane carries a HyperlinkListener. Drive the seam the
        // browser hook reaches — the "emul-hyperlink" DOM event — so the whole
        // activation path (peer fan-out → emulator bridge → status JLabel) is
        // covered WARN-free. The client half is shadow-DOM/executeJs and therefore
        // Karibu-invisible; it is browser-verified per the plan's P2.
        com.vaadin.swingbridge.surrogates.SJEditorPane readOnly = panes.stream()
                .filter(pane -> !pane.isEditable())
                .findFirst().orElseThrow();
        fireHyperlinkDomEvent(readOnly, "https://vaadin.com/docs");
        dump("Step 4 (hyperlink activation → HyperlinkEvent → status JLabel update)", warnings);

        // A WARN gate alone would also pass if the listener silently never fired,
        // so assert the demo's status label actually took the event.
        LocatorJ._assertOne(com.vaadin.swingbridge.surrogates.SJLabel.class, spec -> spec.withPredicate(
                label -> label.getText().contains("ACTIVATED")
                        && label.getText().contains("https://vaadin.com/docs")));

        // Demo 1's HTMLEditorKit stylesheet should have reached the same peer, with
        // "body" mapped onto the content element rather than left as a selector that
        // matches nothing inside the editor.
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of(
                        ".ql-editor { font-family: Georgia, serif; }",
                        ".ql-editor h2 { color: #336699; }",
                        ".ql-editor li { color: #555; }"),
                readOnly.getCssRules(),
                "the demo's addRule calls should have reached the peer, translated");
        dump("Step 5 (HTMLEditorKit stylesheet → translated rules on the peer)", warnings);

        WarnDump.println();
        WarnDump.println("=== editorpanes user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jeditorpane_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors -- (URL and (String url) ctors WARN per the design pass;
        //    covered in the per-class unit tests, not the inventory gate.)
        new JEditorPane();
        dump("JEditorPane  new JEditorPane()", warnings);

        new JEditorPane("text/html", "<p>hello</p>");
        dump("JEditorPane  new JEditorPane(String type, String text)", warnings);

        // -- Text round-trip via Document + htmlValue --
        JEditorPane p = new JEditorPane();
        p.setText("<h1>Title</h1>");
        p.getText();
        p.setText(null);   // normalises to empty
        p.getText();
        dump("JEditorPane  setText / getText round-trip", warnings);

        // -- contentType: only text/html is WARN-free --
        p.setContentType("text/html");
        p.getContentType();
        dump("JEditorPane  setContentType(text/html) round-trip", warnings);

        // -- editable toggle (both directions are WARN-free) --
        p.setEditable(false);
        p.setEditable(true);
        p.isEditable();
        dump("JEditorPane  setEditable toggle round-trip", warnings);

        // -- L&F surface --
        p.getUIClassID();
        p.updateUI();

        // Default Document install + getDocument readback — both must be
        // WARN-free since the inherited JTextComponent installs a PlainDocument
        // that JEditorPane's createDefaultDocument delegates back to via super.
        p.getDocument();
        dump("JEditorPane  L&F + getDocument round-trip", warnings);

        // -- HyperlinkListener: registration, fan-out and teardown are WARN-free --
        javax.swing.event.HyperlinkListener hl = e -> {};
        p.addHyperlinkListener(hl);
        p.getHyperlinkListeners();
        p.fireHyperlinkUpdate(new javax.swing.event.HyperlinkEvent(
                p, javax.swing.event.HyperlinkEvent.EventType.ACTIVATED, null, "#x"));
        p.removeHyperlinkListener(hl);
        dump("JEditorPane  HyperlinkListener add / fire / remove", warnings);

        // -- HTMLEditorKit + StyleSheet: both call orders are WARN-free --
        vaadinx.swing.text.html.HTMLEditorKit kit = new vaadinx.swing.text.html.HTMLEditorKit();
        kit.getStyleSheet().addRule("h2 { color: blue; }");   // before install
        p.setEditorKit(kit);
        kit.getStyleSheet().addRule("body { font-family: serif; }");   // after install
        p.getEditorKit();
        dump("JEditorPane  HTMLEditorKit + StyleSheet.addRule (both orders)", warnings);

        WarnDump.println();
        WarnDump.println("=== JEditorPane API-surface WARN total across buckets above ===");
    }

    @Test
    void inventory_jtextpane_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctor + model --
        vaadinx.swing.JTextPane pane = new vaadinx.swing.JTextPane();
        pane.getStyledDocument();
        pane.getUIClassID();
        dump("JTextPane  ctor + getStyledDocument", warnings);

        // -- Supported styled runs (all render via Delta, all WARN-free) --
        javax.swing.text.StyledDocument doc = pane.getStyledDocument();
        try {
            javax.swing.text.SimpleAttributeSet a = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setBold(a, true);
            javax.swing.text.StyleConstants.setForeground(a, java.awt.Color.RED);
            doc.insertString(0, "styled", a);
            javax.swing.text.SimpleAttributeSet c = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setAlignment(c, javax.swing.text.StyleConstants.ALIGN_CENTER);
            doc.setParagraphAttributes(0, 1, c, false);
        } catch (javax.swing.text.BadLocationException e) {
            throw new IllegalStateException(e);
        }
        pane.getText();
        dump("JTextPane  styled runs (bold/colour/alignment) + getText", warnings);

        // -- ImageIcon embed (supported) --
        pane.insertIcon(new vaadinx.swing.ImageIcon(
                new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB)));
        dump("JTextPane  insertIcon(ImageIcon)", warnings);

        // -- Named styles + input attributes --
        pane.addStyle("emph", null);
        pane.getStyle("emph");
        pane.getInputAttributes();
        dump("JTextPane  addStyle / getStyle / getInputAttributes", warnings);

        // The expected-WARN / expected-throw paths (insertComponent, non-ImageIcon
        // insertIcon, setEditorKit, setDocument(non-StyledDocument), unsupported
        // font attrs) are NOT exercised here — they're covered in JTextPaneTest.

        WarnDump.println();
        WarnDump.println("=== JTextPane API-surface WARN total across buckets above ===");
    }

    /** Dispatch the CustomEvent the peer's shadow-root click hook sends. */
    private static void fireHyperlinkDomEvent(com.vaadin.swingbridge.surrogates.SJEditorPane pane, String href) {
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
                    + " stub WARN(s) fired — regression in the EditorPanes exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
