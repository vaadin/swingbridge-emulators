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

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.html.Span;
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;

/**
 * Surrogate for {@link java.awt.Label} — AWT 1.0 static text, not
 * {@link javax.swing.JLabel}. The whole JDK class is text plus a
 * three-value alignment; no listeners, no icon, no mnemonic, no
 * {@code labelFor}.
 *
 * <pre>{@code
 * SLabel l = new SLabel("Total", SLabel.RIGHT);
 * l.setText("Total: 42");
 * l.setAlignment(SLabel.CENTER);   // → CSS text-align, read back losslessly
 * }</pre>
 *
 * <p>Hosts on {@link Span} — {@link SJLabel}'s {@code <label>} exists for
 * its {@code labelFor} wiring, which this class has none of. Rationale for
 * that, for standing apart from {@link SJLabel}, and for mapping alignment
 * onto {@code text-align} rather than {@code SJLabel}'s flex shape: SD_sbutton's
 * sibling entry SD_slabel.
 *
 * <p>Nothing is stored: {@code text} lives in the element's text
 * content, so {@code setText(null)} reads back {@code ""} (R_vaadin_first
 * loss — the emulator {@code vaadinx.awt.Label} shadows the field
 * so its getter returns null verbatim per R_swing_is_truth); {@code alignment}
 * lives in the CSS. No setter fires a PropertyChangeEvent, since
 * AWT's don't — {@link SJLabel} does only because JLabel does.
 */
public class SLabel extends Span implements ComponentMixin {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    /** {@code java.awt.Label.LEFT} — 0, which is <em>not</em> {@code SwingConstants.LEFT}. */
    public static final int LEFT = java.awt.Label.LEFT;

    /** {@code java.awt.Label.CENTER} — 1, which is <em>not</em> {@code SwingConstants.CENTER}. */
    public static final int CENTER = java.awt.Label.CENTER;

    /** {@code java.awt.Label.RIGHT} — 2, which is <em>not</em> {@code SwingConstants.RIGHT}. */
    public static final int RIGHT = java.awt.Label.RIGHT;

    public SLabel() {
        this("", LEFT);
    }

    public SLabel(String text) {
        this(text, LEFT);
    }

    /**
     * @param alignment {@link #LEFT} / {@link #CENTER} / {@link #RIGHT}
     * @throws IllegalArgumentException on any other value, as in AWT
     */
    public SLabel(String text, int alignment) {
        super();
        _installSwingClass();
        // Block-level so the label fills the region its LayoutManager gave
        // it — an inline span shrink-wraps, leaving text-align nothing to
        // align within. Matches AWT, where a Label fills its region.
        getElement().getStyle().set("display", "block");
        setAlignment(alignment);
        setText(text);
    }

    /**
     * @param text null renders blank — Vaadin's element text content cannot
     *        hold null
     */
    @Override
    public void setText(String text) {
        super.setText(text == null ? "" : text);
    }

    // getText() stays the inherited HasText one: it reads back what setText
    // wrote and never returns null.

    /** @return {@link #LEFT} / {@link #CENTER} / {@link #RIGHT}; never lossy — the CSS keywords are in bijection with them */
    public int getAlignment() {
        return cssToAlignment(getElement().getStyle().get("text-align"));
    }

    /**
     * @throws IllegalArgumentException on a value outside {@link #LEFT} /
     *         {@link #CENTER} / {@link #RIGHT}, before anything is written,
     *         so the label is left untouched (R_match_swing_errors)
     */
    public void setAlignment(int alignment) {
        getElement().getStyle().set("text-align", alignmentToCss(alignment));
    }

    /** The switch doubles as JDK {@code Label.setAlignment}'s validator, message included. */
    private static String alignmentToCss(int alignment) {
        return switch (alignment) {
            case LEFT -> "left";
            case CENTER -> "center";
            case RIGHT -> "right";
            default -> throw new IllegalArgumentException("improper alignment: " + alignment);
        };
    }

    private static int cssToAlignment(String css) {
        if (css == null) return LEFT;
        return switch (css) {
            case "center" -> CENTER;
            case "right" -> RIGHT;
            default -> LEFT;
        };
    }

    /** AWT's {@code Label.paramString} tail: the alignment keyword and the text. */
    protected String paramString() {
        return "align=" + alignmentToCss(getAlignment()) + ",text=" + getText();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
