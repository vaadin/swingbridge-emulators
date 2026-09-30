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

import vaadinx.EHelper;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;

/**
 * Codec between a Swing {@link StyledDocument} and Vaadin RichTextEditor's <b>HTML</b>
 * value, behind {@code vaadinx.swing.JTextPane} (D_jtextpane). One format both ways: the
 * emulator <b>pushes</b> the document to the peer as RTE-subset HTML ({@link #toHtml},
 * via the main {@code setValue} channel) and <b>reads</b> browser edits back from the
 * same HTML value ({@link #fromHtml}). Reading the property the change event fired on
 * — rather than {@code asDelta()} — removes the cross-property staleness of the old
 * mixed push-HTML/read-Delta split (D_jtextpane).
 *
 * <pre>{@code
 * String html = RteHtmlCodec.toHtml(styledDoc);   // push:  doc  -> "<h2>..</h2><p>..</p>"
 * RteHtmlCodec.fromHtml(html, styledDoc);         // read:  HTML -> doc (clear + re-insert)
 * }</pre>
 *
 * <h2>Mapping (empirically pinned against the live component — D_jtextpane)</h2>
 *
 * <table>
 *   <caption>Swing {@code StyleConstants} / opaque-carry attribute ⇄ RTE HTML</caption>
 *   <tr><td>Bold/Italic/Underline/StrikeThrough</td><td>{@code <strong>/<em>/<u>/<s>}</td></tr>
 *   <tr><td>Foreground/Background</td><td>{@code style="color:…"/"background-color:…"}</td></tr>
 *   <tr><td>Superscript/Subscript</td><td>{@code <sup>/<sub>}</td></tr>
 *   <tr><td>Alignment (paragraph)</td>
 *       <td>inline {@code style="text-align:…"} on {@code <p>}/{@code <h1..3>}/{@code <blockquote>}
 *           (left omitted); dropped + WARNed on {@code <pre>}/{@code <li>} — RTE can't persist it there</td></tr>
 *   <tr><td>{@code IconAttribute} ({@code ImageIcon})</td><td>{@code <img src="data:…">} (base64)</td></tr>
 *   <tr><td>{@code FontSize} (uniform per paragraph)</td>
 *       <td>{@code <h1..3>} by band (≥22→h1, 18..21→h2, 14..17→h3); read fabricates the band's
 *           representative size (24/20/16) back onto the runs. Sub-threshold sizes drop + WARN.</td></tr>
 *   <tr><td>{@link #HEADER}/{@link #LIST}/{@link #BLOCKQUOTE}/{@link #CODE_BLOCK}/{@link #LINK}</td>
 *       <td>{@code <h1..3>}/{@code <ol>|<ul>+<li>}/{@code <blockquote>}/{@code <pre>}/{@code <a href>}
 *           — no {@code StyleConstants} home, carried opaquely so a browser edit round-trips</td></tr>
 * </table>
 *
 * <p>Headings have two carriers: the opaque {@link #HEADER} key (authoritative, browser-origin) and
 * a Swing-authored uniform {@code FontSize} band. HEADER wins when present; the band is the
 * set-from-Swing entry point (RTE has no font-size control, so {@code <hN>} is the only lever that
 * renders bigger). A read {@code <hN>} sets HEADER <b>and</b> fabricates the representative FontSize,
 * so introspection sees the size and the round-trip is idempotent from the first cycle.
 *
 * <p>{@link #fromHtml} absorbs Quill's read-back normalizations (verified in a real
 * browser): inline attributes accumulate <b>regardless of tag nesting order</b>
 * ({@code <s><u>} ≡ {@code <u><s>}); colour is read from a {@code <span style="color">}
 * <b>or</b> a {@code style="color"} on an inline element (Quill moves it onto the
 * format tag); {@code rel}/{@code target}/{@code spellcheck} are ignored; a trailing
 * {@code "\n"} inside {@code <pre>} is stripped.
 *
 * <p>Attributes with no RTE counterpart (font family, indents, line spacing,
 * {@code insertComponent}, a non-{@code ImageIcon} icon, and a sub-band {@code FontSize}) WARN via
 * {@link EHelper#onUnimplemented} and are dropped.
 *
 * <p>Not migrator API — internal to the JTextPane emulator.
 */
public final class RteHtmlCodec {

    private RteHtmlCodec() {
    }

    // Opaque-carry keys for HTML block/inline structure that has no StyleConstants
    // equivalent. Stored on the paragraph/character AttributeSet so a
    // browser-originated heading/list/quote/code/link survives a re-serialize; the
    // Swing styled-setter API never produces them (matching a plain StyledEditorKit
    // JTextPane, which has no such concept).
    public static final Object HEADER = "rte.header";      // Integer 1..3
    public static final Object LIST = "rte.list";          // "ordered" | "bullet"
    public static final Object BLOCKQUOTE = "rte.blockquote"; // Boolean.TRUE
    public static final Object CODE_BLOCK = "rte.codeBlock";  // Boolean.TRUE
    public static final Object LINK = "rte.link";          // String href

    // Representative FontSize (points) fabricated on read for each heading level, and the write
    // target the bands snap to — the browser-measured RTE render (h1 24 / h2 20 / h3 16 px). Read
    // fabricates it so StyleConstants introspection shows a heading as large text (D_jtextpane/Idea A); the
    // opaque HEADER key stays the authoritative heading carrier, this is the introspection echo.
    private static final int[] HEADING_FONT_SIZE = {0, 24, 20, 16}; // indexed 1..3

    // ==================== serialize: StyledDocument -> HTML ====================

    /**
     * Serializes {@code doc} to RTE-subset HTML — the format pushed to the peer via
     * the main {@code setValue} channel. Pushing HTML (not {@code asDelta().setValue},
     * whose async {@code executeJs("return this.htmlValue")} reconcile races Quill's
     * render to empty during construction — D_jtextpane) keeps the value consistent and never
     * self-clobbers; Karibu can't reproduce that browser race, so this note is the
     * only guard against a "just use asDelta().setValue" simplification.
     */
    public static String toHtml(StyledDocument doc) {
        StringBuilder out = new StringBuilder();
        Element root = doc.getDefaultRootElement();
        int length = doc.getLength();
        String openList = null; // "ol"/"ul" while inside a run of list paragraphs
        for (int pi = 0; pi < root.getElementCount(); pi++) {
            Element para = root.getElement(pi);
            AttributeSet pa = para.getAttributes();

            String list = pa.isDefined(LIST)
                    ? ("ordered".equals(pa.getAttribute(LIST)) ? "ol" : "ul") : null;

            // A paragraph renders as a heading from a browser-origin HEADER key, or — for a
            // Swing-authored plain paragraph — from a uniform heading-band FontSize (D_jtextpane/Idea A:
            // StyleConstants has no heading concept, and RTE has no font-size control, so a large
            // uniform FontSize is the only thing we can render bigger, via <hN>). Compute up front
            // so paragraphHtml suppresses the "FontSize dropped" WARN for a size a heading consumes.
            boolean plainBlock = list == null && !pa.isDefined(HEADER)
                    && !pa.isDefined(BLOCKQUOTE) && !pa.isDefined(CODE_BLOCK);
            int band = plainBlock ? paragraphHeadingBand(para, doc, length) : 0;
            boolean heading = pa.isDefined(HEADER) || band != 0;
            String inner = paragraphHtml(para, doc, length, heading);

            if (list != null) {
                if (!list.equals(openList)) {
                    if (openList != null) out.append("</").append(openList).append('>');
                    out.append('<').append(list).append('>');
                    openList = list;
                }
                warnAlignmentDropped(pa, "li");
                out.append("<li>").append(inner).append("</li>");
                continue;
            }
            if (openList != null) {
                out.append("</").append(openList).append('>');
                openList = null;
            }
            if (pa.isDefined(HEADER)) {
                appendHeading(out, clampHeader(pa), pa, inner);
            } else if (pa.isDefined(BLOCKQUOTE)) {
                out.append("<blockquote").append(alignAttr(pa)).append('>')
                        .append(inner).append("</blockquote>");
            } else if (pa.isDefined(CODE_BLOCK)) {
                warnAlignmentDropped(pa, "pre");
                out.append("<pre>").append(inner).append("</pre>");
            } else if (band != 0) {
                appendHeading(out, band, pa, inner);
            } else {
                out.append("<p").append(alignAttr(pa)).append('>').append(inner).append("</p>");
            }
        }
        if (openList != null) {
            out.append("</").append(openList).append('>');
        }
        return out.toString();
    }

    private static String paragraphHtml(Element para, StyledDocument doc, int length, boolean heading) {
        StringBuilder sb = new StringBuilder();
        for (int ri = 0; ri < para.getElementCount(); ri++) {
            Element run = para.getElement(ri);
            AttributeSet a = run.getAttributes();
            Icon icon = a.isDefined(StyleConstants.IconAttribute) ? StyleConstants.getIcon(a) : null;
            if (icon != null) {
                String dataUrl = encodeImage(icon);
                if (dataUrl != null) {
                    sb.append("<img src=\"").append(escapeAttr(dataUrl)).append("\">");
                }
                continue;
            }
            if (a.isDefined(StyleConstants.ComponentAttribute)) {
                EHelper.onUnimplemented("JTextPane", "insertComponent",
                        "live component has no RTE representation");
                continue;
            }
            int start = run.getStartOffset();
            int end = Math.min(run.getEndOffset(), length);
            String text = stripTrailingNewline(readText(doc, start, end - start));
            if (!text.isEmpty()) {
                sb.append(wrapInline(escapeHtml(text), a, heading));
            }
        }
        return sb.toString();
    }

    /**
     * Maps a run's inline attributes to nested RTE-subset tags. {@code headingConsumesSize}
     * suppresses the FontSize WARN: on a heading paragraph the size is rendered by the {@code <hN>}
     * wrapper (RTE has no font-size control), so it's consumed, not dropped.
     */
    private static String wrapInline(String html, AttributeSet a, boolean headingConsumesSize) {
        String s = html;
        if (StyleConstants.isSubscript(a)) s = "<sub>" + s + "</sub>";
        if (StyleConstants.isSuperscript(a)) s = "<sup>" + s + "</sup>";
        if (StyleConstants.isStrikeThrough(a)) s = "<s>" + s + "</s>";
        if (StyleConstants.isUnderline(a)) s = "<u>" + s + "</u>";
        if (StyleConstants.isItalic(a)) s = "<em>" + s + "</em>";
        if (StyleConstants.isBold(a)) s = "<strong>" + s + "</strong>";
        StringBuilder style = new StringBuilder();
        if (a.isDefined(StyleConstants.Foreground)) {
            style.append("color:").append(rgb(StyleConstants.getForeground(a))).append(';');
        }
        if (a.isDefined(StyleConstants.Background)) {
            style.append("background-color:").append(rgb(StyleConstants.getBackground(a))).append(';');
        }
        if (style.length() > 0) {
            s = "<span style=\"" + style + "\">" + s + "</span>";
        }
        if (a.isDefined(LINK)) {
            s = "<a href=\"" + escapeAttr(String.valueOf(a.getAttribute(LINK))) + "\">" + s + "</a>";
        }
        warnUnsupported(a, StyleConstants.FontFamily, "FontFamily");
        if (!headingConsumesSize) {
            warnUnsupported(a, StyleConstants.FontSize, "FontSize");
        }
        return s;
    }

    private static void appendHeading(StringBuilder out, int level, AttributeSet pa, String inner) {
        out.append("<h").append(level).append(alignAttr(pa)).append('>')
                .append(inner).append("</h").append(level).append('>');
    }

    /** The clamped 1..3 heading level of a HEADER paragraph; WARNs once if out of RTE's range. */
    private static int clampHeader(AttributeSet pa) {
        int h = ((Number) pa.getAttribute(HEADER)).intValue();
        if (h < 1 || h > 3) {
            // RTE/jsoup whitelist only h1..h3 (the HEADER contract). A stray level — reachable via
            // a user-set attribute — would be unwrapped to bare text by the server sanitizer,
            // silently losing the heading. Clamp so it still renders as a heading.
            EHelper.onUnimplemented("JTextPane", "setParagraphAttributes",
                    "heading level " + h + " outside RTE's h1..h3; clamped to h3");
            h = Math.max(1, Math.min(3, h));
        }
        return h;
    }

    /**
     * The heading level (1..3) a plain paragraph maps to when all its visible runs share a FontSize
     * in one heading band, else 0 (Idea A push). Bands from the browser-measured RTE render
     * (h1 24px / h2 20px / h3 16px / normal 12px): {@code >=22 -> h1}, {@code 18..21 -> h2},
     * {@code 14..17 -> h3}, below that no heading. Whitespace-only and icon/component runs are
     * neutral (skipped) — notably the trailing {@code '\n'} run a migrator's
     * {@code setCharacterAttributes} over the visible text usually doesn't cover.
     */
    private static int paragraphHeadingBand(Element para, StyledDocument doc, int length) {
        int band = 0;
        for (int ri = 0; ri < para.getElementCount(); ri++) {
            Element run = para.getElement(ri);
            AttributeSet a = run.getAttributes();
            if (a.isDefined(StyleConstants.IconAttribute) || a.isDefined(StyleConstants.ComponentAttribute)) {
                continue; // neutral
            }
            int start = run.getStartOffset();
            int end = Math.min(run.getEndOffset(), length);
            if (stripTrailingNewline(readText(doc, start, end - start)).isBlank()) {
                continue; // neutral: whitespace-only (incl. the trailing newline run)
            }
            if (!a.isDefined(StyleConstants.FontSize)) {
                return 0; // a visible run with no size — not a uniform heading
            }
            int b = headingForFontSize(StyleConstants.getFontSize(a));
            if (b == 0) {
                return 0; // sub-threshold size — plain paragraph (dropped + WARNed as usual)
            }
            if (band == 0) {
                band = b;
            } else if (band != b) {
                return 0; // mixed heading bands — not a single heading
            }
        }
        return band;
    }

    private static int headingForFontSize(int size) {
        if (size >= 22) return 1;
        if (size >= 18) return 2;
        if (size >= 14) return 3;
        return 0;
    }

    private static String alignAttr(AttributeSet a) {
        if (a.isDefined(StyleConstants.Alignment)) {
            switch (StyleConstants.getAlignment(a)) {
                case StyleConstants.ALIGN_CENTER -> { return " style=\"text-align: center\""; }
                case StyleConstants.ALIGN_RIGHT -> { return " style=\"text-align: right\""; }
                case StyleConstants.ALIGN_JUSTIFIED -> { return " style=\"text-align: justify\""; }
                default -> { /* left — default */ }
            }
        }
        return "";
    }

    /**
     * WARNs that a non-left paragraph alignment is dropped on the {@code <pre>} / {@code <li>}
     * branches. RTE carries {@code align} in its Delta for code-block/list-item but
     * {@code getSemanticHTML()} never emits the {@code ql-align} class there, so the value can't
     * survive the round-trip (blocked upstream, D_gap_severity_triage sub-bucket (a) —
     * <a href="https://github.com/vaadin/web-components/issues/12188">vaadin/web-components#12188</a>).
     * Emitting {@code text-align} anyway would render nothing and break push↔read idempotency, so
     * we drop it and WARN.
     */
    private static void warnAlignmentDropped(AttributeSet a, String block) {
        if (a.isDefined(StyleConstants.Alignment)
                && StyleConstants.getAlignment(a) != StyleConstants.ALIGN_LEFT) {
            EHelper.onUnimplemented("JTextPane", "setParagraphAttributes",
                    "alignment on <" + block + "> dropped — RTE does not persist text-align there");
        }
    }

    private static void warnUnsupported(AttributeSet a, Object key, String name) {
        if (a.isDefined(key)) {
            EHelper.onUnimplemented("JTextPane", "styled attribute (no RTE counterpart)",
                    name + " dropped");
        }
    }

    private static String rgb(Color c) {
        return "rgb(" + c.getRed() + ", " + c.getGreen() + ", " + c.getBlue() + ")";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escapeHtml(s).replace("\"", "&quot;");
    }

    // ==================== parse: HTML -> StyledDocument ====================

    /**
     * Replaces {@code doc}'s content with the RTE-subset HTML parsed from a browser
     * edit (clear + re-insert) — the inverse of {@link #toHtml}. One block element
     * becomes one paragraph; inline tags accumulate into run attributes <b>regardless
     * of nesting order</b> ({@code <s><u>} ≡ {@code <u><s>}), and colour is read from
     * either a {@code <span style="color:…">} or a {@code style="color:…"} on the
     * inline element itself — Quill attaches it to the format element, e.g.
     * {@code <strong style="color: rgb(…)">x</strong>} (empirically pinned against the
     * live component). {@code rel}/{@code target}/{@code spellcheck} and unknown tags
     * are ignored (text preserved). Malformed input is swallowed + WARNed (D_never_fail_on_gaps: reads
     * never fail).
     *
     * <p>The caller guards the {@code peer}-echo with {@code preventPeerEvents}.
     */
    public static void fromHtml(String html, StyledDocument doc) {
        try {
            doc.remove(0, doc.getLength());
            org.jsoup.nodes.Document parsed = Jsoup.parseBodyFragment(html == null ? "" : html);
            List<Block> blocks = new ArrayList<>();
            collectBlocks(parsed.body(), blocks);

            int pos = 0;
            int[] starts = new int[blocks.size()];
            for (int bi = 0; bi < blocks.size(); bi++) {
                starts[bi] = pos;
                for (Run r : blocks.get(bi).runs) {
                    doc.insertString(pos, r.text(), r.attrs());
                    pos += r.text().length();
                }
                if (bi < blocks.size() - 1) {
                    doc.insertString(pos, "\n", null); // paragraph terminator; last one is implicit
                    pos += 1;
                }
            }
            for (int bi = 0; bi < blocks.size(); bi++) {
                if (starts[bi] < doc.getLength()) {
                    // replace=true makes each paragraph's attributes EXACTLY the parsed
                    // block attrs (empty when none). Not replace=false: doc.remove leaves
                    // a paragraph carrying the previous document's attributes, and the
                    // newline-split during rebuild propagates them, so an aligned
                    // paragraph would bleed its alignment into the next one.
                    AttributeSet ba = blocks.get(bi).blockAttrs;
                    doc.setParagraphAttributes(starts[bi], 1, ba != null ? ba : SimpleAttributeSet.EMPTY, true);
                }
            }
        } catch (BadLocationException | RuntimeException e) {
            EHelper.onUnsupported("JTextPane", "fromHtml", e);
        }
    }

    private record Run(String text, AttributeSet attrs) {
    }

    /** One paragraph: its block attributes plus the inline runs that fill it. */
    private static final class Block {
        AttributeSet blockAttrs;
        final List<Run> runs = new ArrayList<>();
    }

    private static void collectBlocks(org.jsoup.nodes.Element body, List<Block> blocks) {
        for (org.jsoup.nodes.Node node : body.childNodes()) {
            if (node instanceof org.jsoup.nodes.Element el) {
                switch (el.normalName()) {
                    case "ol", "ul" -> {
                        String kind = el.normalName().equals("ol") ? "ordered" : "bullet";
                        for (org.jsoup.nodes.Element li : el.children()) {
                            if (li.normalName().equals("li")) {
                                blocks.add(buildParagraph(li, listAttr(kind)));
                            }
                        }
                    }
                    case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        int level = el.normalName().charAt(1) - '0';
                        blocks.add(buildParagraph(el, withAlign(headerAttr(level), el), fontSizeRun(level)));
                    }
                    case "blockquote" -> blocks.add(buildParagraph(el, withAlign(flag(BLOCKQUOTE), el)));
                    case "pre" -> blocks.add(buildParagraph(el, flag(CODE_BLOCK)));
                    case "p", "div" -> blocks.add(buildParagraph(el, alignFrom(el)));
                    default -> blocks.add(buildParagraph(el, null)); // lenient: unknown block → plain paragraph
                }
            } else if (node instanceof org.jsoup.nodes.TextNode tn && !tn.isBlank()) {
                Block b = new Block(); // stray body-level text → its own paragraph
                b.runs.add(new Run(tn.getWholeText(), SimpleAttributeSet.EMPTY));
                blocks.add(b);
            }
        }
    }

    private static Block buildParagraph(org.jsoup.nodes.Element blockEl, AttributeSet blockAttrs) {
        return buildParagraph(blockEl, blockAttrs, SimpleAttributeSet.EMPTY);
    }

    /**
     * Builds one paragraph. {@code baseRunAttrs} seeds every run's attributes — used to fabricate
     * the representative FontSize on heading runs (Idea A); {@code SimpleAttributeSet.EMPTY} for
     * every other block.
     */
    private static Block buildParagraph(org.jsoup.nodes.Element blockEl, AttributeSet blockAttrs,
                                        AttributeSet baseRunAttrs) {
        Block b = new Block();
        b.blockAttrs = blockAttrs;
        walkInline(blockEl, baseRunAttrs, b);
        stripTrailingNewlineRun(b); // Quill leaves a "\n" inside <pre>; a raw '\n' would split the paragraph
        return b;
    }

    /** Char attributes carrying the representative FontSize for a heading level (clamped 1..3). */
    private static AttributeSet fontSizeRun(int level) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontSize(a, HEADING_FONT_SIZE[Math.max(1, Math.min(3, level))]);
        return a;
    }

    private static void walkInline(org.jsoup.nodes.Node parent, AttributeSet acc, Block block) {
        for (org.jsoup.nodes.Node child : parent.childNodes()) {
            if (child instanceof org.jsoup.nodes.TextNode tn) {
                String t = tn.getWholeText();
                if (!t.isEmpty()) {
                    block.runs.add(new Run(t, acc));
                }
            } else if (child instanceof org.jsoup.nodes.Element el) {
                if (el.normalName().equals("img")) {
                    ImageIcon icon = decodeImage(el.attr("src"));
                    if (icon != null) {
                        SimpleAttributeSet ia = new SimpleAttributeSet(acc);
                        StyleConstants.setIcon(ia, icon);
                        block.runs.add(new Run(" ", ia)); // one-char placeholder, matches toHtml/insertIcon
                    }
                    continue; // void element — no children to recurse
                }
                SimpleAttributeSet next = new SimpleAttributeSet(acc);
                applyInlineTag(el, next);
                applyInlineStyle(el, next);
                walkInline(el, next, block);
            }
        }
    }

    private static void applyInlineTag(org.jsoup.nodes.Element el, SimpleAttributeSet a) {
        switch (el.normalName()) {
            case "strong", "b" -> StyleConstants.setBold(a, true);
            case "em", "i" -> StyleConstants.setItalic(a, true);
            case "u" -> StyleConstants.setUnderline(a, true);
            case "s", "strike", "del" -> StyleConstants.setStrikeThrough(a, true);
            case "sub" -> StyleConstants.setSubscript(a, true);
            case "sup" -> StyleConstants.setSuperscript(a, true);
            case "a" -> a.addAttribute(LINK, el.attr("href")); // rel/target ignored
            default -> { /* span carries only style; unknown inline: keep text, drop tag */ }
        }
    }

    /** Reads {@code color} / {@code background-color} off an element's {@code style} — on any inline element, since Quill moves colour onto the format tag. */
    private static void applyInlineStyle(org.jsoup.nodes.Element el, SimpleAttributeSet a) {
        for (String[] d : cssDecls(el.attr("style"))) {
            if (d[0].equals("color")) {
                Color c = parseColor(d[1]);
                if (c != null) StyleConstants.setForeground(a, c);
            } else if (d[0].equals("background-color")) {
                Color c = parseColor(d[1]);
                if (c != null) StyleConstants.setBackground(a, c);
            }
            // text-align is block-level (alignFrom), never an inline run attribute
        }
    }

    /**
     * Overlays {@code el}'s inline {@code text-align} (if any) onto {@code base}. Lets a heading or
     * blockquote carry alignment too — RTE serializes {@code text-align} as inline style on
     * {@code <p>}/{@code <h1..3>}/{@code <blockquote>} (empirically pinned 2026-07-20), so alignment
     * is read the same way on all three, not just plain paragraphs.
     */
    private static AttributeSet withAlign(AttributeSet base, org.jsoup.nodes.Element el) {
        AttributeSet align = alignFrom(el);
        if (align == null) {
            return base;
        }
        SimpleAttributeSet merged = base == null ? new SimpleAttributeSet() : new SimpleAttributeSet(base);
        merged.addAttributes(align);
        return merged;
    }

    private static AttributeSet alignFrom(org.jsoup.nodes.Element p) {
        for (String[] d : cssDecls(p.attr("style"))) {
            if (d[0].equals("text-align")) {
                SimpleAttributeSet a = new SimpleAttributeSet();
                switch (d[1]) {
                    case "center" -> StyleConstants.setAlignment(a, StyleConstants.ALIGN_CENTER);
                    case "right" -> StyleConstants.setAlignment(a, StyleConstants.ALIGN_RIGHT);
                    case "justify" -> StyleConstants.setAlignment(a, StyleConstants.ALIGN_JUSTIFIED);
                    default -> { return null; } // left is the default — no attribute
                }
                return a;
            }
        }
        return null;
    }

    /** Splits a CSS {@code style} value into {@code [property-lowercased, value-lowercased]} pairs. */
    private static List<String[]> cssDecls(String style) {
        List<String[]> out = new ArrayList<>();
        for (String decl : style.split(";")) {
            int c = decl.indexOf(':');
            if (c < 0) {
                continue;
            }
            out.add(new String[]{
                    decl.substring(0, c).trim().toLowerCase(Locale.ROOT),
                    decl.substring(c + 1).trim().toLowerCase(Locale.ROOT)});
        }
        return out;
    }

    private static AttributeSet headerAttr(int level) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(HEADER, Math.max(1, Math.min(3, level))); // RTE renders only h1..h3
        return a;
    }

    private static AttributeSet listAttr(String kind) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(LIST, kind);
        return a;
    }

    private static AttributeSet flag(Object key) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(key, Boolean.TRUE);
        return a;
    }

    private static void stripTrailingNewlineRun(Block b) {
        if (b.runs.isEmpty()) {
            return;
        }
        int i = b.runs.size() - 1;
        Run last = b.runs.get(i);
        if (last.text().endsWith("\n")) {
            String t = last.text().substring(0, last.text().length() - 1);
            if (t.isEmpty()) {
                b.runs.remove(i);
            } else {
                b.runs.set(i, new Run(t, last.attrs()));
            }
        }
    }

    // ==================== helpers ====================

    private static String readText(StyledDocument doc, int offset, int len) {
        if (len <= 0) {
            return "";
        }
        try {
            return doc.getText(offset, len);
        } catch (BadLocationException e) {
            return "";
        }
    }

    private static String stripTrailingNewline(String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    /** Parses {@code #rrggbb}, {@code #rgb}, or {@code rgb(r,g,b)}; null on failure. */
    private static Color parseColor(String s) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) {
                t = t.substring(1);
                if (t.length() == 3) {
                    t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
                }
                int rgb = Integer.parseInt(t, 16);
                return new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
            }
            if (t.startsWith("rgb")) {
                String inner = t.substring(t.indexOf('(') + 1, t.indexOf(')'));
                String[] parts = inner.split(",");
                return new Color(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()));
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return null;
    }

    /** Rasterises an {@code ImageIcon} to a PNG {@code data:} URL; null (+WARN) otherwise. */
    private static String encodeImage(Icon icon) {
        if (!(icon instanceof ImageIcon ii)) {
            EHelper.onUnimplemented("JTextPane", "insertIcon",
                    "only ImageIcon is supported; a custom Icon needs Graphics paint (OOS)");
            return null;
        }
        try {
            Image img = ii.getImage();
            int w = Math.max(1, ii.getIconWidth());
            int h = Math.max(1, ii.getIconHeight());
            BufferedImage bi;
            if (img instanceof BufferedImage b) {
                bi = b;
            } else {
                bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bi.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            EHelper.onUnsupported("JTextPane", "insertIcon", e);
            return null;
        }
    }

    /** Decodes a {@code data:...;base64,...} image URL to an {@code ImageIcon}; null on failure. */
    private static ImageIcon decodeImage(String dataUrl) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0 || !dataUrl.regionMatches(true, 0, "data:", 0, 5)) {
                return null; // only data: URLs (RTE never emits web URLs)
            }
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            BufferedImage bi = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            return bi == null ? null : new ImageIcon(bi);
        } catch (Exception e) { // IOException from ImageIO.read + any decode failure
            return null;
        }
    }
}
