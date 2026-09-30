/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JFrame
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import vaadinx.swing.app.MainWindowRoute;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Emulator for {@link javax.swing.JFrame}. Per
 * <a href="../../../emulators/decisions.md#D_jframe_as_route">D_jframe_as_route</a>,
 * a JFrame's rendering shape is selected at construction via
 * {@link FrameStrategy}: {@link DialogStrategy} (regular JFrame, peer is
 * {@link com.vaadin.swingbridge.surrogates.SJFrame}) or {@link InlineStrategy}
 * ({@link MainWindow @MainWindow}-annotated, peer is
 * {@link com.vaadin.swingbridge.surrogates.SJPanel}, attached to a
 * {@link MainWindowRoute}).
 *
 * <p>R_leaf_peer_lockdown-locked-down: no protected {@code (Component peer)} ctor — every
 * public ctor funnels through a private {@code (FrameStrategy)} ctor that
 * hard-codes the strategy's peer type. The framework selects among
 * {@code SJFrame}/{@code SJPanel} based on the {@code @MainWindow}
 * annotation per
 * <a href="../../../emulators/decisions.md#D_framework_peer_selection">D_framework_peer_selection</a>;
 * user code cannot influence the choice.
 */
public class JFrame extends vaadinx.awt.Frame
        implements javax.swing.WindowConstants, javax.accessibility.Accessible,
                   vaadinx.swing.RootPaneContainer {

    /**
     * Singleton slot for the {@code @MainWindow}-annotated class. CAS-set
     * on first ctor of a {@code @MainWindow} JFrame; subsequent ctors of
     * the SAME class are no-ops (CAS hits, classes equal — covers
     * multi-tab scenarios where each tab re-instantiates the same main
     * frame). A second DIFFERENT {@code @MainWindow} class throws
     * {@link IllegalStateException} on its first ctor — "at most one main
     * window class per JVM" per D_mainwindow_discovery.
     */
    private static final AtomicReference<Class<?>> MAIN_WINDOW_CLASS = new AtomicReference<>();

    /**
     * Pinned at ctor time per D_frame_strategy. {@link DialogStrategy#INSTANCE} for a
     * regular JFrame; {@link InlineStrategy#INSTANCE} for a
     * {@code @MainWindow} subclass. Drives every peer-side write
     * (visibility, dispose, title) through the strategy's hooks.
     */
    private final FrameStrategy strategy;

    /**
     * For {@link InlineStrategy} frames only — captured at ctor time
     * from {@link MainWindowRoute#current()}, the route currently
     * bootstrapping. {@code null} for {@link DialogStrategy} frames.
     */
    private final MainWindowRoute boundRoute;

    // The frame's one and only child, as in Swing: the root pane holds the
    // layered pane, which holds the menu bar and the content pane, and holds the
    // glass pane beside it. Planted by frameInit via setRootPane, so it is never
    // null after construction; the content pane / menu bar / glass pane
    // accessors all delegate through it rather than shadowing it.
    protected JRootPane rootPane;

    // AWT's rootPaneCheckingEnabled flag: when true (post-frameInit), the
    // public add/setLayout/remove methods redirect to the content pane so
    // migrated code sees Swing's "content pane is the real container"
    // structure. When false, super's methods run directly — which is how
    // frameInit itself plants the content pane inside the frame.
    protected boolean rootPaneCheckingEnabled;

    // Swing's JFrame default is HIDE_ON_CLOSE (see JFrame Javadoc —
    // "By default, this is set to HIDE_ON_CLOSE"). Most migrated apps
    // override this with EXIT_ON_CLOSE right after construction; a few use
    // DISPOSE_ON_CLOSE for transient sub-windows.
    private int defaultCloseOperation = javax.swing.WindowConstants.HIDE_ON_CLOSE;

    public JFrame(java.lang.String title, vaadinx.awt.GraphicsConfiguration gc) {
        // Accepted and ignored: one viewport, one configuration. Title seeds the
        // Dialog header via Frame.setTitle.
        this(title);
    }

    public JFrame(java.lang.String title) throws java.awt.HeadlessException {
        this();
        setTitle(title);
    }

    public JFrame(vaadinx.awt.GraphicsConfiguration gc) {
        this();
    }

    public JFrame() throws java.awt.HeadlessException {
        // Funnel through the private (FrameStrategy) ctor with a strategy
        // chosen by inspecting the runtime class via stack walk — Java
        // forbids `getClass()` in a `super(...)` invocation (JLS 8.8.7.1),
        // so the most-derived JFrame subclass on the construction stack is
        // the closest-correct proxy for the actual instance class.
        this(pickStrategyForCurrentInstance());
    }

    /**
     * The single ctor that actually selects the peer. R_leaf_peer_lockdown-locked-down:
     * private (not protected) so user-code subclasses can't bypass the
     * framework-canonical strategy choice per D_framework_peer_selection.
     */
    private JFrame(FrameStrategy strategy) {
        super(strategy.createPeer());
        this.strategy = strategy;
        // CAS check for @MainWindow uniqueness. The check must run AFTER
        // super(); we couldn't read getClass() in the super() call.
        Class<?> cls = getClass();
        if (cls.isAnnotationPresent(MainWindow.class)) {
            Class<?> existing = MAIN_WINDOW_CLASS.compareAndExchange(null, cls);
            if (existing != null && existing != cls) {
                throw new IllegalStateException(
                        "At most one @MainWindow JFrame class is supported per JVM. "
                                + "Already registered: " + existing.getName()
                                + "; attempted to register: " + cls.getName() + ".");
            }
            // The actual class is @MainWindow-annotated — gate on the real
            // class annotation, not on strategy. The strategy picker can
            // pick DialogStrategy as a safe default in the ambiguous
            // "stack-walk says @MainWindow but no route is mounted on
            // this UI" case (e.g. a stray @MainWindow ctor call from a
            // unit test without a route mounted); using the runtime class
            // here disambiguates the legitimate-throw path.
            if (MainWindowRoute.current() == null) {
                throw new IllegalStateException(
                        "@MainWindow JFrame " + cls.getName()
                                + " constructed with no MainWindowRoute mounted on the current UI. "
                                + "Construct it from inside MainWindowRoute.bootstrap() — "
                                + "typically via the migrated app's mainUI() method called "
                                + "from the route's bootstrap() override "
                                + "(SwingUtilities.invokeLater inside mainUI is supported).");
            }
        }
        // Capture the bootstrapping route for InlineStrategy frames.
        // DialogStrategy frames have no route binding.
        this.boundRoute = strategy == InlineStrategy.INSTANCE
                ? MainWindowRoute.current()
                : null;
        frameInit();
        // The @MainWindow (InlineStrategy) frame IS the app; register it as the
        // session's main window so session-destroy can deliver WINDOW_CLOSING to
        // it (D_shutdown_lifecycle). Secondary DialogStrategy frames aren't the app and don't
        // register. Idempotent under @PreserveOnRefresh — the frame instance is
        // preserved across an F5, so re-registration would be the same instance.
        if (strategy == InlineStrategy.INSTANCE) {
            registerForShutdown(this);
        }
    }

    /**
     * Pick a strategy by walking the construction stack to find the
     * deepest {@link JFrame} subclass <em>currently being constructed</em>
     * — the closest-available proxy for the instance's runtime class
     * while {@code super(...)} is still running. For canonical migration
     * paths ({@code new MyMainFrame()} from {@code mainUI()}) the deepest
     * JFrame-subclass constructor frame is the runtime class.
     *
     * <p>The {@code "<init>"} method-name filter is what disambiguates
     * "this construction is the main frame" from "we're transitively
     * constructing a secondary JFrame from inside an {@code @MainWindow}
     * class's <em>method</em>" — the method-frame's declaring class is
     * the {@code @MainWindow} class, but its frame's method name isn't
     * {@code "<init>"}, so the walker rejects it and falls through to
     * any actual JFrame-subclass ctor on the stack (or to JFrame itself
     * if none is). Without this filter, the picker would mis-route
     * secondary frames to InlineStrategy whenever an {@code @MainWindow}
     * class's method appears on the stack.
     *
     * <p>The previous design also consulted {@link MainWindowRoute#current()}
     * to gate "we're inside bootstrap()." That worked when {@code current()}
     * was a {@code ThreadLocal} cleared on {@code bootstrap()} return,
     * but it broke the canonical desktop-Swing entry shape — wrapping
     * frame construction in {@code SwingUtilities.invokeLater(...)} from
     * inside {@code mainUI()}. The wrapper defers the ctor onto a VT
     * continuation that runs after {@code bootstrap()} returns and any
     * thread-local clears. {@code MainWindowRoute.current()} is now
     * UI-scoped (via {@link com.vaadin.flow.component.ComponentUtil}),
     * so its presence stops being a "we're literally inside bootstrap"
     * gate. The {@code "<init>"} filter replaces that gate's role in
     * the strategy picker.
     */
    private static FrameStrategy pickStrategyForCurrentInstance() {
        Class<?> deepest = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stream -> stream
                        .filter(f -> "<init>".equals(f.getMethodName()))
                        .<Class<?>>map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(c -> JFrame.class.isAssignableFrom(c) && c != JFrame.class)
                        .reduce((a, b) -> b)  // last in walk = oldest = deepest user ctor
                        .orElse((Class<?>) JFrame.class));
        if (!deepest.isAnnotationPresent(MainWindow.class)) {
            return DialogStrategy.INSTANCE;
        }
        return MainWindowRoute.current() != null
                ? InlineStrategy.INSTANCE
                : DialogStrategy.INSTANCE;
    }

    /**
     * The JDK's own body (JFrame.java:255), line for line: enable the window
     * event masks, take the default locale, build the root pane through
     * {@link #createRootPane()} and plant it via {@link #setRootPane}, take the
     * L&amp;F control background, then turn root-pane checking on — each as a
     * <em>call</em>, so a subclass override of any hook actually runs
     * (R_no_vaadin_in_api limb 2). The frame's single child is the root pane;
     * everything else hangs off it.
     *
     * <p>Two of those lines land on deliberate nothings, and the
     * nothings are where the fidelity is: {@code enableEvents} no-ops
     * because dispatch is already unconditional, and
     * {@link UIManager#controlColor} is null because L&amp;F is out of
     * scope. Both still *run*, so a migrator's {@code setBackground}
     * override fires from the constructor exactly as on the desktop.
     */
    protected void frameInit() {
        enableEvents(java.awt.AWTEvent.KEY_EVENT_MASK | java.awt.AWTEvent.WINDOW_EVENT_MASK);
        setLocale(JComponent.getDefaultLocale());
        setRootPane(createRootPane());
        setBackground(UIManager.controlColor());
        // Content pane fills the window body rather than sitting at intrinsic
        // height inside it. The pane-level classes are applied by the surrogate
        // chain; this adds the host-level one, which only the frame knows about
        // — the classes and the measurements are in emul/swindow.css.
        com.vaadin.swingbridge.surrogates.SHelper.markContentPaneSpan(getPeer(), getContentPane().getPeer(), null);
        // Per D_inline_route_sizing: InlineStrategy frames fill their route by default
        // (SJPanel peer gets width:100% / height:100% so it stretches
        // inside the route's setSizeFull()). The setPreferredSize override
        // below re-pins 100%/100% for resizable frames so user setPreferredSize
        // calls don't shrink the peer — main windows in the browser are
        // sized by the host viewport, like under a tiling WM. The
        // setResizable(false) + setPreferredSize + pack() opt-out earns
        // pixel sizing via applyInlineSizingOnPack. DialogStrategy is
        // unaffected.
        if (strategy == InlineStrategy.INSTANCE) {
            InlineStrategy.INSTANCE.applyInlineSizingOnFrameInit(this);
        }
        // From here, frame.add / frame.setLayout / frame.remove(Component)
        // redirect to the content pane. Matches Swing's post-frameInit
        // state; user code that disables the flag (rare) is back to
        // touching the frame directly. A call, not a field write, so a
        // subclass override runs — the JDK's frameInit does the same.
        setRootPaneCheckingEnabled(true);
    }

    /**
     * {@link vaadinx.awt.Window#pack()} hook for the inline-strategy
     * route-sizing model — default path is a no-op (PWA shape: route stays
     * {@code setSizeFull}, SJPanel stays {@code width:100% / height:100%}
     * pinned by {@link #setPreferredSize} below). The
     * {@code setResizable(false) + setPreferredSize + pack()} opt-out path
     * runs the route-shrink + pixel-write in {@link InlineStrategy#applyInlineSizingOnPack}.
     * Per D_inline_route_sizing. DialogStrategy retains the inherited onNoop behaviour
     * (Dialog peer auto-sizes regardless).
     */
    @Override
    public void pack() {
        super.pack();
        if (strategy == InlineStrategy.INSTANCE) {
            InlineStrategy.INSTANCE.applyInlineSizingOnPack(this);
        }
    }

    /**
     * Overrides {@link vaadinx.awt.Component#setPreferredSize} for the
     * InlineStrategy PWA-shape default: a @MainWindow JFrame fills the
     * viewport regardless of the user's preferred-size request. Super still
     * field-shadows the value and fires PCE, so {@code getPreferredSize()}
     * round-trips per R_swing_is_truth; we re-pin {@code width:100% / height:100%} over
     * the parent's pixel write. The {@code setResizable(false)} carve-out
     * lets the parent's pixel write stand for fixed-size main windows.
     * DialogStrategy bypasses this branch — Dialog peer sizing is driven
     * by Component.setPreferredSize verbatim. Per D_inline_route_sizing.
     */
    @Override
    public void setPreferredSize(java.awt.Dimension preferredSize) {
        super.setPreferredSize(preferredSize);
        if (strategy == InlineStrategy.INSTANCE && isResizable()) {
            com.vaadin.flow.dom.Style s = peer().getElement().getStyle();
            s.set("width", "100%");
            s.set("height", "100%");
        }
    }

    /** Delegates to the root pane, as the JDK's does (JFrame.java:645). */
    public vaadinx.awt.Container getContentPane() {
        return getRootPane().getContentPane();
    }

    /**
     * Delegates to the root pane, as the JDK's does (JFrame.java:668) — so
     * {@code frame.getContentPane()} and
     * {@code frame.getRootPane().getContentPane()} are the same pane by
     * construction rather than by a mirror that has to be kept in step.
     */
    public void setContentPane(vaadinx.awt.Container newPane) {
        // Match real Swing (via JRootPane.setContentPane): null is a
        // programming error, not a silent drop. D_never_fail_on_gaps scopes the never-throw
        // rule to incomplete emulation, not to input errors Swing itself
        // rejects.
        if (newPane == null) {
            throw new java.awt.IllegalComponentStateException("contentPane cannot be set to null");
        }
        vaadinx.awt.Container old = getRootPane().getContentPane();
        if (newPane == old) return;
        getRootPane().setContentPane(newPane);
        withPeer(p -> com.vaadin.swingbridge.surrogates.SHelper.markContentPaneSpan(
                p, newPane.getPeer(), old == null ? null : old.getPeer()));
    }

    protected void addImpl(vaadinx.awt.Component comp, java.lang.Object constraints, int index) {
        // Real Swing pattern: intercept frame.add/etc. and redirect into
        // the content pane (JFrame.java:540). Calling getContentPane().add
        // (the public 3-arg overload) — not addImpl directly — keeps the
        // content pane's Swing-side children list and DOM tree consistent
        // through Container's full validation path (self-ancestor check,
        // reparent handling, COMPONENT_ADDED + HIERARCHY_CHANGED events).
        if (isRootPaneCheckingEnabled()) {
            getContentPane().add(comp, constraints, index);
        } else {
            super.addImpl(comp, constraints, index);
        }
    }

    public void setLayout(vaadinx.awt.LayoutManager manager) {
        // Swing apps call frame.setLayout(new BorderLayout()) expecting
        // the layout to apply to the content pane. Mirror that; with the
        // content pane already a Div, D_layout_css_on_content's applyContainerCss path will
        // write CSS to its peer element and actually lay out children.
        if (isRootPaneCheckingEnabled()) {
            getContentPane().setLayout(manager);
        } else {
            super.setLayout(manager);
        }
    }

    public void remove(vaadinx.awt.Component comp) {
        // Symmetric with addImpl's redirect: frame.remove(child) must
        // find the child in the content pane, not the frame's own
        // (rootPane-only) children list.
        if (isRootPaneCheckingEnabled()) {
            getContentPane().remove(comp);
        } else {
            super.remove(comp);
        }
    }

    protected void setRootPaneCheckingEnabled(boolean enabled) {
        this.rootPaneCheckingEnabled = enabled;
    }

    /**
     * Whether {@code add} / {@code remove} / {@code setLayout} redirect into the
     * content pane. The redirect consults this method rather than the field, so a
     * subclass override is honoured (R_no_vaadin_in_api second limb, D_r12_provenance).
     */
    protected boolean isRootPaneCheckingEnabled() {
        return rootPaneCheckingEnabled;
    }

    // JRootPane / layered pane / glass pane. The root pane is the frame's one
    // child, as in Swing, and the content pane / menu bar / glass pane all hang
    // off it — so there is nothing left to construct lazily: frameInit plants it.

    public JRootPane getRootPane() {
        return rootPane;
    }

    /**
     * The JDK's body (JFrame.java:613): remove the outgoing root pane, then add
     * the incoming one. Runs with root-pane checking cleared so
     * {@link #addImpl}'s redirect falls through to {@code Container}'s rather
     * than routing the new root pane into the old one's content pane.
     */
    protected void setRootPane(JRootPane newRootPane) {
        boolean wasChecking = rootPaneCheckingEnabled;
        rootPaneCheckingEnabled = false;
        try {
            if (rootPane != null) {
                super.remove(rootPane);
            }
            this.rootPane = newRootPane;
            if (newRootPane != null) {
                super.addImpl(newRootPane, null, -1);
            }
        } finally {
            rootPaneCheckingEnabled = wasChecking;
        }
    }

    protected JRootPane createRootPane() {
        // Factory hook — subclasses can override to supply a custom
        // JRootPane. Real Swing's JFrame calls this from frameInit;
        // ours constructs on-demand at getRootPane time instead.
        //
        // Share the SJRootPane surrogate (SD_sjframe) with the SJFrame
        // peer so the Enter-shortcut install installed via the
        // emulator path and any surrogate-path default-button reads
        // see the same browser-side registration. Pure-surrogate users
        // going through sjframe.getRootPane() and emulator users
        // going through jframe.getRootPane() now both reach the same
        // SJRootPane.
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJFrame sjf) {
            return new JRootPane(sjf.getRootPane());
        }
        // InlineStrategy peer (SJPanel) and any defensive non-SJFrame
        // path: independent SJRootPane keeps both layers working; just
        // no shared shortcut state with whatever peer the strategy
        // picked. Layered/glass-pane rendering remains deferred either
        // way.
        return new JRootPane();
    }

    public JLayeredPane getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    public void setLayeredPane(JLayeredPane layered) {
        getRootPane().setLayeredPane(layered);
    }

    public vaadinx.awt.Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    public void setGlassPane(vaadinx.awt.Component glass) {
        getRootPane().setGlassPane(glass);
    }

    public void update(java.awt.Graphics arg0) {
        // Real Swing's JFrame overrides update to skip the background clear
        // (since the frame paints its entire surface in paint anyway). We
        // have no painting to skip either way — Container.update delegates
        // to paint, paint recurses into children, and our leaf paint() is
        // an R_layouts_close_enough no-op. Chain up so user overrides on children still fire.
        super.update(arg0);
    }

    protected java.lang.String paramString() {
        // Swing appends ",defaultCloseOperation=<NAME>" to Frame's output.
        // Chain up so Frame's title/resizable/state and Container's layout=
        // line are in front of our addition — matches AWT's toString shape.
        java.lang.String s = super.paramString();
        java.lang.String opName = switch (defaultCloseOperation) {
            case javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE -> "DO_NOTHING_ON_CLOSE";
            case javax.swing.WindowConstants.HIDE_ON_CLOSE -> "HIDE_ON_CLOSE";
            case javax.swing.WindowConstants.DISPOSE_ON_CLOSE -> "DISPOSE_ON_CLOSE";
            case javax.swing.WindowConstants.EXIT_ON_CLOSE -> "EXIT_ON_CLOSE";
            default -> "UNKNOWN_CLOSE_OPERATION";
        };
        return s + ",defaultCloseOperation=" + opName;
    }

    /**
     * AWT's Window.processWindowEvent routes WINDOW_CLOSING to registered
     * listeners; we extend that here to also apply the default close
     * operation after the user's listeners have run. Running super first
     * matches Swing's ordering — if a WindowListener calls System.exit or
     * otherwise short-circuits the app, the default op doesn't get a
     * chance to interfere.
     *
     * <p>Per D_close_operation_dispatch, close-op dispatch differs by strategy:
     * <ul>
     *     <li>{@link DialogStrategy}: {@code EXIT_ON_CLOSE} throws
     *         {@link IllegalStateException} per D_gap_severity_triage case 2.
     *     <li>{@link InlineStrategy} disposes on {@code EXIT_ON_CLOSE}; the
     *         session then ends through {@link vaadinx.AutoShutdown} if that
     *         took the app's last displayable window (D_auto_shutdown).
     *     <li>{@code DISPOSE_ON_CLOSE} calls {@link #dispose()}; {@code HIDE} /
     *         {@code DO_NOTHING} are no-ops in both strategies (HIDE is
     *         already effectively done by the time we get here, and
     *         DO_NOTHING doesn't reach us in practice — setter disarms
     *         the peer's close gestures).
     * </ul>
     */
    protected void processWindowEvent(vaadinx.awt.event.WindowEvent e) {
        super.processWindowEvent(e);
        if (e == null) return;
        if (e.getID() != vaadinx.awt.event.WindowEvent.WINDOW_CLOSING) return;
        strategy.handleClosing(this, defaultCloseOperation);
    }

    /**
     * Strategy hook for {@link vaadinx.awt.Window#show()} /
     * {@link vaadinx.awt.Window#hide()} — delegates the peer-side write through
     * {@link FrameStrategy#applyVisibleToPeer} so InlineStrategy can substitute
     * a route-attach for the default Dialog-open path. Higher-level event firing
     * (COMPONENT_SHOWN/HIDDEN, WINDOW_OPENED) stays in Window, same for both
     * strategies.
     */
    @Override
    protected void applyVisibleToPeerImpl(boolean b) {
        strategy.applyVisibleToPeer(this, b);
    }

    /**
     * Strategy hook for {@link vaadinx.awt.Window#dispose()} — delegates
     * peer tear-down through {@link FrameStrategy#disposePeer} so
     * InlineStrategy can substitute a route-detach for the default
     * SWindow.dispose path.
     */
    @Override
    protected void disposePeer() {
        strategy.disposePeer(this);
    }

    @Override
    public void setTitle(java.lang.String title) {
        // Frame.setTitle already pushes to SFrame for the Dialog header
        // (DialogStrategy peer is SJFrame extends SFrame — instanceof
        // matches). For InlineStrategy peer (SJPanel), the instanceof
        // skips the peer write — afterTitleSet routes the title to the
        // browser tab title via UI.getCurrent().getPage().setTitle.
        super.setTitle(title);
        withPeer(p -> strategy.afterTitleSet(this, title));
    }

    public static boolean isDefaultLookAndFeelDecorated() {
        // AWT default is false — JFrames use OS-native chrome unless the L&F
        // explicitly opts in. We have no L&F layer at all (R_layouts_close_enough / D_pixel_layout_not_planned), and our
        // Dialog peer always renders Vaadin's own overlay chrome, so false
        // is both the honest post-construction answer and the correct one
        // for any code branching on "am I drawing my own chrome?".
        return false;
    }

    // The java.awt.event.WindowEvent-typed overload of processWindowEvent is
    // deliberately absent: the real one above takes vaadinx.awt.event.WindowEvent
    // and holds the close-op switch. A JDK-typed twin was a ghost — no
    // import-swapped code can produce a java.awt.event.WindowEvent to call it
    // with, and it shadowed nothing, so the swapped override already landed on
    // the real method. R_no_vaadin_in_api limb 1: one signature, in ported types.

    /**
     * Delegates to the root pane, as the JDK's does (JFrame.java:470) — so
     * {@code frame.setJMenuBar(x)} and {@code frame.getRootPane().setJMenuBar(x)}
     * are the same bar, where they used to be two. The bar is planted in the
     * root pane's layered pane, above the content pane.
     *
     * <p>No {@code "JMenuBar"} bound property: the JDK routes {@code setJMenuBar}
     * into {@code JRootPane}, and neither class fires anything for it. Firing one
     * here handed migrated code a PropertyChangeEvent that never existed (R_decline_effect_only,
     * D_window_fanout).
     *
     * <p>JDK-shape signature uses {@code javax.swing.JMenuBar}; we
     * accept {@link vaadinx.swing.JMenuBar} per the import-swap
     * migration arc (Stage 2 — user code rewrites
     * {@code import javax.swing.JMenuBar} → {@code import vaadinx.swing.JMenuBar}).
     */
    public void setJMenuBar(vaadinx.swing.JMenuBar bar) {
        getRootPane().setJMenuBar(bar);
    }

    public vaadinx.swing.JMenuBar getJMenuBar() {
        return getRootPane().getJMenuBar();
    }

    public void setIconImage(java.awt.Image arg0) {
        // Real Swing's JFrame override wraps in a List and delegates to
        // setIconImages. Window's setIconImage / setIconImages silent-accept
        // (favicons are route-level, not Window-level — see Window's
        // rationale). Chain up so the rationale stays in one place.
        super.setIconImage(arg0);
    }

    public java.awt.Graphics getGraphics() {
        // Real Swing's JFrame.getGraphics reaches through the root pane to
        // the native peer's Graphics. We don't have a live Graphics pipeline
        // (R_layouts_close_enough — the browser paints). Component.getGraphics already returns
        // a 1x1 off-screen BufferedImage's Graphics so every call on the
        // returned object works (drawRect, setColor, getFontMetrics, …)
        // just paints into nowhere visible — beats null-and-NPE. Chain up.
        return super.getGraphics();
    }

    public void repaint(long arg0, int arg1, int arg2, int arg3, int arg4) {
        // Real Swing routes the 5-arg repaint through the RepaintManager
        // (batched dirty-region invalidation). We have no repaint pipeline
        // (R_layouts_close_enough — browser repaints on DOM change), and Component already
        // treats every repaint overload as a no-op. Chain up to join the
        // no-op family rather than diverge with a re-stubbed WARN.
        super.repaint(arg0, arg1, arg2, arg3, arg4);
    }

    public void setDefaultCloseOperation(int operation) {
        // Delegate validation + gesture-disarm + EXIT_ON_CLOSE throw to
        // SJFrame (SD_sframe/SD_sjframe) — keeps the close-operation logic in one
        // place. SJFrame throws IllegalArgumentException on unknown values
        // (matching Swing's D_never_fail_on_gaps contract), fires its own surrogate-layer PCE,
        // and disables Dialog's close gestures on DO_NOTHING. We keep an
        // emulator-layer field + PCE so migrated PCE listeners registered
        // via JFrame hear the event on their own side (SD_sjslider/SD_sjspinner independent-
        // layer listener-list pattern).
        int old = this.defaultCloseOperation;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJFrame sjf) {
            withPeer(p -> sjf.setDefaultCloseOperation(operation));  // throws IAE on unknown
        } else {
            // InlineStrategy peer (SJPanel) and any defensive non-SJFrame
            // path — validate locally so the contract is preserved across
            // strategies.
            if (operation != javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE
                    && operation != javax.swing.WindowConstants.HIDE_ON_CLOSE
                    && operation != javax.swing.WindowConstants.DISPOSE_ON_CLOSE
                    && operation != javax.swing.WindowConstants.EXIT_ON_CLOSE) {
                throw new IllegalArgumentException(
                        "defaultCloseOperation must be one of: DO_NOTHING_ON_CLOSE, "
                                + "HIDE_ON_CLOSE, DISPOSE_ON_CLOSE, or EXIT_ON_CLOSE");
            }
        }
        if (old == operation) return;
        this.defaultCloseOperation = operation;
        firePropertyChange("defaultCloseOperation", old, operation);
    }

    public int getDefaultCloseOperation() {
        return defaultCloseOperation;
    }

    // D_drag_and_drop: signature ported to the emulator type so migrated code compiles
    // against vaadinx.swing.TransferHandler; window-level DnD wiring is out of
    // the D_drag_and_drop scope (JComponent / JList / JTable only) — drop-and-WARN.
    private vaadinx.swing.TransferHandler transferHandler;

    public void setTransferHandler(vaadinx.swing.TransferHandler newHandler) {
        // The undeliverable limb is SwingUtilities.installSwingDropTargetAsNecessary
        // — window-level DnD wiring is outside D_drag_and_drop's scope (JComponent / JList /
        // JTable only). The handler and its property change are kept: they cost
        // nothing and a migrated app's listener expects them (R_decline_effect_only).
        vaadinx.swing.TransferHandler oldHandler = this.transferHandler;
        this.transferHandler = newHandler;
        vaadinx.EHelper.onUnimplemented("JFrame", "setTransferHandler/dropTarget", newHandler);
        firePropertyChange("transferHandler", oldHandler, newHandler);
    }

    public vaadinx.swing.TransferHandler getTransferHandler() {
        return transferHandler;
    }

    public static void setDefaultLookAndFeelDecorated(boolean arg0) {
        // isDefaultLookAndFeelDecorated reports false; silent on a redundant
        // false-request, warn when user code asks for L&F-painted chrome
        // since we have no L&F layer to honor that (R_layouts_close_enough/D_pixel_layout_not_planned). Same warn-only-
        // on-change shape as setUndecorated(true) in Frame.
        if (arg0) {
            vaadinx.EHelper.onUnimplemented("JFrame", "setDefaultLookAndFeelDecorated", arg0);
        }
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JFrame", "getAccessibleContext");
        return null;
    }

    // ---- Package-private hooks for FrameStrategy ---------------------
    //
    // The strategy classes are in this same package. They access the
    // protected `peer` field (declared in vaadinx.awt.Component, not
    // visible cross-package) through these accessors, and read the
    // bound MainWindowRoute for InlineStrategy's attach/detach.

    /** Package-private peer accessor for {@link FrameStrategy} impls. */
    com.vaadin.flow.component.Component peer() {
        return getPeer();
    }

    /**
     * The {@link MainWindowRoute} this frame is bound to (InlineStrategy
     * only); {@code null} for DialogStrategy frames.
     */
    MainWindowRoute boundRoute() {
        return boundRoute;
    }

    /**
     * The strategy this frame was constructed with. Test-visible for
     * verifying R_leaf_peer_lockdown's "framework-canonical peer choice" contract — user
     * code shouldn't introspect this.
     */
    FrameStrategy strategy() {
        return strategy;
    }

    /**
     * Test-only reset of the {@code @MainWindow} class slot. Production
     * code should never need this — the slot is JVM-scoped by design.
     * Tests that exercise multiple {@code @MainWindow} classes across
     * fixtures need to clear the slot between fixtures. Package-private
     * so tests under {@code vaadinx.swing} can reach it.
     */
    static void resetMainWindowClassForTesting() {
        MAIN_WINDOW_CLASS.set(null);
    }

    // ─── Shutdown lifecycle (D_shutdown_lifecycle) ────────────────────────────────────────
    // The @MainWindow frame registers itself as the session's main window, then
    // WINDOW_CLOSING reaches the app through one of two dispatch triggers:
    //   (1) PROMPT — a real app-tab close. The tab-close detector fires it
    //       directly (SwingBridgeEmulatorsBootstrap → AppTab.onTabClosed → dispatchShutdownFromTabClose)
    //       on the tab-scope reaper thread. Necessary because that thread is
    //       request-less: session.close() there only sets state CLOSING, and
    //       VaadinSession-destroy (→ fireSessionDestroy) fires only at requestEnd,
    //       so relying on trigger (2) would defer WINDOW_CLOSING to the HTTP-session
    //       timeout (measured in a real browser; D_shutdown_lifecycle).
    //   (2) BACKSTOP — the session-destroy listener below (the F5-safe site),
    //       reached by in-app EXIT_ON_CLOSE dispose → session.close(), explicit
    //       logout, idle/container timeout, and the ~9-15% of tab closes the unload
    //       beacon misses.
    // The two dedup: whichever fires first nulls MAIN_WINDOW_KEY + marks
    // shuttingDown, so the other no-ops.

    /** Session attribute: the app's main {@code @MainWindow} JFrame (D_shutdown_lifecycle). */
    private static final String MAIN_WINDOW_KEY = "vaadinx.swing.JFrame.mainWindow";
    /** Session attribute marker: the session-destroy dispatch is installed (once per session). */
    private static final String SHUTDOWN_LISTENER_KEY = "vaadinx.swing.JFrame.shutdownListener";

    /**
     * Records {@code f} as the session's main window and installs the
     * session-destroy → {@code WINDOW_CLOSING} dispatch once per session (D_shutdown_lifecycle).
     * No-ops without a current UI/session (defensive — an {@code @MainWindow}
     * frame is always built under a live route, so this holds in practice).
     */
    private static void registerForShutdown(JFrame f) {
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        if (ui == null) return;
        com.vaadin.flow.server.VaadinSession session = ui.getSession();
        if (session == null) return;
        session.setAttribute(MAIN_WINDOW_KEY, f);
        // Only a running app has an end to detect, and this frame is what makes
        // the session one (D_auto_shutdown).
        vaadinx.AutoShutdown.enableForApp(session);
        if (session.getAttribute(SHUTDOWN_LISTENER_KEY) != null) return;   // already installed
        com.vaadin.flow.server.VaadinService service = session.getService();
        if (service == null) return;
        session.setAttribute(SHUTDOWN_LISTENER_KEY, Boolean.TRUE);
        final com.vaadin.flow.shared.Registration[] reg = new com.vaadin.flow.shared.Registration[1];
        reg[0] = service.addSessionDestroyListener(event -> {
            if (event.getSession() != session) return;
            try {
                dispatchShutdownClosing(session);
            } finally {
                if (reg[0] != null) reg[0].remove();
            }
        });
    }

    /**
     * Fires {@code WINDOW_CLOSING} on the session's main window when the app is
     * killed (real tab close, explicit logout, or container timeout). Runs
     * synchronously on the caller's thread — the session-destroy thread for the
     * backstop trigger, or the tab-scope reaper thread for the prompt tab-close
     * trigger ({@link #dispatchShutdownFromTabClose}). {@code WINDOW_CLOSING}
     * listeners do cleanup only (they can't cancel the close and can't show
     * dialogs — the {@code shuttingDown} flag makes the modal-park / blocking-await
     * seams throw).
     *
     * <p>Skipped when the main window is no longer visible: a programmatic
     * {@code dispose()} (e.g. a Quit menu, which reaches {@code session.close()}
     * through {@link vaadinx.AutoShutdown}) already ran its own
     * {@code WINDOW_CLOSED} and dropped visibility, so firing
     * {@code WINDOW_CLOSING} now would be out-of-order and redundant. That
     * visibility check is the D_shutdown_lifecycle dedup between the display-yanked path (fire)
     * and the app-asked-to-close path (already handled).
     *
     * <p>Package-private so tests can drive it synchronously — Karibu models
     * {@code fireSessionDestroy} as a deferred access task, awkward to drain
     * mid-test; the registration-wiring is covered separately via teardown.
     */
    static void dispatchShutdownClosing(com.vaadin.flow.server.VaadinSession session) {
        vaadinx.EHelper.markShuttingDown(session);
        Object mw = session.getAttribute(MAIN_WINDOW_KEY);
        session.setAttribute(MAIN_WINDOW_KEY, null);
        if (!(mw instanceof JFrame main) || !main.isVisible()) return;
        // Both callers run with the session lock held and VaadinSession.getCurrent()
        // already this session — the backstop inside Vaadin's session.access wrapper
        // (VaadinService.fireSessionDestroy → runPendingAccessTasks), the prompt path
        // on the reaper thread (tab-scope's reap asserts hasLock()). So the throw-seams
        // (Dialog.parkUntilClose, SwingWorker.get, SwingUtilities.invokeAndWait) read
        // isShuttingDown() through it. Don't touch CurrentInstance here: restoring it
        // to null trips Vaadin/Karibu's "session set to null" guard.
        try {
            main.processWindowEvent(new vaadinx.awt.event.WindowEvent(
                    main, vaadinx.awt.event.WindowEvent.WINDOW_CLOSING));
        } catch (RuntimeException e) {
            // A buggy WINDOW_CLOSING listener must not strand teardown (D_close_operation_dispatch):
            // route to the session ErrorHandler and continue.
            vaadinx.EHelper.reportUncaught(session, e);
        }
    }

    /**
     * Prompt-dispatch entry for the tab-scope destroy listener
     * ({@link vaadinx.AppTab#onTabClosed}): fires the D_shutdown_lifecycle {@code WINDOW_CLOSING}
     * on a real app-tab close directly, rather than waiting for the
     * {@link com.vaadin.flow.server.VaadinSession}-destroy backstop. Public only
     * to cross the {@code vaadinx} → {@code vaadinx.swing} package seam; see the
     * "Shutdown lifecycle (D_shutdown_lifecycle)" note above for why the reaper thread can't rely
     * on the backstop.
     */
    public static void dispatchShutdownFromTabClose(com.vaadin.flow.server.VaadinSession session) {
        dispatchShutdownClosing(session);
    }
}
