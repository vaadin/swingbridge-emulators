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

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.swingbridge.surrogates.swing.text.JTextComponentMixin;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import com.vaadin.swingbridge.surrogates.util.CssConvert;

/**
 * Surrogate for {@link javax.swing.JTextArea}, a consumer of SD_sjtextfield's
 * {@link JTextComponentMixin}. Extends Vaadin
 * {@link TextArea} directly (is-a). Reaches the migrator either through
 * {@code :emulators.JTextArea} as peer, or as a richer-than-stock Vaadin
 * component in its own right.
 *
 * <h2>Why TextArea as peer (not TextField)</h2>
 *
 * Vaadin {@link TextArea} renders {@code <vaadin-text-area>} backed by an
 * HTML {@code <textarea>}, giving multi-line editing with native browser
 * wrapping — what JTextArea exists to provide. {@link TextArea} extends
 * {@link com.vaadin.flow.component.textfield.TextFieldBase} (sibling to
 * {@link com.vaadin.flow.component.textfield.TextField} /
 * {@link com.vaadin.flow.component.textfield.PasswordField}), so the
 * mixin's R_swing_is_truth sync activates uniformly.
 *
 * <h2>Override surface against {@link JTextComponentMixin}</h2>
 *
 * <ul>
 *   <li><b>{@link #validate}</b> — visibility-widening override required
 *       by {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public
 *       {@code validate()} contract. Vaadin {@link TextArea} declares
 *       inherited {@code validate()} as {@code protected} (input
 *       validation, not Swing's layout cycle). Same shape SJTextField /
 *       SJPasswordField take per SD_sjtextfield / SD_sjpasswordfield.</li>
 * </ul>
 *
 * <h2>JTextArea-specific surface</h2>
 *
 * <ul>
 *   <li><b>{@code rows} / {@code columns}</b> — ctor + setters store the
 *       sizing hint and write {@code peer.setMinRows(N)} +
 *       {@code peer.setWidth} of a {@link CssConvert#columnsToCssWidth}
 *       character-column hint per R_layouts_close_enough close-enough; negative
 *       throws IAE per R_match_swing_errors. JDK fires no PCE for either; we match.</li>
 *   <li><b>{@code tabSize} / {@code lineWrap} / {@code wrapStyleWord}</b>
 *       — field-only round-trip with PCE per JDK, no Vaadin counterpart.
 *       Vaadin TextArea wraps by default (browser textarea behaviour);
 *       there is no toggle to disable wrap without custom CSS, so
 *       {@code lineWrap=false} is observably ignored — accepted
 *       divergence (R_best_effort_behaviour best-effort, same call the emulator made).
 *       Likewise {@code tabSize} renders at the browser default.</li>
 *   <li><b>{@link #append} / {@link #insert} / {@link #replaceRange}</b>
 *       — Document mutation helpers, all three a bounds check in front of
 *       one private {@code spliceDocument} call: an append is a splice of
 *       the empty span at the end, an insert a splice of the empty span at
 *       {@code pos}. Each goes
 *       through the mixin's Document, so the R_swing_is_truth sync mirrors
 *       changes into the peer transparently (the peer never sees a
 *       half-updated state). Bad bounds throw IAE per R_match_swing_errors /
 *       JDK, each with its own message; null {@code str} is a documented
 *       no-op for {@code append}/{@code insert} and means "delete only" for
 *       {@code replaceRange}.</li>
 *   <li><b>{@link #getLineCount} / {@link #getLineOfOffset} /
 *       {@link #getLineStartOffset} / {@link #getLineEndOffset}</b> —
 *       PlainDocument's default root element holds one child per line,
 *       which is exactly Swing's line model for a JTextArea.
 *       BadLocationException on out-of-range matches JDK.</li>
 *   <li><b>{@code getUIClassID() == "TextAreaUI"}</b>.</li>
 * </ul>
 *
 * <h2>What is NOT here (vs SJTextField / SJPasswordField)</h2>
 *
 * <ul>
 *   <li><b>No ActionListener wiring on Enter.</b> JDK JTextArea's Enter
 *       inserts a newline — that's the whole point of a multi-line
 *       editor. The Enter-as-submit shape lives on JTextField only.</li>
 *   <li><b>No {@code setAction(Action)}.</b> JDK JTextArea doesn't have
 *       it (inherited surface stops at JTextComponent, not
 *       JTextField).</li>
 *   <li><b>No {@code horizontalAlignment}.</b> Same — JTextArea
 *       doesn't expose it; multi-line text follows
 *       ComponentOrientation only.</li>
 * </ul>
 */
public class SJTextArea extends TextArea implements JTextComponentMixin, EnterClaims.Claimant {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    /** Swing default: {@code rows=0} means "no preferred sizing hint". */
    private int rows;

    /** Swing default: {@code columns=0} means "no preferred sizing hint". */
    private int columns;

    /** Swing default: {@code tabSize=8}. Field-only — Vaadin honors browser default. */
    private int tabSize = 8;

    /** Swing default: {@code lineWrap=false}. Field-only — Vaadin TextArea always wraps. */
    private boolean lineWrap;

    /** Swing default: {@code wrapStyleWord=false}. Field-only — meaningful only when lineWrap=true. */
    private boolean wrapStyleWord;

    public SJTextArea() {
        this(null, null, 0, 0);
    }

    public SJTextArea(String text) {
        this(null, text, 0, 0);
    }

    public SJTextArea(int rows, int columns) {
        this(null, null, rows, columns);
    }

    public SJTextArea(String text, int rows, int columns) {
        this(null, text, rows, columns);
    }

    public SJTextArea(Document doc) {
        this(doc, null, 0, 0);
    }

    public SJTextArea(Document doc, String text, int rows, int columns) {
        // Root public ctor — all others funnel here. Order matters:
        // installTextComponentBindings (mixin's R_swing_is_truth sync wiring) MUST
        // run before any setText / setDocument so the listener is live
        // when the initial text reaches the Document.
        super();
        _installSwingClass();
        if (rows < 0) {
            throw new IllegalArgumentException("rows: " + rows);
        }
        if (columns < 0) {
            throw new IllegalArgumentException("columns: " + columns);
        }
        installTextComponentBindings();
        if (doc != null) {
            setDocument(doc);
        }
        this.rows = rows;
        this.columns = columns;
        applySizingToPeer();
        if (text != null) {
            setText(text);
        }
        // The newline half of claimsEnter(): a default button's Enter shortcut
        // preventDefault()s in the browser before the server can decline it, so an
        // editable area's Enter must not reach the shortcut at all.
        getElement().addEventListener("keydown", event -> { })
                .setFilter("event.key === 'Enter' && !element.readonly")
                .stopPropagation();
    }

    /**
     * Visibility-widening override of {@link TextArea#validate} to satisfy
     * {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public {@code validate()}
     * contract. Delegates to super so Vaadin's input-validation behaviour
     * is preserved. Same SJTextField / SJPasswordField pattern per SD_sjtextfield /
     * SD_sjpasswordfield (method-name clash between Swing's "validate the layout" and
     * Vaadin's "validate the input value" — same name, different concerns).
     */
    @Override
    public void validate() {
        super.validate();
    }

    // --- Rows / Columns ----------------------------------------------

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        if (rows < 0) {
            throw new IllegalArgumentException("rows less than zero.");
        }
        int old = this.rows;
        if (old == rows) return;
        this.rows = rows;
        applySizingToPeer();
        // JDK JTextArea fires no PCE for rows (just stores + invalidates).
        // R_layouts_close_enough keeps the revalidate as a no-op; no extra dispatch here.
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        int old = this.columns;
        if (old == columns) return;
        this.columns = columns;
        applySizingToPeer();
    }

    /**
     * Map rows/columns onto the Vaadin TextArea's CSS dimensions. Columns go
     * through {@link CssConvert#columnsToCssWidth}, the same character-column
     * hint JTextField uses. Rows drive {@code minRows} so the textarea renders
     * at least that tall without preventing the user from typing beyond it.
     * {@code 0} means "no hint" — clear the corresponding dimension so the peer
     * picks its default.
     */
    private void applySizingToPeer() {
        // Width behind the D_layout_owns_child_sizing variable — columns is a preferred
        // width, so a layout that sizes the axis itself overrides it (see SJTextField).
        // minRows is not: it is a floor, and no layout policy speaks to it.
        setWidth(com.vaadin.swingbridge.surrogates.util.LayoutCss.prefWidth(CssConvert.columnsToCssWidth(columns)));
        if (rows > 0) {
            setMinRows(rows);
        }
        // Vaadin TextArea has no "clear minRows" — leaving it at whatever
        // Vaadin's default is when rows drops to 0 is close enough for R_layouts_close_enough.
    }

    // --- tabSize / lineWrap / wrapStyleWord (field-only round-trip) --

    public int getTabSize() {
        return tabSize;
    }

    public void setTabSize(int size) {
        // JDK fires "tabSize" PCE. No peer equivalent — Vaadin TextArea
        // renders tabs at the browser's default width.
        int old = this.tabSize;
        this.tabSize = size;
        firePropertyChange("tabSize", old, size);
    }

    public boolean getLineWrap() {
        return lineWrap;
    }

    public void setLineWrap(boolean wrap) {
        // JDK fires "lineWrap" PCE + repaint. Vaadin TextArea wraps by
        // default (browser textarea behaviour); there's no toggle to
        // *disable* wrapping without custom CSS, so lineWrap=false is
        // observably ignored. Field-only round-trip keeps user code
        // that reads back the property honest.
        boolean old = this.lineWrap;
        this.lineWrap = wrap;
        firePropertyChange("lineWrap", old, wrap);
    }

    public boolean getWrapStyleWord() {
        return wrapStyleWord;
    }

    public void setWrapStyleWord(boolean word) {
        // JDK fires "wrapStyleWord" PCE. Only meaningful when lineWrap
        // is also true; under R_best_effort_behaviour we keep the field-only round-trip
        // without enforcing the coupling.
        boolean old = this.wrapStyleWord;
        this.wrapStyleWord = word;
        firePropertyChange("wrapStyleWord", old, word);
    }

    // --- Document mutation helpers (R_swing_is_truth sync via mixin's Document) ----

    /**
     * Appends {@code str} to the Document, a splice of the empty span at
     * the end. Null is a documented no-op per JDK.
     */
    public void append(String str) {
        if (str != null) {
            int end = getDocument().getLength();
            spliceDocument("append", str, end, end);
        }
    }

    /**
     * Inserts {@code str} at offset {@code pos}, a splice of the empty
     * span there. Null {@code str} is a documented no-op per JDK.
     *
     * @throws IllegalArgumentException if {@code pos} is outside the
     *         Document, per R_match_swing_errors — and checked here rather
     *         than in {@code spliceDocument} so the message stays the
     *         JDK's, which names the position rather than a range
     */
    public void insert(String str, int pos) {
        if (str != null) {
            if (pos < 0 || pos > getDocument().getLength()) {
                throw new IllegalArgumentException("Invalid insert position " + pos);
            }
            spliceDocument("insert", str, pos, pos);
        }
    }

    /**
     * Replaces the Document's {@code [start, end)} span with {@code str}.
     *
     * @param str the replacement, or {@code null} to delete the span and
     *        put nothing back — the JDK's "just delete" contract
     * @throws IllegalArgumentException on a span the Document does not
     *         hold, per R_match_swing_errors
     */
    public void replaceRange(String str, int start, int end) {
        if (start < 0 || end < start || end > getDocument().getLength()) {
            throw new IllegalArgumentException("Invalid range: start=" + start + " end=" + end);
        }
        spliceDocument("replaceRange", str, start, end);
    }

    /**
     * Swaps the Document's {@code [start, end)} span for {@code str},
     * the one mutation the three public helpers above are each a special
     * case of. Callers validate first: reaching the {@code catch} means
     * the span was legal when checked and illegal when applied, so the
     * WARN is about SB-Emulators rather than about migrator code.
     *
     * @param method the caller's own name, so the WARN names the method
     *        the migrator actually called
     * @param str the replacement, or {@code null} / empty to delete only
     */
    private void spliceDocument(String method, String str, int start, int end) {
        Document document = getDocument();
        try {
            if (end > start) {
                document.remove(start, end - start);
            }
            if (str != null && !str.isEmpty()) {
                document.insertString(start, str, null);
            }
        } catch (BadLocationException e) {
            SHelper.onUnimplemented(this, method, e);
        }
    }

    // --- Line-oriented Document queries -------------------------------

    /**
     * Number of lines in the Document. PlainDocument's default root
     * element has one child per line — that's exactly Swing's line
     * count. Non-line-structured Documents return 0.
     */
    public int getLineCount() {
        Element root = getDocument().getDefaultRootElement();
        return root == null ? 0 : root.getElementCount();
    }

    /**
     * Index of the line containing {@code offset}. JDK throws
     * BadLocationException when offset is out of range; we match.
     */
    public int getLineOfOffset(int offset) throws BadLocationException {
        Document document = getDocument();
        if (offset < 0 || offset > document.getLength()) {
            throw new BadLocationException("Invalid offset", offset);
        }
        return document.getDefaultRootElement().getElementIndex(offset);
    }

    /**
     * Start offset of {@code line}. JDK throws BadLocationException for
     * out-of-range line; we match.
     */
    public int getLineStartOffset(int line) throws BadLocationException {
        Element root = getDocument().getDefaultRootElement();
        if (line < 0 || line >= root.getElementCount()) {
            throw new BadLocationException("No such line", line);
        }
        return root.getElement(line).getStartOffset();
    }

    /**
     * End offset of {@code line}. Includes the trailing newline (or
     * EOF for the last line) per PlainDocument's element spec.
     */
    public int getLineEndOffset(int line) throws BadLocationException {
        Element root = getDocument().getDefaultRootElement();
        if (line < 0 || line >= root.getElementCount()) {
            throw new BadLocationException("No such line", line);
        }
        return root.getElement(line).getEndOffset();
    }

    /** Enter inserts a newline while editable — Swing's {@code insert-break} binding. */
    @Override
    public boolean claimsEnter() {
        return isEditable();
    }

    // --- L&F class id ------------------------------------------------

    @Override
    public String getUIClassID() {
        return "TextAreaUI";
    }

    /**
     * JDK JTextArea.paramString chains JTextComponent's tail and appends
     * the JTextArea-specific state. We follow the same shape; underlying
     * Vaadin {@code Component.toString} is the honest host-side equivalent.
     */
    protected String paramString() {
        return "rows=" + rows
                + ",columns=" + columns
                + ",tabSize=" + tabSize
                + ",lineWrap=" + lineWrap
                + ",wrapStyleWord=" + wrapStyleWord;
    }

    // --- Accessibility (deferred per surrogate-wide stance) ----------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
