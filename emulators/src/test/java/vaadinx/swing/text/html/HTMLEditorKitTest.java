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

package vaadinx.swing.text.html;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJEditorPane;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.swing.JEditorPane;

import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * {@code HTMLEditorKit} + {@code StyleSheet} (D_htmleditorkit) — the carrier for author CSS on an
 * HTML pane, which is the dominant render-only idiom in the field. Covers:
 *
 * <ol>
 *  <li><b>{@code addRule} reaches the peer</b>, in either order relative to
 *     {@code setEditorKit}.</li>
 *  <li><b>Per-kit stylesheet</b>, diverging from the JDK's shared static one.</li>
 *  <li><b>Type compatibility won by extending the JDK classes</b> — the kit is-a JDK
 *     {@code EditorKit}, its nested {@code ParserCallback} still works with the JDK's
 *     {@code ParserDelegator}, and the custom-{@code ViewFactory} idiom compiles.</li>
 *  <li><b>Expected WARNs</b> — a foreign stylesheet, {@code insertHTML}.</li>
 * </ol>
 */
class HTMLEditorKitTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        Consumer<String> sink = capturedWarns::add;
        EHelper.warnHook = sink;
        SHelper.warnHook = sink;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> { /* no-op default */ };
        SHelper.warnHook = msg -> { /* no-op default */ };
    }

    private static List<String> cssRulesOf(JEditorPane pane) {
        return ((SJEditorPane) pane.getPeer()).getCssRules();
    }

    // --- addRule reaches the peer, in both orders ---------------------------

    @Test
    @DisplayName("addRule after setEditorKit reaches the peer")
    void addRuleAfterInstallReachesThePeer() {
        JEditorPane pane = new JEditorPane("text/html", "<h2>Report</h2>");
        HTMLEditorKit kit = new HTMLEditorKit();
        pane.setEditorKit(kit);
        kit.getStyleSheet().addRule("body { font-family: sans-serif; }");

        assertEquals(List.of(".ql-editor { font-family: sans-serif; }"), cssRulesOf(pane));
        assertTrue(capturedWarns.isEmpty(),
                "the field's dominant idiom must be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("addRule before setEditorKit is flushed on install")
    void addRuleBeforeInstallIsFlushed() {
        // Order-independence is required, not a nicety: a kit is often configured
        // fully and only then handed to the pane.
        HTMLEditorKit kit = new HTMLEditorKit();
        kit.getStyleSheet().addRule("h2 { color: blue; }");
        kit.getStyleSheet().addRule("p { margin: 0; }");

        JEditorPane pane = new JEditorPane("text/html", "<h2>x</h2>");
        pane.setEditorKit(kit);

        assertEquals(List.of(".ql-editor h2 { color: blue; }", ".ql-editor p { margin: 0; }"),
                cssRulesOf(pane));
    }

    @Test
    @DisplayName("installing the same kit twice does not double-apply")
    void installingTheSameKitTwiceIsIdempotent() {
        JEditorPane pane = new JEditorPane("text/html", "<h2>x</h2>");
        HTMLEditorKit kit = new HTMLEditorKit();
        kit.getStyleSheet().addRule("h2 { color: blue; }");
        pane.setEditorKit(kit);
        pane.setEditorKit(kit);

        assertEquals(1, cssRulesOf(pane).size());
    }

    @Test
    @DisplayName("one kit shared across two panes styles both")
    void oneKitStylesTwoPanes() {
        HTMLEditorKit kit = new HTMLEditorKit();
        JEditorPane a = new JEditorPane("text/html", "<h2>a</h2>");
        JEditorPane b = new JEditorPane("text/html", "<h2>b</h2>");
        a.setEditorKit(kit);
        b.setEditorKit(kit);
        kit.getStyleSheet().addRule("h2 { color: blue; }");

        assertEquals(1, cssRulesOf(a).size());
        assertEquals(1, cssRulesOf(b).size());
    }

    @Test
    @DisplayName("setEditorKit fires the editorKit PCE and getEditorKit round-trips")
    void setEditorKitFiresPce() {
        JEditorPane pane = new JEditorPane();
        List<Object> news = new ArrayList<>();
        pane.addPropertyChangeListener("editorKit", e -> news.add(e.getNewValue()));
        HTMLEditorKit kit = new HTMLEditorKit();
        pane.setEditorKit(kit);

        assertSame(kit, assertSingle(news));
        assertSame(kit, pane.getEditorKit());
    }

    // --- per-kit stylesheet (deliberate JDK divergence) --------------------

    @Test
    @DisplayName("each kit gets its own stylesheet, unlike the JDK")
    void eachKitGetsItsOwnStylesheet() {
        assertNotSame(new HTMLEditorKit().getStyleSheet(), new HTMLEditorKit().getStyleSheet(),
                "per-kit sheets are the point — the JDK shares one static sheet JVM-wide");
        // ...and the JDK really does share, which is what we are diverging from.
        assertSame(new javax.swing.text.html.HTMLEditorKit().getStyleSheet(),
                new javax.swing.text.html.HTMLEditorKit().getStyleSheet());
    }

    @Test
    @DisplayName("setStyleSheet installs a vaadinx sheet and binds it to the peer")
    void setStyleSheetInstallsAndBinds() {
        JEditorPane pane = new JEditorPane("text/html", "<h2>x</h2>");
        HTMLEditorKit kit = new HTMLEditorKit();
        pane.setEditorKit(kit);

        StyleSheet fresh = new StyleSheet();
        fresh.addRule("h2 { color: green; }");
        kit.setStyleSheet(fresh);

        assertSame(fresh, kit.getStyleSheet());
        assertEquals(List.of(".ql-editor h2 { color: green; }"), cssRulesOf(pane));
    }

    @Test
    @DisplayName("a JDK stylesheet WARNs and leaves the current one in place")
    void aJdkStylesheetIsRefused() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet ours = kit.getStyleSheet();
        // Called through the JDK-typed reference, because getStyleSheet narrows its
        // return type to our sheet — so migrator code needs no cast after the
        // import swap, and reaching the JDK-typed setter takes an explicit upcast.
        ((javax.swing.text.html.HTMLEditorKit) kit)
                .setStyleSheet(new javax.swing.text.html.StyleSheet());

        assertSame(ours, kit.getStyleSheet(),
                "a sheet that cannot reach the peer must not be installed");
        assertTrue(assertSingle(capturedWarns).contains("setStyleSheet"));
    }

    @Test
    @DisplayName("getRules exposes what was added, while the JDK rule model stays empty")
    void getRulesExposesWhatWasAdded() {
        StyleSheet sheet = new StyleSheet();
        sheet.addRule("h2 { color: blue; }");
        assertEquals(List.of("h2 { color: blue; }"), sheet.getRules());
        // R_vaadin_first: the rule text goes to the peer rather than into a parsed shadow model,
        // so the inherited JDK accessors are not populated.
        assertEquals(0, sheet.getRule("h2").getAttributeCount());
    }

    // --- type compatibility won by extending the JDK classes (D_htmleditorkit) ---------

    @Test
    @DisplayName("the kit is-a JDK EditorKit so setEditorKit keeps the JDK signature")
    void theKitIsAJdkEditorKit() {
        javax.swing.text.EditorKit kit = new HTMLEditorKit();
        assertEquals("text/html", kit.getContentType());
    }

    // The nested-ParserCallback claim — headless HTML scraping surviving the import
    // swap — stays in HTMLEditorKitJavaCompatTest, which exists for exactly that
    // claim and states it against the migrated-source shape (R_java_karibu_tests).
    // It was inexpressible while this file was Kotlin; it is not duplicated here now
    // that it isn't, because the dedicated file is where a reader looks for it.

    @Test
    @DisplayName("the custom-ViewFactory idiom compiles and is inert")
    void customViewFactoryIdiomCompiles() {
        // Five apps in the surveyed corpus ship a near-identical image-suppressing
        // kit. It must compile; it need not do anything, since dropping images is
        // already our behaviour.
        HTMLEditorKit kit = new HTMLEditorKit() {
            @Override
            public ViewFactory getViewFactory() {
                return new HTMLFactory() {
                    @Override
                    public View create(Element elem) {
                        return super.create(elem);
                    }
                };
            }
        };
        assertNotNull(kit.getViewFactory());
    }

    // --- expected WARNs ----------------------------------------------------

    @Test
    @DisplayName("insertHTML WARNs — element-model splicing is not built")
    void insertHtmlWarns() {
        new HTMLEditorKit().insertHTML(
                new javax.swing.text.html.HTMLDocument(), 0, "<p>x</p>", 0, 0, null);
        assertTrue(assertSingle(capturedWarns).contains("insertHTML"));
    }

    @Test
    @DisplayName("a non-HTML kit WARNs and is not installed")
    void aNonHtmlKitIsRefused() {
        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(new javax.swing.text.DefaultEditorKit());
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("setEditorKit")));
    }
}
