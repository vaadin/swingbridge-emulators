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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selector rewriting for author CSS ({@code StyleSheet.addRule}) onto RTE's content.
 * Pure logic, so this is where the translation is pinned down — the injection it
 * feeds is shadow-DOM/executeJs and therefore browser-verified, not unit-tested.
 */
class RteCssRulesTest {

    private final String root = RteCssRules.CONTENT_ROOT;

    @Test
    @DisplayName("a tag selector becomes a descendant of the content root")
    void aTagSelectorBecomesADescendantOfTheContentRoot() {
        RteCssRules.Translated t = RteCssRules.translate("h2 { color: blue; }");
        assertEquals(List.of(root + " h2 { color: blue; }"), t.rules());
        assertTrue(t.skipped().isEmpty());
    }

    @Test
    @DisplayName("body maps onto the content element itself, not a descendant")
    void bodyMapsOntoTheContentElementItselfNotADescendant() {
        // The single most common rule in the field. ".ql-editor body" would match
        // nothing — there is no <body> inside the editor.
        RteCssRules.Translated t = RteCssRules.translate("body { font-family: sans-serif; }");
        assertEquals(List.of(root + " { font-family: sans-serif; }"), t.rules());
    }

    @Test
    @DisplayName("html maps onto the content element too, case-insensitively")
    void htmlMapsOntoTheContentElementTooCaseInsensitively() {
        assertEquals(List.of(root + " { margin: 0; }"),
                RteCssRules.translate("HTML { margin: 0; }").rules());
    }

    @Test
    @DisplayName("a body-prefixed descendant selector absorbs the body")
    void aBodyPrefixedDescendantSelectorAbsorbsTheBody() {
        assertEquals(List.of(root + " p { line-height: 1.4; }"),
                RteCssRules.translate("body p { line-height: 1.4; }").rules());
    }

    @Test
    @DisplayName("a body child-combinator keeps the combinator")
    void aBodyChildCombinatorKeepsTheCombinator() {
        assertEquals(List.of(root + " > p { margin: 0; }"),
                RteCssRules.translate("body > p { margin: 0; }").rules());
    }

    @Test
    @DisplayName("a selector list is scoped member by member")
    void aSelectorListIsScopedMemberByMember() {
        assertEquals(List.of(root + " h1, " + root + " h2 { color: red; }"),
                RteCssRules.translate("h1, h2 { color: red; }").rules());
    }

    @Test
    @DisplayName("several rules in one call all translate")
    void severalRulesInOneCallAllTranslate() {
        RteCssRules.Translated t = RteCssRules.translate("h1 { color: red } p { color: blue }");
        assertEquals(List.of(root + " h1 { color: red }", root + " p { color: blue }"), t.rules());
    }

    @Test
    @DisplayName("class and id selectors pass through scoped")
    void classAndIdSelectorsPassThroughScoped() {
        RteCssRules.Translated t = RteCssRules.translate(".note { color: grey } #lead { font-weight: bold }");
        assertEquals(List.of(root + " .note { color: grey }", root + " #lead { font-weight: bold }"), t.rules());
    }

    @Test
    @DisplayName("an at-rule is skipped whole, including its nested body")
    void anAtRuleIsSkippedWholeIncludingItsNestedBody() {
        // Depth tracking matters: splitting naively on '}' would emit the inner
        // "p { color: red" fragment as broken CSS.
        RteCssRules.Translated t = RteCssRules.translate("@media screen { p { color: red } } h2 { color: blue }");
        assertEquals(List.of(root + " h2 { color: blue }"), t.rules());
        assertEquals(List.of("@media screen"), t.skipped());
    }

    @Test
    @DisplayName("a selector with no declaration block is reported rather than emitted")
    void aSelectorWithNoDeclarationBlockIsReportedRatherThanEmitted() {
        RteCssRules.Translated t = RteCssRules.translate("h2");
        assertTrue(t.rules().isEmpty());
        assertEquals(List.of("h2"), t.skipped());
    }

    @Test
    @DisplayName("an empty declaration block is dropped without a complaint")
    void anEmptyDeclarationBlockIsDroppedWithoutAComplaint() {
        RteCssRules.Translated t = RteCssRules.translate("h2 { }");
        assertTrue(t.rules().isEmpty(), "nothing to apply");
        assertTrue(t.skipped().isEmpty(), "nothing was lost either, so no WARN is owed");
    }

    @Test
    @DisplayName("null and blank input are no-ops")
    void nullAndBlankInputAreNoOps() {
        assertTrue(RteCssRules.translate(null).rules().isEmpty());
        assertTrue(RteCssRules.translate("   ").rules().isEmpty());
        assertTrue(RteCssRules.translate("   ").skipped().isEmpty());
    }

    @Test
    @DisplayName("an unterminated block still yields a well-formed rule")
    void anUnterminatedBlockStillYieldsAWellFormedRule() {
        // Author CSS is often hand-concatenated; a missing final brace should not
        // throw away the declaration, and what we emit gets closed properly.
        assertEquals(List.of(root + " h2 { color: blue }"),
                RteCssRules.translate("h2 { color: blue").rules());
    }

    @Test
    @DisplayName("a statement at-rule does not swallow the rule after it")
    void aStatementAtRuleDoesNotSwallowTheRuleAfterIt() {
        // @import has no block, so scanning ahead to the next '{' would consume the
        // following selector along with it.
        RteCssRules.Translated t = RteCssRules.translate("@import url(other.css); h2 { color: blue }");
        assertEquals(List.of(root + " h2 { color: blue }"), t.rules());
        assertEquals(List.of("@import url(other.css)"), t.skipped());
    }
}
