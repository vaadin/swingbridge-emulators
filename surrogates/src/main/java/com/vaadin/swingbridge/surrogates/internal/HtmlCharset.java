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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which charset a fetched HTML page is encoded in.
 *
 * <p>Order of evidence, matching what browsers do and what
 * {@code JEditorPane.setPage} is expected to honour: the {@code charset}
 * parameter of the {@code Content-Type} response header, then a {@code <meta>}
 * declaration in the markup, then UTF-8.
 *
 * <p>The header parameter is deliberately <em>not</em> read from
 * {@code URLConnection.getContentEncoding()} — that returns the
 * {@code Content-Encoding} header, which carries the <em>compression</em>
 * ({@code gzip}, {@code deflate}) and is unrelated to the charset. It is null on
 * an ordinary response even when {@code Content-Type} declares a charset, so
 * using it means silently decoding every non-UTF-8 page as UTF-8.
 */
public final class HtmlCharset {

    // charset=..., optionally quoted, in a Content-Type header.
    private static final Pattern HEADER_CHARSET =
            Pattern.compile("(?i)\\bcharset\\s*=\\s*\"?([^\";\\s]+)\"?");

    // <meta charset="..."> and the older <meta http-equiv content="...; charset=...">.
    private static final Pattern META_CHARSET =
            Pattern.compile("(?i)<meta[^>]*charset\\s*=\\s*[\"']?([a-z0-9_:.+-]+)");

    /** How far into the document to look for a {@code <meta>} charset. */
    private static final int SNIFF_LIMIT = 4096;

    private HtmlCharset() {}

    /**
     * @param contentTypeHeader the {@code Content-Type} response header, or {@code null}
     * @param body              the fetched bytes; only the head is inspected
     * @return the charset to decode with; never {@code null}
     */
    public static Charset detect(String contentTypeHeader, byte[] body) {
        Charset fromHeader = parse(charsetParam(contentTypeHeader));
        if (fromHeader != null) {
            return fromHeader;
        }
        Charset fromMeta = parse(metaCharset(body));
        return fromMeta != null ? fromMeta : StandardCharsets.UTF_8;
    }

    /** The {@code charset} parameter of a Content-Type header, or {@code null}. */
    public static String charsetParam(String contentTypeHeader) {
        if (contentTypeHeader == null) return null;
        Matcher m = HEADER_CHARSET.matcher(contentTypeHeader);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Whether the header's media type is HTML. Used to WARN when a page is served
     * as something else, since the pane renders it as HTML regardless.
     */
    public static boolean isHtmlMediaType(String contentTypeHeader) {
        if (contentTypeHeader == null) return true;   // unstated: assume the pane's own type
        String mediaType = contentTypeHeader.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.isEmpty() || mediaType.equals("text/html")
                || mediaType.equals("application/xhtml+xml");
    }

    private static String metaCharset(byte[] body) {
        if (body == null || body.length == 0) return null;
        // Decoded as ISO-8859-1 because it is byte-preserving: the declaration we are
        // looking for is ASCII, and this cannot throw on arbitrary bytes the way a
        // strict UTF-8 decode would.
        String head = new String(body, 0, Math.min(body.length, SNIFF_LIMIT),
                StandardCharsets.ISO_8859_1);
        Matcher m = META_CHARSET.matcher(head);
        return m.find() ? m.group(1) : null;
    }

    private static Charset parse(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Charset.forName(name.trim());
        } catch (IllegalArgumentException e) {
            return null;   // unknown or malformed name — fall through to the next source
        }
    }
}
