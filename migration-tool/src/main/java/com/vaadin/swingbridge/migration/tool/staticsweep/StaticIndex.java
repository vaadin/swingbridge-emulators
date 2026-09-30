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

import com.vaadin.swingbridge.migration.IntentionallyStatic;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The facts the class files carry, extracted once: every {@code static} field with its declaration,
 * every {@code getstatic} / {@code putstatic} that names one, every {@code static} method's touches,
 * the {@code <clinit>} notes and the {@code static} nested types.
 *
 * <p><b>Nothing here judges anything</b> — no bucket, no verdict, no type hierarchy. Those need a
 * loaded class ({@link Resolver}) or a rule ({@link SweepReport}); this is what {@code javac}
 * recorded.
 */
final class StaticIndex {

    /** The annotation is matched by its own descriptor, so renaming it is a compile error here. */
    private static final String ALLOWLIST_DESCRIPTOR = IntentionallyStatic.class.descriptorString();

    /**
     * One {@code getstatic} or {@code putstatic}.
     *
     * @param inClass the class whose method holds the instruction, which need not own the field
     * @param method the enclosing method's name; {@code <clinit>} for a field initializer or a
     *     {@code static { }} block, and the synthetic {@code lambda$foo$0} for a write from a lambda
     *     inside {@code foo}
     * @param line from the {@code LineNumberTable}, or {@code null} when the class was compiled
     *     without one
     */
    record Access(String inClass, String method, Integer line, boolean write) {
    }

    /**
     * One {@code static} field, exactly as declared.
     *
     * @param typeName erased and fully-qualified, {@code java.lang.String[]} for an array
     * @param signature the {@code Signature} attribute verbatim, or {@code ""} — the type arguments
     *     erasure would otherwise have lost
     * @param constantValue the {@code ConstantValue} attribute rendered, or {@code null}; present
     *     only for a {@code static final} of a primitive or {@code String} type
     * @param allowlistReason the {@code @IntentionallyStatic} reason, {@code null} when absent;
     *     {@code CLASS} retention, so it is read out of {@code RuntimeInvisibleAnnotations}
     * @param initLine the line of this field's initializing {@code putstatic} inside its own
     *     class's {@code <clinit>}, or {@code null} for a field with no initializer
     */
    record Field(String owner, String sourceFile, String name, String typeName, String signature,
            boolean isFinal, boolean synthetic, boolean enumConstant, boolean primitive,
            String constantValue, String allowlistReason, String allowlistNote, Integer initLine) {

        /** {@code com.acme.App.CACHE} — the key an access and a {@code --diff} row are matched on. */
        String key() {
            return owner + "." + name;
        }

        boolean allowlisted() {
            return allowlistReason != null;
        }
    }

    /**
     * A {@code static} method and the app's own {@code static} fields its body reaches —
     * {@code Q_method_or_type}'s question, answered by the operands rather than by reading the body.
     *
     * @param touches each entry {@code "R field"} or {@code "W field"}, qualified when the field
     *     belongs to another class
     */
    record StaticMethod(String owner, String name, String descriptor, Integer line,
            List<String> touches) {
    }

    /**
     * What a class's static initializer does, for the {@code G_construction} timing signal.
     *
     * @param explicitBlock a heuristic, and the report says so: a {@code <clinit>} with exception
     *     handlers in a class that owns a non-synthetic {@code static} field. A field initializer
     *     and a {@code static { }} block compile to the same method, so nothing in the class file
     *     distinguishes them; a block without a {@code try} is missed, and the {@code $SwitchMap$}
     *     holders — whose {@code <clinit>} is all {@code NoSuchFieldError} handlers — are what the
     *     second clause excludes.
     * @param invokes non-app, non-{@code java.lang} callees, one level deep through app-owned ones
     */
    record ClinitNote(String owner, boolean explicitBlock, List<String> invokes) {
    }

    private final List<Field> fields = new ArrayList<>();
    private final Map<String, List<Access>> accesses = new LinkedHashMap<>();
    private final List<StaticMethod> staticMethods = new ArrayList<>();
    private final List<ClinitNote> clinitNotes = new ArrayList<>();
    private final List<String> nestedStaticTypes = new ArrayList<>();

    /** Every method's external callees, keyed {@code owner#name+descriptor}, for the one-level descent. */
    private final Map<String, List<String>> externalCallees = new LinkedHashMap<>();

    private final ClassTree tree;

    private StaticIndex(ClassTree tree) {
        this.tree = tree;
    }

    static StaticIndex over(ClassTree tree) {
        StaticIndex index = new StaticIndex(tree);
        index.indexAccessesAndCallees();
        index.indexDeclarations();
        return index;
    }

    List<Field> fields() {
        return fields;
    }

    List<Access> accessesTo(String fieldKey) {
        return accesses.getOrDefault(fieldKey, List.of());
    }

    List<StaticMethod> staticMethods() {
        return staticMethods;
    }

    List<ClinitNote> clinitNotes() {
        return clinitNotes;
    }

    List<String> nestedStaticTypes() {
        return nestedStaticTypes;
    }

    /**
     * Pass one: every static-field access in the whole tree, plus every method's external callees.
     *
     * <p>Tree-wide, and ahead of the declarations, because a field's writers and readers are not in
     * its own class file.
     */
    private void indexAccessesAndCallees() {
        for (ClassModel cm : tree.models()) {
            String owner = Names.binary(cm.thisClass().asInternalName());
            for (MethodModel mm : cm.methods()) {
                CodeModel code = mm.code().orElse(null);
                if (code == null) {
                    continue;
                }
                String method = mm.methodName().stringValue();
                List<String> callees = new ArrayList<>();
                int line = -1;
                for (CodeElement element : code) {
                    if (element instanceof LineNumber ln) {
                        line = ln.line();
                    } else if (element instanceof FieldInstruction fi && isStaticAccess(fi)) {
                        String key = Names.binary(fi.owner().asInternalName()) + "."
                                + fi.name().stringValue();
                        accesses.computeIfAbsent(key, k -> new ArrayList<>()).add(new Access(owner,
                                method, line < 0 ? null : line, fi.opcode() == Opcode.PUTSTATIC));
                    } else if (element instanceof InvokeInstruction ii) {
                        callees.add(Names.binary(ii.owner().asInternalName()) + "#"
                                + ii.name().stringValue() + ii.typeSymbol().descriptorString());
                    }
                }
                externalCallees.put(owner + "#" + method + mm.methodTypeSymbol().descriptorString(),
                        callees);
            }
        }
    }

    /** Pass two: the declarations, per class. */
    private void indexDeclarations() {
        for (ClassModel cm : tree.models()) {
            String owner = Names.binary(cm.thisClass().asInternalName());
            String source = cm.findAttribute(Attributes.sourceFile())
                    .map(SourceFileAttribute::sourceFile).map(u -> u.stringValue()).orElse("");

            for (FieldModel fm : cm.fields()) {
                if (fm.flags().has(AccessFlag.STATIC)) {
                    fields.add(field(owner, source, fm));
                }
            }
            for (MethodModel mm : cm.methods()) {
                if (mm.methodName().equalsString("<clinit>")) {
                    clinitNotes.add(clinitNote(cm, owner, mm));
                } else if (mm.flags().has(AccessFlag.STATIC)
                        // A lambda body is a synthetic static method; it is reported through the
                        // method that declares it, which is what a migrator can actually route.
                        && !mm.flags().has(AccessFlag.SYNTHETIC)) {
                    staticMethods.add(staticMethod(owner, mm));
                }
            }
            for (InnerClassesAttribute attribute : cm.findAttributes(Attributes.innerClasses())) {
                for (InnerClassInfo info : attribute.classes()) {
                    boolean ours = info.outerClass()
                            .map(c -> owner.equals(Names.binary(c.asInternalName()))).orElse(false);
                    if (ours && info.flags().contains(AccessFlag.STATIC)) {
                        nestedStaticTypes.add(Names.binary(info.innerClass().asInternalName()));
                    }
                }
            }
        }
    }

    private Field field(String owner, String source, FieldModel fm) {
        ClassDesc type = fm.fieldTypeSymbol();
        String name = fm.fieldName().stringValue();
        Optional<Annotation> allowlist = allowlistAnnotation(fm);
        return new Field(owner, source, name, Names.type(type),
                fm.findAttribute(Attributes.signature()).map(SignatureAttribute::signature)
                        .map(u -> u.stringValue()).orElse(""),
                fm.flags().has(AccessFlag.FINAL),
                fm.flags().has(AccessFlag.SYNTHETIC),
                fm.flags().has(AccessFlag.ENUM),
                type.isPrimitive(),
                fm.findAttribute(Attributes.constantValue()).map(ConstantValueAttribute::constant)
                        .map(c -> String.valueOf(c.constantValue())).orElse(null),
                allowlist.map(a -> enumElement(a, "value")).orElse(null),
                allowlist.map(a -> stringElement(a, "note")).orElse(null),
                initLine(owner + "." + name, owner));
    }

    /**
     * The declaration line, recovered from the initializer's own {@code putstatic}: a field with an
     * initializer is written by its class's {@code <clinit>} at the line it was declared on.
     */
    private Integer initLine(String fieldKey, String owner) {
        return accessesTo(fieldKey).stream()
                .filter(a -> a.write() && a.inClass().equals(owner) && a.method().equals("<clinit>"))
                .map(Access::line).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private StaticMethod staticMethod(String owner, MethodModel mm) {
        List<String> touches = new ArrayList<>();
        Integer line = null;
        CodeModel code = mm.code().orElse(null);
        if (code != null) {
            for (CodeElement element : code) {
                if (element instanceof LineNumber ln && line == null) {
                    line = ln.line();
                } else if (element instanceof FieldInstruction fi && isStaticAccess(fi)) {
                    String fieldOwner = Names.binary(fi.owner().asInternalName());
                    // Only the app's own statics are Q_method_or_type's subject; System.out and
                    // every other JDK static would bury the signal in noise.
                    if (tree.isAppClass(fieldOwner)) {
                        touches.add((fi.opcode() == Opcode.PUTSTATIC ? "W " : "R ")
                                + (fieldOwner.equals(owner) ? "" : Names.simple(fieldOwner) + ".")
                                + fi.name().stringValue());
                    }
                }
            }
        }
        return new StaticMethod(owner, mm.methodName().stringValue(),
                mm.methodTypeSymbol().descriptorString(), line,
                touches.stream().distinct().toList());
    }

    private ClinitNote clinitNote(ClassModel cm, String owner, MethodModel clinit) {
        CodeModel code = clinit.code().orElse(null);
        boolean ownsRealStatic = cm.fields().stream().anyMatch(f -> f.flags().has(AccessFlag.STATIC)
                && !f.flags().has(AccessFlag.SYNTHETIC));
        boolean explicitBlock = code != null && ownsRealStatic && !code.exceptionHandlers().isEmpty();
        List<String> invokes = new ArrayList<>();
        if (code != null) {
            for (CodeElement element : code) {
                if (element instanceof InvokeInstruction ii) {
                    collectInvoke(invokes, ii);
                }
            }
        }
        return new ClinitNote(owner, explicitBlock, invokes.stream().distinct().toList());
    }

    /**
     * One level of descent, because the interesting call is usually one hop down: an app's own
     * {@code buildSessionFactory()} is what {@code <clinit>} invokes, and the environment read that
     * makes the timing matter — a {@code ZoneId.systemDefault()}, a JDBC connect — is inside it.
     */
    private void collectInvoke(List<String> invokes, InvokeInstruction ii) {
        String calleeOwner = Names.binary(ii.owner().asInternalName());
        String callee = Names.simple(calleeOwner) + "." + ii.name().stringValue();
        if (!tree.isAppClass(calleeOwner)) {
            if (!calleeOwner.startsWith("java.lang.")) {
                invokes.add(callee);
            }
            return;
        }
        String key = calleeOwner + "#" + ii.name().stringValue() + ii.typeSymbol().descriptorString();
        for (String inner : externalCallees.getOrDefault(key, List.of())) {
            String innerOwner = inner.substring(0, inner.indexOf('#'));
            if (!tree.isAppClass(innerOwner) && !innerOwner.startsWith("java.lang.")) {
                String innerName = inner.substring(inner.indexOf('#') + 1, inner.indexOf('('));
                invokes.add(callee + " → " + Names.simple(innerOwner) + "." + innerName);
            }
        }
    }

    private static boolean isStaticAccess(FieldInstruction fi) {
        return fi.opcode() == Opcode.GETSTATIC || fi.opcode() == Opcode.PUTSTATIC;
    }

    private static Optional<Annotation> allowlistAnnotation(FieldModel fm) {
        List<Annotation> all = new ArrayList<>();
        fm.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .ifPresent(a -> all.addAll(a.annotations()));
        // CLASS retention puts it in the invisible table; the visible one is read too, so an app
        // that ever republishes the annotation as RUNTIME keeps its allowlist.
        fm.findAttribute(Attributes.runtimeVisibleAnnotations())
                .ifPresent(a -> all.addAll(a.annotations()));
        return all.stream().filter(a -> ALLOWLIST_DESCRIPTOR.equals(a.className().stringValue()))
                .findFirst();
    }

    private static String enumElement(Annotation annotation, String name) {
        for (AnnotationElement element : annotation.elements()) {
            if (element.name().equalsString(name)
                    && element.value() instanceof AnnotationValue.OfEnum e) {
                return e.constantName().stringValue();
            }
        }
        return "?";
    }

    private static String stringElement(Annotation annotation, String name) {
        for (AnnotationElement element : annotation.elements()) {
            if (element.name().equalsString(name)
                    && element.value() instanceof AnnotationValue.OfString s) {
                return s.stringValue();
            }
        }
        return "";
    }
}
