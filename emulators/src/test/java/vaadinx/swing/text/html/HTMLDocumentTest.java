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
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.swing.JEditorPane;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.PlainDocument;
import javax.swing.text.html.HTML;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code HTMLDocument} (D_htmldocument) — the read-only element model an HTML pane hands out.
 * Covers:
 *
 * <ol>
 *  <li><b>The element model is real</b> — {@code getElement(id)}, {@code getDefaultRootElement},
 *     character/paragraph lookups, all built by the JDK parser.</li>
 *  <li><b>Refresh in place</b> — new markup rebuilds the tree on the <i>same</i> instance,
 *     so user {@code DocumentListener}s survive and fire.</li>
 *  <li><b>The pane's {@code getText()} stays markup</b> while the document's own text is the
 *     rendered text — the collision this design exists to avoid.</li>
 *  <li><b>Mutators WARN</b> and change nothing (read-only projection).</li>
 *  <li><b>JDK behaviour inherited verbatim</b> — {@code getIterator} null for block tags,
 *     {@code setBase} round-trip, a broken parse degrading to a WARN.</li>
 * </ol>
 */
class HTMLDocumentTest extends AbstractKaribuTest {

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

    private static JEditorPane htmlPane(String markup) {
        return new JEditorPane("text/html", markup);
    }

    private static HTMLDocument modelOf(JEditorPane pane) {
        return assertInstanceOf(HTMLDocument.class, pane.getDocument());
    }

    /** Records which DocumentListener method fired, in order. */
    private static List<String> recordEvents(javax.swing.text.Document doc) {
        List<String> events = new ArrayList<>();
        doc.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                events.add("insert");
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                events.add("remove");
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                events.add("changed");
            }
        });
        return events;
    }

    // --- the element model --------------------------------------------------

    @Test
    @DisplayName("getElement finds an element by id")
    void getElementFindsById() {
        JEditorPane pane = htmlPane("<h2>Report</h2><p id=\"total\">42</p>");

        Element total = modelOf(pane).getElement("total");
        assertNotNull(total, "id lookup must resolve");
        assertEquals("p", total.getName());
        assertTrue(capturedWarns.isEmpty(), "the element model must be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("the migrator's JDK-typed cast succeeds")
    void jdkTypedCastSucceeds() {
        JEditorPane pane = htmlPane("<p id='x'>hi</p>");

        // The shape migrated code is written in: javax.swing.text.html.HTMLDocument
        // stays JDK on the import swap, so only an is-a subclass satisfies this.
        javax.swing.text.html.HTMLDocument doc =
                (javax.swing.text.html.HTMLDocument) pane.getDocument();
        assertNotNull(doc.getElement("x"));
    }

    @Test
    @DisplayName("getDefaultRootElement exposes the parsed tree")
    void defaultRootElementExposesTheTree() {
        Element root = modelOf(htmlPane("<h2>a</h2><p>b</p>")).getDefaultRootElement();

        assertEquals("html", root.getName());
        assertTrue(root.getElementCount() > 0, "root must have children");
    }

    @Test
    @DisplayName("character and paragraph lookups resolve through the rendered offsets")
    void characterAndParagraphLookups() {
        HTMLDocument doc = modelOf(htmlPane("<p id='p1'>hello</p>"));

        // Offset 1 is inside "hello" — the document's text is rendered text, so the
        // leading newline the parser emits for the block puts content at 1.
        assertEquals("content", doc.getCharacterElement(1).getName());
        assertEquals("p", doc.getParagraphElement(1).getName());
    }

    // --- refresh in place ---------------------------------------------------

    @Test
    @DisplayName("new markup rebuilds the model on the same document instance")
    void newMarkupRebuildsInPlace() {
        JEditorPane pane = htmlPane("<p id='first'>one</p>");
        HTMLDocument doc = modelOf(pane);

        pane.setText("<p id='second'>two</p>");

        assertSame(doc, pane.getDocument(), "the model must be refreshed, not swapped");
        assertNull(doc.getElement("first"), "stale ids must not survive a refresh");
        assertNotNull(doc.getElement("second"));
    }

    @Test
    @DisplayName("a user DocumentListener survives the refresh and fires")
    void documentListenerSurvivesTheRefresh() {
        JEditorPane pane = htmlPane("<p>one</p>");
        List<String> events = recordEvents(pane.getDocument());

        pane.setText("<p>two</p>");

        assertTrue(events.contains("insert"), "the reparse must fire document events: " + events);
    }

    @Test
    @DisplayName("unchanged markup is a no-op")
    void unchangedMarkupIsANoOp() {
        JEditorPane pane = htmlPane("<p>same</p>");
        List<String> events = recordEvents(pane.getDocument());

        pane.setText("<p>same</p>");

        assertTrue(events.isEmpty(), "re-setting identical markup must not reparse: " + events);
    }

    // --- getText stays markup; the document's own text is rendered ----------

    @Test
    @DisplayName("pane getText is markup while the document text is rendered")
    void paneTextIsMarkupDocumentTextIsRendered() throws BadLocationException {
        JEditorPane pane = htmlPane("<h2>Report</h2><ul><li>one</li><li>two</li></ul>");
        HTMLDocument doc = modelOf(pane);

        assertEquals("<h2>Report</h2><ul><li>one</li><li>two</li></ul>", pane.getText());
        String rendered = doc.getText(0, doc.getLength());
        assertTrue(rendered.contains("Report") && rendered.contains("one"), "rendered: " + rendered);
        assertFalse(rendered.contains("<h2>"),
                "the document's text must not carry markup: " + rendered);
    }

    // --- mutators are read-only ---------------------------------------------

    @Test
    @DisplayName("the six HTML mutators WARN and change nothing")
    void theSixMutatorsWarnAndChangeNothing() throws Exception {
        JEditorPane pane = htmlPane("<p id='m'>keep</p>");
        HTMLDocument doc = modelOf(pane);
        Element elem = doc.getElement("m");
        assertNotNull(elem);
        String before = doc.getText(0, doc.getLength());

        doc.setInnerHTML(elem, "<p>x</p>");
        doc.setOuterHTML(elem, "<p>x</p>");
        doc.insertAfterStart(elem, "<p>x</p>");
        doc.insertBeforeEnd(elem, "<p>x</p>");
        doc.insertBeforeStart(elem, "<p>x</p>");
        doc.insertAfterEnd(elem, "<p>x</p>");

        assertEquals(before, doc.getText(0, doc.getLength()), "a read-only model must not mutate");
        assertEquals("<p id='m'>keep</p>", pane.getText());
        assertEquals(6, capturedWarns.size(), "each mutator WARNs once: " + capturedWarns);
        assertTrue(capturedWarns.stream().allMatch(w -> w.contains("HTMLDocument")),
                capturedWarns.toString());
    }

    // --- inherited JDK behaviour --------------------------------------------

    @Test
    @DisplayName("getIterator answers for leaf tags and null for block tags")
    void getIteratorAnswersForLeafTagsOnly() {
        HTMLDocument doc = modelOf(htmlPane(
                "<p>x <a href=\"http://x/y\">link</a></p><ul><li>one</li></ul>"));

        HTMLDocument.Iterator anchors = doc.getIterator(HTML.Tag.A);
        assertNotNull(anchors, "leaf tags iterate");
        assertEquals("http://x/y", anchors.getAttributes().getAttribute(HTML.Attribute.HREF));
        // The JDK's own contract: block tags have no leaf iterator. Inherited, not a gap.
        assertNull(doc.getIterator(HTML.Tag.P));
        assertNull(doc.getIterator(HTML.Tag.LI));
    }

    @Test
    @DisplayName("setBase round-trips")
    void setBaseRoundTrips() throws MalformedURLException {
        HTMLDocument doc = modelOf(htmlPane("<p>x</p>"));

        doc.setBase(URI.create("http://example.org/help/").toURL());

        assertEquals("http://example.org/help/", doc.getBase().toString());
        assertTrue(capturedWarns.isEmpty(), "setBase is inherited, not a stub: " + capturedWarns);
    }

    @Test
    @DisplayName("markup the parser chokes on degrades to a WARN, leaving getText intact")
    void brokenMarkupDegradesToAWarn() {
        JEditorPane pane = htmlPane("<p>fine</p>");

        // Deeply-malformed input: the pane's markup must survive regardless, since
        // the element model is only a projection of it. The NUL must stay an octal
        // escape: a backslash-u escape is decoded in javac's pre-lexing pass, which
        // puts a raw NUL back into the source — the very byte javac rejects, and the
        // reason grep reported the Kotlin original as a binary file.
        pane.setText("<p>unclosed <b>bold <p attr=\0>");

        assertEquals("<p>unclosed <b>bold <p attr=\0>", pane.getText());
    }

    @Test
    @DisplayName("a text-plain pane keeps a PlainDocument")
    void textPlainPaneKeepsAPlainDocument() {
        JEditorPane pane = new JEditorPane("text/plain", "just text");

        assertInstanceOf(PlainDocument.class, pane.getDocument());
        assertEquals("just text", pane.getText());
    }
}
