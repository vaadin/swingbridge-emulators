/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JTextPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.swingbridge.surrogates.SJEditorPane;
import vaadinx.swing.text.RteHtmlCodec;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;

// Hand-finished emulator (D_jtextpane). JTextPane is a thin subclass of JEditorPane that
// reuses the SJEditorPane (RichTextEditor) peer for its editable surface, and adds
// a faithful — within the RTE subset — StyledDocument surface. The bridge is
// RteHtmlCodec: a DefaultStyledDocument is the Swing-side source of truth, and its
// run structure maps onto RTE's HTML value (inline char attrs → inline tags, block
// paragraph attrs → block tags, image embeds → <img>) in both directions — push via
// toHtml, read edits back via fromHtml. See RteHtmlCodec and D_jtextpane for the mapping.

/**
 * Emulator for {@link javax.swing.JTextPane} — a thin subclass of {@link JEditorPane}
 * over the same {@link SJEditorPane} (RTE) peer, backed by a
 * {@link DefaultStyledDocument} whose styled runs render through RTE's <b>HTML</b>
 * value (D_jtextpane).
 *
 * <pre>{@code
 * JTextPane pane = new JTextPane();
 * StyledDocument doc = pane.getStyledDocument();
 * SimpleAttributeSet red = new SimpleAttributeSet();
 * StyleConstants.setForeground(red, Color.RED);
 * StyleConstants.setBold(red, true);
 * doc.insertString(0, "alert", red);   // renders bold red in the browser
 * }</pre>
 *
 * <h2>What renders (the RTE subset)</h2>
 *
 * Bold, italic, underline, strike-through, foreground/background colour,
 * super/subscript, paragraph alignment, and {@code ImageIcon} embedding all map to
 * HTML and render. Anything RTE can't represent (font family/size, indents, line
 * spacing, {@code insertComponent}, a non-{@code ImageIcon} icon) WARNs per call
 * and is dropped — see {@link RteHtmlCodec}.
 *
 * <h2>{@code getText()} is plain text</h2>
 *
 * Unlike {@link JEditorPane} (whose {@code getText()} is HTML), a JTextPane's
 * document holds the <em>visible</em> text with styling carried on run attributes,
 * so {@code getText()} returns plain text — matching real {@code JTextPane}. A
 * consequence: {@code setText("<b>hi</b>")} shows the <em>literal</em> string, as a
 * plain-StyledEditorKit JTextPane would, not bold.
 *
 * <h2>Two-way sync</h2>
 *
 * A {@link DocumentListener} serializes the document → RTE-subset <b>HTML</b> and
 * pushes it through the peer's main value on every mutation (so
 * {@code doc.insertString}/{@code setCharacterAttributes} render). Browser edits
 * fire the peer's value-change; the reverse reads that same <b>HTML</b> value back
 * ({@code getValue()}) and rebuilds the document (clear + re-insert) so
 * {@code getText()}/{@code getStyledDocument()} reflect them and user
 * {@code DocumentListener}s fire (R_swing_is_truth/R_callswing_envelope). {@code preventPeerEvents} guards the loop.
 * One value format both ways: reading {@code getValue()} — the property the change
 * event fired on — avoids the cross-property staleness of reading {@code asDelta()}
 * (a different property that can lag the {@code htmlValue} edit), and push stays HTML
 * because {@code asDelta().setValue}'s async {@code htmlValue} reconcile races Quill
 * to empty during construction — see {@link RteHtmlCodec#toHtml}.
 *
 * <h2>Selection-based styled methods</h2>
 *
 * The selection-based {@link #setCharacterAttributes}/{@link #getCharacterAttributes}
 * operate on the pane's <em>input attributes</em> whatever the caret selects (the
 * JDK's no-selection behaviour); programmatic
 * styling that actually renders goes through the offset-based
 * {@code getStyledDocument().setCharacterAttributes(offset, len, …)} path.
 *
 * <h2>R_leaf_peer_lockdown: leaf lock-down</h2>
 *
 * No public {@code javax.swing.*} class extends JTextPane, so it is a leaf: it omits
 * the protected {@code (Component peer)} ctor and hard-codes {@code new SJEditorPane()}
 * through {@link JEditorPane}'s protected D_peer_ctor_injection seam. Non-final so behaviour-subclassing
 * still works.
 */
public class JTextPane extends JEditorPane {

    // We drive no EditorKit rendering pipeline (R_match_swing_errors(b)), so this
    // kit's one role is the one it has on the desktop too: owning the pending-input
    // attributes getInputAttributes() hands out. Declared EditorKit rather than
    // StyledEditorKit so an override returning the wrong kind fails where the JDK's
    // does — in getStyledEditorKit(), not at construction.
    private final javax.swing.text.EditorKit defaultEditorKit;

    // Render-on-mutation: serialize the document to HTML and push it to the peer on
    // every change. insert/remove AND changedUpdate (attribute-only edits, i.e.
    // setCharacterAttributes) all re-render — the base JTextComponent listener
    // deliberately ignores changedUpdate, so JTextPane needs its own. Guarded by
    // preventPeerEvents so a browser-edit rebuild doesn't echo back.
    private final DocumentListener renderPush = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { pushHtmlToPeer(); }
        @Override public void removeUpdate(DocumentEvent e) { pushHtmlToPeer(); }
        @Override public void changedUpdate(DocumentEvent e) { pushHtmlToPeer(); }
    };

    public JTextPane() {
        super(new SJEditorPane());
        // The JDK's ctor builds its kit through the hook, so an override has to run
        // here or nowhere (R_no_vaadin_in_api limb 2). Not handed on to setEditorKit
        // as the JDK's ctor hands it: that path WARNs that the kit drives no
        // pipeline, which is noise about a default nobody asked for — and reddens
        // every WarnInventoryTest route holding a JTextPane.
        defaultEditorKit = createDefaultEditorKit();
        getDocument().addDocumentListener(renderPush);
    }

    /** Installs {@code doc} as the model (must be a {@link StyledDocument}). */
    public JTextPane(StyledDocument doc) {
        this();
        setStyledDocument(doc);
    }

    // ---- document model ----

    /** A content-type change leaves the {@code StyledDocument}: the peer renders it (D_jtextpane). */
    @Override
    boolean contentTypeInstallsDocument() {
        return false;
    }

    @Override
    protected Document createDefaultDocument() {
        // JDK JTextPane's default model is a DefaultStyledDocument — reused verbatim
        // per D_whitelist_porting (not in the Component hierarchy). getStyledDocument() casts to it.
        return new DefaultStyledDocument();
    }

    public StyledDocument getStyledDocument() {
        return (StyledDocument) getDocument();
    }

    /** Convenience for {@link #setDocument} typed to {@link StyledDocument}. */
    public void setStyledDocument(StyledDocument doc) {
        setDocument(doc);
    }

    @Override
    public void setDocument(Document doc) {
        // Faithful throw (R_match_swing_errors): JDK JTextPane rejects a non-StyledDocument model.
        if (!(doc instanceof StyledDocument)) {
            throw new IllegalArgumentException("Model must be StyledDocument");
        }
        Document old = getDocument();
        if (old != null) {
            old.removeDocumentListener(renderPush);
        }
        super.setDocument(doc);       // base rehooks its own sync + fires "document" PCE
        doc.addDocumentListener(renderPush);
        pushHtmlToPeer();             // reflect the swapped-in model in the peer
    }

    // ---- two-way sync: HTML both ways (push on the main value, read it back) ----

    @Override
    protected void wirePeerToDocument(SJEditorPane editor) {
        editor.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.EAGER);
        // Reverse: a browser edit fires the main value-change; read the HTML back
        // from the same property (getValue(), not asDelta() — reading the property the
        // event fired on avoids cross-property staleness) and rebuild the document.
        // Our own HTML pushes fire this synchronously and are caught by
        // preventPeerEvents, so only genuine edits get through.
        editor.addValueChangeListener(e -> {
            if (ignoreInitHandshake(e.isFromClient(), isBlankHtml(e.getValue()))) {
                return;
            }
            vaadinx.EHelper.callSwing(() -> syncDocumentFromHtml(e.getValue()));
        });
    }

    @Override
    protected void pushTextToPeer(String t) {
        // No-op: setText's document mutation already fired renderPush, which
        // serialized the doc to HTML and pushed it. JEditorPane's own HTML push
        // would double-write, so JTextPane suppresses it.
    }

    private void pushHtmlToPeer() {
        if (preventPeerEvents) {
            return;
        }
        if (getPeer() instanceof SJEditorPane editor) {
            // Push via the main HTML value — NOT asDelta().setValue, whose async
            // executeJs("return this.htmlValue") reconcile races Quill to empty
            // during construction (D_jtextpane). Karibu can't reproduce that browser race,
            // so this is the sole guard against a "just asDelta" rewrite.
            String html = RteHtmlCodec.toHtml(getStyledDocument());
            if (!isBlankHtml(html)) {
                pushedContentToPeer = true;   // expect the client to echo this on attach
            }
            withPeer(p -> {
                preventPeerEvents = true;
                try {
                    editor.setText(html);
                } finally {
                    preventPeerEvents = false;
                }
            });
        }
    }

    /**
     * Rebuilds the document from a browser edit's HTML (clear + re-insert), firing
     * document-level {@code DocumentEvent}s to user listeners (R_swing_is_truth). Package-visible:
     * it's the reverse-sync entry point, and the test drives it directly.
     */
    void syncDocumentFromHtml(String html) {
        if (preventPeerEvents) {
            return;
        }
        preventPeerEvents = true;
        try {
            // Rebuild-on-change: fromHtml clears + re-inserts, firing normal
            // document-level DocumentEvents to user listeners (R_swing_is_truth).
            RteHtmlCodec.fromHtml(html, getStyledDocument());
        } finally {
            preventPeerEvents = false;
        }
    }

    // ---- styled attribute surface ----

    /**
     * Applies {@code attr} to the pane's input attributes whatever is selected, which
     * makes no visible change: the JDK's no-selection behaviour, where the JDK would
     * style a selection. To style existing text call
     * {@code getStyledDocument().setCharacterAttributes(...)}.
     */
    public void setCharacterAttributes(AttributeSet attr, boolean replace) {
        MutableAttributeSet inputAttributes = getInputAttributes();
        if (replace) {
            inputAttributes.removeAttributes(inputAttributes.getAttributeNames());
        }
        inputAttributes.addAttributes(attr);
    }

    /** The pending-input character attributes (JDK returns these at an empty selection). */
    public AttributeSet getCharacterAttributes() {
        return getInputAttributes().copyAttributes();
    }

    /**
     * Applies paragraph {@code attr} to the first paragraph — the JDK's behaviour at
     * an empty selection ({@code setParagraphAttributes(0, 0, …)}), which does
     * render (e.g. alignment). Offset-based styling of other paragraphs goes through
     * {@code getStyledDocument().setParagraphAttributes(offset, len, …)}.
     */
    public void setParagraphAttributes(AttributeSet attr, boolean replace) {
        getStyledDocument().setParagraphAttributes(0, 0, attr, replace);
    }

    /** Paragraph attributes of the first paragraph (JDK reads these at the caret). */
    public AttributeSet getParagraphAttributes() {
        return getStyledDocument().getParagraphElement(0).getAttributes();
    }

    /**
     * @return the kit's pending-input attribute set — the JDK's own home for it,
     *         so a {@link #createDefaultEditorKit} override that seeds attributes
     *         is observable here
     */
    public MutableAttributeSet getInputAttributes() {
        return getStyledEditorKit().getInputAttributes();
    }

    // Named-style surface — delegates to the DefaultStyledDocument's StyleContext
    // (full JDK behaviour). A style whose attributes resolve onto a run render via
    // the codec like any other attribute.

    public Style addStyle(String nm, Style parent) {
        return getStyledDocument().addStyle(nm, parent);
    }

    public void removeStyle(String nm) {
        getStyledDocument().removeStyle(nm);
    }

    public Style getStyle(String nm) {
        return getStyledDocument().getStyle(nm);
    }

    /** Sets the logical style of the first paragraph (JDK applies it at the caret). */
    public void setLogicalStyle(Style s) {
        getStyledDocument().setLogicalStyle(0, s);
    }

    /** Logical style of the first paragraph. */
    public Style getLogicalStyle() {
        return getStyledDocument().getLogicalStyle(0);
    }

    // ---- inline embedding ----

    /**
     * Embeds an image: an {@link vaadinx.swing.ImageIcon} rasterises to a base64 PNG
     * Delta image op and renders as {@code <img>}. Any other {@link Icon} WARNs and
     * is dropped — rasterising a custom {@code paintIcon} needs server-side Graphics
     * paint, which is OOS (R_match_swing_errors(b)).
     */
    public void insertIcon(Icon g) {
        if (g instanceof ImageIcon ii) {
            SimpleAttributeSet attr = new SimpleAttributeSet();
            // Store the JDK icon: the StyledDocument/StyleConstants machinery is pure
            // JDK, and the codec reads javax.swing.Icon back out. The one-char " "
            // placeholder matches javax.swing.JTextPane.insertIcon.
            StyleConstants.setIcon(attr, ii.asJdk());
            insertPlaceholder(attr);
        } else {
            vaadinx.EHelper.onUnimplemented("JTextPane", "insertIcon",
                    "only ImageIcon is supported; a custom Icon needs Graphics paint (OOS)");
        }
    }

    /**
     * Drop-and-WARN: a live inline component has no Delta/RTE representation. Takes
     * the ported {@link vaadinx.awt.Component} (the import-swap type).
     */
    public void insertComponent(vaadinx.awt.Component c) {
        vaadinx.EHelper.onUnimplemented("JTextPane", "insertComponent",
                "live inline component has no RTE representation");
    }

    private void insertPlaceholder(AttributeSet attr) {
        try {
            Document doc = getDocument();
            doc.insertString(doc.getLength(), " ", attr);
        } catch (javax.swing.text.BadLocationException e) {
            vaadinx.EHelper.onUnsupported("JTextPane", "insertIcon", e);
        }
    }

    // ---- EditorKit surface (faithful throw; no rendering pipeline) ----

    @Override
    public final void setEditorKit(javax.swing.text.EditorKit kit) {
        // Faithful throw (R_match_swing_errors): JDK JTextPane.setEditorKit is final and rejects a
        // non-StyledEditorKit. A valid one goes to super, as the JDK's own body does —
        // which stores it and WARNs that the kit's rendering pipeline is not driven.
        if (!(kit instanceof StyledEditorKit)) {
            throw new IllegalArgumentException("Must be StyledEditorKit");
        }
        super.setEditorKit(kit);
    }

    /**
     * @return the kit this pane holds; called <em>from the constructor</em> as the
     *         JDK calls it, so an override cannot read its own subclass's fields
     */
    protected javax.swing.text.EditorKit createDefaultEditorKit() {
        return new StyledEditorKit();
    }

    /**
     * @return the kit {@link #createDefaultEditorKit} built at construction —
     *         where the JDK casts {@code getEditorKit()}, which here answers only
     *         what the migrator installed (see {@link JEditorPane#getEditorKit})
     */
    protected final StyledEditorKit getStyledEditorKit() {
        return (StyledEditorKit) defaultEditorKit;
    }

    @Override
    public String getUIClassID() {
        return "TextPaneUI";
    }
}
