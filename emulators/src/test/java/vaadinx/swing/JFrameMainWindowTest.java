/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJFrame;
import com.vaadin.swingbridge.surrogates.SJPanel;
import vaadinx.MockVirtualThreadAwareServlet;
import vaadinx.swing.app.MainWindowRoute;

import java.awt.Dimension;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D_jframe_as_route — JFrame-as-route.
 *
 * <p>Covers:
 * <ul>
 * <li>Strategy selection via {@code @MainWindow} on the runtime class
 *   (CAS contract for class uniqueness, single-class re-entry no-op,
 *   two-different-classes throw).
 * <li>InlineStrategy peer binding to MainWindowRoute (route capture at
 *   ctor time, throw when constructed outside bootstrap).
 * <li>setVisible attach/detach against the route's element.
 * <li>setTitle routing to UI.getPage for InlineStrategy.
 * <li>R_leaf_peer_lockdown lock-down via reflection — no protected (Component peer) ctor.
 * </ul>
 */
class JFrameMainWindowTest {

    /**
     * Custom MockVaadin setup with a placeholder {@code @Route("")}
     * registered. Karibu's after-session-close hook navigates to
     * {@code ""} to keep tests running with a fresh session; without
     * an empty-path route, the EXIT_ON_CLOSE-closes-session test
     * would fail on the cleanup navigation, not on its assertion.
     */
    @BeforeEach
    void setupKaribu() {
        Routes routes = new Routes(new LinkedHashSet<>(List.of(EmptyTestRoute.class)),
                new LinkedHashSet<>(), false);
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(routes));
        // Each test starts from an empty slot so order-independence
        // holds across the multi-class-throws test fixture.
        JFrame.resetMainWindowClassForTesting();
    }

    @AfterEach
    void teardownKaribu() {
        JFrame.resetMainWindowClassForTesting();
        MockVaadin.tearDown();
    }

    // ---- Strategy selection ------------------------------------------

    @Test
    @DisplayName("plain JFrame picks DialogStrategy with SJFrame peer")
    void plainJFramePicksDialogStrategy() {
        JFrame f = new JFrame();
        assertSame(DialogStrategy.INSTANCE, f.strategy());
        assertTrue(f.peer() instanceof SJFrame);
    }

    @Test
    @DisplayName("non-annotated subclass picks DialogStrategy")
    void nonAnnotatedSubclassPicksDialogStrategy() {
        JFrame f = new NonAnnotatedFrame();
        assertSame(DialogStrategy.INSTANCE, f.strategy());
        assertTrue(f.peer() instanceof SJFrame);
    }

    @Test
    @DisplayName("MainWindow-annotated subclass picks InlineStrategy with SJPanel peer")
    void mainWindowSubclassPicksInlineStrategy() {
        TestMainFrame f = inBootstrap(TestMainFrame::new);
        assertSame(InlineStrategy.INSTANCE, f.strategy());
        assertTrue(f.peer() instanceof SJPanel);
    }

    @Test
    @DisplayName("InlineStrategy frameInit overrides SJPanel FlowLayout with column flex")
    void inlineStrategyFrameInitOverridesFlowLayout() {
        // Regression guard for layout-bugs item [4]/[8]/[19]: the SJPanel
        // peer's default FlowLayout CSS (flex-direction:row + center
        // alignments + flex-wrap:wrap) made the menubar and contentPane
        // lay out as wrapping centered rows, leaving big vertical gaps
        // and not stretching the contentPane to fill the SJPanel's
        // height. applyInlineSizingOnFrameInit overrides those slots so
        // the stack is top-aligned, full-width, and the contentPane
        // grows into remaining vertical space via flex:1.
        TestMainFrame f = inBootstrap(TestMainFrame::new);
        Style sjpStyle = f.peer().getElement().getStyle();
        assertEquals("100%", sjpStyle.get("width"));
        assertEquals("100%", sjpStyle.get("height"));
        assertEquals("column", sjpStyle.get("flex-direction"));
        assertEquals("flex-start", sjpStyle.get("justify-content"));
        assertEquals("stretch", sjpStyle.get("align-items"));
        assertEquals("nowrap", sjpStyle.get("flex-wrap"));
        assertEquals("0", sjpStyle.get("gap"));

        // The sizing targets the root pane — the SJPanel's one child — not the
        // content pane, which the root pane now stands two levels above. The
        // menubar / contentPane split happens inside it, via .emul-rootpane's own
        // flex column.
        Style rpStyle = f.getRootPane().peerContentElement().getStyle();
        assertEquals("1 1 0", rpStyle.get("flex"));
        assertEquals("0", rpStyle.get("min-height"));
        assertEquals("0", rpStyle.get("min-width"));
    }

    @Test
    @DisplayName("setPreferredSize on resizable MainWindow JFrame keeps peer at 100 percent")
    void setPreferredSizeOnResizableMainWindowKeepsPeerFull() {
        // D_inline_route_sizing PWA-shape default: a @MainWindow JFrame fills the viewport
        // regardless of the user's setPreferredSize request. The browser is
        // the WM; like a tiling WM it silently overrides Swing's size
        // requests. Field shadow still round-trips via getPreferredSize()
        // (R_swing_is_truth).
        TestMainFrame f = inBootstrap(TestMainFrame::new);
        Style sjpStyle = f.peer().getElement().getStyle();

        f.setPreferredSize(new Dimension(900, 520));

        assertEquals("100%", sjpStyle.get("width"), "peer width pinned despite setPreferredSize");
        assertEquals("100%", sjpStyle.get("height"), "peer height pinned despite setPreferredSize");
        assertEquals(new Dimension(900, 520), f.getPreferredSize(), "field shadow round-trips for R_swing_is_truth");
        assertTrue(f.isPreferredSizeSet(), "setPreferredSize marks the field as set");
    }

    @Test
    @DisplayName("pack on resizable MainWindow JFrame is inert")
    void packOnResizableMainWindowIsInert() {
        // D_inline_route_sizing PWA-shape default: pack() on InlineStrategy is a no-op. The
        // route stays setSizeFull and the peer stays at 100%/100%.
        Bound<TestMainFrame> bound = inBootstrapWithRoute(TestMainFrame::new);
        TestMainFrame f = bound.frame();
        f.setPreferredSize(new Dimension(900, 520));
        Style routeStyle = bound.route().getElement().getStyle();
        Style sjpStyle = f.peer().getElement().getStyle();

        f.pack();

        assertEquals("100%", routeStyle.get("width"), "route stays setSizeFull (width)");
        assertEquals("100%", routeStyle.get("height"), "route stays setSizeFull (height)");
        assertEquals("100%", sjpStyle.get("width"), "peer stays at 100% (width)");
        assertEquals("100%", sjpStyle.get("height"), "peer stays at 100% (height)");
    }

    @Test
    @DisplayName("setResizable false + setPreferredSize + pack opts into fixed-size pixel sizing")
    void fixedSizeOptInViaResizableFalsePlusPack() {
        // D_inline_route_sizing opt-out: declaring isResizable=false is the JDK-faithful way
        // to ask for a fixed-size main window in the browser. The carve-out
        // gate (!isResizable && isPreferredSizeSet) is checked at pack()
        // time, where it clears the route fill and writes pixel dimensions
        // onto the SJPanel peer.
        Bound<TestMainFrame> bound = inBootstrapWithRoute(TestMainFrame::new);
        TestMainFrame f = bound.frame();
        f.setResizable(false);
        f.setPreferredSize(new Dimension(900, 520));
        Style routeStyle = bound.route().getElement().getStyle();
        Style sjpStyle = f.peer().getElement().getStyle();

        f.pack();

        assertNull(routeStyle.get("width"), "route setSizeFull cleared");
        assertNull(routeStyle.get("height"), "route setSizeFull cleared");
        assertEquals("900px", sjpStyle.get("width"), "peer takes preferred-size pixels");
        assertEquals("520px", sjpStyle.get("height"), "peer takes preferred-size pixels");
    }

    @Test
    @DisplayName("setResizable false without setPreferredSize leaves the frame at viewport-fill")
    void resizableFalseAloneLeavesViewportFill() {
        // The gate requires BOTH conditions. Locking resizable alone (no
        // preferred size) leaves the default path intact.
        Bound<TestMainFrame> bound = inBootstrapWithRoute(TestMainFrame::new);
        TestMainFrame f = bound.frame();
        f.setResizable(false);
        f.pack();

        assertEquals("100%", bound.route().getElement().getStyle().get("width"), "route still setSizeFull");
        assertEquals("100%", f.peer().getElement().getStyle().get("width"), "peer still 100%");
    }

    @Test
    @DisplayName("MainWindow annotation is inherited to user subclasses")
    void mainWindowAnnotationIsInherited() {
        DerivedMainFrame f = inBootstrap(DerivedMainFrame::new);
        assertSame(InlineStrategy.INSTANCE, f.strategy());
    }

    @Test
    @DisplayName("non-MainWindow JFrame constructed transitively from MainWindow class method picks DialogStrategy")
    void secondaryFrameFromMainWindowContextPicksDialogStrategy() {
        // Discovered while converting the Sampler shell: a panel ctor
        // invoked from inside SamplerFrame.showDemo (an @MainWindow class
        // method) constructs `new JFrame()` for a secondary demo. The
        // stack walk finds SamplerFrame as the deepest JFrame subclass
        // and would mis-pick InlineStrategy. Outside bootstrap the
        // strategy picker now resolves the ambiguity to DialogStrategy.
        // Register the @MainWindow class first so the runtime is
        // post-bootstrap.
        inBootstrap(TestMainFrame::new);
        // Construct a plain JFrame from a static method declared on
        // TestMainFrame — simulates the showDemo chain by leaving
        // TestMainFrame on the stack.
        JFrame secondary = TestMainFrame.makePlainSecondaryJFrame();
        assertSame(DialogStrategy.INSTANCE, secondary.strategy());
        assertTrue(secondary.peer() instanceof SJFrame);
    }

    // ---- CAS contract -------------------------------------------------

    @Test
    @DisplayName("re-instantiating the same MainWindow class is a no-op")
    void reInstantiatingTheSameMainWindowClassIsANoOp() {
        // Multi-tab case: tab #2 instantiates the same @MainWindow class.
        // CAS hits with classes equal — no throw.
        inBootstrap(TestMainFrame::new);
        inBootstrap(TestMainFrame::new);
        inBootstrap(TestMainFrame::new);
        // Reaching here means no throw.
    }

    @Test
    @DisplayName("instantiating a second different MainWindow class throws")
    void instantiatingASecondMainWindowClassThrows() {
        inBootstrap(TestMainFrame::new);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> inBootstrap(OtherMainFrame::new));
        assertTrue(ex.getMessage().contains("@MainWindow"), "message names the contract");
        assertTrue(ex.getMessage().contains(TestMainFrame.class.getName()),
                "message names the registered class");
        assertTrue(ex.getMessage().contains(OtherMainFrame.class.getName()),
                "message names the attempted class");
    }

    // ---- bootstrap gating ---------------------------------------------

    @Test
    @DisplayName("bootstrap fires exactly once per route instance")
    void bootstrapFiresExactlyOncePerRouteInstance() {
        vaadinx.Counter calls = new vaadinx.Counter();
        TestRoute route = new TestRoute(calls::inc);
        UI.getCurrent().add(route);
        calls.assertEquals(1);
    }

    @Test
    @DisplayName("bootstrap does NOT re-fire on detach + re-attach (PreserveOnRefresh shape)")
    void bootstrapStaysGatedAcrossDetachReattach() {
        // Vaadin's @PreserveOnRefresh detaches the route from the old
        // UI and re-attaches it to a fresh UI; flow's StateNode resets
        // the node id during the rebind, so AttachEvent#isInitialAttach
        // returns true on the re-attach. A naive gate on isInitialAttach
        // would therefore fire bootstrap() on every browser refresh —
        // visibly: stacks a new @MainWindow JFrame on each refresh.
        // The per-instance `bootstrapped` flag is the reliable gate.
        // detach + re-attach via UI.add/remove is the most direct way
        // to exercise multiple onAttach calls on the same instance
        // under Karibu (whose single-UI fixture can't simulate the
        // cross-UI rebind directly, but the gate's contract is
        // once-per-instance regardless of why onAttach re-fires).
        vaadinx.Counter calls = new vaadinx.Counter();
        TestRoute route = new TestRoute(calls::inc);
        UI.getCurrent().add(route);
        assertEquals(1, calls.get(), "bootstrap fires on first attach");

        UI.getCurrent().remove(route);
        UI.getCurrent().add(route);
        assertEquals(1, calls.get(), "bootstrap stays gated on re-attach of the same instance");

        UI.getCurrent().remove(route);
        UI.getCurrent().add(route);
        assertEquals(1, calls.get(), "still gated after a second cycle");
    }

    // ---- Bound route --------------------------------------------------

    @Test
    @DisplayName("MainWindow JFrame constructed outside bootstrap throws")
    void mainWindowFrameConstructedOutsideBootstrapThrows() {
        // No CURRENT route bound — direct construction.
        IllegalStateException ex = assertThrows(IllegalStateException.class, TestMainFrame::new);
        assertTrue(ex.getMessage().contains("MainWindowRoute.bootstrap"),
                "message points migrator at bootstrap()");
    }

    @Test
    @DisplayName("MainWindow JFrame inside bootstrap captures the route")
    void mainWindowFrameInsideBootstrapCapturesTheRoute() {
        Bound<TestMainFrame> bound = inBootstrapWithRoute(TestMainFrame::new);
        assertSame(bound.route(), bound.frame().boundRoute());
    }

    @Test
    @DisplayName("MainWindow JFrame constructed via SwingUtilities.invokeLater inside bootstrap succeeds")
    void mainWindowFrameViaInvokeLaterInsideBootstrapSucceeds() {
        // Canonical desktop-Swing entry shape: SwingUtilities.invokeLater
        // around frame construction. An earlier ThreadLocal-based
        // MainWindowRoute.current() broke this: the deferred lambda
        // ran on a Vaadin VT continuation after bootstrap() returned
        // and the ThreadLocal cleared, so the @MainWindow JFrame ctor
        // saw a null current() and threw. UI-scoped current() survives
        // the ui.access + EHelper.callSwing hop, so the canonical shape
        // works as-is. Regression guard for the migration guide's
        // canonical mainUI() body.
        TestMainFrame[] captured = new TestMainFrame[1];
        TestRoute route = new TestRoute(
                () -> SwingUtilities.invokeLater(() -> captured[0] = new TestMainFrame()));
        UI.getCurrent().add(route);
        // ui.access enqueues; drain so the invokeLater body runs.
        MockVaadin.runUIQueue();
        TestMainFrame frame = captured[0];
        assertNotNull(frame, "SwingUtilities.invokeLater inside bootstrap did not execute");
        assertSame(InlineStrategy.INSTANCE, frame.strategy());
        assertSame(route, frame.boundRoute());
    }

    @Test
    @DisplayName("MainWindowRoute.current is UI-scoped (not ThreadLocal)")
    void mainWindowRouteCurrentIsUiScoped() {
        // Regression guard: replacing ThreadLocal CURRENT with
        // ComponentUtil.setData on the attached UI is what makes the
        // SwingUtilities.invokeLater shape work. Verifying the
        // mechanism directly so a future refactor can't quietly
        // re-introduce a ThreadLocal without failing this test.
        assertNull(MainWindowRoute.current(), "no route before any onAttach");
        TestRoute route = new TestRoute(() -> { /* no work inside bootstrap for this test */ });
        UI.getCurrent().add(route);
        assertSame(route, MainWindowRoute.current(), "current() reads the attached route from UI data");
        assertSame(route, ComponentUtil.getData(UI.getCurrent(), MainWindowRoute.class),
                "route registered as MainWindowRoute.class typed data on the UI");
        UI.getCurrent().remove(route);
        assertNull(MainWindowRoute.current(), "onDetach clears the UI's MainWindowRoute slot");
    }

    @Test
    @DisplayName("non-MainWindow JFrame leaves boundRoute null")
    void nonMainWindowFrameLeavesBoundRouteNull() {
        JFrame f = new JFrame();
        assertNull(f.boundRoute());
    }

    // ---- setVisible attach / detach ----------------------------------

    @Test
    @DisplayName("setVisible(true) attaches the InlineStrategy peer to the route")
    void setVisibleTrueAttachesThePeerToTheRoute() {
        Bound<TestMainFrame> bound = inBootstrapWithRoute(TestMainFrame::new);
        TestMainFrame frame = bound.frame();
        frame.setVisible(true);

        assertTrue(routeChildren(bound).contains(frame.peer().getElement()),
                "route has the frame's peer as a direct child");
        assertTrue(frame.isVisible());
    }

    @Test
    @DisplayName("setVisible(false) detaches the InlineStrategy peer")
    void setVisibleFalseDetachesThePeer() {
        Bound<TestMainFrame> bound = inBootstrapWithRoute(TestMainFrame::new);
        TestMainFrame frame = bound.frame();
        frame.setVisible(true);
        assertTrue(routeChildren(bound).contains(frame.peer().getElement()));

        frame.setVisible(false);

        assertFalse(routeChildren(bound).contains(frame.peer().getElement()),
                "route no longer has the frame's peer as a child");
    }

    // ---- Title --------------------------------------------------------

    @Test
    @DisplayName("InlineStrategy setTitle does not fail and round-trips title")
    void inlineStrategySetTitleRoundTrips() {
        // We can't easily inspect Page.setTitle through Karibu (it's a
        // browser-side write), but the call must not throw and the
        // emulator-side title field must round-trip via Frame.setTitle.
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setTitle("Hello");
        assertEquals("Hello", frame.getTitle());
    }

    // ---- dispose() -> end of app ------------------------------------
    //
    // Nothing strategy-specific: a dispose ends the app when it takes the last
    // displayable window with it, whatever the close op and whatever the peer, so
    // AutoShutdownTest owns those rows against the JDK/Xvfb oracle
    // (D_auto_shutdown). DialogStrategy's EXIT_ON_CLOSE throw on a peer-originated
    // close is in JFrameTest / SJFrameTest.

    // ---- JMenuBar (InlineStrategy) -----------------------------------

    @Test
    @DisplayName("InlineStrategy setJMenuBar attaches the bar above the contentPane on SJPanel")
    void inlineSetJMenuBarAttachesAboveTheContentPane() {
        // SJPanel has no menubar slot — InlineStrategy.applyMenuBar
        // bends the menubar's peer element under the SJPanel directly,
        // at index 0 so it renders above the JFrame's contentPane.
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        JMenuBar bar = new JMenuBar();
        frame.setJMenuBar(bar);

        assertSame(bar, frame.getJMenuBar());
        // The bar lives in the root pane's layered pane, above the content pane —
        // the JDK's containment, identical under both strategies now that the
        // menu bar is no longer a strategy-specific slot.
        List<Element> layeredChildren = layeredChildren(frame);
        assertEquals(bar.getPeer().getElement(), layeredChildren.get(0),
                "menubar's peer element is the first child of the layered pane");
        assertEquals(frame.getContentPane().getPeer().getElement(), layeredChildren.get(1),
                "content pane follows the menubar");
    }

    @Test
    @DisplayName("InlineStrategy setJMenuBar(null) detaches")
    void inlineSetJMenuBarNullDetaches() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        JMenuBar bar = new JMenuBar();
        frame.setJMenuBar(bar);
        assertTrue(layeredChildren(frame).contains(bar.getPeer().getElement()));

        frame.setJMenuBar(null);

        assertNull(frame.getJMenuBar());
        assertFalse(layeredChildren(frame).contains(bar.getPeer().getElement()),
                "menubar's peer element no longer a child of the layered pane after detach");
    }

    @Test
    @DisplayName("InlineStrategy setJMenuBar swap detaches old and attaches new")
    void inlineSetJMenuBarSwapDetachesOldAttachesNew() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        JMenuBar first = new JMenuBar();
        JMenuBar second = new JMenuBar();
        frame.setJMenuBar(first);
        frame.setJMenuBar(second);

        List<Element> layeredChildren = layeredChildren(frame);
        assertFalse(layeredChildren.contains(first.getPeer().getElement()), "old menubar removed");
        assertEquals(second.getPeer().getElement(), layeredChildren.get(0),
                "new menubar at index 0 of the layered pane");
    }

    @Test
    @DisplayName("InlineStrategy setJMenuBar fires no bound property")
    void inlineSetJMenuBarFiresNoBoundProperty() {
        // Same as JFrameTest's: no "JMenuBar" bound property exists in real
        // Swing, so neither strategy may invent one (R_decline_effect_only). The InlineStrategy
        // menu-bar *placement* is asserted by the tests above, which is the
        // part that actually differs between strategies.
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        vaadinx.Counter fired = new vaadinx.Counter();
        frame.addPropertyChangeListener("JMenuBar", e -> fired.inc());
        frame.setJMenuBar(new JMenuBar());
        fired.assertEquals(0);
    }

    // ---- R_leaf_peer_lockdown lock-down ------------------------------------------------

    @Test
    @DisplayName("JFrame exposes no protected (Component peer) ctor (R_leaf_peer_lockdown)")
    void jFrameExposesNoPeerInjectionCtor() {
        // R_leaf_peer_lockdown + D_framework_peer_selection: the framework selects the peer; user code can't
        // pass one. Reflection over declared ctors must show no
        // protected-or-public ctor that takes a single
        // com.vaadin.flow.component.Component (the peer-injection seam
        // a leaf would otherwise expose).
        List<Constructor<?>> matched = Arrays.stream(JFrame.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 1
                        && c.getParameterTypes()[0] == com.vaadin.flow.component.Component.class)
                .toList();
        assertEquals(List.of(), matched, "no peer-injection ctor exposed");
    }

    // ---- JDK window registries (R_no_vaadin_in_api limb 2) ---------------------------

    // The @MainWindow frame peers on an SJPanel attached to the route, not on
    // a Dialog, so EHelper.getWindows' Dialog-rooted walk used to miss it
    // entirely — Frame.getFrames() answered "no frames" to a migrated app that
    // had one. Silently, which is the failure shape R_no_vaadin_in_api limb 2 names: the hook
    // is present, compiles, and never reaches the thing it describes. A
    // migrator replacing a `static AppFrame _instance` singleton with the
    // JDK-faithful registry walk lands exactly here.

    @Test
    @DisplayName("MainWindow frame shows up in Frame.getFrames")
    void mainWindowFrameShowsUpInGetFrames() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);

        List<vaadinx.awt.Frame> frames = List.of(vaadinx.awt.Frame.getFrames());
        assertEquals(1, frames.size(), "the @MainWindow frame is a Frame");
        assertSame(frame, frames.get(0));
    }

    @Test
    @DisplayName("MainWindow frame shows up in Window.getWindows and getOwnerlessWindows")
    void mainWindowFrameShowsUpInGetWindows() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);

        assertTrue(List.of(vaadinx.awt.Window.getWindows()).contains(frame),
                "a JFrame is a Window, so getWindows must report it too");
        // owner == null on a top-level frame, so it lands in the ownerless
        // subset with no separate fix — the second expression a migrator
        // might reasonably reach for.
        assertTrue(List.of(vaadinx.awt.Window.getOwnerlessWindows()).contains(frame),
                "a top-level frame has no owner");
    }

    @Test
    @DisplayName("invisible MainWindow frame stays in the registries")
    void invisibleMainWindowFrameStaysInTheRegistries() {
        // The registries are creation-based, as AWT's are: hiding a Frame does
        // not undisplayable it, so it stays listed. Measured on JDK 25 — a
        // shown-then-hidden frame is still pinned by allWindows even after the
        // app drops every reference. This asserted the opposite while the
        // registry walked the UI graph, since setVisible(false) detaches the
        // peer from its route; that divergence is the one this front closed.
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);
        assertTrue(List.of(vaadinx.awt.Frame.getFrames()).contains(frame));

        frame.setVisible(false);

        assertTrue(List.of(vaadinx.awt.Frame.getFrames()).contains(frame),
                "hiding does not undisplayable, so the frame stays listed");
        assertTrue(frame.isDisplayable(), "and it is still displayable");

        // Only dispose() takes the pin off — the entry stays, weakly.
        frame.dispose();
        assertFalse(frame.isDisplayable());
        assertTrue(List.of(vaadinx.awt.Frame.getFrames()).contains(frame),
                "a disposed frame stays listed while the app still holds it");
    }

    @Test
    @DisplayName("MainWindow frame and a secondary Dialog-peered frame both appear once")
    void mainWindowAndSecondaryFrameBothAppearOnce() {
        // Two roots feed getWindows now (the route's inline frame + every
        // Dialog). A frame reachable from both must not be listed twice, and
        // one reachable from only the Dialog root must still be listed.
        TestMainFrame main = inBootstrap(TestMainFrame::new);
        main.setVisible(true);
        JFrame secondary = new JFrame();
        secondary.setVisible(true);

        List<vaadinx.awt.Frame> frames = List.of(vaadinx.awt.Frame.getFrames());
        assertEquals(2, frames.size(), "both frames, no duplicates: " + frames);
        assertTrue(frames.contains(main));
        assertTrue(frames.contains(secondary));
    }

    @Test
    @DisplayName("registries are empty with no route mounted")
    void registriesAreEmptyWithNoRouteMounted() {
        // Guards the no-UI / empty-graph path in EHelper.getWindows: a UI with
        // no frames must not throw or invent a window.
        assertEquals(List.of(), List.of(vaadinx.awt.Frame.getFrames()));
        assertEquals(List.of(), List.of(vaadinx.awt.Window.getWindows()));
    }

    // ---- Helpers ------------------------------------------------------

    /** A frame and the {@link TestRoute} whose bootstrap constructed it. */
    private record Bound<T extends JFrame>(TestRoute route, T frame) {
    }

    private static List<Element> routeChildren(Bound<?> bound) {
        return bound.route().getElement().getChildren().toList();
    }

    private static List<Element> layeredChildren(JFrame frame) {
        return frame.getRootPane().getLayeredPane().peerContentElement().getChildren().toList();
    }

    /**
     * Runs {@code block} inside a {@link MainWindowRoute#current()} scope so
     * an {@code @MainWindow} JFrame constructed within captures a route.
     *
     * @return the JFrame + the route used
     */
    private static <T extends JFrame> Bound<T> inBootstrapWithRoute(Supplier<T> block) {
        List<T> captured = new java.util.ArrayList<>();
        TestRoute route = new TestRoute(() -> captured.add(block.get()));
        UI.getCurrent().add(route);
        return new Bound<>(route, captured.get(0));
    }

    /** Same as {@link #inBootstrapWithRoute} but discards the route reference. */
    private static <T extends JFrame> T inBootstrap(Supplier<T> block) {
        return inBootstrapWithRoute(block).frame();
    }

    /** Ad-hoc MainWindowRoute that runs a lambda for bootstrap. */
    private static class TestRoute extends MainWindowRoute {

        private final Runnable onBootstrap;

        TestRoute(Runnable onBootstrap) {
            this.onBootstrap = onBootstrap;
        }

        @Override
        protected void bootstrap() {
            onBootstrap.run();
        }
    }

    /** Plain user-code JFrame subclass, no @MainWindow. */
    private static class NonAnnotatedFrame extends JFrame {
    }

    /** First @MainWindow class for tests. */
    @MainWindow
    private static class TestMainFrame extends JFrame {

        /**
         * Constructs a plain JFrame from a method declared on an
         * {@code @MainWindow} class, so its frame shows up on the stack as a
         * JFrame-subclass declaring class during the strategy picker's walk —
         * exactly the SamplerFrame.showDemo pathology.
         */
        static JFrame makePlainSecondaryJFrame() {
            return new JFrame();
        }
    }

    /** User subclass — should inherit @MainWindow via @Inherited. */
    private static class DerivedMainFrame extends TestMainFrame {
    }

    /** Second, distinct @MainWindow class for the multi-class throw test. */
    @MainWindow
    private static class OtherMainFrame extends JFrame {
    }
}
