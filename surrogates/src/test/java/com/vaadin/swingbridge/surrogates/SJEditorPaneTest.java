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

package com.vaadin.swingbridge.surrogates;

import com.sun.net.httpserver.HttpServer;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SJEditorPane (the RTE-backed peer, SD_sjeditorpane_rte). Covers:
 *
 * <ol>
 *  <li><b>HTML round-trip via RTE htmlValue</b> — {@code setText(html)} / {@code getText()}
 *      go through {@code setValue}/{@code getValue}; {@code setText(null)} clears to {@code ""}.
 *  <li><b>contentType filter</b> — {@code "text/html"} honored WARN-free; other types
 *      round-trip but WARN (RTE speaks HTML only).
 *  <li><b>Editable is default + toggles via setReadOnly</b> — {@code isEditable()} starts
 *      {@code true}, {@code setEditable(false)} flips {@code isReadOnly()}, PCE fires on change.
 *  <li><b>HyperlinkListener</b> — {@code ACTIVATED} fan-out driven by the client hook's
 *      {@code emul-hyperlink} DOM event, read-only panes only, with href resolution
 *      against {@code getPage()} and lazy hook installation.
 *  <li><b>setPage</b> — synchronous URL fetch → htmlValue + {@code "page"} PCE.
 *  <li><b>L&amp;F surface</b> — {@code getUIClassID == "EditorPaneUI"}.
 * </ol>
 */
class SJEditorPaneTest extends AbstractKaribuTest {

    private static final String FIXTURE = "/com/vaadin/swingbridge/surrogates/editor-pane-fixture.html";

    /**
     * A test body handed a live server URL. Distinct from {@link java.util.function.Consumer}
     * because every call site inside one calls {@code setPage}, which throws.
     */
    @FunctionalInterface
    private interface UrlBlock {
        void accept(URL url) throws IOException;
    }

    private URL fixtureUrl() {
        final URL url = getClass().getResource(FIXTURE);
        assertNotNull(url, "Test fixture missing on classpath: " + FIXTURE);
        return url;
    }

    /** Dispatch the DOM event the client hook sends, exactly as Flow would. */
    private static void dispatchHyperlinkDomEvent(SJEditorPane pane, String href) {
        final ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("event.detail.href", href);
        pane.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(pane.getElement(), "emul-hyperlink", data));
    }

    /** Collects the {@code newValue} of each PCE for one named property. */
    private static List<Object> recordNewValues(SJEditorPane pane, String property) {
        final List<Object> news = new ArrayList<>();
        pane.addPropertyChangeListener(property, e -> news.add(e.getNewValue()));
        return news;
    }

    /** Subscribes and returns the list the hyperlink events land in. */
    private static List<HyperlinkEvent> recordHyperlinks(SJEditorPane pane) {
        final List<HyperlinkEvent> events = new ArrayList<>();
        pane.addHyperlinkListener(events::add);
        return events;
    }

    /**
     * Serve one response with full control over headers, so charset and media-type
     * handling can be checked against a real {@code URLConnection} rather than a fixture.
     */
    private static void serving(String contentType, byte[] body, UrlBlock block) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", ex -> {
            if (contentType != null) {
                ex.getResponseHeaders().set("Content-Type", contentType);
            }
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            block.accept(URI.create("http://localhost:" + server.getAddress().getPort() + "/").toURL());
        } finally {
            server.stop(0);
        }
    }

    // --- Constructors -------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor seeds empty text, JDK-default contentType, editable")
    void noArgCtorDefaults() {
        final SJEditorPane pane = new SJEditorPane();
        assertEquals("", pane.getText());
        assertEquals("text/plain", pane.getContentType());
        assertTrue(pane.isEditable());   // JDK JEditorPane is editable by default
        assertFalse(pane.isReadOnly());
        assertNoWarns("no-arg ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("type-text ctor sets contentType and text in one call")
    void typeTextCtor() {
        capturedWarns.clear();
        final SJEditorPane pane = new SJEditorPane("text/html", "<p>hello</p>");
        assertEquals("text/html", pane.getContentType());
        assertEquals("<p>hello</p>", pane.getText());
        assertEquals("<p>hello</p>", pane.getValue());
        assertNoWarns("text/html ctor path should be WARN-free: " + capturedWarns);
    }

    // --- setText / getText (RTE htmlValue round-trip) -----------------------

    @Test
    @DisplayName("setText round-trips through htmlValue")
    void setTextRoundTrips() {
        final SJEditorPane pane = new SJEditorPane();
        pane.setText("<h1>Title</h1><p>Body</p>");
        assertEquals("<h1>Title</h1><p>Body</p>", pane.getText());
        assertEquals("<h1>Title</h1><p>Body</p>", pane.getValue());
    }

    @Test
    @DisplayName("setText(null) normalises to empty string")
    void setTextNullNormalises() {
        final SJEditorPane pane = new SJEditorPane();
        pane.setText("<p>foo</p>");
        pane.setText(null);
        assertEquals("", pane.getText());
    }

    // --- contentType (R_vaadin_first: only text/html honored) ---------------------------

    @Test
    @DisplayName("setContentType text-html is honored WARN-free")
    void contentTypeHtmlIsSilent() {
        final SJEditorPane pane = new SJEditorPane();
        capturedWarns.clear();
        pane.setContentType("text/html");
        assertEquals("text/html", pane.getContentType());
        assertNoWarns("text/html should not WARN: " + capturedWarns);
    }

    @Test
    @DisplayName("setContentType text-plain WARNs but round-trips")
    void contentTypePlainWarns() {
        final SJEditorPane pane = new SJEditorPane("text/html", "");  // start non-default
        capturedWarns.clear();
        pane.setContentType("text/plain");
        assertEquals("text/plain", pane.getContentType());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setContentType"));
    }

    @Test
    @DisplayName("setContentType resolves through the JDK's kit registry — unknown type reads back text/plain")
    void contentTypeResolvesThroughTheKitRegistry() {
        // javax.swing.JEditorPane keeps no contentType field: getContentType()
        // answers the installed kit's type, and a type with no registered kit
        // installs the plain kit. Measured on JDK 25 (D_return_value_audit).
        final SJEditorPane pane = new SJEditorPane("text/html", "");  // start non-default
        pane.setContentType("ADMIN");
        assertEquals("text/plain", pane.getContentType());
        pane.setContentType("text/html; charset=utf-8");
        assertEquals("text/html", pane.getContentType(), "the parameter list never survives");
        pane.setContentType("application/rtf");
        assertEquals("application/rtf", pane.getContentType(), "one of the four default-kit types");
        pane.setContentType("TEXT/HTML");
        assertEquals("text/plain", pane.getContentType(), "the JDK's kit lookup is exact-string");
    }

    @Test
    @DisplayName("setContentType null throws NPE per Swing contract")
    void contentTypeNullThrows() {
        final SJEditorPane pane = new SJEditorPane();
        assertThrows(NullPointerException.class, () -> pane.setContentType(null));
    }

    @Test
    @DisplayName("setContentType fires no PCE")
    void contentTypeFiresNoPce() {
        // The JDK's JEditorPane.setContentType fires nothing of its own; where
        // it swaps kits it reaches setEditorKit, which fires "editorKit" — a
        // different property, and one SB-Emulators has no editor-kit concept to carry
        // (SD_property_fanout_audit). This asserted "contentType" events instead.
        final SJEditorPane pane = new SJEditorPane();
        final List<Object> news = recordNewValues(pane, "contentType");
        pane.setContentType("text/html");
        pane.setContentType("text/plain");
        assertEquals("text/plain", pane.getContentType());
        assertEquals(List.of(), news);
    }

    // --- Editable (RTE readOnly-backed) -------------------------------------

    @Test
    @DisplayName("setEditable(false) makes the RTE read-only")
    void setEditableFalseMakesReadOnly() {
        final SJEditorPane pane = new SJEditorPane();
        capturedWarns.clear();
        pane.setEditable(false);
        assertFalse(pane.isEditable());
        assertTrue(pane.isReadOnly());
        assertNoWarns("editable toggle should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("editable toggles freely back and forth (view-edit toggle)")
    void editableTogglesBothWays() {
        final SJEditorPane pane = new SJEditorPane();
        pane.setEditable(false);
        assertTrue(pane.isReadOnly());
        pane.setEditable(true);
        assertFalse(pane.isReadOnly());
        assertTrue(pane.isEditable());
    }

    @Test
    @DisplayName("setEditable fires editable PCE on actual change")
    void setEditableFiresPceOnChange() {
        final SJEditorPane pane = new SJEditorPane();
        final List<Object> news = recordNewValues(pane, "editable");
        pane.setEditable(false);
        pane.setEditable(false);   // no PCE — same value
        pane.setEditable(true);
        assertEquals(List.of(false, true), news);
    }

    // --- HyperlinkListener (read-only anchor activation) --------------------
    //
    // The client half (the shadow-root delegated click) is invisible to Karibu:
    // no shadow DOM, no executeJs. These tests drive the seam the browser hook
    // reaches — the "emul-hyperlink" DOM event carrying the href — so everything
    // server-side of it is covered. The browser half is verified separately in a
    // real browser and recorded in SD_hyperlink_listener.

    @Test
    @DisplayName("only http, https and mailto hrefs survive the sanitizer")
    void sanitizerKeepsOnlyWebProtocols() {
        // Not our rule — Flow sanitizes the pushed value with a jsoup Safelist whose
        // protocol allowlist for a[href] is ftp/http/https/mailto. Everything else
        // loses the href attribute, and an hrefless <a> is no longer a link in the
        // editor, so it renders as plain text and can never be clicked. That caps
        // what HyperlinkListener can ever fire for: a classpath help set with
        // relative or file: links loses its navigation entirely. Pinned here because
        // it is invisible from the Swing side and would otherwise be rediscovered
        // the hard way (as it was, in the browser).
        final List<String> kept =
                List.of("https://example.com/a.html", "http://example.com/a.html", "mailto:a@b.c");
        final List<String> stripped = List.of("file:/tmp/help/a.html",
                "jar:file:/tmp/x.jar!/help/a.html", "topics.html", "/help/topics.html", "#frag");

        for (String href : kept) {
            final SJEditorPane pane = new SJEditorPane("text/html", "<p><a href=\"" + href + "\">x</a></p>");
            assertTrue(pane.getValue().contains("href=\"" + href + "\""),
                    href + " should have survived, value was " + pane.getValue());
        }
        for (String href : stripped) {
            final SJEditorPane pane = new SJEditorPane("text/html", "<p><a href=\"" + href + "\">x</a></p>");
            assertFalse(pane.getValue().contains("href="),
                    href + " was expected to lose its href, value was " + pane.getValue());
        }
    }

    @Test
    @DisplayName("addHyperlinkListener registers WARN-free and round-trips")
    void addHyperlinkListenerRoundTrips() {
        final SJEditorPane pane = new SJEditorPane();
        capturedWarns.clear();
        final HyperlinkListener l = e -> { };
        pane.addHyperlinkListener(l);
        assertEquals(1, pane.getHyperlinkListeners().length);
        assertNoWarns("addHyperlinkListener should no longer WARN: " + capturedWarns);

        pane.removeHyperlinkListener(l);
        assertEquals(0, pane.getHyperlinkListeners().length);
    }

    @Test
    @DisplayName("client hyperlink event fires ACTIVATED on a read-only pane")
    void clientEventFiresActivated() throws Exception {
        final SJEditorPane pane =
                new SJEditorPane("text/html", "<p><a href=\"https://example.com/help\">help</a></p>");
        pane.setEditable(false);
        final List<HyperlinkEvent> events = recordHyperlinks(pane);

        dispatchHyperlinkDomEvent(pane, "https://example.com/help");

        assertEquals(1, events.size());
        final HyperlinkEvent e = events.get(0);
        assertEquals(HyperlinkEvent.EventType.ACTIVATED, e.getEventType());
        // Swing's LinkController: description is the raw href, URL is it resolved.
        assertEquals("https://example.com/help", e.getDescription());
        assertEquals(URI.create("https://example.com/help").toURL(), e.getURL());
        assertEquals(pane, e.getSource());
        assertNull(e.getSourceElement());   // no HTMLDocument element model
    }

    @Test
    @DisplayName("an editable pane does not activate links")
    void editablePaneDoesNotActivate() {
        // Swing's HTMLEditorKit.LinkController activates a link only when the
        // pane is non-editable — in an editor a click positions the caret.
        final SJEditorPane pane =
                new SJEditorPane("text/html", "<p><a href=\"https://example.com/\">x</a></p>");
        assertTrue(pane.isEditable(), "precondition: panes are editable by default");
        final List<HyperlinkEvent> events = recordHyperlinks(pane);

        dispatchHyperlinkDomEvent(pane, "https://example.com/");

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("relative href resolves against the loaded page")
    void relativeHrefResolvesAgainstPage() throws Exception {
        final SJEditorPane pane = new SJEditorPane();
        final URL fixture = fixtureUrl();
        pane.setPage(fixture);
        pane.setEditable(false);
        final List<HyperlinkEvent> events = recordHyperlinks(pane);

        dispatchHyperlinkDomEvent(pane, "other.html");

        assertEquals(1, events.size());
        assertEquals("other.html", events.get(0).getDescription());
        assertEquals(fixture.toURI().resolve("other.html").toURL(), events.get(0).getURL(),
                "relative href should resolve against getPage() — our stand-in for HTMLDocument base");
    }

    @Test
    @DisplayName("an unresolvable href still fires with a null URL")
    void unresolvableHrefFiresWithNullUrl() {
        // Matches Swing: LinkController leaves getURL() null rather than dropping
        // the event, and the description carries the href regardless.
        final SJEditorPane pane = new SJEditorPane("text/html", "<p>x</p>");
        pane.setEditable(false);
        final List<HyperlinkEvent> events = recordHyperlinks(pane);

        dispatchHyperlinkDomEvent(pane, "#section-2");

        assertEquals(1, events.size());
        assertEquals("#section-2", events.get(0).getDescription());
        assertNull(events.get(0).getURL());
    }

    @Test
    @DisplayName("a removed listener stops receiving activations")
    void removedListenerStopsFiring() {
        final SJEditorPane pane = new SJEditorPane("text/html", "<p>x</p>");
        pane.setEditable(false);
        final List<HyperlinkEvent> events = new ArrayList<>();
        final HyperlinkListener l = events::add;
        pane.addHyperlinkListener(l);
        dispatchHyperlinkDomEvent(pane, "https://example.com/");
        assertEquals(1, events.size());

        pane.removeHyperlinkListener(l);
        dispatchHyperlinkDomEvent(pane, "https://example.com/");
        assertEquals(1, events.size(), "removed listener should not fire again");
    }

    @Test
    @DisplayName("no client hook is installed until a listener is registered")
    void hookIsInstalledLazily() {
        // Laziness matters: the hook preventDefaults link navigation, so a pane
        // nobody listens to must keep the browser's own behaviour.
        final SJEditorPane pane = new SJEditorPane();
        final ElementListenerMap listenerMap =
                pane.getElement().getNode().getFeature(ElementListenerMap.class);
        assertFalse(listenerMap.getExpressions("emul-hyperlink").contains("event.detail.href"),
                "no DOM subscription expected before the first addHyperlinkListener");

        pane.addHyperlinkListener(e -> { });

        assertTrue(listenerMap.getExpressions("emul-hyperlink").contains("event.detail.href"),
                "first addHyperlinkListener should wire the DOM subscription");
    }

    // --- setPage (synchronous URL fetch) ------------------------------------

    @Test
    @DisplayName("setPage URL loads bytes via URLConnection and writes htmlValue")
    void setPageLoadsBytes() throws IOException {
        final SJEditorPane pane = new SJEditorPane();
        final URL fixture = fixtureUrl();

        pane.setPage(fixture);

        assertEquals(fixture, pane.getPage());
        assertEquals("text/html", pane.getContentType());
        assertTrue(pane.getText().contains("<h1>SJEditorPane setPage fixture</h1>"),
                "Loaded HTML missing expected heading: '" + pane.getText() + "'");
        assertNoWarns("Happy-path setPage should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("setPage fires page PCE on actual change")
    void setPageFiresPce() throws IOException {
        final SJEditorPane pane = new SJEditorPane();
        final URL fixture = fixtureUrl();
        final List<Object> olds = new ArrayList<>();
        final List<Object> news = new ArrayList<>();
        pane.addPropertyChangeListener("page", e -> {
            olds.add(e.getOldValue());
            news.add(e.getNewValue());
        });

        pane.setPage(fixture);

        assertEquals(1, news.size());
        assertNull(olds.get(0));
        assertEquals(fixture, news.get(0));
    }

    @Test
    @DisplayName("setPage with unreachable URL propagates IOException")
    void setPageUnreachableThrows() throws Exception {
        final SJEditorPane pane = new SJEditorPane();
        final URL unreachable = URI.create("http://127.0.0.1:1/nope").toURL();
        assertThrows(IOException.class, () -> pane.setPage(unreachable));
    }

    @Test
    @DisplayName("setPage with a malformed URL string throws MalformedURLException, not IAE")
    void setPageMalformedThrowsIoException() {
        // R_match_swing_errors: migrated code catches IOException around setPage, as the JDK signature
        // invites. An IllegalArgumentException would sail past that handler.
        final SJEditorPane pane = new SJEditorPane();
        final MalformedURLException e = assertThrows(MalformedURLException.class,
                () -> pane.setPage("not a url"));
        assertInstanceOf(IOException.class, e);
    }

    @Test
    @DisplayName("setPage honours the charset declared in Content-Type")
    void setPageHonoursDeclaredCharset() throws IOException {
        // The regression guard: the charset used to be read from
        // getContentEncoding() (the compression header, null here), so this page
        // came back mangled.
        final byte[] body = "<h1>café naïve</h1>".getBytes(StandardCharsets.ISO_8859_1);
        serving("text/html; charset=ISO-8859-1", body, url -> {
            final SJEditorPane pane = new SJEditorPane();
            capturedWarns.clear();
            pane.setPage(url);
            assertTrue(pane.getText().contains("café naïve"),
                    "expected the declared charset to be honoured, got '" + pane.getText() + "'");
            assertNoWarns("an ordinary HTML page should load WARN-free: " + capturedWarns);
        });
    }

    @Test
    @DisplayName("setPage falls back to a meta charset when the header is silent")
    void setPageFallsBackToMetaCharset() throws IOException {
        final byte[] body = ("<html><head><meta charset=\"ISO-8859-1\"></head>"
                + "<body><p>naïve</p></body></html>").getBytes(StandardCharsets.ISO_8859_1);
        serving("text/html", body, url -> {
            final SJEditorPane pane = new SJEditorPane();
            pane.setPage(url);
            assertTrue(pane.getText().contains("naïve"), "got '" + pane.getText() + "'");
        });
    }

    @Test
    @DisplayName("setPage WARNs on a non-HTML media type but still loads it")
    void setPageNonHtmlWarns() throws IOException {
        final byte[] body = "plain words".getBytes(StandardCharsets.UTF_8);
        serving("text/plain", body, url -> {
            final SJEditorPane pane = new SJEditorPane();
            capturedWarns.clear();
            pane.setPage(url);
            assertTrue(pane.getText().contains("plain words"), "content should still load");
            assertEquals(1, capturedWarns.size());
            assertTrue(capturedWarns.get(0).contains("setPage"), capturedWarns.toString());
        });
    }

    @Test
    @DisplayName("setPage WARNs about a URL fragment it cannot scroll to")
    void setPageFragmentWarns() throws IOException {
        final URL withRef = URI.create(fixtureUrl().toExternalForm() + "#section-2").toURL();
        final SJEditorPane pane = new SJEditorPane();
        capturedWarns.clear();

        pane.setPage(withRef);

        assertTrue(pane.getText().contains("<h1>SJEditorPane setPage fixture</h1>"),
                "the page should still load, just not scrolled");
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("scrollToReference"), capturedWarns.toString());
    }

    @Test
    @DisplayName("setPage re-fetches the same URL rather than skipping the load")
    void setPageRefetchesSameUrl() throws IOException {
        // Deliberately unlike the JDK, which skips a same-URL load and offers a
        // document-model escape hatch we do not have. Re-fetching keeps a
        // reload-this-page action working.
        final Counter hits = new Counter();
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", ex -> {
            hits.inc();
            final byte[] body = ("<p>hit " + hits.get() + "</p>").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            final URL url = URI.create("http://localhost:" + server.getAddress().getPort() + "/").toURL();
            final SJEditorPane pane = new SJEditorPane();
            pane.setPage(url);
            pane.setPage(url);
            assertEquals(2, hits.get(), "the second setPage should have re-fetched");
            assertTrue(pane.getText().contains("hit 2"), "got '" + pane.getText() + "'");
        } finally {
            server.stop(0);
        }
    }

    // --- addCssRule (author CSS onto the rendered content) ------------------
    //
    // The injection itself is shadow-DOM + executeJs and so Karibu-invisible; what
    // is asserted here is the accumulation and translation feeding it. The rule
    // actually taking effect is browser-verified (recorded in SD_add_css_rule).

    @Test
    @DisplayName("addCssRule accumulates translated rules WARN-free")
    void addCssRuleAccumulates() {
        final SJEditorPane pane = new SJEditorPane("text/html", "<h2>x</h2>");
        capturedWarns.clear();
        pane.addCssRule("h2 { color: blue; }");
        pane.addCssRule("body { font-family: sans-serif; }");

        assertEquals(List.of(
                ".ql-editor h2 { color: blue; }",
                ".ql-editor { font-family: sans-serif; }"), pane.getCssRules());
        assertNoWarns("plain rules should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("an at-rule WARNs while the rest of the sheet still applies")
    void atRuleWarnsButRestApplies() {
        final SJEditorPane pane = new SJEditorPane();
        capturedWarns.clear();
        pane.addCssRule("@media print { p { color: red } } h2 { color: blue }");

        assertEquals(List.of(".ql-editor h2 { color: blue }"), pane.getCssRules());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("addCssRule"), capturedWarns.toString());
    }

    @Test
    @DisplayName("rules survive being added before attach")
    void rulesSurviveBeforeAttach() {
        // The pane is unattached here, which is the normal case: a migrator builds
        // the pane and styles it before adding it to a container.
        final SJEditorPane pane = new SJEditorPane();
        pane.addCssRule("h2 { color: blue }");
        assertEquals(1, pane.getCssRules().size(), "an unattached pane must still hold the rule");
    }

    @Test
    @DisplayName("getCssRules is a defensive copy")
    void getCssRulesIsDefensiveCopy() {
        final SJEditorPane pane = new SJEditorPane();
        pane.addCssRule("h2 { color: blue }");
        final List<String> snapshot = pane.getCssRules();
        pane.addCssRule("p { color: red }");
        assertEquals(1, snapshot.size(), "an earlier snapshot should not grow");
        assertEquals(2, pane.getCssRules().size());
    }

    // --- L&F surface --------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is EditorPaneUI")
    void uiClassIdIsEditorPaneUi() {
        assertEquals("EditorPaneUI", new SJEditorPane().getUIClassID());
    }
}
