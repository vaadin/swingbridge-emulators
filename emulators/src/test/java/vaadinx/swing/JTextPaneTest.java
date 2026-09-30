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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJEditorPane;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.swing.text.RteHtmlCodec;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.PlainDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the JTextPane emulator (D_jtextpane). Pairs with
 * {@link vaadinx.swing.text.RteHtmlCodecTest}
 * (which covers the codec in isolation); this layer covers the thin subclass over
 * the SJEditorPane (RTE) peer: the DefaultStyledDocument backing, the two-way HTML
 * sync, plain-text getText, the faithful throws, and the drop-and-WARN surface.
 */
class JTextPaneTest extends AbstractKaribuTest {

    private final List<String> warns = new ArrayList<>();

    @BeforeEach
    void hook() {
        warns.clear();
        Consumer<String> sink = warns::add;
        EHelper.warnHook = sink;
        SHelper.warnHook = sink;
    }

    @AfterEach
    void unhook() {
        EHelper.warnHook = msg -> { };
        SHelper.warnHook = msg -> { };
    }

    /**
     * The peer's rendered value is HTML (JTextPane pushes via the main HTML channel;
     * {@code asDelta().setValue} is avoided — its async htmlValue reconcile clobbers,
     * D_jtextpane).
     */
    private static String peerHtml(JTextPane pane) {
        return ((SJEditorPane) pane.getPeer()).getValue();
    }

    // ---- construction / R_leaf_peer_lockdown lock-down ----

    @Test
    @DisplayName("default ctor peers over SJEditorPane with a DefaultStyledDocument")
    void defaultCtorPeersOverEditorPane() {
        JTextPane pane = new JTextPane();
        assertInstanceOf(SJEditorPane.class, pane.getPeer());
        assertInstanceOf(DefaultStyledDocument.class, pane.getDocument());
        assertInstanceOf(DefaultStyledDocument.class, pane.getStyledDocument());
        assertEquals("TextPaneUI", pane.getUIClassID());
        assertNoWarns(warns, "default ctor should be WARN-free: " + warns);
    }

    @Test
    @DisplayName("StyledDocument ctor installs the given model")
    void styledDocumentCtorInstallsTheModel() throws BadLocationException {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, "seed", null);
        JTextPane pane = new JTextPane(doc);
        assertEquals(doc, pane.getStyledDocument());
        assertEquals("seed", pane.getText());
    }

    // ---- getText is plain text (faithful JTextPane divergence) ----

    @Test
    @DisplayName("getText returns plain visible text, not markup")
    void getTextIsPlainText() {
        JTextPane pane = new JTextPane();
        pane.setText("hello");
        assertEquals("hello", pane.getText());
        // Unlike JEditorPane, HTML in setText is literal text, not rendered markup.
        pane.setText("<b>hi</b>");
        assertEquals("<b>hi</b>", pane.getText());
    }

    // ---- write path: offset-based styling renders to the peer's Delta ----

    @Test
    @DisplayName("document styling renders to the peer HTML")
    void documentStylingRendersToPeerHtml() throws BadLocationException {
        JTextPane pane = new JTextPane();
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        StyleConstants.setForeground(bold, Color.RED);
        pane.getStyledDocument().insertString(0, "alert", bold);

        String html = peerHtml(pane);
        assertTrue(html.contains("<strong>"), "peer HTML missing bold: " + html);
        assertTrue(html.contains("color:rgb(255, 0, 0)"), "peer HTML missing colour: " + html);
        assertTrue(html.contains("alert"), "peer HTML missing text: " + html);
    }

    @Test
    @DisplayName("setParagraphAttributes aligns the first paragraph and renders")
    void setParagraphAttributesAligns() throws BadLocationException {
        JTextPane pane = new JTextPane();
        pane.getStyledDocument().insertString(0, "centered", null);
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        pane.setParagraphAttributes(center, false);
        assertTrue(peerHtml(pane).contains("text-align: center"),
                "expected centered paragraph: " + peerHtml(pane));
    }

    // ---- reverse path: a browser HTML edit rebuilds the document ----

    @Test
    @DisplayName("a browser HTML edit rebuilds the document and fires DocumentListeners")
    void browserEditRebuildsTheDocument() {
        JTextPane pane = new JTextPane();
        List<String> edits = new ArrayList<>();
        pane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                edits.add("insert");
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                edits.add("remove");
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        // The reverse-sync entry point (fired by the peer's value-change on a genuine
        // browser edit, reading getValue()) parses the client's HTML and rebuilds the
        // document.
        pane.syncDocumentFromHtml("<p><strong>typed</strong></p>");

        assertEquals("typed", pane.getText());
        assertTrue(StyleConstants.isBold(
                pane.getStyledDocument().getCharacterElement(0).getAttributes()));
        assertFalse(edits.isEmpty(), "user DocumentListener should have fired on the rebuild");
    }

    @Test
    @DisplayName("a browser heading edit applies HEADER and fabricates FontSize")
    void browserHeadingAppliesHeaderAndFontSize() {
        JTextPane pane = new JTextPane();
        // Clicking H2 in the RTE toolbar sends the heading HTML back (RTE appends a
        // trailing empty <p>); the reverse-sync must apply the HEADER key AND the
        // fabricated representative FontSize onto the run.
        pane.syncDocumentFromHtml("<h2>H2 Header</h2><p></p>");

        assertEquals("H2 Header", pane.getText().trim());
        assertEquals(2, pane.getStyledDocument().getParagraphElement(0)
                        .getAttributes().getAttribute(RteHtmlCodec.HEADER),
                "browser H2 must set the authoritative HEADER key");
        assertEquals(20, StyleConstants.getFontSize(
                        pane.getStyledDocument().getCharacterElement(0).getAttributes()),
                "browser H2 must fabricate the representative FontSize (introspection echo)");
    }

    // ---- input attributes (selection-based, no server-side selection) ----

    @Test
    @DisplayName("setCharacterAttributes updates the input attributes")
    void setCharacterAttributesUpdatesInputAttributes() {
        JTextPane pane = new JTextPane();
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        pane.setCharacterAttributes(bold, false);
        assertTrue(StyleConstants.isBold(pane.getCharacterAttributes()));
        assertTrue(StyleConstants.isBold(pane.getInputAttributes()));
    }

    @Test
    @DisplayName("createDefaultEditorKit runs at construction and its kit is the one read back")
    void createDefaultEditorKitIsTheInstalledHook() {
        // R12DeadHookTest gates the wiring structurally; this asserts a migrator's
        // override is observable (R_no_vaadin_in_api limb 2).
        class Seeded extends JTextPane {
            // No initializer: `= 0` would run after the super ctor and erase the count.
            int built;

            Seeded() {
            }

            Seeded(javax.swing.text.StyledDocument doc) {
                super(doc);
            }

            @Override
            protected javax.swing.text.EditorKit createDefaultEditorKit() {
                built++;
                javax.swing.text.StyledEditorKit kit = new javax.swing.text.StyledEditorKit();
                StyleConstants.setItalic(kit.getInputAttributes(), true);
                return kit;
            }
        }
        Seeded pane = new Seeded();
        assertEquals(1, pane.built, "the ctor must build its kit through the hook");
        assertTrue(StyleConstants.isItalic(pane.getInputAttributes()),
                "getInputAttributes must read the hook's kit, not a field of its own");

        Seeded withDoc = new Seeded(new javax.swing.text.DefaultStyledDocument());
        assertTrue(StyleConstants.isItalic(withDoc.getInputAttributes()),
                "the StyledDocument ctor delegates to this(), so it wires the same way");
    }

    // ---- ImageIcon embedding ----

    @Test
    @DisplayName("insertIcon with an ImageIcon renders as an image embed")
    void insertImageIconEmbedsTheRaster() {
        JTextPane pane = new JTextPane();
        ImageIcon icon = new ImageIcon(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));
        pane.insertIcon(icon);
        assertTrue(peerHtml(pane).contains("<img src=\"data:image/png;base64,"),
                "expected an image tag in the peer HTML: " + peerHtml(pane));
        assertNoWarns(warns, "ImageIcon embedding is supported and should not WARN: " + warns);
    }

    @Test
    @DisplayName("insertIcon with a non-ImageIcon icon WARNs")
    void insertNonImageIconWarns() {
        JTextPane pane = new JTextPane();
        Icon custom = new Icon() {
            @Override
            public void paintIcon(vaadinx.awt.Component c, Graphics g, int x, int y) {
            }

            @Override
            public int getIconWidth() {
                return 4;
            }

            @Override
            public int getIconHeight() {
                return 4;
            }
        };
        pane.insertIcon(custom);
        assertTrue(warns.stream().anyMatch(w -> w.contains("insertIcon")),
                "non-ImageIcon should WARN: " + warns);
    }

    @Test
    @DisplayName("insertComponent WARNs")
    void insertComponentWarns() {
        JTextPane pane = new JTextPane();
        pane.insertComponent(new JLabel("x"));
        assertTrue(warns.stream().anyMatch(w -> w.contains("insertComponent")),
                "insertComponent should WARN: " + warns);
    }

    // ---- faithful throws (R_match_swing_errors) ----

    @Test
    @DisplayName("setDocument rejects a non-StyledDocument")
    void setDocumentRejectsPlainDocument() {
        JTextPane pane = new JTextPane();
        assertThrows(IllegalArgumentException.class, () -> pane.setDocument(new PlainDocument()));
    }

    @Test
    @DisplayName("setStyledDocument accepts a StyledDocument")
    void setStyledDocumentAccepts() throws BadLocationException {
        JTextPane pane = new JTextPane();
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, "ok", null);
        pane.setStyledDocument(doc);
        assertEquals("ok", pane.getText());
    }

    @Test
    @DisplayName("setEditorKit rejects a non-StyledEditorKit but accepts a StyledEditorKit with a WARN")
    void setEditorKitRejectsPlainAcceptsStyledWithWarn() {
        JTextPane pane = new JTextPane();
        assertThrows(IllegalArgumentException.class,
                () -> pane.setEditorKit(new javax.swing.text.DefaultEditorKit()));
        warns.clear();
        pane.setEditorKit(new javax.swing.text.StyledEditorKit());   // accepted, but undriven → WARN
        assertTrue(warns.stream().anyMatch(w -> w.contains("setEditorKit")),
                "accepted StyledEditorKit should WARN: " + warns);
    }

    // ---- named styles delegate to the document ----

    @Test
    @DisplayName("addStyle and getStyle delegate to the StyledDocument")
    void addStyleDelegatesToTheDocument() {
        JTextPane pane = new JTextPane();
        Style s = pane.addStyle("emph", null);
        StyleConstants.setItalic(s, true);
        assertEquals(s, pane.getStyle("emph"));
    }

    @Test
    @DisplayName("setContentType text-html leaves JTextPane's StyledDocument in place")
    void contentTypeHtmlKeepsTheStyledDocument() {
        // JEditorPane swaps in an HTMLDocument for text/html (D_htmldocument); JTextPane must
        // not, or its StyledDocument surface and RteHtmlCodec push would vanish.
        JTextPane pane = new JTextPane();
        javax.swing.text.StyledDocument doc = pane.getStyledDocument();

        pane.setContentType("text/html");

        assertSame(doc, pane.getDocument());
        assertInstanceOf(DefaultStyledDocument.class, pane.getDocument());
    }
}
