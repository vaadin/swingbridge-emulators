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

package vaadinx.internal;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reflection-based stub generator. Emits vaadinx.* skeletons for classes
 * listed in {@link #MAPPING}; every other type reference is preserved as
 * its original JDK form.
 *
 * <p>Usage: {@code ./mvnw -C -pl generator compile exec:java -Dexec.arguments="javax.swing.JButton,emulators/src/main/java"}
 *
 * <p>Limitations (v1):
 * <ul>
 *   <li>interfaces, enums, annotations are rejected as targets
 *   <li>methods referencing inner classes (names containing {@code $}) are dropped
 *   <li>methods referencing {@code java.awt.peer.*} are dropped
 *   <li>parameter names from reflection are {@code arg0, arg1, …} for JDK classes
 *   <li>emitted bodies do not call {@code super} — humans decide per method
 * </ul>
 */
public final class Generator {

    /** Whitelist: only these JDK classes get ported. Everything else stays as JDK reference. */
    private static final String ROOT_CLASS = "java.awt.Component";

    private static final Map<String, String> MAPPING = Map.ofEntries(
        Map.entry(ROOT_CLASS,                     "vaadinx.awt.Component"),
        Map.entry("java.awt.Container",           "vaadinx.awt.Container"),
        Map.entry("java.awt.Window",              "vaadinx.awt.Window"),
        Map.entry("java.awt.Frame",               "vaadinx.awt.Frame"),
        Map.entry("java.awt.Dialog",              "vaadinx.awt.Dialog"),
        Map.entry("java.awt.LayoutManager",       "vaadinx.awt.LayoutManager"),
        Map.entry("java.awt.LayoutManager2",      "vaadinx.awt.LayoutManager2"),
        Map.entry("java.awt.FlowLayout",          "vaadinx.awt.FlowLayout"),
        Map.entry("java.awt.BorderLayout",        "vaadinx.awt.BorderLayout"),
        Map.entry("javax.swing.JComponent",       "vaadinx.swing.JComponent"),
        Map.entry("javax.swing.AbstractButton",   "vaadinx.swing.AbstractButton"),
        Map.entry("javax.swing.JButton",          "vaadinx.swing.JButton"),
        Map.entry("javax.swing.JFrame",           "vaadinx.swing.JFrame"),
        Map.entry("javax.swing.JDialog",          "vaadinx.swing.JDialog"),
        Map.entry("javax.swing.JPanel",           "vaadinx.swing.JPanel"),
        Map.entry("javax.swing.JLabel",           "vaadinx.swing.JLabel"),
        Map.entry("javax.swing.text.JTextComponent", "vaadinx.swing.text.JTextComponent"),
        Map.entry("javax.swing.text.DefaultCaret", "vaadinx.swing.text.DefaultCaret"),
        Map.entry("javax.swing.JTextField",       "vaadinx.swing.JTextField"),
        Map.entry("javax.swing.JPasswordField",   "vaadinx.swing.JPasswordField"),
        Map.entry("javax.swing.JTextArea",        "vaadinx.swing.JTextArea"),
        Map.entry("javax.swing.JFormattedTextField", "vaadinx.swing.JFormattedTextField"),
        Map.entry("javax.swing.JToggleButton",    "vaadinx.swing.JToggleButton"),
        Map.entry("javax.swing.JCheckBox",        "vaadinx.swing.JCheckBox"),
        Map.entry("javax.swing.JSlider",          "vaadinx.swing.JSlider"),
        Map.entry("javax.swing.JSpinner",         "vaadinx.swing.JSpinner"),
        Map.entry("javax.swing.Icon",             "vaadinx.swing.Icon"),
        Map.entry("javax.swing.RootPaneContainer", "vaadinx.swing.RootPaneContainer"),
        Map.entry("javax.swing.border.Border",    "vaadinx.swing.border.Border")
    );

    /**
     * Interfaces to drop from emitted {@code implements} clauses — <strong>empty, and meant to
     * stay that way.</strong> The set is the last resort for a JDK interface whose method
     * signatures reference mapped types ({@code java.awt.Component} / {@code Container} /
     * {@code Window}) that a {@code vaadinx.*} class cannot satisfy; the right answer is
     * almost always to <em>port</em> the interface and add a {@link #MAPPING} row instead, so
     * the emitted clause names the port.
     *
     * <p>Every member this set ever had turned out to be a mistake. {@code MenuContainer} and
     * {@code ImageObserver} name no mapped type at all ({@code Font} / {@code Event} /
     * {@code MenuComponent} / {@code Image} are reused unchanged), and dropping ImageObserver
     * cost every Component-derived emulator its eight image flags — a dropped interface takes
     * its constants with it, so the emulator stops compiling the migrator's {@code Xxx.CONSTANT}
     * (D_missing_constants, gated by {@code PublicConstantsTest}). {@code RootPaneContainer}
     * did name mapped types, and was still wrong: it is {@code vaadinx.swing.RootPaneContainer}
     * now, and its absence had quietly bent {@code SwingUtilities.getRootPane} into an
     * {@code instanceof} chain that answered null for a {@code JFrame} (D_hierarchy_parity).
     * A dropped interface is also invisible to the constants gate whenever it carries no
     * constants — {@code TypeHierarchyParityTest} is what sees it.
     */
    private static final Set<String> SKIP_INTERFACES = Set.of();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: Generator <fully-qualified-class-name> <output-dir>");
            System.err.println("Example: Generator javax.swing.JButton emulators/src/main/java");
            System.exit(1);
        }
        Class<?> cls = Class.forName(args[0]);
        Path outDir = Path.of(args[1]);
        if (cls.isInterface() || cls.isAnnotation() || cls.isEnum()) {
            System.err.println("Only plain classes are supported: " + cls.getName());
            System.exit(1);
        }
        String mapped = MAPPING.get(cls.getName());
        if (mapped == null) {
            System.err.println("Class not in whitelist — add to MAPPING first: " + cls.getName());
            System.exit(1);
        }

        String src = generate(cls, mapped);
        String targetPkg = mapped.substring(0, mapped.lastIndexOf('.'));
        Path outPath = outDir.resolve(targetPkg.replace('.', '/'))
                             .resolve(cls.getSimpleName() + ".java");
        Files.createDirectories(outPath.getParent());
        Files.writeString(outPath, src);
        System.out.println("Wrote " + outPath);
    }

    // ---- type mapping ----

    private static String mapClassName(String fqn) {
        return MAPPING.getOrDefault(fqn, fqn);
    }

    private static String formatType(Type t) {
        if (t instanceof Class<?> c) {
            if (c.isArray()) return formatType(c.getComponentType()) + "[]";
            if (c.isPrimitive()) return c.getName();
            return mapClassName(c.getName().replace('$', '.'));
        }
        if (t instanceof ParameterizedType p) {
            String raw = formatType(p.getRawType());
            String args = Arrays.stream(p.getActualTypeArguments())
                    .map(Generator::formatType)
                    .collect(Collectors.joining(", "));
            return raw + "<" + args + ">";
        }
        if (t instanceof GenericArrayType g) {
            return formatType(g.getGenericComponentType()) + "[]";
        }
        if (t instanceof WildcardType w) {
            Type[] lower = w.getLowerBounds();
            Type[] upper = w.getUpperBounds();
            if (lower.length > 0) return "? super " + formatType(lower[0]);
            if (upper.length > 0 && !Object.class.equals(upper[0])) return "? extends " + formatType(upper[0]);
            return "?";
        }
        if (t instanceof TypeVariable<?> v) return v.getName();
        return t.getTypeName();
    }

    private static String formatTypeParameters(TypeVariable<?>[] tvs) {
        if (tvs.length == 0) return "";
        return "<" + Arrays.stream(tvs).map(Generator::formatTypeVar).collect(Collectors.joining(", ")) + ">";
    }

    private static String formatTypeVar(TypeVariable<?> v) {
        Type[] bounds = v.getBounds();
        if (bounds.length == 0 || (bounds.length == 1 && Object.class.equals(bounds[0]))) {
            return v.getName();
        }
        return v.getName() + " extends " + Arrays.stream(bounds)
                .map(Generator::formatType)
                .collect(Collectors.joining(" & "));
    }

    /**
     * A method/ctor signature is unsupported — and therefore dropped — if it references
     * {@code java.awt.peer.*} (we don't mirror peer SPIs) or a nested class (would map to
     * a nested vaadinx type that we don't generate). Errs on the side of dropping; humans
     * hand-add later if needed.
     */
    private static boolean unsupported(Type... types) {
        for (Type t : types) {
            String n = t.getTypeName();
            if (n.contains("java.awt.peer.")) return true;
            if (n.contains("$")) return true;
        }
        return false;
    }

    private static String defaultReturn(Class<?> rt) {
        if (rt == void.class) return null;
        if (rt == boolean.class) return "false";
        if (rt == char.class) return "'\\0'";
        if (rt.isPrimitive()) return "0";
        return "null";
    }

    // ---- emit ----

    /**
     * {@code HDR_jdk_derived} for {@code cls}: its own Oracle notice, read out of this JDK's
     * {@code lib/src.zip}, then Vaadin's derived-and-modified block. Always that header — the
     * generator only ever reflects over a JDK class, so what it emits is JDK-derived by
     * construction, and it lands in {@code :emulators}, which is the GPL lane whatever the
     * generator's own module is licensed as.
     *
     * <p>The Oracle block is <em>copied</em>, never templated, because the years differ per file
     * (42 distinct pairs across the 160 emulators today) and a stamped year is a false statement
     * about which file was licensed when. {@code vaadinx.LicenseHeaderTest} rule 4 rejects a
     * template anyway, so a "close enough" header would only be discovered later, by a reddened
     * build in whichever slice next ran the generator.
     *
     * @throws IllegalStateException if {@code src.zip} is missing or has no source for {@code cls} —
     *         loud beats emitting a skeleton whose first bytes are a guess
     */
    private static String licenceHeader(Class<?> cls) {
        Path srcZip = Path.of(System.getProperty("java.home"), "lib", "src.zip");
        if (!Files.isReadable(srcZip)) {
            throw new IllegalStateException("no readable " + srcZip + ", so " + cls.getName()
                    + "'s Oracle copyright header cannot be copied. Run the generator on a full JDK"
                    + " (one that ships lib/src.zip), not a JRE. See PROVENANCE.md § Headers.");
        }
        String path = cls.getName().replace('.', '/') + ".java";
        String source = null;
        try (ZipFile zip = new ZipFile(srcZip.toFile())) {
            for (String module : List.of("java.desktop", "java.base")) {
                ZipEntry entry = zip.getEntry(module + "/" + path);
                if (entry != null) {
                    try (var in = zip.getInputStream(entry)) {
                        source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + srcZip, e);
        }
        if (source == null) {
            throw new IllegalStateException("no source for " + cls.getName() + " in " + srcZip
                    + " — check the class name, or add its module to the list in licenceHeader().");
        }
        int end = source.indexOf("*/");
        if (!source.stripLeading().startsWith("/*") || end < 0) {
            throw new IllegalStateException(cls.getName() + "'s source in " + srcZip
                    + " does not open with a comment block, so there is no Oracle header to copy.");
        }
        String oracle = source.substring(source.indexOf("/*"), end + 2);
        if (!oracle.contains("Oracle and/or its affiliates") || !oracle.contains("\"Classpath\" exception")) {
            throw new IllegalStateException(cls.getName() + "'s header in " + srcZip + " is not an"
                    + " Oracle Classpath-exception notice; refusing to emit it as one.");
        }
        int year = Year.now().getValue();
        return oracle + "\n\n"
                + "/*\n"
                + " * Copyright 2000-" + year + " Vaadin Ltd.\n"
                + " * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0\n"
                + " *\n"
                + " * This file is derived from OpenJDK's " + cls.getName() + "\n"
                + " * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by\n"
                + " * Vaadin Ltd in " + year + "; see PROVENANCE.md at the root of\n"
                + " * https://github.com/vaadin/swingbridge-emulators for how and why, and the\n"
                + " * repository history for each change. Vaadin Ltd licenses its modifications\n"
                + " * under the same GNU General Public License version 2 only and extends the\n"
                + " * \"Classpath\" exception, as set out in the LICENSE file that accompanied\n"
                + " * this code, to its version of this file.\n"
                + " */\n\n";
    }

    private static String generate(Class<?> cls, String targetFqn) {
        String targetPkg = targetFqn.substring(0, targetFqn.lastIndexOf('.'));
        String simpleName = cls.getSimpleName();
        boolean isRoot = cls.getName().equals(ROOT_CLASS);

        StringBuilder sb = new StringBuilder();
        sb.append(licenceHeader(cls));
        sb.append("package ").append(targetPkg).append(";\n\n");
        sb.append("// Generated by vaadinx.internal.Generator from ").append(cls.getName()).append("\n");
        sb.append("// Scaffold only — hand-finish methods the current slice needs.\n\n");

        // "Emulator for" when the port has a peer (classes in the Component hierarchy);
        // "Port of" for the standalone interfaces (LayoutManager, Icon, Border, …) that
        // have no peer. See CLAUDE.md §Terminology.
        sb.append("/** ").append(cls.isInterface() ? "Port of" : "Emulator for")
                .append(" {@link ").append(cls.getName()).append("}. */\n");

        sb.append(classModifiers(cls.getModifiers()));
        sb.append("class ").append(simpleName).append(formatTypeParameters(cls.getTypeParameters()));

        Type superType = cls.getGenericSuperclass();
        if (!isRoot && superType != null && !Object.class.equals(superType)) {
            sb.append(" extends ").append(formatType(superType));
        }
        Type[] ifaces = Arrays.stream(cls.getGenericInterfaces())
                .filter(Generator::emitInterface)
                .toArray(Type[]::new);
        if (ifaces.length > 0) {
            sb.append(" implements ").append(Arrays.stream(ifaces)
                    .map(Generator::formatType)
                    .collect(Collectors.joining(", ")));
        }
        sb.append(" {\n\n");

        if (isRoot) {
            sb.append("    protected final com.vaadin.flow.component.Component peer;\n\n");
        }

        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            int m = c.getModifiers();
            if (!(Modifier.isPublic(m) || Modifier.isProtected(m))) continue;
            if (unsupported(c.getGenericParameterTypes())) continue;
            emitCtor(sb, simpleName, c);
        }

        // Protected peer ctor — the chain terminator. Always emitted.
        sb.append("    protected ").append(simpleName)
          .append("(com.vaadin.flow.component.Component peer) {\n")
          .append(isRoot ? "        this.peer = peer;\n" : "        super(peer);\n")
          .append("    }\n\n");

        for (Method method : cls.getDeclaredMethods()) {
            int m = method.getModifiers();
            if (!(Modifier.isPublic(m) || Modifier.isProtected(m))) continue;
            if (method.isSynthetic() || method.isBridge()) continue;
            if (unsupported(method.getGenericReturnType())) continue;
            if (unsupported(method.getGenericParameterTypes())) continue;
            emitMethod(sb, simpleName, method);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static boolean emitInterface(Type t) {
        Class<?> raw;
        if (t instanceof Class<?> c) raw = c;
        else if (t instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) raw = c;
        else return true;
        if (!Modifier.isPublic(raw.getModifiers())) return false;
        return !SKIP_INTERFACES.contains(raw.getName());
    }

    private static String classModifiers(int m) {
        StringBuilder sb = new StringBuilder();
        if (Modifier.isPublic(m)) sb.append("public ");
        if (Modifier.isAbstract(m) && !Modifier.isInterface(m)) sb.append("abstract ");
        if (Modifier.isFinal(m)) sb.append("final ");
        return sb.toString();
    }

    private static void emitCtor(StringBuilder sb, String simpleName, Constructor<?> c) {
        int m = c.getModifiers();
        sb.append("    ");
        if (Modifier.isPublic(m)) sb.append("public ");
        else if (Modifier.isProtected(m)) sb.append("protected ");
        sb.append(simpleName).append("(");
        sb.append(formatParameters(c.getParameters(), c.getGenericParameterTypes(), c.isVarArgs()));
        sb.append(")");
        Type[] ex = c.getGenericExceptionTypes();
        if (ex.length > 0) {
            sb.append(" throws ").append(Arrays.stream(ex)
                    .map(Generator::formatType)
                    .collect(Collectors.joining(", ")));
        }
        sb.append(" {\n");
        sb.append("        this(new com.vaadin.flow.component.html.Div() /* TODO pick Vaadin peer */);\n");
        sb.append("        vaadinx.EHelper.onUnimplemented(\"").append(simpleName).append("\", \"<init>\"")
          .append(passArgs(c.getParameters())).append(");\n");
        sb.append("    }\n\n");
    }

    private static void emitMethod(StringBuilder sb, String simpleName, Method method) {
        int mods = method.getModifiers();
        boolean isStatic = Modifier.isStatic(mods);
        sb.append("    ");
        if (Modifier.isPublic(mods)) sb.append("public ");
        else if (Modifier.isProtected(mods)) sb.append("protected ");
        if (isStatic) sb.append("static ");
        if (Modifier.isFinal(mods)) sb.append("final ");
        if (Modifier.isSynchronized(mods)) sb.append("synchronized ");
        // intentionally drop: abstract, native

        String typeParams = formatTypeParameters(method.getTypeParameters());
        if (!typeParams.isEmpty()) sb.append(typeParams).append(" ");
        sb.append(formatType(method.getGenericReturnType())).append(" ");
        sb.append(method.getName()).append("(");
        sb.append(formatParameters(method.getParameters(), method.getGenericParameterTypes(), method.isVarArgs()));
        sb.append(")");
        Type[] ex = method.getGenericExceptionTypes();
        if (ex.length > 0) {
            sb.append(" throws ").append(Arrays.stream(ex)
                    .map(Generator::formatType)
                    .collect(Collectors.joining(", ")));
        }
        sb.append(" {\n");

        sb.append("        vaadinx.EHelper.onUnimplemented(\"").append(simpleName).append("\", \"")
          .append(method.getName()).append("\"")
          .append(passArgs(method.getParameters())).append(");\n");

        String def = defaultReturn(method.getReturnType());
        if (def != null) {
            sb.append("        return ").append(def).append(";\n");
        }
        sb.append("    }\n\n");
    }

    private static String formatParameters(Parameter[] ps, Type[] gts, boolean varArgs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append(", ");
            String t = formatType(gts[i]);
            if (varArgs && i == ps.length - 1 && t.endsWith("[]")) {
                t = t.substring(0, t.length() - 2) + "...";
            }
            sb.append(t).append(" ").append(ps[i].getName());
        }
        return sb.toString();
    }

    /** Emits a comma-prefixed list of parameter-name references suitable for a varargs call site. */
    private static String passArgs(Parameter[] ps) {
        if (ps.length == 0) return "";
        return ", " + Arrays.stream(ps).map(Parameter::getName).collect(Collectors.joining(", "));
    }
}
