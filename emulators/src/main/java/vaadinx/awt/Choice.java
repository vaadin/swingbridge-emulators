/*
 * Copyright (c) 1995, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.Choice
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator — third widget in the AWT lane after Button and
// Label, and the first of them that carries state worth adjusting: an item
// list plus a selection, with four selection-adjust quirks the JDK's own
// javadoc mis-describes. Rationale + the sizing of the rest of the AWT
// widget family: ideas/awt-widgets.md; decisions: D_awt_choice / SD_schoice.
//
// The Choice owns its state, as the JDK's does: pItems and selectedIndex are
// the JDK's fields and the mutators are its bodies, so no getter reads the
// peer (D_emulator_owned_state). The JDK pushes each change to
// its peer inside synchronized (this); this class flushes a snapshot after
// the monitor is released instead, since a push off the UI thread takes the
// session lock and a request thread holding that lock may be waiting on this
// monitor (getSelectedItem is synchronized). A structural change flushes the
// whole list (SChoice rebuilds it on every change anyway), a select only the
// selection.
//
// Browser -> AWT: a user's pick runs select(int) and then posts the
// ItemEvent, as XChoicePeer and LWChoicePeer do.

/**
 * Emulator for {@link java.awt.Choice} — the AWT 1.0 dropdown, not
 * {@link vaadinx.swing.JComboBox}. Holds the JDK's item vector and selection
 * itself, so every getter answers on any thread without reaching the
 * {@link com.vaadin.swingbridge.surrogates.SChoice} peer, which renders them:
 *
 * <pre>{@code
 * Choice units = new Choice();
 * units.add("metric");
 * units.add("imperial");                 // "metric" is selected — the first add selects it
 * units.addItemListener(e -> recompute((String) e.getItem()));
 * units.select("imperial");              // silent: no ItemEvent, exactly as in AWT
 * }</pre>
 *
 * <p>The JDK class is a {@code Vector<String>} and an {@code int}: no
 * model, no editable mode, no renderer, no popup API and no
 * {@code ActionListener}. Everything a migrator touches beyond the members
 * below — {@code setBounds}, {@code setFont}, {@code setEnabled},
 * {@code addFocusListener}, {@code requestFocus} — is inherited
 * {@link vaadinx.awt.Component}.
 *
 * <p>The JDK's internal call directions are kept (R_no_vaadin_in_api limb 2):
 * the modern names delegate to the deprecated ones, an insert or remove
 * re-selects through the public {@link #select(int)}, and
 * {@link #getSelectedItem} reads through the public {@link #getItem}, so an
 * override of any of them sees the same traffic it would on the desktop.
 *
 * <p>Leaf peer is locked down per R_leaf_peer_lockdown: {@code java.awt.Choice}
 * has no public subclass in {@code java.awt}, so there is no protected
 * {@code (Component peer)} ctor and every instance — including a user-code
 * subclass — peers over an {@code SChoice}.
 */
public class Choice extends vaadinx.awt.Component
        implements java.awt.ItemSelectable, javax.accessibility.Accessible {

    // The JDK's fields, under its names.
    private final java.util.Vector<java.lang.String> pItems = new java.util.Vector<>();
    private int selectedIndex = -1;

    /**
     * How many structural changes the thread holding this monitor is inside.
     * A {@link #select(int)} they make leaves the peer to their flush, which
     * must not run while the monitor is held.
     */
    private int mutating;

    /**
     * @throws java.awt.HeadlessException never in practice — the clause is kept
     *         for signature fidelity, matching {@link vaadinx.awt.Frame}
     */
    public Choice() throws java.awt.HeadlessException {
        // R_leaf_peer_lockdown lock-down: super(...) takes the SChoice directly, no peer seam.
        super(com.vaadin.swingbridge.surrogates.SChoice.class, com.vaadin.swingbridge.surrogates.SChoice::new);
        installPeerBridge();
    }

    private void installPeerBridge() {
        // Registered once the peer exists, which for a lazy peer is when a UI is current.
        withPeer(peer -> {
            surrogate().addValueChangeListener(e -> {
                // AWT posts nothing for a programmatic select, which is exactly
                // the !isFromClient case — so there is no echo to suppress.
                if (!e.isFromClient()) return;
                Integer index = e.getValue();
                // A browser-side clear has no AWT counterpart: java.awt.Choice
                // fires only SELECTED, never a deselection.
                if (index == null || index < 0 || index >= surrogate().getItemCount()) return;
                // The browser's item, read here on the request thread.
                java.lang.String item = surrogate().getItemAt(index);
                // R_callswing_envelope: the browser → AWT seam funnels through callSwing so a
                // listener that opens a modal dialog can park on the loom virtual
                // thread. Nested callSwing runs inline (D_callswing_loom).
                vaadinx.EHelper.callSwing(() -> {
                    // XChoicePeer's and LWChoicePeer's order: select, then post.
                    select(index);
                    // Enter at processEvent, not processItemEvent: AWT routes a
                    // peer-posted event dispatchEvent → processEvent →
                    // processItemEvent, so entering at the second hop would leave a
                    // migrator's processEvent override never running (R_no_vaadin_in_api limb 2).
                    processEvent(new java.awt.event.ItemEvent(this,
                            java.awt.event.ItemEvent.ITEM_STATE_CHANGED, item,
                            java.awt.event.ItemEvent.SELECTED));
                });
            });
        });
    }

    private com.vaadin.swingbridge.surrogates.SChoice surrogate() {
        return (com.vaadin.swingbridge.surrogates.SChoice) getPeer();
    }

    /**
     * Runs a structural change under this monitor, as the JDK does, then
     * flushes the result to the peer once the monitor is released — even when
     * the change threw part-way, so the peer never keeps a list the fields no
     * longer hold.
     */
    private void mutate(Runnable change) {
        try {
            synchronized (this) {
                mutating++;
                try {
                    change.run();
                } finally {
                    mutating--;
                }
            }
        } finally {
            flushItems();
        }
    }

    /** Pushes the items and the selection, read inside the push so racing writers leave the last state. */
    private void flushItems() {
        withPeer(p -> {
            java.util.List<java.lang.String> items;
            int selected;
            synchronized (this) {
                items = new java.util.ArrayList<>(pItems);
                selected = selectedIndex;
            }
            // A select(int) override that skips super can leave the index
            // pointing past a shrunk list, as in the JDK; the peer shows none.
            surrogate().setItemsAndSelection(items, selected < items.size() ? selected : -1);
        });
    }

    private void flushSelection() {
        withPeer(p -> {
            int selected;
            int size;
            synchronized (this) {
                selected = selectedIndex;
                size = pItems.size();
            }
            // A structural change still on its way carries the selection with it.
            if (surrogate().getItemCount() != size) return;
            surrogate().setValue(selected >= 0 && selected < size ? selected : null);
        });
    }

    @Override
    public void addNotify() {
        // JDK creates the native peer here. Ours is eternal and created in
        // the ctor, so there is nothing to allocate — but the override is
        // kept rather than dropped: user code overriding addNotify() and
        // calling super.addNotify() is a common AWT idiom, and Component's
        // implementation is what logs the displayable transition.
        super.addNotify();
    }

    // --- items --------------------------------------------------------

    /**
     * Delegates to {@link #countItems()}, not the other way round — that is
     * the JDK's direction, so a subclass overriding the deprecated name
     * stays on the invoked path (R_no_vaadin_in_api limb 2). Inverting it would leave such
     * an override silently dead.
     */
    public int getItemCount() {
        return countItems();
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getItemCount()} — but see its note: this is the implementation, not the wrapper */
    @Deprecated
    public int countItems() {
        return pItems.size();
    }

    /**
     * @throws ArrayIndexOutOfBoundsException when out of range, as AWT's
     *         backing {@code Vector} does (R_match_swing_errors)
     */
    public java.lang.String getItem(int index) {
        return getItemImpl(index);
    }

    /** Not overridable, as in the JDK, where the peer reads items through it. */
    private java.lang.String getItemImpl(int index) {
        return pItems.elementAt(index);
    }

    /**
     * Appends an item; if it is the first, it becomes selected.
     *
     * <p>Delegates to {@link #addItem(String)}, the JDK's direction — same
     * R_no_vaadin_in_api limb 2 reasoning as {@link #getItemCount()}. Note it does
     * <em>not</em> route through {@link #insert}: in the JDK both reach a
     * private helper, so an {@code insert} override does not intercept
     * {@code add}.
     *
     * @throws NullPointerException on a null item, as in AWT (R_match_swing_errors)
     */
    public void add(java.lang.String item) {
        addItem(item);
    }

    /** @deprecated as of Java 2 v1.1, use {@link #add(String)} — but this is the implementation, not the wrapper */
    @Deprecated
    public void addItem(java.lang.String item) {
        mutate(() -> insertNoInvalidate(item, pItems.size()));
    }

    /**
     * The JDK's shared insert body, bar the peer push {@link #mutate} makes.
     * Its selection rule is reproduced verbatim, quirk and all: the JDK
     * comments it as "no selection or selection shifted up" but re-selects
     * index 0, so inserting at or before the selection moves it to the top.
     */
    private void insertNoInvalidate(java.lang.String item, int index) {
        if (item == null) {
            throw new NullPointerException("cannot add null item to Choice");
        }
        pItems.insertElementAt(item, index);
        if (selectedIndex < 0 || selectedIndex >= index) {
            select(0);
        }
    }

    /**
     * Inserts at {@code index}, shifting the rest up.
     *
     * <p>Inserting at or before the selection re-selects index 0 —
     * the JDK comments this as "selection shifted up" but the code
     * does not shift. Reproduced verbatim in the peer.
     *
     * @param index clamped to the item count when too large, as in AWT
     * @throws IllegalArgumentException on a negative index, as in AWT (R_match_swing_errors)
     * @throws NullPointerException on a null item, as in AWT (R_match_swing_errors)
     */
    public void insert(java.lang.String item, int index) {
        mutate(() -> {
            if (index < 0) {
                throw new IllegalArgumentException("index less than zero.");
            }
            insertNoInvalidate(item, Math.min(index, pItems.size()));
        });
    }

    /**
     * Removes the first occurrence.
     *
     * @throws IllegalArgumentException when the item is not present, as in
     *         AWT (R_match_swing_errors) — unlike {@link #select(String)}, which is silent
     */
    public void remove(java.lang.String item) {
        // Not routed through remove(int): in the JDK both reach a private
        // helper, so a remove(int) override does not intercept this one.
        mutate(() -> {
            int index = pItems.indexOf(item);
            if (index < 0) {
                throw new IllegalArgumentException("item " + item + " not found in choice");
            }
            removeNoInvalidate(index);
        });
    }

    /**
     * @throws ArrayIndexOutOfBoundsException when out of range, as AWT's
     *         backing {@code Vector} does (R_match_swing_errors)
     */
    public void remove(int position) {
        mutate(() -> removeNoInvalidate(position));
    }

    /**
     * The JDK's shared remove body: emptying drops the selection to none,
     * removing the selected item re-selects index 0, and removing below it
     * re-selects one lower — each through the public {@link #select(int)}.
     */
    private void removeNoInvalidate(int position) {
        pItems.removeElementAt(position);
        if (pItems.size() == 0) {
            selectedIndex = -1;
        } else if (selectedIndex == position) {
            select(0);
        } else if (selectedIndex > position) {
            select(selectedIndex - 1);
        }
    }

    /** Empties the list; the selection becomes {@code -1}, assigned directly rather than through {@code select}. */
    public void removeAll() {
        mutate(() -> {
            pItems.removeAllElements();
            selectedIndex = -1;
        });
    }

    // --- selection ----------------------------------------------------

    /** @return null when nothing is selected, which for AWT means "no items" */
    public synchronized java.lang.String getSelectedItem() {
        return (selectedIndex >= 0) ? getItem(selectedIndex) : null;
    }

    /**
     * @return a length-1 array, or <b>null</b> — not an empty array — when
     *         nothing is selected. AWT's convention, the opposite of Swing's.
     */
    public synchronized java.lang.Object[] getSelectedObjects() {
        if (selectedIndex >= 0) {
            return new java.lang.Object[] {getItem(selectedIndex)};
        }
        return null;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Selects by position. <b>Fires no {@link java.awt.event.ItemEvent}</b> —
     * in AWT only user interaction does. An {@code ItemEvent} arriving at a
     * listener therefore always means "the user picked something", never
     * "the program changed the selection".
     *
     * @throws IllegalArgumentException when out of range, as in AWT (R_match_swing_errors)
     */
    public void select(int pos) {
        // The JDK declares this synchronized and pushes inside the monitor;
        // this pushes after it, for the reason in the file header.
        boolean nested;
        synchronized (this) {
            if ((pos >= pItems.size()) || (pos < 0)) {
                throw new IllegalArgumentException("illegal Choice item position: " + pos);
            }
            if (pItems.size() > 0) {
                selectedIndex = pos;
            }
            nested = mutating > 0;
        }
        if (!nested) {
            flushSelection();
        }
    }

    /**
     * Selects the first item equal to {@code str}, or does nothing when
     * there is no match — AWT is silent here, unlike {@link #remove(String)}.
     * Fires no {@code ItemEvent}, same as {@link #select(int)}.
     */
    public void select(java.lang.String str) {
        // Through this.select(int), as the JDK does — both are public, so a
        // subclass overriding select(int) intercepts select(String) too
        // (R_no_vaadin_in_api limb 2). Not synchronized, unlike the JDK's,
        // so the select(int) below never flushes under this monitor.
        int index = pItems.indexOf(str);
        if (index >= 0) {
            select(index);
        }
    }

    // --- ItemListener -------------------------------------------------

    public synchronized void addItemListener(java.awt.event.ItemListener l) {
        // AWT no-ops on null rather than throwing; EventListenerList would
        // store it and NPE at dispatch.
        if (l == null) return;
        listenerList.add(java.awt.event.ItemListener.class, l);
    }

    public synchronized void removeItemListener(java.awt.event.ItemListener l) {
        if (l == null) return;
        listenerList.remove(java.awt.event.ItemListener.class, l);
    }

    public synchronized java.awt.event.ItemListener[] getItemListeners() {
        return awtListeners(java.awt.event.ItemListener.class);
    }

    @Override
    protected void processEvent(java.awt.AWTEvent e) {
        // JDK's Choice.processEvent peels ItemEvent off before delegating
        // the rest to Component.processEvent. Mirrored so user code calling
        // processEvent directly from a subclass gets the AWT routing — and
        // so the peer bridge above lands on a migrator's override.
        if (e instanceof java.awt.event.ItemEvent ie) {
            processItemEvent(ie);
            return;
        }
        super.processEvent(e);
    }

    /**
     * The single funnel every selection reaches the listeners through —
     * both the peer bridge and a user-code {@code processEvent} call. AWT
     * subclasses override this (calling super) to intercept selections
     * wholesale, so it must stay the only dispatch path.
     */
    protected void processItemEvent(java.awt.event.ItemEvent e) {
        if (e == null) return;
        for (java.awt.event.ItemListener l
                : awtListeners(java.awt.event.ItemListener.class)) {
            l.itemStateChanged(e);
        }
    }

    @Override
    protected java.lang.String paramString() {
        // JDK's Choice.paramString appends ",current=<item>" to Component's
        // shape. Component.paramString returns "" here (see its comment),
        // so chaining up contributes nothing but keeps the JDK's structure.
        return super.paramString() + ",current=" + getSelectedItem();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("Choice", "getAccessibleContext");
        return null;
    }
}
