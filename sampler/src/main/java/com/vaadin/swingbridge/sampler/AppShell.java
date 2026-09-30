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

package com.vaadin.swingbridge.sampler;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.lumo.Lumo;

// @Push is non-negotiable for vaadin-blocking-dialogs' UI fibers. When a Swing
// listener under EHelper.callSwing parks on a blocking dialog, the park releases the
// session lock; only then are UI changes flushed to the browser, and only Push can
// carry them there. Without Push, the dialog never reaches the user.
@Push
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet("styles.css")
public class AppShell implements AppShellConfigurator {
}
