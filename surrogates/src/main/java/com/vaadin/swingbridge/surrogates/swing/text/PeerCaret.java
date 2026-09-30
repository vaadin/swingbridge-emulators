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

import com.vaadin.swingbridge.surrogates.SHelper;

/**
 * The {@link javax.swing.text.Caret} handed out by
 * {@link JTextComponentMixin#getCaret()}, so the standard Swing idiom for
 * repositioning a caret from inside a {@code Document} filter works as written:
 *
 * <pre>{@code
 * int dot = field.getCaret().getDot();
 * field.getCaret().setDot(dot + inserted.length());   // pushes setSelectionRange to the browser
 * }</pre>
 *
 * <p>Dot, mark and the change listeners are real — they read and write the
 * target's caret state, reach the browser, and fire on caret moves in both
 * directions. The rest of the interface is presentation the browser owns (blink
 * rate, visibility, painting) or machinery with no Vaadin counterpart (magic
 * caret position): those WARN per R_vaadin_first and answer with the value a focused HTML
 * input would give.
 *
 * <p>A {@code null} target is the deliberate fallback for a component
 * with no text peer under it — a {@code Div}-peered emulator, or the RTE
 * behind JEditorPane. Every method then WARNs and reads zero. That is the
 * whole point of this class existing: {@code getCaret()} returning
 * {@code null} put an NPE one line into every caller, which R_match_swing_errors's
 * warn-and-continue contract forbids.
 */
public final class PeerCaret implements javax.swing.text.Caret {

    private final JTextComponentMixin target;

    public PeerCaret(JTextComponentMixin target) {
        this.target = target;
    }

    @Override
    public int getDot() {
        if (target == null) return warnAbsent("getDot");
        return target.getCaretPosition();
    }

    @Override
    public int getMark() {
        if (target == null) return warnAbsent("getMark");
        return target.getCaretMark();
    }

    /**
     * Collapses the selection and moves the caret to {@code dot}.
     *
     * @throws IllegalArgumentException if {@code dot} is outside the Document,
     *     matching {@link javax.swing.text.JTextComponent#setCaretPosition}
     */
    @Override
    public void setDot(int dot) {
        if (target == null) { warnAbsent("setDot"); return; }
        target.setCaretPosition(dot);
    }

    /**
     * Moves the caret to {@code dot} while leaving the mark, extending the
     * selection.
     *
     * @throws IllegalArgumentException if {@code dot} is outside the Document
     */
    @Override
    public void moveDot(int dot) {
        if (target == null) { warnAbsent("moveDot"); return; }
        target.moveCaretPosition(dot);
    }

    // --- Browser-owned presentation: answer as a focused HTML input would ---

    /** @return always {@code true} — the browser draws a caret in a focused input, and cannot be told not to. */
    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void setVisible(boolean visible) {
        SHelper.onUnimplemented(self(), "Caret.setVisible", visible);
    }

    /** @return always {@code true} — a selection in an HTML input is always rendered. */
    @Override
    public boolean isSelectionVisible() {
        return true;
    }

    @Override
    public void setSelectionVisible(boolean visible) {
        SHelper.onUnimplemented(self(), "Caret.setSelectionVisible", visible);
    }

    /** @return Swing's own default of 500ms; the real rate is the browser's and is not readable. */
    @Override
    public int getBlinkRate() {
        return 500;
    }

    @Override
    public void setBlinkRate(int rate) {
        SHelper.onUnimplemented(self(), "Caret.setBlinkRate", rate);
    }

    // --- Caret listeners: real, over the mixin's selection bridge ----------

    /** Fires on every dot/mark move, the user's included — they arrive over the mixin's {@code emul-selection} bridge. */
    @Override
    public void addChangeListener(javax.swing.event.ChangeListener l) {
        if (target == null) { warnAbsent("addChangeListener"); return; }
        target.addCaretChangeListener(l);
    }

    @Override
    public void removeChangeListener(javax.swing.event.ChangeListener l) {
        if (target == null) { warnAbsent("removeChangeListener"); return; }
        target.removeCaretChangeListener(l);
    }

    // --- No Vaadin counterpart ---------------------------------------------

    @Override
    public java.awt.Point getMagicCaretPosition() {
        SHelper.onUnimplemented(self(), "Caret.getMagicCaretPosition");
        return null;
    }

    @Override
    public void setMagicCaretPosition(java.awt.Point p) {
        SHelper.onUnimplemented(self(), "Caret.setMagicCaretPosition", p);
    }

    /** User-authored {@code Graphics} paint is out of scope per R_match_swing_errors sub-bucket (b). */
    @Override
    public void paint(java.awt.Graphics g) {
        SHelper.onUnimplemented(self(), "Caret.paint", g);
    }

    // install/deinstall are Swing-internal lifecycle the JDK's own
    // JTextComponent.setCaret drives; nothing in migrated code calls them, and
    // there is no view to attach to. Noop rather than WARN so a migrator who
    // does call one doesn't get a stub WARN for a call that is answered by
    // design.

    @Override
    public void install(javax.swing.text.JTextComponent c) {
        SHelper.onNoop(self(), "Caret.install");
    }

    @Override
    public void deinstall(javax.swing.text.JTextComponent c) {
        SHelper.onNoop(self(), "Caret.deinstall");
    }

    private Object self() {
        return target == null ? this : target;
    }

    private int warnAbsent(String method) {
        SHelper.onUnimplemented(this, "Caret." + method + "/no-text-peer");
        return 0;
    }
}
