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
import com.vaadin.flow.component.html.Div;

import java.awt.IllegalComponentStateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared content-pane / root-pane / menubar machinery for the
 * RootPaneContainer-shaped surrogates ({@link SJFrame}, {@link SJDialog},
 * {@link SJWindow}). Package-private on purpose — this is engine, not API;
 * migrators never see it.
 *
 * <h2>Why composition, not a base class</h2>
 *
 * SD_sjdialog anticipated pulling the scaffolding "up" once a third
 * Dialog-backed surrogate landed. A literal base-class pull-up into
 * {@link SWindow} would surface {@code getContentPane()} / content-pane
 * add-routing on <em>bare</em> SWindow / SFrame — but {@code java.awt.Window}
 * / {@code Frame} have no content pane (the indirection is JFrame's,
 * post-Java-5), and SFrame documents direct-add as its JDK-faithful
 * contract. Owning one scaffold per RootPaneContainer host keeps the
 * shared logic in one place while each host's public surface stays
 * exactly its JDK counterpart's. The hosts keep only one-line overrides;
 * the stateful idioms (bootstrap under a cleared checking flag, the
 * content-pane ↔ root-pane mirror invariant, menubar slot ordering) live
 * here once.
 *
 * <h2>What this class owns, and what {@link SJRootPane} owns</h2>
 *
 * The pane chain itself is the root pane's: host → {@link SJRootPane} →
 * layered pane → {menu bar, content pane}, the JDK's containment. This
 * scaffold owns exactly one structural step — planting the root pane in the
 * host — plus the two things a root pane cannot know about: the host-side
 * {@code emul-has-contentpane} class that makes the window body a flex column,
 * and the checking-flag routing below. Everything else delegates.
 *
 * <h2>The checking-flag idiom</h2>
 *
 * Matches AWT's {@code rootPaneCheckingEnabled}. The host's add / remove
 * overrides consult {@link #isChecking()}: when {@code true} (steady
 * state) they route into the {@link #contentPane}; when {@code false}
 * they fall through to Dialog's native add. {@link #ensureRootPane()} clears
 * the flag around its one {@code host.add(...)} so the host's override falls
 * through instead of recursing back into the pane it is trying to plant.
 *
 * <h2>Menubar slot</h2>
 *
 * Delegated to {@link SJRootPane#setJMenuBar}, which is where the JDK puts it.
 * SJWindow exposes no menubar API — {@code javax.swing.JWindow} has none — and
 * simply never calls it.
 *
 * <h2>A window is never content (D_window_split)</h2>
 *
 * An {@link SWindow} handed to a host's {@code add} nests directly under the
 * host element rather than routing into the content pane; {@code remove}
 * mirrors it. The emulator layer states the same rule by exception —
 * {@code vaadinx.awt.Container.add} throws AWT's own {@code "adding a window
 * to a container"} — so this is faithful containment, not a special case. It
 * is also what lets {@link SWindow#setVisible(boolean)} hand a window to
 * {@code UI.addToModalComponent}, whose {@code HasComponents.add} would
 * otherwise land it in the modal's content pane, where the next
 * {@code getContentPane().removeAll()} would detach it again.
 */
final class RootPaneScaffold {

    /**
     * The Dialog-backed surrogate this scaffold serves. Typed SWindow so
     * the flag-cleared bootstrap calls reach the host's public add
     * family (which falls through to Dialog's native add while
     * {@link #rootPaneCheckingEnabled} is cleared).
     */
    private final SWindow host;

    /**
     * The host's {@code createRootPane()} subclass hook, captured as a
     * supplier at construction so first-read lazy allocation dispatches
     * virtually to any user override — same override point JDK JFrame /
     * JDialog / JWindow expose.
     */
    private final Supplier<SJRootPane> rootPaneFactory;

    /**
     * Mirror of {@link SJRootPane#getContentPane()}, narrowed to {@link Div}
     * because that is what the hosts' JDK-shaped {@code getContentPane()}
     * returns. Lazy; {@link #ensureContentPane()} adopts the root pane's rather
     * than minting a second one.
     */
    private Div contentPane;

    /**
     * Null until first need — AWT's lazy-alloc contract. Once constructed,
     * stable for the host's lifetime.
     */
    private SJRootPane rootPane;

    /**
     * Matches AWT's {@code rootPaneCheckingEnabled}. See class javadoc
     * §"The checking-flag idiom".
     */
    private boolean rootPaneCheckingEnabled = true;

    RootPaneScaffold(SWindow host, Supplier<SJRootPane> rootPaneFactory) {
        this.host = host;
        this.rootPaneFactory = rootPaneFactory;
    }

    /** Consulted by the host's add / remove overrides. */
    boolean isChecking() {
        return rootPaneCheckingEnabled;
    }

    // ---- Content pane -----------------------------------------------

    /** Lazy-creates if never accessed. Never returns null. */
    Div getContentPane() {
        ensureContentPane();
        return contentPane;
    }

    /**
     * Swap the content pane. JDK contract: null throws
     * {@link IllegalComponentStateException}. Existing children
     * are NOT migrated (matches {@code JRootPane.setContentPane}) — user
     * code that wants to keep content must re-add to the new pane.
     *
     * <p>Mirror-into-rootPane invariant: any already-constructed
     * {@link SJRootPane} gets the same Div, so
     * {@code host.getContentPane() == host.getRootPane().getContentPane()}
     * stays true across swaps.
     */
    void setContentPane(Div newPane) {
        if (newPane == null) {
            throw new IllegalComponentStateException("contentPane cannot be set to null");
        }
        if (newPane == this.contentPane) return;

        Div old = this.contentPane;
        // The root pane does the detach-and-replant; no checking-flag scope is
        // needed because nothing here touches host.add.
        ensureRootPane();
        rootPane.setContentPane(newPane);
        SHelper.markContentPaneSpan(host, newPane, old);
        this.contentPane = newPane;
    }

    /**
     * Lazy bootstrap for {@link #contentPane}. <em>Adopts</em> the root pane's
     * content pane rather than minting one: a caller who reached
     * {@code getRootPane().getContentPane()} first has already caused one to
     * exist, and creating a second here would leave two panes in the layered
     * pane with only one of them routed to.
     */
    private void ensureContentPane() {
        if (contentPane != null) return;
        ensureRootPane();
        Component existing = rootPane.getContentPane();
        if (existing instanceof Div div) {
            contentPane = div;
        } else {
            // A subclass overrode createContentPane() with a non-Div. The hosts'
            // JDK-shaped getContentPane() is typed Div, so plant one we can
            // return and let the override's pane go.
            contentPane = new Div();
            rootPane.setContentPane(contentPane);
        }
        SHelper.markContentPaneSpan(host, contentPane, null);
    }

    /**
     * Plants the root pane as the host's direct child — this scaffold's one
     * structural step. Runs under a cleared checking flag so the host's
     * add-override falls through to Dialog's native add instead of recursing
     * into the content pane it is about to own.
     */
    private void ensureRootPane() {
        if (rootPane != null) return;
        rootPane = rootPaneFactory.get();
        withCheckingDisabled(() -> host.add(Collections.singletonList(rootPane)));
    }

    /**
     * Run {@code body} with the checking flag cleared, so a {@code host.add} /
     * {@code host.remove} inside it falls through the host's override to
     * Dialog's native add / remove instead of recursing back into the content
     * pane.
     */
    private void withCheckingDisabled(Runnable body) {
        boolean wasChecking = rootPaneCheckingEnabled;
        rootPaneCheckingEnabled = false;
        try {
            body.run();
        } finally {
            rootPaneCheckingEnabled = wasChecking;
        }
    }

    // ---- Routing bodies for the hosts' add / remove overrides --------
    //
    // The checking==true branch of each host override delegates here so
    // the ensure-then-touch and null-guard idioms stay single-copy. Each
    // splits SWindow children off first — see class javadoc §"A window is
    // never content".

    void addToContent(Collection<Component> components) {
        List<Component> windows = new ArrayList<>();
        List<Component> content = new ArrayList<>();
        partition(components, windows, content);
        if (!windows.isEmpty()) {
            withCheckingDisabled(() -> host.add(windows));
        }
        if (content.isEmpty()) return;
        ensureContentPane();
        contentPane.add(content);
    }

    /**
     * The index is dropped for a window: it names a slot in the content pane,
     * which is not where the window is going.
     */
    void addToContentAtIndex(int index, Component component) {
        if (component instanceof SWindow) {
            withCheckingDisabled(() -> host.add(Collections.singletonList(component)));
            return;
        }
        ensureContentPane();
        contentPane.addComponentAtIndex(index, component);
    }

    void removeFromContent(Component... components) {
        removeFromContent(Arrays.asList(components));
    }

    void removeFromContent(Collection<Component> components) {
        List<Component> windows = new ArrayList<>();
        List<Component> content = new ArrayList<>();
        partition(components, windows, content);
        if (!windows.isEmpty()) {
            withCheckingDisabled(() -> host.remove(windows));
        }
        if (contentPane == null || content.isEmpty()) return;
        contentPane.remove(content);
    }

    /** Split {@code components} into the window and non-window buckets, in order. */
    private static void partition(Collection<Component> components,
                                  List<Component> windows, List<Component> content) {
        for (Component c : components) {
            if (c instanceof SWindow) windows.add(c);
            else content.add(c);
        }
    }

    void removeAllFromContent() {
        if (contentPane == null) return;
        contentPane.removeAll();
    }

    // ---- Root pane ----------------------------------------------------

    /**
     * Lazy-constructs via the host's {@code createRootPane()} hook on first
     * read and plants it in the host. Everything below the root pane is the
     * root pane's own doing.
     */
    SJRootPane getRootPane() {
        ensureRootPane();
        return rootPane;
    }

    /**
     * The JDK's {@code JFrame.setRootPane} body: remove the outgoing root pane from the host,
     * then plant the incoming one, both under a cleared checking flag. The content pane is
     * re-read from the new root pane on next use, since it lives there.
     *
     * @param newRootPane {@code null} leaves the host without one, as the JDK allows
     */
    void setRootPane(SJRootPane newRootPane) {
        if (newRootPane == rootPane) return;
        SJRootPane old = rootPane;
        if (old != null) {
            withCheckingDisabled(() -> host.remove(Collections.singletonList(old)));
        }
        rootPane = newRootPane;
        contentPane = null;
        if (newRootPane != null) {
            withCheckingDisabled(() -> host.add(Collections.singletonList(newRootPane)));
        }
    }

    // ---- Glass pane (D_glasspane_structural / SD_glasspane_structural) --------------------------------------
    //
    // Delegated to SJRootPane, which plants it as its own first child — the
    // JDK's containment and index. The root pane's `position: relative` is the
    // containing block, which is what retired the `emul-has-glasspane`
    // ::part(content) rule: the positioned ancestor is now a light-DOM box the
    // Dialog and inline paths share.

    Component getGlassPane() {
        ensureRootPane();
        return rootPane.getGlassPane();
    }

    void setGlassPane(Component glass) {
        ensureRootPane();
        rootPane.setGlassPane(glass);
    }

    // ---- JMenuBar slot (SD_sjmenubar / D_menu_tree) -----------------------------------

    /**
     * Plant a menubar at index 0 of the root pane's layered pane, above the
     * content pane. Pass {@code null} to detach.
     */
    void setMenuBar(SJMenuBar bar) {
        ensureRootPane();
        rootPane.setJMenuBar(bar);
    }

    /** {@code null} until {@link #setMenuBar} plants one. */
    SJMenuBar getMenuBar() {
        ensureRootPane();
        return rootPane.getJMenuBar();
    }
}
