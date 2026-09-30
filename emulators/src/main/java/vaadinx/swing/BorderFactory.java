/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.BorderFactory
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import vaadinx.swing.border.BevelBorder;
import vaadinx.swing.border.Border;
import vaadinx.swing.border.CompoundBorder;
import vaadinx.swing.border.EmptyBorder;
import vaadinx.swing.border.EtchedBorder;
import vaadinx.swing.border.LineBorder;
import vaadinx.swing.border.MatteBorder;
import vaadinx.swing.border.SoftBevelBorder;
import vaadinx.swing.border.TitledBorder;

/**
 * Port of {@link javax.swing.BorderFactory}. Static factories for the
 * common border shapes, mirroring the JDK surface 1:1 for import-swap.
 *
 * <p>Shared-instance caching follows JDK's pattern: the common "just
 * make an empty border" / "just a plain black line" calls reuse a
 * singleton, since borders are stateless after construction and
 * overwhelmingly created at UI-build time.
 */
public final class BorderFactory {

    private BorderFactory() {}

    // --- Empty ---

    private static EmptyBorder sharedEmpty;

    public static Border createEmptyBorder() {
        // JDK returns a (0,0,0,0) EmptyBorder singleton.
        if (sharedEmpty == null) sharedEmpty = new EmptyBorder(0, 0, 0, 0);
        return sharedEmpty;
    }

    public static Border createEmptyBorder(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    // --- Line ---

    public static Border createLineBorder(java.awt.Color color) {
        return new LineBorder(color, 1, false);
    }

    public static Border createLineBorder(java.awt.Color color, int thickness) {
        return new LineBorder(color, thickness, false);
    }

    public static Border createLineBorder(java.awt.Color color, int thickness, boolean rounded) {
        return new LineBorder(color, thickness, rounded);
    }

    public static Border createBlackLineBorder() {
        return LineBorder.createBlackLineBorder();
    }

    // --- Matte ---

    public static MatteBorder createMatteBorder(int top, int left, int bottom, int right, java.awt.Color color) {
        return new MatteBorder(top, left, bottom, right, color);
    }

    public static MatteBorder createMatteBorder(int top, int left, int bottom, int right, vaadinx.swing.Icon tileIcon) {
        return new MatteBorder(top, left, bottom, right, tileIcon);
    }

    // --- Compound ---

    public static CompoundBorder createCompoundBorder() {
        return new CompoundBorder();
    }

    public static CompoundBorder createCompoundBorder(Border outsideBorder, Border insideBorder) {
        return new CompoundBorder(outsideBorder, insideBorder);
    }

    // --- Titled ---

    public static TitledBorder createTitledBorder(String title) {
        return new TitledBorder(title);
    }

    public static TitledBorder createTitledBorder(Border border) {
        return new TitledBorder(border);
    }

    public static TitledBorder createTitledBorder(Border border, String title) {
        return new TitledBorder(border, title);
    }

    public static TitledBorder createTitledBorder(Border border, String title,
                                                  int titleJustification, int titlePosition) {
        return new TitledBorder(border, title, titleJustification, titlePosition);
    }

    public static TitledBorder createTitledBorder(Border border, String title,
                                                  int titleJustification, int titlePosition,
                                                  java.awt.Font titleFont) {
        return new TitledBorder(border, title, titleJustification, titlePosition, titleFont);
    }

    public static TitledBorder createTitledBorder(Border border, String title,
                                                  int titleJustification, int titlePosition,
                                                  java.awt.Font titleFont, java.awt.Color titleColor) {
        return new TitledBorder(border, title, titleJustification, titlePosition, titleFont, titleColor);
    }

    // --- Bevel ---

    public static Border createLoweredBevelBorder() {
        return new BevelBorder(BevelBorder.LOWERED);
    }

    public static Border createRaisedBevelBorder() {
        return new BevelBorder(BevelBorder.RAISED);
    }

    public static Border createBevelBorder(int type) {
        return new BevelBorder(type);
    }

    public static Border createBevelBorder(int type, java.awt.Color highlight, java.awt.Color shadow) {
        return new BevelBorder(type, highlight, shadow);
    }

    public static Border createBevelBorder(int type, java.awt.Color highlightOuter, java.awt.Color highlightInner,
                                           java.awt.Color shadowOuter, java.awt.Color shadowInner) {
        return new BevelBorder(type, highlightOuter, highlightInner, shadowOuter, shadowInner);
    }

    // --- SoftBevel ---

    public static Border createRaisedSoftBevelBorder() {
        return new SoftBevelBorder(SoftBevelBorder.RAISED);
    }

    public static Border createLoweredSoftBevelBorder() {
        return new SoftBevelBorder(SoftBevelBorder.LOWERED);
    }

    public static Border createSoftBevelBorder(int type) {
        return new SoftBevelBorder(type);
    }

    public static Border createSoftBevelBorder(int type, java.awt.Color highlight, java.awt.Color shadow) {
        return new SoftBevelBorder(type, highlight, shadow);
    }

    public static Border createSoftBevelBorder(int type, java.awt.Color highlightOuter, java.awt.Color highlightInner,
                                               java.awt.Color shadowOuter, java.awt.Color shadowInner) {
        return new SoftBevelBorder(type, highlightOuter, highlightInner, shadowOuter, shadowInner);
    }

    // --- Etched ---

    public static Border createEtchedBorder() {
        return new EtchedBorder();
    }

    public static Border createEtchedBorder(int type) {
        return new EtchedBorder(type);
    }

    public static Border createEtchedBorder(java.awt.Color highlight, java.awt.Color shadow) {
        return new EtchedBorder(highlight, shadow);
    }

    public static Border createEtchedBorder(int type, java.awt.Color highlight, java.awt.Color shadow) {
        return new EtchedBorder(type, highlight, shadow);
    }
}
