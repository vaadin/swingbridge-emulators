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

import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HtmlComponent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.dom.Element;
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;

import javax.swing.event.EventListenerList;
import java.awt.ItemSelectable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Surrogate for {@link java.awt.List} — the AWT 1.0 scrolling list box, not
 * {@link javax.swing.JList}. Hosts a native {@code <select>} directly
 * (is-a {@link HtmlComponent}) and picks up the AWT {@code Component} API
 * from {@link ComponentMixin}.
 *
 * <pre>{@code
 * SList l = new SList(4, false);
 * l.addItem("Mercury", -1);
 * l.addItem("Venus", -1);
 * l.select(1);                 // silent: no ItemEvent, exactly as in AWT
 * l.addItemListener(e -> log(e.getItem()));   // only the browser fires
 * }</pre>
 *
 * <h2>Why a native {@code <select>} and not {@code Grid}</h2>
 *
 * {@code java.awt.List} and an HTML {@code <select size=N>} are the same
 * widget — both 1995-era scrolling selection boxes — so most of what a
 * {@code Grid} peer would have to simulate, the element simply is. The
 * {@code size} attribute <em>is</em> AWT's {@code rows}; the {@code multiple}
 * attribute <em>is</em> the runtime {@link #setMultipleMode} flip, with no
 * component swap; the {@code <option>} children <em>are</em> the item store,
 * positionally identified, so duplicate item Strings need none of
 * {@link SJList}'s SD_sjlist index-identity machinery; and writing
 * {@code option.selected} fires no {@code change}, which is AWT's
 * documented "programmatic selection is silent" contract for free rather
 * than a rule to enforce. Rationale, the measured rendering table and the
 * rejected {@code Grid} / {@code VirtualList} / {@code ListBox} designs:
 * SD_slist.
 *
 * <p>The trade, taken deliberately: a native {@code <select multiple>} is
 * not themed by Aura. That is {@link SScrollbar}'s bargain (SD_sscrollbar) — an AWT
 * widget rendering as the browser's own control — and here it arguably
 * reads as fidelity rather than as a gap.
 *
 * <h2>{@code size} is floored at 2</h2>
 *
 * A {@code <select>} renders as a <em>drop-down</em> at {@code size=1}
 * without {@code multiple}, and as a list box otherwise (measured in Gecko;
 * the HTML spec says "list box control" when {@code multiple} is present or
 * {@code size > 1}). AWT's {@code rows} defaults to 4 but
 * {@code new List(1)} is legal, so {@link #applyRows} emits
 * {@code max(2, rows)}. This is a <em>rendering</em> floor only — the
 * emulator's {@code getRows()} returns the JDK value verbatim, negatives
 * included, because {@code rows} never round-trips through the attribute.
 *
 * <h2>State</h2>
 *
 * Items, {@code multipleMode} and {@code rows} are all DOM state, so none of
 * them is a field here. The one mirror is {@link #selected}: the browser
 * owns the selection and the server cannot read it synchronously, so the
 * {@code change} bridge writes it and every programmatic
 * {@link #select}/{@link #deselect} writes it through alongside the DOM.
 * That is an R_swing_is_truth write-through, not an R_vaadin_first shadow cache — and notably it needs
 * <em>no</em> {@code preventPeerEvents} guard, because our own write fires
 * no {@code change}, so the feedback loop the guard exists to break cannot
 * form.
 *
 * <p>{@code removeAll()} is named {@link #removeAllItems()} here, the
 * {@link SJComboBox#removeAllItems} convention, so nothing reads as
 * the {@code HasComponents} container clear to a stage-3 eye; the
 * emulator {@code vaadinx.awt.List} carries AWT's {@code removeAll}
 * and {@code clear} names. AWT's generic
 * {@code getListeners(Class)} is absent for the SD_sbutton erasure clash
 * against Vaadin {@code Component}'s.
 */
@Tag("select")
public class SList extends HtmlComponent
        implements ComponentMixin, Focusable<SList>, ItemSelectable {

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
     * The JS expression whose value carries the browser's selection back on
     * every {@code change}. Joined to a String on purpose: the payload stays
     * a plain scalar, so nothing depends on how an array-valued expression
     * marshals. Also the key the value arrives under in
     * {@link com.vaadin.flow.dom.DomEvent#getEventData()}.
     */
    private static final String SELECTED_INDICES =
            "[...event.target.selectedOptions].map(o=>o.index).join(',')";

    /** Carries the double-clicked row; {@code event.target} is the {@code <option>}. */
    private static final String TARGET_INDEX = "event.target.index";

    /**
     * Mirror of the browser's selection, ascending. Not an R_vaadin_first shadow: the
     * DOM is authoritative and this is the only readable copy server-side.
     * Ascending order is the X11 peer's and what
     * {@code getSelectedIndexes()}' javadoc implies — and it is what
     * {@code selectedOptions} reports regardless of the order rows were
     * clicked in.
     */
    private final SortedSet<Integer> selected = new TreeSet<>();

    private final EventListenerList listenerList = new EventListenerList();

    public SList() {
        this(0, false);
    }

    public SList(int rows) {
        this(rows, false);
    }

    /**
     * @param rows AWT's visible-row count, already through the JDK's
     *        {@code (rows != 0) ? rows : 4} substitution by the caller;
     *        emitted as {@code size=max(2, rows)}
     */
    public SList(int rows, boolean multipleMode) {
        super();
        _installSwingClass();
        applyRows(rows);
        applyMultipleMode(multipleMode);
        installChangeBridge();
        installActionBridges();
        clearBrowserSelection();
    }

    // --- peer configuration -------------------------------------------

    private void applyRows(int rows) {
        getElement().setAttribute("size", String.valueOf(Math.max(2, rows)));
    }

    /**
     * Written as the <em>attribute</em>, not the property: the browser applies
     * its "selectedness setting algorithm" as the {@code <option>} children
     * arrive, and a {@code <select>} that is not yet {@code multiple} at that
     * moment
     * auto-selects the first one. A property update lands too late to prevent
     * it. See {@link #clearBrowserSelection}, which cleans up the rest.
     */
    private void applyMultipleMode(boolean multipleMode) {
        if (multipleMode) {
            getElement().setAttribute("multiple", "");
        } else {
            getElement().removeAttribute("multiple");
        }
    }

    /**
     * Forces the element to show no selection whenever ours is empty.
     *
     * <p>Not belt-and-braces. HTML requires a <em>single</em>-select
     * {@code <select>} to keep exactly one option selected, so the browser
     * auto-selects index 0 the moment the first {@code <option>} is inserted —
     * and a fresh {@code java.awt.List} has no selection at all. Without this,
     * the very first render shows row 0 highlighted while
     * {@code getSelectedIndex()} correctly answers {@code -1}: a server/browser
     * disagreement that every browserless test passes straight through.
     *
     * <p>Via {@code executeJs} rather than {@code setProperty}, because Vaadin
     * suppresses a property write whose server-side value is unchanged — and
     * after a user selection the server still holds the {@code -1} it last
     * wrote, so the update that actually matters would be the one dropped.
     */
    private void clearBrowserSelection() {
        if (!selected.isEmpty()) return;
        getElement().executeJs("this.selectedIndex = -1");
    }

    // --- browser → AWT bridges ----------------------------------------

    /**
     * The one selection source. A DOM {@code change} exists only because a
     * user committed a selection, so there is no {@code isFromClient()}-style
     * filter to apply and no echo of our own writes to suppress — the
     * "programmatic mutation is silent" contract is structural here.
     *
     * <p>Emits one {@link ItemEvent} per index that entered or left the
     * selection, payload {@code Integer} (AWT's {@code XListPeer} posts the
     * index, not the item String — unlike {@code Checkbox}, which posts its
     * label, and {@code Choice}, which posts its item).
     */
    private void installChangeBridge() {
        getElement().addEventListener("change", event -> {
            SortedSet<Integer> now = parseIndices(
                    readEventString(event, SELECTED_INDICES));
            List<ItemEvent> pending = new ArrayList<>();
            for (Integer index : now) {
                if (!selected.contains(index)) {
                    pending.add(itemEvent(index, ItemEvent.SELECTED));
                }
            }
            for (Integer index : selected) {
                if (!now.contains(index)) {
                    pending.add(itemEvent(index, ItemEvent.DESELECTED));
                }
            }
            selected.clear();
            selected.addAll(now);
            if (pending.isEmpty()) return;
            // R_callswing_envelope: one envelope around the whole fan-out, so a listener that
            // opens a modal dialog parks the loom virtual thread once rather
            // than once per index.
            SHelper.callSwing(() -> pending.forEach(this::processItemEvent));
        }).addEventData(SELECTED_INDICES);
    }

    /**
     * {@code ActionEvent} on double-click and on Enter, {@code actionCommand}
     * = the item text.
     *
     * <p>AWT's double-click gate is
     * {@code currentIndex >= 0 && clickCount >= 2 && clickCount % 2 == 0};
     * a {@code dblclick} whose target is an {@code <option>} satisfies the
     * first two limbs by construction, and the alternate-click half of the
     * third is a backward-compatibility detail of the X11 peer that no
     * browser reproduces. Enter's payload should be the <em>focused</em> row
     * rather than the selected one; a native {@code <select>} exposes no
     * focus index, so it reports the selection instead — the two differ only
     * in MULTI mode (R_match_swing_errors sub-bucket (c)).
     */
    private void installActionBridges() {
        getElement().addEventListener("dblclick", event -> {
            int index = readEventInt(event, TARGET_INDEX);
            if (index < 0 || index >= getItemCount()) return;
            fireActionPerformed(index);
        }).addEventData(TARGET_INDEX);

        getElement().addEventListener("keydown", event -> {
            if (selected.isEmpty()) return;
            fireActionPerformed(selected.first());
        }).setFilter("event.key === 'Enter'");
    }

    private void fireActionPerformed(int index) {
        String command = getItemAt(index);
        SHelper.callSwing(() -> processActionEvent(new ActionEvent(
                this, ActionEvent.ACTION_PERFORMED, command)));
    }

    private ItemEvent itemEvent(int index, int stateChange) {
        return new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED,
                Integer.valueOf(index), stateChange);
    }

    private static String readEventString(com.vaadin.flow.dom.DomEvent event, String key) {
        tools.jackson.databind.JsonNode data = event.getEventData();
        if (data == null || !data.has(key)) return "";
        return data.get(key).asString("");
    }

    private static int readEventInt(com.vaadin.flow.dom.DomEvent event, String key) {
        tools.jackson.databind.JsonNode data = event.getEventData();
        if (data == null || !data.has(key)) return -1;
        return data.get(key).asInt(-1);
    }

    /** {@code "0,2,3"} → {0,2,3}; {@code ""} → empty. Unparseable entries are dropped. */
    private static SortedSet<Integer> parseIndices(String raw) {
        SortedSet<Integer> out = new TreeSet<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(",")) {
            try {
                out.add(Integer.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // A malformed payload must not take down the event bridge.
            }
        }
        return out;
    }

    // --- items (the <option> children are the store) --------------------

    public int getItemCount() {
        return getElement().getChildCount();
    }

    /**
     * @throws ArrayIndexOutOfBoundsException as AWT's {@code Vector.elementAt}
     *         does — explicitly, because the plain
     *         {@code IndexOutOfBoundsException} superclass would slip past a
     *         migrator's {@code catch} (R_match_swing_errors)
     */
    public String getItemAt(int index) {
        checkElementIndex(index);
        return getElement().getChild(index).getText();
    }

    public String[] getItemsArray() {
        int count = getItemCount();
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            out[i] = getElement().getChild(i).getText();
        }
        return out;
    }

    /**
     * AWT's {@code addItem(String, int)} — the one insert body, coercions
     * included: an out-of-range index appends and a null item becomes
     * {@code ""}. Never throws.
     *
     * @param index {@code -1}, or anything outside {@code [0, count)}, appends
     */
    public void addItem(String item, int index) {
        int count = getItemCount();
        int at = (index < -1 || index >= count) ? -1 : index;
        String text = item == null ? "" : item;
        Element option = new Element("option");
        option.setText(text);
        if (at == -1) {
            getElement().appendChild(option);
        } else {
            getElement().insertChild(at, option);
            shiftSelectionForInsert(at);
        }
        clearBrowserSelection();
    }

    /**
     * AWT's {@code delItems(start, end)}, quirk and all: the loop runs
     * downwards from {@code end}, so a negative {@code start} removes the
     * higher indices and <em>then</em> throws, leaving the list shorter. An
     * {@code end} past the top throws on the first removal with nothing
     * removed.
     *
     * @throws ArrayIndexOutOfBoundsException exactly where AWT's
     *         {@code Vector.removeElementAt} would (R_match_swing_errors)
     */
    public void removeItemRange(int start, int end) {
        for (int i = end; i >= start; i--) {
            checkElementIndex(i);
            getElement().removeChild(i);
            shiftSelectionForRemove(i);
        }
        clearBrowserSelection();
    }

    /**
     * Replaces the items and the selection, applying none of the insert /
     * remove selection rules above. For a caller that keeps AWT's item vector
     * and selection itself and flushes them here after each change — the
     * emulator {@code vaadinx.awt.List} does. Only the rows that differ are
     * touched (the common prefix and suffix stay), so a caller flushing after
     * every single {@code add} costs one {@code <option>} per call.
     *
     * @param texts copied; a null entry renders as {@code ""}, as AWT stores it
     * @param selectedIndexes see {@link #setSelectedIndexes}
     */
    public void setItemsAndSelection(List<String> texts, int[] selectedIndexes) {
        int oldCount = getItemCount();
        int newCount = texts.size();
        int prefix = 0;
        while (prefix < oldCount && prefix < newCount
                && getElement().getChild(prefix).getText().equals(textOf(texts.get(prefix)))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < oldCount - prefix && suffix < newCount - prefix
                && getElement().getChild(oldCount - 1 - suffix).getText()
                        .equals(textOf(texts.get(newCount - 1 - suffix)))) {
            suffix++;
        }
        for (int i = oldCount - suffix - 1; i >= prefix; i--) {
            getElement().removeChild(i);
        }
        for (int i = prefix; i < newCount - suffix; i++) {
            Element option = new Element("option");
            option.setText(textOf(texts.get(i)));
            getElement().insertChild(i, option);
        }
        setSelectedIndexes(selectedIndexes);
    }

    private static String textOf(String item) {
        return item == null ? "" : item;
    }

    /**
     * Shows exactly this selection, applying none of the selection rules
     * above. Fires no {@link ItemEvent}.
     *
     * @param selectedIndexes in any order, duplicates allowed; entries outside
     *        the rows are skipped, since nothing can render them. In SINGLE
     *        mode only the first rendered entry shows, since a single-select
     *        {@code <select>} holds one — the caller may keep more (AWT's own
     *        selection array can, after a multi → single flip while peerless)
     */
    public void setSelectedIndexes(int[] selectedIndexes) {
        int count = getItemCount();
        SortedSet<Integer> next = new TreeSet<>();
        for (int index : selectedIndexes) {
            if (index >= 0 && index < count) {
                next.add(index);
                if (!isMultipleMode()) break;
            }
        }
        for (int i = 0; i < count; i++) {
            Element option = getElement().getChild(i);
            boolean want = next.contains(i);
            if (option.getProperty("selected", false) != want) {
                option.setProperty("selected", want);
            }
        }
        // The server-side properties can lag the browser, whose own changes
        // only reach the mirror; when the browser differs, set it outright.
        if (!next.equals(selected)) {
            StringBuilder csv = new StringBuilder();
            for (Integer index : next) {
                csv.append(csv.isEmpty() ? "" : ",").append(index);
            }
            getElement().executeJs(
                    "const s=new Set($0.split(',').filter(x=>x).map(Number));"
                            + "for(const o of this.options)o.selected=s.has(o.index);"
                            + "if(!s.size)this.selectedIndex=-1;",
                    csv.toString());
        }
        selected.clear();
        selected.addAll(next);
    }

    /** Empties the list and the selection. */
    public void removeAllItems() {
        getElement().removeAllChildren();
        selected.clear();
    }

    /** The index of the first item equal to {@code item}, or {@code -1}. */
    public int indexOfItem(String item) {
        int count = getItemCount();
        for (int i = 0; i < count; i++) {
            if (java.util.Objects.equals(getElement().getChild(i).getText(), item)) {
                return i;
            }
        }
        return -1;
    }

    private void checkElementIndex(int index) {
        int count = getItemCount();
        if (index < 0 || index >= count) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " + count);
        }
    }

    // --- selection ------------------------------------------------------

    /**
     * Selects a row. <b>Fires no {@link ItemEvent}</b> — AWT's javadoc says
     * so outright, and here it is the platform's behaviour rather than
     * something suppressed. Out of range is a no-op: AWT documents
     * out-of-range as "unspecified behavior" and its peer path silently does
     * nothing.
     */
    public void select(int index) {
        if (index < 0 || index >= getItemCount()) return;
        if (!isMultipleMode()) {
            for (Integer previous : new TreeSet<>(selected)) {
                if (previous != index) {
                    setOptionSelected(previous, false);
                }
            }
            selected.clear();
        }
        setOptionSelected(index, true);
        selected.add(index);
    }

    /**
     * Deselects a row, reproducing AWT's guard rather than only its outcome:
     * in SINGLE mode the call reaches the peer only when {@code index} is the
     * sole selected row. Fires no {@link ItemEvent}.
     */
    public void deselect(int index) {
        if (index < 0 || index >= getItemCount()) return;
        if (!isMultipleMode() && getSelectedIndex() != index) return;
        setOptionSelected(index, false);
        selected.remove(index);
        clearBrowserSelection();
    }

    private void setOptionSelected(int index, boolean value) {
        if (index < 0 || index >= getItemCount()) return;
        getElement().getChild(index).setProperty("selected", value);
    }

    /** Ascending, and a fresh array each call — never the live mirror. */
    public int[] getSelectedIndexes() {
        return selected.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * @return the sole selected index, or {@code -1} when nothing <em>or
     *         more than one thing</em> is selected. Not "the first selected
     *         index" — AWT's body is {@code (sel.length == 1) ? sel[0] : -1}
     */
    public int getSelectedIndex() {
        return selected.size() == 1 ? selected.first() : -1;
    }

    /** @return null when nothing, or more than one thing, is selected */
    public String getSelectedItem() {
        int index = getSelectedIndex();
        return index < 0 ? null : getItemAt(index);
    }

    public String[] getSelectedItemsArray() {
        int[] indexes = getSelectedIndexes();
        String[] out = new String[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            out[i] = getItemAt(indexes[i]);
        }
        return out;
    }

    /**
     * @return an <b>empty array</b> when nothing is selected — AWT's
     *         {@code List} convention, and the opposite of
     *         {@link SChoice#getSelectedObjects}, which answers null
     */
    @Override
    public Object[] getSelectedObjects() {
        return getSelectedItemsArray();
    }

    public boolean isIndexSelected(int index) {
        return selected.contains(index);
    }

    /**
     * AWT never fixes up its own {@code selected} array on a remove and
     * leans on the peer to do it; we do the peer's job. Every index above
     * the removed one shifts down, and the removed one drops out.
     */
    private void shiftSelectionForRemove(int removed) {
        SortedSet<Integer> next = new TreeSet<>();
        for (Integer index : selected) {
            if (index == removed) continue;
            next.add(index > removed ? index - 1 : index);
        }
        selected.clear();
        selected.addAll(next);
    }

    private void shiftSelectionForInsert(int inserted) {
        SortedSet<Integer> next = new TreeSet<>();
        for (Integer index : selected) {
            next.add(index >= inserted ? index + 1 : index);
        }
        selected.clear();
        selected.addAll(next);
    }

    // --- mode -------------------------------------------------------------

    /**
     * Read straight off the peer — no shadow, the round-trip is lossless.
     * Reads the <em>attribute</em> because {@link #applyMultipleMode} writes
     * one; Vaadin's property map and its attribute map are separate, so
     * {@code getProperty("multiple", …)} would answer false forever.
     */
    public boolean isMultipleMode() {
        return getElement().hasAttribute("multiple");
    }

    /**
     * Flips the {@code multiple} attribute live. Fires no {@link ItemEvent}.
     *
     * <p>Going multiple → single, the browser keeps at most one option
     * selected (measured in Gecko: the first). AWT's rule is "the item with
     * the location cursor stays, else all are deselected" and there is no
     * location cursor here, so the mirror is trimmed to match what the
     * browser will have done — a third outcome inside the same R_best_effort_behaviour latitude,
     * and closer to AWT than dropping the selection wholesale.
     */
    public void setMultipleMode(boolean multipleMode) {
        if (multipleMode == isMultipleMode()) return;
        applyMultipleMode(multipleMode);
        if (!multipleMode && selected.size() > 1) {
            Integer keep = selected.first();
            for (Integer index : new TreeSet<>(selected)) {
                if (!index.equals(keep)) {
                    setOptionSelected(index, false);
                }
            }
            selected.clear();
            selected.add(keep);
        }
    }

    /** {@code scrollIntoView} on the row; no read-back, so no getter here. */
    public void makeVisible(int index) {
        if (index < 0 || index >= getItemCount()) return;
        getElement().executeJs("this.options[$0]?.scrollIntoView({block:'nearest'})", index);
    }

    // --- listener fan-out --------------------------------------------------

    @Override
    public void addItemListener(ItemListener l) {
        // AWT silently ignores a null listener; EventListenerList would store
        // it and NPE at dispatch.
        if (l == null) return;
        listenerList.add(ItemListener.class, l);
    }

    @Override
    public void removeItemListener(ItemListener l) {
        if (l == null) return;
        listenerList.remove(ItemListener.class, l);
    }

    public ItemListener[] getItemListeners() {
        return SHelper.awtOrder(listenerList, ItemListener.class);
    }

    public void addActionListener(ActionListener l) {
        if (l == null) return;
        listenerList.add(ActionListener.class, l);
    }

    public void removeActionListener(ActionListener l) {
        if (l == null) return;
        listenerList.remove(ActionListener.class, l);
    }

    public ActionListener[] getActionListeners() {
        return SHelper.awtOrder(listenerList, ActionListener.class);
    }

    /**
     * AWT's dispatch hook, and the single funnel every browser selection
     * reaches the listeners through. Overriding it and calling super is the
     * documented interception idiom for {@code java.awt.List} subclasses.
     */
    protected void processItemEvent(ItemEvent e) {
        if (e == null) return;
        for (ItemListener l : getItemListeners()) {
            l.itemStateChanged(e);
        }
    }

    /** The {@code ActionEvent} twin of {@link #processItemEvent}. */
    protected void processActionEvent(ActionEvent e) {
        if (e == null) return;
        for (ActionListener l : getActionListeners()) {
            l.actionPerformed(e);
        }
    }

    /** AWT's {@code List.paramString} tail. */
    protected String paramString() {
        return "selected=" + getSelectedItem();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
