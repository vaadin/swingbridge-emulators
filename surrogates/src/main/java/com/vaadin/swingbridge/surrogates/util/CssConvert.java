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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.util.Locale;

/**
 * Stateless conversions between AWT value types ({@link Cursor}, {@link Color},
 * {@link Font}) and the CSS-string dialect their Vaadin peers speak, plus the
 * inverse parsers that let surrogate getters reconstruct the AWT value by
 * reading the DOM style back (no server-side shadow cache — Vaadin-first per
 * SD_vaadin_first_binding). Pure functions, no state; shared by both modules — {@code :emulators}
 * (which stores Swing-side fields per R_swing_is_truth and so only needs the forward
 * AWT→CSS direction) and {@code :surrogates} (which round-trips through the
 * DOM and needs the reverse parsers too).
 *
 * <p>Lives in {@code com.vaadin.swingbridge.surrogates} as the lowest module both sides share
 * ({@code :emulators} has an {@code api} dependency on {@code :surrogates}):
 * one copy of each converter, called from both modules.
 *
 * <p>Border↔CSS conversion is deliberately <em>not</em> here — it carries real
 * internal coupling (private CSS readers, per-UI {@code <style>} injection,
 * TitledBorder machinery) and stays in {@link BorderCss}.
 */
public final class CssConvert {

    private CssConvert() {}

    // --- Swing text "columns" → CSS width -----------------------------

    /**
     * Swing's {@code columns} hint → a CSS width for the field's <em>host</em>
     * element: {@code "calc(<n>ch + 2em)"}, or {@code null} for {@code n <= 0}
     * ("no hint" — let the peer pick its default).
     *
     * <pre>{@code
     * new SJTextField().setColumns(3);   // host width: calc(3ch + 2em)
     * }</pre>
     *
     * <p>{@code ch} is the width of a {@code "0"} glyph, close enough to
     * Swing's character-column semantics under R_layouts_close_enough. The {@code 2em} term is what
     * makes the hint mean what it says: Vaadin fields are {@code border-box},
     * so the host width has to carry the field's own chrome — input-field
     * padding, borders, and the inner {@code <input>}'s own padding — on top of
     * the text. Measured at 22px against a 12px font, i.e. just under
     * {@code 2em}; being font-relative it stays proportional as the font grows,
     * and errs slightly wide rather than clipping.
     *
     * <p><b>Not {@code n + "ch"}.</b> That sizes the host to the text
     * alone, so the chrome eats the whole budget: measured in-browser, a
     * 3-column field ended up with <b>negative</b> room for text and
     * rendered about one digit wide. Nothing server-side can catch that —
     * the CSS is valid and Karibu asserts only the string — so the value
     * this returns is browser-verified, and the Sampler {@code Focus}
     * route's 3-digit phone fields are where it shows.
     */
    public static String columnsToCssWidth(int columns) {
        return columns > 0 ? "calc(" + columns + "ch + 2em)" : null;
    }

    // --- Cursor ↔ CSS -------------------------------------------------

    /**
     * AWT {@link Cursor} → CSS {@code cursor} keyword for the 14 predefined
     * cursor types. Returns {@code null} for {@link Cursor#CUSTOM_CURSOR} (or
     * any unknown type) — we cannot rasterize a custom cursor image to a
     * {@code data:} URL without a Graphics implementation, so the caller
     * should {@code onUnimplemented} and skip writing CSS (letting the style
     * inherit).
     */
    public static String toCssCursor(Cursor cursor) {
        return switch (cursor.getType()) {
            case Cursor.DEFAULT_CURSOR    -> "default";
            case Cursor.CROSSHAIR_CURSOR  -> "crosshair";
            case Cursor.TEXT_CURSOR       -> "text";
            case Cursor.WAIT_CURSOR       -> "wait";
            case Cursor.SW_RESIZE_CURSOR  -> "sw-resize";
            case Cursor.SE_RESIZE_CURSOR  -> "se-resize";
            case Cursor.NW_RESIZE_CURSOR  -> "nw-resize";
            case Cursor.NE_RESIZE_CURSOR  -> "ne-resize";
            case Cursor.N_RESIZE_CURSOR   -> "n-resize";
            case Cursor.S_RESIZE_CURSOR   -> "s-resize";
            case Cursor.W_RESIZE_CURSOR   -> "w-resize";
            case Cursor.E_RESIZE_CURSOR   -> "e-resize";
            case Cursor.HAND_CURSOR       -> "pointer";
            case Cursor.MOVE_CURSOR       -> "move";
            default -> null;
        };
    }

    /**
     * CSS {@code cursor} keyword → predefined AWT {@link Cursor}. Inverse of
     * {@link #toCssCursor(Cursor)}; lets {@code getCursor()} reconstruct the
     * Swing value by reading the DOM style directly (no server-side cache).
     * Unknown or non-predefined keywords fall through to
     * {@link Cursor#getDefaultCursor()} — the caller gets a non-null Cursor
     * per Swing's convention, which matters for migrated code that chains off
     * the result.
     */
    public static Cursor cursorFromCss(String css) {
        if (css == null || css.isEmpty()) return Cursor.getDefaultCursor();
        int type = switch (css) {
            case "default"    -> Cursor.DEFAULT_CURSOR;
            case "crosshair"  -> Cursor.CROSSHAIR_CURSOR;
            case "text"       -> Cursor.TEXT_CURSOR;
            case "wait"       -> Cursor.WAIT_CURSOR;
            case "sw-resize"  -> Cursor.SW_RESIZE_CURSOR;
            case "se-resize"  -> Cursor.SE_RESIZE_CURSOR;
            case "nw-resize"  -> Cursor.NW_RESIZE_CURSOR;
            case "ne-resize"  -> Cursor.NE_RESIZE_CURSOR;
            case "n-resize"   -> Cursor.N_RESIZE_CURSOR;
            case "s-resize"   -> Cursor.S_RESIZE_CURSOR;
            case "w-resize"   -> Cursor.W_RESIZE_CURSOR;
            case "e-resize"   -> Cursor.E_RESIZE_CURSOR;
            case "pointer"    -> Cursor.HAND_CURSOR;
            case "move"       -> Cursor.MOVE_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        };
        return Cursor.getPredefinedCursor(type);
    }

    // --- Size (CSS px string → int) -----------------------------------

    /**
     * Parse a Vaadin CSS size string (from {@code HasSize#getWidth} /
     * {@code HasSize#getHeight}) back to an {@code int} pixel count. Returns
     * {@code 1} for {@code null}, empty, non-pixel units ({@code "%"},
     * {@code "em"}, …), or any unparseable value — <em>not zero</em>: Swing
     * migration code often guards on {@code comp.getWidth() > 0} as a
     * "component is visible" check, and returning 0 for a real-but-CSS-sized
     * component would trip that guard and change behaviour silently.
     *
     * <p>Accepts fractional {@code px} values ({@code "12.5px"}) by
     * truncating — AWT dimensions are integer.
     */
    public static int parsePxOr1(String css) {
        if (css == null || css.isEmpty()) return 1;
        if (!css.endsWith("px")) return 1;
        String numeric = css.substring(0, css.length() - 2);
        try {
            double v = Double.parseDouble(numeric);
            int px = (int) v;
            return px > 0 ? px : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // --- Color ↔ CSS --------------------------------------------------

    /** AWT {@link Color} → CSS. {@code rgb()} if opaque, {@code rgba()} otherwise. */
    public static String toCss(Color color) {
        int r = color.getRed(), g = color.getGreen(), b = color.getBlue(), a = color.getAlpha();
        if (a == 255) {
            return "rgb(" + r + "," + g + "," + b + ")";
        }
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)", r, g, b, a / 255.0);
    }

    /**
     * Parse a CSS color string written by {@link #toCss(Color)} back to an
     * AWT {@link Color}. Handles {@code rgb(r,g,b)} and
     * {@code rgba(r,g,b,a)} with the alpha as a decimal fraction. Returns
     * {@code null} for null/empty/unparseable inputs — named colors, hex
     * literals, {@code hsl()}, and other forms aren't recognised, which is
     * the accepted lossiness of the Vaadin-first stance (SD_vaadin_first_binding): if user
     * code writes a color via {@code getElement().getStyle().set("color",
     * "red")} bypassing the mixin, the surrogate-side getter returns null.
     *
     * <p>Alpha round-trip: {@code Color(r,g,b,128)} writes
     * {@code "rgba(r,g,b,0.502)"}; reading back computes
     * {@code Math.round(0.502 * 255) = 128}. One-bit slop is possible on
     * extreme values (e.g. 1 → 0.004 → round-trips to 1); generally safe.
     */
    public static Color colorFromCss(String css) {
        if (css == null) return null;
        String s = css.trim();
        if (s.isEmpty()) return null;
        try {
            if (s.startsWith("rgb(") && s.endsWith(")")) {
                String[] parts = s.substring(4, s.length() - 1).split(",");
                if (parts.length != 3) return null;
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new Color(r, g, b);
            }
            if (s.startsWith("rgba(") && s.endsWith(")")) {
                String[] parts = s.substring(5, s.length() - 1).split(",");
                if (parts.length != 4) return null;
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                double a = Double.parseDouble(parts[3].trim());
                int alpha = (int) Math.round(a * 255.0);
                if (alpha < 0) alpha = 0;
                if (alpha > 255) alpha = 255;
                return new Color(r, g, b, alpha);
            }
        } catch (NumberFormatException ignored) {
            // Fall through — malformed numbers count as unparseable.
        }
        return null;
    }

    /**
     * AWT {@link Color} → {@code #rrggbb} hex, the dialect the browser
     * {@code <input type="color">} {@code value} property speaks. Alpha is
     * dropped — a color input is opaque-only, so this is deliberately lossy
     * (documented in SD_sjcolorchooser). Use {@link #toCss(Color)} for the general
     * background/foreground CSS path where alpha matters.
     */
    public static String toHexColor(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Parse a {@code #rrggbb} (or {@code #rgb} shorthand) hex color as written
     * by {@link #toHexColor(Color)} — or as echoed back by the browser
     * {@code <input type="color">} — into an AWT {@link Color}. Returns
     * {@code null} for null/empty/malformed input (Vaadin-first lossiness per
     * SD_vaadin_first_binding, matching {@link #colorFromCss(String)}'s null-on-unparseable stance).
     * The result is always opaque (alpha 255) since the color input carries no
     * alpha channel.
     */
    public static Color colorFromHex(String hex) {
        if (hex == null) return null;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 3) {
                // #rgb shorthand — some browsers echo it. Expand each nibble.
                int r = Integer.parseInt(s.substring(0, 1).repeat(2), 16);
                int g = Integer.parseInt(s.substring(1, 2).repeat(2), 16);
                int b = Integer.parseInt(s.substring(2, 3).repeat(2), 16);
                return new Color(r, g, b);
            }
            if (s.length() == 6) {
                return new Color(Integer.parseInt(s, 16));
            }
        } catch (NumberFormatException ignored) {
            // Fall through — malformed hex counts as unparseable.
        }
        return null;
    }

    // --- Font ↔ CSS ---------------------------------------------------

    /**
     * Stand-in for the Look-and-Feel-installed default font that a real Swing
     * component carries from construction (UIManager {@code "TextField.font"}
     * and friends). Emulator/surrogate components run no L&amp;F, so nothing
     * installs a per-component font — this fills the same role so
     * {@code getFont()} honours Swing's non-null contract and the pervasive
     * GUI-builder idiom {@code comp.setFont(comp.getFont().deriveFont(...))}
     * (which every NetBeans/Matisse {@code initComponents()} emits) doesn't
     * NPE. {@code Dialog}/plain/12 matches AWT's own logical default and the
     * exact fallbacks {@link #fontFromCss} already collapses to, so a value
     * read back through {@code getFont()} is self-consistent whether it came
     * from this constant or from reconstructed CSS.
     */
    public static final Font DEFAULT_FONT = new Font(Font.DIALOG, Font.PLAIN, 12);

    /**
     * AWT logical font families mapped to CSS generics; everything else is
     * quoted with a {@code sans-serif} fallback.
     */
    public static String toCssFontFamily(Font font) {
        String family = font.getFamily();
        return switch (family) {
            case "Dialog", "SansSerif" -> "sans-serif";
            case "Serif" -> "serif";
            case "Monospaced", "DialogInput" -> "monospace";
            default -> "\"" + family.replace("\"", "\\\"") + "\", sans-serif";
        };
    }

    /**
     * Reverse of {@link #toCssFontFamily(Font)}: map CSS generic font
     * family names back to AWT logical family names. First-token-wins for
     * comma-separated stacks; quoted specifics unquote to their raw name.
     * Returns {@code null} for null/empty/unparseable inputs.
     */
    public static String awtFontFamilyFromCss(String css) {
        if (css == null) return null;
        String s = css.trim();
        if (s.isEmpty()) return null;
        int comma = s.indexOf(',');
        String first = (comma < 0 ? s : s.substring(0, comma)).trim();
        if (first.length() >= 2 && first.startsWith("\"") && first.endsWith("\"")) {
            return first.substring(1, first.length() - 1).replace("\\\"", "\"");
        }
        return switch (first) {
            case "sans-serif" -> "SansSerif";
            case "serif"      -> "Serif";
            case "monospace"  -> "Monospaced";
            default           -> first;
        };
    }

    /**
     * Reconstruct an AWT {@link Font} by reading the {@code font-family},
     * {@code font-size}, {@code font-weight}, and {@code font-style} slots
     * individually. Returns {@code null} when none of the four are set —
     * matches Swing's "no font installed" state.
     *
     * <p>Lossy: weights other than {@code bold}/{@code normal} collapse to
     * plain; font-size in non-px units falls back to 12 (AWT's default
     * size); intermediate CSS (e.g. {@code font-size: calc(1em + 2px)})
     * reads back as 12.
     */
    public static Font fontFromCss(String family, String size, String weight, String style) {
        if (family == null && size == null && weight == null && style == null) return null;
        String awtFamily = awtFontFamilyFromCss(family);
        if (awtFamily == null) awtFamily = "Dialog";
        int fontStyle = 0;
        if ("bold".equalsIgnoreCase(weight)) fontStyle |= Font.BOLD;
        if ("italic".equalsIgnoreCase(style)) fontStyle |= Font.ITALIC;
        int fontSize = 12;
        if (size != null && size.endsWith("px")) {
            try {
                fontSize = (int) Double.parseDouble(size.substring(0, size.length() - 2));
                if (fontSize <= 0) fontSize = 12;
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return new Font(awtFamily, fontStyle, fontSize);
    }
}
