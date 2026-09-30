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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.swingbridge.surrogates.internal.TabbedPaneStateStore;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;
import com.vaadin.swingbridge.surrogates.util.Icons;

import javax.swing.Icon;
import javax.swing.SingleSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Surrogate for {@link javax.swing.JTabbedPane} (SD_sjtabbedpane).
 * Tab strip + content swap on top of Vaadin {@link TabSheet}. Selection
 * lives on the peer (R_vaadin_first source-of-truth); per-tab metadata
 * (title/icon/tooltip/custom-header) lives in {@link TabbedPaneStateStore}
 * and is rendered into each Vaadin {@link Tab}'s children on every change.
 *
 * <h2>Why children-based, never {@code Tab.setLabel}</h2>
 *
 * {@code tab.setLabel("X")} wipes the Tab's children — so an icon added with
 * {@code tab.add(icon)} disappears on the next title change. The children-based
 * render strategy ({@code tab.removeAll(); tab.add(icon); tab.add(new
 * Span(title));}) sidesteps the wipe and keeps a uniform state machine across
 * (title only / title + icon / custom header) modes.
 * {@link Tab#setAriaLabel(String)} preserves accessibility regardless of visual
 * mode.
 *
 * <h2>API partition (per SD_sjtabbedpane)</h2>
 *
 * <p>Methods Vaadin can reproduce are real impls (add/insert/remove,
 * selectedIndex, title/icon/tooltip/enabled at, tabComponentAt). The
 * cluster Vaadin doesn't expose Java-side — {@code setBackgroundAt},
 * {@code setForegroundAt}, {@code setDisabledIconAt},
 * {@code setMnemonicAt}, {@code setDisplayedMnemonicIndexAt},
 * {@code setModel} — drop-and-WARN. Round-trip for those headline
 * properties that warrant it lives on the emulator's
 * {@code vaadinx.swing.JTabbedPane}; per-tab no-counterpart attributes
 * stay drop-and-WARN both layers (R_vaadin_first + per-tab arrays would be storage
 * for storage's sake without a Vaadin pulse behind them).
 *
 * <h2>Tab placement / layout policy</h2>
 *
 * <ul>
 *   <li>{@code setTabPlacement(TOP)} — delivered (Vaadin TabSheet is top-only).</li>
 *   <li>{@code setTabPlacement(LEFT|RIGHT|BOTTOM)} — WARN, field-shadow
 *       round-trip on emulator. R_match_swing_errors sub-bucket (a) blocked-upstream:
 *       Vaadin TabSheet exposes no orientation knob.</li>
 *   <li>{@code setTabLayoutPolicy(SCROLL_TAB_LAYOUT)} — silently accepted
 *       (Vaadin auto-scrolls on overflow already; no WARN since user
 *       intent is already met).</li>
 *   <li>{@code setTabLayoutPolicy(WRAP_TAB_LAYOUT)} — WARN, field-shadow
 *       round-trip. R_match_swing_errors sub-bucket (a): Vaadin has no wrap mode.</li>
 * </ul>
 *
 * <p>Garbage placement / policy ints throw {@link IllegalArgumentException}
 * matching JDK (R_match_swing_errors).
 *
 * <h2>Selection bridge</h2>
 *
 * <p>Two-way wire: peer SelectedChangeEvent → callSwing (R_callswing_envelope) →
 * {@code model.setSelectedIndex} → internal modelListener fires
 * ChangeListeners + pushes back to peer iff peer differs.
 * {@code preventPeerEvents} guards the peer-push leg against re-entrancy.
 * The model is JDK-side {@link javax.swing.DefaultSingleSelectionModel}; user code
 * can {@code getModel().addChangeListener(...)} and observe the same
 * fan-out as {@code tabbedPane.addChangeListener(...)}.
 *
 * <h2>removeTabAt selection-shift</h2>
 *
 * <p>Vaadin TabSheet's {@code remove(int)} reshuffles selection identically
 * to JDK across every corner (remove middle: stays at index; remove last:
 * shifts to i-1; remove below: decrements; remove above: unchanged; remove
 * only-tab: -1). No bridge translation needed — just forward.
 *
 * <h2>Loading-spinner suppression</h2>
 *
 * <p>vaadin-tabsheet shows an indeterminate spinner whenever the selected tab
 * has no attached content; an empty tab strip (all tabs closed) is exactly
 * that, so it would spin forever. Swing renders a blank content area instead,
 * so {@code emul/sjtabbedpane.css} ({@link StyleSheet} below) hides the loader
 * part. The loading state is web-component-owned (no server-side lever). The
 * CSS ships as a plain servlet-served static resource ({@code META-INF/
 * resources/emul/sjtabbedpane.css}), not a frontend import, so it never forces
 * a Vite dev-bundle rebuild.
 */
@StyleSheet("emul/sjtabbedpane.css")
public class SJTabbedPane extends TabSheet implements JComponentMixin {

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

    // JDK-side constants (mirrored on this layer so user code reading
    // SJTabbedPane.WRAP_TAB_LAYOUT compiles).
    public static final int WRAP_TAB_LAYOUT = 0;
    public static final int SCROLL_TAB_LAYOUT = 1;

    // ---- Constructors ------------------------------------------------

    public SJTabbedPane() {
        this(SwingConstants.TOP, WRAP_TAB_LAYOUT);
    }

    public SJTabbedPane(int tabPlacement) {
        this(tabPlacement, WRAP_TAB_LAYOUT);
    }

    public SJTabbedPane(int tabPlacement, int tabLayoutPolicy) {
        super();
        validateTabPlacement(tabPlacement);
        validateTabLayoutPolicy(tabLayoutPolicy);
        _installSwingClass();
        TabbedPaneStateStore store = TabbedPaneStateStore.of(this);
        store.tabPlacement = tabPlacement;
        store.tabLayoutPolicy = tabLayoutPolicy;
        // Non-default values went through validation but bypass WARN at ctor
        // time — WARN policy lives in the setters, where explicit user intent
        // is unambiguous. Defaults match JDK so a vanilla ctor stays silent.
        installSelectionBridge(store);
    }

    private static void validateTabPlacement(int tabPlacement) {
        if (tabPlacement != SwingConstants.TOP
                && tabPlacement != SwingConstants.LEFT
                && tabPlacement != SwingConstants.BOTTOM
                && tabPlacement != SwingConstants.RIGHT) {
            throw new IllegalArgumentException(
                    "illegal tab placement: must be TOP, LEFT, BOTTOM, or RIGHT");
        }
    }

    private static void validateTabLayoutPolicy(int tabLayoutPolicy) {
        if (tabLayoutPolicy != WRAP_TAB_LAYOUT && tabLayoutPolicy != SCROLL_TAB_LAYOUT) {
            throw new IllegalArgumentException(
                    "illegal tab layout policy: must be WRAP_TAB_LAYOUT or SCROLL_TAB_LAYOUT");
        }
    }

    private TabbedPaneStateStore store() {
        return TabbedPaneStateStore.of(this);
    }

    // ---- Selection bridge -------------------------------------------

    /**
     * Wire the peer ↔ model two-way sync. Peer change → model write under
     * {@code preventPeerEvents}; model change → ChangeListener fan-out +
     * peer push iff peer disagrees (equality guard against the no-op
     * round-trip when model write was triggered by the peer event).
     */
    private void installSelectionBridge(TabbedPaneStateStore store) {
        // Peer → us: every TabSheet SelectedChangeEvent flows into the model.
        // preventPeerEvents short-circuits only when WE pushed to the peer
        // from the model side (modelListener) and the resulting peer-side
        // SelectedChangeEvent fires synchronously inside that push call;
        // user clicks + autoselect arrive with preventPeerEvents=false and
        // propagate into the model normally.
        addSelectedChangeListener(e -> {
            if (store.preventPeerEvents) return;
            SHelper.callSwing(() -> {
                // Drive model only. The modelListener (next) is the single
                // place that fires user ChangeListeners + handles peer push
                // (which the equality check below skips when peer already
                // matches the new model value, i.e. THIS peer-originated case).
                store.model.setSelectedIndex(getSelectedIndex());
            });
        });

        // Model → world: single fan-out point. Fires user ChangeListeners
        // unconditionally; pushes to peer iff peer disagrees (the equality
        // guard is what prevents the peer-originated round-trip from
        // re-entering super.setSelectedIndex). preventPeerEvents is OUR
        // guard around the push so the peer's resulting SelectedChangeEvent
        // doesn't double-fire user listeners.
        // Allowed by R_tolerate_off_ui_thread because callback from model: SingleSelectionModel ChangeListener
        store.modelListener = e -> SHelper.runOnOwnerUI(this, () -> {
            int target = store.model.getSelectedIndex();
            if (target != getSelectedIndex()) {
                store.preventPeerEvents = true;
                try {
                    super.setSelectedIndex(target);
                } finally {
                    store.preventPeerEvents = false;
                }
            }
            fireChangeListeners(store);
        });
        store.model.addChangeListener(store.modelListener);
    }

    private void fireChangeListeners(TabbedPaneStateStore store) {
        if (store.changeListeners.isEmpty()) return;
        ChangeEvent evt = new ChangeEvent(this);
        // Snapshot to tolerate add/remove during iteration (matches JDK
        // EventListenerList pattern).
        ChangeListener[] snap = store.changeListeners.toArray(new ChangeListener[0]);
        for (ChangeListener l : snap) l.stateChanged(evt);
    }

    public void addChangeListener(ChangeListener l) {
        if (l == null) return;
        store().changeListeners.add(l);
    }

    public void removeChangeListener(ChangeListener l) {
        store().changeListeners.remove(l);
    }

    public ChangeListener[] getChangeListeners() {
        return store().changeListeners.toArray(new ChangeListener[0]);
    }

    // ---- Model facade ------------------------------------------------

    public SingleSelectionModel getModel() {
        return store().model;
    }

    /**
     * Swap the selection model, as the JDK allows: unhooks the internal modelListener
     * from the old model, hooks it to the new one, and syncs the peer to the new model's
     * selection. The model is JDK-side machinery (a {@link javax.swing.SingleSelectionModel}),
     * so this is pure rewiring — no Vaadin counterpart is involved, which is why it is
     * implemented rather than drop-and-WARN (the R_vaadin_first model carve-out).
     */
    public void setModel(SingleSelectionModel model) {
        TabbedPaneStateStore store = store();
        if (store.model == model) return;
        if (store.model != null) store.model.removeChangeListener(store.modelListener);
        store.model = model;
        if (model != null) {
            model.addChangeListener(store.modelListener);
            // Adopt the new model's selection — the modelListener only fires on future
            // changes, so the handoff itself must sync the peer once.
            int target = model.getSelectedIndex();
            if (target >= 0 && target < getTabCount() && target != getSelectedIndex()) {
                store.preventPeerEvents = true;
                try {
                    super.setSelectedIndex(target);
                } finally {
                    store.preventPeerEvents = false;
                }
            }
        }
    }

    // ---- Selected-index / -component --------------------------------

    @Override
    public void setSelectedIndex(int index) {
        // JDK throws IOOBE for index out of [-1, tabCount). The probe
        // confirmed Vaadin TabSheet silently ignores out-of-range; we
        // validate explicitly to match JDK contract per R_match_swing_errors.
        if (index < -1 || index >= getTabCount()) {
            throw new IndexOutOfBoundsException(
                    "Index: " + index + ", Tab count: " + getTabCount());
        }
        // Drive through the model — model.setSelectedIndex fires
        // stateChanged, the modelListener handles peer push + listener fan-out.
        store().model.setSelectedIndex(index);
    }

    public Component getSelectedComponent() {
        int idx = getSelectedIndex();
        if (idx < 0 || idx >= getTabCount()) return null;
        return getComponentAt(idx);
    }

    public void setSelectedComponent(Component c) {
        int idx = indexOfComponent(c);
        if (idx == -1) {
            throw new IllegalArgumentException("component not found in tabbed pane");
        }
        setSelectedIndex(idx);
    }

    // ---- Tab placement / layout policy (headline shadows) -----------

    public int getTabPlacement() {
        return store().tabPlacement;
    }

    public void setTabPlacement(int tabPlacement) {
        validateTabPlacement(tabPlacement);
        TabbedPaneStateStore store = store();
        if (store.tabPlacement == tabPlacement) return;
        store.tabPlacement = tabPlacement;
        if (tabPlacement != SwingConstants.TOP) {
            // R_match_swing_errors sub-bucket (a) blocked-upstream: Vaadin TabSheet has no
            // placement knob. Value round-trips for emulator-side PCE; visual
            // stays top.
            SHelper.onUnimplemented(this, "setTabPlacement", tabPlacement);
        }
    }

    public int getTabLayoutPolicy() {
        return store().tabLayoutPolicy;
    }

    public void setTabLayoutPolicy(int tabLayoutPolicy) {
        validateTabLayoutPolicy(tabLayoutPolicy);
        TabbedPaneStateStore store = store();
        if (store.tabLayoutPolicy == tabLayoutPolicy) return;
        store.tabLayoutPolicy = tabLayoutPolicy;
        if (tabLayoutPolicy == SCROLL_TAB_LAYOUT) {
            // Silent: Vaadin already auto-scrolls on overflow. User intent met.
            SHelper.onNoop(this, "setTabLayoutPolicy(SCROLL_TAB_LAYOUT)");
        } else {
            // WRAP: R_match_swing_errors sub-bucket (a) blocked-upstream — Vaadin has no wrap mode.
            SHelper.onUnimplemented(this, "setTabLayoutPolicy(WRAP_TAB_LAYOUT)");
        }
    }

    // ---- Tab add / insert / remove ----------------------------------

    public void addTab(String title, Component component) {
        addTab(title, null, component, null);
    }

    public void addTab(String title, Icon icon, Component component) {
        addTab(title, icon, component, null);
    }

    public void addTab(String title, Icon icon, Component component, String tip) {
        insertTab(title, icon, component, tip, getTabCount());
    }

    public void insertTab(String title, Icon icon, Component component, String tip, int index) {
        if (index < 0 || index > getTabCount()) {
            // JDK throws IOOBE; Vaadin treats -1 as append (probe confirmed),
            // which we don't want leaking through. Validate per R_match_swing_errors.
            throw new IndexOutOfBoundsException(
                    "tab index out of range: " + index);
        }
        TabbedPaneStateStore store = store();
        Tab tab = new Tab();
        TabbedPaneStateStore.TabMeta meta = new TabbedPaneStateStore.TabMeta(title, icon, tip);
        store.tabMetas.add(index, meta);
        // Insert into peer; null content becomes empty Div to satisfy
        // Vaadin's non-null contract (probe-observed). super.add on first
        // tab will fire SelectedChangeEvent (autoselect) and propagate into
        // the model via the bridge — we don't preventPeerEvents here, since
        // the autoselect *should* drive user ChangeListeners + sync the model.
        Component content = component != null ? component : new com.vaadin.flow.component.html.Div();
        if (index == getTabCount()) {
            super.add(tab, content);
        } else {
            super.add(tab, content, index);
        }
        fillTabContent(content);
        installTabHeaderClickGuard(tab);
        renderTabHeader(index);
        if (tip != null) tab.setTooltipText(tip);
        syncModelFromPeer(store);
        // Only on the null-component branch, and carrying the *argument* index rather
        // than the position actually used — the JDK's shape exactly. A non-null
        // component fires nothing here (SD_reverse_fanout_rows).
        if (component == null) {
            firePropertyChange("indexForNullComponent", -1, index);
        }
    }

    /**
     * Swing fidelity: an interactive control (a JButton close-"x", an input,
     * a link) placed inside a custom tab header consumes its own click and
     * does NOT select the tab. Vaadin's vaadin-tabs, by contrast, selects a
     * tab on any click that bubbles up from within it — so clicking a close
     * button would select that tab first, and the listener's
     * {@code removeTabAt} would then remove the now-selected tab, shifting the
     * selection (closing a non-selected tab would move selection off the tab
     * the user was on). We stop the click at the tab before it reaches
     * vaadin-tabs' selection handler, but only when it originated from an
     * interactive descendant; plain label clicks still bubble through and
     * select the tab, matching Swing. vaadin-tabs listens in the bubble phase,
     * so stopping here (also bubble) is sufficient, and the control's own Flow
     * click listener has already fired by the time the event reaches the tab.
     */
    private void installTabHeaderClickGuard(Tab tab) {
        tab.getElement().executeJs(
                "this.addEventListener('click', function(e){"
              + " if (e.target.closest("
              + "'vaadin-button,button,input,textarea,select,a[href],[role=button]'))"
              + " e.stopPropagation();"
              + "});");
    }

    /**
     * Give every Vaadin {@link Button} in a custom tab header the {@code
     * tertiary-inline} theme so it renders compact in the tab strip (the
     * close-"x" pattern). The header is an app-supplied component (built by
     * code like {@code setTabComponentAt(i, panelWithCloseButton)}); we walk
     * its server-side {@code getChildren()} tree and add the theme name on
     * each Button. {@code ThemeList.add} is set-backed, so the re-render on
     * {@code setTabComponentAt} / {@code setTitleAt} is naturally idempotent.
     */
    /**
     * Make a tab's content fill the TabSheet content area, matching JDK
     * JTabbedPane where the selected component occupies the whole content
     * rectangle. Vaadin TabSheet's content part is {@code display:block},
     * so a content component otherwise sizes to its own intrinsic height
     * and leaves the lower part of the panel empty (and the part's
     * {@code overflow:auto} would scroll a too-tall child). {@code
     * height:100%} fills the part when the TabSheet has a definite height
     * (the common case once the surrounding layout gives it one) and
     * harmlessly collapses to content height when it doesn't — same
     * close-enough outcome as Swing, which only fills when the tabbed pane
     * itself was given space. Width fills on its own (block child).
     *
     * <p>{@code box-sizing:border-box} pairs with the {@code height:100%}:
     * a content panel with its own padding/border (e.g. a JPanel with an
     * EmptyBorder → CSS padding) is content-box by default, so {@code
     * height:100%} would add that padding on top of the full height and
     * overflow the content part by a few px — enough to trip its {@code
     * overflow:auto} into showing a scrollbar. border-box folds the
     * padding into the 100% so the panel fits the content area exactly.
     */
    private void fillTabContent(Component content) {
        content.getElement().getStyle().set("height", "100%");
        content.getElement().getStyle().set("box-sizing", "border-box");
    }

    private void inlineTabHeaderButtons(Component header) {
        if (header instanceof Button b) {
            b.getElement().getThemeList().add("tertiary-inline");
        }
        header.getChildren().forEach(this::inlineTabHeaderButtons);
    }

    public void removeTabAt(int index) {
        if (index < 0 || index >= getTabCount()) {
            throw new IndexOutOfBoundsException(
                    "tab index out of range: " + index);
        }
        TabbedPaneStateStore store = store();
        store.tabMetas.remove(index);
        // Vaadin's selection-shift after remove(int) matches JDK exactly
        // (probe-verified — middle/last/below/above/only-tab all align).
        // For shifts that change which Tab is selected, Vaadin fires
        // SelectedChangeEvent and the bridge syncs the model. For shifts
        // where the same Tab moves to a new index (remove(below-selected)),
        // Vaadin doesn't fire — selectedTab instance is unchanged. The
        // syncModelFromPeer call below catches that silent shift.
        if (getSelectedIndex() == index) {
            // Removing the selected tab moves Vaadin's selection onto the neighbour; when
            // that one is disabled Vaadin falls back to the removed tab and throws "Tab to
            // select must be a child". So deselect first, then select the JDK's target,
            // which Vaadin refuses if it is disabled, leaving no tab selected.
            store.preventPeerEvents = true;
            try {
                super.setSelectedIndex(-1);
                super.remove(index);
            } finally {
                store.preventPeerEvents = false;
            }
            int target = index < getTabCount() ? index : index - 1;
            if (target >= 0) {
                super.setSelectedIndex(target);
            }
        } else {
            super.remove(index);
        }
        syncModelFromPeer(store);
    }

    public void removeAll() {
        TabbedPaneStateStore store = store();
        // No bulk remove on TabSheet; loop from the end so indices stay
        // stable. Deselecting first keeps Vaadin from moving the selection
        // onto each neighbour in turn — a disabled one throws, see
        // removeTabAt — and leaves one model change, to -1, as the JDK's
        // removeAll makes.
        store.preventPeerEvents = true;
        try {
            super.setSelectedIndex(-1);
            for (int i = getTabCount() - 1; i >= 0; i--) {
                super.remove(i);
            }
        } finally {
            store.preventPeerEvents = false;
        }
        store.tabMetas.clear();
        syncModelFromPeer(store);
    }

    /**
     * After a structural mutation that may or may not have fired
     * SelectedChangeEvent (insert at the autoselect seam, remove with
     * index-shift but unchanged selectedTab), force the model to match
     * peer's current selectedIndex. Idempotent if the SelectedChangeEvent
     * bridge already synced: model.setSelectedIndex dedupes when value is
     * unchanged.
     */
    private void syncModelFromPeer(TabbedPaneStateStore store) {
        int peerIdx = getSelectedIndex();
        if (store.model.getSelectedIndex() != peerIdx) {
            store.model.setSelectedIndex(peerIdx);
        }
    }

    // ---- Per-tab title / icon / tooltip / enabled -------------------

    public String getTitleAt(int index) {
        return store().tabMetas.get(index).title;
    }

    public void setTitleAt(int index, String title) {
        TabbedPaneStateStore.TabMeta meta = store().tabMetas.get(index);
        String oldTitle = meta.title;
        meta.title = title;
        renderTabHeader(index);
        // Two JDK oddities reproduced deliberately. The event reports *which* tab
        // changed, not what to — a -1 sentinel old value and the index as the new —
        // so a listener has to re-read the pane. And the guard is `!=`, reference
        // inequality, so two equal-but-distinct Strings fire where two interned-equal
        // ones do not (SD_reverse_fanout_rows).
        if (oldTitle != title) {
            firePropertyChange("indexForTitle", -1, index);
        }
    }

    public Icon getIconAt(int index) {
        return store().tabMetas.get(index).icon;
    }

    public void setIconAt(int index, Icon icon) {
        TabbedPaneStateStore.TabMeta meta = store().tabMetas.get(index);
        meta.icon = icon;
        renderTabHeader(index);
    }

    public String getToolTipTextAt(int index) {
        return store().tabMetas.get(index).tooltip;
    }

    public void setToolTipTextAt(int index, String tip) {
        TabbedPaneStateStore.TabMeta meta = store().tabMetas.get(index);
        meta.tooltip = tip;
        Tab tab = getTabAt(index);
        tab.setTooltipText(tip);
    }

    public boolean isEnabledAt(int index) {
        return getTabAt(index).isEnabled();
    }

    public void setEnabledAt(int index, boolean enabled) {
        getTabAt(index).setEnabled(enabled);
    }

    public Component getTabComponentAt(int index) {
        return store().tabMetas.get(index).tabComponent;
    }

    public void setTabComponentAt(int index, Component tabComponent) {
        TabbedPaneStateStore.TabMeta meta = store().tabMetas.get(index);
        Component oldValue = meta.tabComponent;
        if (tabComponent == oldValue) return;      // the JDK's guard, also reference
        meta.tabComponent = tabComponent;
        renderTabHeader(index);
        firePropertyChange("indexForTabComponent", -1, index);   // same -1 sentinel shape
    }

    // ---- Index / component lookups ----------------------------------

    public int indexOfTab(String title) {
        TabbedPaneStateStore store = store();
        for (int i = 0; i < store.tabMetas.size(); i++) {
            String t = store.tabMetas.get(i).title;
            if (java.util.Objects.equals(t, title)) return i;
        }
        return -1;
    }

    public int indexOfComponent(Component component) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getComponentAt(i) == component) return i;
        }
        return -1;
    }

    public int indexOfTabComponent(Component tabComponent) {
        TabbedPaneStateStore store = store();
        for (int i = 0; i < store.tabMetas.size(); i++) {
            if (store.tabMetas.get(i).tabComponent == tabComponent) return i;
        }
        return -1;
    }

    public Component getComponentAt(int index) {
        Tab tab = getTabAt(index);
        return getComponent(tab);
    }

    public void setComponentAt(int index, Component component) {
        // Swap content under the existing Tab without touching metadata.
        // No direct TabSheet API for "replace at index" — remove + insert
        // would lose tab metadata + selection. We work through the tab/content
        // pair: remove the old content from the tab's slot, add the new.
        // Vaadin's TabSheet pairs Tab→Content internally; we replace by
        // removing the old content (which keeps the tab) and adding the new.
        if (component == null) {
            SHelper.onUnimplemented(this, "setComponentAt/null-component", index);
            return;
        }
        Tab tab = getTabAt(index);
        Component old = getComponent(tab);
        if (old == component) return;
        // Vaadin: there's no public TabSheet API to replace just the content
        // under a tab while preserving selection. The least-disruptive path
        // is the JDK approach: full re-add at index.
        TabbedPaneStateStore store = store();
        TabbedPaneStateStore.TabMeta meta = store.tabMetas.get(index);
        // The JDK keeps the selection and fires nothing. Vaadin moves it twice — off the
        // removed tab, then past the inserted one — so the peer events stay out of the model
        // and the selected index is put back afterwards.
        int selected = getSelectedIndex();
        store.preventPeerEvents = true;
        try {
            super.remove(index);
            // We removed at index; re-insert a fresh Tab + new content there.
            // meta stays — renderTabHeader will reattach title/icon/custom.
            Tab fresh = new Tab();
            if (index == getTabCount()) {
                super.add(fresh, component);
            } else {
                super.add(fresh, component, index);
            }
            if (getSelectedIndex() != selected) {
                super.setSelectedIndex(selected);
            }
        } finally {
            store.preventPeerEvents = false;
        }
        fillTabContent(component);
        renderTabHeader(index);
        if (meta.tooltip != null) getTabAt(index).setTooltipText(meta.tooltip);
        syncModelFromPeer(store);
    }

    // ---- Header render strategy (children-based, never setLabel) ----

    /**
     * Rebuild a Tab's visual children from its TabMeta. Always
     * children-based per SD_sjtabbedpane: {@code Tab.setLabel} would wipe any
     * previously-added icon child, so we never use it. Accessibility is
     * preserved through {@link Tab#setAriaLabel(String)} which sets the
     * ARIA label on the host element without affecting children.
     *
     * <p>Three render modes:
     * <ul>
     *   <li>{@code customComponent} non-null → custom owns header
     *       (single child); ariaLabel from {@code title} for a11y.</li>
     *   <li>{@code icon} + {@code title} → Icon component + Span(title).</li>
     *   <li>{@code title} only → Span(title).</li>
     *   <li>{@code icon} only → Icon component.</li>
     *   <li>both null → empty Tab; ariaLabel cleared.</li>
     * </ul>
     */
    private void renderTabHeader(int index) {
        Tab tab = getTabAt(index);
        TabbedPaneStateStore.TabMeta meta = store().tabMetas.get(index);
        tab.removeAll();
        if (meta.tabComponent != null) {
            tab.add(meta.tabComponent);
            // The tab header is the JTabbedPane's own real estate, so it owns
            // the styling of controls placed there. Buttons in a custom header
            // — the classic close-"x" affordance — would otherwise render at
            // full button size and crowd the tab strip; the tertiary-inline
            // theme makes them compact/borderless/inline. Applied here (not in
            // app code) so every close button gets it without each migrated
            // app reaching the Vaadin peer. Paired with the tertiary-inline
            // opt-out in the migration stylesheet, which keeps the Metal
            // button chrome from overriding the theme back to full size.
            inlineTabHeaderButtons(meta.tabComponent);
            // ariaLabel falls back to title (or empty) for screen readers
            // even when visual is custom.
            tab.setAriaLabel(meta.title != null ? meta.title : "");
            return;
        }
        Component iconComp = meta.icon != null
                ? Icons.toVaadinIconComponent(this, meta.icon) : null;
        boolean hasTitle = meta.title != null && !meta.title.isEmpty();
        if (iconComp != null) tab.add(iconComp);
        if (hasTitle) tab.add(new Span(meta.title));
        tab.setAriaLabel(meta.title != null ? meta.title : "");
    }

    // ---- Per-tab no-counterpart cluster (R_vaadin_first drop-and-WARN) ----------

    /**
     * No Vaadin counterpart for per-tab keyboard accelerator on the tab
     * header. R_vaadin_first drop-and-WARN; round-trip can lift to emulator field-
     * shadow if a real migration target needs it.
     */
    public void setMnemonicAt(int index, int mnemonic) {
        SHelper.onUnimplemented(this, "setMnemonicAt", index, mnemonic);
    }

    public int getMnemonicAt(int index) {
        return 0;
    }

    public void setDisplayedMnemonicIndexAt(int index, int mnemonicIndex) {
        SHelper.onUnimplemented(this, "setDisplayedMnemonicIndexAt", index, mnemonicIndex);
    }

    public int getDisplayedMnemonicIndexAt(int index) {
        return -1;
    }

    /**
     * Per-tab background colour. No Vaadin per-tab background knob
     * (theme-controlled); R_vaadin_first drop-and-WARN.
     */
    public void setBackgroundAt(int index, java.awt.Color background) {
        SHelper.onUnimplemented(this, "setBackgroundAt", index, background);
    }

    public java.awt.Color getBackgroundAt(int index) {
        return null;
    }

    public void setForegroundAt(int index, java.awt.Color foreground) {
        SHelper.onUnimplemented(this, "setForegroundAt", index, foreground);
    }

    public java.awt.Color getForegroundAt(int index) {
        return null;
    }

    public void setDisabledIconAt(int index, Icon disabledIcon) {
        SHelper.onUnimplemented(this, "setDisabledIconAt", index, disabledIcon);
    }

    public Icon getDisabledIconAt(int index) {
        return null;
    }

    // ---- Bounds-at / index-at (R_layouts_close_enough — no server-side coords) ----------

    public java.awt.Rectangle getBoundsAt(int index) {
        SHelper.onUnimplemented(this, "getBoundsAt", index);
        return null;
    }

    public int indexAtLocation(int x, int y) {
        SHelper.onUnimplemented(this, "indexAtLocation", x, y);
        return -1;
    }

    /** JDK always returns ≥1; we always have a single row of tabs (no WRAP). */
    public int getTabRunCount() {
        return getTabCount() == 0 ? 0 : 1;
    }

    // ---- L&F surface (drop-and-WARN per R_match_swing_errors sub-bucket (b)) ----------

    public String getUIClassID() {
        return "TabbedPaneUI";
    }

    public void updateUI() {
        // L&F swap — no-op for us; Vaadin owns the DOM. Same shape as
        // SJPanel.updateUI / SJSplitPane.updateUI.
    }

    public void setUI(javax.swing.plaf.TabbedPaneUI ui) {
        SHelper.onUnimplemented(this, "setUI", ui);
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
