package vaadinx.jgoodies.forms.util;

// SB-Emulators-authored replacement for upstream's DefaultUnitConverter / AbstractUnitConverter,
// which are NOT vendored. Those measure a Look&Feel dialog font through FontMetrics and
// call Toolkit.getScreenResolution() — which throws HeadlessException in a servlet JVM,
// and where it doesn't, measures a server-side font no browser will ever render.
//
// The pixel API below therefore has no honest answer and WARNs. The real conversion runs
// in FormCss, which maps units to CSS lengths instead: px/pt/mm/cm/in exist natively in
// CSS, and dialog units become font-relative (ch/em) so they scale with the browser's
// actual font rather than a constant that is wrong at every zoom level.

import vaadinx.awt.Component;

/**
 * Answers the pixel-valued {@link UnitConverter} contract for a server, where no honest
 * pixel answer exists — every method WARNs via {@code EHelper.onUnimplemented} and returns
 * {@code 0}.
 *
 * <p>Installed as the default by {@link vaadinx.jgoodies.forms.layout.Sizes}. Reaching one
 * of these methods means something asked for a <em>pixel</em> size, so the CSS path was
 * bypassed — the WARN is the signal that a container laid out through the pixel engine
 * instead of {@link vaadinx.jgoodies.forms.layout.FormLayout}'s CSS emission. The conversion
 * that actually runs is the layout package's package-private {@code FormCss}.
 */
public final class CssUnitConverter implements UnitConverter {

    private static final CssUnitConverter INSTANCE = new CssUnitConverter();

    public static CssUnitConverter getInstance() {
        return INSTANCE;
    }

    private CssUnitConverter() {
    }

    private int warn(String method, Object value) {
        vaadinx.EHelper.onUnimplemented("CssUnitConverter", method, value);
        return 0;
    }

    @Override
    public int inchAsPixel(double in, Component component) {
        return warn("inchAsPixel", in);
    }

    @Override
    public int millimeterAsPixel(double mm, Component component) {
        return warn("millimeterAsPixel", mm);
    }

    @Override
    public int centimeterAsPixel(double cm, Component component) {
        return warn("centimeterAsPixel", cm);
    }

    @Override
    public int pointAsPixel(int pt, Component component) {
        return warn("pointAsPixel", pt);
    }

    @Override
    public int dialogUnitXAsPixel(int dluX, Component component) {
        return warn("dialogUnitXAsPixel", dluX);
    }

    @Override
    public int dialogUnitYAsPixel(int dluY, Component component) {
        return warn("dialogUnitYAsPixel", dluY);
    }
}
