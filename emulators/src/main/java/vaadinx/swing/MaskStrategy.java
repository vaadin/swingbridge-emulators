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
import javax.swing.text.MaskFormatter;
import java.text.ParseException;

/**
 * D_jformattedtextfield strategy for {@link MaskFormatter}. Peer is
 * {@link SJFormattedTextField} (Vaadin TextField via the catch-all
 * surrogate); the mask string is converted to a regex and pushed to the
 * peer's {@code pattern} attribute for browser-side validation per
 * [SD_maskformatter_regex](../../../../../surrogates/decisions.md#SD_maskformatter_regex).
 *
 * <h2>Mask → regex conversion</h2>
 *
 * <p>JDK MaskFormatter mask characters map to regex character classes:
 * <ul>
 *   <li>{@code #} → digit ({@code [0-9]})</li>
 *   <li>{@code U} → letter, uppercased ({@code [a-zA-Z]} — case-coercion
 *       not enforced browser-side; commitEdit could uppercase server-side
 *       but doesn't today)</li>
 *   <li>{@code L} → letter, lowercased ({@code [a-zA-Z]} — same caveat)</li>
 *   <li>{@code A} → alphanumeric ({@code [a-zA-Z0-9]})</li>
 *   <li>{@code ?} → letter ({@code [a-zA-Z]})</li>
 *   <li>{@code *} → any character ({@code .})</li>
 *   <li>{@code H} → hex digit ({@code [0-9a-fA-F]})</li>
 *   <li>Anything else: literal — regex-escaped if it's a metachar.</li>
 * </ul>
 *
 * <p>The {@code '} (single-quote) literal-escape mask char per JDK
 * MaskFormatter is NOT modeled — rare in practice (most masks
 * use literal punctuation directly: e.g. {@code "###-###-####"} for a
 * phone number). Per R_match_swing_errors (c) drop-and-WARN.
 *
 * <p>Other MaskFormatter configuration (setValidCharacters,
 * setInvalidCharacters, setOverwriteMode, setPlaceholderCharacter) is not
 * routed through to the browser — the regex pattern is the only client-side
 * validation surface. Migrators who depend on those should validate
 * server-side (commitEdit will catch invalid input via the formatter's
 * stringToValue).
 */
final class MaskStrategy implements FormattedFieldStrategy {

    static final MaskStrategy INSTANCE = new MaskStrategy();

    private MaskStrategy() {}

    @Override
    public Component createPeer() {
        return new SJFormattedTextField();
    }

    @Override
    public Class<? extends Component> peerType() {
        return SJFormattedTextField.class;
    }

    @Override
    public void install(JFormattedTextField field, Component peer) {
        // No peer-side bridge — MaskFormatter's value type is String, so
        // the inherited Document↔peer-text sync covers the typing path.
        // commitEdit consults the formatter for stringToValue parsing.
    }

    @Override
    public String documentText(JFormattedTextField field, Object value) {
        AbstractFormatter formatter = field.getFormatter();
        if (formatter == null) {
            return value == null ? "" : value.toString();
        }
        try {
            return formatter.valueToString(value);
        } catch (ParseException e) {
            vaadinx.EHelper.onUnimplemented("JFormattedTextField",
                    "MaskFormatter.valueToString rejected value (degrading to toString)", value);
            return value == null ? "" : value.toString();
        }
    }

    /** The mask's browser pattern; the text reaches the peer through the Document sync. */
    @Override
    public void afterSetValue(JFormattedTextField field, Component peer, Object value) {
        if (field.getFormatter() instanceof MaskFormatter mf) {
            ((SJFormattedTextField) peer).setBrowserPattern(maskToRegex(mf.getMask()));
        }
    }

    @Override
    public boolean accepts(AbstractFormatter formatter) {
        return formatter instanceof MaskFormatter;
    }

    @Override
    public Class<?> getValueClass() {
        return String.class;
    }

    /**
     * Convert a JDK MaskFormatter mask string to a browser-side regex.
     * Anchors with {@code ^...$} so the pattern attribute matches the
     * whole input. Returns {@code null} for null/empty mask (no regex
     * filter applied).
     */
    static String maskToRegex(String mask) {
        if (mask == null || mask.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(mask.length() * 4);
        sb.append('^');
        for (int i = 0; i < mask.length(); i++) {
            char c = mask.charAt(i);
            switch (c) {
                case '#' -> sb.append("[0-9]");
                case 'U', 'L', '?' -> sb.append("[a-zA-Z]");
                case 'A' -> sb.append("[a-zA-Z0-9]");
                case '*' -> sb.append('.');
                case 'H' -> sb.append("[0-9a-fA-F]");
                case '\'' -> {
                    // Literal-escape for next char — not modeled: drop the
                    // quote and pass through next char as literal. WARN once
                    // at conversion time.
                    vaadinx.EHelper.onUnimplemented("MaskStrategy",
                            "MaskFormatter literal-escape ' (not modeled)", mask);
                    if (i + 1 < mask.length()) {
                        appendLiteral(sb, mask.charAt(++i));
                    }
                }
                default -> appendLiteral(sb, c);
            }
        }
        sb.append('$');
        return sb.toString();
    }

    private static void appendLiteral(StringBuilder sb, char c) {
        // Regex metachar set: . * + ? ^ $ ( ) [ ] { } | \
        if (".*+?^$()[]{}|\\".indexOf(c) >= 0) {
            sb.append('\\').append(c);
        } else {
            sb.append(c);
        }
    }
}
