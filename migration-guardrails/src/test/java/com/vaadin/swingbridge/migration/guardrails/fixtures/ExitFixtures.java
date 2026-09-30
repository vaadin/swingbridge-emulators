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

package com.vaadin.swingbridge.migration.guardrails.fixtures;

/**
 * Fixtures for the JVM-exit gate, one per place a {@code System.exit} can sit relative to the two
 * relaxations: an ordinary method, a real {@code main}, a lambda inside that {@code main}, and a
 * whole class the migrator allowlists by name. The anonymous-class case needs its own package —
 * {@code fixtures.anonymous} — since {@code importClasses} would not pick the synthesised class up.
 */
public final class ExitFixtures {

    private ExitFixtures() {
    }

    /** The UI-reachable exit the gate exists to catch — no relaxation reaches it. */
    public static final class Exiter {
        public static void boom() {
            System.exit(1);
        }
    }

    /** The one legitimate case: a fatal-boot exit in the process entry point. */
    public static final class MainExiter {
        public static void main(String[] args) {
            if (args.length == 0) {
                System.exit(2);
            }
        }
    }

    /**
     * {@code invokeLater(() -> … System.exit(0))} written inside {@code main} — lexically in the
     * entry point, but compiled to a synthetic method, and the exit runs whenever the UI gets round
     * to the callback. Must stay flagged under {@code allowSystemExitInMainMethods()}.
     */
    public static final class LambdaInMainExiter {
        public static void main(String[] args) {
            final Runnable later = () -> System.exit(3);
            later.run();
        }
    }

    /** Exits outside any {@code main}, so only {@link ExitFixtures.AllowlistedClassExiter}'s own name lets it pass. */
    public static final class AllowlistedClassExiter {
        public static void shutDownTheWholeProcess() {
            Runtime.getRuntime().halt(5);
        }
    }
}
