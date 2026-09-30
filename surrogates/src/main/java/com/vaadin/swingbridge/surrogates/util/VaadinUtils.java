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

package com.vaadin.swingbridge.surrogates.util;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;

/**
 * Broad, generic Vaadin UI/DOM utilities used across the surrogate layer;
 * shared with {@code :emulators} in the SD_shelper_statics-allowed direction where needed.
 */
public final class VaadinUtils {

    private VaadinUtils() {}

    /**
     * Inject a server-managed {@code <style>} element into the given UI's
     * element tree exactly once, guarded by a per-UI sentinel. Generic
     * mechanism behind pseudo-element CSS that inline {@code Style} can't
     * reach (e.g. {@code BorderCss}'s TitledBorder {@code ::before} rule,
     * {@code SJProgressBar}'s progress-string {@code ::after} rule).
     *
     * <p>The {@code <style>} lives in the UI element tree rather than going
     * through {@link com.vaadin.flow.component.page.Page#executeJs} /
     * {@code addStyleSheet}: only a tree-attached Element survives a
     * {@code @PreserveOnRefresh} reload, because Vaadin re-renders the
     * preserved tree (it doesn't replay executeJs or re-emit already-sent
     * dependencies). The {@code sentinelKey} guards against double-attach on
     * a single UI and survives reload for the same reason.
     *
     * @param ui             the target UI; no-op when {@code null}
     * @param sentinelKey    per-UI {@link ComponentUtil} data key marking
     *                       "already injected"
     * @param dataEmulValue   value for the {@code data-emul} attribute on the
     *                       {@code <style>} (identifies the rule in DevTools)
     * @param css            the stylesheet text
     */
    public static void injectStyleOnce(UI ui, String sentinelKey, String dataEmulValue, String css) {
        if (ui == null) return;
        if (ComponentUtil.getData(ui, sentinelKey) != null) return;
        ComponentUtil.setData(ui, sentinelKey, Boolean.TRUE);
        Element styleEl = new Element("style");
        styleEl.setAttribute("data-emul", dataEmulValue);
        styleEl.setText(css);
        ui.getElement().appendChild(styleEl);
    }
}
