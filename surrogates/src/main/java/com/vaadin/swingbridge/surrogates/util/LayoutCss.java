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

/**
 * Builders that translate AWT/Swing {@code LayoutManager} parameters into the
 * container-wide CSS a Vaadin host element needs, plus {@link #applyContainerCss}
 * which writes a built block onto that element. One-way build + apply — there is
 * no round-trip back to a {@code LayoutManager} (layout is best-effort per R_best_effort_behaviour/R_layouts_close_enough),
 * so this is deliberately not an {@code S*Convert}-shaped pair.
 *
 * <p>Shared by both modules in the SD_shelper_statics-allowed direction ({@code :emulators →
 * :surrogates}): emulator {@code LayoutManager}s route their output through
 * {@code vaadinx.EHelper.applyContainerCss} (a thin forwarder to
 * {@link #applyContainerCss}); the surrogate {@code ContainerMixin} calls the
 * builders directly. {@code BoxLayout} axis constants are referenced by literal
 * {@code int} (that layout lives in {@code :emulators}, which {@code :surrogates}
 * can't depend on); {@link java.awt.FlowLayout}'s constants are JDK-side and
 * referenced by symbol.
 *
 * <h2>Implementation details</h2>
 *
 * <p>Each grid/flex builder writes an explicit value for <em>every</em> key any
 * sibling builder might have written, not just its own — a JPanel's default ctor
 * installs FlowLayout (writing {@code flex-wrap} / {@code align-items} /
 * {@code justify-content} / {@code gap}) and other layouts write
 * {@code grid-template-areas}, so a later {@code setLayout(...)} that set only its
 * own keys would inherit stale ones (a leftover {@code flex-wrap: wrap} wraps a
 * BoxLayout column into several visual columns, a leftover named-areas template
 * pins children into the wrong grid cells). The per-builder reset comments note
 * only the <em>layout-specific</em> consequence.
 */
public final class LayoutCss {

    private LayoutCss() {}

    /**
     * Apply a block of layout CSS to a container's host element. Gates on the
     * element being a plain {@code <div>}: non-Div hosts get a
     * {@link SHelper#onUnsupportedPeerShape} ERROR log and no CSS is written — setting a
     * {@code LayoutManager} on a surrogate whose element is, say, a
     * {@code <vaadin-button>} would corrupt its rendering.
     *
     * <p>{@code null} values remove the corresponding style property, mirroring
     * the {@code null → inherit from parent} contract used elsewhere in the
     * Vaadin-first style layer (see {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin#setBackground} convention).
     */
    public static void applyContainerCss(Element target, String layoutManagerName, java.util.Map<String, String> css) {
        if (!"div".equalsIgnoreCase(target.getTag())) {
            SHelper.onUnsupportedPeerShape(layoutManagerName,
                    "container element is <" + target.getTag() + ">, not <div> — layout CSS not applied");
            return;
        }
        Style style = target.getStyle();
        css.forEach((k, v) -> {
            if (v == null) style.remove(k);
            else style.set(k, v);
        });
    }

    /**
     * Apply per-child layout CSS to one child's element — the per-child leg of a
     * {@code CssEmittingLayoutManager} pass. Unlike {@link #applyContainerCss} there is
     * no {@code <div>} gate: a child is any element. {@code null} removes, absent key
     * untouched (same key-by-key contract).
     */
    public static void applyChildCss(Element target, java.util.Map<String, String> css) {
        Style style = target.getStyle();
        css.forEach((k, v) -> {
            if (v == null) style.remove(k);
            else style.set(k, v);
        });
    }

    /**
     * The custom property through which a child's <em>preferred width</em> reaches CSS,
     * and through which its parent layout overrides it. See {@link #sizingCss} for the
     * two-owner contract; {@code D_layout_owns_child_sizing} for why it exists.
     */
    public static final String PREF_W_VAR = "--emul-layout-w";

    /** Vertical counterpart of {@link #PREF_W_VAR}. */
    public static final String PREF_H_VAR = "--emul-layout-h";

    /**
     * The value a layout writes into {@link #PREF_W_VAR} / {@link #PREF_H_VAR} to take
     * an axis over: {@code auto} restores the container's own {@code stretch}, which
     * sizes the border box (where {@code width: 100%} overflows a content-box element by
     * its border).
     */
    public static final String AXIS_LAYOUT = "auto";

    /**
     * The value a layout writes to leave an axis to the child's preferred size.
     * {@code initial} on a custom property is the guaranteed-invalid value, so the
     * child's {@code var(--emul-layout-w, <pref>px)} falls back to the pref — which is
     * what lets a layout express its whole policy in the variable and never touch
     * {@code width}.
     */
    public static final String AXIS_PREF = "initial";

    /**
     * Wrap a preferred width in the {@link #PREF_W_VAR} indirection: the value a child
     * writes to its own {@code width}, letting its parent layout override the axis
     * without a write ordering between the two.
     *
     * <p>{@code null} in, {@code null} out — so a caller whose hint is "no preference"
     * ({@link CssConvert#columnsToCssWidth} on zero columns) can pass it straight
     * through to a {@code setWidth} that clears the property.
     *
     * @param fallback the CSS length the child prefers, e.g. {@code "850px"} or
     *                 {@code "calc(10ch + 2em)"}
     */
    public static String prefWidth(String fallback) {
        return fallback == null ? null : "var(" + PREF_W_VAR + ", " + fallback + ")";
    }

    /** Vertical counterpart of {@link #prefWidth}. */
    public static String prefHeight(String fallback) {
        return fallback == null ? null : "var(" + PREF_H_VAR + ", " + fallback + ")";
    }

    /**
     * The per-child half of {@code D_layout_owns_child_sizing}: which axes of one child
     * the parent layout sizes itself, and which it leaves to the child's preferred size.
     *
     * <p>A preferred size means nothing on its own — AWT's child never sizes itself, and
     * {@code layoutContainer} decides per axis whether to consult, clamp or ignore the
     * pref ({@code BorderLayout} NORTH takes the container's width and only the pref
     * <em>height</em>; {@code GridLayout} ignores both). So the child writes the pref as
     * {@code width: var(--emul-layout-w, Npx)} and each layout writes the two variables
     * here, one per axis.
     *
     * <p>Both variables are always written, {@code initial} included: custom properties
     * inherit, so a child that left one unset would read its ancestor's value — a
     * BorderLayout CENTER panel's {@code auto} would otherwise erase the pref widths of
     * every descendant under an absolute-layout child. The {@code @property … inherits:
     * false} registrations in {@code emul/emulator-theme.css} are the other half of that
     * defence; this one holds without the stylesheet.
     *
     * @param layoutSizesWidth  the layout drives the horizontal axis (pref width ignored)
     * @param layoutSizesHeight the layout drives the vertical axis (pref height ignored)
     */
    public static java.util.Map<String, String> sizingCss(boolean layoutSizesWidth,
                                                          boolean layoutSizesHeight) {
        java.util.Map<String, String> css = new java.util.LinkedHashMap<>();
        css.put(PREF_W_VAR, layoutSizesWidth ? AXIS_LAYOUT : AXIS_PREF);
        css.put(PREF_H_VAR, layoutSizesHeight ? AXIS_LAYOUT : AXIS_PREF);
        return css;
    }

    /**
     * Every container-side CSS property a builder in this class can write — the
     * union {@link #resetContainerCss} clears. A new builder that introduces a
     * property must add it here, or a {@code setLayout} replacing one layout with
     * another leaves that property behind; {@code LayoutCssTest} guards the drift
     * by asserting every builder's key set is a subset of this list.
     */
    private static final java.util.List<String> CONTAINER_KEYS = java.util.List.of(
            "display", "flex-direction", "flex-wrap", "gap",
            "justify-content", "justify-items", "align-items", "align-content",
            "grid-template-areas", "grid-template-columns", "grid-template-rows");

    /** The {@link #CONTAINER_KEYS} union, for the drift guard in {@code LayoutCssTest}. */
    public static java.util.List<String> containerKeys() {
        return CONTAINER_KEYS;
    }

    /**
     * Drop every container-side layout property from {@code target} — the undo for
     * {@link #applyContainerCss}, called when a container's {@code LayoutManager} is
     * replaced or cleared.
     *
     * <p>Why an undo is needed at all: AWT's {@code setLayout} only invalidates, so
     * the new manager's container CSS does not land until {@code validate()} /
     * {@code revalidate()}. That quirk is faithful and deliberately kept (see
     * {@code vaadinx.awt.FlowLayout#setAlignment}) — but a browser makes the
     * un-revalidated interval <em>visible</em> in a way Swing never does: the host
     * keeps the old manager's {@code display:flex} while children added under the new
     * manager already carry its {@code grid-area}, so the container renders scrambled
     * rather than merely un-laid-out. Clearing the container side turns that interval
     * into plain block flow — still stale, like Swing's, but coherent.
     *
     * <p>Per-child CSS is deliberately left alone: {@code grid-*} properties are inert
     * without a grid parent, and clearing them would fight {@code GridBagLayout}'s
     * deliberate retention of child {@code width} / {@code height}.
     *
     * <p>Silent no-op on a non-{@code <div>} host — {@link #applyContainerCss} never
     * wrote layout CSS there, so there is nothing to undo and no unsupported shape to report
     * to report.
     */
    public static void resetContainerCss(Element target) {
        if (!"div".equalsIgnoreCase(target.getTag())) return;
        Style style = target.getStyle();
        CONTAINER_KEYS.forEach(style::remove);
    }

    /**
     * Container CSS for the custom-{@code LayoutManager} fallback: a readable
     * vertical stack. Installed by {@code Container.doLayout} when a container's layout
     * is a hand-rolled pixel {@code LayoutManager} the emulator can't translate
     * (M1D_custom_layoutmanager). Stretch-fills the cross axis so
     * children stay full-width and nothing clips; resets {@code grid-template-areas} so
     * a prior grid layout's named cells don't pin children (leftover
     * {@code grid-template-columns}/{@code rows} are inert under {@code display:flex}).
     */
    public static java.util.Map<String, String> verticalFallbackCss() {
        java.util.Map<String, String> css = new java.util.LinkedHashMap<>();
        css.put("display", "flex");
        css.put("flex-direction", "column");
        css.put("flex-wrap", "nowrap");
        css.put("align-items", "stretch");
        css.put("justify-content", "flex-start");
        css.put("gap", "0");
        css.put("grid-template-areas", null);
        return css;
    }

    /**
     * Build the container-wide CSS for {@link java.awt.FlowLayout}.
     * {@code align} matches FlowLayout's int constants
     * ({@link java.awt.FlowLayout#LEFT} / {@code CENTER} / {@code RIGHT} /
     * {@code LEADING} / {@code TRAILING}). {@code LEADING}/{@code TRAILING}
     * map to {@code flex-start}/{@code flex-end} — the {@code dir="rtl"}
     * attribute on the host flips them automatically per Swing's
     * leading/trailing-under-RTL contract. Ports the emulator's existing
     * {@code FlowLayout.layoutContainer} body factored by primitives so
     * both layers (and JDK / emulator FlowLayout flavours) call the same
     * builder.
     */
    public static java.util.Map<String, String> flowLayoutCss(int align, int hgap, int vgap) {
        java.util.Map<String, String> css = new java.util.LinkedHashMap<>();
        css.put("display", "flex");
        css.put("flex-wrap", "wrap");
        css.put("justify-content", switch (align) {
            case java.awt.FlowLayout.LEFT, java.awt.FlowLayout.LEADING -> "flex-start";
            case java.awt.FlowLayout.RIGHT, java.awt.FlowLayout.TRAILING -> "flex-end";
            case java.awt.FlowLayout.CENTER -> "center";
            default -> "flex-start";
        });
        // JDK FlowLayout vertically centers components within a row.
        // Without this, flex's default (stretch) makes Vaadin text-fields
        // top-align their inputs while JLabels appear centered, producing
        // the visible split when a tall component (e.g. a multi-row
        // JTextArea) sets the row's cross-axis size. setAlignOnBaseline
        // remains a field-only round-trip — no clean per-row CSS analog.
        css.put("align-items", "center");
        // CSS gap shorthand is row-gap then column-gap. Swing's hgap is the
        // horizontal spacing between components on a row (column-gap);
        // vgap is the spacing between wrapped rows (row-gap).
        css.put("gap", vgap + "px " + hgap + "px");
        return css;
    }

    /**
     * Build the container-wide CSS for {@code vaadinx.swing.BoxLayout}.
     * {@code axis} matches BoxLayout's int constants
     * ({@code X_AXIS=0} / {@code Y_AXIS=1} / {@code LINE_AXIS=2} /
     * {@code PAGE_AXIS=3}). LINE_AXIS pins to {@code row} and PAGE_AXIS to
     * {@code column} — full RTL flipping ({@code row-reverse} on
     * {@code dir="rtl"}) would require tracking orientation at
     * layoutContainer time; pinned LTR per R_layouts_close_enough same-shape as BorderLayout's
     * LINE_START / LINE_END caveat. No {@code gap} key — JDK BoxLayout has
     * no inherent inter-component spacing; {@code Box.createVerticalStrut}
     * / {@code createGlue} components carry that responsibility via their
     * own per-element CSS. Constants referenced by literal int rather than
     * by symbol — {@code vaadinx.swing.BoxLayout} sits in {@code :emulators}
     * and {@code :surrogates} can't depend on it.
     */
    public static java.util.Map<String, String> boxLayoutCss(int axis) {
        java.util.Map<String, String> css = new java.util.LinkedHashMap<>();
        css.put("display", "flex");
        // 0 = X_AXIS, 2 = LINE_AXIS → row.  1 = Y_AXIS, 3 = PAGE_AXIS → column.
        css.put("flex-direction", switch (axis) {
            case 0, 2 -> "row";
            case 1, 3 -> "column";
            default -> "column"; // unreachable — BoxLayout's ctor validates axis
        });
        // Reset (see class doc): a leftover flex-wrap: wrap would wrap this
        // BoxLayout column into several visual columns when height-constrained
        // — the opposite of BoxLayout's semantics.
        css.put("flex-wrap", "nowrap");
        css.put("justify-content", "flex-start");
        // JDK BoxLayout sizes children to {@code min(parentSize, child.getMaximumSize())}
        // on the cross axis — JPanel's default {@code maximumSize.width =
        // Integer.MAX_VALUE} so a row JPanel inside a Y_AXIS BoxLayout
        // stretches to fill the container width; the alignmentX (default
        // 0.5f) positions a constrained-width child within the leftover
        // gap, which never appears for unbounded children. CSS analog:
        // {@code align-items: stretch} matches the unbounded-max common
        // case (the row fills the container, all WEST/CENTER/EAST grid
        // cells land at predictable horizontal positions). A child with a
        // preferred width stretches too, because the emulator-side
        // {@code BoxLayout.childCss} hands the cross axis to this stretch
        // (D_layout_owns_child_sizing) — the divergence left is JDK's
        // center-positioning of a child whose {@code maximumSize} was
        // explicitly set, which caps the stretch there and not here.
        css.put("align-items", "stretch");
        // No inherent inter-component spacing — Box.createVerticalStrut /
        // createGlue components carry that responsibility via their own
        // per-element CSS.
        css.put("gap", "0");
        return css;
    }

    /**
     * Build the container-wide CSS for {@link java.awt.BorderLayout}. Per-
     * child {@code grid-area} writes happen separately in the layout's
     * {@code addLayoutComponent} (see {@code :emulators.BorderLayout}); this
     * helper just handles the grid setup.
     */
    public static java.util.Map<String, String> borderLayoutCss(int hgap, int vgap) {
        java.util.Map<String, String> css = new java.util.LinkedHashMap<>();
        css.put("display", "grid");
        // "auto minmax(0, 1fr) auto" — side regions take their intrinsic
        // size, the center region absorbs the rest. The minmax(0, 1fr)
        // (vs. plain 1fr) lets the CENTER track shrink below its
        // children's min-content size when the grid container itself is
        // height/width-constrained. This matches Swing's BorderLayout
        // semantics — CENTER is the absorber that may clip its content
        // (the inner JScrollPane / Grid handles the overflow). Plain
        // 1fr is shorthand for minmax(auto, 1fr), and `auto` forces the
        // track to be at least min-content tall, which would push grid
        // ancestors past their fixed height when CENTER holds an
        // intrinsically-tall subtree (the failure mode that motivated
        // D_inline_route_sizing's contentPane sizing extension).
        css.put("grid-template-columns", "auto minmax(0, 1fr) auto");
        css.put("grid-template-rows", "auto minmax(0, 1fr) auto");
        // North and south span the full width; west/center/east share the
        // middle row. Each child's grid-area slots into one of these cells.
        css.put("grid-template-areas",
                "\"north north north\" \"west center east\" \"south south south\"");
        css.put("gap", vgap + "px " + hgap + "px");
        // Reset (see class doc): a leftover align-items: center would center
        // items at intrinsic size instead of filling their cells, breaking the
        // height chain when CENTER holds a JScrollPane / Grid that must stretch
        // into the 1fr track.
        css.put("align-items", "stretch");
        css.put("justify-items", "stretch");
        return css;
    }

    /**
     * Build the container-wide CSS for {@code vaadinx.awt.GridBagLayout}.
     * {@code columnWeights} and {@code rowWeights} carry the per-track
     * weight, max-aggregated across each track's children — JDK
     * GridBagLayout's "any non-zero weight in a column makes that column
     * absorb extra space" rule. A non-zero weight maps to {@code Nfr}
     * (normalised so the sum of fr units across tracks reads naturally —
     * we just use the raw weight as the fr count, which preserves the
     * relative-ratio contract); a zero weight maps to {@code auto} so the
     * track sizes to its content. {@code minmax(0, …)} on fr tracks lets
     * them shrink below intrinsic size when the grid is height-bounded —
     * BorderLayout CENTER precedent.
     *
     * <p>{@code hgap}/{@code vgap} write through {@code gap} (column-gap
     * row-gap shorthand). JDK GridBagLayout has no hgap/vgap of its own —
     * spacing comes from per-child {@code Insets}. We pin gap to {@code 0}
     * here; all spacing rides on per-child margin (insets) / padding
     * (ipadx/ipady) writes from {@code GridBagLayout.layoutContainer}.
     *
     * <p>Per-child {@code grid-row}/{@code grid-column}/{@code justify-self}/
     * {@code align-self}/{@code margin}/{@code padding}/{@code min-*}
     * writes happen separately in the layout's
     * {@code applyChildConstraintsCss}; this helper just handles the grid
     * setup. Resets flex-era keys for the same reason borderLayoutCss
     * does — JPanel's default ctor installs FlowLayout and a
     * setLayout(GridBagLayout) follow-up would otherwise leave them
     * behind.
     */
    public static java.util.Map<String, String> gridBagLayoutCss(
            double[] columnWeights, double[] rowWeights) {
        java.util.Map<String, String> css = new java.util.LinkedHashMap<>();
        css.put("display", "grid");
        css.put("grid-template-columns", trackTemplate(columnWeights));
        css.put("grid-template-rows", trackTemplate(rowWeights));
        css.put("gap", "0");
        css.put("align-items", "stretch");
        css.put("justify-items", "stretch");
        // Reset (see class doc): clear any grid-template-areas a prior
        // BorderLayout wrote, else children stay pinned into Border cells.
        css.put("grid-template-areas", null);
        return css;
    }

    /**
     * Build the container-wide CSS for {@code vaadinx.awt.GridLayout}.
     * {@code nrows}/{@code ncols} are the <em>resolved</em> grid dimensions —
     * GridLayout fixes one dimension and derives the other from the live
     * child count, so the emulator does that arithmetic and passes the final
     * numbers here. All cells are equal: {@code repeat(N, 1fr)} on both axes,
     * matching JDK GridLayout's "divide the container into equal-sized
     * rectangles" contract. Children auto-place row-major ({@code grid-auto-flow:
     * row}, the CSS default) which reproduces GridLayout's left-to-right,
     * top-to-bottom fill without any per-child placement.
     *
     * <p>{@code align-items}/{@code justify-items: stretch} makes each child
     * fill its cell — GridLayout resizes every component to the cell size
     * (unlike FlowLayout, which uses preferred sizes). {@code hgap}/{@code vgap}
     * write through {@code gap} (row-gap column-gap shorthand: {@code vgap} is
     * the space between rows, {@code hgap} between columns). Resets the flex-era
     * keys and {@code grid-template-areas} per the class-doc reset rule.
     *
     * <p>{@code nrows}/{@code ncols} are clamped to at least 1 by the caller so
     * {@code repeat(0, 1fr)} — invalid CSS — can never be emitted for an empty
     * container.
     */
    public static java.util.Map<String, String> gridLayoutCss(
            int nrows, int ncols, int hgap, int vgap) {
        java.util.Map<String, String> css = new java.util.LinkedHashMap<>();
        css.put("display", "grid");
        css.put("grid-template-columns", "repeat(" + Math.max(1, ncols) + ", 1fr)");
        css.put("grid-template-rows", "repeat(" + Math.max(1, nrows) + ", 1fr)");
        css.put("gap", vgap + "px " + hgap + "px");
        css.put("align-items", "stretch");
        css.put("justify-items", "stretch");
        css.put("grid-template-areas", null);
        return css;
    }

    /**
     * Build the container-wide CSS for {@code vaadinx.swing.GroupLayout} (D_grouplayout).
     * The emulator reconstructs a CSS grid from GroupLayout's two axis
     * group-trees and passes the finished {@code repeat}/{@code auto} track
     * templates here ({@code colTemplate}/{@code rowTemplate}), each a
     * space-separated track list where content-sized tracks are {@code auto}
     * and resizable tracks are {@code minmax(0, 1fr)}.
     *
     * <p>{@code align-items}/{@code justify-items: start} is the default cell
     * anchoring — GroupLayout's {@code LEADING} alignment — with per-child
     * {@code align-self}/{@code justify-self} overrides written by the emulator
     * from each component's effective parallel-group alignment.
     * {@code align-content}/{@code justify-content: start} keeps the whole track
     * group top-left anchored (resetting any {@code justify-content: center}
     * left over from a JPanel's default FlowLayout, which on a grid would
     * center the tracks).
     *
     * <p>{@code gapPx} is a single uniform inter-track gap approximating
     * GroupLayout's per-gap spacing — GroupLayout's gaps don't map to grid
     * tracks in the reconstruction (R_layouts_close_enough; exact per-gap sizing is the deferred
     * pixel-accurate layout is out of scope permanently,
     * D_pixel_layout_not_planned). {@code grid-template-areas} is
     * cleared per the class-doc reset rule.
     */
    public static java.util.Map<String, String> groupLayoutCss(
            String colTemplate, String rowTemplate, int gapPx) {
        java.util.Map<String, String> css = new java.util.LinkedHashMap<>();
        css.put("display", "grid");
        css.put("grid-template-columns", colTemplate);
        css.put("grid-template-rows", rowTemplate);
        css.put("gap", gapPx + "px");
        css.put("align-items", "start");
        css.put("justify-items", "start");
        css.put("align-content", "start");
        css.put("justify-content", "start");
        css.put("grid-template-areas", null);
        return css;
    }

    private static String trackTemplate(double[] weights) {
        if (weights.length == 0) {
            // Defensive — a GridBagLayout container with no children would
            // hit this. CSS Grid needs at least one track entry; "auto"
            // collapses to 0 with no children inside, harmless.
            return "auto";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < weights.length; i++) {
            if (i > 0) sb.append(' ');
            double w = weights[i];
            if (w > 0.0) {
                // minmax(0, …fr) so the track can shrink below content
                // size when the container is bounded — same pattern as
                // BorderLayout CENTER.
                sb.append("minmax(0, ").append(w).append("fr)");
            } else {
                sb.append("auto");
            }
        }
        return sb.toString();
    }
}
