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

package com.vaadin.swingbridge.surrogates.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;

import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

/**
 * State holder for {@link com.vaadin.swingbridge.surrogates.swing.text.JTextComponentMixin}
 * (SD_sjtextfield). Same {@link ComponentUtil#setData} pattern as
 * {@link ButtonStateStore} — keeps the mixin self-contained without
 * forcing every concrete consumer (SJTextField / future SJPasswordField /
 * SJTextArea) to expose abstract getters/setters as boilerplate.
 *
 * <h2>What does NOT live here</h2>
 *
 * <ul>
 *   <li>{@code editable} — read directly from {@code peer.isReadOnly()}
 *       per the R_vaadin_first Vaadin-first stance (SD_vaadin_first_binding). No shadow store needed.</li>
 *   <li>{@code listenerList} for ActionListener — JTextField-specific,
 *       not shared with JTextArea. Lives on the concrete (SJTextField)
 *       per SD_sjpanel's "concrete-specific state stays on the concrete" call.</li>
 *   <li>{@code columns} / {@code horizontalAlignment} / {@code rows} /
 *       {@code lineWrap} — JDK declares these on JTextField and JTextArea
 *       individually, not on JTextComponent. Each concrete owns its own.</li>
 * </ul>
 */
public final class TextStateStore {

    /** The installed {@link Document}. Lazy-initialised by the mixin's install hook. */
    public Document document;

    /**
     * Single shared {@link DocumentListener} that pushes Document mutations
     * into the peer under {@link #preventPeerEvents}. Created lazily on first
     * install; persists across {@code setDocument} swaps. Internal glue, not
     * user-supplied API.
     */
    public DocumentListener docToPeer;

    /**
     * R_swing_is_truth feedback-loop guard — same shape SJSlider / SJSpinner /
     * :emulators.JTextComponent use. Set true before peer writes; cleared
     * in {@code finally}; checked at the top of both directional listeners.
     */
    public boolean preventPeerEvents;

    /**
     * Swing's two-value caret model — {@code selectionStart = min(dot, mark)},
     * {@code selectionEnd = max(dot, mark)}, and {@code dot == mark} means a
     * bare caret with nothing selected.
     *
     * <p>Write-only mirror: the mixin pushes these to the browser and
     * never reads them back, so a caret the *user* moved leaves them stale.
     * Reads clamp to the Document length —
     * {@link com.vaadin.swingbridge.surrogates.swing.text.JTextComponentMixin#getCaretPosition()}
     * carries the contract.
     */
    public int dot;
    public int mark;

    /**
     * Caret listeners, and the {@link javax.swing.event.ChangeListener}s a
     * {@link javax.swing.text.Caret} takes — both fan out on every dot/mark
     * move, server-initiated or read back from the browser.
     *
     * <p>Legitimate under R_vaadin_first (not a shadow cache) because a real Vaadin
     * subscription stands behind them: the {@code emul-selection} DOM
     * listener the mixin installs on the peer element.
     */
    public final java.util.List<javax.swing.event.CaretListener> caretListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    public final java.util.List<javax.swing.event.ChangeListener> caretChangeListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Set once the browser-side selection bridge has been wired, so a second install is a no-op. */
    public boolean selectionBridgeInstalled;

    /** Set once the client-side selection reporter has been installed on the input element. */
    public boolean selectionReporterInstalled;

    /**
     * The one {@link javax.swing.text.Caret} view handed out for this
     * component. Cached because Swing's {@code getCaret()} returns a stable
     * instance and code compares against it —
     * {@code e.getSource() == field.getCaret()} is a real idiom, and a fresh
     * object per call would make it silently false.
     */
    public javax.swing.text.Caret caret;

    private TextStateStore() {}

    public static TextStateStore of(Component target) {
        TextStateStore existing = ComponentUtil.getData(target, TextStateStore.class);
        if (existing != null) return existing;
        TextStateStore fresh = new TextStateStore();
        ComponentUtil.setData(target, TextStateStore.class, fresh);
        return fresh;
    }
}
