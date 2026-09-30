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

package com.vaadin.swingbridge.surrogates;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The surrogate classes the reflective gates sweep: every public concrete Vaadin component in {@code src/main}. */
final class SurrogateTypes {

    private SurrogateTypes() {}

    static List<Class<?>> all() throws IOException {
        Path root = Path.of("target/classes");
        try (Stream<Path> s = Files.walk(root)) {
            return s.map(root::relativize).map(Path::toString)
                    .filter(p -> p.endsWith(".class") && !p.contains("$"))
                    .map(p -> p.substring(0, p.length() - ".class".length()).replace('/', '.').replace('\\', '.'))
                    .sorted()
                    .<Class<?>>map(SurrogateTypes::load)
                    .filter(com.vaadin.flow.component.Component.class::isAssignableFrom)
                    .filter(c -> Modifier.isPublic(c.getModifiers()) && !Modifier.isAbstract(c.getModifiers()))
                    .toList();
        }
    }

    /** The public no-arg constructor, or {@code null}. */
    static Constructor<?> noArgConstructor(Class<?> type) {
        try {
            return type.getConstructor();
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
