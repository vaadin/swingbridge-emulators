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

package vaadinx.swing.text;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RteHtmlCodec} — the StyledDocument ⇄ RTE-subset HTML bridge behind
 * {@link vaadinx.swing.JTextPane} (D_jtextpane). Pure logic: no Vaadin/Karibu, just a
 * {@link DefaultStyledDocument} on one side and the HTML string on the other. The
 * {@code fromHtml(toHtml(doc))} round trip is the drift-eliminating assertion — one format,
 * both codecs checked against each other; the {@code fromHtml} fixtures also pin the Quill
 * read-back normalizations captured by the real-browser probe.
 */
class RteHtmlCodecTest {

    private final List<String> warns = new ArrayList<>();

    @BeforeEach
    void hook() {
        warns.clear();
        EHelper.warnHook = msg -> warns.add(msg);
    }

    @AfterEach
    void unhook() {
        EHelper.warnHook = msg -> { };
    }

    /** Stands in for Kotlin's {@code DefaultStyledDocument.() -> Unit} receiver lambda. */
    @FunctionalInterface
    private interface DocBuilder {
        void build(DefaultStyledDocument doc) throws BadLocationException;
    }

    private static DefaultStyledDocument doc(DocBuilder build) throws BadLocationException {
        DefaultStyledDocument d = new DefaultStyledDocument();
        build.build(d);
        return d;
    }

    // ---- serialize: StyledDocument -> HTML (the push format) ----

    @Test
    @DisplayName("plain text serializes to a paragraph")
    void plainTextSerializesToAParagraph() throws BadLocationException {
        assertEquals("<p>hello</p>", RteHtmlCodec.toHtml(doc(d -> d.insertString(0, "hello", null))));
    }

    @Test
    @DisplayName("inline styles nest into subset tags and colour spans")
    void inlineStylesNestIntoSubsetTagsAndColourSpans() throws BadLocationException {
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        assertEquals("<p><strong>hi</strong></p>", RteHtmlCodec.toHtml(doc(d -> d.insertString(0, "hi", bold))));

        SimpleAttributeSet red = new SimpleAttributeSet();
        StyleConstants.setForeground(red, Color.RED);
        assertEquals(
                "<p><span style=\"color:rgb(255, 0, 0);\">r</span></p>",
                RteHtmlCodec.toHtml(doc(d -> d.insertString(0, "r", red))));
    }

    @Test
    @DisplayName("alignment becomes a text-align style and headings wrap")
    void alignmentBecomesATextAlignStyleAndHeadingsWrap() throws BadLocationException {
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        assertEquals(
                "<p style=\"text-align: center\">mid</p>",
                RteHtmlCodec.toHtml(doc(d -> {
                    d.insertString(0, "mid", null);
                    d.setParagraphAttributes(0, 1, center, false);
                })));
        SimpleAttributeSet heading = new SimpleAttributeSet();
        heading.addAttribute(RteHtmlCodec.HEADER, 2);
        assertEquals(
                "<h2>Title</h2>",
                RteHtmlCodec.toHtml(doc(d -> {
                    d.insertString(0, "Title", null);
                    d.setParagraphAttributes(0, 1, heading, false);
                })));
    }

    @Test
    @DisplayName("alignment rides along on headings and blockquotes")
    void alignmentRidesAlongOnHeadingsAndBlockquotes() throws BadLocationException {
        // RTE persists text-align as inline style on <p>/<h1..3>/<blockquote> (browser-probed);
        // the codec emits it on those three, not just plain paragraphs.
        SimpleAttributeSet centerHeading = new SimpleAttributeSet();
        centerHeading.addAttribute(RteHtmlCodec.HEADER, 2);
        StyleConstants.setAlignment(centerHeading, StyleConstants.ALIGN_CENTER);
        assertEquals(
                "<h2 style=\"text-align: center\">Title</h2>",
                RteHtmlCodec.toHtml(doc(d -> {
                    d.insertString(0, "Title", null);
                    d.setParagraphAttributes(0, 1, centerHeading, false);
                })));
        SimpleAttributeSet rightQuote = new SimpleAttributeSet();
        rightQuote.addAttribute(RteHtmlCodec.BLOCKQUOTE, true);
        StyleConstants.setAlignment(rightQuote, StyleConstants.ALIGN_RIGHT);
        assertEquals(
                "<blockquote style=\"text-align: right\">Q</blockquote>",
                RteHtmlCodec.toHtml(doc(d -> {
                    d.insertString(0, "Q", null);
                    d.setParagraphAttributes(0, 1, rightQuote, false);
                })));
    }

    @Test
    @DisplayName("alignment on code-block and list-item is dropped and WARNs")
    void alignmentOnCodeBlockAndListItemIsDroppedAndWarns() throws BadLocationException {
        // RTE carries align in its Delta for <pre>/<li> but never serializes it, so emitting
        // text-align there would render nothing and break round-trip idempotency (D_gap_severity_triage(a)).
        SimpleAttributeSet centeredCode = new SimpleAttributeSet();
        centeredCode.addAttribute(RteHtmlCodec.CODE_BLOCK, true);
        StyleConstants.setAlignment(centeredCode, StyleConstants.ALIGN_CENTER);
        assertEquals(
                "<pre>x</pre>",
                RteHtmlCodec.toHtml(doc(d -> {
                    d.insertString(0, "x", null);
                    d.setParagraphAttributes(0, 1, centeredCode, false);
                })));
        SimpleAttributeSet centeredItem = new SimpleAttributeSet();
        centeredItem.addAttribute(RteHtmlCodec.LIST, "bullet");
        StyleConstants.setAlignment(centeredItem, StyleConstants.ALIGN_CENTER);
        assertEquals(
                "<ul><li>i</li></ul>",
                RteHtmlCodec.toHtml(doc(d -> {
                    d.insertString(0, "i", null);
                    d.setParagraphAttributes(0, 1, centeredItem, false);
                })));
        assertTrue(warns.stream().filter(it -> it.contains("alignment on <")).count() == 2,
                "both <pre> and <li> alignment drops should WARN: " + warns);
    }

    @Test
    @DisplayName("a heading level above RTE's h1-h3 clamps to h3 and WARNs")
    void aHeadingLevelAboveRtesH1H3ClampsToH3AndWarns() throws BadLocationException {
        SimpleAttributeSet h5 = new SimpleAttributeSet();
        h5.addAttribute(RteHtmlCodec.HEADER, 5);
        assertEquals(
                "<h3>Deep</h3>",
                RteHtmlCodec.toHtml(doc(d -> {
                    d.insertString(0, "Deep", null);
                    d.setParagraphAttributes(0, 1, h5, false);
                })));
        assertTrue(warns.stream().anyMatch(it -> it.contains("h1..h3")), "clamp should WARN: " + warns);
    }

    @Test
    @DisplayName("text is HTML-escaped")
    void textIsHtmlEscaped() throws BadLocationException {
        assertEquals("<p>&lt;b&gt; &amp; &lt;/b&gt;</p>",
                RteHtmlCodec.toHtml(doc(d -> d.insertString(0, "<b> & </b>", null))));
    }

    @Test
    @DisplayName("an ImageIcon run serializes to an img tag")
    void anImageIconRunSerializesToAnImgTag() throws BadLocationException {
        ImageIcon icon = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setIcon(attr, icon);
        assertTrue(RteHtmlCodec.toHtml(doc(d -> d.insertString(0, " ", attr)))
                .contains("<img src=\"data:image/png;base64,"));
    }

    @Test
    @DisplayName("unsupported font attributes WARN on serialize")
    void unsupportedFontAttributesWarnOnSerialize() throws BadLocationException {
        // FontSize 13 is below the h3 band (14), so it's dropped + WARNed as before; a size in a
        // heading band would instead be consumed by an <hN> wrapper (see the Idea A tests).
        SimpleAttributeSet big = new SimpleAttributeSet();
        StyleConstants.setFontSize(big, 13);
        StyleConstants.setFontFamily(big, "Serif");
        RteHtmlCodec.toHtml(doc(d -> d.insertString(0, "x", big)));
        assertTrue(warns.stream().anyMatch(it -> it.contains("FontSize")), "FontSize should WARN: " + warns);
        assertTrue(warns.stream().anyMatch(it -> it.contains("FontFamily")), "FontFamily should WARN: " + warns);
    }

    /** Local helper standing in for the Kotlin local {@code fun html(size: Int)}. */
    private static String html(int size) throws BadLocationException {
        return RteHtmlCodec.toHtml(doc(d -> {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setFontSize(a, size);
            d.insertString(0, "Big", a);
        }));
    }

    @Test
    @DisplayName("a uniform heading-band FontSize maps to a heading and consumes the size WARN-free")
    void aUniformHeadingBandFontSizeMapsToAHeadingAndConsumesTheSizeWarnFree() throws BadLocationException {
        // Swing-authored large text (no HEADER key) renders as <hN> by band; the size is consumed
        // by the wrapper, not dropped, so it must not WARN.
        assertEquals("<h1>Big</h1>", html(24));
        assertEquals("<h2>Big</h2>", html(20));
        assertEquals("<h3>Big</h3>", html(16));
        assertEquals("<h1>Big</h1>", html(40)); // above the top band still caps at h1
        assertTrue(warns.stream().noneMatch(it -> it.contains("FontSize")), "heading-consumed size must not WARN: " + warns);
    }

    @Test
    @DisplayName("a mixed-size or partially-sized paragraph is not a heading")
    void aMixedSizeOrPartiallySizedParagraphIsNotAHeading() throws BadLocationException {
        SimpleAttributeSet h1 = new SimpleAttributeSet();
        StyleConstants.setFontSize(h1, 24);
        SimpleAttributeSet h2 = new SimpleAttributeSet();
        StyleConstants.setFontSize(h2, 20);
        // Two different bands in one paragraph -> plain <p>, sizes dropped + WARNed.
        assertEquals("<p>ab</p>", RteHtmlCodec.toHtml(doc(d -> {
            d.insertString(0, "a", h1); d.insertString(1, "b", h2);
        })));
        // A visible run without a size breaks uniformity -> plain <p>.
        assertEquals("<p>ab</p>", RteHtmlCodec.toHtml(doc(d -> {
            d.insertString(0, "a", h1); d.insertString(1, "b", null);
        })));
    }

    @Test
    @DisplayName("a trailing unsized newline run does not defeat heading detection")
    void aTrailingUnsizedNewlineRunDoesNotDefeatHeadingDetection() throws BadLocationException {
        // A migrator's setCharacterAttributes over the visible text typically leaves the paragraph's
        // trailing '\n' run unsized; that whitespace run is neutral and must not block the heading.
        DefaultStyledDocument d = new DefaultStyledDocument();
        d.insertString(0, "Title\nbody", null);
        SimpleAttributeSet size20 = new SimpleAttributeSet();
        StyleConstants.setFontSize(size20, 20);
        d.setCharacterAttributes(0, 5, size20, false);
        assertEquals("<h2>Title</h2><p>body</p>", RteHtmlCodec.toHtml(d));
    }

    // ---- parse: HTML -> StyledDocument ----

    @Test
    @DisplayName("fromHtml of empty html yields an empty document")
    void fromHtmlOfEmptyHtmlYieldsAnEmptyDocument() throws BadLocationException {
        DefaultStyledDocument d = new DefaultStyledDocument();
        d.insertString(0, "stale", null);
        RteHtmlCodec.fromHtml("", d);
        assertEquals("", d.getText(0, d.getLength()));
    }

    @Test
    @DisplayName("fromHtml restores block alignment onto the paragraph")
    void fromHtmlRestoresBlockAlignmentOntoTheParagraph() throws BadLocationException {
        DefaultStyledDocument d = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml("<p style=\"text-align: center\">mid</p>", d);
        assertEquals("mid", d.getText(0, d.getLength()));
        assertEquals(StyleConstants.ALIGN_CENTER,
                StyleConstants.getAlignment(d.getParagraphElement(0).getAttributes()));
    }

    @Test
    @DisplayName("fromHtml restores alignment merged onto headings and blockquotes")
    void fromHtmlRestoresAlignmentMergedOntoHeadingsAndBlockquotes() throws BadLocationException {
        DefaultStyledDocument d = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml(
                "<h2 style=\"text-align: center\">Title</h2><blockquote style=\"text-align: right\">Q</blockquote>", d);
        AttributeSet h = d.getParagraphElement(0).getAttributes();
        assertEquals(2, h.getAttribute(RteHtmlCodec.HEADER), "heading level kept");
        assertEquals(StyleConstants.ALIGN_CENTER, StyleConstants.getAlignment(h), "heading centered");
        AttributeSet q = d.getParagraphElement(d.getText(0, d.getLength()).indexOf("Q")).getAttributes();
        assertEquals(true, q.getAttribute(RteHtmlCodec.BLOCKQUOTE), "blockquote flag kept");
        assertEquals(StyleConstants.ALIGN_RIGHT, StyleConstants.getAlignment(q), "blockquote right-aligned");
    }

    @Test
    @DisplayName("fromHtml fabricates the representative FontSize onto heading runs")
    void fromHtmlFabricatesTheRepresentativeFontSizeOntoHeadingRuns() throws BadLocationException {
        DefaultStyledDocument d = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml("<h1>A</h1><h2>B</h2><h3>C</h3>", d);
        assertEquals(24, StyleConstants.getFontSize(d.getCharacterElement(d.getText(0, d.getLength()).indexOf("A")).getAttributes()));
        assertEquals(20, StyleConstants.getFontSize(d.getCharacterElement(d.getText(0, d.getLength()).indexOf("B")).getAttributes()));
        assertEquals(16, StyleConstants.getFontSize(d.getCharacterElement(d.getText(0, d.getLength()).indexOf("C")).getAttributes()));
        // HEADER stays the authoritative carrier alongside the fabricated size.
        assertEquals(1, d.getParagraphElement(0).getAttributes().getAttribute(RteHtmlCodec.HEADER));
    }

    @Test
    @DisplayName("a Swing-authored FontSize heading round-trips idempotently")
    void aSwingAuthoredFontSizeHeadingRoundTripsIdempotently() throws BadLocationException {
        // FontSize 20 (no HEADER) -> <h2> -> read back as HEADER=2 + FontSize 20 -> <h2> again.
        DefaultStyledDocument original = doc(d -> {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setFontSize(a, 20);
            d.insertString(0, "Head", a);
        });
        String html = RteHtmlCodec.toHtml(original);
        assertEquals("<h2>Head</h2>", html);
        DefaultStyledDocument rebuilt = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml(html, rebuilt);
        assertEquals(2, rebuilt.getParagraphElement(0).getAttributes().getAttribute(RteHtmlCodec.HEADER));
        assertEquals(20, StyleConstants.getFontSize(rebuilt.getCharacterElement(0).getAttributes()));
        assertEquals(html, RteHtmlCodec.toHtml(rebuilt), "second serialize must be identical");
    }

    @Test
    @DisplayName("an aligned paragraph does not bleed alignment into a following unaligned paragraph")
    void anAlignedParagraphDoesNotBleedAlignmentIntoAFollowingUnalignedParagraph() {
        DefaultStyledDocument d = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml("<p style=\"text-align: center\">a</p><p>b</p>", d);
        assertEquals(StyleConstants.ALIGN_CENTER,
                StyleConstants.getAlignment(d.getParagraphElement(0).getAttributes()), "para0 centered");
        assertEquals(StyleConstants.ALIGN_LEFT,
                StyleConstants.getAlignment(d.getParagraphElement(2).getAttributes()), "para1 must stay left");
    }

    @Test
    @DisplayName("fromHtml on a previously-aligned document does not inherit stale paragraph attributes")
    void fromHtmlOnAPreviouslyAlignedDocumentDoesNotInheritStaleParagraphAttributes() throws BadLocationException {
        // Real reverse-sync runs on an already-populated doc (a prior push/edit left it
        // centered). doc.remove leaves an empty paragraph carrying that old alignment,
        // and the newline-split during rebuild bleeds it into the next paragraph unless
        // fromHtml resets each paragraph. (Fresh-doc tests never exercise this.)
        DefaultStyledDocument d = new DefaultStyledDocument();
        d.insertString(0, "old", null);
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        d.setParagraphAttributes(0, 1, center, false);

        RteHtmlCodec.fromHtml("<p style=\"text-align: center\">a</p><p>b</p>", d);

        assertEquals(StyleConstants.ALIGN_CENTER,
                StyleConstants.getAlignment(d.getParagraphElement(0).getAttributes()), "para0 centered");
        int p1 = d.getText(0, d.getLength()).indexOf("b");
        assertEquals(StyleConstants.ALIGN_LEFT,
                StyleConstants.getAlignment(d.getParagraphElement(p1).getAttributes()), "para1 must not inherit center");
    }

    @Test
    @DisplayName("fromHtml decodes a data-URL image into an icon run")
    void fromHtmlDecodesADataUrlImageIntoAnIconRun() {
        String png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4"
                + "2mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        DefaultStyledDocument d = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml("<p><img src=\"" + png + "\"></p>", d);
        assertNotNull(StyleConstants.getIcon(d.getCharacterElement(0).getAttributes()),
                "expected an icon on the placeholder run");
    }

    @Test
    @DisplayName("fromHtml absorbs Quill normalizations captured by the browser probe")
    void fromHtmlAbsorbsQuillNormalizationsCapturedByTheBrowserProbe() throws BadLocationException {
        // Colour migrated onto <strong> + italic nested inside (Quill's shape).
        DefaultStyledDocument d1 = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml("<p><strong style=\"color: rgb(255, 0, 0);\"><em>bi</em></strong></p>", d1);
        AttributeSet a = d1.getCharacterElement(0).getAttributes();
        assertTrue(StyleConstants.isBold(a), "bold from <strong>");
        assertTrue(StyleConstants.isItalic(a), "italic from nested <em>");
        assertEquals(new Color(255, 0, 0), StyleConstants.getForeground(a), "colour from <strong style>");

        // Inline nesting order swapped by Quill (<u><s> -> <s><u>) — accumulate regardless.
        DefaultStyledDocument d2 = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml("<p><s><u>us</u></s></p>", d2);
        AttributeSet b = d2.getCharacterElement(0).getAttributes();
        assertTrue(StyleConstants.isUnderline(b) && StyleConstants.isStrikeThrough(b));

        // Link gains rel/target — LINK carries just the href.
        DefaultStyledDocument d3 = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml("<p><a href=\"https://x\" rel=\"nofollow\" target=\"_blank\">link</a></p>", d3);
        assertEquals("https://x", d3.getCharacterElement(0).getAttributes().getAttribute(RteHtmlCodec.LINK));

        // <pre> keeps a trailing newline from Quill — stripped, no spurious blank line.
        DefaultStyledDocument d4 = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml("<pre>code();\n</pre>", d4);
        assertEquals("code();", d4.getText(0, d4.getLength()));
        assertNotNull(d4.getParagraphElement(0).getAttributes().getAttribute(RteHtmlCodec.CODE_BLOCK));
    }

    // ---- HTML round trip (the drift-eliminating assertion: our two codecs vs each other) ----

    @Test
    @DisplayName("HTML round trip preserves text, inline styling, headings, and lists")
    void htmlRoundTripPreservesTextInlineStylingHeadingsAndLists() throws BadLocationException {
        SimpleAttributeSet boldRed = new SimpleAttributeSet();
        StyleConstants.setBold(boldRed, true); StyleConstants.setForeground(boldRed, new Color(255, 0, 0));
        SimpleAttributeSet heading = new SimpleAttributeSet();
        heading.addAttribute(RteHtmlCodec.HEADER, 2);
        SimpleAttributeSet bullet = new SimpleAttributeSet();
        bullet.addAttribute(RteHtmlCodec.LIST, "bullet");
        DefaultStyledDocument original = doc(d -> {
            d.insertString(0, "Title\n", null);
            d.setParagraphAttributes(0, 1, heading, false);
            d.insertString(6, "plain ", null);
            d.insertString(12, "red", boldRed);
            d.insertString(15, "\n", null);
            d.insertString(16, "item", null);
            d.setParagraphAttributes(16, 1, bullet, false);
        });
        String html = RteHtmlCodec.toHtml(original);

        DefaultStyledDocument rebuilt = new DefaultStyledDocument();
        RteHtmlCodec.fromHtml(html, rebuilt);

        assertEquals(original.getText(0, original.getLength()), rebuilt.getText(0, rebuilt.getLength()));
        assertEquals(2, rebuilt.getParagraphElement(0).getAttributes().getAttribute(RteHtmlCodec.HEADER));
        int redIdx = rebuilt.getText(0, rebuilt.getLength()).indexOf("red");
        AttributeSet ra = rebuilt.getCharacterElement(redIdx).getAttributes();
        assertTrue(StyleConstants.isBold(ra));
        assertEquals(new Color(255, 0, 0), StyleConstants.getForeground(ra));
        int itemIdx = rebuilt.getText(0, rebuilt.getLength()).indexOf("item");
        assertEquals("bullet", rebuilt.getParagraphElement(itemIdx).getAttributes().getAttribute(RteHtmlCodec.LIST));
        // Re-serializing the rebuilt doc yields the same HTML — the codec pair is self-consistent.
        assertEquals(html, RteHtmlCodec.toHtml(rebuilt));
    }
}
