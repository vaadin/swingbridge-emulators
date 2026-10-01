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
 * This file is derived from OpenJDK's javax.swing.JTabbedPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JTabbedPane (D_jtabbedpane).
//
// The pane owns its state, as the JDK's does: the per-tab Pages, the SingleSelectionModel
// and the selection arithmetic of insertTab / removeTabAt are the JDK's bodies over emulator
// fields, so no getter reads the peer (D_emulator_owned_state). The selection
// model is NOT shared with SJTabbedPane, unlike the pilot's shared models: the surrogate is
// the stage-3 JTabbedPane and shifts its own model on every structural change, so sharing
// would put two writers on one model. The surrogate keeps a private model, and this class
// flushes its selection into it (pushSelection) after every change of the pane's model.
//
// Browser -> Swing: a user's tab click runs setSelectedIndex, as BasicTabbedPaneUI's mouse
// handler does; preventPeerEvents keeps the echoes of this class's own peer writes out.
//
// Container.components holds the tab contents in insertion order, as the JDK's does (its
// insertTab appends through addImpl); tab components never enter it.
//
// Peer lock-down per R_leaf_peer_lockdown: javax.swing.JTabbedPane is a leaf in the public
// Swing hierarchy. Every ctor peers on SJTabbedPane(...) directly — no protected
// (Component peer) ctor. User-code subclasses inherit the locked peer.

import com.vaadin.swingbridge.surrogates.SJTabbedPane;

/** Emulator for {@link javax.swing.JTabbedPane}. See D_jtabbedpane. */
public class JTabbedPane extends vaadinx.swing.JComponent
        implements javax.accessibility.Accessible, javax.swing.SwingConstants, vaadinx.FieldReconciler.Reconcilable {

    public static final int WRAP_TAB_LAYOUT = 0;
    public static final int SCROLL_TAB_LAYOUT = 1;

    // JDK protected fields, Swing-side truth per D_field_write_reconcile (see JSlider for
    // the canonical commentary).
    protected int tabPlacement = TOP;
    protected javax.swing.SingleSelectionModel model;

    /** What {@link #createChangeListener} returned, registered on {@link #model} by {@link #setModel}. */
    protected javax.swing.event.ChangeListener changeListener = null;

    /** Lazily created by {@link #fireStateChanged}, as in the JDK. */
    protected transient javax.swing.event.ChangeEvent changeEvent = null;

    private int tabLayoutPolicy;
    private java.util.List<TabPage> pages;

    /** The content component {@link #fireStateChanged} last made visible. */
    private vaadinx.awt.Component visComp = null;

    /** Flushes the model's selection to the peer; kept on {@link #subscribedModel}. */
    private final javax.swing.event.ChangeListener selectionPush = e -> pushSelection();
    private javax.swing.SingleSelectionModel subscribedModel;

    /** Set while this class writes the peer, whose {@code SelectedChangeEvent} echo is not a user's. */
    private boolean preventPeerEvents;

    // Last value pushed to the peer — reconcileFields()'s write-detection baseline.
    private int pushedTabPlacement;

    // ---- Constructors (R_leaf_peer_lockdown lock-down: no protected (Component peer) ctor)

    public JTabbedPane() {
        this(TOP, WRAP_TAB_LAYOUT);
    }

    public JTabbedPane(int tabPlacement) {
        this(tabPlacement, WRAP_TAB_LAYOUT);
    }

    public JTabbedPane(int tabPlacement, int tabLayoutPolicy) {
        super(SJTabbedPane.class, peerFactory(tabPlacement, tabLayoutPolicy));
        // The surrogate already holds both values, so the setters' pushes change nothing
        // there and construction stays WARN-free.
        pushedTabPlacement = tabPlacement;
        setTabPlacement(tabPlacement);
        setTabLayoutPolicy(tabLayoutPolicy);
        pages = new java.util.ArrayList<>(1);
        setModel(new javax.swing.DefaultSingleSelectionModel());
        withPeer(p -> surrogate().addSelectedChangeListener(e -> {
            if (preventPeerEvents) return;
            int index = surrogate().getSelectedIndex();
            // BasicTabbedPaneUI's mouse handler: selects only a different tab.
            vaadinx.EHelper.callSwing(() -> {
                if (index >= 0 && index != getSelectedIndex()) setSelectedIndex(index);
            });
        }));
        vaadinx.FieldReconciler.register(this);
    }

    /**
     * Validated in the JDK's order and with its messages, now: the surrogate, which checks them
     * too, is built only once a UI is current.
     */
    private static java.util.function.Supplier<SJTabbedPane> peerFactory(int tabPlacement, int tabLayoutPolicy) {
        checkTabPlacement(tabPlacement);
        checkTabLayoutPolicy(tabLayoutPolicy);
        return () -> new SJTabbedPane(tabPlacement, tabLayoutPolicy);
    }

    private SJTabbedPane surrogate() {
        return (SJTabbedPane) getPeer();
    }

    /** D_field_write_reconcile repair hook — see {@link JSlider#reconcileFields()}. */
    @Override
    public final void reconcileFields() {
        if (model != subscribedModel) {
            javax.swing.SingleSelectionModel m = model;
            if (subscribedModel != null) subscribedModel.removeChangeListener(changeListener);
            followModel(m);
            if (m != null) m.addChangeListener(changeListener);
            pushSelection();
            vaadinx.FieldReconciler.reportDirectWrite(this, "model", "setModel");
        }
        if (tabPlacement != pushedTabPlacement) {
            int v = tabPlacement;
            withPeer(p -> surrogate().setTabPlacement(v));
            pushedTabPlacement = tabPlacement;
            vaadinx.FieldReconciler.reportDirectWrite(this, "tabPlacement", "setTabPlacement");
        }
    }

    /** Writes the peer with {@link #preventPeerEvents} set. */
    private void peerWrite(java.util.function.Consumer<com.vaadin.flow.component.Component> body) {
        withPeer(p -> guarded(p, body));
    }

    /**
     * {@link #peerWrite}, or {@link #withPeerOnLiveUI} when the body carries an icon: the
     * surrogate turns it into a Vaadin {@code Image}, whose resource URL needs a current
     * UI even while this pane is detached — {@code JLabel.setIcon}'s case.
     */
    private void peerWrite(javax.swing.Icon icon,
            java.util.function.Consumer<com.vaadin.flow.component.Component> body) {
        if (icon != null) {
            withPeerOnLiveUI(false, p -> guarded(p, body));
        } else {
            peerWrite(body);
        }
    }

    private void guarded(com.vaadin.flow.component.Component p,
            java.util.function.Consumer<com.vaadin.flow.component.Component> body) {
        preventPeerEvents = true;
        try {
            body.accept(p);
        } finally {
            preventPeerEvents = false;
        }
    }

    /**
     * Makes the peer show the model's selection. An index the pane has no tab for, which
     * the JDK lets a model hold, shows no tab.
     */
    private void pushSelection() {
        int index = model != null ? model.getSelectedIndex() : -1;
        int shown = index >= -1 && index < pages.size() ? index : -1;
        peerWrite(p -> surrogate().setSelectedIndex(shown));
    }

    /**
     * Moves {@link #selectionPush} onto {@code m}. Called before {@link #changeListener} is
     * added, so it runs after it: a model notifies the last-registered listener first, and
     * BasicTabbedPaneUI's repaint runs after the pane's listeners too.
     */
    private void followModel(javax.swing.SingleSelectionModel m) {
        if (subscribedModel != null) subscribedModel.removeChangeListener(selectionPush);
        subscribedModel = m;
        if (m != null) m.addChangeListener(selectionPush);
    }

    // ---- ChangeListener ----------------------------------------------

    /** The JDK's model listener, re-firing each model change from the pane. */
    protected class ModelListener implements javax.swing.event.ChangeListener, java.io.Serializable {
        protected ModelListener() {}

        public void stateChanged(javax.swing.event.ChangeEvent e) {
            fireStateChanged();
        }
    }

    protected javax.swing.event.ChangeListener createChangeListener() {
        return new ModelListener();
    }

    public void addChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.add(javax.swing.event.ChangeListener.class, l);
    }

    public void removeChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.remove(javax.swing.event.ChangeListener.class, l);
    }

    public javax.swing.event.ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(javax.swing.event.ChangeListener.class);
    }

    /**
     * Shows the selected tab's component and hides the one shown before, then notifies the
     * listeners, last to first. The JDK also moves the focus into the new component when the
     * old one held it; that is not reproduced.
     */
    protected void fireStateChanged() {
        int selIndex = getSelectedIndex();
        if (selIndex < 0) {
            if (visComp != null && visComp.isVisible()) {
                visComp.setVisible(false);
            }
            visComp = null;
        } else {
            vaadinx.awt.Component newComp = getComponentAt(selIndex);
            if (newComp != null && newComp != visComp) {
                if (visComp != null && visComp.isVisible()) {
                    visComp.setVisible(false);
                }
                if (!newComp.isVisible()) {
                    newComp.setVisible(true);
                }
                visComp = newComp;
            }
        }
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == javax.swing.event.ChangeListener.class) {
                if (changeEvent == null) {
                    changeEvent = new javax.swing.event.ChangeEvent(this);
                }
                ((javax.swing.event.ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }

    // ---- Model / selection -------------------------------------------

    public javax.swing.SingleSelectionModel getModel() {
        return model;
    }

    public void setModel(javax.swing.SingleSelectionModel model) {
        javax.swing.SingleSelectionModel oldModel = getModel();
        if (oldModel != null) {
            oldModel.removeChangeListener(changeListener);
            changeListener = null;
        }
        this.model = model;
        followModel(model);
        if (model != null) {
            changeListener = createChangeListener();
            model.addChangeListener(changeListener);
        }
        firePropertyChange("model", oldModel, model);
        pushSelection();
    }

    public int getSelectedIndex() {
        return model.getSelectedIndex();
    }

    public void setSelectedIndex(int index) {
        if (index != -1) {
            checkIndex(index);
        }
        setSelectedIndexImpl(index);
    }

    /** The JDK's {@code setSelectedIndexImpl} without its accessibility notifications. */
    private void setSelectedIndexImpl(int index) {
        model.setSelectedIndex(index);
    }

    public vaadinx.awt.Component getSelectedComponent() {
        int index = getSelectedIndex();
        if (index == -1) {
            return null;
        }
        return getComponentAt(index);
    }

    public void setSelectedComponent(vaadinx.awt.Component c) {
        int index = indexOfComponent(c);
        if (index != -1) {
            setSelectedIndex(index);
        } else {
            throw new IllegalArgumentException("component not found in tabbed pane");
        }
    }

    // ---- Tab placement / layout policy -------------------------------

    public int getTabPlacement() {
        return tabPlacement;
    }

    public void setTabPlacement(int tabPlacement) {
        checkTabPlacement(tabPlacement);
        if (this.tabPlacement != tabPlacement) {
            int oldValue = this.tabPlacement;
            this.tabPlacement = tabPlacement;
            peerWrite(p -> surrogate().setTabPlacement(tabPlacement));
            pushedTabPlacement = tabPlacement;
            firePropertyChange("tabPlacement", oldValue, tabPlacement);
        }
    }

    private static void checkTabPlacement(int tabPlacement) {
        if (tabPlacement != TOP && tabPlacement != LEFT
                && tabPlacement != BOTTOM && tabPlacement != RIGHT) {
            throw new IllegalArgumentException("illegal tab placement:"
                    + " must be TOP, BOTTOM, LEFT, or RIGHT");
        }
    }

    public int getTabLayoutPolicy() {
        return tabLayoutPolicy;
    }

    public void setTabLayoutPolicy(int tabLayoutPolicy) {
        checkTabLayoutPolicy(tabLayoutPolicy);
        if (this.tabLayoutPolicy != tabLayoutPolicy) {
            int oldValue = this.tabLayoutPolicy;
            this.tabLayoutPolicy = tabLayoutPolicy;
            peerWrite(p -> surrogate().setTabLayoutPolicy(tabLayoutPolicy));
            firePropertyChange("tabLayoutPolicy", oldValue, tabLayoutPolicy);
        }
    }

    private static void checkTabLayoutPolicy(int tabLayoutPolicy) {
        if (tabLayoutPolicy != WRAP_TAB_LAYOUT
                && tabLayoutPolicy != SCROLL_TAB_LAYOUT) {
            throw new IllegalArgumentException("illegal tab layout policy:"
                    + " must be WRAP_TAB_LAYOUT or SCROLL_TAB_LAYOUT");
        }
    }

    // ---- Tab add / insert / remove -----------------------------------

    public void insertTab(String title, vaadinx.swing.Icon icon,
                          vaadinx.awt.Component component, String tip, int index) {
        int newIndex = index;
        // A component already in the pane moves: its old tab goes first.
        int removeIndex = indexOfComponent(component);
        if (component != null && removeIndex != -1) {
            removeTabAt(removeIndex);
            if (newIndex > removeIndex) {
                newIndex--;
            }
        }
        int selectedIndex = getSelectedIndex();
        TabPage page = new TabPage(title != null ? title : "", icon, null, component, tip);
        pages.add(newIndex, page);
        if (component != null) {
            addSlotChild(component, -1);
        }
        int at = newIndex;
        javax.swing.Icon jdkIcon = unwrap("insertTab", icon);
        peerWrite(jdkIcon, p -> surrogate().insertTab(page.title, jdkIcon,
                component != null ? component.getPeer() : null, tip, at));
        if (component != null) {
            component.setVisible(false);
        } else {
            // The argument index, not the adjusted one, as the JDK fires it (D_owed_events).
            firePropertyChange("indexForNullComponent", -1, index);
        }
        if (pages.size() == 1) {
            setSelectedIndex(0);
        }
        if (selectedIndex >= newIndex) {
            setSelectedIndexImpl(selectedIndex + 1);
        }
    }

    public void addTab(String title, vaadinx.swing.Icon icon, vaadinx.awt.Component component, String tip) {
        insertTab(title, icon, component, tip, pages.size());
    }

    public void addTab(String title, vaadinx.swing.Icon icon, vaadinx.awt.Component component) {
        insertTab(title, icon, component, null, pages.size());
    }

    public void addTab(String title, vaadinx.awt.Component component) {
        insertTab(title, null, component, null, pages.size());
    }

    // The JDK overrides every add(...) overload, not addImpl: each one adds a tab. A
    // UIResource is the L&F's own child and is added as a plain one.

    @Override
    public vaadinx.awt.Component add(vaadinx.awt.Component component) {
        if (!(component instanceof javax.swing.plaf.UIResource)) {
            addTab(component.getName(), component);
        } else {
            super.add(component);
        }
        return component;
    }

    @Override
    public vaadinx.awt.Component add(String title, vaadinx.awt.Component component) {
        if (!(component instanceof javax.swing.plaf.UIResource)) {
            addTab(title, component);
        } else {
            super.add(title, component);
        }
        return component;
    }

    @Override
    public vaadinx.awt.Component add(vaadinx.awt.Component component, int index) {
        if (!(component instanceof javax.swing.plaf.UIResource)) {
            // Container.add() takes -1 as "append".
            insertTab(component.getName(), null, component, null,
                    index == -1 ? getTabCount() : index);
        } else {
            super.add(component, index);
        }
        return component;
    }

    /** A String constraint is the title and an Icon the icon; anything else is ignored. */
    @Override
    public void add(vaadinx.awt.Component component, Object constraints) {
        if (!(component instanceof javax.swing.plaf.UIResource)) {
            if (constraints instanceof String) {
                addTab((String) constraints, component);
            } else if (constraints instanceof vaadinx.swing.Icon) {
                addTab(null, (vaadinx.swing.Icon) constraints, component);
            } else {
                add(component);
            }
        } else {
            super.add(component, constraints);
        }
    }

    @Override
    public void add(vaadinx.awt.Component component, Object constraints, int index) {
        if (!(component instanceof javax.swing.plaf.UIResource)) {
            vaadinx.swing.Icon icon = constraints instanceof vaadinx.swing.Icon ? (vaadinx.swing.Icon) constraints : null;
            String title = constraints instanceof String ? (String) constraints : null;
            insertTab(title, icon, component, null, index == -1 ? getTabCount() : index);
        } else {
            super.add(component, constraints, index);
        }
    }

    public void removeTabAt(int index) {
        checkIndex(index);
        vaadinx.awt.Component component = getComponentAt(index);
        int selected = getSelectedIndex();
        if (component == visComp) {
            visComp = null;
        }
        setTabComponentAt(index, null);
        pages.remove(index);
        peerWrite(p -> surrogate().removeTabAt(index));
        // The JDK's hand-off to BasicTabbedPaneUI, which has no IndexPropertyChangeEvent
        // to read the index from.
        putClientProperty("__index_to_remove__", Integer.valueOf(index));
        if (selected > index) {
            setSelectedIndexImpl(selected - 1);
        } else if (selected >= getTabCount()) {
            setSelectedIndexImpl(selected - 1);
        } else if (index == selected) {
            // Same index, another tab: the model does not change, the pane fires.
            fireStateChanged();
        }
        if (component != null) {
            removeTabContent(component);
            component.setVisible(true);
        }
    }

    /**
     * Unlinks a tab's content from {@code Container.components}; the surrogate has already
     * dropped its peer. First clears {@code __index_to_remove__}, as BasicTabbedPaneUI's
     * {@code ContainerListener} does on every removal — installed at construction, it runs
     * before the migrator's, so a desktop listener saw the property already cleared.
     */
    private void removeTabContent(vaadinx.awt.Component component) {
        if (getClientProperty("__index_to_remove__") != null) {
            putClientProperty("__index_to_remove__", null);
        }
        removeSlotChild(component);
    }

    @Override
    public void remove(vaadinx.awt.Component component) {
        int index = indexOfComponent(component);
        if (index != -1) {
            removeTabAt(index);
        } else {
            // Not through super.remove(Component): it calls remove(int), which removes a tab.
            vaadinx.awt.Component[] children = getComponents();
            for (int i = 0; i < children.length; i++) {
                if (component == children[i]) {
                    super.remove(i);
                    break;
                }
            }
        }
    }

    /** Removes the <b>tab</b> at {@code index}, not the child — they differ, as in the JDK. */
    @Override
    public void remove(int index) {
        removeTabAt(index);
    }

    /** Deselects first, so it fires once, then removes the tabs last to first. */
    @Override
    public void removeAll() {
        setSelectedIndexImpl(-1);
        int tabCount = getTabCount();
        while (tabCount-- > 0) {
            removeTabAt(tabCount);
        }
    }

    public int getTabCount() {
        return pages.size();
    }

    /** One run while there are tabs: the browser scrolls the tab strip rather than wrapping it. */
    public int getTabRunCount() {
        return pages.isEmpty() ? 0 : 1;
    }

    // ---- Per-tab getters -----------------------------------------------

    public String getTitleAt(int index) {
        return pages.get(index).title;
    }

    public vaadinx.swing.Icon getIconAt(int index) {
        return pages.get(index).icon;
    }

    /**
     * The icon set by {@link #setDisabledIconAt}. The JDK's L&amp;F derives a greyed one when none
     * was set; with no L&amp;F, this answers {@code null} instead.
     */
    public vaadinx.swing.Icon getDisabledIconAt(int index) {
        return pages.get(index).disabledIcon;
    }

    public String getToolTipTextAt(int index) {
        return pages.get(index).tip;
    }

    /** The tab's own colour, or this pane's background when it has none. */
    public java.awt.Color getBackgroundAt(int index) {
        return pages.get(index).getBackground();
    }

    /** @see #getBackgroundAt */
    public java.awt.Color getForegroundAt(int index) {
        return pages.get(index).getForeground();
    }

    public boolean isEnabledAt(int index) {
        return pages.get(index).enabled;
    }

    public vaadinx.awt.Component getComponentAt(int index) {
        return pages.get(index).component;
    }

    public int getMnemonicAt(int tabIndex) {
        checkIndex(tabIndex);
        return pages.get(tabIndex).mnemonic;
    }

    public int getDisplayedMnemonicIndexAt(int tabIndex) {
        checkIndex(tabIndex);
        return pages.get(tabIndex).mnemonicIndex;
    }

    /** Always {@code null}: the server has no tab geometry (R_layouts_close_enough). */
    public java.awt.Rectangle getBoundsAt(int index) {
        checkIndex(index);
        vaadinx.EHelper.onUnimplemented("JTabbedPane", "getBoundsAt", index);
        return null;
    }

    // ---- Per-tab setters -----------------------------------------------

    /**
     * Fires {@code ("indexForTitle", -1, index)}, guarded — as the JDK guards
     * it — on {@code oldTitle != title} by <b>reference</b>, not
     * {@code equals}. So two equal-but-distinct Strings fire and two identical
     * references do not; faithful to the JDK's body, odd as it reads (D_owed_events).
     */
    public void setTitleAt(int index, String title) {
        TabPage page = pages.get(index);
        String oldTitle = page.title;
        page.title = title;
        peerWrite(p -> surrogate().setTitleAt(index, title));
        if (oldTitle != title) {
            firePropertyChange("indexForTitle", -1, index);
        }
        page.updateDisplayedMnemonicIndex();
    }

    public void setIconAt(int index, vaadinx.swing.Icon icon) {
        TabPage page = pages.get(index);
        vaadinx.swing.Icon oldIcon = page.icon;
        if (icon != oldIcon) {
            page.icon = icon;
            javax.swing.Icon jdkIcon = unwrap("setIconAt", icon);
            peerWrite(jdkIcon, p -> surrogate().setIconAt(index, jdkIcon));
        }
    }

    public void setDisabledIconAt(int index, vaadinx.swing.Icon disabledIcon) {
        pages.get(index).disabledIcon = disabledIcon;
        javax.swing.Icon jdkIcon = unwrap("setDisabledIconAt", disabledIcon);
        peerWrite(jdkIcon, p -> surrogate().setDisabledIconAt(index, jdkIcon));
    }

    public void setToolTipTextAt(int index, String toolTipText) {
        pages.get(index).tip = toolTipText;
        peerWrite(p -> surrogate().setToolTipTextAt(index, toolTipText));
    }

    /**
     * Stores the tab's colour and fires nothing, as the JDK's repaints and fires nothing;
     * {@code null} clears it. The surrogate declines the rendering: a Vaadin TabSheet has no
     * per-tab background (R_decline_effect_only).
     */
    public void setBackgroundAt(int index, java.awt.Color background) {
        pages.get(index).background = background;
        peerWrite(p -> surrogate().setBackgroundAt(index, background));
    }

    /** @see #setBackgroundAt */
    public void setForegroundAt(int index, java.awt.Color foreground) {
        pages.get(index).foreground = foreground;
        peerWrite(p -> surrogate().setForegroundAt(index, foreground));
    }

    public void setEnabledAt(int index, boolean enabled) {
        pages.get(index).enabled = enabled;
        peerWrite(p -> surrogate().setEnabledAt(index, enabled));
    }

    /**
     * Replaces the tab's content, keeping the selection. {@code null} leaves the tab
     * without one, as the JDK allows; the peer shows an empty area.
     */
    public void setComponentAt(int index, vaadinx.awt.Component component) {
        TabPage page = pages.get(index);
        if (component != page.component) {
            if (page.component != null) {
                removeTabContent(page.component);
            }
            page.component = component;
            boolean selectedPage = getSelectedIndex() == index;
            if (selectedPage) {
                if (visComp != null && visComp.isVisible() && !visComp.equals(component)) {
                    visComp.setVisible(false);
                }
                visComp = component;
            }
            if (component != null) {
                component.setVisible(selectedPage);
                addSlotChild(component, -1);
            }
            peerWrite(p -> surrogate().setComponentAt(index, component != null
                    ? component.getPeer() : new com.vaadin.flow.component.html.Div()));
        }
    }

    /**
     * Throws the JDK's {@code IllegalArgumentException} for an index outside the title. The
     * surrogate declines the effect: a Vaadin tab has no underlined character.
     */
    public void setDisplayedMnemonicIndexAt(int tabIndex, int mnemonicIndex) {
        checkIndex(tabIndex);
        pages.get(tabIndex).setDisplayedMnemonicIndex(mnemonicIndex);
        peerWrite(p -> surrogate().setDisplayedMnemonicIndexAt(tabIndex, mnemonicIndex));
    }

    /**
     * Fires {@code "displayedMnemonicIndexAt"} when the underlined character moves, then
     * {@code "mnemonicAt"}, both with {@code (null, null)} — the JDK's literal argument pair.
     * {@code PropertyChangeSupport} lets a both-null pair through, so the event always
     * reaches listeners and carries no payload (D_owed_events). The surrogate declines the
     * keyboard effect.
     */
    public void setMnemonicAt(int tabIndex, int mnemonic) {
        checkIndex(tabIndex);
        pages.get(tabIndex).setMnemonic(mnemonic);
        peerWrite(p -> surrogate().setMnemonicAt(tabIndex, mnemonic));
        firePropertyChange("mnemonicAt", null, null);
    }

    /**
     * Custom tab header, not a child in {@code Container.components}. Throws the JDK's
     * {@code IllegalArgumentException} for a component that is already a tab's content,
     * and moves one that is already another tab's header.
     */
    public void setTabComponentAt(int index, vaadinx.awt.Component component) {
        if (component != null && indexOfComponent(component) != -1) {
            throw new IllegalArgumentException("Component is already added to this JTabbedPane");
        }
        vaadinx.awt.Component oldValue = getTabComponentAt(index);
        if (component != oldValue) {
            int tabComponentIndex = indexOfTabComponent(component);
            if (tabComponentIndex != -1) {
                setTabComponentAt(tabComponentIndex, null);
            }
            pages.get(index).tabComponent = component;
            peerWrite(p -> surrogate().setTabComponentAt(index, component != null ? component.getPeer() : null));
            firePropertyChange("indexForTabComponent", -1, index);
        }
    }

    public vaadinx.awt.Component getTabComponentAt(int index) {
        return pages.get(index).tabComponent;
    }

    // ---- Index lookups -------------------------------------------------

    /** {@code null} finds an empty title. Throws NPE past a tab whose title was set to {@code null}, as the JDK does. */
    public int indexOfTab(String title) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getTitleAt(i).equals(title == null ? "" : title)) {
                return i;
            }
        }
        return -1;
    }

    public int indexOfTab(vaadinx.swing.Icon icon) {
        for (int i = 0; i < getTabCount(); i++) {
            vaadinx.swing.Icon tabIcon = getIconAt(i);
            if ((tabIcon != null && tabIcon.equals(icon))
                    || (tabIcon == null && tabIcon == icon)) {
                return i;
            }
        }
        return -1;
    }

    /** {@code null} finds the first tab without a content component. */
    public int indexOfComponent(vaadinx.awt.Component component) {
        for (int i = 0; i < getTabCount(); i++) {
            vaadinx.awt.Component c = getComponentAt(i);
            if ((c != null && c.equals(component))
                    || (c == null && c == component)) {
                return i;
            }
        }
        return -1;
    }

    public int indexOfTabComponent(vaadinx.awt.Component tabComponent) {
        for (int i = 0; i < getTabCount(); i++) {
            vaadinx.awt.Component c = getTabComponentAt(i);
            if (c == tabComponent) {
                return i;
            }
        }
        return -1;
    }

    /** Always {@code -1}: the server has no tab geometry (R_layouts_close_enough). */
    public int indexAtLocation(int x, int y) {
        vaadinx.EHelper.onUnimplemented("JTabbedPane", "indexAtLocation", x, y);
        return -1;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Tab count: " + pages.size());
        }
    }

    /**
     * The JDK icon to hand the surrogate for a ported one, or null.
     *
     * <p>Same triage as {@link JLabel#setIcon}: a {@link vaadinx.swing.ImageIcon}
     * unwraps to its JDK delegate and renders; any other {@code Icon} impl is
     * user-authored paint (R_match_swing_errors (b)) and reaches the peer as null, WARNing here
     * rather than surrogate-side so migration logs name {@code JTabbedPane}.
     */
    private javax.swing.Icon unwrap(String method, vaadinx.swing.Icon icon) {
        if (icon == null) return null;
        if (icon instanceof vaadinx.swing.ImageIcon ours) return ours.asJdk();
        vaadinx.EHelper.onUnimplemented("JTabbedPane", method, icon);
        return null;
    }

    // toString delegates entirely to super: JComponent builds it from the
    // paramString chain, and our paramString override already appends
    // tabPlacement / tabLayoutPolicy. Kept as an explicit JDK-shape anchor
    // (JDK JTabbedPane overrides toString the same way) rather than dropped,
    // so the seam stays visible if a richer dump is ever needed.
    @Override
    public String toString() {
        return super.toString();
    }

    // ---- L&F surface ------------------------------------------------

    public String getUIClassID() {
        return "TabbedPaneUI";
    }

    public void updateUI() {
        // L&F swap — no-op for us; Vaadin owns the DOM. Same shape as
        // JPanel.updateUI / JSplitPane.updateUI.
    }

    public javax.swing.plaf.TabbedPaneUI getUI() {
        vaadinx.EHelper.onUnimplemented("JTabbedPane", "getUI");
        return null;
    }

    public void setUI(javax.swing.plaf.TabbedPaneUI ui) {
        vaadinx.EHelper.onUnimplemented("JTabbedPane", "setUI", ui);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JTabbedPane", "getAccessibleContext");
        return null;
    }

    @Override
    protected String paramString() {
        // JDK's JTabbedPane.paramString reports tabPlacement, validateRoot,
        // haveRegistered. We only do tabPlacement + tabLayoutPolicy.
        String placement;
        switch (tabPlacement) {
            case TOP: placement = "TOP"; break;
            case LEFT: placement = "LEFT"; break;
            case BOTTOM: placement = "BOTTOM"; break;
            case RIGHT: placement = "RIGHT"; break;
            default: placement = String.valueOf(tabPlacement);
        }
        String policy = tabLayoutPolicy == SCROLL_TAB_LAYOUT
                ? "SCROLL_TAB_LAYOUT" : "WRAP_TAB_LAYOUT";
        return super.paramString()
                + ",tabPlacement=" + placement
                + ",tabLayoutPolicy=" + policy;
    }

    /** One tab's state: the JDK's {@code Page}, less its accessibility half. */
    private final class TabPage {
        String title;
        java.awt.Color background;
        java.awt.Color foreground;
        vaadinx.swing.Icon icon;
        vaadinx.swing.Icon disabledIcon;
        vaadinx.awt.Component component;
        String tip;
        boolean enabled = true;
        int mnemonic = -1;
        int mnemonicIndex = -1;
        vaadinx.awt.Component tabComponent;

        TabPage(String title, vaadinx.swing.Icon icon, vaadinx.swing.Icon disabledIcon,
             vaadinx.awt.Component component, String tip) {
            this.title = title;
            this.icon = icon;
            this.disabledIcon = disabledIcon;
            this.component = component;
            this.tip = tip;
        }

        void setMnemonic(int mnemonic) {
            this.mnemonic = mnemonic;
            updateDisplayedMnemonicIndex();
        }

        void setDisplayedMnemonicIndex(int mnemonicIndex) {
            if (this.mnemonicIndex != mnemonicIndex) {
                String t = getTitle();
                if (mnemonicIndex != -1 && (t == null
                        || mnemonicIndex < 0
                        || mnemonicIndex >= t.length())) {
                    throw new IllegalArgumentException(
                            "Invalid mnemonic index: " + mnemonicIndex);
                }
                this.mnemonicIndex = mnemonicIndex;
                JTabbedPane.this.firePropertyChange("displayedMnemonicIndexAt", null, null);
            }
        }

        void updateDisplayedMnemonicIndex() {
            setDisplayedMnemonicIndex(
                    SwingUtilities.findDisplayedMnemonicIndex(getTitle(), mnemonic));
        }

        java.awt.Color getBackground() {
            return background != null ? background : JTabbedPane.this.getBackground();
        }

        java.awt.Color getForeground() {
            return foreground != null ? foreground : JTabbedPane.this.getForeground();
        }

        private String getTitle() {
            return getTitleAt(getPageIndex());
        }

        /** Found through the component, as the JDK's is — so two tabs without one share an index. */
        private int getPageIndex() {
            if (component != null || tabComponent == null) {
                return indexOfComponent(component);
            }
            return indexOfTabComponent(tabComponent);
        }
    }
}
