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
 * This file is derived from OpenJDK's javax.swing.text.html.HTMLEditorKit
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.text.html;

// Hand-finished emulator. The design choice worth knowing before editing: this
// class EXTENDS javax.swing.text.html.HTMLEditorKit instead of reimplementing it
// (D_htmleditorkit). That single decision buys three things a from-scratch port would each
// have had to solve:
//
//  1. It is-a javax EditorKit, so JEditorPane.setEditorKit(EditorKit) keeps the
//     JDK signature and migrator code compiles with no cast.
//  2. The nested types come along by inheritance — HTMLFactory (which the
//     render-only custom-ViewFactory idiom subclasses) and ParserCallback /
//     Parser (which headless HTML-scraping code passes to the JDK's
//     ParserDelegator). Because they ARE the JDK's types, a rewritten
//     `vaadinx...HTMLEditorKit.ParserCallback` is still accepted by
//     `javax...ParserDelegator.parse(...)`.
//  3. Anything we have not touched degrades to JDK behaviour rather than to a
//     stub, which for a kit whose rendering half we never drive is the safer
//     default.
//
// What we override is exactly the part that has to reach the Vaadin peer:
// getStyleSheet/setStyleSheet, plus the bind that hands the sheet its pane.

/**
 * Emulator for {@link javax.swing.text.html.HTMLEditorKit} — the carrier for
 * author CSS on an HTML {@code JEditorPane}. Supports the field's dominant
 * render-only idiom:
 *
 * <pre>{@code
 * JEditorPane pane = new JEditorPane();
 * HTMLEditorKit kit = new HTMLEditorKit();
 * pane.setEditorKit(kit);
 * kit.getStyleSheet().addRule("body { font-family: sans-serif; }");
 * pane.setEditable(false);
 * pane.setText("<h2>Report</h2><ul><li>one</li></ul>");
 * }</pre>
 *
 * <h2>Scope: the stylesheet, not the rendering pipeline</h2>
 *
 * The kit's job here is to carry a {@link StyleSheet} to the pane. It does
 * <em>not</em> drive rendering: Vaadin's {@code RichTextEditor} renders the pane's
 * HTML, and the JDK's rendering path is built on {@code View.paint(Graphics, …)},
 * permanently out of scope per R_match_swing_errors sub-bucket (b). So {@link #getViewFactory()}
 * answers with a real factory (inherited, so subclassing it compiles) but nothing
 * consults it — a custom {@code ViewFactory} is inert. That costs migrated code
 * nothing in the one idiom that actually appears in the field: a factory that
 * refuses {@code <img>} views so a report pane never hits the network, which is
 * already our behaviour since images outside base64 data-URLs are dropped anyway.
 *
 * <h2>Headless parsing needs nothing from us</h2>
 *
 * Code that uses this class purely as an HTML scraper — a {@code ParserCallback}
 * handed to {@code javax.swing.text.html.parser.ParserDelegator}, no pane in sight
 * — keeps working after an import swap, because the nested callback types are the
 * JDK's own by inheritance. The one seam that does not survive is subclassing
 * {@code Parser} itself: an override written as {@code parse(Reader, ParserCallback,
 * boolean)} resolves the parameter to this class's inherited nested type, which is
 * the JDK type, so it compiles — but a hand-written signature naming a
 * {@code vaadinx} callback type would not. {@code ParserDelegator} is the
 * documented way in, so this is a corner rather than a road.
 *
 * <h2>What drops-and-WARNs</h2>
 *
 * {@link #insertHTML} — splicing markup into an {@code HTMLDocument} needs the
 * element model that is not built (there is no {@code HTMLDocument} behind the
 * pane; the emulator keeps a {@code PlainDocument}). {@code read} / {@code write}
 * likewise: the pane's own {@code setText} / {@code getText} are the supported way
 * in and out.
 */
public class HTMLEditorKit extends javax.swing.text.html.HTMLEditorKit {

    // Per-kit sheet, NOT the JDK's shared static one — see StyleSheet's javadoc
    // for why that divergence is deliberate.
    private StyleSheet styleSheet = new StyleSheet();

    /**
     * This kit's own stylesheet. Unlike the JDK, the sheet is not shared with other
     * kits — see {@link StyleSheet} for the rationale.
     */
    @Override
    public StyleSheet getStyleSheet() {
        return styleSheet;
    }

    /**
     * Install a stylesheet. A {@code vaadinx} sheet takes effect; a JDK-typed sheet
     * has no way to reach the peer and WARNs, leaving the current one in place —
     * failing to style is better than silently accepting a sheet that can never
     * apply.
     */
    @Override
    public void setStyleSheet(javax.swing.text.html.StyleSheet s) {
        if (s instanceof StyleSheet ours) {
            this.styleSheet = ours;
            for (com.vaadin.swingbridge.surrogates.SJEditorPane peer : boundPeers) {
                ours.bindTo(peer);
            }
        } else if (s != null) {
            vaadinx.EHelper.onUnimplemented("HTMLEditorKit", "setStyleSheet", s);
        }
    }

    private final java.util.List<com.vaadin.swingbridge.surrogates.SJEditorPane> boundPeers =
            new java.util.ArrayList<>();

    /**
     * Framework-internal (the {@code _} prefix marks it, as on
     * {@code _installSwingClass}): bind this kit's stylesheet to a pane's peer.
     * Called by {@code JEditorPane.setEditorKit} from a sibling package; the peer is
     * remembered so a later {@link #setStyleSheet} reaches it too. Not part of the
     * emulated JDK surface — migrated code has no reason to call it.
     */
    public void _bindTo(com.vaadin.swingbridge.surrogates.SJEditorPane peer) {
        if (peer == null || boundPeers.contains(peer)) return;
        boundPeers.add(peer);
        styleSheet.bindTo(peer);
    }

    @Override
    public void insertHTML(javax.swing.text.html.HTMLDocument doc, int offset, String html,
                           int popDepth, int pushDepth, javax.swing.text.html.HTML.Tag insertTag) {
        vaadinx.EHelper.onUnimplemented("HTMLEditorKit", "insertHTML", offset, html);
    }
}
