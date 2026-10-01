/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.List
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator — the AWT lane's scrolling list box. Two things here
// are unusual enough to state up front. First, almost every JDK method on
// this class delegates *to* its own deprecated AWT-1.0 alias rather than the
// other way round, so the delegation directions below look backwards on
// purpose (R_no_vaadin_in_api limb 2 — inverting one leaves a migrator's override of the
// deprecated name silently dead). Second, the peer is a native <select>, not
// a Vaadin component: rationale, the measured rendering table and the
// rejected Grid design are in D_awt_list / SD_slist.
//
// The List owns its state (D_emulator_owned_state): items,
// selected, multipleMode, rows and visibleIndex are the JDK's fields, so no
// getter reads the peer. The JDK itself keeps the selection in two places —
// its own `selected` array while the list has no peer, and the platform
// peer once it is displayable — and the two follow different rules (measured
// on JDK 25: a displayed list shifts its selection on insert and remove,
// keeps it sorted, and collapses it on a multi -> single flip; a peerless one
// does none of that). Both live here over the one `selected` field, which is
// exactly what the JDK's hand-over does: the peer is seeded from `selected`
// at addNotify and copied back into it at removeNotify. The peer-side rules
// are XListPeer's, together with its focusIndex, which decides what survives
// a multi -> single flip.
//
// Peer writes are flushed after this monitor is released rather than made
// inside it, as the JDK does: a push off the UI thread takes the session
// lock, and a request thread holding that lock may be waiting on this monitor.

/**
 * Emulator for {@link java.awt.List} — the AWT 1.0 scrolling list box, not
 * {@link vaadinx.swing.JList}. Holds the JDK's items and selection itself, so
 * every getter answers on any thread without reaching the
 * {@link com.vaadin.swingbridge.surrogates.SList} peer, which renders them:
 *
 * <pre>{@code
 * List planets = new List(4, false);
 * planets.add("Mercury");
 * planets.add("Venus");
 * planets.addItemListener(e -> show((Integer) e.getItem()));   // payload is the INDEX
 * planets.addActionListener(e -> open(e.getActionCommand()));  // double-click / Enter
 * planets.select(1);                                           // silent: no ItemEvent
 * }</pre>
 *
 * <h2>The selection follows two rule sets, as in the JDK</h2>
 *
 * Until the list is displayable — added to a frame that has been packed or
 * shown — the JDK's own bodies run: {@code select} appends to the selection
 * array, an insert or remove leaves the selected indexes where they were (so
 * they may now name other rows, or none), and flipping multi → single keeps
 * every selected index. Once it is displayable, the platform peer's rules
 * run: the selection shifts with inserts and removes, stays sorted in
 * multiple mode, and a multi → single flip keeps only the row that had the
 * location cursor. Both are reproduced; the peer's are {@code XListPeer}'s.
 *
 * <p>Leaf peer is locked down per R_leaf_peer_lockdown: no public class in {@code java.awt}
 * extends {@code java.awt.List}, so there is no protected
 * {@code (Component peer)} ctor and every instance — including a user-code
 * subclass — peers over an {@code SList}.
 *
 * <p>The two event payloads differ, and both trip people up.
 * {@code ItemEvent.getItem()} is an {@code Integer} <em>index</em>
 * (unlike {@link vaadinx.awt.Choice}, which posts the item String),
 * while {@code ActionEvent.getActionCommand()} is the item
 * <em>text</em>. And an {@code ItemEvent} always means "the user
 * picked something": every mutator and both selection methods are
 * silent, which is the inverse of {@link vaadinx.swing.JList}.
 */
public class List extends vaadinx.awt.Component
        implements java.awt.ItemSelectable, javax.accessibility.Accessible {

    // The JDK's fields, under its names.
    private java.util.Vector<java.lang.String> items = new java.util.Vector<>();

    /**
     * AWT's visible-row count. Verbatim, including the JDK's
     * {@code (rows != 0) ? rows : 4} substitution and negative values, which
     * is why it cannot be read back off the peer's floored {@code size}.
     */
    private final int rows;

    private boolean multipleMode;
    private int[] selected = new int[0];

    /** Last index passed to {@link #makeVisible}; {@code -1} until then. */
    private int visibleIndex = -1;

    /**
     * {@code XListPeer}'s location cursor: the row a programmatic select or a
     * user's click last landed on, and the one a multi → single flip keeps.
     * Meaningful only while displayable, as the peer's own is.
     */
    private int focusIndex;

    /** How many mutators the monitor-holding thread is inside; only the outermost flushes. */
    private int mutating;

    /** The widest flush the mutators in flight asked for; guarded by this monitor. */
    private int pendingFlush;

    private static final int FLUSH_SELECTION = 1;
    private static final int FLUSH_ALL = 2;

    /**
     * @throws java.awt.HeadlessException never in practice — the clause is kept
     *         for signature fidelity, matching {@link vaadinx.awt.Frame}
     */
    public List() throws java.awt.HeadlessException {
        this(0, false);
    }

    /** @throws java.awt.HeadlessException never in practice */
    public List(int rows) throws java.awt.HeadlessException {
        this(rows, false);
    }

    /**
     * @param rows {@code 0} means four; anything else is kept verbatim,
     *        negatives included, and there is no {@code setRows}
     * @throws java.awt.HeadlessException never in practice
     */
    public List(int rows, boolean multipleMode) throws java.awt.HeadlessException {
        // R_leaf_peer_lockdown lock-down: super(...) takes the SList directly, no peer seam.
        // The substitution happens before the peer is built so a half-built
        // List with the wrong row count is never observable.
        super(com.vaadin.swingbridge.surrogates.SList.class, () -> new com.vaadin.swingbridge.surrogates.SList(rows != 0 ? rows : DEFAULT_VISIBLE_ROWS, multipleMode));
        this.rows = rows != 0 ? rows : DEFAULT_VISIBLE_ROWS;
        this.multipleMode = multipleMode;
        installPeerBridge();
    }

    /** The JDK's package-private default; a list with zero rows is unusable. */
    static final int DEFAULT_VISIBLE_ROWS = 4;

    private void installPeerBridge() {
        // Registered once the peer exists, which for a lazy peer is when a UI is current.
        withPeer(peer -> {
            // Peer → AWT, both types. The surrogate is this list's platform peer:
            // it diffs the browser's selection into one ItemEvent per row, as
            // XListPeer posts one per click. The row's new state goes into
            // `selected` by the peer's rules, as XListPeer's own does, and the
            // event is re-sourced to `this` so migrated code casting
            // `(List) e.getItemSelectable()` sees the emulator. callSwing again
            // per R_callswing_envelope — nested calls run inline (D_callswing_loom).
            //
            // Enter at processEvent, not processItemEvent: AWT routes a
            // peer-posted event dispatchEvent → processEvent → process*Event, so
            // entering at the second hop would leave a migrator's processEvent
            // override compiling, looking wired, and never running (R_no_vaadin_in_api limb 2,
            // and the exact defect D_awt_dead_hooks swept out of this lane).
            surrogate().addItemListener(e -> vaadinx.EHelper.callSwing(() -> {
                int index = (Integer) e.getItem();
                synchronized (this) {
                    if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                        focusIndex = index;
                        peerSelectItem(index);
                    } else {
                        if (multipleMode) {
                            focusIndex = index;
                        }
                        peerDeselectItem(index);
                    }
                }
                processEvent(new java.awt.event.ItemEvent(
                        this, e.getID(), e.getItem(), e.getStateChange()));
            }));
            surrogate().addActionListener(e -> vaadinx.EHelper.callSwing(() ->
                    processEvent(new java.awt.event.ActionEvent(
                            this, e.getID(), e.getActionCommand(),
                            e.getWhen(), e.getModifiers()))));
        });
    }

    private com.vaadin.swingbridge.surrogates.SList surrogate() {
        return (com.vaadin.swingbridge.surrogates.SList) getPeer();
    }

    // --- flushing -------------------------------------------------------

    /**
     * Runs a mutator body under this monitor, as the JDK does, then — once the
     * outermost one returns and the monitor is released — flushes the result
     * to the peer. Flushes even when the body threw part-way, since the JDK's
     * {@code delItems} can remove rows and then throw.
     *
     * @param flush {@link #FLUSH_SELECTION} or {@link #FLUSH_ALL}
     */
    private void mutate(int flush, Runnable body) {
        try {
            synchronized (this) {
                mutating++;
                pendingFlush = Math.max(pendingFlush, flush);
                try {
                    body.run();
                } finally {
                    mutating--;
                }
            }
        } finally {
            int due;
            synchronized (this) {
                due = mutating == 0 ? pendingFlush : 0;
                if (due != 0) {
                    pendingFlush = 0;
                }
            }
            if (due != 0) {
                flush(due == FLUSH_ALL);
            }
        }
    }

    /** Pushes the current state, read inside the push so racing writers leave the last one. */
    private void flush(boolean withItems) {
        withPeer(p -> {
            java.util.List<java.lang.String> texts;
            int[] sel;
            boolean multi;
            synchronized (this) {
                texts = withItems ? new java.util.ArrayList<>(items) : null;
                sel = selected.clone();
                multi = multipleMode;
            }
            com.vaadin.swingbridge.surrogates.SList peer = surrogate();
            peer.setMultipleMode(multi);
            if (texts != null) {
                peer.setItemsAndSelection(texts, sel);
            } else {
                peer.setSelectedIndexes(sel);
            }
        });
    }

    // --- the platform peer's selection rules (XListPeer), used while displayable

    /** {@code XListPeer.selectItem}: single mode replaces the first entry, multiple mode inserts in order. */
    private void peerSelectItem(int index) {
        if (isSelectedInArray(index)) {
            return;
        }
        if (!multipleMode) {
            if (selected.length == 0) {
                selected = new int[] {index};
            } else {
                selected[0] = index;
            }
        } else {
            int[] newsel = new int[selected.length + 1];
            int i = 0;
            while (i < selected.length && index > selected[i]) {
                newsel[i] = selected[i];
                i++;
            }
            newsel[i] = index;
            System.arraycopy(selected, i, newsel, i + 1, selected.length - i);
            selected = newsel;
        }
    }

    /** {@code XListPeer.deselectItem}. */
    private void peerDeselectItem(int index) {
        if (!isSelectedInArray(index)) {
            return;
        }
        if (!multipleMode) {
            selected = new int[0];
        } else {
            removeFirst(index);
        }
    }

    private boolean isSelectedInArray(int index) {
        for (int i : selected) {
            if (i == index) return true;
        }
        return false;
    }

    /** Drops the first occurrence of {@code index}, if any; the arraycopy shape both JDK bodies use. */
    private boolean removeFirst(int index) {
        for (int i = 0; i < selected.length; i++) {
            if (selected[i] == index) {
                int[] newsel = new int[selected.length - 1];
                System.arraycopy(selected, 0, newsel, 0, i);
                System.arraycopy(selected, i + 1, newsel, i, selected.length - (i + 1));
                selected = newsel;
                return true;
            }
        }
        return false;
    }

    @Override
    public void addNotify() {
        // Where the JDK creates its peer, which seeds itself from this list:
        // the selection verbatim (the same field here) and the location
        // cursor on the last selected row, or row 0 (XListPeer.postInit).
        synchronized (this) {
            focusIndex = selected.length > 0 ? selected[selected.length - 1] : 0;
        }
        super.addNotify();
    }

    @Override
    public void removeNotify() {
        // JDK snapshots peer.getSelectedIndexes() into its own `selected`
        // field here, because the peer is about to go away and take the
        // selection with it. Ours is already that field, so there is nothing
        // to copy — the override stays for the super-calling idiom.
        super.removeNotify();
    }

    // --- items --------------------------------------------------------

    /**
     * Delegates to {@link #countItems()}, not the other way round — the JDK's
     * direction, so a subclass overriding the deprecated name stays on the
     * invoked path (R_no_vaadin_in_api limb 2). Every pair below runs the same way.
     */
    public int getItemCount() {
        return countItems();
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getItemCount()} — but this is the implementation, not the wrapper */
    @Deprecated
    public int countItems() {
        return items.size();
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
        return items.elementAt(index);
    }

    /** @return a fresh array; the receiver is untouched */
    public synchronized java.lang.String[] getItems() {
        java.lang.String[] itemCopies = new java.lang.String[items.size()];
        items.copyInto(itemCopies);
        return itemCopies;
    }

    public void add(java.lang.String item) {
        addItem(item);
    }

    /** @deprecated replaced by {@link #add(String)} — but this is on the invoked path */
    @Deprecated
    public void addItem(java.lang.String item) {
        addItem(item, -1);
    }

    /**
     * @param index anything outside {@code [0, getItemCount())} appends, with
     *        no exception — including {@code -1}, the documented "append"
     */
    public void add(java.lang.String item, int index) {
        addItem(item, index);
    }

    /**
     * The one insert body every {@code add} reaches, coercions included: an
     * out-of-range index appends and a null item becomes {@code ""}, so no
     * insert ever throws.
     *
     * @deprecated replaced by {@link #add(String, int)} — but this is the
     *             implementation, not the wrapper
     */
    @Deprecated
    public void addItem(java.lang.String item, int index) {
        mutate(FLUSH_ALL, () -> {
            int at = (index < -1 || index >= items.size()) ? -1 : index;
            java.lang.String text = (item == null) ? "" : item;
            if (at == -1) {
                items.addElement(text);
            } else {
                items.insertElementAt(text, at);
                if (isDisplayable()) {
                    // XListPeer.addItem: an insert shifts the selection up.
                    for (int j = 0; j < selected.length; j++) {
                        if (selected[j] >= at) {
                            selected[j] += 1;
                        }
                    }
                }
            }
        });
    }

    /**
     * Replaces the item at {@code index}.
     *
     * <p>The JDK body is literally {@code remove(index); add(newValue,
     * index);}, both public, so a subclass overriding either
     * intercepts this too. Kept as the two calls rather than a
     * direct peer replace, which would break that (R_no_vaadin_in_api limb 2). A
     * side effect falls out of it: on a displayed list the replaced row
     * loses its selection.
     *
     * @throws ArrayIndexOutOfBoundsException when out of range — raised by
     *         the {@code remove} half, before anything is added
     */
    public void replaceItem(java.lang.String newValue, int index) {
        mutate(FLUSH_ALL, () -> {
            remove(index);
            add(newValue, index);
        });
    }

    public void removeAll() {
        clear();
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #removeAll()} — but this is the implementation */
    @Deprecated
    public void clear() {
        mutate(FLUSH_ALL, () -> {
            items = new java.util.Vector<>();
            selected = new int[0];
            if (isDisplayable()) {
                focusIndex = -1; // XListPeer.clear
            }
        });
    }

    /**
     * Removes the first occurrence.
     *
     * @throws IllegalArgumentException when the item is not in the list, with
     *         the JDK's own message (R_match_swing_errors)
     */
    public void remove(java.lang.String item) {
        mutate(FLUSH_ALL, () -> {
            int index = items.indexOf(item);
            if (index < 0) {
                throw new IllegalArgumentException("item " + item + " not found in list");
            }
            // Through this.remove(int) — the JDK's own route, so a remove(int)
            // override intercepts the String form too.
            remove(index);
        });
    }

    /**
     * @throws ArrayIndexOutOfBoundsException when out of range, as AWT's
     *         {@code Vector.removeElementAt} does (R_match_swing_errors)
     */
    public void remove(int position) {
        delItem(position);
    }

    /** @deprecated replaced by {@link #remove(int)} — but this is on the invoked path */
    @Deprecated
    public void delItem(int position) {
        delItems(position, position);
    }

    /**
     * The one delete body every {@code remove} reaches.
     *
     * @throws ArrayIndexOutOfBoundsException when the range leaves the list —
     *         and note the JDK's loop runs <em>downwards</em> from
     *         {@code end}, so a negative {@code start} removes the higher
     *         indices first and only then throws, leaving the list shorter.
     *         Reproduced. An {@code end} past the top throws with nothing
     *         removed.
     * @deprecated as of JDK 1.1, and the JDK's own note says it is expected to
     *             become package-private — but it is public today and is the
     *             implementation, not the wrapper
     */
    @Deprecated
    public void delItems(int start, int end) {
        mutate(FLUSH_ALL, () -> {
            int before = items.size();
            for (int i = end; i >= start; i--) {
                items.removeElementAt(i);
            }
            if (isDisplayable()) {
                peerDelItems(start, end, before);
            }
        });
    }

    /**
     * {@code XListPeer.delItems}' selection and focus arithmetic, over the
     * peer's own item count — which, like the JDK's, it clamps and swaps
     * independently of the loop above.
     */
    private void peerDelItems(int s, int e, int peerSize) {
        if (peerSize == 0) {
            return;
        }
        if (s > e) {
            int tmp = s;
            s = e;
            e = tmp;
        }
        if (s < 0) {
            s = 0;
        }
        if (e >= peerSize) {
            e = peerSize - 1;
        }
        for (int i = s; i <= e; i++) {
            removeFirst(i);
        }
        int diff = (e - s) + 1;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i] > e) {
                selected[i] -= diff;
            }
        }
        if (focusIndex > e) {
            focusIndex -= diff;
        } else if (focusIndex >= s && focusIndex <= e) {
            int focusBound = (peerSize - diff > 0) ? 0 : -1;
            focusIndex = Math.max(s - 1, focusBound);
        }
    }

    // --- selection ----------------------------------------------------

    /**
     * @return the sole selected index, or {@code -1} when nothing <em>or more
     *         than one thing</em> is selected. Not "the first selected index"
     */
    public synchronized int getSelectedIndex() {
        int[] sel = getSelectedIndexes();
        return (sel.length == 1) ? sel[0] : -1;
    }

    /** @return a copy, and a zero-length array when nothing is selected */
    public synchronized int[] getSelectedIndexes() {
        return selected.clone();
    }

    /** @return null when nothing, or more than one thing, is selected */
    public synchronized java.lang.String getSelectedItem() {
        int index = getSelectedIndex();
        return (index < 0) ? null : getItem(index);
    }

    /** @return a zero-length array when nothing is selected */
    public synchronized java.lang.String[] getSelectedItems() {
        // Composed from getSelectedIndexes() + getItem(int), the JDK's body,
        // which keeps both overridable methods on the invoked path (R_no_vaadin_in_api limb 2).
        int[] sel = getSelectedIndexes();
        java.lang.String[] str = new java.lang.String[sel.length];
        for (int i = 0; i < sel.length; i++) {
            str[i] = getItem(sel[i]);
        }
        return str;
    }

    /**
     * @return an <b>empty array</b> when nothing is selected — {@code List}'s
     *         convention, and the opposite of {@link vaadinx.awt.Choice} and
     *         {@link vaadinx.awt.Checkbox}, which answer null
     */
    public java.lang.Object[] getSelectedObjects() {
        return getSelectedItems();
    }

    /**
     * Selects a row. <b>Fires no {@link java.awt.event.ItemEvent}</b> — the
     * JDK's javadoc says so outright. Out of range is documented as
     * unspecified; both of the JDK's branches store the index anyway, and so
     * does this, so {@code getSelectedIndex()} can answer a row that does not
     * exist.
     */
    public void select(int index) {
        mutate(FLUSH_SELECTION, () -> {
            if (isDisplayable()) {
                // XListPeer.select: a programmatic select moves the location cursor too.
                focusIndex = index;
                peerSelectItem(index);
                return;
            }
            boolean alreadySelected = false;
            for (int i : selected) {
                if (i == index) {
                    alreadySelected = true;
                    break;
                }
            }
            if (!alreadySelected) {
                if (!multipleMode) {
                    selected = new int[] {index};
                } else {
                    int[] newsel = new int[selected.length + 1];
                    System.arraycopy(selected, 0, newsel, 0, selected.length);
                    newsel[selected.length] = index;
                    selected = newsel;
                }
            }
        });
    }

    /**
     * Deselects a row; fires nothing. While displayable, in single mode the
     * call reaches the peer only when {@code index} is the sole selected row —
     * the JDK's own guard.
     */
    public void deselect(int index) {
        mutate(FLUSH_SELECTION, () -> {
            if (isDisplayable()) {
                if (isMultipleMode() || (getSelectedIndex() == index)) {
                    peerDeselectItem(index);
                }
            } else {
                removeFirst(index);
            }
        });
    }

    public boolean isIndexSelected(int index) {
        return isSelected(index);
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #isIndexSelected(int)} — but this is the implementation */
    @Deprecated
    public boolean isSelected(int index) {
        // Through getSelectedIndexes(), as the JDK does.
        for (int i : getSelectedIndexes()) {
            if (i == index) return true;
        }
        return false;
    }

    // --- rows, mode, scrolling ------------------------------------------

    /**
     * @return the constructor's value verbatim — {@code 4} for the zero
     *         default, and negatives unchanged. Never changes; there is no
     *         setter
     */
    public int getRows() {
        return rows;
    }

    public boolean isMultipleMode() {
        return allowsMultipleSelections();
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #isMultipleMode()} — but this is the implementation */
    @Deprecated
    public boolean allowsMultipleSelections() {
        return multipleMode;
    }

    /**
     * Flips multi-selection at runtime; fires nothing. Going multiple →
     * single, a displayed list keeps the row with the location cursor if it
     * is selected and drops the rest; a peerless one keeps them all.
     */
    public void setMultipleMode(boolean b) {
        setMultipleSelections(b);
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #setMultipleMode(boolean)} — but this is the implementation, and it holds the no-change guard */
    @Deprecated
    public void setMultipleSelections(boolean b) {
        mutate(FLUSH_SELECTION, () -> {
            if (b != multipleMode) {
                if (isDisplayable() && !b) {
                    // XListPeer.setMultipleSelections, which runs while its
                    // own flag is still the old one.
                    int selPos = isSelectedInArray(focusIndex) ? focusIndex : -1;
                    selected = new int[0];
                    if (selPos != -1) {
                        peerSelectItem(selPos);
                    }
                }
                multipleMode = b;
            }
        });
    }

    /** @return the last index passed to {@link #makeVisible}, or {@code -1} */
    public int getVisibleIndex() {
        return visibleIndex;
    }

    /** Scrolls the row into view. The index is recorded even when out of range, as in AWT. */
    public void makeVisible(int index) {
        synchronized (this) {
            visibleIndex = index;
        }
        withPeer(p -> surrogate().makeVisible(index));
    }

    // --- sizing ---------------------------------------------------------
    // The JDK answers these from peer.getPreferredSize(rows), i.e. from font
    // metrics we do not have. Routed to Component's own dimension instead of
    // an onUnimplemented WARN so a plain `new List(5)` keeps the exit gate
    // clean; the divergence is cosmetic and covered by R_layouts_close_enough.

    public java.awt.Dimension getPreferredSize(int rows) {
        return preferredSize(rows);
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getPreferredSize(int)} */
    @Deprecated
    public java.awt.Dimension preferredSize(int rows) {
        // super.getPreferredSize(), not super.preferredSize(): Component's
        // deprecated alias delegates *up* to getPreferredSize(), which this
        // class overrides to delegate back down — calling it here would
        // recurse until the stack blew. The JDK has no such cycle because its
        // Component.preferredSize() reads the field directly.
        return super.getPreferredSize();
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        return preferredSize();
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getPreferredSize()} */
    @Override
    @Deprecated
    public java.awt.Dimension preferredSize() {
        // The guard is the JDK's, and its else branch is not dead code: the
        // ctor substitutes 4 for a zero row count, so only a negative one
        // ever reaches it.
        return (rows > 0) ? preferredSize(rows) : super.getPreferredSize();
    }

    public java.awt.Dimension getMinimumSize(int rows) {
        return minimumSize(rows);
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getMinimumSize(int)} */
    @Deprecated
    public java.awt.Dimension minimumSize(int rows) {
        return super.getMinimumSize();
    }

    @Override
    public java.awt.Dimension getMinimumSize() {
        return minimumSize();
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getMinimumSize()} */
    @Override
    @Deprecated
    public java.awt.Dimension minimumSize() {
        return (rows > 0) ? minimumSize(rows) : super.getMinimumSize();
    }

    // --- listeners ------------------------------------------------------

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

    public synchronized void addActionListener(java.awt.event.ActionListener l) {
        if (l == null) return;
        listenerList.add(java.awt.event.ActionListener.class, l);
    }

    public synchronized void removeActionListener(java.awt.event.ActionListener l) {
        if (l == null) return;
        listenerList.remove(java.awt.event.ActionListener.class, l);
    }

    public synchronized java.awt.event.ActionListener[] getActionListeners() {
        return awtListeners(java.awt.event.ActionListener.class);
    }

    @Override
    protected void processEvent(java.awt.AWTEvent e) {
        // JDK's List.processEvent peels ItemEvent first, then ActionEvent,
        // then delegates. Order preserved so a subclass sees AWT's routing.
        if (e instanceof java.awt.event.ItemEvent ie) {
            processItemEvent(ie);
            return;
        }
        if (e instanceof java.awt.event.ActionEvent ae) {
            processActionEvent(ae);
            return;
        }
        super.processEvent(e);
    }

    /**
     * The single funnel every selection reaches its listeners through — both
     * the peer bridge and a user-code {@code processEvent} call. Overriding it
     * and calling super is AWT's documented interception idiom.
     */
    protected void processItemEvent(java.awt.event.ItemEvent e) {
        if (e == null) return;
        for (java.awt.event.ItemListener l
                : awtListeners(java.awt.event.ItemListener.class)) {
            l.itemStateChanged(e);
        }
    }

    /** The double-click / Enter twin of {@link #processItemEvent}. */
    protected void processActionEvent(java.awt.event.ActionEvent e) {
        if (e == null) return;
        for (java.awt.event.ActionListener l
                : awtListeners(java.awt.event.ActionListener.class)) {
            l.actionPerformed(e);
        }
    }

    @Override
    protected java.lang.String paramString() {
        // JDK appends ",selected=<item>" to Component's shape. Component's
        // returns "" here, so chaining contributes nothing but keeps the
        // structure — and routes through getSelectedItem(), hence getItem().
        return super.paramString() + ",selected=" + getSelectedItem();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("List", "getAccessibleContext");
        return null;
    }
}
