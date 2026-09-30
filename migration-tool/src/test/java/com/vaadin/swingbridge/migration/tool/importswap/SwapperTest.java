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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sharp edges of the two rewrite passes, each pinned by the case that motivated it. */
class SwapperTest {

    private static PortedTypes table;

    @BeforeAll
    static void loadTable() throws IOException {
        table = PortedTypes.load(List.of(Testbed.TABLE));
        assertTrue(table.size() > 100, "swap table looks empty: " + table.size() + " rows");
    }

    private static Swapper swapper() {
        return new Swapper(table, Testbed.noSiblings());
    }

    private static String rewrite(String source) {
        Swapper.Result r = swapper().rewrite(source);
        return r.changed() ? r.text() : source;
    }

    @Test
    @DisplayName("pass 1 swaps in-code fully-qualified references, the NetBeans-generated shape")
    void rewritesQualifiedReferences() {
        String out = rewrite("""
                package com.acme;

                public class Form extends javax.swing.JPanel {
                    void init() {
                        javax.swing.JTextField f = new javax.swing.JTextField();
                        java.awt.Font font = new java.awt.Font("Dialog", 0, 12);
                    }
                }
                """);
        assertTrue(out.contains("extends vaadinx.swing.JPanel"));
        assertTrue(out.contains("new vaadinx.swing.JTextField()"));
        // Font stays JDK — no table row, so no rewrite.
        assertTrue(out.contains("java.awt.Font font = new java.awt.Font("), out);
    }

    @Test
    @DisplayName("a nested type rides its enclosing type's row")
    void rewritesNestedQualifiedReference() {
        String out = rewrite("""
                package com.acme;
                class C { java.awt.Component.BaselineResizeBehavior b; }
                """);
        assertTrue(out.contains("vaadinx.awt.Component.BaselineResizeBehavior"), out);
    }

    @Test
    @DisplayName("a type name inside a string literal is never a reference — the measured false positive")
    void ignoresStringLiterals() {
        String source = """
                package com.acme;

                import javax.swing.UIManager;

                class C {
                    void go() {
                        UIManager.getColor("Button.background");
                        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                        String s = "javax.swing.JButton";
                    }
                }
                """;
        String out = rewrite(source);
        assertTrue(out.contains("\"Button.background\""), out);
        assertTrue(out.contains("\"com.sun.java.swing.plaf.windows.WindowsLookAndFeel\""), out);
        assertTrue(out.contains("\"javax.swing.JButton\""), "a literal must never be rewritten:\n" + out);
        assertFalse(out.contains("import vaadinx.awt.Button;"), "Button came from a string:\n" + out);
    }

    @Test
    @DisplayName("an on-demand import of a rewritable package is deleted and its types re-imported explicitly")
    void expandsWildcard() {
        String out = rewrite("""
                package com.acme;

                import java.awt.*;

                class C {
                    BorderLayout layout;
                    Color color;
                }
                """);
        assertFalse(out.contains("import java.awt.*;"), out);
        assertTrue(out.contains("import vaadinx.awt.BorderLayout;"), out);
        // The stay-JDK type is re-added explicitly: deletion is what makes a missed
        // reference a compile error instead of a silent bind to the JDK type.
        assertTrue(out.contains("import java.awt.Color;"), out);
    }

    @Test
    @DisplayName("List resolves the way javac resolved it — the doc's headline collision")
    void resolvesListLikeJavac() {
        String widget = rewrite("""
                package com.acme;

                import java.awt.*;

                class C { List l; }
                """);
        assertTrue(widget.contains("import vaadinx.awt.List;"), "java.awt.* alone means the widget:\n" + widget);

        String collection = rewrite("""
                package com.acme;

                import java.awt.*;
                import java.util.List;

                class C { List<String> l; }
                """);
        assertTrue(collection.contains("import java.util.List;"), collection);
        assertFalse(collection.contains("vaadinx.awt.List"),
                "an explicit import outranks an on-demand one:\n" + collection);
    }

    @Test
    @DisplayName("an explicit import of a ported date type is swapped, closing the silent timezone drift")
    void swapsExplicitDateImports() {
        String out = rewrite("""
                package com.acme;

                import java.util.Calendar;

                class C { Calendar c = Calendar.getInstance(); }
                """);
        assertTrue(out.contains("import vaadinx.util.Calendar;"), out);
        assertFalse(out.contains("import java.util.Calendar;"),
                "both imports would be a duplicate-name compile error:\n" + out);
    }

    @Test
    @DisplayName("a same-package sibling is never shadowed by an emitted import")
    void doesNotShadowSibling() throws IOException {
        SiblingIndex siblings = Testbed.siblings("com.acme", "Panel");
        String source = """
                package com.acme;

                import java.awt.*;

                class C { Panel p; }
                """;
        Swapper.Result r = new Swapper(table, siblings).rewrite(source);
        String out = r.changed() ? r.text() : source;
        assertFalse(out.contains("vaadinx.awt.Panel"),
                "the app declares its own Panel; emitting the emulator would silently redirect it:\n" + out);
    }

    @Test
    @DisplayName("a javadoc {@link} keeps its import alive")
    void javadocReferenceCountsAsUse() {
        String out = rewrite("""
                package com.acme;

                import javax.swing.JButton;

                /** See {@link JButton}. */
                class C { }
                """);
        assertTrue(out.contains("import vaadinx.swing.JButton;"), out);
    }

    @Test
    @DisplayName("a static import of an emulated type is kept and reported, never guessed at")
    void declinesStaticImport() {
        Swapper.Result r = swapper().rewrite("""
                package com.acme;

                import static javax.swing.JFrame.EXIT_ON_CLOSE;

                class C { int i = EXIT_ON_CLOSE; }
                """);
        assertEquals(1, r.declines().size(), r.declines().toString());
        assertTrue(r.declines().get(0).contains("static import"), r.declines().toString());
    }

    @Test
    @DisplayName("an import this tool cannot read leaves the whole file untouched")
    void declinesUnparsableImport() {
        Swapper.Result r = swapper().rewrite("""
                package com.acme;

                import javax.swing.
                    JButton;

                class C { JButton b; }
                """);
        assertNull(r.text(), "the file must be left untouched, not half-rewritten");
        assertTrue(r.declines().get(0).contains("cannot read the import"), r.declines().toString());
    }

    @Test
    @DisplayName("a second run is a no-op")
    void isIdempotent() {
        String once = rewrite("""
                package com.acme;

                import java.awt.*;
                import javax.swing.JButton;

                class C {
                    BorderLayout layout;
                    Color color;
                    JButton b = new javax.swing.JButton();
                }
                """);
        Swapper.Result second = swapper().rewrite(once);
        assertNull(second.text(), "second run changed the file again:\n" + second.changes());
    }

    @Test
    @DisplayName("a half-swapped tree is tolerated — the state a real migrator runs the tool on")
    void tolerantOfPartialTree() {
        String out = rewrite("""
                package com.acme;

                import vaadinx.swing.JButton;
                import javax.swing.JLabel;

                class C {
                    JButton b;
                    JLabel l;
                }
                """);
        assertTrue(out.contains("import vaadinx.swing.JButton;"), out);
        assertTrue(out.contains("import vaadinx.swing.JLabel;"), out);
        assertFalse(out.contains("javax.swing.JLabel"), out);
    }

    @Test
    @DisplayName("a file with no imports still gets the ones its wildcard-free code needs")
    void noImportBlock() {
        String out = rewrite("""
                package com.acme;
                class C { void go() { new javax.swing.JButton(); } }
                """);
        assertTrue(out.contains("new vaadinx.swing.JButton()"), out);
    }

    @Test
    @DisplayName("java.awt.event splits per type off the flat table — stumbles 3 and 4")
    void mixedEventPackage() {
        String out = rewrite("""
                package com.acme;

                import java.awt.event.*;

                class C {
                    MouseEvent m;
                    ActionEvent a;
                    ActionListener l;
                }
                """);
        assertTrue(out.contains("import vaadinx.awt.event.MouseEvent;"), out);
        assertTrue(out.contains("import java.awt.event.ActionEvent;"), out);
        assertTrue(out.contains("import java.awt.event.ActionListener;"), out);
    }

    @Test
    @DisplayName("an add-on package's unmapped type is reported; an unrelated third-party one is not")
    void reportsAddonCoverageHoles() throws IOException {
        PortedTypes withAddons = PortedTypes.load(List.of(Testbed.TABLE, Testbed.JGOODIES_TABLE));
        Swapper.Result r = new Swapper(withAddons, Testbed.noSiblings()).rewrite("""
                package com.acme;

                import com.jgoodies.forms.builder.PanelBuilder;
                import com.jgoodies.forms.layout.FormLayout;
                import com.formdev.flatlaf.FlatClientProperties;
                import javax.swing.WindowConstants;

                class C {
                    PanelBuilder b;
                    FormLayout l;
                    FlatClientProperties p;
                    int i = WindowConstants.EXIT_ON_CLOSE;
                }
                """);
        assertEquals(1, r.declines().size(), r.declines().toString());
        assertTrue(r.declines().get(0).contains("PanelBuilder"), r.declines().toString());
        // A dependency the migrated app keeps, and a JDK type that stays JDK, are both silent: only
        // an add-on root can leave an import that will not resolve at all.
        assertTrue(r.text().contains("import com.formdev.flatlaf.FlatClientProperties;"), r.text());
        assertTrue(r.text().contains("import javax.swing.WindowConstants;"), r.text());
        assertTrue(r.text().contains("import vaadinx.jgoodies.forms.layout.FormLayout;"), r.text());
    }

    /** Shared fixtures: the checked-in tables, and sibling indexes without touching a real tree. */
    static final class Testbed {

        static final Path TABLE = Path.of("../emulators/src/main/resources/META-INF/emul/ported-types.tsv");

        static final Path JGOODIES_TABLE = Path.of(
                "../third-party/jgoodies-forms-1.2.1/src/main/resources/META-INF/emul/ported-types.tsv");

        static SiblingIndex noSiblings() {
            try {
                return SiblingIndex.scan(List.of());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        static SiblingIndex siblings(String packageName, String typeName) throws IOException {
            Path dir = java.nio.file.Files.createTempDirectory("emul-siblings");
            Path file = dir.resolve(typeName + ".java");
            java.nio.file.Files.writeString(file,
                    "package " + packageName + ";\npublic class " + typeName + " {}\n");
            return SiblingIndex.scan(List.of(dir));
        }
    }
}
