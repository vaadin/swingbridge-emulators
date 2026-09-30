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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.swingbridge.surrogates.SJPanel;
import vaadinx.swing.app.MainWindowRoute;

import javax.swing.WindowConstants;

/**
 * The {@code @MainWindow} shape: peer is a {@link SJPanel} (extends
 * Vaadin {@code Div}), attached to a
 * {@link MainWindowRoute}'s element so the frame renders inline as the
 * route's content rather than as a centered Dialog overlay. Per
 * <a href="../../../emulators/decisions.md#D_frame_strategy">D_frame_strategy</a>.
 *
 * <p><b>Bound route:</b> captured at JFrame construction time from
 * {@link MainWindowRoute#current()} (set during the route's
 * {@code bootstrap()}). The frame's whole lifetime points at that one
 * route — re-attaching to a different route later is not supported.
 *
 * <p><b>Close-op semantics:</b> every close operation resolves to a
 * {@code dispose()} or a hide, and the session ends when the dispose takes the
 * app's last displayable window with it — {@link vaadinx.AutoShutdown}, per
 * <a href="../../../emulators/decisions.md#D_auto_shutdown">D_auto_shutdown</a>.
 * Resolves
 * <a href="../../../emulators/decisions.md#D_gap_severity_triage">D_gap_severity_triage</a>
 * case 2 for the inline path; DialogStrategy keeps the throw.
 */
final class InlineStrategy implements FrameStrategy {
    static final InlineStrategy INSTANCE = new InlineStrategy();

    private InlineStrategy() { }

    @Override
    public Component createPeer() {
        return new SJPanel();
    }

    @Override
    public void applyVisibleToPeer(JFrame f, boolean b) {
        MainWindowRoute route = f.boundRoute();
        if (route == null) {
            // Defensive — JFrame's ctor enforces this for @MainWindow,
            // but covers the case where an InlineStrategy frame somehow
            // arrives here without a route bound (programming error).
            throw new IllegalStateException(
                    "@MainWindow JFrame " + f + " has no bound MainWindowRoute. "
                            + "Construct it from inside MainWindowRoute.bootstrap() "
                            + "(typically via the migrated app's mainUI() method).");
        }
        if (b) {
            // Idempotent attach — only append if not already a child of
            // the route. Vaadin's appendChild handles re-parenting if
            // the peer was attached elsewhere, but skipping the call when
            // already attached avoids spurious DOM mutations.
            if (f.peer().getElement().getParent() != route.getElement()) {
                route.getElement().appendChild(f.peer().getElement());
            }
        } else {
            f.peer().getElement().removeFromParent();
        }
    }

    @Override
    public void disposePeer(JFrame f) {
        f.peer().getElement().removeFromParent();
    }

    @Override
    public void afterTitleSet(JFrame f, String title) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().setTitle(title == null ? "" : title);
        }
    }

    @Override
    public void handleClosing(JFrame f, int defaultCloseOperation) {
        switch (defaultCloseOperation) {
            case WindowConstants.DISPOSE_ON_CLOSE -> f.dispose();
            case WindowConstants.HIDE_ON_CLOSE -> f.setVisible(false);
            case WindowConstants.DO_NOTHING_ON_CLOSE -> { /* no-op */ }
            case WindowConstants.EXIT_ON_CLOSE ->
                    // As DISPOSE_ON_CLOSE: what ends the session is taking the app's
                    // last displayable window (D_auto_shutdown), not the close op.
                    f.dispose();
            default -> { /* unreachable (setter validates), drop */ }
        }
    }

    /**
     * Inline-strategy default sizing — installs {@code width:100% / height:100%}
     * on the SJPanel peer at {@link JFrame#frameInit} time so the peer fills
     * the route (which is itself {@code setSizeFull()} from
     * {@link MainWindowRoute}'s ctor), then overrides the SJPanel's default
     * {@link java.awt.FlowLayout} CSS with a vertical flex column so the
     * menubar (intrinsic height) and the contentPane (flex-grow) stack
     * cleanly instead of getting laid out as wrapping flex-row items
     * centered both ways. The contentPane is given {@code flex:1} +
     * {@code min-height:0} so the BorderLayout grid inside has a definite
     * height to allocate to its {@code 1fr} CENTER track. Per
     * <a href="../../../emulators/decisions.md#D_inline_route_sizing">D_inline_route_sizing</a>.
     *
     * <p>{@link JFrame#setPreferredSize} re-pins 100%/100% over
     * {@link vaadinx.awt.Component#setPreferredSize}'s pixel write for
     * resizable frames (the default), so user setSize / setPreferredSize
     * calls field-shadow without shrinking the peer. The
     * {@code setResizable(false) + setPreferredSize + pack()} opt-out
     * earns pixel sizing via {@link #applyInlineSizingOnPack}.
     *
     * <p>The column-flex overrides write through to the same CSS slots
     * SJPanel's ctor-time {@link com.vaadin.swingbridge.surrogates.SJPanel#setLayout
     * setLayout(new FlowLayout())} populated. We don't call
     * {@code setLayout(null)} or similar on the SJPanel — user code that
     * reads {@code frame.getRootPane().getContentPane().getLayout()} (or
     * the surrogate's layout) sees its own contentPane's layout, not this
     * outer chrome SJPanel's. The CSS overrides stay invisible at the
     * Swing API surface.
     */
    void applyInlineSizingOnFrameInit(JFrame f) {
        com.vaadin.flow.dom.Style sjpStyle = f.peer().getElement().getStyle();
        sjpStyle.set("width", "100%");
        sjpStyle.set("height", "100%");
        // Override the FlowLayout CSS the SJPanel ctor wrote so the
        // [menubar, contentPane] DOM order renders as a top-to-bottom
        // stack (menubar at intrinsic height, contentPane absorbing the
        // remainder). FlowLayout left these as `flex-direction:row` +
        // `justify-content:center` + `align-items:center` + `flex-wrap:wrap`
        // + `gap:5px`, which centered both axes and wrapped the contentPane
        // below the menubar with vertical padding from the flex centering.
        sjpStyle.set("flex-direction", "column");
        sjpStyle.set("justify-content", "flex-start");
        sjpStyle.set("align-items", "stretch");
        sjpStyle.set("flex-wrap", "nowrap");
        sjpStyle.set("gap", "0");
        // The root pane — the SJPanel's one child — absorbs the panel's height,
        // and the menubar / contentPane split happens inside it (`.emul-rootpane`
        // is itself a flex column, with the layered pane `display: contents` so
        // the bar and the pane are its direct flex items). This used to target
        // the content pane, which the root pane now stands two levels above.
        //
        // `min-height:0` is critical: flex items default to
        // `min-height:min-content`, which would stop the chain shrinking past its
        // children's intrinsic heights — and our inner BorderLayout grid uses
        // `minmax(0, 1fr)` for the same reason, expecting a definite outer height
        // that's free to be smaller than min-content if the user sizes the window
        // down. Basis 0 rather than `.emul-rootpane`'s `auto` is safe here and is
        // what D_inline_route_sizing measured: the SJPanel parent has a definite height, so there
        // is no auto-height container for a zero basis to collapse.
        com.vaadin.flow.dom.Style rpStyle = f.getRootPane().peerContentElement().getStyle();
        rpStyle.set("flex", "1 1 0");
        rpStyle.set("min-height", "0");
        rpStyle.set("min-width", "0");
    }

    /**
     * Inline-strategy {@code pack()} response — clears the route's
     * {@code setSizeFull} so the route shrinks to wrap the SJPanel, and
     * conditionally clears the SJPanel's 100% defaults (only when no
     * {@code preferredSize} was set, since {@link vaadinx.awt.Component#setPreferredSize}
     * writes pixel values to the same CSS slot the defaults occupy). Per D_inline_route_sizing.
     *
     * <p>The contentPane's {@code flex:1} / {@code min-height:0} stays
     * untouched — in either size mode (preferred-sized SJPanel or
     * content-sized SJPanel after this method runs), the column-flex chain
     * still wants the contentPane to absorb space below the menubar, and
     * {@code flex:1} in an auto-height column container settles to
     * intrinsic-with-grow-as-available, which is the right behaviour for a
     * pack()-driven shrink-wrap window.
     */
    void applyInlineSizingOnPack(JFrame f) {
        // PWA-shape default (resizable @MainWindow JFrame): no-op. The route
        // stays setSizeFull and the SJPanel stays at 100% / 100% (pinned by
        // JFrame.setPreferredSize). Migrators' setSize / setPreferredSize /
        // pack calls field-shadow for R_swing_is_truth round-trip but don't shrink the
        // peer below the viewport — the browser is the WM, and like a
        // tiling WM it silently overrides Swing's size requests.
        //
        // setResizable(false) + setPreferredSize + pack() opt-out: a
        // @MainWindow JFrame that explicitly declares "I am not a normal
        // resizable window" earns pixel sizing. Clear the route's
        // setSizeFull so it shrinks to wrap; re-write the preferred-size
        // pixels onto the SJPanel (JFrame.setPreferredSize re-pinned
        // 100%/100% under the default branch if the user toggled
        // setResizable(false) after setPreferredSize).
        if (!f.isResizable() && f.isPreferredSizeSet()) {
            MainWindowRoute route = f.boundRoute();
            if (route != null) {
                route.getElement().getStyle().remove("width");
                route.getElement().getStyle().remove("height");
            }
            java.awt.Dimension p = f.getPreferredSize();
            com.vaadin.flow.dom.Style s = f.peer().getElement().getStyle();
            if (p.width > 0) {
                s.set("width", p.width + "px");
            }
            if (p.height > 0) {
                s.set("height", p.height + "px");
            }
        }
    }

}
