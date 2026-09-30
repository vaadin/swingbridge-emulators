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

package com.vaadin.swingbridge.migration.tool.staticsweep;

import com.vaadin.swingbridge.migration.tool.guardrails.ImmutableTypes;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The two questions a field's declared type cannot answer on its own — <b>is it a component</b>
 * (subclasses included) and <b>is it immutable</b> — put to the JVM's own resolver:
 *
 * <pre>{@code
 * try (Resolver r = Resolver.over(classDirs, libDirs, cpEntries)) {
 *     r.verdict("com.acme.MainFrame", false);   // => COMPONENT, "MainFrame <: java.awt.Component"
 *     r.verdict("org.absent.Thing", false);     // => UNRESOLVED — the row stays on the worklist
 * }
 * }</pre>
 *
 * <p><b>No application code runs.</b> {@code Class.forName(name, false, loader)} loads without
 * initialising, so a class whose {@code <clinit>} boots Hibernate or seeds a database is read as a
 * type and nothing more.
 *
 * <p><b>{@link Kind#UNRESOLVED} never demotes a row.</b> A dependency the caller did not hand in
 * costs precision, never silence: the sweep's failure mode has to be "you look at this row", not
 * "we assumed it was fine".
 */
final class Resolver implements Closeable {

    /**
     * Component supertypes, matched across both migration stages: {@code java.awt.Component} before
     * the import swap, its emulator and Vaadin's own after it. Named as strings and resolved through
     * the app's classpath — the same by-name match the guardrail gate uses, and the reason this
     * module needs no dependency on {@code :emulators} to recognise an emulator component.
     */
    private static final List<String> COMPONENT_TYPES = List.of("java.awt.Component",
            "vaadinx.awt.Component", "com.vaadin.flow.component.Component");

    /** Types whose {@code static} holder is the {@code G_lint_blind} shape rather than a component. */
    private static final List<String> ACTION_TYPES = List.of("javax.swing.Action", "vaadinx.swing.Action");

    enum Kind {
        /** Assignable to a component supertype: the sweep's prohibition, not a verdict to make. */
        COMPONENT,
        /** A Swing model or a {@code ButtonGroup} — shared UI state a component gate does not catch. */
        MODEL,
        /** An {@code Action} — shared UI state for the same reason. */
        ACTION,
        /** Resolved, and none of the above. */
        PLAIN,
        /** A primitive; no class to load and nothing to be assignable to. */
        PRIMITIVE,
        /** Would not load. Never demotes a row, and says so in the report. */
        UNRESOLVED
    }

    /**
     * @param detail what to print beside the kind — the resolved supertype for a component, the
     *     failure's exception class for an unresolved type, {@code ""} otherwise
     */
    record Verdict(Kind kind, String detail) {

        /** The report's own cell, Markdown and all: {@code **YES** (MainFrame <: java.awt.Component)}. */
        String render() {
            return switch (kind) {
                case COMPONENT -> "**YES** (" + detail + ")";
                case MODEL -> "model (" + detail + ")";
                case ACTION -> "action";
                case PLAIN -> "no";
                case PRIMITIVE -> "no (primitive)";
                case UNRESOLVED -> "unresolved" + (detail.isEmpty() ? "" : " (" + detail + ")");
            };
        }

        boolean component() {
            return kind == Kind.COMPONENT;
        }
    }

    private final URLClassLoader loader;
    private final List<Path> classpath;
    private final Map<String, Class<?>> loaded = new LinkedHashMap<>();
    private final List<Class<?>> componentTypes = new ArrayList<>();
    private final List<Class<?>> actionTypes = new ArrayList<>();

    private Resolver(URLClassLoader loader, List<Path> classpath) {
        this.loader = loader;
        this.classpath = classpath;
        for (String name : COMPONENT_TYPES) {
            load(name).ifPresent(componentTypes::add);
        }
        for (String name : ACTION_TYPES) {
            load(name).ifPresent(actionTypes::add);
        }
    }

    /**
     * The parent is the <b>platform</b> class loader, not the application one: the app's
     * types must resolve against the app's dependencies, never against this tool's — a
     * {@code Logger} on both classpaths would otherwise resolve to ArchUnit's transitive copy.
     * The platform loader still carries {@code java.desktop}, which is what makes
     * {@code java.awt.Component} resolvable with no jars handed in at all.
     *
     * @param classDirs the app's class directories, which come first so the app's own types win
     * @param libDirs directories whose {@code *.jar} files go on the classpath
     * @param cpEntries entries given directly, as {@code --cp} accepts them
     */
    static Resolver over(List<Path> classDirs, List<Path> libDirs, List<Path> cpEntries)
            throws IOException {
        Set<Path> entries = new LinkedHashSet<>(classDirs);
        for (Path dir : libDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> list = Files.list(dir)) {
                list.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted()
                        .forEach(entries::add);
            }
        }
        entries.addAll(cpEntries);

        List<URL> urls = new ArrayList<>();
        for (Path entry : entries) {
            try {
                urls.add(entry.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IOException("cannot put " + entry + " on the classpath", e);
            }
        }
        return new Resolver(new URLClassLoader(urls.toArray(new URL[0]),
                ClassLoader.getPlatformClassLoader()), List.copyOf(entries));
    }

    List<Path> classpath() {
        return classpath;
    }

    /**
     * Releases the loader's handles on the classpath jars.
     *
     * <p>Windows refuses to delete an open jar, so an unclosed loader is invisible on Linux
     * and fails the sweep's own {@code @TempDir} cleanup on Windows — the reason this class is
     * {@link Closeable} at all, since the {@code main} it serves would have exited anyway.
     */
    @Override
    public void close() throws IOException {
        loader.close();
    }

    /**
     * What kind of thing a field of this type holds.
     *
     * @param typeName as {@link Names#type} renders it; an array is judged by its element type, so a
     *     {@code JPanel[]} reads as a component holder — which the gate's raw-type match does not
     *     see, and the report says so
     */
    Verdict verdict(String typeName, boolean primitive) {
        if (primitive) {
            return new Verdict(Kind.PRIMITIVE, "");
        }
        String element = Names.element(typeName);
        Class<?> type = load(element).orElse(null);
        if (type == null) {
            return new Verdict(Kind.UNRESOLVED, "");
        }
        String array = typeName.equals(element) ? "" : "[] of ";
        for (Class<?> component : componentTypes) {
            if (component.isAssignableFrom(type)) {
                return new Verdict(Kind.COMPONENT,
                        array + type.getSimpleName() + " <: " + component.getName());
            }
        }
        if (isSwingModel(element)) {
            return new Verdict(Kind.MODEL, Names.simple(element));
        }
        for (Class<?> action : actionTypes) {
            if (action.isAssignableFrom(type)) {
                return new Verdict(Kind.ACTION, "");
            }
        }
        return new Verdict(Kind.PLAIN, "");
    }

    /** The gate's own rule, asked with plain facts so both readers reach the same verdict. */
    boolean isImmutable(StaticIndex.Field field) {
        Class<?> type = load(Names.element(field.typeName())).orElse(null);
        boolean array = !field.typeName().equals(Names.element(field.typeName()));
        return ImmutableTypes.isImmutable(field.typeName(), field.primitive(),
                !array && type != null && type.isEnum(), !array && type != null && type.isRecord());
    }

    /**
     * Type arguments erasure dropped, filtered to the component ones: a
     * {@code static Map<String, JPanel>} is a component holder the declared type cannot betray.
     *
     * <p>Walks the {@code L…;} class names after the first {@code <} — everything before it
     * is the raw type, which is {@code Q_component}'s subject rather than this one's. A raw
     * {@code static Map} and a wildcard carry no names here and recover nothing; both are of a
     * mutable type, so the rule flags them anyway and the row still gets read.
     *
     * @param signature the {@code Signature} attribute verbatim
     */
    List<String> componentTypeArguments(String signature) {
        int open = signature.indexOf('<');
        if (open < 0) {
            return List.of();
        }
        List<String> hits = new ArrayList<>();
        for (int i = signature.indexOf('L', open); i >= 0; i = signature.indexOf('L', i + 1)) {
            int end = signature.indexOf(';', i);
            int nested = signature.indexOf('<', i);
            if (end < 0) {
                break;
            }
            if (nested >= 0 && nested < end) {
                end = nested;
            }
            String name = signature.substring(i + 1, end).replace('/', '.');
            Class<?> type = load(name).orElse(null);
            if (type != null && componentTypes.stream().anyMatch(c -> c.isAssignableFrom(type))) {
                hits.add(type.getSimpleName());
            }
            i = end;
        }
        return hits.stream().distinct().toList();
    }

    /**
     * {@code Throwable}, not {@code ClassNotFoundException}: a class whose supertype is
     * missing from the handed-in classpath fails with a {@code NoClassDefFoundError}, and a
     * class file the JVM rejects outright with a {@code ClassFormatError}. All three mean the
     * same thing to the report — unresolved, read the row.
     */
    private java.util.Optional<Class<?>> load(String binaryName) {
        if (loaded.containsKey(binaryName)) {
            return java.util.Optional.ofNullable(loaded.get(binaryName));
        }
        Class<?> type;
        try {
            type = PRIMITIVES.containsKey(binaryName) ? PRIMITIVES.get(binaryName)
                    : Class.forName(binaryName, false, loader);
        } catch (Throwable t) {
            type = null;
        }
        loaded.put(binaryName, type);
        return java.util.Optional.ofNullable(type);
    }

    /**
     * A shared {@code TableModel} / {@code ButtonGroup} leaks a UI across users exactly as a
     * component does, and neither the component gate nor a lint catches it — so the report names
     * the shape rather than letting it read as an ordinary mutable field.
     */
    private static boolean isSwingModel(String typeName) {
        boolean swing = typeName.startsWith("javax.swing.") || typeName.startsWith("vaadinx.swing.");
        return swing && (typeName.endsWith("Model") || typeName.endsWith("ButtonGroup"));
    }

    private static final Map<String, Class<?>> PRIMITIVES = Map.of("int", int.class, "long",
            long.class, "double", double.class, "float", float.class, "short", short.class, "byte",
            byte.class, "char", char.class, "boolean", boolean.class, "void", void.class);
}
