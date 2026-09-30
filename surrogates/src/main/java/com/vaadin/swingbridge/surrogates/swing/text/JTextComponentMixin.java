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

package com.vaadin.swingbridge.surrogates.swing.text;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.textfield.TextFieldBase;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.internal.TextStateStore;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

/**
 * Mixin for {@link javax.swing.text.JTextComponent} (SD_sjtextfield). Layers the
 * JTextComponent surface on top of {@link JComponentMixin}. Shared across
 * SJTextField, SJPasswordField, and SJTextArea.
 *
 * <h2>R_vaadin_first stance: Document-as-source-of-truth, R_swing_is_truth sync to peer</h2>
 *
 * Per R_vaadin_first, surrogates reproduce the UI functionality of their Swing
 * originals. The {@link Document} model is core JTextComponent functionality
 * — it drives DocumentListener fan-out, holds the editable text, and is
 * what user code mutates. The mixin keeps it as the source of truth and
 * R_swing_is_truth-syncs to a Vaadin {@link TextFieldBase} peer (TextField / PasswordField
 * / TextArea all extend it). Highlighter / Keymap / NavigationFilter /
 * editor-kit machinery have no Vaadin counterpart and drop-and-WARN.
 *
 * <p>The caret and selection <em>do</em> have one: dot/mark live in
 * {@link TextStateStore} and reach the browser as a {@code setSelectionRange}
 * on the peer's own input element, so the auto-advance and
 * reposition-after-reformat idioms work. The reverse direction is
 * {@link #installSelectionBridge}, wired lazily on the first caret listener —
 * without a listener, dot/mark are a write-only mirror and
 * {@link #getCaretPosition()} can lag a caret the user moved.
 *
 * <h2>R_swing_is_truth Document↔peer sync</h2>
 *
 * The two-way bridge mirrors the {@code :emulators.swing.text.JTextComponent}
 * implementation verbatim — same code shape, surrogate-side state holder:
 *
 * <ul>
 *   <li>{@link #installTextComponentBindings} (called from concrete's
 *       ctor) installs the default {@link PlainDocument} via
 *       {@link #createDefaultDocument}, attaches the internal
 *       {@code docToPeer} {@link DocumentListener}, and (when the peer is
 *       a {@link TextFieldBase}) sets {@link ValueChangeMode#EAGER} +
 *       wires the peer's {@code addValueChangeListener} to mirror back.</li>
 *   <li>The internal {@code docToPeer} listener fires on
 *       insert/remove. If {@code preventPeerEvents} is set, it bails;
 *       else it sets the flag, calls {@code peer.setValue(readDocument())},
 *       clears in {@code finally}.</li>
 *   <li>The peer ValueChangeListener fires on user keystrokes (eager).
 *       Same flag check; mutates the Document under the flag.</li>
 *   <li>{@link #setDocument} swaps the Document, unhooks the listener
 *       from old, attaches to new, fires {@code "document"} PCE, then
 *       calls the internal mutation hook so the new doc's text reaches
 *       the peer.</li>
 * </ul>
 *
 * <h2>{@code getUI} dropped — Vaadin {@code Optional<UI>} signature clash</h2>
 *
 * Same shape SD_sjbutton records for {@code Button.getIcon()} and SD_sjpanel records
 * for {@code JPanel.getUI()}: Vaadin {@code Component.getUI()} returns
 * {@code Optional<UI>}, JDK {@code JTextComponent.getUI()} returns
 * {@code TextUI}. The mixin doesn't declare a JDK-shaped getter.
 * {@link #setUI(javax.swing.plaf.TextUI)} survives as overload-safe
 * (no clash on parameter type).
 */
public interface JTextComponentMixin extends JComponentMixin {

    /** Cast {@code this} to Vaadin {@link Component} for holder lookups. */
    private Component _self() {
        return (Component) this;
    }

    // --- Install hook (called from concrete's ctor) -------------------

    /**
     * Install the default Document, attach the docToPeer listener, and
     * wire the peer's ValueChangeListener if the peer is a
     * {@link TextFieldBase}. Idempotent: a second call is a no-op once
     * a Document is installed (same guard shape as
     * {@code AbstractButtonMixin.installButtonBindings}).
     */
    default void installTextComponentBindings() {
        TextStateStore s = TextStateStore.of(_self());
        if (s.document != null) return;

        // Lazy-create the shared DocumentListener once. Same instance
        // re-used across setDocument swaps — unhook + re-hook in
        // setDocument handles the lifecycle.
        s.docToPeer = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onDocumentMutated(); }
            @Override public void removeUpdate(DocumentEvent e) { onDocumentMutated(); }
            @Override public void changedUpdate(DocumentEvent e) {
                // Attribute changes only — PlainDocument never fires this,
                // and rich-text attributes don't round-trip to a plain
                // TextFieldBase peer. Safe no-op.
            }
        };

        // Direct field assign (no setDocument call) — initial install
        // shouldn't fire a "document" PCE. Same shape :emulators.JTextComponent
        // takes in its ctor.
        s.document = createDefaultDocument();
        s.document.addDocumentListener(s.docToPeer);

        // Peer → Swing wire: typing in the browser updates the Document.
        // EAGER mode fires ValueChange on every keystroke (closer to
        // Swing's "Document updates while typing" contract than the
        // default ON_CHANGE). Wrapped in SHelper.callSwing per R_callswing_envelope.
        if (_self() instanceof TextFieldBase<?, ?> tf) {
            tf.setValueChangeMode(ValueChangeMode.EAGER);
            @SuppressWarnings("unchecked")
            HasValue<?, Object> hv = (HasValue<?, Object>) tf;
            hv.addValueChangeListener(e -> SHelper.callSwing(
                    () -> syncDocumentFromPeer((String) e.getValue())));
        }
    }

    /**
     * Factory hook for the default {@link Document}. Subclasses override
     * to pick richer Document models. PlainDocument matches what JTextField /
     * JTextArea / JPasswordField all use.
     */
    default Document createDefaultDocument() {
        return new PlainDocument();
    }

    // --- Document accessors -------------------------------------------

    default Document getDocument() {
        return TextStateStore.of(_self()).document;
    }

    /**
     * Swap the Document. Detaches the {@code docToPeer} listener from
     * the old Document, attaches to the new, fires {@code "document"}
     * PCE, then mirrors the new doc's text to the peer so the UI
     * reflects the swap immediately. Null is legal per JDK.
     */
    default void setDocument(Document doc) {
        TextStateStore s = TextStateStore.of(_self());
        Document old = s.document;
        if (old == doc) return;
        if (old != null && s.docToPeer != null) {
            old.removeDocumentListener(s.docToPeer);
        }
        s.document = doc;
        if (doc != null && s.docToPeer != null) {
            doc.addDocumentListener(s.docToPeer);
        }
        firePropertyChange("document", old, doc);
        if (doc != null) {
            // Mirror new doc's text to peer immediately.
            onDocumentMutated();
        }
    }

    // --- R_swing_is_truth sync helpers (private to the mixin) ----------------------

    /**
     * Push the current Document's text into the peer, guarded by
     * {@code preventPeerEvents} so the peer's ValueChangeListener bails
     * rather than round-tripping back into the Document. No-op when
     * the peer isn't a TextFieldBase (Div fallback).
     *
     * <p>Hops onto the owner's UI thread, since a background thread may mutate the
     * Document — the JDK documents {@code AbstractDocument.insertString} / {@code remove}
     * as thread safe (SD_background_model_hop).
     */
    @SuppressWarnings("unchecked")
    private void onDocumentMutated() {
        TextStateStore s = TextStateStore.of(_self());
        if (s.preventPeerEvents) return;
        if (_self() instanceof TextFieldBase<?, ?> tf) {
            String text = readDocument();
            // Allowed by R_tolerate_off_ui_thread because callback from model: Document DocumentListener
            SHelper.runOnOwnerUI(_self(), () -> {
                s.preventPeerEvents = true;
                try {
                    ((HasValue<?, String>) tf).setValue(text);
                } finally {
                    s.preventPeerEvents = false;
                }
            });
        }
    }

    /**
     * Mirror the peer's current value into the Document, guarded so the
     * DocumentListener bails rather than round-tripping back to the peer.
     * The Document mutation still fires events to user-registered
     * DocumentListeners — that's R_swing_is_truth's whole point.
     */
    private void syncDocumentFromPeer(String peerValue) {
        TextStateStore s = TextStateStore.of(_self());
        if (s.preventPeerEvents) return;
        s.preventPeerEvents = true;
        try {
            s.document.remove(0, s.document.getLength());
            if (peerValue != null && !peerValue.isEmpty()) {
                s.document.insertString(0, peerValue, null);
            }
        } catch (BadLocationException e) {
            SHelper.onUnimplemented(_self(), "syncDocumentFromPeer", e);
        } finally {
            s.preventPeerEvents = false;
        }
        // Follow the text to its end, without pushing back to the browser.
        // Two callers land here and this is the best answer for both: a
        // programmatic setValue (where an HTML input puts the caret at the
        // end, so this matches) and the user typing (where the browser's caret
        // is authoritative and unreadable — but typing at the end is the common
        // case, and leaving dot at a stale 0 is wrong there every time).
        int previousDot = s.dot;
        int previousMark = s.mark;
        s.dot = s.document.getLength();
        s.mark = s.dot;
        fireCaretUpdate(previousDot, previousMark);
    }

    /**
     * Unmasked snapshot of the Document's text for internal sync use.
     * Bypasses subclass {@code getText()} overrides (a future
     * SJPasswordField masks its return value with echoChar) so the
     * peer always sees cleartext.
     */
    private String readDocument() {
        TextStateStore s = TextStateStore.of(_self());
        try {
            return s.document.getText(0, s.document.getLength());
        } catch (BadLocationException e) {
            SHelper.onUnimplemented(_self(), "readDocument", e);
            return "";
        }
    }

    // --- Text round-trip ----------------------------------------------

    default String getText() {
        return readDocument();
    }

    default String getText(int offs, int len) throws BadLocationException {
        return TextStateStore.of(_self()).document.getText(offs, len);
    }

    /**
     * Replace the Document's contents. Null normalises to empty string
     * — JDK's AbstractDocument early-returns on null/empty, we make that
     * explicit. BadLocationException on (0, length) of a live Document
     * is effectively unreachable; swallow-and-warn per D_never_fail_on_gaps.
     */
    default void setText(String t) {
        TextStateStore s = TextStateStore.of(_self());
        try {
            s.document.remove(0, s.document.getLength());
            if (t != null && !t.isEmpty()) {
                s.document.insertString(0, t, null);
            }
        } catch (BadLocationException e) {
            SHelper.onUnimplemented(_self(), "setText", e);
        }
        // Swing's caret rides its Document's insert to the end of the new
        // text; a browser input does the same on a value change. Match both,
        // so a setText followed by getCaretPosition() doesn't report a stale
        // offset into text that no longer exists.
        int previousDot = s.dot;
        int previousMark = s.mark;
        s.dot = s.document.getLength();
        s.mark = s.dot;
        fireCaretUpdate(previousDot, previousMark);
    }

    // --- Editable -----------------------------------------------------

    /**
     * Reads {@code peer.isReadOnly()} (negated) when peer is a
     * {@link TextFieldBase}. Non-TextFieldBase peers always report
     * {@code true} — accepted lossy direction per R_vaadin_first (no reflection).
     */
    default boolean isEditable() {
        if (_self() instanceof TextFieldBase<?, ?> tf) {
            return !tf.isReadOnly();
        }
        return true;
    }

    /**
     * Propagate to {@code peer.setReadOnly}, fire {@code "editable"} PCE.
     * No shadow store per R_vaadin_first Vaadin-first; readback via
     * {@link #isEditable}. Equality short-circuit reads the current peer
     * state to avoid spurious PCE.
     */
    default void setEditable(boolean b) {
        boolean old = isEditable();
        if (old == b) return;
        if (_self() instanceof TextFieldBase<?, ?> tf) {
            tf.setReadOnly(!b);
        }
        firePropertyChange("editable", old, b);
    }

    // --- Copy / cut / paste — drop-and-WARN per R_vaadin_first (clipboard is post-Sampler)

    default void copy() {
        SHelper.onUnimplemented(_self(), "copy");
    }

    default void cut() {
        SHelper.onUnimplemented(_self(), "cut");
    }

    default void paste() {
        SHelper.onUnimplemented(_self(), "paste");
    }

    // --- Caret + selection -------------------------------------------
    //
    // One mechanism, dot + mark in TextStateStore, exactly as Swing models it:
    // the caret is `dot`, the anchored end of the selection is `mark`, and
    // every method below is a view onto that pair. The Document supplies the
    // text and the bounds; a `setSelectionRange` push mirrors the pair into
    // the browser's own input element.
    //
    // Only the server→browser direction exists. Vaadin surfaces no
    // selection/caret property and no `selectionchange` event, so a caret the
    // user moves is invisible here — hence the clamp on every read, and the
    // documented staleness on getCaretPosition().

    default int getSelectionStart() {
        TextStateStore s = TextStateStore.of(_self());
        return Math.min(clampToDocument(s.dot), clampToDocument(s.mark));
    }

    default int getSelectionEnd() {
        TextStateStore s = TextStateStore.of(_self());
        return Math.max(clampToDocument(s.dot), clampToDocument(s.mark));
    }

    default void setSelectionStart(int start) {
        select(start, getSelectionEnd());
    }

    default void setSelectionEnd(int end) {
        select(getSelectionStart(), end);
    }

    /**
     * Selects {@code [start, end)}, leaving the caret at {@code end}.
     *
     * <p>Out-of-range arguments clamp rather than throw, and an {@code end}
     * below {@code start} collapses to {@code start} — the JDK's own
     * {@link javax.swing.text.JTextComponent#select} contract, and the reason
     * this method can't just delegate to {@link #setCaretPosition}, which
     * throws instead.
     */
    default void select(int start, int end) {
        TextStateStore s = TextStateStore.of(_self());
        int docLength = s.document.getLength();
        int from = Math.min(Math.max(start, 0), docLength);
        int previousDot = s.dot;
        int previousMark = s.mark;
        s.mark = from;
        s.dot = Math.min(Math.max(end, from), docLength);
        pushSelectionToPeer();
        fireCaretUpdate(previousDot, previousMark);
    }

    default void selectAll() {
        select(0, TextStateStore.of(_self()).document.getLength());
    }

    /** @return the selected text, or {@code null} when nothing is selected — the JDK's contract, not the empty string. */
    default String getSelectedText() {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start == end) return null;
        try {
            return getText(start, end - start);
        } catch (BadLocationException e) {
            SHelper.onUnimplemented(_self(), "getSelectedText", e);
            return null;
        }
    }

    /**
     * Replaces the selection with {@code content} (deleting it when
     * {@code content} is null or empty), then leaves the caret after what was
     * inserted.
     *
     * <p>Runs through the Document, so DocumentListeners fire and the R_swing_is_truth sync
     * carries the new text to the peer — no separate value push.
     */
    default void replaceSelection(String content) {
        TextStateStore s = TextStateStore.of(_self());
        int start = getSelectionStart();
        int end = getSelectionEnd();
        try {
            if (start != end) s.document.remove(start, end - start);
            if (content != null && !content.isEmpty()) {
                s.document.insertString(start, content, null);
            }
        } catch (BadLocationException e) {
            SHelper.onUnimplemented(_self(), "replaceSelection", e);
            return;
        }
        setCaretPosition(start + (content == null ? 0 : content.length()));
    }

    /**
     * @return the caret offset — the value last written here, clamped to the
     *     Document, <em>not</em> a reading of where the browser's caret
     *     actually sits. A caret the user moved by clicking or arrowing is not
     *     observable server-side, so this lags in exactly that case.
     */
    default int getCaretPosition() {
        return clampToDocument(TextStateStore.of(_self()).dot);
    }

    /**
     * @throws IllegalArgumentException if {@code position} is negative or past
     *     the end of the Document, per
     *     {@link javax.swing.text.JTextComponent#setCaretPosition} (R_match_swing_errors — Swing
     *     throws here, so we do)
     */
    default void setCaretPosition(int position) {
        TextStateStore s = TextStateStore.of(_self());
        requireInDocument(position, "setCaretPosition");
        int previousDot = s.dot;
        int previousMark = s.mark;
        s.dot = position;
        s.mark = position;
        pushSelectionToPeer();
        fireCaretUpdate(previousDot, previousMark);
    }

    /**
     * Moves the caret to {@code pos} while leaving the mark where it is, so
     * the selection grows or shrinks.
     *
     * @throws IllegalArgumentException if {@code pos} is outside the Document
     */
    default void moveCaretPosition(int pos) {
        TextStateStore s = TextStateStore.of(_self());
        requireInDocument(pos, "moveCaretPosition");
        int previousDot = s.dot;
        s.dot = pos;
        pushSelectionToPeer();
        fireCaretUpdate(previousDot, s.mark);
    }

    /**
     * The anchored end of the selection — Swing reaches it only through
     * {@link javax.swing.text.Caret#getMark()}, which is why this accessor has
     * no JDK counterpart on JTextComponent itself.
     */
    default int getCaretMark() {
        return clampToDocument(TextStateStore.of(_self()).mark);
    }

    /** @return the same live view on every call, as Swing does; never {@code null}, so {@code getCaret().setDot(n)} is safe to chain. */
    default javax.swing.text.Caret getCaret() {
        TextStateStore s = TextStateStore.of(_self());
        if (s.caret == null) s.caret = new PeerCaret(this);
        return s.caret;
    }

    /**
     * WARNs and discards. A user-supplied Caret exists to *paint* a
     * caret, which is R_match_swing_errors sub-bucket (b) territory, and the browser draws
     * its input's caret regardless of what we store. Honouring the setter
     * would mean {@link #getCaret()} handing back an object whose dot/mark
     * no longer drive anything.
     */
    default void setCaret(javax.swing.text.Caret caret) {
        SHelper.onUnimplemented(_self(), "setCaret", caret);
    }

    /**
     * Fires on every caret move, including the ones the user makes — clicking
     * into the text, arrowing, drag-selecting — which arrive over the
     * {@code emul-selection} bridge {@link #installSelectionBridge} sets up.
     */
    default void addCaretListener(javax.swing.event.CaretListener l) {
        if (l == null) return;
        TextStateStore.of(_self()).caretListeners.add(l);
        installSelectionBridge();
    }

    default void removeCaretListener(javax.swing.event.CaretListener l) {
        TextStateStore.of(_self()).caretListeners.remove(l);
    }

    default javax.swing.event.CaretListener[] getCaretListeners() {
        return TextStateStore.of(_self()).caretListeners
                .toArray(new javax.swing.event.CaretListener[0]);
    }

    /** Registers a listener on the {@link javax.swing.text.Caret} rather than on the component; same fan-out. */
    default void addCaretChangeListener(javax.swing.event.ChangeListener l) {
        if (l == null) return;
        TextStateStore.of(_self()).caretChangeListeners.add(l);
        installSelectionBridge();
    }

    default void removeCaretChangeListener(javax.swing.event.ChangeListener l) {
        TextStateStore.of(_self()).caretChangeListeners.remove(l);
    }

    /**
     * Fans out to both listener families, and only when dot or mark actually
     * moved — which is also what stops the push→{@code selectionchange}→push
     * echo from looping.
     */
    private void fireCaretUpdate(int previousDot, int previousMark) {
        TextStateStore s = TextStateStore.of(_self());
        if (s.dot == previousDot && s.mark == previousMark) return;
        if (s.caretListeners.isEmpty() && s.caretChangeListeners.isEmpty()) return;
        javax.swing.event.CaretEvent caretEvent = new SCaretEvent(_self(), s.dot, s.mark);
        // Swing's DefaultCaret sources its ChangeEvent at the Caret, not the
        // component — so this one does too.
        javax.swing.event.ChangeEvent changeEvent = new javax.swing.event.ChangeEvent(getCaret());
        SHelper.callSwing(() -> {
            for (javax.swing.event.CaretListener l : s.caretListeners) l.caretUpdate(caretEvent);
            for (javax.swing.event.ChangeListener l : s.caretChangeListeners) l.stateChanged(changeEvent);
        });
    }

    /**
     * Wires the browser's caret back to dot/mark, so a caret the *user* moved
     * is readable server-side and fires listeners.
     *
     * <p>Installed lazily on the first listener registration rather than in
     * {@link #installTextComponentBindings}: it costs a round trip per caret
     * move, and a component nobody is listening to should not pay it.
     * {@link #fireCaretUpdate} dedupes an unchanged dot/mark, which is what breaks
     * the echo from our own {@code setSelectionRange} push.
     */
    default void installSelectionBridge() {
        TextStateStore s = TextStateStore.of(_self());
        if (s.selectionBridgeInstalled) return;
        s.selectionBridgeInstalled = true;

        addSelectionEventListener("emul-selection", 0, (start, end, backward) -> {
            int previousDot = s.dot;
            int previousMark = s.mark;
            s.dot = backward ? start : end;
            s.mark = backward ? end : start;
            fireCaretUpdate(previousDot, previousMark);
        });
        installSelectionReporter();
    }

    /**
     * Reports the browser's selection to a caller that keeps its own caret model,
     * without touching this component's dot and mark:
     *
     * <pre>{@code
     * Registration r = field.addSelectionReportListener(
     *         (start, end, backward) -> caret.setDot(backward ? end : start), 1000);
     * }</pre>
     *
     * <p>Two reports reach it. A <em>move</em> — a click, an arrow key, a drag, the caret
     * advancing as the user types — is held for {@code debounceMillis} of browser idleness,
     * and Flow sends a held event ahead of any other DOM event the browser fires, so a
     * click or key listener on the server already sees where the caret was. A
     * <em>pre-edit</em> report goes at once on every {@code beforeinput}, carrying the
     * selection the edit is about to replace; it reaches the server ahead of the value
     * change, so a Document filter reading the caret sees where the user typed.
     *
     * @param debounceMillis 0 to report every move as it happens
     * @return removes both listeners; the client-side reporter stays, and costs nothing
     *     without them
     */
    default com.vaadin.flow.shared.Registration addSelectionReportListener(SelectionReportListener listener,
            int debounceMillis) {
        com.vaadin.flow.shared.Registration moves =
                addSelectionEventListener("emul-selection", debounceMillis, listener);
        com.vaadin.flow.shared.Registration beforeEdit =
                addSelectionEventListener("emul-selection-now", 0, listener);
        installSelectionReporter();
        return () -> {
            moves.remove();
            beforeEdit.remove();
        };
    }

    /**
     * Shows {@code [start, end]} selected in the peer's input, the caret at {@code start}
     * when {@code backward}. Render-only: this component's own dot and mark stay as they
     * are and nothing fires, which is what a caller with its own caret model needs.
     */
    default void renderSelection(int start, int end, boolean backward) {
        whenInputElementReady(
                "if (i.setSelectionRange) i.setSelectionRange($0, $1, $2);",
                start, end, backward ? "backward" : "forward");
    }

    private com.vaadin.flow.shared.Registration addSelectionEventListener(String type, int debounceMillis,
            SelectionReportListener listener) {
        com.vaadin.flow.dom.DomListenerRegistration registration = _self().getElement()
                .addEventListener(type, event -> {
                    tools.jackson.databind.JsonNode data = event.getEventData();
                    if (data == null || !data.has("event.detail.start")) return;
                    listener.selectionReported(
                            data.get("event.detail.start").asInt(0),
                            data.get("event.detail.end").asInt(0),
                            data.has("event.detail.dir")
                                    && "backward".equals(data.get("event.detail.dir").asString("")));
                })
                .addEventData("event.detail.start")
                .addEventData("event.detail.end")
                .addEventData("event.detail.dir");
        if (debounceMillis > 0) {
            registration.debounce(debounceMillis, com.vaadin.flow.dom.DebouncePhase.TRAILING);
        }
        return registration;
    }

    /**
     * Installs, once, the client half both kinds of report share.
     *
     * <p>The JS listens on the peer's inner {@code inputElement}, not on the host —
     * {@code selectionchange} does not cross a shadow boundary — and re-dispatches a
     * custom event on the host, which is what Flow's element listener can see. A move
     * that repeats the last reported offsets is not dispatched, so a keystroke that only
     * changes the text spends nothing on an unchanged caret; the server dedupes again,
     * which is what breaks the echo from a {@code setSelectionRange} push.
     */
    private void installSelectionReporter() {
        TextStateStore s = TextStateStore.of(_self());
        if (s.selectionReporterInstalled) return;
        s.selectionReporterInstalled = true;
        whenInputElementReady(
                "let last = null;"
                + "const report = (type) => {"
                + "  const key = i.selectionStart + ':' + i.selectionEnd + ':' + i.selectionDirection;"
                + "  if (type === 'emul-selection' && key === last) return;"
                + "  last = key;"
                + "  this.dispatchEvent(new CustomEvent(type, {detail: {"
                + "    start: i.selectionStart, end: i.selectionEnd, dir: i.selectionDirection}}));"
                + "};"
                + "const move = () => report('emul-selection');"
                // selectionchange on an input is the modern, precise signal;
                // keyup/mouseup/select are the belt-and-braces for engines that
                // fire it late or not at all. The dedupe above makes the overlap
                // free.
                + "['selectionchange', 'select', 'keyup', 'mouseup'].forEach("
                + "    n => i.addEventListener(n, move));"
                + "i.addEventListener('beforeinput', () => report('emul-selection-now'));");
    }

    private int clampToDocument(int offset) {
        int docLength = TextStateStore.of(_self()).document.getLength();
        return Math.min(Math.max(offset, 0), docLength);
    }

    private void requireInDocument(int offset, String method) {
        int docLength = TextStateStore.of(_self()).document.getLength();
        if (offset < 0 || offset > docLength) {
            throw new IllegalArgumentException(
                    method + ": bad position: " + offset + " (document length " + docLength + ")");
        }
    }

    /**
     * Mirrors dot/mark onto the peer's own {@code <input>}/{@code <textarea>}.
     *
     * <p>Direction carries which end the caret sits at, so
     * shift-selecting backwards leaves the caret at the front.
     */
    private void pushSelectionToPeer() {
        TextStateStore s = TextStateStore.of(_self());
        renderSelection(getSelectionStart(), getSelectionEnd(), s.dot < s.mark);
    }

    /**
     * Runs {@code js} on the client with {@code i} bound to the peer's inner
     * input element, once that element exists.
     *
     * <p>Waits on {@code updateComplete} because a fresh peer's
     * {@code inputElement} is null until its first render, and
     * {@code executeJs} can land before then — a {@code setCaretPosition}
     * from a constructor would otherwise be dropped on the floor. Peers
     * with no input element at all (the {@code Div} fallback) no-op
     * silently rather than WARNing: the caller already got its server-side
     * answer, and the WARN would fire on every caret move.
     */
    private void whenInputElementReady(String js, Object... params) {
        _self().getElement().executeJs(
                "const run = () => { const i = this.inputElement; if (!i) return; " + js + " };"
                        + "this.updateComplete ? this.updateComplete.then(run) : run();",
                params);
    }

    // --- Caret / selection / disabled-text colors — drop-and-WARN ----

    default java.awt.Color getCaretColor() {
        SHelper.onUnimplemented(_self(), "getCaretColor");
        return null;
    }

    default void setCaretColor(java.awt.Color c) {
        SHelper.onUnimplemented(_self(), "setCaretColor", c);
    }

    default java.awt.Color getSelectionColor() {
        SHelper.onUnimplemented(_self(), "getSelectionColor");
        return null;
    }

    default void setSelectionColor(java.awt.Color c) {
        SHelper.onUnimplemented(_self(), "setSelectionColor", c);
    }

    default java.awt.Color getSelectedTextColor() {
        SHelper.onUnimplemented(_self(), "getSelectedTextColor");
        return null;
    }

    default void setSelectedTextColor(java.awt.Color c) {
        SHelper.onUnimplemented(_self(), "setSelectedTextColor", c);
    }

    default java.awt.Color getDisabledTextColor() {
        SHelper.onUnimplemented(_self(), "getDisabledTextColor");
        return null;
    }

    default void setDisabledTextColor(java.awt.Color c) {
        SHelper.onUnimplemented(_self(), "setDisabledTextColor", c);
    }

    // --- Highlighter / Keymap / NavigationFilter — drop-and-WARN -----

    default javax.swing.text.Highlighter getHighlighter() {
        SHelper.onUnimplemented(_self(), "getHighlighter");
        return null;
    }

    default void setHighlighter(javax.swing.text.Highlighter h) {
        SHelper.onUnimplemented(_self(), "setHighlighter", h);
    }

    default javax.swing.text.Keymap getKeymap() {
        SHelper.onUnimplemented(_self(), "getKeymap");
        return null;
    }

    default void setKeymap(javax.swing.text.Keymap k) {
        SHelper.onUnimplemented(_self(), "setKeymap", k);
    }

    default javax.swing.text.NavigationFilter getNavigationFilter() {
        SHelper.onUnimplemented(_self(), "getNavigationFilter");
        return null;
    }

    default void setNavigationFilter(javax.swing.text.NavigationFilter f) {
        SHelper.onUnimplemented(_self(), "setNavigationFilter", f);
    }

    // --- Margin (Insets — no Vaadin counterpart per SD_sjbutton's pattern) ---

    default java.awt.Insets getMargin() {
        SHelper.onUnimplemented(_self(), "getMargin");
        return null;
    }

    default void setMargin(java.awt.Insets m) {
        SHelper.onUnimplemented(_self(), "setMargin", m);
    }

    // --- Focus accelerator — drop-and-WARN (could wire via Shortcuts later)

    default char getFocusAccelerator() {
        SHelper.onUnimplemented(_self(), "getFocusAccelerator");
        return '\0';
    }

    default void setFocusAccelerator(char c) {
        SHelper.onUnimplemented(_self(), "setFocusAccelerator", c);
    }

    // --- Drag and drop — drop-and-WARN ----------

    default boolean getDragEnabled() {
        SHelper.onUnimplemented(_self(), "getDragEnabled");
        return false;
    }

    default void setDragEnabled(boolean enabled) {
        SHelper.onUnimplemented(_self(), "setDragEnabled", enabled);
    }

    default javax.swing.DropMode getDropMode() {
        SHelper.onUnimplemented(_self(), "getDropMode");
        return null;
    }

    default void setDropMode(javax.swing.DropMode m) {
        SHelper.onUnimplemented(_self(), "setDropMode", m);
    }

    // --- Editor-kit actions — empty array (matches emulator) ---------

    default javax.swing.Action[] getActions() {
        return new javax.swing.Action[0];
    }

    // --- View↔model coordinate math — drop-and-WARN per R_layouts_close_enough -----------

    default int viewToModel(java.awt.Point p) {
        SHelper.onUnimplemented(_self(), "viewToModel", p);
        return 0;
    }

    default int viewToModel2D(java.awt.geom.Point2D p) {
        SHelper.onUnimplemented(_self(), "viewToModel2D", p);
        return 0;
    }

    default java.awt.Rectangle modelToView(int pos) throws BadLocationException {
        SHelper.onUnimplemented(_self(), "modelToView", pos);
        return null;
    }

    default java.awt.geom.Rectangle2D modelToView2D(int pos) throws BadLocationException {
        SHelper.onUnimplemented(_self(), "modelToView2D", pos);
        return null;
    }

    // --- Print / Read / Write — drop-and-WARN (printing out of scope) -

    default boolean print() throws java.awt.print.PrinterException {
        SHelper.onUnimplemented(_self(), "print");
        return false;
    }

    default void read(java.io.Reader r, Object desc) throws java.io.IOException {
        SHelper.onUnimplemented(_self(), "read", r, desc);
    }

    default void write(java.io.Writer w) throws java.io.IOException {
        SHelper.onUnimplemented(_self(), "write", w);
    }

    // --- L&F dispatch (no-op / WARN). No JDK-shaped getUI — Vaadin
    //     Component.getUI() Optional<UI> clash; same SD_sjbutton/SD_sjpanel stance.

    default void updateUI() {
        // No-op: our "UI" is the Vaadin peer, which isn't pluggable.
    }

    default void setUI(javax.swing.plaf.TextUI ui) {
        SHelper.onUnimplemented(_self(), "setUI", ui);
    }
}
