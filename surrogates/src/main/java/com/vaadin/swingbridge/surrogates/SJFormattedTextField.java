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

import javax.swing.JFormattedTextField;

/**
 * Surrogate for {@link javax.swing.JFormattedTextField} when the emulator's
 * formatter dispatch lands on {@link com.vaadin.swingbridge.surrogates.SJFormattedTextField}'s
 * peer family per [SD_sjformatted_family](../../../../../decisions.md#SD_sjformatted_family) — the catch-all
 * strategy (DefaultFormatter / no formatter / cross-family fallback) and
 * the MaskFormatter strategy (browser-side {@code pattern} attribute).
 *
 * <p>Extends {@link SJTextField} (NOT just {@link com.vaadin.flow.component.textfield.TextField})
 * so the JTextField-shape surface — columns, horizontalAlignment,
 * actionCommand, ActionListener-on-Enter via {@link SJTextField}'s key
 * wiring, JTextComponentMixin Document↔peer-text sync, setAction
 * propagation — is inherited rather than duplicated. Per
 * [SD_formatted_no_base_class](../../../../../decisions.md#SD_formatted_no_base_class) the JTextField surface is
 * "duplicated per surrogate" only because Vaadin's parallel-not-shared
 * peer hierarchy forces it for non-TextField peers (DatePicker /
 * NumberField / IntegerField / LongField). When the peer IS-A TextField
 * (this case), straight inheritance from SJTextField is the cleaner
 * shape — no duplication, no copy-paste, the SJTextField surface is
 * available to migrators directly.
 *
 * <p>JFormattedTextField-specific surface today:
 * <ul>
 *   <li>{@link #getFocusLostBehavior} / {@link #setFocusLostBehavior} —
 *       round-trip with R_match_swing_errors IAE on bad int. The actual focus-lost commit
 *       logic lives on the emulator-side strategy (D_jformattedtextfield) — surrogate stores
 *       the int so emulator code reads it back and {@link JFormattedTextField}
 *       getter round-trips.</li>
 * </ul>
 *
 * <p>{@code focusLostBehavior} + the COMMIT/etc constants stay inline rather
 * than in a shared mixin: the surface (1 field + 4 constants) is too small
 * across the formatted-field family to warrant the abstraction (R_infra_not_surface).
 */
public class SJFormattedTextField extends SJTextField {

    /** Mirrors {@link JFormattedTextField#COMMIT}. */
    public static final int COMMIT           = JFormattedTextField.COMMIT;
    public static final int COMMIT_OR_REVERT = JFormattedTextField.COMMIT_OR_REVERT;
    public static final int REVERT           = JFormattedTextField.REVERT;
    public static final int PERSIST          = JFormattedTextField.PERSIST;

    /** JDK default per JFormattedTextField javadoc. */
    private int focusLostBehavior = COMMIT_OR_REVERT;

    public SJFormattedTextField() {
        super();
        noteClientCommits();
    }

    public SJFormattedTextField(String text) {
        super(text);
        noteClientCommits();
    }

    private void noteClientCommits() {
        addValueChangeListener(e -> {
            if (e.isFromClient()) EnterClaims.noteClientCommit(this);
        });
    }

    /**
     * Enter commits an edit and only an unedited field lets it through — Swing's
     * {@code CommitAction}, enabled by {@code isEdited()} rather than by listeners.
     */
    @Override
    public boolean claimsEnter() {
        return EnterClaims.committedThisRoundTrip(this);
    }

    /**
     * Set or clear the HTML {@code pattern} attribute for browser-side
     * regex validation. Used by emulator-side {@code MaskStrategy} (D_formatted_strategy_interface)
     * to push the converted MaskFormatter mask through to the input
     * element. {@code null} clears the attribute.
     *
     * <p>Cross-package access from {@code vaadinx.swing.MaskStrategy}
     * requires public visibility. Pure-surrogate users should compose
     * with Vaadin's standard validation API instead — this method exists
     * for the emulator-side bridge per
     * [SD_maskformatter_regex](../../../../../decisions.md#SD_maskformatter_regex).
     */
    public void setBrowserPattern(String regex) {
        if (regex == null) {
            getElement().removeAttribute("pattern");
        } else {
            getElement().setAttribute("pattern", regex);
        }
    }

    /** Read-back of the browser pattern; for diagnostics + tests. */
    public String getBrowserPattern() {
        return getElement().getAttribute("pattern");
    }

    public int getFocusLostBehavior() {
        return focusLostBehavior;
    }

    public void setFocusLostBehavior(int behavior) {
        if (behavior != COMMIT && behavior != COMMIT_OR_REVERT
                && behavior != REVERT && behavior != PERSIST) {
            throw new IllegalArgumentException(
                    "setFocusLostBehavior must be one of: JFormattedTextField.COMMIT, "
                            + "COMMIT_OR_REVERT, PERSIST or REVERT");
        }
        this.focusLostBehavior = behavior;
        // No "focusLostBehavior" property change: the JDK's
        // JFormattedTextField.setFocusLostBehavior validates the argument,
        // assigns the field and returns (SD_property_fanout_audit).
    }

    /**
     * Override the L&amp;F class id to match JDK JFormattedTextField (which
     * returns {@code "FormattedTextFieldUI"}, distinct from JTextField's
     * {@code "TextFieldUI"}). Pure cosmetic for L&amp;F-aware UIManager
     * lookups; we don't dispatch on the class id.
     */
    @Override
    public String getUIClassID() {
        return "FormattedTextFieldUI";
    }
}
