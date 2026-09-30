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

package vaadinx.swing.text.html;

import org.junit.jupiter.api.Test;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The worked example of R_java_karibu_tests' one hard requirement: a test whose claim is
 * about <em>Java source resolution</em> must be written in Java, because Kotlin
 * cannot express: Kotlin does not resolve a nested classifier inherited through a
 * superclass, so {@code HTMLEditorKit.ParserCallback} is simply unresolvable there.
 * Since the claim being defended is "migrated Java code still compiles", the test
 * has to be written in the language that code is written in.
 *
 * <p>The claim (D_htmleditorkit): because {@link HTMLEditorKit} <em>extends</em> the JDK kit
 * rather than reimplementing it, headless HTML-scraping code — the pattern that
 * uses this class purely as a parser, with no pane anywhere — keeps compiling and
 * running after the import swap, with no shim types and no rewrite carve-out.
 * These bodies are shaped exactly like the field code they stand in for; if this
 * file stops compiling, that guarantee is gone.
 */
class HTMLEditorKitJavaCompatTest {

    @Test
    void nestedParserCallbackIsAcceptedByTheJdkParserDelegator() throws Exception {
        List<Object> hrefs = new ArrayList<>();
        // Written as a migrator would after the swap: the callback type is reached
        // through OUR kit, and handed to the JDK's own ParserDelegator.
        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                if (t == HTML.Tag.A) {
                    hrefs.add(a.getAttribute(HTML.Attribute.HREF));
                }
            }
        };
        new ParserDelegator().parse(new StringReader("<a href='x.html'>y</a>"), callback, true);

        assertEquals(List.of("x.html"), hrefs);
    }

    @Test
    void nestedParserTypeResolvesThroughOurKit() {
        // HTMLEditorKit.Parser is the abstract type ParserDelegator extends; code
        // that declares a field or parameter of it must still compile.
        HTMLEditorKit.Parser parser = new ParserDelegator();
        assertNotNull(parser);
    }

    @Test
    void nestedHtmlFactoryIsSubclassableThroughOurKit() {
        // The render-only custom-ViewFactory idiom: five apps in the surveyed corpus
        // ship a near-identical copy to stop a report pane loading images.
        javax.swing.text.ViewFactory factory = new HTMLEditorKit() {
            @Override
            public javax.swing.text.ViewFactory getViewFactory() {
                return new HTMLFactory() {
                    @Override
                    public javax.swing.text.View create(javax.swing.text.Element elem) {
                        Object tag = elem.getAttributes()
                                .getAttribute(javax.swing.text.StyleConstants.NameAttribute);
                        return tag == HTML.Tag.IMG ? null : super.create(elem);
                    }
                };
            }
        }.getViewFactory();

        assertNotNull(factory);
    }

    @Test
    void theKitAssignsToAJdkEditorKitVariable() {
        // So pane.setEditorKit(kit) keeps the JDK signature and needs no cast.
        javax.swing.text.EditorKit kit = new HTMLEditorKit();
        assertEquals("text/html", kit.getContentType());
    }
}
