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

package com.vaadin.swingbridge.surrogates.swing.text;

/**
 * Receives the browser's selection in a text peer's input, for a caller that keeps
 * its own caret model:
 *
 * <pre>{@code
 * field.addSelectionReportListener((start, end, backward) ->
 *         caret.setDot(backward ? end : start), 1000);
 * }</pre>
 *
 * <p>Offsets are the input's {@code selectionStart} / {@code selectionEnd}, so
 * {@code start <= end}; {@code backward} says the caret sits at {@code start}. The
 * callback runs on the request thread, with the session locked and no UI fiber.
 */
@FunctionalInterface
public interface SelectionReportListener extends java.io.Serializable {

    void selectionReported(int start, int end, boolean backward);
}
