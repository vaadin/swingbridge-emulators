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

import java.lang.constant.ClassDesc;

/**
 * The name shapes this tool moves between:
 *
 * <pre>
 * com/acme/Main$1      internal, as the class file spells it   → binary(…)
 * com.acme.Main$1      binary, as Class.forName wants it
 * java.lang.String[]   a field's type, from its ClassDesc      → type(…)
 * String[]             short enough for a report column        → simple(…)
 * </pre>
 */
final class Names {

    private Names() {
    }

    /** {@code com/acme/Main$1} → {@code com.acme.Main$1}. */
    static String binary(String internalName) {
        return internalName.replace('/', '.');
    }

    /**
     * The fully-qualified type name, arrays as {@code Foo[]} and primitives as {@code int}.
     *
     * <p>An array renders the way <b>ArchUnit</b>'s {@code JavaClass.getName()} does, not in
     * JVM form ({@code [Ljava.lang.String;}). {@code ImmutableTypes} is asked the same question
     * by that reader and by this one, and it matches type names as strings — so a different
     * rendering here is a different immutability verdict, with nothing to catch it.
     */
    static String type(ClassDesc desc) {
        if (desc.isArray()) {
            return type(desc.componentType()) + "[]";
        }
        if (desc.isPrimitive()) {
            return desc.displayName();
        }
        String pkg = desc.packageName();
        return pkg.isEmpty() ? desc.displayName() : pkg + "." + desc.displayName();
    }

    /** Everything after the last {@code .} — {@code java.util.Map} → {@code Map}. */
    static String simple(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    /**
     * The element type of an array, or the type itself.
     *
     * @param typeName as {@link #type(ClassDesc)} renders it
     */
    static String element(String typeName) {
        String name = typeName;
        while (name.endsWith("[]")) {
            name = name.substring(0, name.length() - 2);
        }
        return name;
    }
}
