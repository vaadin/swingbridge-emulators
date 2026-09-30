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

import com.vaadin.flow.component.richtexteditor.RichTextEditor;
import com.vaadin.swingbridge.surrogates.internal.HtmlCharset;
import com.vaadin.swingbridge.surrogates.internal.RteCssRules;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;

/**
 * Surrogate for {@link javax.swing.JEditorPane} — the single, unconditional
 * peer behind {@code vaadinx.swing.JEditorPane} (SD_sjeditorpane_rte). Extends Vaadin
 * {@link RichTextEditor} directly (is-a), so one peer covers read-only display,
 * editable HTML, and a runtime view↔edit toggle:
 *
 * <pre>{@code
 * SJEditorPane pane = new SJEditorPane("text/html", "<p>hi</p>");
 * pane.setEditable(false);        // read-only: hides the RTE toolbar
 * pane.setEditable(true);         // back to editing — no peer swap
 * String html = pane.getText();   // reads RTE's htmlValue back
 * }</pre>
 *
 * HTML round-trips through RTE's {@code setValue}/{@code getValue} (HTML is the
 * Flow default — no {@code asDelta()}); {@code editable} maps to
 * {@code setReadOnly(!editable)}. Replaces the deleted read-only {@code Div}
 * peer (SD_sjeditorpane_div).
 *
 * <h2>Lossy HTML subset — display <em>and</em> edit (R_vaadin_first)</h2>
 *
 * RTE accepts only {@code p, br, h1–h6, strong/em/u/s, a, ol/ul/li} (no
 * nesting), {@code blockquote, pre, img} (base64 data-URLs only — no web-URL
 * images); {@code <b>}→{@code <strong>}, unknown tags→{@code <p>}. <b>No tables,
 * no arbitrary CSS.</b> Because RTE is the only peer, this hits read-only
 * <em>display</em> too: a report with tables loses them — the accepted
 * regression from the deleted {@code Div} (SD_sjeditorpane_rte). RTE's subset normalization
 * doubles as a sanitizer, so it is safer for user-supplied HTML than the
 * {@code Div}'s raw {@code innerHTML} was. A migrator needing faithful static
 * HTML drops to a Vaadin {@code Html}/{@code Div} in that one view.
 *
 * <h2>Content type</h2>
 *
 * Only {@code "text/html"} is honored — RTE speaks HTML. {@code "text/plain"} /
 * {@code "text/rtf"} field-round-trip but drop-and-WARN (no EditorKit pipeline;
 * {@code paintComponent} is permanently OOS per R_match_swing_errors sub-bucket (b)). Default is
 * the JDK's {@code "text/plain"}, but {@code setText} writes markup either way
 * (R_layouts_close_enough close-enough — typical use sets {@code "text/html"} first).
 *
 * <h2>{@code setPage(URL)} — synchronous fetch</h2>
 *
 * {@link #setPage(URL)} opens the URL via {@link URLConnection}, reads the bytes
 * synchronously, decodes them using {@link com.vaadin.swingbridge.surrogates.internal.HtmlCharset}
 * (Content-Type charset → {@code <meta>} declaration → UTF-8), forces
 * {@code contentType="text/html"}, writes through {@link #setText}, and fires the
 * {@code "page"} PCE. Relative links in the loaded page resolve against it — see
 * {@link #fireHyperlinkActivated}.
 *
 * <p>Accepted divergences (R_layouts_close_enough): the fetch is server-side (it resolves from the
 * Vaadin app's network reach, not the browser's), skips EditorKit content-type
 * dispatch, and is synchronous-only (blocks the request thread — fine for
 * small/local fetches). A non-HTML media type and a URL fragment each WARN: the
 * content is rendered as HTML regardless, and there is no {@code scrollToReference}
 * counterpart.
 *
 * <p><b>Deliberately unlike the JDK: every call re-fetches.</b> The JDK skips the
 * load when the URL already equals the displayed one, and forcing a reload there
 * means clearing the document's stream-description property — a document-model
 * hook that does not exist here. Copying the skip without the escape hatch would
 * leave a "reload this page" button permanently dead, so re-fetching is the
 * best-effort choice (R_best_effort_behaviour): both the ordinary case and the reload case work.
 *
 * <h2>Hyperlink activation — read-only panes only</h2>
 *
 * {@link #addHyperlinkListener} delivers {@code ACTIVATED} when the user clicks a
 * link in a pane that is <em>not</em> editable, matching Swing, where
 * {@code HTMLEditorKit.LinkController} activates a link only on a non-editable
 * pane (in an editor a click positions the caret). A delegated click listener in
 * the RTE's shadow root reports the {@code href}; see
 * {@link #HYPERLINK_HOOK_JS} for why the listener cannot sit on the host element.
 * Hover ({@code ENTERED} / {@code EXITED}) is not wired.
 *
 * <h2>Author CSS for the rendered content</h2>
 *
 * {@link #addCssRule} styles the pane's content — the peer half of
 * {@code HTMLEditorKit}'s {@code StyleSheet.addRule}. The stylesheet is injected
 * into this instance's shadow root, so it reaches content RTE renders there while
 * staying confined to this pane. Appearance only: structure Quill's model refuses
 * (tables) is gone before any rule could apply.
 *
 * <h2>What drops-and-WARNs (R_vaadin_first)</h2>
 *
 * {@code setContentType} for non-HTML types; at-rules passed to
 * {@link #addCssRule}. {@code setEditorKit} / {@code setDocument} etc. live on the
 * emulator and never reach here.
 */
public class SJEditorPane extends RichTextEditor implements JComponentMixin, EnterClaims.Claimant {

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

    // The content types javax.swing.JEditorPane registers a kit for out of the
    // box (JEditorPane.loadDefaultKitsIfNecessary). Anything else resolves to the
    // plain kit, which is why setContentType answers "text/plain" for it.
    private static final java.util.Set<String> KIT_CONTENT_TYPES =
            java.util.Set.of("text/plain", "text/html", "text/rtf", "application/rtf");

    // ---- Swing-side state (source of truth, R_swing_is_truth) ----

    private String contentType = "text/plain";

    // Last-set page URL — null until setPage is called. Round-trips through
    // getPage; the JDK contract is "the URL the caller supplied, not the
    // post-redirect URL the content actually arrived from."
    private URL page;

    // ---- Constructors (mirror JDK JEditorPane) ----

    public SJEditorPane() {
        super();
        _installSwingClass();
    }

    /** Content-type + initial text. Other ctors (URL forms) sit on the emulator. */
    public SJEditorPane(String type, String text) {
        super();
        _installSwingClass();
        setContentType(type);
        setText(text);
    }

    // ---- text round-trip via RTE htmlValue (R_vaadin_first Vaadin-first, no shadow store) ----

    /**
     * Returns the RTE's current HTML value. Round-trip is lossy per R_vaadin_first — RTE
     * normalizes to its supported subset (see class javadoc), so
     * {@code setText(s); getText()} may not be byte-for-byte {@code s}. The
     * emulator's {@code Document} keeps a faithful copy for its own
     * {@code getText}; this reads what the editor actually holds.
     */
    public String getText() {
        return getValue();
    }

    /**
     * Writes {@code t} into RTE's HTML value. {@code null} normalises to empty
     * string (RTE's empty value; matches JDK's "clear the document").
     */
    public void setText(String t) {
        setValue(t == null ? "" : t);
    }

    // ---- contentType (filter-only; only text/html honored) ----

    public String getContentType() {
        return contentType;
    }

    /**
     * Resolve the type the way the JDK does and keep the result; only
     * {@code "text/html"} is honored for rendering, everything else WARNs per
     * R_vaadin_first — RTE renders HTML, and EditorKit-driven rendering is OOS once
     * Graphics paint is off the table (R_match_swing_errors sub-bucket (b)).
     *
     * <p><b>The argument is not what the getter answers, and that is the JDK's
     * contract, not an approximation.</b> {@code javax.swing.JEditorPane} keeps no
     * content-type field: {@code getContentType()} returns the <em>installed
     * EditorKit's</em> type, and a type with no registered kit installs the plain
     * kit — so {@code setContentType("ADMIN")} reads back as {@code "text/plain"},
     * and a {@code ";charset=…"} parameter list never survives the round trip.
     * SB-Emulators stored the string verbatim and answered it back, inventing a
     * content type the desktop cannot report (D_return_value_audit).
     *
     * <p>The four types with kits registered by default are the JDK's own
     * ({@code text/plain}, {@code text/html}, {@code text/rtf},
     * {@code application/rtf}); the lookup is an exact-string one there,
     * so {@code "TEXT/HTML"} resolves to the plain kit as it does here. A
     * migrator's {@code registerEditorKitForContentType} is not honored —
     * kit-driven rendering has no path to the peer — so a custom type
     * resolves to {@code text/plain} where the desktop would echo it.
     */
    public void setContentType(String type) {
        if (type == null) {
            throw new NullPointerException("contentType");
        }
        int parm = type.indexOf(';');
        String requested = parm > -1 ? type.substring(0, parm).trim() : type;
        String resolved = KIT_CONTENT_TYPES.contains(requested) ? requested : "text/plain";
        String old = this.contentType;
        if (Objects.equals(old, resolved)) return;
        this.contentType = resolved;
        if (!"text/html".equals(resolved)) {
            SHelper.onUnimplemented(this, "setContentType", type);
        }
        // No "contentType" property change: the JDK's setContentType fires
        // nothing of its own. Where a kit swap happens it reaches
        // setEditorKit, which fires "editorKit" — a different property, and one
        // SB-Emulators has no editor-kit concept to carry (SD_property_fanout_audit).
    }

    // ---- setPage / getPage (synchronous URL fetch) ----

    /**
     * The URL most recently passed to {@link #setPage(URL)}, or {@code null}
     * if no page has been loaded. Returns the caller's URL, not the
     * post-redirect one.
     */
    public URL getPage() {
        return page;
    }

    /**
     * Fetch the URL's content synchronously and render it as HTML. Bytes decode
     * via {@link URLConnection#getContentEncoding} when present, else UTF-8.
     * Forces {@code contentType="text/html"} and fires the {@code "page"} PCE.
     *
     * @throws IOException if the fetch fails — the same checked exception the
     *         JDK declares, so migrators' catch-and-degrade code still works.
     */
    public void setPage(URL page) throws IOException {
        URL old = this.page;
        URLConnection conn = page.openConnection();
        byte[] bytes;
        try (var in = conn.getInputStream()) {
            bytes = in.readAllBytes();
        }
        String contentTypeHeader = conn.getContentType();
        if (!HtmlCharset.isHtmlMediaType(contentTypeHeader)) {
            // Rendered as HTML anyway — say so rather than quietly mangling a
            // text/plain or XML resource into markup.
            SHelper.onUnimplemented(this, "setPage", contentTypeHeader);
        }
        if (page.getRef() != null && !page.getRef().isEmpty()) {
            // JDK setPage scrolls to the fragment via scrollToReference; there is no
            // counterpart on the editor, so the page loads at the top.
            SHelper.onUnimplemented(this, "scrollToReference", page.getRef());
        }
        String html = new String(bytes, HtmlCharset.detect(contentTypeHeader, bytes));
        this.page = page;
        setContentType("text/html");
        setText(html);
        firePropertyChange("page", old, page);
    }

    /**
     * Parse the string and dispatch to {@link #setPage(URL)}.
     *
     * @throws java.net.MalformedURLException if {@code url} will not parse — the
     *         same {@link IOException} subtype the JDK throws (it goes through
     *         {@code new URL(String)}), so migrated {@code catch (IOException)}
     *         handlers still catch it. {@code URI.create(...).toURL()} raises an
     *         unchecked {@code IllegalArgumentException} for some malformed inputs,
     *         which such a handler would miss (R_match_swing_errors).
     */
    public void setPage(String url) throws IOException {
        URL parsed;
        try {
            parsed = URI.create(url).toURL();
        } catch (IllegalArgumentException e) {
            java.net.MalformedURLException wrapped = new java.net.MalformedURLException(url);
            wrapped.initCause(e);
            throw wrapped;
        }
        setPage(parsed);
    }

    // ---- editable (RTE readOnly-backed; no shadow store per R_vaadin_first) ----

    /** Reads {@code !isReadOnly()} — RTE read-only mode hides the toolbar. */
    public boolean isEditable() {
        return !isReadOnly();
    }

    /**
     * Maps to {@code setReadOnly(!b)} and fires the {@code "editable"} PCE.
     * Both states — and a runtime toggle between them — ride the one RTE peer.
     */
    public void setEditable(boolean b) {
        boolean old = isEditable();
        if (old == b) return;
        setReadOnly(!b);
        firePropertyChange("editable", old, b);
    }

    // ---- Author CSS for the rendered content ----

    /**
     * Replace-in-place stylesheet injection. The {@code <style>} goes into this
     * instance's shadow root, which is already a style boundary, so no selector
     * scoping is needed to keep one pane's rules off another's content. Rewriting
     * {@code textContent} on every push (rather than appending an element per rule)
     * makes the script idempotent, so the same call serves an incremental
     * {@code addCssRule} and a full re-push after re-attach.
     */
    private static final String CSS_INJECT_JS = """
            const root = this.shadowRoot;
            if (!root) return;
            let s = root.querySelector('style[data-emul="sjeditorpane-css"]');
            if (!s) {
              s = document.createElement('style');
              s.setAttribute('data-emul', 'sjeditorpane-css');
              root.appendChild(s);
            }
            s.textContent = $0;
            """;

    private final java.util.List<String> cssRules = new java.util.ArrayList<>();

    private boolean cssBridgeInstalled;

    /**
     * Apply author CSS to the pane's rendered content — the peer-side half of
     * {@code HTMLEditorKit}'s {@code StyleSheet.addRule}. Accepts a single rule or
     * a whole stylesheet's worth; selectors are rewritten to reach RTE's content
     * element (see {@link com.vaadin.swingbridge.surrogates.internal.RteCssRules}).
     *
     * <p>Only <em>appearance</em> is reachable this way. Structure RTE's model
     * refuses — tables above all — is dropped as the value enters Quill, before
     * any stylesheet could matter, so no rule brings it back (SD_sjeditorpane_rte).
     *
     * <p>At-rules ({@code @media} and friends) are skipped and WARN: the rest of
     * the sheet still applies.
     */
    public void addCssRule(String css) {
        RteCssRules.Translated translated = RteCssRules.translate(css);
        for (String skipped : translated.skipped()) {
            SHelper.onUnimplemented(this, "addCssRule", skipped);
        }
        if (translated.rules().isEmpty()) return;
        cssRules.addAll(translated.rules());
        if (!cssBridgeInstalled) {
            cssBridgeInstalled = true;
            // Re-push on every attach: Vaadin re-renders a @PreserveOnRefresh tree
            // after F5 but does not replay executeJs, so a once-only injection
            // leaves the content unstyled after a reload.
            addAttachListener(e -> pushCss());
        }
        if (isAttached()) {
            pushCss();
        }
    }

    /** The rules applied so far, in the order they were added. */
    public java.util.List<String> getCssRules() {
        return java.util.List.copyOf(cssRules);
    }

    private void pushCss() {
        getElement().executeJs(CSS_INJECT_JS, String.join("\n", cssRules));
    }

    // ---- HyperlinkListener (anchor activation on a read-only pane) ----

    /**
     * Client-side hook script. Installed into the shadow root — <b>not</b> the
     * host — because a click on an anchor inside the RTE's shadow DOM
     * <em>retargets</em>: a host-level listener sees {@code e.target} as
     * {@code <vaadin-rich-text-editor>} and {@code closest('a[href]')} returns
     * null, so the obvious spelling never fires. {@code composedPath()} pierces
     * the boundary regardless of where the listener sits, so it is used as well.
     *
     * <p>Three things this must do beyond finding the anchor: skip editable panes
     * (Swing's {@code HTMLEditorKit.LinkController} activates a link only when the
     * pane is non-editable — in an editor a click positions the caret);
     * {@code preventDefault} because Quill renders anchors with
     * {@code target="_blank" rel="noopener noreferrer"} and would otherwise open a
     * browser tab <em>in addition</em> to firing the Swing event; and guard with a
     * sentinel so a re-install is idempotent.
     */
    private static final String HYPERLINK_HOOK_JS = """
            const root = this.shadowRoot;
            if (!root || root.__emulHyperlinkHook) return;
            root.__emulHyperlinkHook = true;
            root.addEventListener('click', e => {
              if (!(this.readonly || this.readOnly)) return;
              const a = e.composedPath().find(n => n.tagName === 'A' && n.hasAttribute('href'));
              if (!a) return;
              e.preventDefault();
              e.stopPropagation();
              this.dispatchEvent(new CustomEvent('emul-hyperlink',
                  { detail: { href: a.getAttribute('href') } }));
            });
            """;

    private final javax.swing.event.EventListenerList hyperlinkListeners =
            new javax.swing.event.EventListenerList();

    private boolean hyperlinkBridgeInstalled;

    /**
     * Add a listener notified when the user activates a link in a <b>read-only</b>
     * pane. Only {@code ACTIVATED} is delivered; {@code ENTERED} / {@code EXITED}
     * hover events have no counterpart wired (the JDK guarantees no delivery
     * cadence for them, and nothing in the surveyed field code reads them).
     *
     * <p>The client hook is installed lazily on the first registration, so a pane
     * nobody listens to keeps the browser's own navigation behaviour. One
     * consequence worth knowing: once a listener has been added, links stop
     * navigating natively even if every listener is later removed — the hook stays
     * installed. That direction matches Swing (where a link click never navigates
     * on its own) so it is left as-is.
     */
    public synchronized void addHyperlinkListener(javax.swing.event.HyperlinkListener l) {
        ensureHyperlinkBridge();
        hyperlinkListeners.add(javax.swing.event.HyperlinkListener.class, l);
    }

    public synchronized void removeHyperlinkListener(javax.swing.event.HyperlinkListener l) {
        hyperlinkListeners.remove(javax.swing.event.HyperlinkListener.class, l);
    }

    public synchronized javax.swing.event.HyperlinkListener[] getHyperlinkListeners() {
        return hyperlinkListeners.getListeners(javax.swing.event.HyperlinkListener.class);
    }

    /**
     * Wire the one shared DOM subscription that drives the Swing listener-list
     * fan-out (R_vaadin_first's shared-registration shape, as on SJButton). The script is
     * re-run on every attach rather than once: Vaadin re-renders a
     * {@code @PreserveOnRefresh} tree after F5 but does not replay
     * {@code executeJs}, so a once-only install would leave links dead after a
     * page reload.
     */
    private void ensureHyperlinkBridge() {
        if (hyperlinkBridgeInstalled) return;
        hyperlinkBridgeInstalled = true;
        getElement().addEventListener("emul-hyperlink", this::onClientHyperlink)
                .addEventData("event.detail.href");
        addAttachListener(e -> getElement().executeJs(HYPERLINK_HOOK_JS));
        if (isAttached()) {
            getElement().executeJs(HYPERLINK_HOOK_JS);
        }
    }

    /**
     * Bridge from the client CustomEvent to the Swing fan-out. Re-checks
     * {@code isEditable} server-side: the client gate decides whether to suppress
     * the browser's navigation, this one decides whether the Swing event is owed.
     */
    private void onClientHyperlink(com.vaadin.flow.dom.DomEvent event) {
        if (isEditable()) return;
        tools.jackson.databind.JsonNode data = event.getEventData();
        String key = "event.detail.href";
        String href = data != null && data.has(key) ? data.get(key).asString("") : "";
        if (href.isEmpty()) return;
        SHelper.callSwing(() -> fireHyperlinkActivated(href));
    }

    /**
     * Fan out an {@code ACTIVATED} event, matching what Swing's
     * {@code LinkController} builds: {@code getDescription()} is the raw
     * {@code href} as authored and {@code getURL()} is that href resolved — so a
     * relative link resolves against {@link #getPage()}, the nearest thing we have
     * to {@code HTMLDocument.getBase()}. An href that cannot form a URL (a bare
     * fragment, an unresolvable relative path with no page loaded) yields a
     * {@code null} URL, as it does in Swing, rather than dropping the event — the
     * description still carries the href.
     *
     * <p>The source element argument is always {@code null}: we have no
     * {@code HTMLDocument} element model to point at (plan slice 4).
     */
    protected void fireHyperlinkActivated(String href) {
        javax.swing.event.HyperlinkEvent e = new javax.swing.event.HyperlinkEvent(
                this, javax.swing.event.HyperlinkEvent.EventType.ACTIVATED,
                resolveHref(href), href, null);
        for (javax.swing.event.HyperlinkListener l
                : hyperlinkListeners.getListeners(javax.swing.event.HyperlinkListener.class)) {
            l.hyperlinkUpdate(e);
        }
    }

    /** Resolve against the loaded page when there is one; {@code null} if no URL forms. */
    private URL resolveHref(String href) {
        try {
            return page != null ? page.toURI().resolve(href).toURL() : URI.create(href).toURL();
        } catch (java.net.URISyntaxException | java.net.MalformedURLException
                | IllegalArgumentException e) {
            return null;
        }
    }

    // ---- L&F / UIClassID ----

    /**
     * JDK JEditorPane returns {@code "EditorPaneUI"}. We don't drive a
     * pluggable {@code ComponentUI} (R_layouts_close_enough — Vaadin owns the DOM), but keep the
     * ID so migrated code that introspects via BeanInfo finds the expected value.
     */
    @Override
    public String getUIClassID() {
        return "EditorPaneUI";
    }

    /** Enter inserts a line break while editable — Swing's {@code insert-break} binding. */
    @Override
    public boolean claimsEnter() {
        return isEditable();
    }
}
