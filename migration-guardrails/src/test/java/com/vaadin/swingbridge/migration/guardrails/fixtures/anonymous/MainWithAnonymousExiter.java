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

package com.vaadin.swingbridge.migration.guardrails.fixtures.anonymous;

/**
 * The {@code Main$1} case: {@code main} declares an anonymous class that exits. Neither relaxation
 * may reach it — the exit lives in a class of its own, which is neither a {@code main} method nor
 * the name the migrator allowlisted.
 *
 * <p>Alone in its package so a test can import the package and pick up the {@code $1} javac
 * synthesises; {@code importClasses} names classes and would miss it.
 */
public final class MainWithAnonymousExiter {

    private MainWithAnonymousExiter() {
    }

    public static void main(String[] args) {
        new Runnable() {
            @Override
            public void run() {
                System.exit(4);
            }
        }.run();
    }
}
