/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.text.html.HTMLDocument
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.text.html;

// Hand-finished emulator. Two JDK facts that shaped this class and are invisible
// from the code (both measured, headless JDK 25):
//
//  1. kit.read() into a document that still holds content throws
//     RuntimeException("Must insert new content into body element-"). That reads
//     like "swap in a fresh document on every change" — which would silently drop
//     user DocumentListener registrations. Clearing first (what Swing's own
//     setText does) makes re-reads work on ONE stable instance. Don't "simplify"
//     reparse() into a fresh-instance swap.
//  2. getIterator(HTML.Tag) returns null for BLOCK tags (p, li, h2) and an
//     iterator only for leaf tags (a, img, b). Inherited verbatim; not a bug to fix.

/**
 * Emulator for {@link javax.swing.text.html.HTMLDocument} — the read-only element
 * model behind an HTML {@link vaadinx.swing.JEditorPane}:
 *
 * <pre>{@code
 * pane.setContentType("text/html");
 * pane.setText("<h2>Report</h2><p id=\"total\">42</p>");
 *
 * HTMLDocument doc = (HTMLDocument) pane.getDocument();   // the JDK type
 * Element total = doc.getElement("total");
 * Element root  = doc.getDefaultRootElement();
 * }</pre>
 *
 * Extends the JDK class (D_htmldocument) rather than porting it, which is what makes that
 * cast compile — {@code javax.swing.text.html.HTMLDocument} stays JDK on the
 * import swap — and what makes the element tree the JDK parser's own work rather
 * than emulation.
 *
 * <h2>Read-only: a projection of the pane's markup</h2>
 *
 * The pane owns the markup; this model is reparsed from it on every change
 * ({@code setText}, {@code setPage}, a browser edit). So {@code getText(0,
 * getLength())} here is the <em>rendered</em> text, as on a real
 * {@code HTMLDocument}, while the pane's {@code getText()} keeps returning markup.
 *
 * <p>The six HTML mutators ({@link #setInnerHTML}, {@link #setOuterHTML},
 * {@link #insertAfterStart}, {@link #insertBeforeEnd}, {@link #insertBeforeStart},
 * {@link #insertAfterEnd}) WARN and change nothing: writing through them would mean
 * serializing this model back to RTE-subset HTML. A console or transcript pane that
 * appends through them rewrites to {@code pane.setText(pane.getText() + html)}.
 *
 * <p>{@link #setBase} / {@link #getBase} round-trip but redirect nothing — links
 * resolve against the pane's loaded page (SD_hyperlink_listener) and RTE takes only base64
 * data-URL images (SD_sjeditorpane_rte).
 *
 * <p><b>{@code getElement(id)} serves server-authored markup only.</b> The editor
 * does not carry {@code id} attributes back out, so once content has round-tripped
 * through a browser edit the ids are gone and the lookup answers {@code null}. What
 * {@code setText} / {@code setPage} put in is fully addressable; what the user
 * typed over is not.
 *
 * <p>Reparsing is eager, skipped when the markup is unchanged, so the model
 * is never stale and {@code DocumentListener}s fire at the change rather than at
 * the next read. Warm cost is ~0.1 ms for a small pane, ~1.9 ms for a
 * 200-paragraph document; a lazy dirty-flag refresh would fit behind
 * {@code _setMarkup} if that ever bites. A document user code builds itself —
 * {@code new javax.swing.text.html.HTMLDocument()}, {@code kit.createDefaultDocument()}
 * — is untouched JDK behaviour, mutators included; this class is only what a
 * pane hands out.
 */
public class HTMLDocument extends javax.swing.text.html.HTMLDocument {

    // Stateless across calls (each read builds its own HTMLReader) and the parser's
    // DTD is cached statically by the JDK, so one shared kit avoids re-warming it.
    private static final javax.swing.text.html.HTMLEditorKit PARSER_KIT =
            new javax.swing.text.html.HTMLEditorKit();

    private String markup = "";
    private boolean reparsing;

    public HTMLDocument() {
        super();
    }

    public HTMLDocument(javax.swing.text.html.StyleSheet styles) {
        super(styles);
    }

    /**
     * Framework-internal (the {@code _} prefix marks it, as on
     * {@link HTMLEditorKit#_bindTo}): install the pane's markup and rebuild the
     * element model from it.
     *
     * @param html the pane's markup; {@code null} empties the model
     */
    public void _setMarkup(java.lang.String html) {
        java.lang.String next = html == null ? "" : html;
        if (next.equals(markup)) {
            return;
        }
        markup = next;
        reparse();
    }

    /** @return the markup this model was built from, verbatim */
    public java.lang.String _getMarkup() {
        return markup;
    }

    private void reparse() {
        // Reentrancy is real, not defensive: the JDK's reader mutates this document.
        if (reparsing) {
            return;
        }
        reparsing = true;
        try {
            if (getLength() > 0) {
                remove(0, getLength());   // see file header — not optional
            }
            if (!markup.isEmpty()) {
                PARSER_KIT.read(new java.io.StringReader(markup), this, 0);
            }
        } catch (javax.swing.text.BadLocationException | java.io.IOException | RuntimeException e) {
            // WARN rather than propagate: the markup is intact either way (it is the
            // pane's getText()), so failing the caller's setText over a broken
            // projection would be the worse trade.
            vaadinx.EHelper.onUnsupported("HTMLDocument", "reparse", e);
        } finally {
            reparsing = false;
        }
    }

    // ---- mutators: read-only model, so each WARNs and changes nothing ----------
    //
    // Dropping the JDK's checked exceptions from the signatures is deliberate —
    // migrated call sites keep their try/catch either way.

    @Override
    public void setInnerHTML(javax.swing.text.Element elem, java.lang.String htmlText) {
        vaadinx.EHelper.onUnimplemented("HTMLDocument", "setInnerHTML", elem, htmlText);
    }

    @Override
    public void setOuterHTML(javax.swing.text.Element elem, java.lang.String htmlText) {
        vaadinx.EHelper.onUnimplemented("HTMLDocument", "setOuterHTML", elem, htmlText);
    }

    @Override
    public void insertAfterStart(javax.swing.text.Element elem, java.lang.String htmlText) {
        vaadinx.EHelper.onUnimplemented("HTMLDocument", "insertAfterStart", elem, htmlText);
    }

    @Override
    public void insertBeforeEnd(javax.swing.text.Element elem, java.lang.String htmlText) {
        vaadinx.EHelper.onUnimplemented("HTMLDocument", "insertBeforeEnd", elem, htmlText);
    }

    @Override
    public void insertBeforeStart(javax.swing.text.Element elem, java.lang.String htmlText) {
        vaadinx.EHelper.onUnimplemented("HTMLDocument", "insertBeforeStart", elem, htmlText);
    }

    @Override
    public void insertAfterEnd(javax.swing.text.Element elem, java.lang.String htmlText) {
        vaadinx.EHelper.onUnimplemented("HTMLDocument", "insertAfterEnd", elem, htmlText);
    }
}
