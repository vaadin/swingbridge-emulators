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
 * The {@link javax.swing.event.CaretEvent} handed to a
 * {@link javax.swing.event.CaretListener} on every dot/mark move — Swing's own
 * event class is abstract and its only concrete implementation is package-
 * private inside {@code javax.swing.text.JTextComponent}, so the fan-out needs
 * one of these.
 *
 * <p>Immutable: the offsets are the ones at fire time, not a live view of the
 * component. Swing's own implementation is mutable and reused across fires,
 * which is a trap for a listener that stashes the event — this one has no such
 * hazard.
 */
public final class SCaretEvent extends javax.swing.event.CaretEvent {

    private final int dot;
    private final int mark;

    public SCaretEvent(Object source, int dot, int mark) {
        super(source);
        this.dot = dot;
        this.mark = mark;
    }

    @Override
    public int getDot() {
        return dot;
    }

    @Override
    public int getMark() {
        return mark;
    }

    @Override
    public String toString() {
        return "SCaretEvent[dot=" + dot + ", mark=" + mark + ", source=" + getSource() + "]";
    }
}
