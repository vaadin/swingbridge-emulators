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

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every {@code .class} file under the given directories, parsed once.
 *
 * <pre>{@code
 * ClassTree tree = ClassTree.of(List.of(Path.of("target/classes")));
 * tree.isAppClass("com.acme.App");        // => true:  the tree declares it
 * tree.isAppClass("org.slf4j.Logger");    // => false: a jar does
 * }</pre>
 *
 * <p>That distinction is what <b>app-owned</b> means throughout this tool: the app's own statics are
 * worth reporting, {@code System.out} is not.
 */
final class ClassTree {

    private final List<ClassModel> models;
    private final Set<String> appClasses;
    private final long newestClassMillis;

    private ClassTree(List<ClassModel> models, Set<String> appClasses, long newestClassMillis) {
        this.models = models;
        this.appClasses = appClasses;
        this.newestClassMillis = newestClassMillis;
    }

    /**
     * @param classDirs directories to walk; a missing one contributes nothing rather than failing,
     *     so a multi-module app can be swept with one invocation naming every module's
     *     {@code target/classes} whether or not each module has been built
     */
    static ClassTree of(List<Path> classDirs) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dir : classDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".class"))
                        .forEach(files::add);
            }
        }
        files.sort(Comparator.naturalOrder());

        List<ClassModel> models = new ArrayList<>(files.size());
        Set<String> names = new LinkedHashSet<>();
        long newest = 0;
        for (Path file : files) {
            ClassModel model = ClassFile.of().parse(file);
            models.add(model);
            names.add(Names.binary(model.thisClass().asInternalName()));
            newest = Math.max(newest, Files.getLastModifiedTime(file).toMillis());
        }
        return new ClassTree(List.copyOf(models), Set.copyOf(names), newest);
    }

    List<ClassModel> models() {
        return models;
    }

    /** Whether this name was declared by a class file in the swept tree, rather than by a jar. */
    boolean isAppClass(String binaryName) {
        return appClasses.contains(binaryName);
    }

    int classFileCount() {
        return models.size();
    }

    /** Epoch millis of the most recently written class file, or 0 when the tree is empty. */
    long newestClassMillis() {
        return newestClassMillis;
    }
}
