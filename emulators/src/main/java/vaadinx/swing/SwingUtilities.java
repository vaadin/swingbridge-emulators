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
 * This file is derived from OpenJDK's javax.swing.SwingUtilities
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import vaadinx.BrowserSessionClosedError;
import vaadinx.EHelper;
import vaadinx.EmulatorContext;
import vaadinx.awt.Component;
import vaadinx.awt.Container;
import vaadinx.awt.Window;
import vaadinx.awt.event.MouseEvent;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Emulator for {@link javax.swing.SwingUtilities}. Splits the JDK surface
 * three ways:
 *
 * <ul>
 *   <li><b>EDT primitives</b> — {@link #invokeLater}, {@link #invokeAndWait},
 *       {@link #isEventDispatchThread} bottom out in {@code UI.getCurrent()},
 *       the Vaadin UI-thread proxy.</li>
 *   <li><b>Tree walks</b> — ancestor / root-pane / window / class-named
 *       lookups walk our emulator parent chain ({@link Component#getParent()})
 *       and resolve cleanly. The Rectangle math overloads
 *       ({@code computeIntersection / Union / Difference},
 *       {@code isRectangleContainingRectangle}) and the mouse-button bit
 *       checks ({@code isLeftMouseButton} et al.) are pure value-math and
 *       behave exactly as the JDK implementation.</li>
 *   <li><b>Pixel / paint / accessibility / L&amp;F surface</b> — coordinate
 *       conversions, deepest-component-at, paint, layoutCompoundLabel,
 *       FontMetrics-based string width, accessibility queries, processKeyBindings
 *       / notifyAction key dispatch, UI input/action map plumbing, viewport
 *       wrapper helpers, focus-owner walk, and {@code updateComponentTreeUI}
 *       all WARN via {@link EHelper#onUnimplemented} and return safe defaults.
 *       These pieces of the JDK surface depend on machinery we don't have
 *       (a Graphics pipeline, a FontMetrics implementation, an L&amp;F
 *       dispatcher, the AWT focus-traversal engine), so we surface the gap
 *       in the migration log instead of fabricating answers.</li>
 * </ul>
 *
 * <p>"EDT" maps onto "Vaadin UI thread context with the session lock." The
 * proxy is {@code UI.getCurrent() != null}: every legitimate
 * Swing-side codepath enters with a current UI (peer event listeners,
 * timer fires, worker process/done callbacks all funnel through
 * {@link vaadinx.EHelper#callSwing} which sets the UI). A background
 * thread the user spawned themselves has no current UI and so reports
 * "not on EDT" — matching Swing's model.
 *
 * <p><b>From a background thread</b>, {@link #invokeLater} / {@link #invokeAndWait}
 * reach the EDT through the thread's {@link vaadinx.EmulatorContext}: the runnable
 * is delivered to the session's live UI the way a {@link Timer} fire or a
 * {@link SwingWorker#done()} is ({@link EHelper#onSessionLiveUI}). The context
 * carries the session but never makes a UI current, so these calls cannot resolve
 * the EDT from {@code UI.getCurrent()} there. A thread with no context has no
 * session to deliver to and throws {@link IllegalStateException}
 * (D_invoke_from_background).
 */
public final class SwingUtilities implements javax.swing.SwingConstants {

    private SwingUtilities() {}

    // ─── EDT primitives ──────────────────────────────────────────────────

    /**
     * @return {@code true} when called from a Vaadin UI thread context.
     */
    public static boolean isEventDispatchThread() {
        return UI.getCurrent() != null;
    }

    /**
     * Posts the runnable to the Vaadin UI thread asynchronously. Runs
     * inside a {@link vaadinx.EHelper#callSwing} virtual-thread continuation
     * so the runnable can park on blocking dialogs (R_callswing_envelope envelope around
     * every Swing-side event handler).
     *
     * <p>From a background thread the runnable is dropped, silently, when the session
     * has no live UI left — a closed tab has no EDT to run it on.
     *
     * @throws IllegalStateException on a thread with neither a current UI nor an
     *         {@link vaadinx.EmulatorContext} — see class doc.
     */
    public static void invokeLater(Runnable runnable) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.access(() -> EHelper.callSwing(runnable));
            return;
        }
        EHelper.onSessionLiveUI(requireContextSession("invokeLater"), runnable);
    }

    /**
     * Posts the runnable to the Vaadin UI thread and blocks the calling
     * thread until it has run. Throws {@link IllegalStateException}
     * when called from the EDT itself — under Swing this would deadlock
     * (D_gap_severity_triage case 2: loom rescue trigger). Migrators surfaced by
     * this throw should either move the call off the EDT or use
     * {@link #invokeLater} for fire-and-forget dispatch.
     *
     * <p>"Has run" includes a modal dialog the runnable opens: the call returns once
     * the user has answered it, as on the desktop.
     *
     * @throws IllegalStateException when called from the EDT, or on a thread with
     *         no {@link vaadinx.EmulatorContext} — see class doc.
     * @throws InvocationTargetException if the runnable throws.
     * @throws InterruptedException if the wait is interrupted.
     * @throws BrowserSessionClosedError if the session has no live UI to run the
     *         runnable on, or loses it while the runnable waits on a modal dialog —
     *         nothing would ever end the wait (R_match_swing_errors case (8))
     */
    public static void invokeAndWait(Runnable runnable)
            throws InterruptedException, InvocationTargetException {
        if (EHelper.isShuttingDown()) {
            // D_shutdown_lifecycle: during the session-destroy → WINDOW_CLOSING dispatch there
            // is no live UI to hop to, so the wait would never be satisfied.
            // Name shutdown as the cause rather than surface the downstream
            // "no current UI" error from requireCurrentUI.
            throw new IllegalStateException(
                    "Application is shutting down — SwingUtilities.invokeAndWait cannot hop to the UI thread " +
                    "(no live UI during teardown). Do synchronous cleanup only inside WINDOW_CLOSING listeners.");
        }
        if (isEventDispatchThread()) {
            throw new IllegalStateException(
                    "SwingUtilities.invokeAndWait called from the EDT; this would deadlock under Swing — " +
                    "use invokeLater or move the call off the EDT.");
        }
        VaadinSession session = requireContextSession("invokeAndWait");
        // Completed from inside the UI fiber, so a modal dialog the runnable parks on
        // keeps this caller waiting until its answer, not merely until the park.
        CompletableFuture<Void> ran = new CompletableFuture<>();
        try {
            session.access(() -> {
                UI live = EHelper.singleLiveUI(session);
                if (live == null) {
                    ran.completeExceptionally(new BrowserSessionClosedError(
                            "SwingUtilities.invokeAndWait has no EDT to run on: the browser tab is gone "
                            + "(no live UI in this session), so the runnable can never run."));
                    return;
                }
                try {
                    live.accessSynchronously(() -> EHelper.callSwing(() -> {
                        try {
                            runnable.run();
                            ran.complete(null);
                        } catch (BrowserSessionClosedError e) {
                            // A modal inside the runnable whose tab closed: tell the waiting
                            // caller, and let callSwing end the fiber quietly at its root.
                            ran.completeExceptionally(e);
                            throw e;
                        } catch (Throwable t) {
                            // Caught like the JDK's InvocationEvent with catchThrowables: the
                            // caller owns the failure, the EDT's handler never sees it.
                            ran.completeExceptionally(new InvocationTargetException(t));
                        }
                    }));
                } catch (RuntimeException | Error e) {
                    // The envelope refused before the runnable started; without this the
                    // caller would wait forever. A no-op once the runnable has completed it.
                    ran.completeExceptionally(e);
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            throw new BrowserSessionClosedError("SwingUtilities.invokeAndWait has no EDT to run on: "
                    + "the session is gone.", e);
        }
        try {
            ran.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof InvocationTargetException ite) throw ite;
            if (e.getCause() instanceof Error err) throw err;
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        }
    }

    /**
     * The session a background thread's {@link #invokeLater} / {@link #invokeAndWait}
     * delivers to, off its {@link vaadinx.EmulatorContext}.
     */
    private static VaadinSession requireContextSession(String method) {
        EmulatorContext ctx = EmulatorContext.getOrNull();
        if (ctx == null || ctx.session() == null) {
            throw new IllegalStateException(
                    "SwingUtilities." + method + " was called on a thread with no current UI and no "
                    + "EmulatorContext, so there is no Vaadin session whose UI thread could run it. "
                    + "Background work started with a plain Thread or an unwrapped executor carries no "
                    + "session. Fix it where the work is started: build the pool with "
                    + "EmulatorContext.wrap(executor), or capture EmulatorContext.get() on the UI thread "
                    + "and run the body inside ctx.run(...). SwingWorker does this for you.");
        }
        return ctx.session();
    }

    // ─── Tree walks (easy, faithful) ─────────────────────────────────────

    /**
     * Walks {@code c}'s emulator parent chain and returns the first
     * {@link Window} ancestor (excluding {@code c} itself), or {@code null}
     * if none. Mirrors {@link javax.swing.SwingUtilities#getWindowAncestor}.
     */
    public static Window getWindowAncestor(Component c) {
        for (Container p = c == null ? null : c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof Window w) return w;
        }
        return null;
    }

    /** Alias for {@link #getWindowAncestor} — same JDK contract. */
    public static Window windowForComponent(Component c) {
        return getWindowAncestor(c);
    }

    /**
     * Returns the {@link JRootPane} that contains {@code c}, or {@code null}
     * if none — the JDK's own body, reachable because {@link RootPaneContainer}
     * is ported (D_hierarchy_parity). The container test comes first and covers
     * {@code c} itself, so {@code getRootPane(frame)} answers the frame's root
     * pane rather than walking past it.
     */
    public static JRootPane getRootPane(Component c) {
        if (c instanceof RootPaneContainer rpc) {
            return rpc.getRootPane();
        }
        for ( ; c != null; c = c.getParent()) {
            if (c instanceof JRootPane rp) {
                return rp;
            }
        }
        return null;
    }

    /**
     * Returns the topmost ancestor of {@code c}: either the first
     * {@link Window} ancestor or, if none, the topmost ancestor with no
     * parent. {@code c} itself is returned when it has no parent.
     */
    public static Component getRoot(Component c) {
        if (c == null) return null;
        Component last = c;
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            last = p;
            if (p instanceof Window) return p;
        }
        return last;
    }

    /**
     * @return {@code true} if {@code a} is {@code b} or a descendant of
     *         {@code b} in the emulator parent chain.
     */
    public static boolean isDescendingFrom(Component a, Component b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        for (Container p = a.getParent(); p != null; p = p.getParent()) {
            if (p == b) return true;
        }
        return false;
    }

    /**
     * Walks {@code c}'s parent chain (starting at {@code c.getParent()})
     * and returns the first ancestor whose runtime class is assignable to
     * {@code clazz}, or {@code null} if none.
     */
    public static Container getAncestorOfClass(Class<?> clazz, Component c) {
        if (clazz == null || c == null) return null;
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (clazz.isInstance(p)) return p;
        }
        return null;
    }

    /**
     * Walks {@code c}'s parent chain (starting at {@code c.getParent()})
     * and returns the first ancestor whose {@link Component#getName() name}
     * matches {@code name}, or {@code null} if none.
     */
    public static Container getAncestorNamed(String name, Component c) {
        if (name == null || c == null) return null;
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (name.equals(p.getName())) return p;
        }
        return null;
    }

    /**
     * JDK contract: returns parent unwrapped through a {@code JViewport}.
     * We don't emulate {@code JViewport} (Vaadin scrolling doesn't go
     * through a viewport peer), so this just returns {@link Component#getParent()}.
     */
    public static Container getUnwrappedParent(Component c) {
        return c == null ? null : c.getParent();
    }

    // ─── Mouse-button bit checks (easy, value-math) ──────────────────────

    private static final int BUTTON1 = 1;
    private static final int BUTTON2 = 2;
    private static final int BUTTON3 = 3;

    public static boolean isLeftMouseButton(MouseEvent e) {
        return (e.getModifiersEx() & vaadinx.awt.event.InputEvent.BUTTON1_DOWN_MASK) != 0
                || e.getButton() == BUTTON1;
    }

    public static boolean isMiddleMouseButton(MouseEvent e) {
        return (e.getModifiersEx() & vaadinx.awt.event.InputEvent.BUTTON2_DOWN_MASK) != 0
                || e.getButton() == BUTTON2;
    }

    public static boolean isRightMouseButton(MouseEvent e) {
        return (e.getModifiersEx() & vaadinx.awt.event.InputEvent.BUTTON3_DOWN_MASK) != 0
                || e.getButton() == BUTTON3;
    }

    // ─── Rectangle math (delegate to JDK) ───────────────────────────────

    public static boolean isRectangleContainingRectangle(Rectangle a, Rectangle b) {
        return javax.swing.SwingUtilities.isRectangleContainingRectangle(a, b);
    }

    public static Rectangle computeIntersection(int x, int y, int width, int height, Rectangle dest) {
        return javax.swing.SwingUtilities.computeIntersection(x, y, width, height, dest);
    }

    public static Rectangle computeUnion(int x, int y, int width, int height, Rectangle dest) {
        return javax.swing.SwingUtilities.computeUnion(x, y, width, height, dest);
    }

    public static Rectangle[] computeDifference(Rectangle rectA, Rectangle rectB) {
        return javax.swing.SwingUtilities.computeDifference(rectA, rectB);
    }

    /**
     * Index of the first occurrence of {@code mnemonic} in {@code text}, ignoring case, or
     * -1 — also for a lower-case {@code mnemonic}, which is not a {@code VK_} code. The JDK's
     * package-private helper; its copy is unreachable from here.
     *
     * @param text may be {@code null}
     */
    static int findDisplayedMnemonicIndex(String text, int mnemonic) {
        if (text == null || mnemonic == '\0') {
            return -1;
        }

        if (mnemonic >= 'a' && mnemonic <= 'z') {
            return -1;
        }

        char uc = Character.toUpperCase((char) mnemonic);
        char lc = Character.toLowerCase((char) mnemonic);

        int uci = text.indexOf(uc);
        int lci = text.indexOf(lc);

        if (uci == -1) {
            return lci;
        } else if (lci == -1) {
            return uci;
        } else {
            return (lci < uci) ? lci : uci;
        }
    }

    // ─── WARN-and-default: pixel coordinates / bounds ───────────────────

    public static Rectangle getLocalBounds(Component c) {
        EHelper.onUnimplemented("SwingUtilities", "getLocalBounds", c);
        return new Rectangle();
    }

    public static Point convertPoint(Component source, Point aPoint, Component destination) {
        EHelper.onUnimplemented("SwingUtilities", "convertPoint", source, aPoint, destination);
        return aPoint == null ? new Point() : new Point(aPoint);
    }

    public static Point convertPoint(Component source, int x, int y, Component destination) {
        EHelper.onUnimplemented("SwingUtilities", "convertPoint", source, x, y, destination);
        return new Point(x, y);
    }

    public static Rectangle convertRectangle(Component source, Rectangle aRectangle, Component destination) {
        EHelper.onUnimplemented("SwingUtilities", "convertRectangle", source, aRectangle, destination);
        return aRectangle == null ? new Rectangle() : new Rectangle(aRectangle);
    }

    public static MouseEvent convertMouseEvent(Component source, MouseEvent sourceEvent, Component destination) {
        EHelper.onUnimplemented("SwingUtilities", "convertMouseEvent", source, sourceEvent, destination);
        return sourceEvent;
    }

    public static void convertPointToScreen(Point p, Component c) {
        EHelper.onUnimplemented("SwingUtilities", "convertPointToScreen", p, c);
    }

    public static void convertPointFromScreen(Point p, Component c) {
        EHelper.onUnimplemented("SwingUtilities", "convertPointFromScreen", p, c);
    }

    public static Component getDeepestComponentAt(Component parent, int x, int y) {
        EHelper.onUnimplemented("SwingUtilities", "getDeepestComponentAt", parent, x, y);
        return null;
    }

    public static Rectangle calculateInnerArea(JComponent c, Rectangle r) {
        EHelper.onUnimplemented("SwingUtilities", "calculateInnerArea", c, r);
        return r == null ? new Rectangle() : r;
    }

    // ─── WARN-and-default: focus / paint / fonts ────────────────────────

    public static Component findFocusOwner(Component c) {
        EHelper.onUnimplemented("SwingUtilities", "findFocusOwner", c);
        return null;
    }

    public static int computeStringWidth(java.awt.FontMetrics fm, String str) {
        EHelper.onUnimplemented("SwingUtilities", "computeStringWidth", fm, str);
        return 0;
    }

    public static String layoutCompoundLabel(JComponent c, java.awt.FontMetrics fm, String text, Icon icon,
                                             int verticalAlignment, int horizontalAlignment,
                                             int verticalTextPosition, int horizontalTextPosition,
                                             Rectangle viewR, Rectangle iconR, Rectangle textR,
                                             int textIconGap) {
        EHelper.onUnimplemented("SwingUtilities", "layoutCompoundLabel",
                c, fm, text, icon,
                verticalAlignment, horizontalAlignment,
                verticalTextPosition, horizontalTextPosition,
                viewR, iconR, textR, textIconGap);
        return text == null ? "" : text;
    }

    public static String layoutCompoundLabel(java.awt.FontMetrics fm, String text, Icon icon,
                                             int verticalAlignment, int horizontalAlignment,
                                             int verticalTextPosition, int horizontalTextPosition,
                                             Rectangle viewR, Rectangle iconR, Rectangle textR,
                                             int textIconGap) {
        EHelper.onUnimplemented("SwingUtilities", "layoutCompoundLabel",
                fm, text, icon,
                verticalAlignment, horizontalAlignment,
                verticalTextPosition, horizontalTextPosition,
                viewR, iconR, textR, textIconGap);
        return text == null ? "" : text;
    }

    public static void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h) {
        EHelper.onUnimplemented("SwingUtilities", "paintComponent", g, c, p, x, y, w, h);
    }

    public static void paintComponent(Graphics g, Component c, Container p, Rectangle r) {
        EHelper.onUnimplemented("SwingUtilities", "paintComponent", g, c, p, r);
    }

    /**
     * L&amp;F dispatch is permanently deferred per R_match_swing_errors sub-bucket (b) — the
     * styling story lives in a later Vaadin+CSS pass. We WARN so migration
     * logs surface the call site, but make no peer changes.
     */
    public static void updateComponentTreeUI(Component c) {
        EHelper.onUnimplemented("SwingUtilities", "updateComponentTreeUI", c);
    }

    // ─── WARN-and-default: key bindings / Action dispatch / UI maps ─────

    public static boolean processKeyBindings(vaadinx.awt.event.KeyEvent event) {
        EHelper.onUnimplemented("SwingUtilities", "processKeyBindings", event);
        return false;
    }

    public static boolean notifyAction(javax.swing.Action action,
                                       javax.swing.KeyStroke ks,
                                       vaadinx.awt.event.KeyEvent event,
                                       Object sender,
                                       int modifiers) {
        EHelper.onUnimplemented("SwingUtilities", "notifyAction", action, ks, event, sender, modifiers);
        return false;
    }

    public static void replaceUIInputMap(JComponent c, int condition, javax.swing.InputMap map) {
        EHelper.onUnimplemented("SwingUtilities", "replaceUIInputMap", c, condition, map);
    }

    public static void replaceUIActionMap(JComponent c, javax.swing.ActionMap map) {
        EHelper.onUnimplemented("SwingUtilities", "replaceUIActionMap", c, map);
    }

    public static javax.swing.InputMap getUIInputMap(JComponent c, int condition) {
        EHelper.onUnimplemented("SwingUtilities", "getUIInputMap", c, condition);
        return null;
    }

    public static javax.swing.ActionMap getUIActionMap(JComponent c) {
        EHelper.onUnimplemented("SwingUtilities", "getUIActionMap", c);
        return null;
    }

    // ─── WARN-and-default: accessibility ────────────────────────────────

    public static int getAccessibleIndexInParent(Component c) {
        EHelper.onUnimplemented("SwingUtilities", "getAccessibleIndexInParent", c);
        return -1;
    }

    public static javax.accessibility.Accessible getAccessibleAt(Component c, Point p) {
        EHelper.onUnimplemented("SwingUtilities", "getAccessibleAt", c, p);
        return null;
    }

    public static javax.accessibility.AccessibleStateSet getAccessibleStateSet(Component c) {
        EHelper.onUnimplemented("SwingUtilities", "getAccessibleStateSet", c);
        return new javax.accessibility.AccessibleStateSet();
    }

    public static int getAccessibleChildrenCount(Component c) {
        EHelper.onUnimplemented("SwingUtilities", "getAccessibleChildrenCount", c);
        return 0;
    }

    public static javax.accessibility.Accessible getAccessibleChild(Component c, int i) {
        EHelper.onUnimplemented("SwingUtilities", "getAccessibleChild", c, i);
        return null;
    }
}
