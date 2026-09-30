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

package com.vaadin.swingbridge.migration.guardrails.fixtures.synthetics;

/**
 * The compiler-synthetic statics the gate has to skip, in their own package so a test can import the
 * package and pick up the classes javac generates alongside these two — {@code Season}'s
 * {@code $VALUES} array and the {@code $SwitchMap$…} table this switch emits. Both are
 * {@code static}, both of a mutable array type, and neither is anything a migrator can route.
 *
 * <p>Nothing here declares a {@code static} field of its own, so any violation the gate reports over
 * this package is a synthetic one.
 */
public final class SwitchOverEnum {

    private SwitchOverEnum() {
    }

    public static int monthsAway(Season season) {
        switch (season) {
            case SPRING:
                return 3;
            case SUMMER:
                return 6;
            default:
                return 0;
        }
    }
}
