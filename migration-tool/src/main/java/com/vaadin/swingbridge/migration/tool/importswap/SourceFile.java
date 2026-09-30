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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One {@code .java} file, split into the three regions the rewriters need: its package, its import
 * lines, and everything else.
 *
 * <pre>{@code
 * SourceFile f = SourceFile.parse(Files.readString(path));
 * f.packageName();                     // => "com.acme.ui"
 * f.imports().get(0).fqn();            // => "javax.swing.JButton"
 * f.imports().get(0).onDemand();       // => false
 * }</pre>
 *
 * <p>Import statements are matched line-wise. The JLS permits whitespace and newlines around the
 * dots of an import, so a file whose {@code import} keyword does not match is reported and left
 * untouched rather than half-rewritten — see {@link #unparsableImport()}.
 *
 * <p>Immutable.
 */
final class SourceFile {

    /** {@code import [static] some.qualified.Name[.*];} — the shape that covers real-world source. */
    private static final Pattern IMPORT =
            Pattern.compile("^[ \t]*import[ \t]+(static[ \t]+)?([\\w.]+(?:\\.\\*)?)[ \t]*;[ \t]*$");

    /** Any line whose first token is {@code import}, to catch the shapes {@link #IMPORT} rejects. */
    private static final Pattern IMPORT_KEYWORD = Pattern.compile("^[ \t]*import[ \t\n]");

    private static final Pattern PACKAGE = Pattern.compile("^[ \t]*package[ \t]+([\\w.]+)[ \t]*;[ \t]*$");

    /** One import statement, and where its line sits in the file. */
    record Import(String fqn, boolean isStatic, boolean onDemand, int lineIndex, String text) {

        /** The package for an on-demand import; the enclosing package for a single-type one. */
        String packageName() {
            return onDemand ? fqn : fqn.substring(0, fqn.lastIndexOf('.'));
        }

        /** The imported simple name, or {@code "*"} for an on-demand import. */
        String simpleName() {
            return onDemand ? "*" : fqn.substring(fqn.lastIndexOf('.') + 1);
        }
    }

    private final String source;
    private final List<String> lines;
    private final String packageName;
    private final List<Import> imports;
    private final int packageLineIndex;
    private final String unparsableImport;

    private SourceFile(String source, List<String> lines, String packageName, List<Import> imports,
            int packageLineIndex, String unparsableImport) {
        this.source = source;
        this.lines = lines;
        this.packageName = packageName;
        this.imports = imports;
        this.packageLineIndex = packageLineIndex;
        this.unparsableImport = unparsableImport;
    }

    static SourceFile parse(String source) {
        Lexed lex = Lexed.of(source);
        // Scan the code-only view: an "import" inside a comment or a string is not a statement.
        List<String> codeLines = List.of(lex.codeOnly().split("\n", -1));
        List<String> rawLines = List.of(source.split("\n", -1));

        String pkg = "";
        int pkgLine = -1;
        List<Import> imports = new ArrayList<>();
        String unparsable = null;

        for (int i = 0; i < codeLines.size(); i++) {
            String line = codeLines.get(i);
            Matcher p = PACKAGE.matcher(line);
            if (pkgLine < 0 && p.matches()) {
                pkg = p.group(1);
                pkgLine = i;
                continue;
            }
            Matcher m = IMPORT.matcher(line);
            if (m.matches()) {
                String fqn = m.group(2);
                boolean onDemand = fqn.endsWith(".*");
                imports.add(new Import(onDemand ? fqn.substring(0, fqn.length() - 2) : fqn,
                        m.group(1) != null, onDemand, i, rawLines.get(i)));
            } else if (unparsable == null && IMPORT_KEYWORD.matcher(line).find()) {
                unparsable = rawLines.get(i).strip();
            }
        }
        return new SourceFile(source, rawLines, pkg, imports, pkgLine, unparsable);
    }

    String source() {
        return source;
    }

    List<String> lines() {
        return lines;
    }

    String packageName() {
        return packageName;
    }

    List<Import> imports() {
        return imports;
    }

    /** The first {@code import} line this parser could not read, or null when all parsed. */
    String unparsableImport() {
        return unparsableImport;
    }

    /** Line index of the first import, or just after the package declaration when there are none. */
    int importBlockStart() {
        return imports.isEmpty() ? packageLineIndex + 1 : imports.get(0).lineIndex();
    }

    /** One past the last import line, or {@link #importBlockStart()} when there are none. */
    int importBlockEnd() {
        return imports.isEmpty() ? importBlockStart() : imports.get(imports.size() - 1).lineIndex() + 1;
    }
}
