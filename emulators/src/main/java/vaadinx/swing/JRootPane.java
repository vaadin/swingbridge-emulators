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
 * This file is derived from OpenJDK's javax.swing.JRootPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.swingbridge.surrogates.SJMenuBar;
import com.vaadin.swingbridge.surrogates.SJRootPane;
import vaadinx.EHelper;
import vaadinx.awt.Container;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import java.awt.IllegalComponentStateException;

/**
 * Emulator for {@link javax.swing.JRootPane} — a thin shell over
 * {@link SJRootPane} (SD_sjframe): the surrogate carries the Enter-
 * shortcut install and its own {@code "defaultButton"} PCE; the
 * emulator keeps its AWT-typed content / layered / glass pane holders
 * and its own listener chain so migrated code registered at the
 * {@code vaadinx.swing.JRootPane} layer hears events on its own side
 * (same two-layer convention as SJSlider / SJSpinner / SFrame).
 *
 * <h2>Structural, and the containment is the JDK's</h2>
 *
 * This root pane sits between the window and its content pane, as Swing's does,
 * and holds the JDK's own chain:
 *
 * <pre>
 * JRootPane
 * ├── glassPane                (index 0, as in the JDK)
 * └── JLayeredPane
 *     ├── JMenuBar
 *     └── contentPane
 * </pre>
 *
 * The menu bar belongs to the <em>layered</em> pane, not to this pane —
 * {@code javax.swing.JRootPane}'s class doc states it outright, and both of the
 * layered pane's children go into the same {@code FRAME_CONTENT_LAYER}, so the
 * layering is not what stacks them. {@code RootLayout} stacks them in y,
 * reaching through the layered pane to do it; SB-Emulators spells that as CSS on the
 * surrogate chain. The three panes are built in the constructor through the
 * JDK's own {@code createGlassPane} / {@code createLayeredPane} /
 * {@code createContentPane} hooks, in the JDK's order — which is what makes a
 * migrator's override of any of them actually run (R_no_vaadin_in_api limb 2).
 *
 * <p>What is <em>not</em> reproduced is the layered pane's z-ordering: layer
 * constants round-trip but reorder nothing, since Vaadin renders in DOM order
 * and {@code JInternalFrame} renders as a Dialog overlay instead (D_internal_frames).
 */
public class JRootPane extends JComponent implements Accessible {

    // AWT-typed panes. Emulator users see AWT types; pure-surrogate users see
    // the Vaadin-typed ones on SJRootPane. The typed-holder split matches the
    // SJSlider BoundedRangeModel precedent — but unlike a holder these are real
    // children of this container, so the DOM chain builds itself out of their
    // peers via Container.addImpl.
    protected Container contentPane;
    protected JLayeredPane layeredPane;
    protected vaadinx.awt.Component glassPane;
    protected JMenuBar menuBar;

    // Emulator-layer default-button reference. Browser-side shortcut
    // install is delegated to the surrogate (SJRootPane) peer —
    // SJRootPane owns the ShortcutRegistration, its lifecycle keys off
    // the button's peer, and replacing / clearing tears it down there.
    // Emulator-layer PCE for "defaultButton" fires independently of the
    // surrogate's PCE; migrated listeners register on one layer.
    protected JButton defaultButton;

    /**
     * Peers on its own {@code SJRootPane}. A window's {@code setRootPane} hands that same
     * surrogate to the window's surrogate, so both layers share one root pane and the Enter
     * shortcut {@code setDefaultButton} installs follows the window through a single listener.
     */
    public JRootPane() {
        super(SJRootPane.class, SJRootPane::new);
        // The JDK's own constructor body, hooks and order (JRootPane.java:328).
        // Each setter plants the pane as a child of this container, so the DOM
        // chain falls out of Container.addImpl nesting the peers.
        setGlassPane(createGlassPane());
        setLayeredPane(createLayeredPane());
        setContentPane(createContentPane());
        // JDK sets its own layout to a RootLayout here; ours is CSS on the
        // surrogate chain (emul/swindow.css), installed by the surrogate.
        setDoubleBuffered(true);
        withPeer(p -> surrogate().setLayerEnterClaims(JRootPane::claimsEnter));
    }

    /**
     * The Enter claim only this layer can see: a {@code JTextField}'s own
     * {@code ActionListener}s live on the emulator, not on its peer, and Swing's
     * {@code notify-field-accept} takes Enter whenever there is one. A
     * {@code JFormattedTextField} is exempt, as in the JDK — its commit binding
     * is enabled by an edit, which its peer reports itself.
     */
    private static boolean claimsEnter(com.vaadin.flow.component.Component peer) {
        // Not EHelper.getEmulator: the walk also visits peer-less components, such
        // as a spinner's inner field, where that one throws.
        return ComponentUtil.getData(peer, vaadinx.awt.Component.class)
                        instanceof JTextField field
                && !(field instanceof JFormattedTextField)
                && field.getActionListeners().length > 0;
    }

    /**
     * Factory for the default content pane, called from the constructor. Real
     * Swing returns a {@code JPanel} with a {@code BorderLayout}; the built-in
     * BorderLayout writes its CSS onto
     * {@link Container#peerContentElement()} either way, so a plain
     * Div-backed Container is equivalent here.
     */
    protected Container createContentPane() {
        return new Container();
    }

    /** Factory for the default layered pane, called from the constructor. */
    protected JLayeredPane createLayeredPane() {
        return new JLayeredPane();
    }

    /**
     * Factory for the default glass pane, called from the constructor. Invisible
     * as in the JDK, so a later {@code setVisible(true)} is what curtains the
     * window.
     */
    protected vaadinx.awt.Component createGlassPane() {
        JPanel gp = new JPanel();
        gp.setVisible(false);
        return gp;
    }

    /** Narrow the peer to its SJRootPane type. */
    private SJRootPane surrogate() {
        return (SJRootPane) getPeer();
    }

    public Container getContentPane() {
        return contentPane;
    }

    /**
     * The JDK's body: detach the outgoing pane from the layered pane, then add
     * the incoming one there. Children are not migrated — callers that want to
     * keep content must re-add to the new pane.
     */
    public void setContentPane(Container content) {
        if (content == null) {
            // JDK throws IllegalComponentStateException — match per D_never_fail_on_gaps.
            throw new IllegalComponentStateException("contentPane cannot be set to null");
        }
        if (content == this.contentPane) return;
        JLayeredPane layered = getLayeredPane();
        if (contentPane != null && contentPane.getParent() == layered) {
            layered.remove(contentPane);
        }
        this.contentPane = content;
        layered.add(content, JLayeredPane.FRAME_CONTENT_LAYER);
        // Push the reference down so the surrogate's own field points at the
        // same element rather than lazily minting a rival pane, and so it tags
        // emul-contentpane. The DOM placement the two layers compute agrees —
        // see the class javadoc on the two-layer split.
        withPeer(p -> surrogate().setContentPane(content.getPeer()));
    }

    public JLayeredPane getLayeredPane() {
        return layeredPane;
    }

    /**
     * Faithfully <em>does not</em> migrate the menu bar or content pane into the
     * new pane — the JDK's {@code setLayeredPane} swaps only its own child,
     * leaving them parented to the outgoing pane, which is why nothing but a
     * look-and-feel calls it.
     */
    public void setLayeredPane(JLayeredPane layered) {
        if (layered == null) {
            throw new IllegalComponentStateException("layeredPane cannot be set to null");
        }
        if (layered == this.layeredPane) return;
        if (layeredPane != null && layeredPane.getParent() == this) {
            remove(layeredPane);
        }
        this.layeredPane = layered;
        add(layered);
        withPeer(p -> surrogate().setLayeredPane(layered.getPeer()));
    }

    public vaadinx.awt.Component getGlassPane() {
        return glassPane;
    }

    /**
     * Plant {@code glass} as this pane's first child — the JDK's index as well
     * as its containment ({@code rootPane.add(glassPane, 0)}), so a later
     * {@code getGlassPane().setVisible(true)} renders a curtain that blocks
     * mouse input beneath it (D_glasspane_structural/SD_glasspane_structural).
     */
    public void setGlassPane(vaadinx.awt.Component glass) {
        if (glass == null) {
            throw new IllegalComponentStateException("glassPane cannot be set to null");
        }
        if (glass == this.glassPane) return;
        if (glassPane != null && glassPane.getParent() == this) {
            remove(glassPane);
        }
        this.glassPane = glass;
        add(glass, null, 0);
        withPeer(p -> surrogate().setGlassPane(glass.getPeer()));
    }

    /** {@code null} until {@link #setJMenuBar} plants one. */
    public JMenuBar getJMenuBar() {
        return menuBar;
    }

    /** JDK alias for {@link #getJMenuBar()}, deprecated there since 1.1. */
    public JMenuBar getMenuBar() {
        return menuBar;
    }

    /**
     * Plant {@code bar} in the layered pane at {@code FRAME_CONTENT_LAYER},
     * above the content pane — the JDK's own containment
     * ({@code layeredPane.add(menuBar, FRAME_CONTENT_LAYER)}). Pass
     * {@code null} to detach. Fires nothing: neither {@code JFrame} nor
     * {@code JDialog} nor {@code JRootPane} fires a bound property for the menu
     * bar (D_window_fanout); {@code JInternalFrame} is the sole {@code javax.swing} class
     * that does.
     */
    public void setJMenuBar(JMenuBar bar) {
        if (bar == this.menuBar) return;
        JLayeredPane layered = getLayeredPane();
        if (menuBar != null && menuBar.getParent() == layered) {
            layered.remove(menuBar);
        }
        this.menuBar = bar;
        if (bar != null) {
            layered.add(bar, JLayeredPane.FRAME_CONTENT_LAYER);
        }
        // Push the slot down so the surrogate tags the bar and orders it above
        // the content pane in the layered pane's DOM — DOM order is what the CSS
        // column reads, where RootLayout reads the menuBar field.
        withPeer(p -> surrogate().setJMenuBar(bar == null ? null : (SJMenuBar) bar.getPeer()));
    }

    /** JDK alias for {@link #setJMenuBar}, deprecated there since 1.1. */
    public void setMenuBar(JMenuBar bar) {
        setJMenuBar(bar);
    }

    /**
     * The JDK's body verbatim: add normally, then re-assert the glass pane at
     * index 0 if it slipped. Deliberately does <em>not</em> redirect into the
     * content pane — {@code rootPane.add(child)} adds a direct child of the root
     * pane in Swing too, which is why {@code JRootPane}'s own class doc tells
     * callers to add to {@code getContentPane()} instead. The redirect belongs to
     * {@code JFrame} / {@code JDialog} / {@code JWindow}, which is where
     * {@code rootPaneCheckingEnabled} lives; {@code JRootPane} has no such flag.
     */
    @Override
    protected void addImpl(vaadinx.awt.Component comp, Object constraints, int index) {
        super.addImpl(comp, constraints, index);
        if (glassPane != null
                && glassPane.getParent() == this
                && getComponentCount() > 0
                && getComponent(0) != glassPane) {
            add(glassPane, null, 0);
        }
    }

    public JButton getDefaultButton() {
        return defaultButton;
    }

    /**
     * Install {@code button} as the root pane's default button — Enter
     * anywhere inside the enclosing window triggers its click.
     * Browser-side shortcut install is delegated to the {@link SJRootPane}
     * peer (which owns the {@code ShortcutRegistration} and its
     * button-tied lifecycle). The emulator fires its own
     * {@code "defaultButton"} PCE on this layer — the surrogate fires
     * its own independently.
     */
    public void setDefaultButton(JButton button) {
        JButton old = this.defaultButton;
        if (old == button) return;
        this.defaultButton = button;
        // Unwrap to the Vaadin Button for the surrogate's install path.
        // Null → surrogate clears the previous shortcut (that's the
        // only bookkeeping we owe here); no local ShortcutRegistration
        // field to tear down.
        withPeer(p -> surrogate().setDefaultButton(
                button == null ? null : (Button) button.getPeer()));
        firePropertyChange("defaultButton", old, button);
    }

    public String getUIClassID() {
        return "RootPaneUI";
    }

    // JDK's JRootPane also defines NONE_DECORATED / FRAME / PLAIN_DIALOG
    // window-decoration-style constants. Migrations rarely hit them
    // outside of a custom LaF; ported here for API parity.
    public static final int NONE = 0;
    public static final int FRAME = 1;
    public static final int PLAIN_DIALOG = 2;
    public static final int INFORMATION_DIALOG = 3;
    public static final int ERROR_DIALOG = 4;
    public static final int COLOR_CHOOSER_DIALOG = 5;
    public static final int FILE_CHOOSER_DIALOG = 6;
    public static final int QUESTION_DIALOG = 7;
    public static final int WARNING_DIALOG = 8;

    public AccessibleContext getAccessibleContext() {
        EHelper.onUnimplemented("JRootPane", "getAccessibleContext");
        return null;
    }
}
