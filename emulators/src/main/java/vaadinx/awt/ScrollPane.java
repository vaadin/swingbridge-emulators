/*
 * Copyright (c) 1996, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.ScrollPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator. The structural half is a re-run of the Label/Button
// shape; the interesting parts are all divergences that earned their own
// argument, and each is commented where it happens rather than only in D_awt_scrollpane:
//   - addImpl does NOT reproduce the JDK's addToPanel lightweight wrapping
//   - setValue does not clamp, because a faithful clamp collapses to 0 forever
//   - the scroll channel is server->browser only, so user scrolls fire nothing
// Rationale: D_awt_scrollpane / SD_sscrollpane; the lane: D_awt_lane.

/**
 * Emulator for {@link java.awt.ScrollPane} — AWT's scrolling container, not
 * {@link vaadinx.swing.JScrollPane}. Holds exactly one child, gives it its
 * full size, and shows a movable window onto it. Peers over
 * {@link com.vaadin.swingbridge.surrogates.SScrollPane} (Vaadin {@code Scroller}).
 *
 * <pre>{@code
 * ScrollPane sp = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
 * sp.add(tallPanel);             // a second add() evicts the first
 * sp.setScrollPosition(0, 200);
 * Point p = sp.getScrollPosition();
 * }</pre>
 *
 * <h2>The scrollbars are not components</h2>
 *
 * Unlike Swing's {@code JScrollBar}, AWT's scrollbars are drawn by the peer
 * and never enter the component tree. What user code gets instead is a pair of
 * {@link vaadinx.awt.ScrollPaneAdjustable} value models, one per axis, via
 * {@link #getVAdjustable} / {@link #getHAdjustable}. The range is derived, not
 * set: their {@code setMinimum} / {@code setMaximum} / {@code setVisibleAmount}
 * throw {@link java.awt.AWTError}, verbatim as in AWT.
 *
 * <h2>Scrolling is server&#8594;browser only</h2>
 *
 * {@link #setScrollPosition} pushes to the browser and
 * {@link #getScrollPosition} reads back what was pushed — correct in the same
 * request, which is the common migrated idiom. But <b>a user's wheel or drag is
 * invisible to the server</b>: there is no {@code scroll} DOM listener, so
 * {@code getScrollPosition()} does not track a gesture and no
 * {@code AdjustmentListener} fires for one. Four migrated idioms break on this,
 * all silently — save/restore of a scroll offset, two panes synchronised
 * through an {@code AdjustmentListener}, "scroll to bottom only if already at
 * bottom", and hand-rolled paging near {@code getMaximum()}. Nothing
 * <i>renders</i> wrong: the browser owns overflow, and a Vaadin Grid or
 * VirtualList inside the pane lazy-loads over its own channel. Deliberate per
 * D_awt_scrollpane / SD_sscrollpane, which record why the read direction is not built.
 *
 * <h2>Geometry answers are the JDK's own un-realized answers</h2>
 *
 * {@link #getViewportSize} is always 100&times;100 and both scrollbar
 * dimensions are always 0 — which is literally what a never-laid-out JDK
 * {@code ScrollPane} reports (its ctor seeds {@code width = height = 100}, and
 * the scrollbar getters return 0 while {@code peer == null}). A rare case where
 * dummy geometry and JDK geometry agree exactly, so none of the three WARNs.
 *
 * <h2>Leaf peer is locked down per R_leaf_peer_lockdown</h2>
 *
 * Nothing in {@code java.awt} extends {@code ScrollPane}, and
 * {@code javax.swing.plaf} is out of scope (R_match_swing_errors sub-bucket (b)). Both public
 * ctors funnel into one {@code super(SScrollPane.class, …)}, so every instance —
 * including a user-code subclass — peers over a {@code Scroller} and no ctor
 * offers a peer seam.
 *
 * <p>The policy constants are 0/1/2, numerically identical to
 * {@link java.awt.Adjustable}'s {@code HORIZONTAL}/{@code VERTICAL}/
 * {@code NO_ORIENTATION}. So {@code new ScrollPane(Adjustable.VERTICAL)}
 * compiles and silently means {@link #SCROLLBARS_ALWAYS} — no throw,
 * wrong pane. The safe direction is
 * {@code javax.swing.ScrollPaneConstants}, whose values are 20–22 /
 * 30–32 and therefore disjoint: one of those handed to this ctor
 * throws {@link IllegalArgumentException}, loudly.
 */
public class ScrollPane extends vaadinx.awt.Container implements javax.accessibility.Accessible {

    public static final int SCROLLBARS_AS_NEEDED = 0;
    public static final int SCROLLBARS_ALWAYS = 1;
    public static final int SCROLLBARS_NEVER = 2;

    /** Ctor-only in the JDK — there is no setter — so final here. */
    private final int scrollbarDisplayPolicy;

    private final vaadinx.awt.ScrollPaneAdjustable hAdjustable;
    private final vaadinx.awt.ScrollPaneAdjustable vAdjustable;

    /** JDK default is true. */
    private boolean wheelScrollingEnabled = true;

    /** Chains to {@code this(SCROLLBARS_AS_NEEDED)}, as the JDK does. */
    public ScrollPane() {
        this(SCROLLBARS_AS_NEEDED);
    }

    /**
     * @param scrollbarDisplayPolicy {@link #SCROLLBARS_AS_NEEDED},
     *        {@link #SCROLLBARS_ALWAYS} or {@link #SCROLLBARS_NEVER}
     * @throws IllegalArgumentException on any other value, with AWT's message.
     *         Thrown while evaluating {@code super(...)}'s argument, so it leaves
     *         no half-built ScrollPane
     */
    public ScrollPane(int scrollbarDisplayPolicy) {
        super(com.vaadin.swingbridge.surrogates.SScrollPane.class, peerFactory(scrollbarDisplayPolicy));
        this.scrollbarDisplayPolicy = scrollbarDisplayPolicy;
        // Both constructed eagerly, as the JDK does. The JDK also passes its
        // internal PeerFixer as their listener; ours have an empty chain and
        // push through the pane instead.
        this.hAdjustable = new vaadinx.awt.ScrollPaneAdjustable(this, java.awt.Adjustable.HORIZONTAL);
        this.vAdjustable = new vaadinx.awt.ScrollPaneAdjustable(this, java.awt.Adjustable.VERTICAL);
    }

    /**
     * Validates the policy now, since the peer — whose ctor validates it too — is built only
     * once a UI is current.
     */
    private static java.util.function.Supplier<com.vaadin.swingbridge.surrogates.SScrollPane> peerFactory(
            int scrollbarDisplayPolicy) {
        switch (scrollbarDisplayPolicy) {
            case SCROLLBARS_AS_NEEDED, SCROLLBARS_ALWAYS, SCROLLBARS_NEVER -> { }
            default -> throw new IllegalArgumentException("illegal scrollbar display policy");
        }
        return () -> new com.vaadin.swingbridge.surrogates.SScrollPane(scrollbarDisplayPolicy);
    }

    private com.vaadin.swingbridge.surrogates.SScrollPane surrogate() {
        return (com.vaadin.swingbridge.surrogates.SScrollPane) getPeer();
    }

    /** Writes both axes' current values through to the peer in one round-trip. */
    void pushScrollPosition() {
        int h = hAdjustable.getValue();
        int v = vAdjustable.getValue();
        withPeer(p -> surrogate().scrollTo(h, v));
    }

    /**
     * Adds the single child, evicting any previous one.
     *
     * <p>The body order is AWT's and is observable: the existing child is
     * removed <i>before</i> the index check, so {@code add(c2, null, 1)} on a
     * populated pane leaves the pane <b>empty</b> and then throws. A wart, but
     * free to reproduce.
     *
     * <p>Two departures from {@code java.awt.ScrollPane.addImpl}.
     * <ul>
     * <li><b>No {@code addToPanel} branch.</b> The JDK wraps a
     * <i>lightweight</i> child in a {@code Panel} with a
     * {@code BorderLayout}, so {@code getComponent(0)} there is the
     * wrapper, not what you added. That fork exists to give a native
     * scrolled-window a heavyweight child to host; our peer's shadow
     * root is a bare {@code <slot>} that accepts any element, and SB-Emulators
     * has no lightweight/heavyweight split to test in the first place.
     * So {@code getComponent(0)} here <i>is</i> the child — more
     * permissive than the JDK, where the corresponding cast throws
     * {@code ClassCastException}. See D_awt_scrollpane.
     * <li>{@code addSlotChild} rather than {@code super.addImpl},
     * because {@code Scroller} owns its content slot and
     * {@code Container.addImpl} would {@code insertChild} into the
     * peer element and fight {@code setContent} (the D_shadow_children_tree_shape rationale,
     * transferred verbatim). {@code addSlotChild} still runs the
     * add-to-self and ancestor-cycle checks and still fires
     * {@code COMPONENT_ADDED} + {@code HIERARCHY_CHANGED}.
     * </ul>
     * {@code Container.addImpl}'s own
     * {@code IllegalArgumentException("illegal component position")}
     * bounds check is skipped: ScrollPane's {@code index > 0} check
     * with its own message replaces it.
     */
    @Override
    protected final void addImpl(vaadinx.awt.Component comp, Object constraints, int index) {
        if (getComponentCount() > 0) {
            remove(0);
        }
        if (index > 0) {
            throw new IllegalArgumentException("position greater than 0");
        }
        addSlotChild(comp, index);
        withPeer(p -> surrogate().setContent(comp.getPeer()));
        // Swapping the content resets the browser's scrollTop to 0, so restore
        // the offset the adjustables still hold. The JDK's native peer
        // preserves it across a child swap — addNotify's bug-4124460 save and
        // restore dance exists precisely to protect this value. Skipped at the
        // origin, which is the common first-add case: the browser is already
        // there, so the round-trip would buy nothing.
        if (hAdjustable.getValue() != 0 || vAdjustable.getValue() != 0) {
            pushScrollPosition();
        }
    }

    /**
     * Removes the child at {@code index}, clearing the peer's content slot.
     *
     * <p>Overridden where the JDK does not override it (there, the
     * native peer handles removal). Necessary because
     * {@code Container.remove(int)} calls
     * {@code removed.peer.getElement().removeFromParent()}, which
     * would rip the element out while {@code Scroller.getContent()}
     * still pointed at it. This is also how {@code addImpl}'s own
     * {@code remove(0)} reaches the right code.
     *
     * @throws ArrayIndexOutOfBoundsException if out of range, as in AWT
     */
    @Override
    public void remove(int index) {
        if (index < 0 || index >= getComponentCount()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        vaadinx.awt.Component removed = getComponent(index);
        removeSlotChild(removed);
        withPeer(p -> surrogate().setContent(null));
    }

    /** @return the ctor-set policy; AWT provides no setter */
    public int getScrollbarDisplayPolicy() {
        return scrollbarDisplayPolicy;
    }

    /**
     * @return always 100&times;100 — see the class javadoc. Computed directly
     *         rather than through {@code getWidth()}/{@code getInsets()},
     *         which would WARN and answer 0; the JDK's ctor seeds
     *         {@code width = height = 100} and its insets here are zero, so
     *         this constant <i>is</i> the faithful answer for a pane that was
     *         never laid out. Fresh instance — AWT lets callers mutate it
     */
    public java.awt.Dimension getViewportSize() {
        return new java.awt.Dimension(100, 100);
    }

    /**
     * @return always 0 — the JDK's own answer whenever {@code peer == null} or
     *         the policy is {@link #SCROLLBARS_NEVER}. Our scrollbars are the
     *         browser's and report no dimensions
     */
    public int getHScrollbarHeight() {
        return 0;
    }

    /** @return always 0, for the same reason as {@link #getHScrollbarHeight} */
    public int getVScrollbarWidth() {
        return 0;
    }

    /**
     * @return the vertical axis's value model. Declared {@link java.awt.Adjustable}
     *         rather than {@link vaadinx.awt.ScrollPaneAdjustable} because the JDK
     *         declares the wider type "to maintain backward compatibility";
     *         narrowing it would diverge from the signature migrated code compiled
     *         against. Cast to {@code ScrollPaneAdjustable} works, as in AWT
     */
    public java.awt.Adjustable getVAdjustable() {
        return vAdjustable;
    }

    /** @return the horizontal axis's value model; see {@link #getVAdjustable} */
    public java.awt.Adjustable getHAdjustable() {
        return hAdjustable;
    }

    /**
     * Scrolls to {@code (x, y)} within the child.
     *
     * <p>Exactly {@code hAdjustable.setValue(x); vAdjustable.setValue(y)}, as
     * in AWT, so it fires up to two {@link java.awt.event.AdjustmentEvent}s.
     * Unlike AWT the values are not clamped to the child's extent — see
     * {@link vaadinx.awt.ScrollPaneAdjustable} for why, and note the browser
     * clamps on arrival so the rendered result is still legal.
     *
     * @throws NullPointerException if the pane has no child. AWT chose NPE with
     *         this message rather than IllegalStateException
     */
    public void setScrollPosition(int x, int y) {
        if (getComponentCount() == 0) {
            throw new NullPointerException("child is null");
        }
        hAdjustable.setValue(x);
        vAdjustable.setValue(y);
    }

    /**
     * @param p the target position
     * @throws NullPointerException if {@code p} is null — thrown by the
     *         {@code p.x} dereference <i>before</i> the child check, so a null
     *         Point NPEs (without a message) even on an empty pane. Order
     *         matters and is AWT's
     */
    public void setScrollPosition(java.awt.Point p) {
        setScrollPosition(p.x, p.y);
    }

    /**
     * @return the last position pushed, not necessarily where the user is —
     *         see the class javadoc. Fresh instance
     * @throws NullPointerException if the pane has no child, as in AWT
     */
    public java.awt.Point getScrollPosition() {
        if (getComponentCount() == 0) {
            throw new NullPointerException("child is null");
        }
        return new java.awt.Point(hAdjustable.getValue(), vAdjustable.getValue());
    }

    /**
     * Refuses to install a layout manager, as {@code java.awt.ScrollPane} does.
     * {@code getLayout()} therefore answers null forever.
     *
     * @param mgr ignored
     * @throws java.awt.AWTError always, even for {@code null}
     */
    @Override
    public final void setLayout(vaadinx.awt.LayoutManager mgr) {
        throw new java.awt.AWTError("ScrollPane controls layout");
    }

    /**
     * Delegates to {@link #layout()} exactly as the JDK's does. Our
     * {@code Container.doLayout()} dispatches to the LayoutManager and
     * only falls through to {@code layout()} when there is none —
     * which happens to always be true here, since {@link #setLayout}
     * throws. Overriding makes the {@code layout()} hook live by
     * construction rather than by that coincidence (R_no_vaadin_in_api limb 2).
     */
    @Override
    public void doLayout() {
        layout();
    }

    /**
     * @deprecated As of JDK 1.1, replaced by {@link #doLayout()}. Retained
     *             because {@code doLayout()} calls it and user code overrides
     *             it — AWT-era code hooks layout here.
     */
    @Deprecated
    @Override
    public void layout() {
        // JDK reshapes the child to a negative offset and then calls setSpan on
        // both adjustables — that is how AWT scrolls, by moving the child. Our
        // peer scrolls itself and we compute no pixel span (R_layouts_close_enough), so there is
        // nothing to do. Early-return-on-empty is kept because the JDK's does
        // it and the shape is what a subclass chaining to super expects.
        if (getComponentCount() == 0) {
            return;
        }
    }

    /**
     * User-authored {@code Graphics} paint of components is
     * permanently out of scope (R_match_swing_errors sub-bucket (b))
     *
     * @param g ignored
     */
    public void printComponents(java.awt.Graphics g) {
        vaadinx.EHelper.onUnimplemented("ScrollPane", "printComponents", g);
    }

    @Override
    public void addNotify() {
        // JDK allocates the native peer here, and brackets it with the bug-4124460
        // save/restore of both adjustable values (the native peer resets them).
        // Ours is eternal once built, so there is neither a peer to allocate
        // here nor a reset to defend against — but the override is kept
        // rather than dropped: overriding addNotify() and chaining to super is a
        // common AWT-era idiom, and Container's implementation is what runs
        // doLayout() on attach.
        super.addNotify();
    }

    /**
     * The hook is reachable — {@code Component.processEvent} routes
     * {@code MouseWheelEvent} here — but nothing currently
     * <i>dispatches</i> one, so in practice it does not run. That is
     * an absent source rather than a dead hook, so R_no_vaadin_in_api limb 2 is
     * satisfied. No {@code ScrollPaneWheelScroller} arithmetic: that
     * class is JDK-internal and the browser already wheel-scrolls the
     * peer natively, which is what the flag asks for.
     * <p>{@code e.consume()} on the enabled path is the JDK's, kept so
     * the call graph matches; per D_event_consume it marks the event and suppresses
     * nothing, the browser having already scrolled
     *
     * @param e the wheel event
     */
    @Override
    protected void processMouseWheelEvent(vaadinx.awt.event.MouseWheelEvent e) {
        if (isWheelScrollingEnabled()) {
            e.consume();
        }
        super.processMouseWheelEvent(e);
    }

    /**
     * @param handleWheel {@code false} is R_match_swing_errors sub-bucket (c) drop-and-WARN: CSS
     *        cannot disable the wheel while keeping scrollbars draggable
     *        ({@code overflow: hidden} and {@code ScrollDirection.NONE} both
     *        kill scrolling outright). The value round-trips regardless;
     *        {@code true}, the default, is free because the browser
     *        wheel-scrolls the peer natively
     */
    public void setWheelScrollingEnabled(boolean handleWheel) {
        if (!handleWheel) {
            vaadinx.EHelper.onUnimplemented("ScrollPane", "setWheelScrollingEnabled", handleWheel);
        }
        wheelScrollingEnabled = handleWheel;
    }

    public boolean isWheelScrollingEnabled() {
        return wheelScrollingEnabled;
    }

    /** Verbatim JDK format, including the empty-pane (0,0) fallback. */
    @Override
    public String paramString() {
        String sdpStr;
        switch (scrollbarDisplayPolicy) {
            case SCROLLBARS_AS_NEEDED -> sdpStr = "as-needed";
            case SCROLLBARS_ALWAYS -> sdpStr = "always";
            case SCROLLBARS_NEVER -> sdpStr = "never";
            default -> sdpStr = "invalid display policy";
        }
        java.awt.Point p = (getComponentCount() > 0) ? getScrollPosition() : new java.awt.Point(0, 0);
        java.awt.Insets i = getInsets();
        return super.paramString() + ",ScrollPosition=(" + p.x + "," + p.y + ")"
                + ",Insets=(" + i.top + "," + i.left + "," + i.bottom + "," + i.right + ")"
                + ",ScrollbarDisplayPolicy=" + sdpStr
                + ",wheelScrollingEnabled=" + isWheelScrollingEnabled();
    }

    /**
     * @return always null — accessibility contexts are permanently out of scope
     *         (R_match_swing_errors sub-bucket (b)), as on {@link vaadinx.awt.Panel} /
     *         {@link vaadinx.awt.Button}
     */
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("ScrollPane", "getAccessibleContext");
        return null;
    }
}
