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

package vaadinx.swing;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.richtexteditor.RichTextEditor;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJEditorPane;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.swing.text.html.HTMLDocument;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.PlainDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for JEditorPane. Pairs with SJEditorPaneTest — this layer
 * covers the thin shell's forwarding + the two-layer Document/RTE sync:
 *
 * <ol>
 *  <li><b>Ctor wiring</b> — peer is always SJEditorPane; all four ctors
 *     funnel through the same protected (Component peer) seam.
 *  <li><b>setText two-layer push</b> — emulator Document AND peer htmlValue both
 *     reflect the markup; getText reads from the inherited Document.
 *  <li><b>peer → Document sync</b> — a browser value-change on the RTE peer mirrors
 *     into the Document so getText() reflects user edits and DocumentListeners
 *     fire (R_swing_is_truth).
 *  <li><b>editable</b> — setEditable propagates to the RTE peer's readOnly and
 *     fires the "editable" PCE; editable is the JDK default.
 *  <li><b>contentType forwarding</b> — WARN-bound types still fire WARN surrogate-side.
 *  <li><b>HyperlinkListener</b> — peer activation bridges into the emulator's own
 *     listener list with {@code source} rebound to the emulator (R_swing_is_truth).
 *  <li><b>URL ctors / setPage</b> synchronously fetch via the surrogate.
 *  <li><b>EditorKit surface WARNs.</b>
 * </ol>
 */
class JEditorPaneTest extends AbstractKaribuTest {

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

    /** The RTE peer, as the surrogate whose value every two-layer assertion below reads. */
    private static SJEditorPane peerOf(JEditorPane pane) {
        return (SJEditorPane) pane.getPeer();
    }

    /** The setPage / URL-ctor fixture, which must exist on the test classpath. */
    private URL fixture() {
        URL url = getClass().getResource("/vaadinx/swing/editor-pane-fixture.html");
        assertNotNull(url, "editor-pane-fixture.html missing from the test classpath");
        return url;
    }

    // --- Constructors -------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor wires SJEditorPane peer with no WARN")
    void noArgCtorWiresPeerWarnFree() {
        JEditorPane pane = new JEditorPane();
        assertInstanceOf(SJEditorPane.class, pane.getPeer());
        assertEquals("", pane.getText());
        assertEquals("text/plain", pane.getContentType());
        assertTrue(pane.isEditable());   // JDK JEditorPane is editable by default
        assertNoWarns(capturedWarns, "default ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("type-text ctor sets contentType and text in one call")
    void typeTextCtorSetsBoth() {
        capturedWarns.clear();
        JEditorPane pane = new JEditorPane("text/html", "<p>hi</p>");
        assertEquals("text/html", pane.getContentType());
        assertEquals("<p>hi</p>", pane.getText());
        assertEquals("<p>hi</p>", peerOf(pane).getValue());
        assertNoWarns(capturedWarns, "type-text ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("URL ctor synchronously fetches via setPage")
    void urlCtorFetchesSynchronously() throws IOException {
        URL fixture = fixture();
        JEditorPane pane = new JEditorPane(fixture);
        assertInstanceOf(SJEditorPane.class, pane.getPeer());
        assertEquals(fixture, pane.getPage());
        assertEquals("text/html", pane.getContentType());
        assertTrue(pane.getText().contains("<h1>JEditorPane setPage fixture</h1>"),
                "Expected loaded HTML, got: '" + pane.getText() + "'");
        assertNoWarns(capturedWarns, "URL ctor happy path should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("URL-string ctor parses string and dispatches to setPage")
    void urlStringCtorParsesAndDispatches() throws IOException {
        URL fixture = fixture();
        JEditorPane pane = new JEditorPane(fixture.toString());
        assertInstanceOf(SJEditorPane.class, pane.getPeer());
        assertEquals(fixture.toString(), pane.getPage().toString());
        assertTrue(pane.getText().contains("<h1>"));
        assertNoWarns(capturedWarns);
    }

    @Test
    @DisplayName("URL ctor with unreachable URL propagates IOException")
    void urlCtorWithUnreachableUrlPropagatesIoException() {
        assertThrows(IOException.class,
                () -> new JEditorPane(URI.create("http://127.0.0.1:1/nope").toURL()));
    }

    // --- setText / getText (two-layer push) ---------------------------------

    @Test
    @DisplayName("setText writes Document AND peer htmlValue")
    void setTextWritesBothLayers() {
        JEditorPane pane = new JEditorPane();
        pane.setText("<h1>Heading</h1>");
        // Emulator side: Document reads back the same.
        assertEquals("<h1>Heading</h1>", pane.getText());
        // Peer side: RTE htmlValue carries the markup.
        assertEquals("<h1>Heading</h1>", peerOf(pane).getValue());
    }

    @Test
    @DisplayName("setText null normalises to empty on both layers")
    void setTextNullNormalisesOnBothLayers() {
        JEditorPane pane = new JEditorPane("text/html", "<p>foo</p>");
        pane.setText(null);
        assertEquals("", pane.getText());
        assertEquals("", peerOf(pane).getValue());
    }

    // --- peer → Document sync (editable typing) -----------------------------

    @Test
    @DisplayName("browser value-change on the RTE peer mirrors into the Document")
    void browserValueChangeMirrorsIntoTheDocument() {
        JEditorPane pane = new JEditorPane("text/html", "<p>start</p>");
        List<String> docEdits = new ArrayList<>();
        pane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                docEdits.add("insert");
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                docEdits.add("remove");
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        // Simulate a client edit: RTE fires a value-change, the emulator mirrors
        // the new HTML into the Document under preventPeerEvents.
        peerOf(pane).setValue("<p>edited</p>");

        assertEquals("<p>edited</p>", pane.getText());   // getText() reflects the edit
        assertFalse(docEdits.isEmpty(), "DocumentListener should have fired");
    }

    // --- editable -----------------------------------------------------------

    @Test
    @DisplayName("setEditable propagates to peer readOnly and fires PCE")
    void setEditablePropagatesAndFiresPce() {
        JEditorPane pane = new JEditorPane();
        List<Object> news = new ArrayList<>();
        pane.addPropertyChangeListener("editable", e -> news.add(e.getNewValue()));
        pane.setEditable(false);
        assertFalse(pane.isEditable());
        assertTrue(((RichTextEditor) pane.getPeer()).isReadOnly());
        pane.setEditable(true);
        assertFalse(((RichTextEditor) pane.getPeer()).isReadOnly());
        assertEquals(List.of(false, true), news);
    }

    // --- contentType forwarding ---------------------------------------------

    @Test
    @DisplayName("setContentType html is WARN-free")
    void setContentTypeHtmlIsWarnFree() {
        JEditorPane pane = new JEditorPane();
        capturedWarns.clear();
        pane.setContentType("text/html");
        assertEquals("text/html", pane.getContentType());
        assertNoWarns(capturedWarns);
    }

    @Test
    @DisplayName("setContentType text-plain WARNs (surrogate-side)")
    void setContentTypePlainWarns() {
        JEditorPane pane = new JEditorPane("text/html", "");
        capturedWarns.clear();
        pane.setContentType("text/plain");
        assertEquals("text/plain", pane.getContentType());
        assertEquals(1, capturedWarns.size());
    }

    // --- HyperlinkListener --------------------------------------------------

    /** Dispatches the DOM event the browser-side hook sends to the peer. */
    private static void dispatchHyperlinkDomEvent(JEditorPane pane, String href) {
        Element el = peerOf(pane).getElement();
        var data = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        data.put("event.detail.href", href);
        el.getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(el, "emul-hyperlink", data));
    }

    @Test
    @DisplayName("addHyperlinkListener registers WARN-free")
    void addHyperlinkListenerRegistersWarnFree() {
        JEditorPane pane = new JEditorPane();
        capturedWarns.clear();
        HyperlinkListener l = e -> { };
        pane.addHyperlinkListener(l);
        assertEquals(1, pane.getHyperlinkListeners().length);
        assertEquals(0, capturedWarns.size(), "should no longer WARN: " + capturedWarns);

        pane.removeHyperlinkListener(l);
        assertEquals(0, pane.getHyperlinkListeners().length);
    }

    @Test
    @DisplayName("peer activation reaches the emulator with source rebound to the emulator")
    void peerActivationReachesTheEmulator() throws Exception {
        JEditorPane pane = new JEditorPane("text/html", "<p><a href=\"https://example.com/a\">a</a></p>");
        pane.setEditable(false);
        List<HyperlinkEvent> events = new ArrayList<>();
        pane.addHyperlinkListener(events::add);

        dispatchHyperlinkDomEvent(pane, "https://example.com/a");

        HyperlinkEvent e = assertSingle(events);
        // R_swing_is_truth: JDK-shaped user code casts getSource() to JEditorPane — it must be
        // the emulator, not the surrogate peer.
        assertSame(pane, e.getSource());
        assertEquals(HyperlinkEvent.EventType.ACTIVATED, e.getEventType());
        assertEquals("https://example.com/a", e.getDescription());
        assertEquals(URI.create("https://example.com/a").toURL(), e.getURL());
    }

    @Test
    @DisplayName("fireHyperlinkUpdate is a public synthesizer as in the JDK")
    void fireHyperlinkUpdateIsAPublicSynthesizer() {
        JEditorPane pane = new JEditorPane();
        List<HyperlinkEvent> events = new ArrayList<>();
        pane.addHyperlinkListener(events::add);

        HyperlinkEvent synthetic =
                new HyperlinkEvent(pane, HyperlinkEvent.EventType.ACTIVATED, null, "manual");
        pane.fireHyperlinkUpdate(synthetic);

        assertEquals(List.of(synthetic), events);
    }

    @Test
    @DisplayName("an editable pane does not deliver activations")
    void anEditablePaneDoesNotDeliverActivations() {
        JEditorPane pane = new JEditorPane("text/html", "<p><a href=\"https://example.com/\">x</a></p>");
        List<HyperlinkEvent> events = new ArrayList<>();
        pane.addHyperlinkListener(events::add);

        dispatchHyperlinkDomEvent(pane, "https://example.com/");

        assertEquals(0, events.size(), "editable panes position the caret, matching Swing");
    }

    @Test
    @DisplayName("a subclass peering over a non-SJEditorPane WARNs on addHyperlinkListener")
    void aForeignPeerWarnsOnAddHyperlinkListener() {
        // The R_leaf_peer_lockdown protected-ctor seam: a foreign peer has no hyperlink plumbing.
        JEditorPane pane = new JEditorPane(new Div()) {
        };
        capturedWarns.clear();
        pane.addHyperlinkListener(e -> { });
        assertTrue(assertSingle(capturedWarns).contains("addHyperlinkListener"));
    }

    // --- EditorKit / setPage / getPage surface ------------------------------

    @Test
    @DisplayName("setEditorKit WARNs on a non-null kit, stays quiet on null")
    void setEditorKitWarnsOnNonNull() {
        JEditorPane pane = new JEditorPane();
        capturedWarns.clear();
        pane.setEditorKit(null);
        assertEquals(0, capturedWarns.size());
        pane.setEditorKit(new DefaultEditorKit());
        assertTrue(assertSingle(capturedWarns).contains("setEditorKit"));
    }

    @Test
    @DisplayName("setPage URL forwards to peer and loads synchronously")
    void setPageForwardsAndLoadsSynchronously() throws IOException {
        JEditorPane pane = new JEditorPane();
        URL fixture = fixture();
        capturedWarns.clear();
        pane.setPage(fixture);
        assertEquals(fixture, pane.getPage());
        assertTrue(pane.getText().contains("<h1>"));
        assertNoWarns(capturedWarns, "setPage happy path should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("getPage returns null until setPage is called")
    void getPageIsNullUntilSetPage() {
        JEditorPane pane = new JEditorPane();
        capturedWarns.clear();
        assertNull(pane.getPage());
        assertNoWarns(capturedWarns, "getPage on a fresh pane should NOT WARN: " + capturedWarns);
    }

    @Test
    @DisplayName("createEditorKitForContentType WARNs")
    void createEditorKitForContentTypeWarns() {
        capturedWarns.clear();
        JEditorPane.createEditorKitForContentType("text/html");
        assertEquals(1, capturedWarns.size());
    }

    // --- L&F surface --------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is EditorPaneUI")
    void getUiClassIdIsEditorPaneUi() {
        assertEquals("EditorPaneUI", new JEditorPane().getUIClassID());
    }

    // --- attach-handshake guard (D_jtextpane) --------------------------------------

    @Test
    @DisplayName("handshake skips both the spurious empty and the content echo when we pushed content")
    void handshakeSkipsSpuriousEmptyAndEcho() {
        JEditorPane pane = new JEditorPane("text/html", "<p>hi</p>");   // pushes content → expects an echo
        // The browser reports an empty init-state, then echoes our pushed content —
        // both before any user edit; skip both so the Document (already correct from
        // construction) neither clears nor rebuilds through partial states.
        assertTrue(pane.ignoreInitHandshake(true, true));   // spurious pre-render empty
        assertTrue(pane.ignoreInitHandshake(true, false));  // echo of our own push
        // Handshake done: a later client empty IS a genuine clear-all → processed.
        assertFalse(pane.ignoreInitHandshake(true, true));
    }

    @Test
    @DisplayName("handshake applies the client's first content when we pushed nothing")
    void handshakeAppliesFirstContentWhenNothingPushed() {
        JEditorPane pane = new JEditorPane();   // no content pushed → no echo expected
        assertTrue(pane.ignoreInitHandshake(true, true));    // spurious empty still skipped
        assertFalse(pane.ignoreInitHandshake(true, false));  // genuine first edit → processed
    }

    @Test
    @DisplayName("init-handshake guard never gates server-side (non-client) changes")
    void initHandshakeNeverGatesServerSideChanges() {
        JEditorPane pane = new JEditorPane("text/html", "<p>hi</p>");
        assertFalse(pane.ignoreInitHandshake(false, true));
        assertFalse(pane.ignoreInitHandshake(false, false));
    }

    // --- HTML element model (D_htmldocument) ------------------------------------------

    @Test
    @DisplayName("setContentType text-html installs an empty element model, as the JDK's kit swap does")
    void setContentTypeHtmlInstallsTheElementModel() {
        JEditorPane pane = new JEditorPane();
        pane.setText("<p id='lost'>hi</p>");
        assertInstanceOf(PlainDocument.class, pane.getDocument());   // text/plain default

        pane.setContentType("text/html");

        HTMLDocument doc = assertInstanceOf(HTMLDocument.class, pane.getDocument());
        assertNull(doc.getElement("lost"), "a new kit brings a new, empty document");
        assertEquals("", pane.getText());
        assertEquals("", peerOf(pane).getValue(), "the peer shows the empty document");
    }

    @Test
    @DisplayName("switching back to text-plain installs an empty PlainDocument")
    void switchingBackToPlainRetiresTheElementModel() {
        JEditorPane pane = new JEditorPane("text/html", "<p>hi</p>");

        pane.setContentType("text/plain");

        assertInstanceOf(PlainDocument.class, pane.getDocument());
        assertEquals("", pane.getText());
    }

    /**
     * A script measured on JDK 25: which document each content type brings, when it is
     * replaced, the charset client property, and the resolved type.
     */
    @Test
    @DisplayName("setContentType replays the JDK's kit and document script")
    void contentTypeMatchesTheJdk() {
        JEditorPane p = new JEditorPane();
        assertEquals("text/plain", p.getContentType());
        assertNull(p.getPage());
        List<String> log = new ArrayList<>();
        p.addPropertyChangeListener(e -> log.add(e.getPropertyName()));

        javax.swing.text.Document d0 = p.getDocument();
        p.setContentType("text/html");
        javax.swing.text.Document d1 = p.getDocument();
        assertNotSame(d0, d1);
        p.setContentType("text/html");
        assertSame(d1, p.getDocument(), "the installed registered type again changes nothing");
        assertEquals(List.of("document"), log);
        log.clear();

        p.setContentType("text/html; charset=ISO-8859-2");
        assertEquals("text/html", p.getContentType());
        assertEquals("ISO-8859-2", p.getClientProperty("charset"));
        assertSame(d1, p.getDocument());
        assertEquals(List.of("charset"), log);
        p.setContentType("text/plain; charset=\"UTF-16\"");
        assertEquals("UTF-16", p.getClientProperty("charset"), "quotes are stripped");
        p.setContentType("application/x-foo; charset=koi8-r");
        assertEquals("UTF-16", p.getClientProperty("charset"), "only a text/ type sets it");
        assertEquals("text/plain", p.getContentType());

        javax.swing.text.Document d2 = p.getDocument();
        p.setContentType("ADMIN");
        javax.swing.text.Document d3 = p.getDocument();
        p.setContentType("ADMIN");
        assertEquals("text/plain", p.getContentType());
        assertNotSame(d2, d3);
        assertNotSame(d3, p.getDocument(), "an unregistered type gets a fresh kit every time");
        p.setContentType("TEXT/HTML");
        assertEquals("text/plain", p.getContentType(), "the lookup is exact-string");
        p.setContentType("text/rtf");
        assertEquals("text/rtf", p.getContentType());
        assertInstanceOf(DefaultStyledDocument.class, p.getDocument());
        assertThrows(NullPointerException.class, () -> p.setContentType(null));
        assertEquals("text/plain", new JTextPane().getContentType());
    }

    @Test
    @DisplayName("a browser edit refreshes the element model")
    void aBrowserEditRefreshesTheElementModel() throws BadLocationException {
        JEditorPane pane = new JEditorPane("text/html", "<p id='before'>start</p>");
        HTMLDocument doc = assertInstanceOf(HTMLDocument.class, pane.getDocument());

        peerOf(pane).setValue("<p>edited</p>");

        assertEquals("<p>edited</p>", pane.getText());
        assertTrue(doc.getText(0, doc.getLength()).contains("edited"),
                "a browser edit must reach the element model");
        assertNull(doc.getElement("before"), "the pre-edit tree must be gone");
    }

    @Test
    @DisplayName("ids do not survive a round trip through the peer")
    void idsDoNotSurviveARoundTripThroughThePeer() {
        // Documented limitation (D_htmldocument): the editor's model does not carry `id`
        // through, so getElement(id) serves server-authored markup only. Pinned so
        // an upstream change to what RTE preserves surfaces as a test failure
        // rather than as a migrator's silently-null lookup.
        JEditorPane pane = new JEditorPane("text/html", "<p id='keep'>hi</p>");
        HTMLDocument doc = assertInstanceOf(HTMLDocument.class, pane.getDocument());
        assertNotNull(doc.getElement("keep"), "server-authored ids resolve");

        peerOf(pane).setValue("<p id='keep'>edited in the browser</p>");

        assertEquals("<p>edited in the browser</p>", pane.getText(), "the peer drops the id");
        assertNull(doc.getElement("keep"));
    }

    @Test
    @DisplayName("setPage installs the element model for the loaded page")
    void setPageInstallsTheElementModel() throws IOException {
        JEditorPane pane = new JEditorPane();
        URL fixture = fixture();
        capturedWarns.clear();

        pane.setPage(fixture);

        HTMLDocument doc = assertInstanceOf(HTMLDocument.class, pane.getDocument());
        assertEquals("html", doc.getDefaultRootElement().getName());
        assertTrue(doc.getLength() > 0, "the loaded page must populate the model");
        assertNoWarns(capturedWarns, "setPage happy path should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("a user-installed document is replaced by a content-type change, as in the JDK")
    void aUserInstalledDocumentIsReplaced() {
        JEditorPane pane = new JEditorPane();
        DefaultStyledDocument own = new DefaultStyledDocument();
        pane.setDocument(own);

        pane.setContentType("text/html");

        assertInstanceOf(HTMLDocument.class, pane.getDocument());
    }

    // --- setPage: the JDK's body over getStream ---------------------------------------

    /** A pane logging what setPage reaches: getStream, scrollToReference and the events. */
    private static final class LoggingPane extends JEditorPane {
        final List<String> log = new ArrayList<>();

        LoggingPane() {
            addPropertyChangeListener(e -> log.add("PCE " + e.getPropertyName()
                    + ("page".equals(e.getPropertyName()) ? " " + name(e.getOldValue()) + "->" + name(e.getNewValue()) : "")));
        }

        @Override
        protected java.io.InputStream getStream(URL page) throws IOException {
            log.add("getStream(" + name(page) + ")");
            return super.getStream(page);
        }

        @Override
        public void scrollToReference(String reference) {
            log.add("scrollToReference(" + reference + ")");
        }

        List<String> drain() {
            List<String> out = new ArrayList<>(log);
            log.clear();
            return out;
        }
    }

    private static String name(Object url) {
        if (url == null) return "null";
        URL u = (URL) url;
        String file = u.getPath().substring(u.getPath().lastIndexOf('/') + 1);
        return u.getRef() != null ? file + "#" + u.getRef() : file;
    }

    /**
     * The JDK 25 script, with the HTML load synchronous here where the JDK's runs on a worker:
     * the fetch, the same-file skip and the reload idiom, a reference, a plain-text page, the
     * argument errors, and a missing file switching the content type before it throws. The JDK
     * also fires {@code "editorKit"} at each kit change; this pane has no kit objects to name.
     */
    @Test
    @DisplayName("setPage replays the JDK's script")
    void setPageMatchesTheJdk(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path aHtml = dir.resolve("a.html");
        java.nio.file.Files.writeString(aHtml, "<html><body><p>one</p><a href=\"rel.html\">r</a></body></html>");
        java.nio.file.Files.writeString(dir.resolve("b.txt"), "plain text");
        URL html = aHtml.toUri().toURL();
        URL txt = dir.resolve("b.txt").toUri().toURL();
        LoggingPane q = new LoggingPane();

        q.setPage(html);
        assertEquals(List.of("getStream(a.html)", "PCE document", "PCE document", "PCE page null->a.html"), q.drain());
        assertEquals(html, q.getPage());
        assertEquals("text/html", q.getContentType());
        assertInstanceOf(HTMLDocument.class, q.getDocument());
        assertTrue(q.getText().contains("<p>one</p>"), q.getText());

        java.nio.file.Files.writeString(aHtml, "<html><body><p>two</p></body></html>");
        q.setPage(html);
        assertEquals(List.of(), q.drain(), "the same file is not fetched again");
        assertTrue(q.getText().contains("one"));
        q.getDocument().putProperty(javax.swing.text.Document.StreamDescriptionProperty, null);
        q.setPage(html);
        assertEquals(List.of("getStream(a.html)", "PCE document", "PCE page null->a.html"), q.drain());
        assertTrue(q.getText().contains("two"), "clearing the page is the JDK's reload");

        q.setPage(new URI(html + "#sec").toURL());
        assertEquals(List.of("scrollToReference(sec)", "PCE page a.html->a.html#sec"), q.drain());
        assertEquals("sec", q.getPage().getRef());

        q.setPage(txt);
        assertEquals(List.of("getStream(b.txt)", "PCE document", "PCE document", "PCE page a.html#sec->b.txt"), q.drain());
        assertEquals("text/plain", q.getContentType());
        assertInstanceOf(PlainDocument.class, q.getDocument());
        assertEquals("plain text", q.getText());

        assertEquals("invalid url", assertThrows(IOException.class, () -> q.setPage((URL) null)).getMessage());
        assertEquals("invalid url", assertThrows(IOException.class, () -> q.setPage((String) null)).getMessage());
        assertThrows(java.net.MalformedURLException.class, () -> q.setPage("not a url"));
        assertEquals(List.of(), q.drain());

        assertThrows(java.io.FileNotFoundException.class, () -> q.setPage(dir.resolve("missing.html").toUri().toURL()));
        assertEquals(List.of("getStream(missing.html)", "PCE document"), q.drain());
        assertEquals("text/html", q.getContentType(), "switched before the stream opened");
        assertNull(q.getPage());

        q.setPage(html);
        q.setText("x");
        assertEquals(html, q.getPage(), "setText keeps the page");

        JEditorPane bogus = new JEditorPane();
        bogus.setContentType("text/plain; charset=bogus");
        assertEquals("bogus", assertThrows(java.io.UnsupportedEncodingException.class,
                () -> bogus.setPage(txt)).getMessage());
        assertNull(bogus.getPage());
    }

    @Test
    @DisplayName("a page declaring its charset in a meta tag decodes by it, WARN-free")
    void aMetaCharsetPageDecodesWarnFree(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path page = dir.resolve("latin.html");
        java.nio.file.Files.write(page, ("<html><head><meta http-equiv=\"Content-Type\" "
                + "content=\"text/html; charset=ISO-8859-1\"></head><body><p>café</p></body></html>")
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        JEditorPane pane = new JEditorPane();
        capturedWarns.clear();

        pane.setPage(page.toUri().toURL());

        assertTrue(pane.getText().contains("café"), pane.getText());
        HTMLDocument doc = assertInstanceOf(HTMLDocument.class, pane.getDocument());
        assertTrue(doc.getText(0, doc.getLength()).contains("café"), "the element model parsed it");
        assertNoWarns(capturedWarns, "the <meta> directive must not restart the parse: " + capturedWarns);
    }

    @Test
    @DisplayName("a link resolves against the pane's own page")
    void aLinkResolvesAgainstThePage(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path page = dir.resolve("index.html");
        java.nio.file.Files.writeString(page, "<p><a href=\"next.html\">next</a></p>");
        JEditorPane pane = new JEditorPane(page.toUri().toURL());
        pane.setEditable(false);
        List<URL> urls = new ArrayList<>();
        pane.addHyperlinkListener(e -> urls.add(e.getURL()));

        dispatchHyperlinkDomEvent(pane, "next.html");

        assertEquals(List.of(dir.resolve("next.html").toUri().toURL()), urls);
    }

    // --- R_leaf_peer_lockdown lock-down: protected peer ctor stays open ---------------------

    @Test
    @DisplayName("protected peer ctor supports subclass with custom peer")
    void protectedPeerCtorSupportsACustomPeer() {
        // Verifies the D_peer_ctor_injection seam works — a subclass passing a non-SJEditorPane
        // peer constructs (forwarding logic just doesn't fire). Mirrors the
        // rationale for keeping the protected ctor open per R_leaf_peer_lockdown (JTextPane is a
        // JDK subclass of JEditorPane).
        JEditorPane custom = new JEditorPane(new Div()) {
        };
        assertFalse(custom.getPeer() instanceof SJEditorPane);
    }
}
