package vaadinx.jgoodies.forms.layout;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockService;
import com.github.mvysny.kaributesting.v10.mock.MockVaadinServlet;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.github.mvysny.blockingdialogs.uifiber.loom.VirtualThreadAwareLock;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import vaadinx.EHelper;
import vaadinx.jgoodies.forms.factories.FormFactory;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;

import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract this fork actually has to keep is the <em>emitted CSS</em> — upstream's own suite
 * asserts pixel arithmetic, which is precisely the part that cannot run server-side, so it is
 * not ported (R_layouts_close_enough; there is no synchronous child measurement in a browser).
 *
 * <p>Specs here are taken from {@code testapps/inventory/swing}, which is what forced this port: the
 * app is WindowBuilder-generated and its whole vocabulary is {@code default}, {@code max(Ndlu;default)},
 * an optional {@code :grow}, and a {@code left:} / {@code top:} prefix.
 */
class FormLayoutCssTest {

    private final List<String> warns = new ArrayList<>();
    private Consumer<String> savedEHook;
    private Consumer<String> savedSHook;

    @BeforeEach
    void setup() {
        MockVaadin.setup(MockedUI::new, new MockVaadinServlet(new Routes()) {
            // the loom runner's SessionLockCheck fails every session whose lock isn't wrapped
            @Override
            protected VaadinServletService createServletService(DeploymentConfiguration configuration) {
                final VaadinServletService service = new MockService(this, configuration, getUiFactory()) {
                    @Override
                    protected Lock getSessionLock(WrappedSession wrappedSession) {
                        return VirtualThreadAwareLock.wrap(this, wrappedSession, super.getSessionLock(wrappedSession));
                    }
                };
                try {
                    service.init();
                } catch (ServiceException e) {
                    throw new RuntimeException(e);
                }
                getRoutes().register(service.getContext());
                return service;
            }
        });
        savedEHook = EHelper.warnHook;
        savedSHook = SHelper.warnHook;
        EHelper.warnHook = warns::add;
        SHelper.warnHook = warns::add;
    }

    @AfterEach
    void teardown() {
        EHelper.warnHook = savedEHook;
        SHelper.warnHook = savedSHook;
        MockVaadin.tearDown();
    }

    private static vaadinx.awt.Component component() {
        return new vaadinx.awt.Component(new Div()) {
        };
    }

    private static String cols(JPanel p) {
        return p.getPeer().getElement().getStyle().get("grid-template-columns");
    }

    private static String rows(JPanel p) {
        return p.getPeer().getElement().getStyle().get("grid-template-rows");
    }

    private static String style(vaadinx.awt.Component c, String property) {
        return c.getPeer().getElement().getStyle().get(property);
    }

    /** The app's canonical panel: gap, content, gap, content — all four are real tracks. */
    private static JPanel inventoryPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FormLayout(
                new ColumnSpec[]{
                        FormFactory.RELATED_GAP_COLSPEC,
                        ColumnSpec.decode("max(18dlu;default)"),
                        FormFactory.RELATED_GAP_COLSPEC,
                        ColumnSpec.decode("default:grow"),
                },
                new RowSpec[]{
                        FormFactory.RELATED_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                }));
        return panel;
    }

    // ---- tracks ---------------------------------------------------------

    @Test
    @DisplayName("a container laid out by FormLayout becomes a CSS grid")
    void aContainerLaidOutByFormLayoutBecomesACssGrid() {
        JPanel panel = inventoryPanel();
        panel.doLayout();
        assertEquals("grid", panel.getPeer().getElement().getStyle().get("display"));
    }

    /**
     * The load-bearing property: gap specs stay tracks, so four specs make four tracks and a
     * CellConstraints index still means what it meant in Swing.
     */
    @Test
    @DisplayName("gap specs emit their own tracks rather than collapsing into gap")
    void gapSpecsEmitTheirOwnTracksRatherThanCollapsingIntoGap() {
        JPanel panel = inventoryPanel();
        panel.doLayout();

        // Asserted whole rather than split on spaces — a minmax() track contains one.
        assertEquals("1.03ch minmax(4.635ch, auto) 1.03ch minmax(0, 1fr)", cols(panel),
                "four specs must yield four tracks, gaps included");
        assertNull(panel.getPeer().getElement().getStyle().get("gap"),
                "collapsing gaps into `gap` would renumber every constraint");
    }

    @Test
    @DisplayName("default size is auto and grow becomes an fr track")
    void defaultSizeIsAutoAndGrowBecomesAnFrTrack() {
        JPanel panel = inventoryPanel();
        panel.doLayout();
        assertTrue(cols(panel).endsWith("minmax(0, 1fr)"), "`default:grow` absorbs the slack");
        assertEquals("0.5em auto", rows(panel), "gap row then DEFAULT_ROWSPEC");
    }

    @Test
    @DisplayName("a max-bounded dlu size becomes a minmax floored at the dlu bound")
    void aMaxBoundedDluSizeBecomesAMinmaxFlooredAtTheDluBound() {
        JPanel panel = inventoryPanel();
        panel.doLayout();
        // 18 dluX = 18/4 average char widths, calibrated against ch (which measures "0").
        assertTrue(cols(panel).contains("minmax(4.635ch, auto)"),
                "at least the dlu bound, growing to content: " + cols(panel));
    }

    /** Only dialog units need inventing; CSS has the rest natively. */
    @Test
    @DisplayName("native CSS units pass straight through")
    void nativeCssUnitsPassStraightThrough() {
        JPanel panel = new JPanel();
        panel.setLayout(new FormLayout(
                new ColumnSpec[]{
                        ColumnSpec.decode("12px"),
                        ColumnSpec.decode("10pt"),
                        ColumnSpec.decode("5mm"),
                },
                new RowSpec[]{FormFactory.DEFAULT_ROWSPEC}));
        panel.doLayout();
        assertEquals("12px 10pt 5mm", cols(panel));
    }

    @Test
    @DisplayName("min and pref component sizes map to the content keywords")
    void minAndPrefComponentSizesMapToTheContentKeywords() {
        JPanel panel = new JPanel();
        panel.setLayout(new FormLayout(
                new ColumnSpec[]{ColumnSpec.decode("min"), ColumnSpec.decode("pref"), ColumnSpec.decode("default")},
                new RowSpec[]{FormFactory.DEFAULT_ROWSPEC}));
        panel.doLayout();
        assertEquals("min-content max-content auto", cols(panel));
    }

    // ---- per-child placement --------------------------------------------

    /**
     * The String constraint form is what WindowBuilder generates, and all 13 of the app's form
     * panels use it exclusively — no CellConstraints objects anywhere.
     */
    @Test
    @DisplayName("string constraints place a child on the matching grid lines")
    void stringConstraintsPlaceAChildOnTheMatchingGridLines() {
        JPanel panel = inventoryPanel();
        vaadinx.awt.Component field = component();
        panel.add(field, "4, 2, fill, default");
        panel.doLayout();

        assertEquals("4", style(field, "grid-column"));
        assertEquals("2", style(field, "grid-row"));
        assertEquals("stretch", style(field, "justify-self"));
    }

    @Test
    @DisplayName("spans become grid-line span syntax")
    void spansBecomeGridLineSpanSyntax() {
        JPanel panel = inventoryPanel();
        vaadinx.awt.Component wide = component();
        panel.add(wide, "2, 2, 3, 1, fill, fill");
        panel.doLayout();

        assertEquals("2 / span 3", style(wide, "grid-column"));
        assertEquals("2", style(wide, "grid-row"));
        assertEquals("stretch", style(wide, "align-self"));
    }

    @Test
    @DisplayName("left and top map to start, right and bottom to end")
    void leftAndTopMapToStartRightAndBottomToEnd() {
        JPanel panel = inventoryPanel();
        vaadinx.awt.Component a = component();
        vaadinx.awt.Component b = component();
        panel.add(a, "2, 2, left, top");
        panel.add(b, "4, 2, right, bottom");
        panel.doLayout();

        assertEquals("start", style(a, "justify-self"));
        assertEquals("start", style(a, "align-self"));
        assertEquals("end", style(b, "justify-self"));
        assertEquals("end", style(b, "align-self"));
    }

    /**
     * {@code default} alignment is not a fixed value — it defers to the track's own default, and
     * Forms differs per axis (a column fills, a row centres). Getting this wrong is invisible
     * until a form looks subtly off, so it is pinned here.
     */
    @Test
    @DisplayName("default alignment resolves against the spec, per axis")
    void defaultAlignmentResolvesAgainstTheSpecPerAxis() {
        JPanel panel = new JPanel();
        panel.setLayout(new FormLayout(
                new ColumnSpec[]{ColumnSpec.decode("left:default")},
                new RowSpec[]{RowSpec.decode("center:default")}));
        vaadinx.awt.Component c = component();
        panel.add(c, "1, 1");
        panel.doLayout();

        assertEquals("start", style(c, "justify-self"), "column spec says left");
        assertEquals("center", style(c, "align-self"), "row spec says center");
    }

    /**
     * Upstream's {@code CellConstraints.extent}: a FILL cell gives its component the whole cell,
     * every other alignment the component's own size. A {@code JTextField(columns)} carries that
     * size as its CSS width, which a cell's {@code stretch} alone does not override — the
     * inventory app's Category Name field rendered at 120px in a 240px column.
     */
    @Test
    @DisplayName("a fill cell sizes its child's width; a left cell leaves the preferred width")
    void aFillCellSizesItsChildsWidthALeftCellLeavesThePreferredWidth() {
        JPanel panel = inventoryPanel();
        JTextField filled = new JTextField(10);
        JTextField left = new JTextField(10);
        panel.add(filled, "4, 2, fill, default");
        panel.add(left, "2, 2, left, default");
        panel.doLayout();

        assertEquals("var(--emul-layout-w, calc(10ch + 2em))", style(filled, "width"),
                "the column count stays the preferred width");
        assertEquals("auto", style(filled, "--emul-layout-w"), "FILL takes the width over");
        assertEquals("initial", style(left, "--emul-layout-w"), "LEFT keeps the preferred width");
        assertEquals("initial", style(filled, "--emul-layout-h"), "a default row centres, so the height is the field's");
    }

    /** Upstream's concreteAlignment: a spanning cell has no one spec, so DEFAULT means FILL. */
    @Test
    @DisplayName("a spanning cell's default alignment is fill, whatever its first column says")
    void aSpanningCellsDefaultAlignmentIsFill() {
        JPanel panel = new JPanel();
        panel.setLayout(new FormLayout(
                new ColumnSpec[]{ColumnSpec.decode("left:default"), ColumnSpec.decode("left:default")},
                new RowSpec[]{FormFactory.DEFAULT_ROWSPEC}));
        vaadinx.awt.Component wide = component();
        panel.add(wide, "1, 1, 2, 1");
        panel.doLayout();

        assertEquals("stretch", style(wide, "justify-self"));
        assertEquals("auto", style(wide, "--emul-layout-w"));
        assertEquals("center", style(wide, "align-self"), "one row: its spec still decides");
        assertEquals(List.of(), warns);
    }

    /**
     * Upstream rejects a constraint-less add outright, and so does the fork — R_match_swing_errors says match
     * Swing's error handling, and this is a programming error the migrator wants to see rather
     * than a component silently landing in cell (1,1).
     */
    @Test
    @DisplayName("adding without constraints throws, as upstream does")
    void addingWithoutConstraintsThrowsAsUpstreamDoes() {
        JPanel panel = inventoryPanel();
        assertThrows(NullPointerException.class, () -> panel.add(component()));
    }

    /**
     * Equal-size groups need measured content widths, which CSS never exposes. Dropping them
     * is accepted (R_layouts_close_enough/R_vaadin_first), dropping them <em>silently</em> is not — a form whose grouped
     * columns come out ragged would otherwise be unexplainable.
     */
    @Test
    @DisplayName("column groups WARN rather than diverge silently")
    void columnGroupsWarnRatherThanDivergeSilently() {
        JPanel panel = inventoryPanel();
        ((FormLayout) panel.getLayout()).setColumnGroups(new int[][]{{2, 4}});
        panel.doLayout();

        assertTrue(warns.stream().anyMatch(w -> w.contains("setColumnGroups")), "expected a WARN, got " + warns);
    }

    @Test
    @DisplayName("cell insets WARN rather than diverge silently")
    void cellInsetsWarnRatherThanDivergeSilently() {
        JPanel panel = inventoryPanel();
        vaadinx.awt.Component c = component();
        CellConstraints cc = new CellConstraints(2, 2);
        cc.insets = new Insets(1, 2, 3, 4);   // a public field upstream, not a builder
        panel.add(c, cc);
        panel.doLayout();

        assertTrue(warns.stream().anyMatch(w -> w.contains("insets")), "expected a WARN, got " + warns);
    }

    // ---- gate -----------------------------------------------------------

    @Test
    @DisplayName("laying out the app's spec vocabulary raises no stub WARNs")
    void layingOutTheAppsSpecVocabularyRaisesNoStubWarns() {
        JPanel panel = inventoryPanel();
        panel.add(component(), "2, 2");
        panel.add(component(), "4, 2, fill, default");
        panel.doLayout();

        assertEquals(List.of(), warns);
    }
}
