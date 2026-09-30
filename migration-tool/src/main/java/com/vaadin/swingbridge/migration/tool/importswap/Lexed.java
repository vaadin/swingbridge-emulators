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

package com.vaadin.swingbridge.migration.tool.importswap;

/**
 * Classifies every character of a Java source file as code, comment or literal, so the rewriters can
 * scan and edit text without an AST while never touching a string:
 *
 * <pre>{@code
 * Lexed lex = Lexed.of(source);
 * lex.isCode(i);      // false inside "..." , '.' , // ... and /* ... *\/
 * lex.codeOnly();     // same length as source, comments and literals blanked to spaces
 * }</pre>
 *
 * <p>Masking is <em>in place and length-preserving</em> — blanked to spaces, newlines kept — because
 * pass 1 edits the original text at offsets taken from a masked view. Deleting the masked regions
 * instead would shift every offset after the first string literal.
 *
 * <p>Immutable.
 *
 * <p>The blanking is what stops {@code UIManager.getColor("Button.background")} from being
 * read as a reference to {@code java.awt.Button}, and
 * {@code setLookAndFeel("com.sun...WindowsLookAndFeel")} from being rewritten — measured
 * false positives, not hypothetical ones. Comments are classified separately from literals
 * rather than lumped in with them: a javadoc {@code {@link java.awt.Component}} <em>should</em>
 * be rewritten by pass 1, while prose mentioning a type should not conjure an import.
 */
final class Lexed {

    static final byte CODE = 0;
    static final byte COMMENT = 1;
    static final byte LITERAL = 2;

    private final String source;
    private final byte[] kind;

    private Lexed(String source, byte[] kind) {
        this.source = source;
        this.kind = kind;
    }

    static Lexed of(String source) {
        byte[] kind = new byte[source.length()];
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                i = mark(kind, source, i, lineCommentEnd(source, i), COMMENT);
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = mark(kind, source, i, end < 0 ? n : end + 2, COMMENT);
            } else if (c == '"' && source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                i = mark(kind, source, i, end < 0 ? n : end + 3, LITERAL);
            } else if (c == '"') {
                i = mark(kind, source, i, quotedEnd(source, i, '"'), LITERAL);
            } else if (c == '\'') {
                i = mark(kind, source, i, quotedEnd(source, i, '\''), LITERAL);
            } else {
                i++;
            }
        }
        return new Lexed(source, kind);
    }

    private static int mark(byte[] kind, String source, int from, int to, byte as) {
        int end = Math.min(to, source.length());
        for (int i = from; i < end; i++) {
            kind[i] = as;
        }
        return end;
    }

    private static int lineCommentEnd(String source, int from) {
        int nl = source.indexOf('\n', from);
        return nl < 0 ? source.length() : nl;
    }

    /** End of a quoted run, one past the closing quote; escapes consume the next character. */
    private static int quotedEnd(String source, int from, char quote) {
        int i = from + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (c == '\n') {
                // Unterminated — a malformed file, or our lexer lost sync. Stop at the line
                // rather than swallowing the rest of the file into one literal.
                return i;
            } else {
                i++;
            }
        }
        return source.length();
    }

    boolean isCode(int offset) {
        return kind[offset] == CODE;
    }

    boolean isComment(int offset) {
        return kind[offset] == COMMENT;
    }

    /** The source with comments and literals blanked — the view the simple-name scan reads. */
    String codeOnly() {
        return blanked(k -> k == CODE);
    }

    /** The source with literals blanked but comments intact — the view pass 1 rewrites over. */
    String codeAndComments() {
        return blanked(k -> k != LITERAL);
    }

    /** The source with only comments left — where {@code {@link Foo}} references are harvested. */
    String commentsOnly() {
        return blanked(k -> k == COMMENT);
    }

    private String blanked(java.util.function.IntPredicate keep) {
        char[] out = source.toCharArray();
        for (int i = 0; i < out.length; i++) {
            if (!keep.test(kind[i]) && out[i] != '\n') {
                out[i] = ' ';
            }
        }
        return new String(out);
    }
}
