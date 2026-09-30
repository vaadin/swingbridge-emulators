/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.util.prefs;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import vaadinx.EHelper;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Per-UI server-side mirror of the browser's {@code localStorage} preferences
 * tree, plus the browser round-trip that warms it. One instance per Vaadin UI,
 * held via {@link ComponentUtil#setData(com.vaadin.flow.component.Component, Class, Object)}
 * — the same per-UI storage {@code WebClipboard} and {@code CurrentDrag} use.
 *
 * <p><b>Warm-once, read-synchronously.</b> The whole tree is pulled from
 * {@code localStorage} in a single {@code executeJs} round-trip on first
 * access ({@link #ensureLoaded()}), parking the calling virtual thread exactly
 * as a blocking {@code JOptionPane} does (see {@code WebClipboard.awaitJs} /
 * D_callswing_loom). After that every read is served synchronously from {@link #nodes}, and
 * every write updates {@link #nodes} and fires a fire-and-forget
 * {@code writeNode} back to {@code localStorage}. This is what lets
 * {@code java.util.prefs.Preferences}' synchronous API sit over an inherently
 * async browser store — see D_preferences.
 *
 * <p><b>First-read context contract (twin of {@code BrowserTimeZone} / SD_browser_timezone).</b>
 * The warm-up round-trip can only park on a virtual thread with a live UI, i.e.
 * inside {@code SwingUtilities.invokeLater} (the load-bearing wrapper the
 * migrated {@code mainUI()} already uses, D_jframe_as_route) or any peer event listener under
 * {@code EHelper.callSwing}. Reading a pref from a {@code static} initializer,
 * a {@code main()} body before {@code VaadinBoot.run()}, or outside the
 * {@code invokeLater} wrapper throws {@link IllegalStateException} with a
 * pointer at the fix — the exact cases the migration guide already lists for
 * {@code BrowserTimeZone.get()}. No new migration rule.
 *
 * <p><b>Multi-tab.</b> Each UI (browser tab) warms its own cache from the one
 * shared {@code localStorage}; a write in tab A is invisible to tab B's
 * already-warm cache (last-flush-wins). Cross-tab change events don't fire.
 * Folds into the project's existing "multi-tab unsupported" gap; the fix, if
 * ever tackled, is a {@code storage}-event bridge. See D_preferences.
 */
final class PrefsCache {

    /**
     * JVM-global test toggle. When {@code true}, {@link #ensureLoaded()} treats
     * the cache as warm-and-empty and all writes stay in-memory — no
     * {@code executeJs}, no virtual-thread requirement — so Karibu unit tests
     * exercise the full {@code Preferences} surface without a real browser.
     * Mirrors {@code WebClipboard.setTestMode}. A UI is still required (so the
     * off-context throw stays testable); only the browser hop is bypassed.
     */
    static volatile boolean testMode = false;

    /** nodePath (Preferences absolutePath, e.g. {@code "/com/example"}) → {key → value}. */
    private final Map<String, Map<String, String>> nodes = new LinkedHashMap<>();

    private boolean loaded = false;
    private boolean helperLoaded = false;

    /**
     * The warm-up round-trip in flight, if any: a second UI fiber warming meanwhile waits for this
     * one rather than issuing its own, whose stale answer would overwrite the first one's writes.
     */
    private CompletableFuture<JsonNode> loading;

    /** The cached root node for this UI, so repeated {@code userRoot()} calls reuse one tree. */
    private VaadinPreferences root;

    private PrefsCache() {}

    /**
     * The current UI's cache, created on first use. Reached only on the
     * app-scoped path — {@link VaadinPreferencesFactory#userRoot()} routes here
     * only when an {@link vaadinx.EmulatorContext} is active (D_prefs_scope_split); a caller
     * with no context lands in {@link JvmLocalPreferences} instead and never
     * gets here. Throws {@link IllegalStateException} when that app-scoped access
     * has no reachable UI (a pref read from a {@code static} initializer,
     * {@code main()} before boot, or a background {@code SwingWorker} off the UI
     * thread) — a programming error, not a soft gap. See D_prefs_first_read_context.
     */
    static PrefsCache current() {
        UI ui = UI.getCurrent();
        if (ui == null) {
            throw new IllegalStateException(
                    "java.util.prefs.Preferences accessed with no current Vaadin UI. "
                            + "Preferences are backed by the browser's localStorage; access them from a "
                            + "Vaadin UI context — inside SwingUtilities.invokeLater (where mainUI() builds "
                            + "the window), a peer event listener / ActionListener, or under UI.access(...). "
                            + "static initializers, static final fields, and main() before VaadinBoot.run() "
                            + "run too early. See D_preferences.");
        }
        PrefsCache cache = ComponentUtil.getData(ui, PrefsCache.class);
        if (cache == null) {
            cache = new PrefsCache();
            ComponentUtil.setData(ui, PrefsCache.class, cache);
        }
        return cache;
    }

    /** This UI's root preferences node, lazily built and reused. */
    VaadinPreferences root() {
        if (root == null) {
            root = new VaadinPreferences(this);
        }
        return root;
    }

    // ===========================================================
    // Warm-up
    // ===========================================================

    /**
     * Pull the whole preferences tree from {@code localStorage} into
     * {@link #nodes} on first call; a no-op thereafter. Every {@code *Spi}
     * operation calls this first so reads never see a half-populated tree and
     * writes never clobber unread keys.
     *
     * <p>{@link VaadinPreferencesFactory#userRoot()} calls it too, before any node is handed
     * out, so the round-trip never runs inside the {@code AbstractPreferences} lock an
     * {@code *Spi} call holds: a second UI fiber of the session reading a preference meanwhile
     * would keep the session lock while it waited for that monitor, and the browser's answer
     * could never get in.
     */
    void ensureLoaded() {
        if (loaded) return;
        if (testMode) {
            loaded = true;
            return;
        }
        UI ui = UI.getCurrent();
        if (ui == null) {
            // Should be unreachable — current() already required a UI — but a
            // node instance could outlive its UI if misused across tabs.
            throw new IllegalStateException(
                    "PrefsCache.ensureLoaded called with no current UI. See D_preferences.");
        }
        if (!Thread.currentThread().isVirtual()) {
            throw new IllegalStateException(
                    "java.util.prefs.Preferences first read parks on a browser round-trip and must "
                            + "run on a virtual thread. Read preferences inside SwingUtilities.invokeLater "
                            + "(where mainUI() builds the window) or a peer event listener under "
                            + "EHelper.callSwing — not on the plain Vaadin request thread. See D_preferences.");
        }
        if (loading == null) {
            if (!helperLoaded) {
                ui.getPage().addJavaScript("context://emul/prefs-helper.js");
                helperLoaded = true;
            }
            loading = new CompletableFuture<>();
            final CompletableFuture<JsonNode> future = loading;
            ui.getPage().executeJs("return window.emulPrefs.readAll()").then(JsonNode.class,
                    future::complete,
                    err -> future.completeExceptionally(new PendingJavaScriptResult.JavaScriptException(err)));
        }
        final JsonNode all;
        try {
            all = awaitJs(loading);
        } catch (RuntimeException e) {
            loading = null;  // a later read retries
            throw e;
        }
        if (!loaded) {
            parseInto(all);
            loaded = true;
        }
    }

    /** Decode the {@code readAll()} envelope ({@code {path: {key: value}}}) into {@link #nodes}. */
    private void parseInto(JsonNode all) {
        if (all == null || !all.isObject()) return;
        for (Map.Entry<String, JsonNode> nodeEntry : all.properties()) {
            JsonNode kv = nodeEntry.getValue();
            if (kv == null || !kv.isObject()) continue;
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> e : kv.properties()) {
                JsonNode v = e.getValue();
                if (v != null && !v.isNull()) map.put(e.getKey(), v.asString());
            }
            nodes.put(nodeEntry.getKey(), map);
        }
    }

    /**
     * Park the current UI fiber on the {@code executeJs} result, bridged through a plain
     * {@link CompletableFuture} as {@code WebClipboard.awaitJs} does, to sidestep Vaadin's
     * {@code DeadlockDetectingCompletableFuture} {@code hasLock()} refusal (D_clipboard_future_bridge).
     */
    private static JsonNode awaitJs(CompletableFuture<JsonNode> future) {
        try {
            return EHelper.awaitBrowserRoundTrip(UI.getCurrent(), future);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Preferences load was interrupted", ie);
        } catch (ExecutionException ee) {
            throw new IllegalStateException("Preferences load failed before the browser responded",
                    ee.getCause() != null ? ee.getCause() : ee);
        }
    }

    // ===========================================================
    // Backing-store ops (called from VaadinPreferences *Spi methods; each has
    // already invoked ensureLoaded()).
    // ===========================================================

    String get(String path, String key) {
        Map<String, String> map = nodes.get(path);
        return map == null ? null : map.get(key);
    }

    void put(String path, String key, String value) {
        nodes.computeIfAbsent(path, p -> new LinkedHashMap<>()).put(key, value);
        persist(path);
    }

    void remove(String path, String key) {
        Map<String, String> map = nodes.get(path);
        if (map != null) {
            map.remove(key);
            persist(path);
        }
    }

    String[] keys(String path) {
        Map<String, String> map = nodes.get(path);
        return map == null ? new String[0] : map.keySet().toArray(new String[0]);
    }

    /** Immediate child node names of {@code path}, derived from the flat node-path set. */
    String[] childrenNames(String path) {
        String prefix = path.equals("/") ? "/" : path + "/";
        java.util.LinkedHashSet<String> children = new java.util.LinkedHashSet<>();
        for (String p : nodes.keySet()) {
            if (p.startsWith(prefix) && p.length() > prefix.length()) {
                String rest = p.substring(prefix.length());
                int slash = rest.indexOf('/');
                children.add(slash < 0 ? rest : rest.substring(0, slash));
            }
        }
        return children.toArray(new String[0]);
    }

    void removeNode(String path) {
        nodes.remove(path);
        if (testMode) return;
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.getPage().executeJs("window.emulPrefs.removeNode($0)", path);
        }
    }

    /** Persist one node's current map back to {@code localStorage} (fire-and-forget). */
    private void persist(String path) {
        if (testMode) return;
        UI ui = UI.getCurrent();
        if (ui == null) return; // ensureLoaded already guaranteed a UI for the mutating call
        ui.getPage().executeJs("window.emulPrefs.writeNode($0, $1)",
                path, jsonEncode(nodes.getOrDefault(path, Map.of())));
    }

    /**
     * Minimal JSON-object encoder for a {@code String → String} map. Hand-rolled
     * rather than pulling {@code ObjectMapper} through here: the payload shape is
     * fixed and tiny, and we already only ever <i>read</i> JSON (via the Vaadin
     * {@link JsonNode} on warm-up), never needing a full serializer elsewhere.
     */
    private static String jsonEncode(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            escape(sb, e.getKey()).append(':');
            escape(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    private static StringBuilder escape(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"');
    }
}
