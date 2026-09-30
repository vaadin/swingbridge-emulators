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

package vaadinx.swing;

import com.vaadin.flow.component.Component;
import com.vaadin.swingbridge.surrogates.SJFormattedTextField;

import javax.swing.JFormattedTextField.AbstractFormatter;
import java.text.ParseException;

/**
 * D_jformattedtextfield default / fallback strategy — peer is {@link SJFormattedTextField}
 * (which extends Vaadin {@link com.vaadin.flow.component.textfield.TextField}
 * via {@link com.vaadin.swingbridge.surrogates.SJTextField}). The catch-all: used when no formatter
 * is set, when the formatter is a {@code DefaultFormatter}, or for any formatter
 * family the dedicated strategies (Date / Integer / Long / Number / Mask) don't claim.
 *
 * <p>Round-trip semantics:
 * <ul>
 *   <li><b>{@link #afterSetValue}</b> — runs the formatter's
 *       {@code valueToString} when present, falling back to
 *       {@code Object.toString} otherwise. The result is pushed to
 *       {@link com.vaadin.swingbridge.surrogates.SJTextField#setValue setValue(String)}, which
 *       JTextComponentMixin's R_swing_is_truth sync mirrors back to the emulator's
 *       Document — so {@code field.getText()} reads the formatted text
 *       and Document mutation events fire to user-registered
 *       DocumentListeners.</li>
 *   <li><b>{@link #install}</b> — no peer-side bridge listener. The
 *       Object value field on {@link JFormattedTextField} updates only
 *       through explicit user code calls
 *       ({@link JFormattedTextField#setValue}) or
 *       {@link JFormattedTextField#commitEdit} (which the emulator's
 *       overridden {@code fireActionPerformed} runs on Enter). Browser-typed
 *       input doesn't continuously update the Object value — only the
 *       displayed text via the inherited Document↔peer sync. This matches
 *       JDK JFormattedTextField semantics: typing changes the text, only
 *       a commit (Enter / focus-loss with COMMIT*) updates the value.</li>
 * </ul>
 *
 * <p>{@link #accepts} returns {@code true} for any formatter — this is the
 * catch-all per D_formatted_strategy_interface's resolution table. A field constructed under
 * DateStrategy that gets a non-Date formatter installed via
 * {@code setFormatter} would WARN-and-keep DateStrategy per D_formatter_swap_rules (c);
 * the cross-family-from-DefaultStrategy direction (DefaultStrategy →
 * DateStrategy) is symmetric — DateStrategy.accepts returns {@code false}
 * for DefaultFormatter, so a default-constructed field setting a
 * DateFormatter via setFormatter doesn't transition to DateStrategy.
 * That's correct: peer pinning is the entire point of D_formatter_is_framework_config.
 */
final class DefaultFormattedStrategy implements FormattedFieldStrategy {

    static final DefaultFormattedStrategy INSTANCE = new DefaultFormattedStrategy();

    private DefaultFormattedStrategy() {}

    @Override
    public Component createPeer() {
        return new SJFormattedTextField();
    }

    @Override
    public void install(JFormattedTextField field, Component peer) {
        // No peer-side value bridge — see class javadoc. The R_swing_is_truth
        // Document↔peer text sync is inherited from JTextComponentMixin
        // and runs without strategy involvement.
    }

    @Override
    public void afterSetValue(JFormattedTextField field, Component peer, Object value) {
        SJFormattedTextField tf = (SJFormattedTextField) peer;
        String text = stringifyViaFormatter(field, value);
        tf.setValue(text);
    }

    /**
     * Run {@code formatter.valueToString} when a formatter is installed;
     * fall back to {@code Object.toString} (or empty string for null)
     * otherwise. ParseException from valueToString degrades to toString
     * with a WARN — the formatter rejected the value type, but we'd
     * rather show something than crash the form.
     */
    private static String stringifyViaFormatter(JFormattedTextField field, Object value) {
        AbstractFormatter formatter = field.getFormatter();
        if (formatter == null) {
            return value == null ? "" : value.toString();
        }
        try {
            return formatter.valueToString(value);
        } catch (ParseException e) {
            vaadinx.EHelper.onUnimplemented("JFormattedTextField",
                    "valueToString rejected value (degrading to toString)", value);
            return value == null ? "" : value.toString();
        }
    }

    @Override
    public boolean accepts(AbstractFormatter formatter) {
        // Catch-all — accepts anything other strategies don't claim.
        return true;
    }

    @Override
    public Class<?> getValueClass() {
        return String.class;
    }
}
