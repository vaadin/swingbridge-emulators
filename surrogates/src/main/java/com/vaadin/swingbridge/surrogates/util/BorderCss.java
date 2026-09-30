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

import com.vaadin.swingbridge.surrogates.SHelper;

import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.Style;

import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Insets;

/**
 * Border ↔ CSS conversion for the surrogate {@code JComponentMixin.setBorder} /
 * {@code getBorder} path: {@link #applyBorderCss} writes a JDK {@link Border} as
 * inline CSS, {@link #borderFromCss} reconstructs the closest {@link Border}
 * back (lossy per SD_border_css_lossy). Includes the {@link TitledBorder} pseudo-element
 * rendering, whose one-off {@code ::before} rule is injected per UI via the
 * generic {@link VaadinUtils#injectStyleOnce} primitive.
 *
 * <p>Color conversion delegates to {@link CssConvert}. Shared with
 * {@code :emulators} in the SD_shelper_statics-allowed direction — the emulator
 * {@code vaadinx.swing.border.TitledBorder} calls
 * {@link #ensureTitledBorderStyleInjected} directly.
 */
public final class BorderCss {

    private BorderCss() {}

    /**
     * Every inline-style key {@link #applyBorderCss} might write. Kept in
     * one place so {@link #clearBorderCss} covers the complete surface —
     * any border-family property we set must be listed here so
     * {@code setBorder(newBorder)} doesn't leave the previous border's
     * keys lingering.
     */
    private static final String[] BORDER_CSS_KEYS = {
            "border",
            "border-top", "border-right", "border-bottom", "border-left",
            "border-width", "border-style", "border-color",
            "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
            "border-top-style", "border-right-style", "border-bottom-style", "border-left-style",
            "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
            "border-radius",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
    };

    /**
     * Remove every border-related CSS key we might have written, plus
     * the {@code data-emul-border-title} attribute that
     * {@link #applyBorderCss} writes for {@link TitledBorder}. Used
     * before each write, and on {@code setBorder(null)}.
     */
    public static void clearBorderCss(Element element) {
        Style style = element.getStyle();
        for (String key : BORDER_CSS_KEYS) {
            style.remove(key);
        }
        element.removeAttribute("data-emul-border-title");
    }

    // --- TitledBorder CSS rendering ----------
    //
    // Pseudo-element rendering (::before with attr() content) isn't
    // addressable from inline Style, so we inject a one-off <style>
    // element per page. The rule targets [data-emul-border-title] and
    // reads the title via attr() — standard CSS, no shadow DOM piercing
    // required.

    /** The CSS rule that renders the title via {@code ::before}. */
    private static final String TITLED_BORDER_CSS = """
            [data-emul-border-title] {
                position: relative;
            }
            [data-emul-border-title]::before {
                content: attr(data-emul-border-title);
                position: absolute;
                top: -0.6em;
                left: 0.75em;
                padding: 0 0.3em;
                background: var(--vaadin-background-color, white);
                color: var(--vaadin-text-color-secondary, currentcolor);
                font-size: 0.85em;
                line-height: 1;
                pointer-events: none;
            }
            """;

    /** Sentinel marking that a UI already has the stylesheet element attached. */
    private static final String TITLED_BORDER_STYLE_KEY = "emul.titled-border-style-element";

    /**
     * Ensure the {@code [data-emul-border-title]::before} rule is installed on
     * the current page (and stays installed across {@code @PreserveOnRefresh}
     * reloads). Thin wrapper over {@link VaadinUtils#injectStyleOnce}; no-op when
     * no UI is current.
     */
    public static void ensureTitledBorderStyleInjected(com.vaadin.flow.component.UI ui) {
        VaadinUtils.injectStyleOnce(ui, TITLED_BORDER_STYLE_KEY, "border-title", TITLED_BORDER_CSS);
    }

    /**
     * Write the CSS representation of {@code border} onto {@code element}.
     * Dispatches by runtime type across the JDK border hierarchy:
     *
     * <ul>
     *   <li>{@link LineBorder} → {@code border: Npx solid color} (+ optional {@code border-radius}).</li>
     *   <li>{@link EmptyBorder} → {@code padding}.</li>
     *   <li>{@link MatteBorder} (color-only) → four per-side {@code border-X} longhands.</li>
     *   <li>{@link BevelBorder} / {@link SoftBevelBorder} → {@code border: 2px inset|outset color}.</li>
     *   <li>{@link EtchedBorder} → {@code border: 2px groove|ridge color}.</li>
     *   <li>{@link CompoundBorder} with {@code EmptyBorder} inner → outer border + inner's insets as padding. Other inner types WARN.</li>
     *   <li>{@link TitledBorder} → inner border (or {@code 1px solid}
     *       default) + {@code data-emul-border-title} attribute carrying
     *       the title text; pseudo-element CSS rule injected once per
     *       UI via {@link #ensureTitledBorderStyleInjected}.</li>
     *   <li>Any other {@link Border} (including custom user subclasses) → WARN.</li>
     * </ul>
     *
     * <p>{@link #clearBorderCss} runs first so the previous border's keys
     * can't linger.
     *
     * <p>{@code border = null} clears without writing — same semantics as
     * Swing's {@code setBorder(null)}.
     */
    public static void applyBorderCss(Element element, Border border) {
        clearBorderCss(element);
        if (border == null) return;

        // Order matters: MatteBorder extends EmptyBorder, SoftBevelBorder
        // extends BevelBorder — more specific branches must come first.
        if (border instanceof LineBorder lb) {
            writeLineBorder(element, lb.getLineColor(), lb.getThickness(), lb.getRoundedCorners());
        } else if (border instanceof MatteBorder mb) {
            Insets i = mb.getBorderInsets();
            Color color = mb.getMatteColor();
            if (color == null) {
                // Icon-backed MatteBorder — no CSS analog.
                SHelper.onUnimplemented(element, "setBorder/MatteBorder-icon", mb);
                return;
            }
            writeMatteBorder(element, i, color);
        } else if (border instanceof EmptyBorder eb) {
            writePadding(element, eb.getBorderInsets());
        } else if (border instanceof SoftBevelBorder sbb) {
            writeBevelBorder(element, sbb.getBevelType(), sbb.getHighlightOuterColor());
        } else if (border instanceof BevelBorder bb) {
            writeBevelBorder(element, bb.getBevelType(), bb.getHighlightOuterColor());
        } else if (border instanceof EtchedBorder eb) {
            writeEtchedBorder(element, eb.getEtchType(), eb.getHighlightColor());
        } else if (border instanceof CompoundBorder cb) {
            Border inner = cb.getInsideBorder();
            Border outer = cb.getOutsideBorder();
            if (inner instanceof EmptyBorder innerEmpty && outer != null) {
                applyBorderCss(element, outer);          // writes border + clears first
                writePadding(element, innerEmpty.getBorderInsets());
            } else if (inner == null && outer != null) {
                applyBorderCss(element, outer);
            } else if (outer == null && inner != null) {
                applyBorderCss(element, inner);
            } else {
                SHelper.onUnimplemented(element, "setBorder/CompoundBorder-nested-non-empty", cb);
            }
        } else if (border instanceof TitledBorder tb) {
            // Pseudo-element rendering via the
            // session-scoped style rule injected by
            // ensureTitledBorderStyleInjected. The title text rides on
            // a data-attribute the rule's attr() reads.
            ensureTitledBorderStyleInjected(com.vaadin.flow.component.UI.getCurrent());
            Border inner = tb.getBorder();
            if (inner != null) {
                applyBorderCss(element, inner);  // Recurses; clearBorderCss runs again (harmless).
            } else {
                // No inside border — render the default 1px solid line so
                // the title has something to sit on. Matches JDK's default
                // TitledBorder rendering.
                element.getStyle().set("border", "1px solid currentcolor");
            }
            // applyBorderCss(inner) above called clearBorderCss which
            // wiped the data-attribute we're about to set; write it AFTER
            // the recursive call.
            String title = tb.getTitle();
            element.setAttribute("data-emul-border-title", title == null ? "" : title);
        } else {
            // Custom Border subclass, or JDK type we don't cover.
            SHelper.onUnimplemented(element, "setBorder/custom-Border-impl", border);
        }
    }

    private static void writeLineBorder(Element element, Color color, int thickness, boolean rounded) {
        Style style = element.getStyle();
        String css = thickness + "px solid "
                + (color == null ? "currentcolor" : CssConvert.toCss(color));
        style.set("border", css);
        if (rounded) {
            style.set("border-radius", (thickness * 2) + "px");
        }
    }

    private static void writePadding(Element element, Insets i) {
        // AWT Insets (top,left,bottom,right) ↔ CSS padding (top right bottom left).
        element.getStyle().set("padding",
                i.top + "px " + i.right + "px " + i.bottom + "px " + i.left + "px");
    }

    private static void writeMatteBorder(Element element, Insets i, Color color) {
        // Write per-side longhands (width/style/color triples) so the
        // symmetric reader can pick each side up without shorthand parsing.
        String col = CssConvert.toCss(color);
        Style style = element.getStyle();
        for (String side : new String[]{"top", "right", "bottom", "left"}) {
            int px = switch (side) {
                case "top"    -> i.top;
                case "right"  -> i.right;
                case "bottom" -> i.bottom;
                default       -> i.left;
            };
            if (px > 0) {
                style.set("border-" + side + "-width", px + "px");
                style.set("border-" + side + "-style", "solid");
                style.set("border-" + side + "-color", col);
            }
        }
    }

    private static void writeBevelBorder(Element element, int bevelType, Color highlightOuter) {
        // BevelBorder.LOWERED=1, RAISED=0. CSS inset/outset approximate.
        String cssStyle = (bevelType == BevelBorder.LOWERED) ? "inset" : "outset";
        Color color = (highlightOuter != null) ? highlightOuter : Color.GRAY;
        element.getStyle().set("border", "2px " + cssStyle + " " + CssConvert.toCss(color));
    }

    private static void writeEtchedBorder(Element element, int etchType, Color highlight) {
        // EtchedBorder.LOWERED=1, RAISED=0.
        String cssStyle = (etchType == EtchedBorder.LOWERED) ? "groove" : "ridge";
        Color color = (highlight != null) ? highlight : Color.GRAY;
        element.getStyle().set("border", "2px " + cssStyle + " " + CssConvert.toCss(color));
    }

    /**
     * Reconstruct the closest JDK {@link Border} from the CSS currently
     * written on {@code element}. Reverse of {@link #applyBorderCss} —
     * lossy per SD_border_css_lossy: custom Borders, TitledBorder, CompoundBorder with a
     * non-EmptyBorder inner, and icon-backed MatteBorder can't round-trip.
     *
     * <p>Dispatch:
     * <ul>
     *   <li>No border keys, no padding → {@code null}.</li>
     *   <li>Padding only → {@link EmptyBorder}.</li>
     *   <li>Uniform {@code border} with {@code solid} style → {@link LineBorder}
     *       (picks up {@code border-radius} into {@code roundedCorners}).</li>
     *   <li>Uniform {@code border} with {@code inset}/{@code outset} → {@link BevelBorder}.</li>
     *   <li>Uniform {@code border} with {@code groove}/{@code ridge} → {@link EtchedBorder}.</li>
     *   <li>Mixed per-side widths/colors → {@link MatteBorder}.</li>
     *   <li>Border + padding → {@link CompoundBorder} of reconstructed outer + {@link EmptyBorder} inner.</li>
     * </ul>
     */
    public static Border borderFromCss(Element element) {
        Style style = element.getStyle();
        Insets padding = readPaddingInsets(style);
        Border visualBorder = readVisualBorder(style);
        if (visualBorder == null) {
            if (padding == null) return null;
            return new EmptyBorder(padding);
        }
        if (padding == null) {
            return visualBorder;
        }
        return new CompoundBorder(visualBorder, new EmptyBorder(padding));
    }

    /** Read padding insets from CSS; {@code null} when nothing set. Reads shorthand first, falls back to longhands. */
    private static Insets readPaddingInsets(Style style) {
        String shorthand = style.get("padding");
        if (shorthand != null && !shorthand.isBlank()) {
            int[] trbl = parsePaddingShorthand(shorthand);
            if (trbl != null) return new Insets(trbl[0], trbl[3], trbl[2], trbl[1]);
        }
        int top = readPx(style.get("padding-top"), 0);
        int right = readPx(style.get("padding-right"), 0);
        int bottom = readPx(style.get("padding-bottom"), 0);
        int left = readPx(style.get("padding-left"), 0);
        if (top == 0 && right == 0 && bottom == 0 && left == 0) return null;
        return new Insets(top, left, bottom, right);
    }

    /**
     * Parse CSS {@code padding} shorthand into {@code [top,right,bottom,left]}.
     * Accepts 1/2/3/4 value forms per CSS spec. Returns {@code null} for
     * unparseable (non-px units, calc, …).
     */
    private static int[] parsePaddingShorthand(String css) {
        String[] parts = css.trim().split("\\s+");
        int[] vals = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].endsWith("px")) return null;
            try {
                vals[i] = (int) Double.parseDouble(parts[i].substring(0, parts[i].length() - 2));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (parts.length) {
            case 1 -> new int[]{vals[0], vals[0], vals[0], vals[0]};
            case 2 -> new int[]{vals[0], vals[1], vals[0], vals[1]};
            case 3 -> new int[]{vals[0], vals[1], vals[2], vals[1]};
            case 4 -> vals;
            default -> null;
        };
    }

    /**
     * Read the visual border from CSS. Returns {@code null} when no border
     * is set. Dispatches to LineBorder / BevelBorder / EtchedBorder /
     * MatteBorder based on uniformity + style keyword.
     */
    private static Border readVisualBorder(Style style) {
        String shorthand = style.get("border");
        int[] widths = readBorderWidths(style);
        String[] styles = readBorderStyles(style);
        String[] colorsCss = readBorderColors(style);

        if (shorthand != null) {
            Border parsed = parseUniformBorderShorthand(shorthand);
            if (parsed != null) {
                // Pick up border-radius even when the shorthand set width/style/color.
                if (parsed instanceof LineBorder lb) {
                    boolean rounded = style.get("border-radius") != null;
                    if (rounded && !lb.getRoundedCorners()) {
                        return new LineBorder(lb.getLineColor(), lb.getThickness(), true);
                    }
                }
                return parsed;
            }
        }

        if (widths == null && styles == null && colorsCss == null) return null;
        if (widths == null) widths = new int[]{0, 0, 0, 0};

        // Uniform across sides?
        boolean uniformW = allEqual(widths);
        boolean uniformS = styles != null && allEqual(styles);
        boolean uniformC = colorsCss != null && allEqual(colorsCss);

        if (uniformW && uniformS && uniformC && widths[0] > 0) {
            Color c = CssConvert.colorFromCss(colorsCss[0]);
            return borderForStyle(styles[0], widths[0], c, style.get("border-radius") != null);
        }

        // Mixed widths or styles ⇒ MatteBorder on the first non-null color.
        Color c = null;
        if (colorsCss != null) {
            for (String cc : colorsCss) {
                c = CssConvert.colorFromCss(cc);
                if (c != null) break;
            }
        }
        if (c == null) return null;
        return new MatteBorder(widths[0], widths[3], widths[2], widths[1], c);
    }

    /** Parse {@code border: Npx STYLE COLOR} uniform shorthand. Returns null on mismatch. */
    private static Border parseUniformBorderShorthand(String shorthand) {
        String[] parts = shorthand.trim().split("\\s+", 3);
        if (parts.length < 2) return null;
        if (!parts[0].endsWith("px")) return null;
        int width;
        try {
            width = (int) Double.parseDouble(parts[0].substring(0, parts[0].length() - 2));
        } catch (NumberFormatException e) {
            return null;
        }
        String styleKw = parts[1];
        String colorCss = parts.length == 3 ? parts[2] : null;
        Color color = colorCss == null ? null : CssConvert.colorFromCss(colorCss);
        return borderForStyle(styleKw, width, color, false);
    }

    private static Border borderForStyle(String styleKw, int width, Color color, boolean rounded) {
        return switch (styleKw) {
            case "solid"  -> color == null ? null : new LineBorder(color, width, rounded);
            case "inset"  -> new BevelBorder(BevelBorder.LOWERED, color, color);
            case "outset" -> new BevelBorder(BevelBorder.RAISED, color, color);
            case "groove" -> new EtchedBorder(EtchedBorder.LOWERED, color, color);
            case "ridge"  -> new EtchedBorder(EtchedBorder.RAISED, color, color);
            default       -> null;
        };
    }

    /** Read four border widths as {[top, right, bottom, left]} px. Null when no longhand set. */
    private static int[] readBorderWidths(Style style) {
        return readPerSidePx(style, "border-", "-width");
    }

    /** Read four border styles. Null when no longhand set. */
    private static String[] readBorderStyles(Style style) {
        return readPerSideStr(style, "border-", "-style");
    }

    /** Read four border colors (as CSS strings). Null when no longhand set. */
    private static String[] readBorderColors(Style style) {
        return readPerSideStr(style, "border-", "-color");
    }

    private static int[] readPerSidePx(Style style, String prefix, String suffix) {
        String t = style.get(prefix + "top" + suffix);
        String r = style.get(prefix + "right" + suffix);
        String b = style.get(prefix + "bottom" + suffix);
        String l = style.get(prefix + "left" + suffix);
        if (t == null && r == null && b == null && l == null) return null;
        return new int[]{readPx(t, 0), readPx(r, 0), readPx(b, 0), readPx(l, 0)};
    }

    private static String[] readPerSideStr(Style style, String prefix, String suffix) {
        String t = style.get(prefix + "top" + suffix);
        String r = style.get(prefix + "right" + suffix);
        String b = style.get(prefix + "bottom" + suffix);
        String l = style.get(prefix + "left" + suffix);
        if (t == null && r == null && b == null && l == null) return null;
        return new String[]{nullToEmpty(t), nullToEmpty(r), nullToEmpty(b), nullToEmpty(l)};
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static int readPx(String css, int fallback) {
        if (css == null || !css.endsWith("px")) return fallback;
        try {
            return (int) Double.parseDouble(css.substring(0, css.length() - 2));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean allEqual(int[] a) {
        return a[0] == a[1] && a[1] == a[2] && a[2] == a[3];
    }

    private static boolean allEqual(String[] a) {
        return a[0].equals(a[1]) && a[1].equals(a[2]) && a[2].equals(a[3]);
    }
}
