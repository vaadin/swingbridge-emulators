package vaadinx.jgoodies.forms.layout;

// SB-Emulators-authored: the translation FormLayout's pixel engine is replaced by. Not upstream code.
//
// Lives in this package so it can read Sizes.ComponentSize, which is package-private. The
// spec model itself is upstream and untouched — everything here is a pure read of it.

/**
 * Translates JGoodies Forms specs into CSS Grid, the substitution that lets a `FormLayout`
 * lay out in a browser where its `setBounds` arithmetic cannot run:
 *
 * <pre>{@code
 * ColumnSpec.decode("max(65dlu;default):grow")  =>  "minmax(16.74ch, 1fr)"
 * ColumnSpec.decode("left:default")             =>  "auto"          + justify-self: start
 * FormFactory.RELATED_GAP_COLSPEC               =>  "1.03ch"        (a real track, see below)
 * }</pre>
 *
 * <p><b>Gap specs stay tracks.</b> In Forms a gap <em>is</em> a spec in the array, which is why
 * generated constraints use even indices — {@code "8, 4"} is column 8, row 4. CSS grid lines are
 * 1-based too, so a constraint's index maps to {@code grid-column: 8} with no renumbering, and
 * an asymmetric gap stays expressible. Collapsing gaps into a {@code gap} property would cost
 * both.
 *
 * <p><b>Dialog units.</b> Upstream's {@code DefaultUnitConverter} takes the dluX base unit
 * to be the advance of a single capital <b>"X"</b> in the dialog font (its
 * {@code averageCharWidthTestString} defaults to {@code "X"}, despite the name) divided
 * by 4, and the dluY base unit from the font's <b>ascent</b> divided by 8. On a server
 * there is no such font — and upstream's measurement of one throws
 * {@code HeadlessException} headless — so the units are mapped font-<em>relatively</em>
 * instead: {@code ch} (the advance of "0") for X, {@code em} (the font size) for Y.
 * That tracks the browser's real font at any zoom, where a baked pixel constant is
 * right at exactly one. {@code ch} stands in for the "X" advance, hence
 * {@link #DLU_X_CALIBRATION}. Residual divergence is R_layouts_close_enough territory.
 */
final class FormCss {

    /**
     * Corrects the {@code ch}-based dluX conversion for the glyph mismatch: upstream's base unit
     * is the advance of <b>"X"</b>, CSS {@code ch} is the advance of <b>"0"</b>, so this is
     * {@code width("X") / width("0")}.
     *
     * <p><b>Measured</b>, not guessed: 1.034 for Aura's default font (Instrument Sans 14px) in
     * Chromium, where {@code width("X")} is 9.633px and {@code 1ch} is 9.317px — canvas and DOM
     * agreeing to four decimals. Rounded to 1.03 and deliberately *not* 1.0, so the factor stays
     * visible instead of being inlined away as a no-op multiply.
     *
     * <p>Font-dependent by nature, and CSS has no "advance of X" unit, so a constant against
     * {@code ch} is the only expressible form. For proportional sans-serif faces "X" and "0" sit
     * within a few percent of each other, so a value near 1 is the right default; revisit against
     * a real form only if a specific font stack proves badly off.
     */
    private static final double DLU_X_CALIBRATION = 1.03;

    /**
     * dluY per em: upstream's vertical base unit is the font's <b>ascent</b> (nudged upward for
     * small fonts) divided by 8, and {@code em} stands in for that ascent.
     *
     * <p>Measured within 2.3% for Aura's default font — a 14px ascent gives upstream 1.792px per
     * dluY against this mapping's 1.750px — so the substitution holds and needs no calibration
     * factor of its own.
     */
    private static final double DLU_Y_PER_EM = 8.0;

    /** dluX per ch, before calibration. */
    private static final double DLU_X_PER_CH = 4.0;

    private FormCss() {
    }

    // ---- tracks ---------------------------------------------------------

    /**
     * The {@code grid-template-columns} / {@code -rows} entry for one spec.
     *
     * @param horizontal selects the dialog-unit axis; a {@code ColumnSpec} is horizontal
     */
    static String track(FormSpec spec, boolean horizontal) {
        String base = sizeToCss(spec.getSize(), horizontal);
        double grow = spec.getResizeWeight();
        if (grow <= 0) {
            return base;
        }
        // A growing track needs its size as minmax's *minimum*, so the fr can absorb slack.
        // minmax can't nest, so a bounded size collapses to its own lower bound here.
        String min = minPartOf(spec.getSize(), horizontal);
        return "minmax(" + min + ", " + trim(grow) + "fr)";
    }

    /** The CSS a size expresses on its own, ignoring resize weight. */
    private static String sizeToCss(Size size, boolean horizontal) {
        if (size instanceof ConstantSize) {
            return length((ConstantSize) size, horizontal);
        }
        if (size instanceof Sizes.ComponentSize) {
            return componentSize((Sizes.ComponentSize) size);
        }
        if (size instanceof PrototypeSize) {
            // "as wide as this string" is precisely what the ch unit measures.
            return ((PrototypeSize) size).getPrototype().length() + "ch";
        }
        if (size instanceof BoundedSize) {
            BoundedSize bounded = (BoundedSize) size;
            String basis = sizeToCss(bounded.getBasis(), horizontal);
            Size lower = bounded.getLowerBound();
            Size upper = bounded.getUpperBound();
            if (lower != null) {
                // max(Ndlu;default): at least the bound, growing to content.
                return "minmax(" + sizeToCss(lower, horizontal) + ", " + basis + ")";
            }
            if (upper != null) {
                // min(Ndlu;default): content-sized but capped.
                return "minmax(0, " + sizeToCss(upper, horizontal) + ")";
            }
            return basis;
        }
        vaadinx.EHelper.onUnimplemented("FormCss", "sizeToCss", size);
        return "auto";
    }

    /**
     * A size reduced to something legal as {@code minmax}'s first argument — no nested
     * {@code minmax}, so a bounded size yields its lower bound (or {@code auto} when it has
     * none).
     */
    private static String minPartOf(Size size, boolean horizontal) {
        if (size instanceof BoundedSize) {
            Size lower = ((BoundedSize) size).getLowerBound();
            return lower == null ? "auto" : sizeToCss(lower, horizontal);
        }
        if (size instanceof Sizes.ComponentSize) {
            // 0 rather than the content keyword: a growing track that also insists on its
            // content width refuses to shrink below it, which breaks narrow viewports.
            return "0";
        }
        return sizeToCss(size, horizontal);
    }

    private static String componentSize(Sizes.ComponentSize size) {
        if (size == Sizes.MINIMUM) {
            return "min-content";
        }
        if (size == Sizes.PREFERRED) {
            return "max-content";
        }
        return "auto"; // Sizes.DEFAULT — preferred, but yielding when space is short
    }

    /** One {@link ConstantSize} as a CSS length. */
    static String length(ConstantSize size, boolean horizontal) {
        double value = size.getValue();
        ConstantSize.Unit unit = size.getUnit();
        // px/pt/mm/cm/in all exist in CSS natively; only dialog units need inventing.
        if (unit == ConstantSize.PIXEL) {
            return trim(value) + "px";
        }
        if (unit == ConstantSize.POINT) {
            return trim(value) + "pt";
        }
        if (unit == ConstantSize.MILLIMETER) {
            return trim(value) + "mm";
        }
        if (unit == ConstantSize.CENTIMETER) {
            return trim(value) + "cm";
        }
        if (unit == ConstantSize.INCH) {
            return trim(value) + "in";
        }
        if (unit == ConstantSize.DIALOG_UNITS_X) {
            return trim(value / DLU_X_PER_CH * DLU_X_CALIBRATION) + "ch";
        }
        if (unit == ConstantSize.DIALOG_UNITS_Y) {
            return trim(value / DLU_Y_PER_EM) + "em";
        }
        // DIALOG_UNITS_X/Y are resolved per axis when a spec doesn't pin one.
        return trim(horizontal
                ? value / DLU_X_PER_CH * DLU_X_CALIBRATION
                : value / DLU_Y_PER_EM) + (horizontal ? "ch" : "em");
    }

    // ---- per-child placement --------------------------------------------

    /** {@code grid-column} / {@code grid-row}: 1-based origin and span, straight from the spec. */
    static String gridLine(int origin, int span) {
        return span <= 1 ? Integer.toString(origin) : origin + " / span " + span;
    }

    /**
     * The {@code justify-self} / {@code align-self} for one cell, resolving
     * {@code CellConstraints.DEFAULT} against the track's own default alignment — which is
     * what "default" means in Forms, and differs per axis (columns fill, rows centre).
     *
     * @param specDefault the cell's one spec's default alignment, or {@code null} when the cell
     *                    spans several, where {@code DEFAULT} means fill
     */
    static String selfAlignment(CellConstraints.Alignment cellAlignment,
                                FormSpec.DefaultAlignment specDefault) {
        boolean isDefault = cellAlignment == null || cellAlignment == CellConstraints.DEFAULT;
        if (isDefault && specDefault == null) {
            return "stretch";
        }
        String name = isDefault ? String.valueOf(specDefault) : String.valueOf(cellAlignment);
        switch (name) {
            case "fill":
                return "stretch";
            case "left":
            case "top":
                return "start";
            case "right":
            case "bottom":
                return "end";
            case "center":
                return "center";
            default:
                vaadinx.EHelper.onUnimplemented("FormCss", "selfAlignment", name);
                return "stretch";
        }
    }

    /** Drops the trailing {@code .0} so tracks read {@code 4ch}, not {@code 4.0ch}. */
    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.valueOf(Math.round(value * 1000d) / 1000d);
    }
}
