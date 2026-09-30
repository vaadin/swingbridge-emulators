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
 * This file is derived from OpenJDK's javax.swing.text.html.StyleSheet
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.text.html;

// Hand-finished emulator. Extends the JDK StyleSheet rather than reimplementing
// it: the JDK class references nothing in the Component hierarchy, so extending
// keeps migrator code that passes a StyleSheet around (or assigns it to a
// javax-typed field) compiling unchanged, while addRule gets a behaviour hook.
// See D_htmleditorkit for the wider "extend, don't port" decision on this package.

/**
 * Emulator for {@link javax.swing.text.html.StyleSheet} — carries author CSS from
 * {@code HTMLEditorKit} onto the pane's rendered content.
 *
 * <p>{@link #addRule} is the load-bearing method; it is the second-most-used
 * member of the whole {@code javax.swing.text.html} surface in the field, almost
 * always in this shape:
 *
 * <pre>{@code
 * HTMLEditorKit kit = new HTMLEditorKit();
 * pane.setEditorKit(kit);
 * kit.getStyleSheet().addRule("body { font-family: sans-serif; }");
 * }</pre>
 *
 * <p><b>Order does not matter.</b> Rules added before the sheet is bound to a pane
 * (the shape above adds them after {@code setEditorKit}, but the reverse is just as
 * common) are held and flushed on bind, and the peer holds them again until it is
 * attached to the DOM.
 *
 * <h2>Appearance only</h2>
 *
 * A rule reaches the real {@code <h2>} / {@code <p>} / {@code <a>} elements RTE
 * renders, so tag selectors translate close to 1:1. What no rule can restore is
 * structure Quill's model refuses — tables above all — because that is dropped as
 * the value enters the editor, before styling could apply (SD_sjeditorpane_rte).
 *
 * <h2>Deliberate divergence: the sheet is per-kit, not shared</h2>
 *
 * The JDK's {@code HTMLEditorKit.getStyleSheet()} returns a <em>static</em> sheet
 * shared by every kit in the JVM, so one {@code addRule} silently restyles every
 * HTML pane in the app. We give each kit its own sheet: the sharing is a
 * long-standing footgun rather than intent, per-pane styling is what the calling
 * code means, and shadow-root injection is per-instance anyway. Sharing one kit
 * across several panes still styles all of them (R_best_effort_behaviour).
 *
 * <h2>What is not reproduced (R_vaadin_first)</h2>
 *
 * The JDK-side rule model is not populated — {@code getRule}, {@code getDeclaration}
 * and the {@code AttributeSet} views inherited from the JDK class read empty. The
 * rule text is handed to the peer instead of parsed into a shadow model, per R_vaadin_first's
 * "don't fabricate a store so a setter round-trips through its own getter";
 * {@link #getRules()} exposes what was added for anyone who needs to see it.
 * At-rules ({@code @media} and friends) are skipped with a WARN — the rest of the
 * sheet still applies.
 */
public class StyleSheet extends javax.swing.text.html.StyleSheet {

    // Rules added before a peer exists. Kept so the common
    // "new kit → addRule → setEditorKit" order works as well as the reverse.
    private final java.util.List<String> pending = new java.util.ArrayList<>();

    // The panes this sheet styles. A list rather than a single field because one
    // kit legitimately installs on several panes.
    private final java.util.List<com.vaadin.swingbridge.surrogates.SJEditorPane> peers =
            new java.util.ArrayList<>();

    /**
     * Apply a CSS rule to every bound pane's content, and to any pane bound later.
     * Accepts a single rule or a whole stylesheet's worth, as the JDK does.
     */
    @Override
    public void addRule(String rule) {
        if (rule == null || rule.isBlank()) return;
        pending.add(rule);
        for (com.vaadin.swingbridge.surrogates.SJEditorPane peer : peers) {
            peer.addCssRule(rule);
        }
    }

    /** The rule texts passed to {@link #addRule}, in order. */
    public java.util.List<String> getRules() {
        return java.util.List.copyOf(pending);
    }

    /**
     * Bind a pane and flush every rule added so far into it. Called when the kit is
     * installed on a pane; re-binding the same pane is a no-op so a repeated
     * {@code setEditorKit} does not double-apply.
     */
    void bindTo(com.vaadin.swingbridge.surrogates.SJEditorPane peer) {
        if (peer == null || peers.contains(peer)) return;
        peers.add(peer);
        for (String rule : pending) {
            peer.addCssRule(rule);
        }
    }
}
