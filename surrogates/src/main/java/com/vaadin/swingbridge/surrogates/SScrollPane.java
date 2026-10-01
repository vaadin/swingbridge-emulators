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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.swingbridge.surrogates.awt.ContainerMixin;

import java.awt.AWTError;
import java.awt.LayoutManager;

/**
 * Surrogate for {@link java.awt.ScrollPane} — AWT's scrolling container, not
 * {@link javax.swing.JScrollPane}. Extends Vaadin {@link Scroller} directly
 * (is-a) and picks up the AWT {@code Container} API from
 * {@link ContainerMixin}. Rationale: SD_sscrollpane / D_awt_scrollpane.
 *
 * <pre>{@code
 * SScrollPane pane = new SScrollPane(java.awt.ScrollPane.SCROLLBARS_AS_NEEDED);
 * pane.setContent(tallStack);
 * pane.scrollTo(0, 200);          // pushed to the browser; nothing reads back
 * }</pre>
 *
 * <h2>Why Scroller, and why not {@link SJScrollPane}</h2>
 *
 * {@code Scroller}'s single content slot <em>is</em> AWT's single-child rule,
 * and its {@code ScrollDirection} covers the scrollbar display policy at the
 * fidelity D_scrollbar_policy_mapping already accepted. Reusing {@code SJScrollPane} was rejected
 * for SD_sbutton/SD_slabel's reason: it implements {@code JComponentMixin} and so drags
 * in border CSS, client properties, the opaque flag, tooltips and the L&amp;F
 * tail — none of which {@code java.awt.ScrollPane} has, being a
 * {@code Container} rather than a {@code JComponent}.
 *
 * <h2>This class holds no state, deliberately</h2>
 *
 * The scroll direction lives on the {@code Scroller} (Vaadin-first, no shadow)
 * and the JDK-shaped display-policy int lives on the emulator, exactly as
 * D_scrollbar_policy_mapping puts the Swing policy ints on {@code JScrollPane} and not on
 * {@code SJScrollPane}. The scroll offset would have been this class's one
 * legitimate holding — a browser fact, the {@code FocusTracker} / SD_focus_tracker shape —
 * but with the browser&#8594;server channel descoped it is no longer a browser
 * fact, merely the last value the emulator pushed, and the emulator's two
 * {@code ScrollPaneAdjustable}s already own that. Storing it here as well
 * would be the shadow-cache anti-pattern R_vaadin_first exists to forbid.
 *
 * <h2>Scrolling is server&#8594;browser only</h2>
 *
 * {@link #scrollTo} pushes; nothing reads back. There is no {@code scroll} DOM
 * listener on this element, so a user's wheel or drag is invisible to the
 * server: {@code getScrollPosition()} on the emulator answers what was last
 * pushed, and no {@code AdjustmentListener} fires for a gesture. Deliberate
 * per SD_sscrollpane, which also records the probe finding that {@code scroll}
 * neither bubbles nor composes (so a delegated UI-wide listener is not an option).
 *
 * <h2>Auto-scroll guard</h2>
 *
 * {@link #setContent(Component)} forces {@link ScrollDirection#NONE} for
 * content that owns its own overflow layer, exactly as {@link SJScrollPane}
 * does. The two copies are deliberate duplication rather than a shared util:
 * lifting ~15 lines would mean touching a Swing-lane class for an AWT-lane
 * slice. <b>If you change one, change the other</b> —
 * {@link SJScrollPane#setContent}.
 */
public class SScrollPane extends Scroller implements ContainerMixin {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    /**
     * @param scrollbarDisplayPolicy one of {@code java.awt.ScrollPane}'s
     *        {@code SCROLLBARS_*} constants
     * @throws IllegalArgumentException on any other value, with AWT's own message
     */
    public SScrollPane(int scrollbarDisplayPolicy) {
        super();
        _installSwingClass();
        setScrollDirection(toScrollDirection(scrollbarDisplayPolicy));
    }

    /**
     * Maps AWT's one-policy-for-both-axes int onto Vaadin's direction enum.
     *
     * <p>A three-arm switch, not D_scrollbar_policy_mapping's 3&times;3 cross-product, because AWT
     * has a single policy covering both axes.
     * {@code SCROLLBARS_ALWAYS} WARNs: Vaadin's {@code Scroller} has no
     * force-show affordance, so it renders as {@code AS_NEEDED}. Same web
     * component and same gap as D_scrollbar_policy_mapping, so the same R_match_swing_errors sub-bucket (a)
     * blocked-upstream classification applies.
     */
    private ScrollDirection toScrollDirection(int policy) {
        switch (policy) {
            case java.awt.ScrollPane.SCROLLBARS_AS_NEEDED:
                return ScrollDirection.BOTH;
            case java.awt.ScrollPane.SCROLLBARS_NEVER:
                return ScrollDirection.NONE;
            case java.awt.ScrollPane.SCROLLBARS_ALWAYS:
                SHelper.onUnimplemented(this, "SCROLLBARS_ALWAYS", policy);
                return ScrollDirection.BOTH;
            default:
                // AWT's own message, verbatim.
                throw new IllegalArgumentException("illegal scrollbar display policy");
        }
    }

    /**
     * Scrolls the peer element to {@code (x, y)}, in pixels.
     *
     * <p>{@code Scroller} exposes only {@code scrollToTop()} /
     * {@code scrollToBottom()}, so this goes through {@code executeJs} — the
     * SD_caret_selection shape (server&#8594;browser only, server value is the source of
     * truth). Verified 2026-08-25 that {@code vaadin-scroller}'s shadow root
     * is a bare {@code <slot>} with {@code overflow: auto} on the host, so
     * {@code this.scrollTop} addresses the real scrolling box and needs no
     * {@code shadowRoot} selector.
     *
     * <p>Fire-and-forget: an out-of-range value is clamped by the browser on
     * arrival (verified synchronous), so the rendered result is always legal
     * even when the caller's number is optimistic. Nothing reports the clamped
     * value back.
     */
    public void scrollTo(int x, int y) {
        getElement().executeJs("this.scrollTop=$0;this.scrollLeft=$1", y, x);
    }

    /**
     * Set the scrolled content, applying the auto-scroll guard.
     *
     * <p>Delegates to {@link Scroller#setContent(Component)}, then if the
     * content is an instance of any {@linkplain #isAutoScrollingType known
     * auto-scrolling Vaadin component type}, forces this Scroller's direction
     * to {@link ScrollDirection#NONE} and stretches the content to fill the
     * viewport. Prevents the stacked-scrollbar pathology when a Grid /
     * TextArea / Scroller is wrapped.
     *
     * <p>{@code null} content clears the slot without applying the guard.
     *
     * <p>Note this does <em>not</em> re-push the scroll offset, though
     * replacing content resets the browser's {@code scrollTop} to 0: this
     * class does not know the offset. The emulator's {@code addImpl} re-pushes
     * from its adjustables instead.
     */
    @Override
    public void setContent(Component content) {
        super.setContent(content);
        if (content != null && isAutoScrollingType(content)) {
            super.setScrollDirection(ScrollDirection.NONE);
            content.getElement().getStyle().set("width", "100%");
            content.getElement().getStyle().set("height", "100%");
        }
    }

    /**
     * Returns {@code true} if {@code content} is a Vaadin component type whose
     * shadow DOM owns its own overflow:auto layer — wrapping it in a Scroller
     * produces stacked scrollbars or zero-height collapse.
     *
     * <p>Deliberate duplicate of {@link SJScrollPane}'s private twin; keep the
     * two lists in step.
     */
    private static boolean isAutoScrollingType(Component content) {
        return content instanceof Grid
                || content instanceof TextArea
                || content instanceof Scroller;
    }

    /**
     * A ScrollPane's layout is nailed to null forever, so this always answers
     * null — matching the JDK field that {@link #setLayout} refuses to write.
     *
     * <p>Nothing can ever reach {@code LayoutStore}, so the
     * {@link ContainerMixin} default would answer null too; stated directly so
     * the contract does not depend on {@code setLayout}'s throw holding.
     */
    @Override
    public LayoutManager getLayout() {
        return null;
    }

    /**
     * Refuses to install a layout manager, as {@code java.awt.ScrollPane} does.
     *
     * <p>The {@link ContainerMixin} default would store into
     * {@code LayoutStore} and write layout CSS — right for a panel, wrong
     * here — so it is overridden away. A stage-3 pure-surrogate user reaching
     * for {@code setLayout} deserves the same answer as a stage-2 one.
     *
     * @throws AWTError always, with AWT's own message, even for {@code null}
     */
    @Override
    public void setLayout(LayoutManager mgr) {
        throw new AWTError("ScrollPane controls layout");
    }
}
