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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Shared plumbing for {@code com.vaadin.swingbridge.surrogates.*}. Surrogate-local mirror of
 * {@code vaadinx.EHelper} — module direction is {@code :emulators → :surrogates},
 * so we can't reuse the emulator's helper without inverting it (SD_shelper_statics).
 */
public final class SHelper {

    private static final Logger log = LoggerFactory.getLogger(SHelper.class);

    /**
     * Test-only capture sink for {@link #onUnimplemented}. Tests install a
     * collector to assert "this path fires no stub-level WARNs" without
     * having to reconfigure logging; production stays on the no-op default
     * so call sites can feed the hook unconditionally.
     */
    public static java.util.function.Consumer<String> warnHook = msg -> {};

    private SHelper() {}

    /**
     * Warn-and-continue stub hook for unimplemented surrogate API. Logs WARN
     * and returns; never throws (R_match_swing_errors / SD_dropin_stance). The first arg is the receiver
     * instance — its runtime class shows up in the log, so a surrogate
     * extending different Vaadin types each report their actual class name.
     */
    public static void onUnimplemented(Object self, String method, Object... args) {
        String className = self == null ? "null" : self.getClass().getSimpleName();
        String msg = "Unimplemented " + className + "." + method + "(" + format(args) + ")";
        log.warn(msg);
        warnHook.accept(msg);
    }

    /**
     * Uniform funnel for Vaadin peer → Swing-side callbacks. Every
     * surrogate's Vaadin-listener body whose job is to fire a Swing-style
     * event (model change, ChangeListener fan-out, focus, value-change,
     * window closing, …) wraps through here instead of calling
     * {@code runnable.run()} directly.
     *
     * <p>Today this is literally {@code runnable.run()} and will stay
     * that way (SD_sframe). Blocking-dialog handling is an
     * {@code :emulators}-layer concern: {@link com.vaadin.flow.component.dialog.Dialog#setOpened(boolean)}
     * and modal {@code JOptionPane.showXxxDialog} calls don't block the
     * Vaadin request thread without the UI-fiber hook,
     * and that hook lives only in {@code vaadinx.EHelper.callSwing} —
     * surrogates can't hack around it without duplicating machinery we
     * intentionally scope to the emulator layer. The seam survives here
     * as a uniform peer→Swing funnel for future cross-cutting work (log
     * instrumentation, error-handler routing, etc.) that would otherwise
     * have to audit every listener.
     *
     * <p>Exceptions propagate. Vaadin's {@link com.vaadin.flow.server.ErrorHandler}
     * is the proper escalation path; swallowing here would pre-empt user
     * error handling and hide real bugs.
     */
    public static void callSwing(Runnable runnable) {
        runnable.run();
    }

    /**
     * Runs {@code body} on {@code owner}'s UI thread and returns once it has run — the
     * surrogate-side hop for the carve-out in R_tolerate_off_ui_thread's surrogate limb, which
     * allows it only inside a callback from a Swing model.
     *
     * <pre>{@code
     * // SJComboBox's ListDataListener, on whatever thread mutated the model:
     * // Allowed by R_tolerate_off_ui_thread because callback from model: ComboBoxModel ListDataListener
     * SHelper.runOnOwnerUI(this, () -> onModelEvent(e));
     * }</pre>
     *
     * <p>Goes through the session {@code owner} captured ({@link #sessionOf}); see
     * {@link #runOnSession} for what happens under its lock.
     *
     * <p>Synchronous by design, per SD_background_model_hop: Swing fires a
     * model's listeners before the mutator returns, and a deferred body would
     * reorder them against the caller's next line.
     */
    public static void runOnOwnerUI(com.vaadin.flow.component.Component owner, Runnable body) {
        runOnSession(sessionOf(owner), owner, body);
    }

    /**
     * Runs {@code body}, which touches {@code peer}, under {@code session}'s lock and with
     * {@code peer}'s UI current, and returns once it has run: the one implementation of the
     * UI hop, behind both {@link #runOnOwnerUI} and the emulators' {@code withPeer}.
     *
     * <p>Nothing about {@code peer} is read before the lock is held. Its state node, its
     * parent chain and {@code getUI()} are written by the UI thread under that lock, so an
     * unlocked read races it, and {@code Composite.getElement()} even initialises content.
     * Under the lock, the UI owning the peer's state tree is the one made current; a peer
     * detached after being attached still names its old tree, which Flow still guards.
     *
     * <p>Inline when a UI is already current (the caller holds the lock), and when
     * {@code session} is {@code null}: the peer has captured none, so it has never been
     * attached, has no state tree, and has no lock to take. If the hop itself fails before
     * the body starts, it is logged at WARN and the body runs inline, unlocked. An exception
     * from the body always propagates, and the body never runs twice.
     *
     * @param session the session {@code peer} captured, or {@code null} if it has none
     */
    public static void runOnSession(com.vaadin.flow.server.VaadinSession session,
            com.vaadin.flow.component.Component peer, Runnable body) {
        if (session == null || com.vaadin.flow.component.UI.getCurrent() != null) {
            body.run();
            return;
        }
        // Set where the body starts, so a throw from the body itself always propagates and a
        // failure of the hop never runs the body twice.
        boolean[] started = {false};
        Runnable tracked = () -> {
            started[0] = true;
            body.run();
        };
        try {
            session.accessSynchronously(() -> {
                com.vaadin.flow.component.UI ui = ownerUI(peer);
                if (ui == null || ui.getSession() != session) {
                    tracked.run();
                    return;
                }
                try {
                    ui.accessSynchronously(tracked::run);
                } catch (com.vaadin.flow.component.UIDetachedException e) {
                    if (started[0]) throw e;
                    // The UI closed before its lock could be entered; the session lock, which
                    // is the one guarding the tree, is already held.
                    tracked.run();
                }
            });
        } catch (RuntimeException e) {
            if (started[0]) throw e;
            log.warn("Could not take the session lock to hop a write to {} onto its UI thread; running it "
                    + "on the calling thread instead, unlocked", peer.getClass().getName(), e);
            tracked.run();
        }
    }

    /**
     * The UI owning {@code peer}'s state tree, or {@code null} if it has none. Only call it
     * holding the session lock.
     */
    public static com.vaadin.flow.component.UI ownerUI(com.vaadin.flow.component.Component peer) {
        if (peer.getElement().getNode().getOwner() instanceof com.vaadin.flow.internal.StateTree tree) {
            return tree.getUI();
        }
        return null;
    }

    /** Name of the per-surrogate field {@link #sessionOf} reads; each surrogate root declares it. */
    static final String SESSION_FIELD = "hopSession";

    private static final ClassValue<java.lang.invoke.VarHandle> SESSION_HANDLE = new ClassValue<>() {
        @Override
        protected java.lang.invoke.VarHandle computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(SESSION_FIELD);
                    if (f.getType() != com.vaadin.flow.server.VaadinSession.class) continue;
                    return java.lang.invoke.MethodHandles.privateLookupIn(c, java.lang.invoke.MethodHandles.lookup())
                            .unreflectVarHandle(f);
                } catch (NoSuchFieldException e) {
                    // Not declared here; try the superclass.
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("cannot access " + c.getName() + "." + SESSION_FIELD, e);
                }
            }
            return null;
        }
    };

    /**
     * The session {@code peer} captured — at construction when one was current, else handed
     * down by its emulator ({@link #offerSession}), else at first attach — or {@code null}
     * if it has none. Reads the surrogate's private {@code hopSession} field, which is
     * {@code volatile}; a component with no such field (a bare Vaadin component) answers
     * {@code null}.
     */
    public static com.vaadin.flow.server.VaadinSession sessionOf(com.vaadin.flow.component.Component peer) {
        java.lang.invoke.VarHandle h = SESSION_HANDLE.get(peer.getClass());
        return h == null ? null : (com.vaadin.flow.server.VaadinSession) h.getVolatile(peer);
    }

    /**
     * Records {@code session} as {@code peer}'s unless it already has one: how an emulator built
     * off the UI thread, whose context knows the session, hands it to a surrogate whose
     * constructor found none current. No-op for a bare Vaadin component or a {@code null}
     * session.
     */
    public static void offerSession(com.vaadin.flow.component.Component peer, com.vaadin.flow.server.VaadinSession session) {
        java.lang.invoke.VarHandle h = SESSION_HANDLE.get(peer.getClass());
        if (h != null && session != null) h.compareAndSet(peer, null, session);
    }

    /**
     * Deliberate no-op hook for Swing API that's redundant in the Vaadin world
     * (layout invalidation, repaint, peer lifecycle, paint dispatch). Logs at
     * DEBUG only — these get hit hot, and unlike {@link #onUnimplemented} the
     * call is answered by design, not deferred work. Does <em>not</em> feed
     * {@link #warnHook}: the testapps WARN-inventory gates assert emptiness,
     * and a deliberate-noop should not count as a stub regression.
     *
     * <p>No varargs on purpose: args add no information for a call we
     * ignored by design, and omitting them keeps the hot path allocation-free.
     */
    public static void onNoop(Object self, String method) {
        if (log.isDebugEnabled()) {
            String className = self == null ? "null" : self.getClass().getSimpleName();
            log.debug("no-op {}.{}()", className, method);
        }
    }

    /**
     * ERROR-log hook for situations where emulation can't honor a well-formed
     * Swing request because the peer-element shape is wrong — e.g. setting a
     * {@link java.awt.LayoutManager} on a Container whose host element isn't a
     * plain {@code <div>}. Mirrors {@code vaadinx.EHelper.onUnsupportedPeerShape}
     * verbatim — {@code className} is used directly, not resolved from an
     * object's runtime class: callers here pass a descriptive name (e.g. the
     * offending {@code LayoutManager}'s name) rather than the offending
     * instance. Louder than {@link #onUnimplemented}'s WARN because the user
     * has stepped into genuinely broken territory; returns without throwing
     * per R_match_swing_errors.
     */
    public static void onUnsupportedPeerShape(String className, String situation) {
        String msg = "Unsupported peer shape in " + className + ": " + situation;
        log.error(msg);
        warnHook.accept(msg);
    }

    /**
     * Resolve a class to a non-empty {@link Class#getSimpleName} by walking up
     * the superclass chain past anonymous-class frames (whose simpleName is
     * {@code ""}). User code that does {@code new SJButton() { ... }} thus
     * stamps as {@code "SJButton"}, not blank.
     */
    public static String resolveSwingClassName(Class<?> klass) {
        Class<?> c = klass;
        while (c != null && c.getSimpleName().isEmpty()) c = c.getSuperclass();
        return c == null ? "?" : c.getSimpleName();
    }

    /**
     * Stamp the {@code data-swing-class} attribute on a peer / surrogate
     * element so DevTools shows which Swing class a Vaadin element is
     * standing in for. Last-write-wins: when an emulator wraps a surrogate,
     * the emulator's stamp overwrites the surrogate's — stage-2 user code
     * sees the emulator class, stage-3 user code sees the surrogate class.
     */
    public static void stampSwingClass(com.vaadin.flow.component.Component target, Class<?> klass) {
        target.getElement().setAttribute("data-swing-class", resolveSwingClassName(klass));
    }

    /**
     * Tags a window's content pane, and the window itself, so the content pane
     * fills the window body instead of rendering at intrinsic height inside it.
     *
     * <p>Both classes are defined in {@code emul/swindow.css}, which carries the
     * measurements and the reason {@code flex-basis} must stay {@code auto}.
     * Call after the pane is planted, and on every swap — passing the pane being
     * replaced as {@code old} so it gives the class back.
     *
     * @param old the content pane being replaced, or {@code null} on first plant
     */
    public static void markContentPaneSpan(com.vaadin.flow.component.Component host,
                                           com.vaadin.flow.component.Component pane,
                                           com.vaadin.flow.component.Component old) {
        if (old != null) old.getElement().getClassList().remove("emul-contentpane");
        pane.getElement().getClassList().add("emul-contentpane");
        host.getElement().getClassList().add("emul-has-contentpane");
    }

    /**
     * Tags a root pane so it becomes the flex column the menu bar and content
     * pane stack inside, and the positioned ancestor the glass pane insets
     * against. The rule is in {@code emul/swindow.css}; call once per root pane,
     * at construction.
     *
     * <p>Both layers call this on the same element — the emulator's
     * {@code JRootPane} peers on the surrogate it tags — so the class is
     * idempotent by construction.
     */
    public static void markRootPane(com.vaadin.flow.component.Component rootPane) {
        rootPane.getElement().getClassList().add("emul-rootpane");
    }

    /**
     * Tags a layered pane {@code display: contents}, so its children become
     * direct flex items of the root pane's column and the pane itself
     * contributes no geometry. Measured a layout no-op; see
     * {@code emul/swindow.css} for what breaks with a bare {@code Div} instead.
     *
     * <p>Applies to the <em>instance</em> a root pane owns, never to
     * {@code JLayeredPane} as a class — {@code JDesktopPane} extends it, and a
     * universal rule would strip every user-instantiated one of its containing
     * block.
     */
    public static void markLayeredPane(com.vaadin.flow.component.Component layeredPane) {
        layeredPane.getElement().getClassList().add("emul-layeredpane");
    }

    /**
     * Tags a menu bar to take its intrinsic height in the root pane's column,
     * leaving the rest to the content pane — the CSS spelling of
     * {@code JRootPane.RootLayout}'s {@code menuBar.setBounds(0, 0, w, mbd.height)}.
     *
     * @param old the menu bar being replaced, or {@code null} on first plant
     */
    public static void markMenuBarSlot(com.vaadin.flow.component.Component bar,
                                       com.vaadin.flow.component.Component old) {
        if (old != null) old.getElement().getClassList().remove("emul-menubar");
        if (bar != null) bar.getElement().getClassList().add("emul-menubar");
    }

    /**
     * The stored listeners of {@code type} in <em>AWT dispatch order</em> —
     * first-registered-first, which is the reverse of what
     * {@link javax.swing.event.EventListenerList} yields.
     *
     * @param <T> an <em>AWT</em> listener type. Swing listener types must not
     *        come through here: {@code AWTEventMulticaster} dispatches
     *        first-registered-first while Swing's list is last-first, and each
     *        convention is correct for its own half of the JDK (D_awt_dead_hooks).
     * @return a fresh array, reversed in place; iterate it rather than
     *         re-reading the list, so in-dispatch listener mutation stays safe
     */
    public static <T extends java.util.EventListener> T[] awtOrder(
            javax.swing.event.EventListenerList list, Class<T> type) {
        T[] a = list.getListeners(type);
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            T t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    private static String format(Object[] args) {
        if (args == null || args.length == 0) return "";
        return Arrays.stream(args).map(SHelper::formatOne).collect(Collectors.joining(","));
    }

    private static String formatOne(Object o) {
        if (o == null) return "null";
        Package p = o.getClass().getPackage();
        if (p != null && "java.lang".equals(p.getName())) return o.toString();
        // Anonymous classes have empty simpleName — walk up to the nearest
        // named ancestor so a "new SComponentListener(){...}" logs as
        // "SComponentListener", not as an empty name.
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            String name = c.getSimpleName();
            if (!name.isEmpty()) return name;
        }
        for (Class<?> i : o.getClass().getInterfaces()) {
            String name = i.getSimpleName();
            if (!name.isEmpty()) return name;
        }
        return o.getClass().getName();
    }
}
