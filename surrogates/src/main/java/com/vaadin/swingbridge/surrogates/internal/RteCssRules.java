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

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites author CSS (as handed to {@code HTMLEditorKit}'s
 * {@code StyleSheet.addRule}) into rules that apply to RTE's rendered content.
 *
 * <p>Two transformations, both forced by where the content actually lives. RTE
 * renders the pane's HTML as real elements inside a {@code .ql-editor} div in the
 * component's shadow root, so every selector is confined under that class —
 * otherwise a rule like {@code button { … }} would also hit the editor toolbar.
 * And the document root selectors {@code body} / {@code html} have no counterpart
 * inside the editor, so they map onto the content element itself rather than
 * becoming a descendant selector that matches nothing — which matters because
 * {@code body { font-family: … }} is the single most common rule in the field.
 *
 * <p>Scoping to one pane needs no help from the selector: the stylesheet is
 * injected into that instance's own shadow root, which is already a style
 * boundary. (A {@code :host([theme~="…"])} prefix would be required only for the
 * static {@code themeFor} delivery shape, where one stylesheet is shared.)
 *
 * <p>At-rules ({@code @media}, {@code @import}, {@code @font-face}) are skipped
 * whole, nested body included, and reported so the caller can WARN — emitting a
 * half-parsed {@code @media} block would inject broken CSS rather than none.
 */
public final class RteCssRules {

    /** The element RTE renders pane content into, inside its shadow root. */
    public static final String CONTENT_ROOT = ".ql-editor";

    private RteCssRules() {}

    /**
     * @param rules   translated rules, ready to concatenate into a {@code <style>}
     * @param skipped selector text of each rule that could not be translated
     */
    public record Translated(List<String> rules, List<String> skipped) {}

    /**
     * Translate a CSS fragment. Accepts anything {@code StyleSheet.addRule} does —
     * a single rule or a whole stylesheet's worth.
     */
    public static Translated translate(String css) {
        List<String> rules = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (css == null || css.isBlank()) {
            return new Translated(rules, skipped);
        }

        int i = 0;
        int n = css.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(css.charAt(i))) i++;
            if (i >= n) break;

            // A statement at-rule (@import/@charset/@namespace) ends at its
            // semicolon and has no block. It has to be consumed on its own terms:
            // scanning to the next '{' would swallow the following rule's selector
            // along with it, losing a rule we could have applied.
            if (css.charAt(i) == '@') {
                int semi = css.indexOf(';', i);
                int nextBrace = css.indexOf('{', i);
                if (semi >= 0 && (nextBrace < 0 || semi < nextBrace)) {
                    skipped.add(css.substring(i, semi).trim());
                    i = semi + 1;
                    continue;
                }
            }

            int brace = css.indexOf('{', i);
            if (brace < 0) {
                String trailing = css.substring(i).trim();
                if (!trailing.isEmpty()) {
                    skipped.add(trailing);   // selector with no declaration block
                }
                break;
            }
            String selectors = css.substring(i, brace).trim();

            // Consume the block, tracking depth so a nested at-rule body is
            // swallowed as one unit rather than split at its first '}'.
            int depth = 1;
            int j = brace + 1;
            while (j < n && depth > 0) {
                char c = css.charAt(j);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                j++;
            }
            String body = css.substring(brace + 1, depth == 0 ? j - 1 : n).trim();
            i = j;

            if (selectors.isEmpty() || selectors.startsWith("@")) {
                skipped.add(selectors.isEmpty() ? "{" + body + "}" : selectors);
                continue;
            }
            if (body.isEmpty()) {
                continue;   // empty declaration block — nothing to emit, nothing lost
            }

            List<String> scoped = new ArrayList<>();
            for (String selector : selectors.split(",")) {
                String s = selector.trim();
                if (!s.isEmpty()) {
                    scoped.add(scope(s));
                }
            }
            if (scoped.isEmpty()) {
                skipped.add(selectors);
            } else {
                rules.add(String.join(", ", scoped) + " { " + body + " }");
            }
        }
        return new Translated(rules, skipped);
    }

    /**
     * Confine one selector under the content root. A leading {@code body} /
     * {@code html} is absorbed into the root rather than kept as a descendant:
     * {@code body} alone becomes the root, {@code body p} becomes {@code root p}.
     */
    private static String scope(String selector) {
        String s = selector;
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        for (String root : new String[] {"body", "html"}) {
            if (lower.equals(root)) {
                return CONTENT_ROOT;
            }
            if (lower.startsWith(root + " ") || lower.startsWith(root + ">")) {
                s = s.substring(root.length()).trim();
                lower = s.toLowerCase(java.util.Locale.ROOT);
            }
        }
        if (s.isEmpty()) {
            return CONTENT_ROOT;
        }
        // A child-combinator remainder ("> p", left by "body > p") reads correctly
        // as-is: ".ql-editor > p".
        return CONTENT_ROOT + " " + s;
    }
}
