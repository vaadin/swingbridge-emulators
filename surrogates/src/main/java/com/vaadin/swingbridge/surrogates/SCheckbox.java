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

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;

import javax.swing.event.EventListenerList;
import java.awt.ItemSelectable;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Surrogate for {@link java.awt.Checkbox} — the AWT 1.0 checkbox, not
 * {@link javax.swing.JCheckBox}. Extends Vaadin {@link Checkbox} directly
 * (is-a) and picks up the AWT {@code Component} API from
 * {@link ComponentMixin}.
 *
 * <pre>{@code
 * SCheckbox c = new SCheckbox("Enable sync", false);
 * c.addItemListener(e -> log(e.getStateChange() == ItemEvent.SELECTED));
 * c.setState(true);       // silent: no ItemEvent, exactly as in AWT
 * c.setRadioLook(true);   // renders as a radio glyph; behaviour unchanged
 * }</pre>
 *
 * <h2>Only the browser posts events</h2>
 *
 * AWT's {@code setState} fires nothing — only the toolkit peer posts an
 * {@link ItemEvent}. That inverts Swing, where {@code setSelected} drives a
 * {@code ButtonModel} fan-out, and it is why the value listener below gates
 * on {@code isFromClient()} instead of carrying a {@code preventPeerEvents}
 * flag: a server-side push is precisely the case AWT stays silent for, so
 * there is no echo to suppress.
 *
 * <h2>Why not {@code AbstractButtonMixin}</h2>
 *
 * The mixin already owns {@code addItemListener} / {@code fireItemStateChanged}
 * / {@code getSelectedObjects}, so the overlap is larger than it was for
 * {@link SButton} — but the mixin's {@code ItemEvent} fan-out is driven off
 * the {@link javax.swing.ButtonModel}, so a programmatic {@code setSelected}
 * delivers an event. That is exactly the behaviour AWT does not have, on the
 * most-used method of the class. The rest of the mixin ({@code getModel},
 * {@code setAction}, the icon family, mnemonic, {@code doClick},
 * {@code ActionListener}, {@code ChangeListener}) has no {@code java.awt.Checkbox}
 * counterpart at all, and it sits on {@code JComponentMixin}, which would
 * bring borders and client properties to a {@code java.awt.Component}.
 * Duplicating the ~30-line fan-out is the cheaper mistake (SD_scheckbox).
 *
 * <h2>The radio glyph</h2>
 *
 * A grouped {@code java.awt.Checkbox} paints as a radio button — in the JDK
 * a live peer property read off {@code group != null}, not a construction-time
 * type choice. {@link #setRadioLook} reproduces that as the {@code emul-radio}
 * theme name, which {@code emul/scheckbox.css} turns into a round box with a
 * dot — via a documented style property for the shape and a {@code ::part}
 * override for the glyph, for the reason that file records. Appearance only:
 * mutual exclusion lives on the emulator
 * {@code vaadinx.awt.Checkbox}, which owns the {@code CheckboxGroup} (D_buttongroup_browser_click),
 * and this class un-checks on a second click like any checkbox.
 */
@StyleSheet("emul/scheckbox.css")
public class SCheckbox extends Checkbox implements ComponentMixin, ItemSelectable {

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

    private final EventListenerList listenerList = new EventListenerList();

    public SCheckbox() {
        this(null, false);
    }

    public SCheckbox(String label) {
        this(label, false);
    }

    /**
     * @param label null renders a captionless box and reads back as {@code ""}
     *        (accepted R_vaadin_first loss; the emulator keeps the JDK's null)
     */
    public SCheckbox(String label, boolean state) {
        super();
        _installSwingClass();
        // One Vaadin subscription drives the whole ItemListener fan-out
        // (R_vaadin_first's shared-subscription wiring shape).
        addValueChangeListener(e -> {
            // AWT posts an ItemEvent only for a toolkit-originated toggle;
            // setState and the group cascade are silent. isFromClient is
            // exactly that distinction, so no preventPeerEvents flag is
            // needed here (unlike SJCheckBox, whose Swing contract fires on
            // server pushes too).
            if (!e.isFromClient()) return;
            boolean selected = Boolean.TRUE.equals(e.getValue());
            // R_callswing_envelope: the browser → AWT seam funnels through callSwing so a
            // listener that opens a modal dialog can park on the loom
            // virtual thread.
            SHelper.callSwing(() -> processItemEvent(new ItemEvent(
                    this,
                    ItemEvent.ITEM_STATE_CHANGED,
                    getLabel(),
                    selected ? ItemEvent.SELECTED : ItemEvent.DESELECTED)));
        });
        if (label != null) setLabel(label);
        setState(state);
    }

    // --- state (AWT 1.0 name for Vaadin's value) ----------------------

    public boolean getState() {
        return Boolean.TRUE.equals(getValue());
    }

    /** Fires no {@link ItemEvent} — AWT's programmatic setter is silent. */
    public void setState(boolean state) {
        setValue(state);
    }

    /**
     * @return a one-element array holding the label when checked, else null —
     *         AWT's convention here, which {@code java.awt.List} does
     *         <em>not</em> share (it answers an empty array)
     */
    @Override
    public Object[] getSelectedObjects() {
        return getState() ? new Object[]{getLabel()} : null;
    }

    // --- label --------------------------------------------------------

    /**
     * Never mix with {@code setLabelComponent} — Vaadin's
     * {@code setLabel} evicts a label component child.
     *
     * @param label null clears the caption and reads back as {@code ""};
     *        the changed-only guard is AWT's
     */
    @Override
    public void setLabel(String label) {
        String text = label == null ? "" : label;
        if (text.equals(getLabel())) return;
        super.setLabel(text);
    }

    // --- radio look ---------------------------------------------------

    public boolean isRadioLook() {
        return getElement().getThemeList().contains("emul-radio");
    }

    /**
     * Switches between the checkbox and radio glyphs. Appearance only — the
     * mutual exclusion an AWT {@code CheckboxGroup} implies is the emulator's
     * job.
     *
     * @param radioLook DOM-backed via the {@code emul-radio} theme name, so
     *        the round-trip is lossless and no field shadow is needed
     */
    public void setRadioLook(boolean radioLook) {
        getElement().getThemeList().set("emul-radio", radioLook);
    }

    // --- ItemListener fan-out -----------------------------------------

    @Override
    public void addItemListener(ItemListener l) {
        // AWT silently ignores null listeners; EventListenerList.add would
        // happily store one and NPE at dispatch time.
        if (l == null) return;
        listenerList.add(ItemListener.class, l);
    }

    @Override
    public void removeItemListener(ItemListener l) {
        if (l == null) return;
        listenerList.remove(ItemListener.class, l);
    }

    public ItemListener[] getItemListeners() {
        return awtOrder();
    }

    /** @return the listeners in AWT's first-registered-first dispatch order (D_awt_dead_hooks) */
    private ItemListener[] awtOrder() {
        return SHelper.awtOrder(listenerList, ItemListener.class);
    }

    /**
     * AWT's dispatch hook. Overriding it (and calling super) is the
     * documented way for {@code java.awt.Checkbox} subclasses to intercept
     * every toggle before its listeners see it, so it stays the single
     * funnel the peer's value change routes through.
     */
    protected void processItemEvent(ItemEvent e) {
        if (e == null) return;
        for (ItemListener l : awtOrder()) {
            l.itemStateChanged(e);
        }
    }

    // AWT's generic getListeners(Class<T>) is NOT declared here: Vaadin
    // Component already has getListeners(Class<? extends ComponentEvent>),
    // same erasure and an unrelated return type, so the JVM forbids both on
    // one class. Same clash SButton and SChoice document. getItemListeners()
    // covers the only listener type this class owns, and the emulator
    // vaadinx.awt.Checkbox inherits a working getListeners from
    // vaadinx.awt.Component — which is where migrated code calls it anyway.

    // --- Overrides forced by "classes beat interfaces" ----------------

    /**
     * Routes into the mixin chain so SD_auto_pce's auto-PCE fires.
     *
     * <p>Not forced by a shadow — Vaadin {@code Checkbox} declares no
     * {@code setEnabled}, so {@code ComponentMixin}'s default wins on
     * specificity anyway. Kept to pin the choice in source, as
     * {@link SJCheckBox} does.
     */
    @Override
    public void setEnabled(boolean enabled) {
        ComponentMixin.super.setEnabled(enabled);
    }

    /**
     * Widens {@code Checkbox.validate()} to public so the mixin's
     * {@code validate()} contract is satisfied — a class member beats an
     * interface default, and narrowing the visibility would not compile.
     */
    @Override
    public void validate() {
        super.validate();
    }

    /** AWT's {@code Checkbox.paramString} omits the label clause when null. */
    protected String paramString() {
        String label = getLabel();
        return (label == null ? "" : "label=" + label + ",") + "state=" + getState();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
