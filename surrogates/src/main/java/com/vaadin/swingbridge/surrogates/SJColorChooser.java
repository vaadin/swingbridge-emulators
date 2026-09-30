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

import com.vaadin.swingbridge.surrogates.util.CssConvert;

import com.vaadin.flow.component.html.Input;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.JComponent;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.colorchooser.DefaultColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Surrogate for {@link javax.swing.JColorChooser} (SD_sjcolorchooser). Extends Vaadin
 * {@link Input} with {@code type="color"} — Vaadin 25 ships no color-picker
 * component, so we re-purpose the browser-native {@code <input type="color">}
 * primitive, whose {@code value} property is a {@code #rrggbb} hex string.
 * {@link Input} is public and already an
 * {@code AbstractSinglePropertyField<Input, String>} bound to the
 * {@code value} property, so it does the value-property binding +
 * {@code value-changed} subscription for us — cleaner than re-hosting a raw
 * {@code <input>} the way {@link VaadinRadioButton} had to (that re-host only
 * exists because Vaadin's own {@code RadioButton} wrapper is package-private).
 *
 * <h2>Model flow (SJSlider / SD_sjslider precedent)</h2>
 *
 * The installed {@link ColorSelectionModel} is the source of truth (R_swing_is_truth). A
 * single {@link ChangeListener} on the model fans out to user ChangeListeners
 * and pushes the selected color (as hex) to the peer (this Input's own
 * element) under the {@link #preventPeerEvents} R_swing_is_truth guard. The peer's
 * {@code ValueChangeListener} mirrors browser-originated edits back into the
 * model via {@link SHelper#callSwing} (R_callswing_envelope); the resulting model ChangeEvent
 * re-enters {@link #fanOutModelChange}, hits the guard, and only the Swing-side
 * fan-out runs — no second peer write. Unlike display-only {@link SJProgressBar}
 * (no peer→Swing path), this component has a live browser edit path, so it
 * needs both the guard and the callSwing wrap, exactly like {@link SJSlider}.
 *
 * <h2>Alpha loss (accepted, SD_sjcolorchooser)</h2>
 *
 * {@code <input type="color">} is opaque {@code #rrggbb} only. A model color
 * with {@code alpha < 255} pushes to the peer as its opaque RGB, and a browser
 * round-trip drops alpha — an accepted R_best_effort_behaviour/R_vaadin_first divergence. The model itself
 * retains whatever {@link #setColor} last stored until a browser edit
 * overwrites it.
 *
 * <h2>Chooser panels / preview panel: R_vaadin_first drop-and-WARN</h2>
 *
 * {@link AbstractColorChooserPanel} content (swatches / HSV / RGB tabs) and the
 * preview panel are pure custom paint — R_match_swing_errors sub-bucket (b). The native color
 * input <em>is</em> the chooser UI here. The setters field-round-trip so
 * introspection works, but WARN and drive nothing.
 */
public class SJColorChooser extends Input implements JComponentMixin {

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

    // ---- ColorSelectionModel state (source of truth, R_swing_is_truth) ----

    private ColorSelectionModel selectionModel;
    private ChangeListener modelListener;

    // R_swing_is_truth feedback-loop guard — set before writing to the peer (super.setValue)
    // so the peer's ValueChangeListener bails instead of round-tripping the
    // browser value back into the model. Identical pattern to SJSlider.
    private boolean preventPeerEvents;

    // javax.swing.EventListenerList for ChangeListener fan-out (source=this).
    // Same shape as SJSlider's — lazy event allocation, type-keyed arrays.
    private final EventListenerList listenerList = new EventListenerList();

    // ---- R_vaadin_first drop-and-WARN state (round-trips its own getter, drives nothing) ----

    private boolean dragEnabled;
    private JComponent previewPanel;
    private final List<AbstractColorChooserPanel> chooserPanels = new ArrayList<>();

    // ---- Constructors (root = the model ctor) ----

    public SJColorChooser() {
        this(new DefaultColorSelectionModel());
    }

    public SJColorChooser(Color initialColor) {
        this(new DefaultColorSelectionModel(initialColor != null ? initialColor : Color.white));
    }

    public SJColorChooser(ColorSelectionModel model) {
        super();
        // Native color picker: the browser renders the swatch + system dialog.
        setType("color");
        _installSwingClass();
        initPeerListener();
        installModel(model != null ? model : new DefaultColorSelectionModel());
    }

    // ---- Peer → model (R_callswing_envelope seam), SJSlider.initPeerListener shape ----

    private void initPeerListener() {
        // A browser color pick fires ValueChangeListener carrying the new
        // #rrggbb hex. Funnelled through SHelper.callSwing so the R_callswing_envelope seam
        // lands in one place (SD_sframe keeps it a plain run() on the surrogate).
        addValueChangeListener(e -> SHelper.callSwing(() -> syncColorFromPeer(e.getValue())));
    }

    // ---- Model plumbing (SJSlider.installModel shape) ----

    /**
     * Attach the supplied model, wire the fan-out listener, push the model's
     * current color through to the peer. Also called on {@link #setSelectionModel}
     * — teardown of the previous subscription happens here so the single entry
     * point is self-contained.
     */
    private void installModel(ColorSelectionModel model) {
        if (selectionModel != null && modelListener != null) {
            selectionModel.removeChangeListener(modelListener);
        }
        selectionModel = model;
        if (modelListener == null) {
            modelListener = e -> fanOutModelChange();
        }
        model.addChangeListener(modelListener);
        pushColorToPeer(model.getSelectedColor());
    }

    /**
     * Dispatch one ChangeEvent (source=this) to every registered user
     * ChangeListener and push the model's current color to the peer. Called
     * whenever the model fires — user-code {@link #setColor} and peer-mirror
     * writes both land here.
     */
    private void fanOutModelChange() {
        // Allowed by R_tolerate_off_ui_thread because callback from model: ColorSelectionModel ChangeListener
        SHelper.runOnOwnerUI(this, () -> {
            pushColorToPeer(selectionModel.getSelectedColor());
            fireStateChanged();
        });
    }

    private void pushColorToPeer(Color color) {
        preventPeerEvents = true;
        try {
            super.setValue(color == null ? "#000000" : CssConvert.toHexColor(color));
        } finally {
            preventPeerEvents = false;
        }
    }

    private void syncColorFromPeer(String hex) {
        if (preventPeerEvents) return;
        Color color = CssConvert.colorFromHex(hex);
        if (color == null) return;
        if (color.equals(selectionModel.getSelectedColor())) return;
        // Writing through the model fires its ChangeEvent, which our
        // modelListener picks up and fans out + pushes to the peer.
        selectionModel.setSelectedColor(color);
    }

    // ---- Swing-shape color API (routes through the model) ----
    //
    // No signature clash with Input's setValue(String) / getValue() — these
    // are differently named, so both surfaces coexist. super.setValue(hex) is
    // used internally for the guarded peer push.

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
        setColor(new Color(c));
    }

    public ColorSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void setSelectionModel(ColorSelectionModel newModel) {
        ColorSelectionModel old = selectionModel;
        if (old == newModel || newModel == null) return;
        installModel(newModel);
        firePropertyChange("selectionModel", old, newModel);
    }

    // ---- ChangeListener fan-out (source = this), SJSlider shape ----

    public void addChangeListener(ChangeListener l) {
        listenerList.add(ChangeListener.class, l);
    }

    public void removeChangeListener(ChangeListener l) {
        listenerList.remove(ChangeListener.class, l);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(ChangeListener.class);
    }

    protected void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener l : listenerList.getListeners(ChangeListener.class)) {
            l.stateChanged(event);
        }
    }

    // ---- dragEnabled: harmless flag, field round-trip, silent ----

    public boolean getDragEnabled() {
        return dragEnabled;
    }

    public void setDragEnabled(boolean b) {
        // No Vaadin drag-out-of-swatch counterpart; the flag round-trips for
        // introspection but drives nothing. Silent (not WARN) — it's a benign
        // preference, not a dropped capability the migrator should see logged.
        this.dragEnabled = b;
    }

    // ---- Preview panel + chooser panels: R_vaadin_first drop-and-WARN (sub-bucket (b)) ----

    public JComponent getPreviewPanel() {
        return previewPanel;
    }

    public void setPreviewPanel(JComponent preview) {
        // Pure custom paint — the native color input has no preview slot.
        // Field-round-trip so getPreviewPanel is honest; WARN so the drop shows.
        this.previewPanel = preview;
        SHelper.onUnimplemented(this, "setPreviewPanel", preview);
    }

    public AbstractColorChooserPanel[] getChooserPanels() {
        return chooserPanels.toArray(new AbstractColorChooserPanel[0]);
    }

    public void setChooserPanels(AbstractColorChooserPanel[] panels) {
        chooserPanels.clear();
        if (panels != null) Collections.addAll(chooserPanels, panels);
        SHelper.onUnimplemented(this, "setChooserPanels", (Object) panels);
    }

    public void addChooserPanel(AbstractColorChooserPanel panel) {
        chooserPanels.add(panel);
        SHelper.onUnimplemented(this, "addChooserPanel", panel);
    }

    public AbstractColorChooserPanel removeChooserPanel(AbstractColorChooserPanel panel) {
        chooserPanels.remove(panel);
        SHelper.onUnimplemented(this, "removeChooserPanel", panel);
        return panel;
    }

    // ---- L&F ----

    @Override
    public String getUIClassID() {
        // Kept for BeanInfo-style introspection; matches JDK JColorChooser.
        return "ColorChooserUI";
    }
}
