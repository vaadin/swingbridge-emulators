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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Charset detection for {@code setPage}. The regression this pins down: the charset was
 * previously read from {@code URLConnection.getContentEncoding()}, which is the
 * <em>compression</em> header and is null on an ordinary response — so every page
 * declaring a non-UTF-8 charset was silently decoded as UTF-8 and mangled.
 */
class HtmlCharsetTest {

    private final Charset latin1 = StandardCharsets.ISO_8859_1;

    @Test
    @DisplayName("the Content-Type charset parameter wins")
    void theContentTypeCharsetParameterWins() {
        assertEquals(latin1, HtmlCharset.detect("text/html; charset=ISO-8859-1", new byte[0]));
    }

    @Test
    @DisplayName("the charset parameter is matched case-insensitively and unquoted")
    void theCharsetParameterIsMatchedCaseInsensitivelyAndUnquoted() {
        assertEquals(latin1, HtmlCharset.detect("text/html; CHARSET=\"iso-8859-1\"", new byte[0]));
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.detect("text/html;charset=utf-8", new byte[0]));
    }

    @Test
    @DisplayName("a meta charset declaration is used when the header is silent")
    void aMetaCharsetDeclarationIsUsedWhenTheHeaderIsSilent() {
        byte[] body = "<html><head><meta charset=\"ISO-8859-1\"></head><body>x</body></html>"
                .getBytes(StandardCharsets.US_ASCII);
        assertEquals(latin1, HtmlCharset.detect("text/html", body));
    }

    @Test
    @DisplayName("the older http-equiv meta form is recognised")
    void theOlderHttpEquivMetaFormIsRecognised() {
        byte[] body = ("<html><head><meta http-equiv=\"Content-Type\" "
                + "content=\"text/html; charset=iso-8859-1\"></head></html>")
                .getBytes(StandardCharsets.US_ASCII);
        assertEquals(latin1, HtmlCharset.detect(null, body));
    }

    @Test
    @DisplayName("the header beats the meta declaration when they disagree")
    void theHeaderBeatsTheMetaDeclarationWhenTheyDisagree() {
        byte[] body = "<meta charset=\"ISO-8859-1\">".getBytes(StandardCharsets.US_ASCII);
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.detect("text/html; charset=utf-8", body));
    }

    @Test
    @DisplayName("UTF-8 is the fallback when nothing declares a charset")
    void utf8IsTheFallbackWhenNothingDeclaresACharset() {
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.detect(null, new byte[0]));
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.detect("text/html", "<p>x</p>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("an unknown charset name falls through instead of throwing")
    void anUnknownCharsetNameFallsThroughInsteadOfThrowing() {
        // "gzip" is what the old getContentEncoding()-based code could hand in.
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.detect("text/html; charset=gzip", new byte[0]));
    }

    @Test
    @DisplayName("sniffing arbitrary bytes does not throw")
    void sniffingArbitraryBytesDoesNotThrow() {
        // Byte sequences that are invalid UTF-8 must not break the meta sniff.
        byte[] body = {-1, -2, -3, 0x3C, 0x70, 0x3E};
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.detect(null, body));
    }

    @Test
    @DisplayName("charsetParam returns null when absent")
    void charsetParamReturnsNullWhenAbsent() {
        assertNull(HtmlCharset.charsetParam("text/html"));
        assertNull(HtmlCharset.charsetParam(null));
        assertEquals("utf-8", HtmlCharset.charsetParam("text/html; charset=utf-8"));
    }

    @Test
    @DisplayName("media type check accepts html and treats an absent header as fine")
    void mediaTypeCheckAcceptsHtmlAndTreatsAnAbsentHeaderAsFine() {
        assertTrue(HtmlCharset.isHtmlMediaType("text/html; charset=utf-8"));
        assertTrue(HtmlCharset.isHtmlMediaType("application/xhtml+xml"));
        assertTrue(HtmlCharset.isHtmlMediaType(null), "unstated should not WARN");
        assertFalse(HtmlCharset.isHtmlMediaType("text/plain"));
        assertFalse(HtmlCharset.isHtmlMediaType("application/json"));
    }
}
