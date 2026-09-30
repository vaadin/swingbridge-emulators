/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JColorChooser
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JColorChooser (D_jcolorchooser), rendered by
// com.vaadin.swingbridge.surrogates.SJColorChooser, the browser <input type="color">.
// The chooser owns its state as the JDK's does: the selection model, the chooser
// panels, the preview panel and dragEnabled are this class's fields, and no getter
// reads the peer (D_emulator_owned_state). The surrogate keeps a
// private model that only this class writes, through a flush of the current color;
// a browser pick comes back as the model write a desktop chooser panel makes. There
// is no ChangeListener on the chooser itself — the JDK has none; listeners go on
// getSelectionModel(). The emulator also hosts the blocking statics (showDialog /
// createDialog), which can't live on the surrogate, which never parks (SD_sframe):
// they build an internal modal JDialog and park the calling UI fiber on the JDialog
// modal-park machinery, exactly like JFileChooser (D_file_dialogs).
//
// R_leaf_peer_lockdown leaf lock-down: javax.swing.JColorChooser is a leaf in the public
// Swing hierarchy (nothing public extends it). Peer is hard-coded to
// SJColorChooser; the (SJColorChooser peer) ctor is private + typed-narrow,
// so user-code subclasses can't reach it to swap the peer. Mirrors the
// JProgressBar / JSlider shape almost exactly.

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.swingbridge.surrogates.SJColorChooser;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Emulator for {@link javax.swing.JColorChooser}. R_leaf_peer_lockdown-locked peer is {@link SJColorChooser}. */
public class JColorChooser extends vaadinx.swing.JComponent implements javax.accessibility.Accessible {

    public static final String SELECTION_MODEL_PROPERTY = "selectionModel";
    public static final String PREVIEW_PANEL_PROPERTY = "previewPanel";
    public static final String CHOOSER_PANELS_PROPERTY = "chooserPanels";

    // The JDK's fields, under its names. chooserPanels is seeded with the default five in
    // the constructor, as BasicColorChooserUI.installUI does and every desktop L&F inherits.
    private javax.swing.colorchooser.ColorSelectionModel selectionModel;
    private vaadinx.swing.JComponent previewPanel = null;
    private javax.swing.colorchooser.AbstractColorChooserPanel[] chooserPanels =
            new javax.swing.colorchooser.AbstractColorChooserPanel[0];
    private boolean dragEnabled;

    // Set while this class pushes a color into the peer, whose value-change listener
    // would otherwise hear its own write as a pick (R_swing_is_truth).
    private boolean preventPeerEvents;

    // The L&F's model listener (BasicColorChooserUI's previewListener, which repaints):
    // here it flushes the color to the peer. Moves with the model in setSelectionModel.
    private final javax.swing.event.ChangeListener modelListener = e -> flushColor();

    public JColorChooser() {
        this(Color.white);
    }

    public JColorChooser(Color initialColor) {
        this(new javax.swing.colorchooser.DefaultColorSelectionModel(initialColor));
    }

    /** @throws NullPointerException if {@code model} is null, as the JDK's L&amp;F install does */
    public JColorChooser(javax.swing.colorchooser.ColorSelectionModel model) {
        // Peer lock-down per R_leaf_peer_lockdown: javax.swing.JColorChooser is a leaf, and
        // the surrogate keeps a private model of its own, which only this class writes.
        super(new SJColorChooser());
        selectionModel = model;
        chooserPanels = javax.swing.colorchooser.ColorChooserComponentFactory.getDefaultChooserPanels();
        model.addChangeListener(modelListener);
        flushColor();
        dragEnabled = false;
        // A browser pick runs the model write a desktop chooser panel makes
        // (AbstractColorChooserPanel: getColorSelectionModel().setSelectedColor), inside a
        // UI fiber, so a model listener may open a modal dialog (R_callswing_envelope).
        chooser().addValueChangeListener(e -> {
            if (preventPeerEvents) return;
            Color picked = chooser().getColor();
            vaadinx.EHelper.callSwing(() -> getSelectionModel().setSelectedColor(picked));
        });
    }

    /** Narrow the peer to its SJColorChooser type. Peer is always an SJColorChooser. */
    private SJColorChooser chooser() {
        return (SJColorChooser) getPeer();
    }

    /** Rule 5's flush: the model's current color, read inside the push. */
    private void flushColor() {
        withPeer(p -> {
            Color color = selectionModel.getSelectedColor();
            if (color == null) return;
            preventPeerEvents = true;
            try {
                chooser().setColor(color);
            } finally {
                preventPeerEvents = false;
            }
        });
    }

    // ---- Color API (the model's) ----

    public Color getColor() {
        return selectionModel.getSelectedColor();
    }

    public void setColor(Color color) {
        selectionModel.setSelectedColor(color);
    }

    public void setColor(int r, int g, int b) {
        setColor(new Color(r, g, b));
    }

    public void setColor(int c) {
        setColor((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    // ---- SelectionModel ----

    public javax.swing.colorchooser.ColorSelectionModel getSelectionModel() {
        return selectionModel;
    }

    /**
     * @throws NullPointerException if {@code newModel} is null — after storing it and before
     *         the event, as the JDK's L&amp;F listener does when it moves its model listener
     */
    public void setSelectionModel(javax.swing.colorchooser.ColorSelectionModel newModel) {
        javax.swing.colorchooser.ColorSelectionModel oldModel = selectionModel;
        selectionModel = newModel;
        // BasicColorChooserUI's property listener, which is registered first and so runs
        // before any of the migrator's.
        oldModel.removeChangeListener(modelListener);
        newModel.addChangeListener(modelListener);
        flushColor();
        firePropertyChange(SELECTION_MODEL_PROPERTY, oldModel, newModel);
    }

    // ---- dragEnabled ----

    public boolean getDragEnabled() {
        return dragEnabled;
    }

    /**
     * Stored, and drives nothing: the native color input has no drag-out. The JDK's
     * {@code HeadlessException} is not thrown, since a browser is not headless.
     */
    public void setDragEnabled(boolean b) {
        dragEnabled = b;
    }

    // ---- Preview panel / chooser panels (state and events kept, rendering declined) ----

    /**
     * {@code null} until one is set: the JDK's L&amp;F installs a {@code DefaultPreviewPanel},
     * a {@code javax.swing} component this type cannot hold.
     */
    public vaadinx.swing.JComponent getPreviewPanel() {
        return previewPanel;
    }

    /**
     * Stores the panel and fires {@code previewPanel}; the panel itself is
     * never rendered (the native colour input has no preview slot).
     *
     * <p>Typed {@code vaadinx.swing.JComponent} per R_no_vaadin_in_api limb 1 — with
     * the JDK's type a migrated {@code JPanel} could not be passed at
     * all. R_decline_effect_only owes the state and the event, and declines
     * only the effect.
     */
    public void setPreviewPanel(vaadinx.swing.JComponent preview) {
        if (previewPanel != preview) {
            vaadinx.swing.JComponent oldPreview = previewPanel;
            previewPanel = preview;
            vaadinx.EHelper.onUnimplemented("JColorChooser", "setPreviewPanel(render)", preview);
            firePropertyChange(JColorChooser.PREVIEW_PANEL_PROPERTY, oldPreview, preview);
        }
    }

    public void addChooserPanel(javax.swing.colorchooser.AbstractColorChooserPanel panel) {
        javax.swing.colorchooser.AbstractColorChooserPanel[] oldPanels = getChooserPanels();
        javax.swing.colorchooser.AbstractColorChooserPanel[] newPanels =
                new javax.swing.colorchooser.AbstractColorChooserPanel[oldPanels.length + 1];
        System.arraycopy(oldPanels, 0, newPanels, 0, oldPanels.length);
        newPanels[newPanels.length - 1] = panel;
        setChooserPanels(newPanels);
    }

    /** @throws IllegalArgumentException if {@code panel} is not one of this chooser's */
    public javax.swing.colorchooser.AbstractColorChooserPanel removeChooserPanel(
            javax.swing.colorchooser.AbstractColorChooserPanel panel) {
        int containedAt = -1;
        for (int i = 0; i < chooserPanels.length; i++) {
            if (chooserPanels[i] == panel) {
                containedAt = i;
                break;
            }
        }
        if (containedAt == -1) {
            throw new IllegalArgumentException("chooser panel not in this chooser");
        }
        javax.swing.colorchooser.AbstractColorChooserPanel[] newArray =
                new javax.swing.colorchooser.AbstractColorChooserPanel[chooserPanels.length - 1];
        System.arraycopy(chooserPanels, 0, newArray, 0, containedAt);
        System.arraycopy(chooserPanels, containedAt + 1, newArray, containedAt, chooserPanels.length - containedAt - 1);
        setChooserPanels(newArray);
        return panel;
    }

    /**
     * Stores a copy and fires {@code chooserPanels}; the panels are never rendered, since the
     * native color input is the chooser UI. The panels' {@code installChooserPanel} hook is not
     * called: it takes a {@code javax.swing.JColorChooser}, which this is not.
     *
     * @throws NullPointerException if {@code panels} or one of its elements is null — an element
     *         after storing the array and before the event, as the JDK's L&amp;F listener does
     */
    public void setChooserPanels(javax.swing.colorchooser.AbstractColorChooserPanel[] panels) {
        javax.swing.colorchooser.AbstractColorChooserPanel[] oldValue = chooserPanels;
        chooserPanels = java.util.Arrays.copyOf(panels, panels.length);
        for (javax.swing.colorchooser.AbstractColorChooserPanel panel : panels) {
            // BasicColorChooserUI reads each new panel's display name for its tab.
            panel.getDisplayName();
        }
        vaadinx.EHelper.onUnimplemented("JColorChooser", "setChooserPanels(render)", (Object) panels);
        firePropertyChange(CHOOSER_PANELS_PROPERTY, oldValue, panels);
    }

    public javax.swing.colorchooser.AbstractColorChooserPanel[] getChooserPanels() {
        return java.util.Arrays.copyOf(chooserPanels, chooserPanels.length);
    }

    // ===========================================================
    // Blocking statics — build an internal modal JDialog hosting a fresh
    // JColorChooser, call dialog.setVisible(true) (parks the calling VT on
    // the JDialog modal-park machinery, D_file_dialogs), and translate the user's
    // choice to the JDK return shape. Caller must be in an EHelper.callSwing
    // UI fiber (R_callswing_envelope); otherwise the modal park throws
    // IllegalStateException via UIFibers.checkInUIFiber().
    // ===========================================================

    public static Color showDialog(vaadinx.awt.Component parent, String title, Color initialColor) {
        return showDialog(parent, title, initialColor, true);
    }

    public static Color showDialog(vaadinx.awt.Component parent, String title, Color initialColor,
                                   boolean colorTransparencySelectionEnabled) {
        JColorChooser pane = new JColorChooser(initialColor != null ? initialColor : Color.white);
        Color[] result = { null };

        JDialog dialog = buildOwnedModalDialog(parent, title);
        Dialog peerDialog = (Dialog) dialog.getPeer();
        peerDialog.add(pane.getPeer());

        Button ok = new Button("OK");
        Button cancel = new Button("Cancel");
        Button reset = new Button("Reset");
        // Raw Vaadin Buttons: dispose() fires the Swing-side WINDOW_CLOSED, so
        // the handlers funnel through EHelper.callSwing per R_callswing_envelope.
        ok.addClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            result[0] = pane.getColor();
            dialog.dispose();
        }));
        cancel.addClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            result[0] = null;
            dialog.dispose();
        }));
        // JDK's Reset button restores the initial color without closing.
        reset.addClickListener(e -> vaadinx.EHelper.callSwing(() -> pane.setColor(initialColor)));

        peerDialog.add(new HorizontalLayout(ok, cancel, reset));

        dialog.setVisible(true); // parks the VT until a button / X disposes
        // null on Cancel / X / ESC — JDK contract.
        return result[0];
    }

    /**
     * JDK-shaped {@code createDialog}: returns a <em>non-blocking</em> JDialog
     * the caller wires and shows itself. The two ActionListeners fire on
     * OK / Cancel respectively (JDK semantics); both dispose the dialog.
     */
    public static JDialog createDialog(vaadinx.awt.Component parent, String title, boolean modal,
                                       JColorChooser chooserPane,
                                       ActionListener okListener,
                                       ActionListener cancelListener) {
        JDialog dialog = buildOwnedModalDialog(parent, title);
        dialog.setModal(modal);
        Dialog peerDialog = (Dialog) dialog.getPeer();
        peerDialog.add(chooserPane.getPeer());

        Button ok = new Button("OK");
        Button cancel = new Button("Cancel");
        ok.addClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            if (okListener != null) {
                okListener.actionPerformed(new ActionEvent(chooserPane, ActionEvent.ACTION_PERFORMED, "OK"));
            }
            dialog.dispose();
        }));
        cancel.addClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            if (cancelListener != null) {
                cancelListener.actionPerformed(new ActionEvent(chooserPane, ActionEvent.ACTION_PERFORMED, "Cancel"));
            }
            dialog.dispose();
        }));
        peerDialog.add(new HorizontalLayout(ok, cancel));
        return dialog; // caller does dialog.setVisible(true)
    }

    /** Build the modal JDialog owned by {@code parent}'s window ancestor (JFileChooser shape). */
    private static JDialog buildOwnedModalDialog(vaadinx.awt.Component parent, String title) {
        vaadinx.awt.Window owner = windowAncestor(parent);
        JDialog dialog;
        if (owner instanceof vaadinx.awt.Frame f) {
            dialog = new JDialog(f, title, true);
        } else if (owner instanceof vaadinx.awt.Dialog d) {
            dialog = new JDialog(d, title, true);
        } else {
            dialog = new JDialog((vaadinx.awt.Frame) null, title, true);
        }
        dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        return dialog;
    }

    private static vaadinx.awt.Window windowAncestor(vaadinx.awt.Component c) {
        while (c != null) {
            if (c instanceof vaadinx.awt.Window w) return w;
            c = c.getParent();
        }
        return null;
    }

    @Override
    protected String paramString() {
        StringBuilder chooserPanelsString = new StringBuilder();
        for (javax.swing.colorchooser.AbstractColorChooserPanel panel : chooserPanels) {
            chooserPanelsString.append('[').append(panel).append(']');
        }
        String previewPanelString = (previewPanel != null ? previewPanel.toString() : "");
        return super.paramString() + ",chooserPanels="
                + chooserPanelsString.toString() + ",previewPanel="
                + previewPanelString;
    }

    // ---- L&F stubs (JProgressBar shape) ----

    public void updateUI() {
        // L&F swap — Vaadin owns the DOM; no pluggable UI.
    }

    public String getUIClassID() {
        // Kept for BeanInfo-style introspection; matches JDK JColorChooser.
        return "ColorChooserUI";
    }

    public javax.swing.plaf.ColorChooserUI getUI() {
        // No pluggable UI delegate; short-circuit rather than bare-cast a null.
        return null;
    }

    public void setUI(javax.swing.plaf.ColorChooserUI ui) {
        // L&F install — no-op per above.
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JColorChooser", "getAccessibleContext");
        return null;
    }
}
