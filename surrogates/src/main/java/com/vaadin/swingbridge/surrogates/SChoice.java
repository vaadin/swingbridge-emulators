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

import com.vaadin.flow.component.select.Select;
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;

import javax.swing.event.EventListenerList;
import java.awt.ItemSelectable;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Surrogate for {@link java.awt.Choice} — the AWT 1.0 dropdown, not
 * {@link javax.swing.JComboBox}. Extends Vaadin {@link Select} directly
 * (is-a) and picks up the AWT {@code Component} API from
 * {@link ComponentMixin}.
 *
 * <pre>{@code
 * SChoice c = new SChoice();
 * c.addItem("Red");
 * c.addItem("Green");     // "Red" is already selected — the first add selects it
 * c.select("Green");      // silent: no ItemEvent, exactly as in AWT
 * }</pre>
 *
 * <h2>Why not a re-skin of {@link SJComboBox}</h2>
 *
 * {@code java.awt.Choice} is a {@code Vector<String>} plus an {@code int}.
 * It has no {@code ComboBoxModel}, no {@code ListDataListener}, no editable
 * mode, no {@code ComboBoxEditor}, no {@code PopupMenuListener}, no
 * {@code maximumRowCount}, no {@code ListCellRenderer} and no
 * {@code ActionListener} — reusing {@link SJComboBox} would graft an entire
 * MVC layer onto a class that has none of it. The host differs for the same
 * reason: {@link SJComboBox} needs Vaadin {@code ComboBox} for
 * {@code allowCustomValue} / filtering / {@code pageSize}, and giving
 * {@code Choice} that host would hand the migrator a text input the JDK
 * class does not have. See SD_schoice.
 *
 * <h2>Index identity (SD_sjlist's rule, second application)</h2>
 *
 * The type parameter is {@code Integer}: the peer's items are the
 * <em>indices</em> {@code 0..n-1}, with {@link #setItemLabelGenerator}
 * supplying the rendered text from {@link #items}. {@code Choice} permits
 * duplicate item Strings, and Vaadin's {@code KeyMapper} keys items in a
 * {@code HashMap} — equal Strings (usually the same interned instance)
 * would collapse to one key, which no {@code IdentifierProvider} could
 * separate. Indices make that unreachable rather than merely tolerated,
 * and they carry a bonus: the Vaadin value <em>is</em> the AWT concept, so
 * {@code selectedIndex} needs no storage at all.
 *
 * <h2>State</h2>
 *
 * Three plain fields, no {@code Store} class: the {@code List<String>} item
 * texts (R_vaadin_first-legitimate — with index identity the data provider holds
 * indices, so it cannot also hold the labels), the {@code preventPeerEvents}
 * guard, and an {@link EventListenerList} driving the {@code ItemListener}
 * fan-out. The {@code Store} classes exist for mixin-shared state and for
 * hosts whose Vaadin super-ctor invokes a subclass override before its
 * fields exist; {@code Select}'s ctor calls {@code setPresentationValue},
 * not {@code setValue}, so neither applies here.
 *
 * <p>The item-mutation family is named after {@link SJComboBox}
 * ({@code addItem} / {@code insertItemAt} / {@code removeItem} /
 * {@code removeItemAt} / {@code removeAllItems} /
 * {@code getItemAt}), not after AWT. {@code Choice.removeAll()}
 * and {@code Select.removeAll()} are an exact signature collision
 * with opposite meanings — Vaadin's removes the <em>non-item</em>
 * slotted children — so the AWT names live on the emulator
 * {@code vaadinx.awt.Choice}, which does not extend {@code Select}.
 * Note also that {@link #setLabel} here is Vaadin's floating field
 * caption, unlike {@link SButton#setLabel} which is AWT's button
 * caption.
 */
public class SChoice extends Select<Integer> implements ComponentMixin, ItemSelectable {

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
     * The item texts, indexed exactly as AWT's {@code pItems}. Source of
     * truth for every item read; the peer holds only the indices.
     */
    private final List<String> items = new ArrayList<>();

    /**
     * R_swing_is_truth feedback-loop guard. Set while we write to the peer — including
     * around the {@code setItems} rebuild, which fires a ValueChangeEvent of
     * its own (see {@link #pushItems}) — and read by the ValueChangeListener
     * before it enters the R_callswing_envelope envelope.
     */
    private boolean preventPeerEvents;

    private final EventListenerList listenerList = new EventListenerList();

    public SChoice() {
        super();
        _installSwingClass();
        // Index → rendered text. Defensive on the bounds because Select
        // re-renders items during its own reset() and a half-applied
        // rebuild must not blow up the render pass.
        setItemLabelGenerator(index ->
                index != null && index >= 0 && index < items.size() ? items.get(index) : "");
        // One Vaadin subscription drives the whole ItemListener fan-out
        // (R_vaadin_first's shared-subscription wiring shape).
        addValueChangeListener(e -> {
            // R_swing_is_truth's echo rule: read the guard in the listener body, BEFORE
            // entering callSwing. Our own write-through — and the value
            // clear that every setItems rebuild performs — is not peer→AWT
            // work at all, so it must never reach the R_callswing_envelope envelope.
            if (preventPeerEvents) return;
            Integer index = e.getValue();
            // A browser-side clear has no AWT counterpart: java.awt.Choice
            // fires only SELECTED, never a deselection.
            if (index == null || index < 0 || index >= items.size()) return;
            String item = items.get(index);
            // R_callswing_envelope: the browser → AWT seam funnels through callSwing so a
            // listener that opens a modal dialog can park on the loom
            // virtual thread.
            SHelper.callSwing(() -> processItemEvent(new ItemEvent(
                    this, ItemEvent.ITEM_STATE_CHANGED, item, ItemEvent.SELECTED)));
        });
    }

    // --- peer pushes --------------------------------------------------

    /**
     * Runs {@code body} with the R_swing_is_truth guard raised, restoring the previous
     * value rather than clearing it. Save-and-restore, not a flat
     * assignment: {@link #pushItems} nests — Vaadin's {@code reset()} calls
     * {@code clear()}, which re-enters our own {@code setValue} — and a
     * flat {@code finally preventPeerEvents = false} would drop the outer
     * guard halfway through the rebuild.
     */
    private void guarded(Runnable body) {
        boolean previous = preventPeerEvents;
        preventPeerEvents = true;
        try {
            body.run();
        } finally {
            preventPeerEvents = previous;
        }
    }

    /**
     * Rebuild the peer's items as the index range {@code 0..n-1}.
     *
     * <p>Vaadin's {@code Select.reset()} — which every {@code setItems}
     * funnels through — does {@code keyMapper.removeAll(); listBox.removeAll();
     * clear();}, so a rebuild <em>wipes the selection</em> and fires a
     * ValueChangeEvent. Hence the guard here rather than only around the
     * selection push, and hence every caller re-pushing the selection
     * afterwards. Whole-list rebuild per mutation is O(n) and free for a
     * dropdown — the same trade {@link SJComboBox#pushItemsToPeer} makes.
     */
    private void pushItems() {
        guarded(() -> setItems(IntStream.range(0, items.size()).boxed().toList()));
    }

    /**
     * Write the selection through to the Vaadin value.
     *
     * @param index null clears it; must otherwise already be present in the
     *        peer's items, so this always runs <em>after</em>
     *        {@link #pushItems} — Vaadin's {@code modelToPresentation}
     *        answers null for an item its {@code KeyMapper} has not seen yet
     */
    private void pushSelection(Integer index) {
        guarded(() -> super.setValue(index));
    }

    // --- items --------------------------------------------------------

    public int getItemCount() {
        return items.size();
    }

    /**
     * @throws ArrayIndexOutOfBoundsException as AWT's {@code Vector.elementAt}
     *         does — explicitly, because {@code ArrayList.get} raises the
     *         plain {@code IndexOutOfBoundsException} superclass and a
     *         migrator's {@code catch} would miss it (R_match_swing_errors)
     */
    public String getItemAt(int index) {
        if (index < 0 || index >= items.size()) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " + items.size());
        }
        return items.get(index);
    }

    /**
     * Appends an item. If it is the first, it becomes selected — AWT's
     * "a non-empty Choice always has a selection" invariant falls out of
     * the insert rule below.
     *
     * @throws NullPointerException on a null item, as in AWT (R_match_swing_errors)
     */
    public void addItem(String item) {
        insertItemImpl(item, items.size());
    }

    /**
     * @param index clamped to the item count when too large, as in AWT
     * @throws IllegalArgumentException on a negative index, as in AWT (R_match_swing_errors)
     * @throws NullPointerException on a null item, as in AWT (R_match_swing_errors)
     */
    public void insertItemAt(String item, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index less than zero.");
        }
        insertItemImpl(item, Math.min(index, items.size()));
    }

    /**
     * AWT's {@code insertNoInvalidate}, minus the peer-null branch.
     *
     * <p>The selection rule is reproduced verbatim, quirk and all: the JDK
     * comments it as "no selection or selection shifted up" but the code
     * does not shift — it re-selects index 0. Inserting at or before the
     * selection therefore moves the selection to the top of the list.
     */
    private void insertItemImpl(String item, int index) {
        if (item == null) {
            throw new NullPointerException("cannot add null item to Choice");
        }
        int previous = getSelectedIndex();
        items.add(index, item);
        pushItems();
        if (previous < 0 || previous >= index) {
            select(0);
        } else {
            // Untouched by the insert, but the rebuild cleared it.
            pushSelection(previous);
        }
    }

    /**
     * Removes the first occurrence.
     *
     * @throws IllegalArgumentException when the item is not present, as in
     *         AWT (R_match_swing_errors) — note AWT throws rather than no-opping here, unlike
     *         {@link #select(String)}
     */
    public void removeItem(String item) {
        int index = items.indexOf(item);
        if (index < 0) {
            throw new IllegalArgumentException("item " + item + " not found in choice");
        }
        removeItemImpl(index);
    }

    /**
     * @throws ArrayIndexOutOfBoundsException as AWT's
     *         {@code Vector.removeElementAt} does (R_match_swing_errors)
     */
    public void removeItemAt(int position) {
        if (position < 0 || position >= items.size()) {
            throw new ArrayIndexOutOfBoundsException(position + " >= " + items.size());
        }
        removeItemImpl(position);
    }

    /**
     * AWT's {@code removeNoInvalidate}: emptying drops the selection to
     * "none", removing the selected item re-selects index 0, and removing
     * below it decrements.
     */
    private void removeItemImpl(int position) {
        int previous = getSelectedIndex();
        items.remove(position);
        pushItems();
        if (items.isEmpty()) {
            pushSelection(null);
        } else if (previous == position) {
            select(0);
        } else if (previous > position) {
            select(previous - 1);
        } else if (previous >= 0) {
            // Below the removal point: same index, but the rebuild cleared it.
            pushSelection(previous);
        }
    }

    /** Empties the list; the selection becomes "none" ({@code -1}). */
    public void removeAllItems() {
        items.clear();
        pushItems();
        // AWT assigns selectedIndex = -1 directly rather than going through
        // select(). The rebuild already cleared the peer's value; the
        // explicit push keeps the push-items-then-push-selection order
        // uniform across every mutator.
        pushSelection(null);
    }

    /**
     * Replaces the items and the selection in one rebuild, applying none of
     * the insert / remove selection rules above. For a caller that keeps
     * AWT's item vector itself and flushes it here after each change — the
     * emulator {@code vaadinx.awt.Choice} does.
     *
     * @param texts copied; the caller keeps ownership
     * @param selectedIndex {@code -1} for none. AWT keeps a non-empty list
     *        selected, but a {@code select(int)} override that skips super
     *        can leave it unselected, so {@code -1} is accepted with items too
     * @throws IllegalArgumentException when {@code selectedIndex} is out of range
     * @throws NullPointerException on a null item
     */
    public void setItemsAndSelection(List<String> texts, int selectedIndex) {
        // A loop, not contains(null), which List.of's lists answer with an NPE of their own.
        for (String text : texts) {
            if (text == null) {
                throw new NullPointerException("cannot add null item to Choice");
            }
        }
        if (selectedIndex >= texts.size() || selectedIndex < -1) {
            throw new IllegalArgumentException("illegal Choice item position: " + selectedIndex);
        }
        items.clear();
        items.addAll(texts);
        pushItems();
        pushSelection(selectedIndex < 0 ? null : selectedIndex);
    }

    // --- selection ----------------------------------------------------

    /** @return -1 when there is no selection, which for AWT means "no items" */
    public int getSelectedIndex() {
        Integer value = getValue();
        return value == null ? -1 : value;
    }

    /** @return null when there is no selection */
    public String getSelectedItem() {
        int index = getSelectedIndex();
        return index >= 0 ? getItemAt(index) : null;
    }

    /**
     * @return a length-1 array, or <b>null</b> — not an empty array — when
     *         nothing is selected. {@link SJComboBox#getSelectedObjects}
     *         returns {@code new Object[0]} for the same method: AWT and
     *         Swing take opposite conventions and each is reproduced.
     */
    @Override
    public Object[] getSelectedObjects() {
        int index = getSelectedIndex();
        return index >= 0 ? new Object[] { getItemAt(index) } : null;
    }

    /**
     * Selects by position. <b>Fires no {@link ItemEvent}</b> — in AWT only
     * user interaction does, which is the sharpest divergence from
     * {@link SJComboBox} (JComboBox fires on both paths).
     *
     * @throws IllegalArgumentException when out of range, as in AWT (R_match_swing_errors)
     */
    public void select(int pos) {
        if (pos >= items.size() || pos < 0) {
            throw new IllegalArgumentException("illegal Choice item position: " + pos);
        }
        pushSelection(pos);
    }

    /**
     * Selects the first item equal to {@code str}, or does nothing when
     * there is no match — AWT is silent here, unlike {@link #removeItem}.
     * Fires no {@link ItemEvent}, same as {@link #select(int)}.
     */
    public void select(String str) {
        int index = items.indexOf(str);
        if (index >= 0) {
            select(index);
        }
    }

    /**
     * Converges with {@link #select(int)} so the fan-out doesn't depend on
     * which API the caller reached for.
     *
     * @param value null clears the selection; anything else must be a valid
     *        position, and throws exactly as {@code select} does
     */
    @Override
    public void setValue(Integer value) {
        if (value == null) {
            pushSelection(null);
            return;
        }
        select(value);
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
     * documented way for {@code java.awt.Choice} subclasses to intercept
     * every selection before its listeners see it, so it stays the single
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
    // one class. Same clash SButton documents. getItemListeners() covers the
    // only listener type this class owns, and the emulator vaadinx.awt.Choice
    // inherits a working getListeners from vaadinx.awt.Component — which is
    // where migrated code calls it anyway.

    // --- Overrides forced by "classes beat interfaces" ----------------

    /**
     * Vaadin {@code HasEnabled}'s concrete default shadows the mixin's
     * {@code setEnabled} (which carries SD_auto_pce's auto-PCE). Redirect
     * explicitly, same as {@link SButton}.
     */
    @Override
    public void setEnabled(boolean enabled) {
        ComponentMixin.super.setEnabled(enabled);
    }

    /**
     * Widens {@code Select.validate()} to public so the mixin's
     * {@code validate()} contract is satisfied — a class member beats an
     * interface default, and narrowing the visibility would not compile.
     * The {@link SJComboBox#validate} precedent.
     */
    @Override
    public void validate() {
        super.validate();
    }

    /** AWT's {@code Choice.paramString} tail: the selected item. */
    protected String paramString() {
        return "current=" + getSelectedItem();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
