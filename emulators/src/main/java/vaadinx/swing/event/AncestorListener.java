/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.event.AncestorListener
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.event;

/**
 * Port of {@link javax.swing.event.AncestorListener}. Receives
 * {@link AncestorEvent}s on attach / detach of the listened
 * {@link vaadinx.swing.JComponent} or any of its ancestors.
 *
 * <p>{@code ancestorMoved} fires under JDK Swing when an ancestor's
 * layout position changes; Vaadin doesn't surface that server-side, so
 * the wiring in {@code JComponent.addNotify} / {@code removeNotify}
 * never fires it. {@code ancestorAdded} / {@code ancestorRemoved} fire
 * on Vaadin attach / detach — same accepted incompleteness as the
 * surrogate-side {@code SAncestorListener}.
 */
public interface AncestorListener extends java.util.EventListener {
    void ancestorAdded(AncestorEvent event);
    void ancestorRemoved(AncestorEvent event);
    void ancestorMoved(AncestorEvent event);
}
