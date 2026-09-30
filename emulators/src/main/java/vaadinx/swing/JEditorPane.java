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
 * This file is derived from OpenJDK's javax.swing.JEditorPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

/**
 * Emulator for {@link javax.swing.JEditorPane}, rendered by a
 * {@link com.vaadin.swingbridge.surrogates.SJEditorPane} (RTE) peer, editable by default
 * like the JDK class. Decided per D_jeditorpane / SD_sjeditorpane_rte (Shape S: always-RTE, single peer);
 * the read-only {@code Div} peer is gone.
 *
 * <h2>R_leaf_peer_lockdown status: non-leaf, keeps the protected peer ctor</h2>
 *
 * JDK {@link javax.swing.JTextPane} extends JEditorPane, so JEditorPane is
 * non-leaf in the public Swing hierarchy. Per R_leaf_peer_lockdown we keep the protected
 * {@code (Component peer)} ctor open as the D_peer_ctor_injection seam for a future JTextPane
 * (or other subclass) to pass its own peer through. The class stays non-final.
 *
 * <h2>R_swing_is_truth two-way sync (editable)</h2>
 *
 * The base {@link vaadinx.swing.text.JTextComponent} keeps a {@link javax.swing.text.Document}
 * as the emulator-side source of truth and only auto-syncs it to
 * {@code TextFieldBase} peers — RTE is not one, so JEditorPane wires both
 * directions itself:
 *
 * <ul>
 *   <li><b>Swing → peer.</b> {@code setText} chains {@code super.setText(t)}
 *       (mutating the Document, firing user DocumentListeners) and pushes the
 *       markup into RTE's {@code htmlValue} via {@code peer.setText}. Direct
 *       {@code Document} mutation (e.g. {@code getDocument().insertString(...)})
 *       does NOT reach the peer — that path needs an HTMLEditorKit pipeline
 *       that's permanently OOS per R_match_swing_errors sub-bucket (b).</li>
 *   <li><b>peer → Swing.</b> Browser edits fire RTE's value-change (EAGER, so
 *       the Document tracks typing per R_swing_is_truth's "updates while typing" contract);
 *       the listener mirrors the HTML into the Document under
 *       {@code preventPeerEvents} (R_swing_is_truth feedback guard), wrapped in
 *       {@code EHelper.callSwing} per R_callswing_envelope. So {@code getText()} reflects user
 *       edits and DocumentListeners fire.</li>
 * </ul>
 *
 * <h2>Lossy HTML subset (SD_sjeditorpane_rte)</h2>
 *
 * RTE renders/accepts only its Quill subset — no tables, no arbitrary CSS,
 * base64-only images. This is now the accepted regression in <em>display</em>
 * too (the {@code Div} peer that rendered any HTML is deleted). The emulator's
 * Document still holds the faithful markup, so {@code getText()} is exact; only
 * the rendered peer is lossy. Details in {@link com.vaadin.swingbridge.surrogates.SJEditorPane}.
 *
 * <h2>Content type, page and document: the JDK's</h2>
 *
 * The content type is the installed kit's, the page is the document's
 * {@code StreamDescriptionProperty}, and a kit change brings a new, empty document — so the
 * content type goes first. {@link #setPage(java.net.URL)} is the JDK's body over the protected
 * {@link #getStream} hook, fetching on the server and synchronously, and skipping a URL that is
 * the same file as the loaded one. None of it reads the peer (D_emulator_owned_state).
 *
 * <h2>HTML element model</h2>
 *
 * A {@code text/html} pane's {@code getDocument()} is a
 * {@link vaadinx.swing.text.html.HTMLDocument} — a read-only projection of the
 * markup, reparsed on every change (D_htmldocument). So the JDK-shaped introspection code
 * works:
 *
 * <pre>{@code
 * pane.setContentType("text/html");
 * pane.setText("<h2>Report</h2><p id=\"total\">42</p>");
 * Element total = ((HTMLDocument) pane.getDocument()).getElement("total");
 * }</pre>
 *
 * {@code getText()} keeps returning <em>markup</em> — the document's own text is
 * the rendered text, as in Swing — and the document's HTML mutators WARN, so
 * writes still go through {@code setText}. A {@code text/plain} pane keeps a
 * {@code PlainDocument}.
 *
 * <h2>Hyperlink activation</h2>
 *
 * {@link #addHyperlinkListener} delivers {@code ACTIVATED} when the user clicks a
 * link in a non-editable pane, with {@code source} rebound to this emulator. The
 * peer-side plumbing lives on {@link com.vaadin.swingbridge.surrogates.SJEditorPane}; the
 * non-editable restriction is Swing's own ({@code HTMLEditorKit.LinkController}).
 *
 * <h2>EditorKit: the stylesheet, not the rendering pipeline</h2>
 *
 * {@link #setEditorKit} accepts a {@link vaadinx.swing.text.html.HTMLEditorKit} so
 * that {@code kit.getStyleSheet().addRule(…)} styles the pane's rendered content
 * (D_htmleditorkit). The kit's <em>rendering</em> half stays out of scope — Vaadin renders the
 * HTML, and the JDK path is {@code Graphics}-based (R_match_swing_errors sub-bucket (b)) — so a
 * custom {@code ViewFactory} is inert, and installing a kit does not swap the
 * document (the content type does, see above).
 *
 * <h2>What drops-and-WARNs</h2>
 *
 * {@code setEditorKit} with any other kind of kit, {@code getEditorKit} before one
 * is installed, and {@code createEditorKitForContentType /
 * getEditorKitForContentType} — content-type-driven kit lookup has nothing to
 * return.
 */
public class JEditorPane extends vaadinx.swing.text.JTextComponent {

    // Client-property keys the JDK reads out of the *component*, not out of a
    // setter — putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, TRUE) is the
    // documented way to make an HTML pane honour the component font. Both are
    // inert here: font and CSS length handling are the RTE's and R_layouts_close_enough's (D_missing_constants added
    // them because the key names have to compile at the call site).
    public static final String W3C_LENGTH_UNITS = "JEditorPane.w3cLengthUnits";
    public static final String HONOR_DISPLAY_PROPERTIES = "JEditorPane.honorDisplayProperties";

    // Attach handshake (D_jtextpane): the browser's RTE first reports its transient empty
    // init-state (fromClient), THEN echoes back our server-pushed value — both
    // *before* the user has touched anything. Mirroring the empty wipes the
    // Document; mirroring the echo rebuilds it to content it already holds, firing
    // the DocumentListener through partial states (a visible flash). So the whole
    // handshake is ignored: it's the client catching up to the server, not an edit.
    // Shared with JTextPane's wiring.
    private boolean peerReportedContent;   // client has completed the handshake
    boolean pushedContentToPeer;           // we pushed non-empty content (so expect an echo)

    /**
     * Whether this peer value-change belongs to the attach handshake and should be
     * skipped. Only {@code fromClient} changes are gated — server pushes are guarded
     * by {@code preventPeerEvents}, and tests drive the reverse sync server-side.
     */
    boolean ignoreInitHandshake(boolean fromClient, boolean blank) {
        if (!fromClient || peerReportedContent) {
            return false;
        }
        if (blank) {
            return true;               // spurious pre-render empties from the client
        }
        peerReportedContent = true;    // first real content resolves the handshake
        return pushedContentToPeer;    // it's the echo of our own push → skip; if we
                                       // pushed nothing, it's a genuine first edit → apply
    }

    public JEditorPane() {
        this(new com.vaadin.swingbridge.surrogates.SJEditorPane());
    }

    /**
     * {@code (String type, String text)} JDK ctor — sets the content type
     * first so {@code setText} writes against the right mime default, then
     * writes the initial markup.
     */
    public JEditorPane(java.lang.String type, java.lang.String text) {
        this(new com.vaadin.swingbridge.surrogates.SJEditorPane());
        setContentType(type);
        setText(text);
    }

    /**
     * URL-loading ctor — fetches the URL synchronously via
     * {@link #setPage(java.net.URL)}. IOException propagates per Swing's signature.
     */
    public JEditorPane(java.net.URL initialPage) throws java.io.IOException {
        this(new com.vaadin.swingbridge.surrogates.SJEditorPane());
        setPage(initialPage);
    }

    /**
     * URL-string-loading ctor — parses the string and dispatches to
     * {@link #setPage(java.lang.String)}.
     */
    public JEditorPane(java.lang.String url) throws java.io.IOException {
        this(new com.vaadin.swingbridge.surrogates.SJEditorPane());
        setPage(url);
    }

    /**
     * R_leaf_peer_lockdown D_peer_ctor_injection seam — kept protected because JTextPane is a JDK subclass.
     * Subclasses passing a non-SJEditorPane peer lose the
     * setText-forwarding and edit-sync the public ctors wire; that's their
     * problem to handle (matches the JTextField precedent).
     */
    protected JEditorPane(com.vaadin.flow.component.Component peer) {
        super(peer);
        // peer → Swing: browser edits mirror into the Document. RTE isn't a
        // TextFieldBase, so JTextComponent's base sync skips it — wire it here.
        if (peer instanceof com.vaadin.swingbridge.surrogates.SJEditorPane editor) {
            wirePeerToDocument(editor);
        }
    }

    /**
     * Subscribes to browser edits and mirrors them into the Document (EAGER per
     * R_swing_is_truth's "tracks typing", {@code callSwing} per R_callswing_envelope). Overridable seam: JTextPane
     * drives its two-way sync through RTE's <b>Delta</b> value instead of HTML, so
     * it replaces this wiring rather than adding a second, conflicting listener.
     */
    protected void wirePeerToDocument(com.vaadin.swingbridge.surrogates.SJEditorPane editor) {
        editor.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.EAGER);
        editor.addValueChangeListener(e -> {
            if (ignoreInitHandshake(e.isFromClient(), isBlankHtml(e.getValue()))) {
                return;
            }
            // Read the feedback-loop guard before entering callSwing, not only inside
            // the callback — see JTextComponent's listener for why.
            if (preventPeerEvents) return;
            vaadinx.EHelper.callSwing(() -> syncDocumentFromPeer(e.getValue()));
        });
    }

    /** RTE's empty {@code htmlValue} shapes. */
    static boolean isBlankHtml(String v) {
        return v == null || v.isBlank() || "<p></p>".equals(v) || "<p><br></p>".equals(v);
    }

    /**
     * Mirror the peer's HTML into the Document, guarded by
     * {@code preventPeerEvents} (inherited from JTextComponent) so the write
     * doesn't round-trip back through {@code setText}'s peer push. The Document
     * mutation still fires user DocumentListeners — that's R_swing_is_truth's whole point.
     */
    private void syncDocumentFromPeer(String html) {
        if (preventPeerEvents) return;
        preventPeerEvents = true;
        try {
            writeMarkup(html);
        } finally {
            preventPeerEvents = false;
        }
    }

    @Override
    public void setText(java.lang.String t) {
        // Update the document model (fires user DocumentListeners) AND push the
        // markup to the peer. Direct Document mutation by user code won't reach the
        // peer — see class javadoc.
        writeMarkup(t);
        pushTextToPeer(t);
    }

    /**
     * Write markup into whichever document model is installed: the HTML element
     * model reparses from it, any other document takes it as plain text (which is
     * what backs {@code getText()} there).
     */
    private void writeMarkup(java.lang.String t) {
        if (getDocument() instanceof vaadinx.swing.text.html.HTMLDocument html) {
            html._setMarkup(t);
        } else {
            super.setText(t);
        }
    }

    /**
     * The pane's markup — HTML for an HTML pane, exactly as {@code setText} /
     * {@code setPage} / the last browser edit left it.
     *
     * <p>Overridden because the base reads the document, and an
     * {@link vaadinx.swing.text.html.HTMLDocument}'s text is the <em>rendered</em>
     * text ({@code "Report one two"}), not the markup. Swing's own JEditorPane
     * serializes via {@code kit.write()} instead, which we deliberately don't copy:
     * its HTML-3.2 dialect ({@code <font>}, {@code <b>}, {@code align=}) would
     * rewrite the caller's markup on every read.
     */
    @Override
    public java.lang.String getText() {
        if (getDocument() instanceof vaadinx.swing.text.html.HTMLDocument html) {
            return html._getMarkup();
        }
        return super.getText();
    }

    /**
     * Pushes {@code setText}'s markup into RTE's {@code htmlValue} under
     * {@code preventPeerEvents} (so the peer's value-change listener doesn't
     * re-mirror our own write back into the Document). Overridable seam: JTextPane
     * pushes through its Delta serializer instead, driven by the Document mutation
     * {@code super.setText} already made, so it overrides this to a no-op.
     */
    protected void pushTextToPeer(java.lang.String t) {
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJEditorPane editor) {
            if (!isBlankHtml(t)) {
                pushedContentToPeer = true;   // expect the client to echo this on attach
            }
            withPeer(p -> {
                preventPeerEvents = true;
                try {
                    editor.setText(t == null ? "" : t);
                } finally {
                    preventPeerEvents = false;
                }
            });
        }
    }

    @Override
    public void setEditable(boolean b) {
        // Base stores the field + fires "editable" PCE, but only propagates to
        // TextFieldBase peers. RTE read-only mode lives on the surrogate, so
        // forward explicitly — setReadOnly(!b) hides the RTE toolbar.
        super.setEditable(b);
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJEditorPane editor) {
            withPeer(p -> editor.setEditable(b));
        }
    }

    // The content types javax.swing.JEditorPane registers a kit for out of the box
    // (loadDefaultKitsIfNecessary). Any other type gets a fresh plain kit.
    private static final java.util.Set<String> KIT_CONTENT_TYPES =
            java.util.Set.of("text/plain", "text/html", "text/rtf", "application/rtf");

    // The installed kit, as far as the content type sees it: its type (what getContentType
    // answers), and which registered type's per-pane cached kit it is — null for the default
    // kit the pane starts with, or for the fresh one an unregistered type gets.
    private java.lang.String contentType = "text/plain";
    private java.lang.String kitType;

    /**
     * Sets the type of content, installing the kit the JDK would: the type up to any
     * {@code ";"} parameter list, if one of {@code text/plain}, {@code text/html},
     * {@code text/rtf}, {@code application/rtf}, else {@code text/plain}. A {@code charset}
     * parameter on a {@code text/} type becomes the {@code "charset"} client property.
     *
     * <p>A different kit brings its own empty document, as in the JDK, so a
     * {@code setText} before {@code setContentType} is lost:
     * <pre>{@code
     * pane.setContentType("text/html");   // first: installs the HTMLDocument
     * pane.setText("<h2>Report</h2>");
     * }</pre>
     * Setting the installed registered type again changes nothing; an unregistered type
     * installs a fresh document every time.
     *
     * @throws NullPointerException if {@code type} is null
     */
    public final void setContentType(java.lang.String type) {
        int parm = type.indexOf(';');
        if (parm > -1) {
            java.lang.String paramList = type.substring(parm);
            type = type.substring(0, parm).trim();
            if (type.toLowerCase().startsWith("text/")) {
                setCharsetFromContentTypeParameters(paramList);
            }
        }
        boolean registered = KIT_CONTENT_TYPES.contains(type);
        if (registered && type.equals(kitType)) {
            return;   // the cached kit is already installed
        }
        kitType = registered ? type : null;
        installKitFor(registered ? type : "text/plain");
    }

    /** The JDK's parameter-list parse: the {@code charset} value, unquoted, as a client property. */
    private void setCharsetFromContentTypeParameters(java.lang.String paramList) {
        java.lang.String charset = com.vaadin.swingbridge.surrogates.internal.HtmlCharset.charsetParam(paramList);
        if (charset != null) {
            putClientProperty("charset", charset);
        }
    }

    /**
     * What {@code setEditorKit(kit)} does for a content-type change: the kit's type becomes
     * the pane's, and the kit's empty default document replaces the current one.
     */
    private void installKitFor(java.lang.String type) {
        contentType = type;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJEditorPane editor) {
            withPeer(p -> editor.setContentType(this.contentType));
        }
        if (contentTypeInstallsDocument()) {
            setDocument(createDocumentFor(type));
            pushTextToPeer(getText());
        }
    }

    /**
     * Whether a kit change replaces the document, as the JDK's always does. {@code JTextPane}
     * answers {@code false}: its {@code StyledDocument} is what its peer renders (D_jtextpane).
     */
    boolean contentTypeInstallsDocument() {
        return true;
    }

    /** The default document of the kit the JDK registers for {@code type}. */
    private static javax.swing.text.Document createDocumentFor(java.lang.String type) {
        return switch (type) {
            case "text/html" -> new vaadinx.swing.text.html.HTMLDocument();
            case "text/rtf", "application/rtf" -> new javax.swing.text.DefaultStyledDocument();
            default -> new javax.swing.text.PlainDocument();
        };
    }

    /**
     * The installed kit's content type. It is not the argument of {@link #setContentType}:
     * {@code "ADMIN"} reads back as {@code "text/plain"}, and a parameter list never survives.
     */
    public final java.lang.String getContentType() {
        return contentType;
    }

    // Bridge from the surrogate's hyperlink fan-out into this emulator's
    // listenerList, installed on first registration. Two reasons it isn't wired
    // in the ctor: the surrogate installs its client-side hook on first
    // registration too (so a pane nobody listens to keeps native link
    // navigation), and eager wiring here would defeat that laziness.
    private boolean hyperlinkBridgeInstalled;

    /**
     * Register a listener for link activation on a read-only pane. The event's
     * {@code source} is this JEditorPane — not the peer — so JDK-shaped user code
     * casting {@code e.getSource()} works. {@code getURL()} / {@code getDescription()}
     * carry what Swing's {@code LinkController} would supply; the source
     * {@code Element} is always {@code null} (no HTMLDocument element model).
     * Delivery is {@code ACTIVATED}-only, and only while {@code !isEditable()} —
     * both matching Swing.
     */
    public synchronized void addHyperlinkListener(javax.swing.event.HyperlinkListener listener) {
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJEditorPane editor) {
            if (!hyperlinkBridgeInstalled) {
                hyperlinkBridgeInstalled = true;
                withPeer(p -> editor.addHyperlinkListener(e -> vaadinx.EHelper.callSwing(
                        () -> fireHyperlinkUpdate(new javax.swing.event.HyperlinkEvent(
                                this, e.getEventType(), resolveHref(e.getDescription()),
                                e.getDescription(), null)))));
            }
            listenerList.add(javax.swing.event.HyperlinkListener.class, listener);
        } else {
            vaadinx.EHelper.onUnimplemented("JEditorPane", "addHyperlinkListener", listener);
        }
    }

    public synchronized void removeHyperlinkListener(javax.swing.event.HyperlinkListener listener) {
        listenerList.remove(javax.swing.event.HyperlinkListener.class, listener);
    }

    public synchronized javax.swing.event.HyperlinkListener[] getHyperlinkListeners() {
        return listenerList.getListeners(javax.swing.event.HyperlinkListener.class);
    }

    /**
     * The link's URL as the JDK's {@code LinkController} builds it: the {@code href} against
     * the loaded page, or {@code null} if no URL forms (SD_hyperlink_listener).
     */
    private java.net.URL resolveHref(java.lang.String href) {
        java.net.URL page = getPage();
        try {
            return page != null ? page.toURI().resolve(href).toURL() : java.net.URI.create(href).toURL();
        } catch (java.net.URISyntaxException | java.net.MalformedURLException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Fan out to this emulator's registered listeners, in registration order.
     * Public to match the JDK, where it is both the internal dispatch point and a
     * documented way for user code to synthesize an event.
     */
    public void fireHyperlinkUpdate(javax.swing.event.HyperlinkEvent e) {
        for (javax.swing.event.HyperlinkListener l
                : listenerList.getListeners(javax.swing.event.HyperlinkListener.class)) {
            l.hyperlinkUpdate(e);
        }
    }

    /**
     * Loads {@code page}, the JDK's way: the stream comes from {@link #getStream}, which also
     * sets the content type from the connection, and the page is read into a new document of
     * that type's kit. A page that is the same file as the loaded one is not fetched again —
     * only its reference, if any, is scrolled to — so a reload clears the page first:
     * <pre>{@code
     * pane.getDocument().putProperty(Document.StreamDescriptionProperty, null);
     * pane.setPage(url);
     * }</pre>
     *
     * <p>The fetch runs on the server, and synchronously: the JDK loads an HTML page on a
     * background thread, so its {@code "page"} event comes after this returns, where here it
     * comes before.
     *
     * @throws java.io.IOException if {@code page} is null ({@code "invalid url"}) or the fetch
     *         fails — after the content type has switched, as in the JDK
     */
    public void setPage(java.net.URL page) throws java.io.IOException {
        if (page == null) {
            throw new java.io.IOException("invalid url");
        }
        java.net.URL loaded = getPage();
        if ((loaded == null) || !loaded.sameFile(page)) {
            java.io.InputStream in = getStream(page);
            javax.swing.text.Document doc = initializeModel(page);
            read(in, doc);
            setDocument(doc);
        }
        final java.lang.String reference = page.getRef();
        if (reference != null) {
            // The JDK defers this past a reload with invokeLater, until the asynchronously
            // loaded view has painted; this load is synchronous and has no view.
            scrollToReference(reference);
            getDocument().putProperty(javax.swing.text.Document.StreamDescriptionProperty, page);
        }
        firePropertyChange("page", loaded, page);
    }

    // What the connection said about the page, carried onto the document it is read into.
    private java.util.Hashtable<java.lang.String, java.lang.Object> pageProperties;

    /**
     * Opens the connection, sets the content type from it, and returns its stream. A
     * migrator's override (authentication, a custom resolver) is called from
     * {@link #setPage} as on the desktop. Redirects are not followed, as in the JDK.
     */
    protected java.io.InputStream getStream(java.net.URL page) throws java.io.IOException {
        final java.net.URLConnection conn = page.openConnection();
        if (conn instanceof java.net.HttpURLConnection hconn) {
            hconn.setInstanceFollowRedirects(false);
            hconn.getResponseCode();
        }
        handleConnectionProperties(conn);
        return conn.getInputStream();
    }

    private void handleConnectionProperties(java.net.URLConnection conn) {
        if (pageProperties == null) {
            pageProperties = new java.util.Hashtable<>();
        }
        java.lang.String type = conn.getContentType();
        if (type != null) {
            setContentType(type);
            pageProperties.put("content-type", type);
        }
        pageProperties.put(javax.swing.text.Document.StreamDescriptionProperty, conn.getURL());
        java.lang.String enc = conn.getContentEncoding();
        if (enc != null) {
            pageProperties.put("content-encoding", enc);
        }
    }

    /** A new document of the installed kit's type, carrying the connection's properties. */
    private javax.swing.text.Document initializeModel(java.net.URL page) {
        javax.swing.text.Document doc = contentTypeInstallsDocument()
                ? createDocumentFor(contentType) : getDocument();
        if (pageProperties != null) {
            for (java.util.Map.Entry<java.lang.String, java.lang.Object> e : pageProperties.entrySet()) {
                doc.putProperty(e.getKey(), e.getValue());
            }
            pageProperties.clear();
        }
        if (doc.getProperty(javax.swing.text.Document.StreamDescriptionProperty) == null) {
            doc.putProperty(javax.swing.text.Document.StreamDescriptionProperty, page);
        }
        return doc;
    }

    /**
     * Reads the page into {@code doc}, decoded by the {@code "charset"} client property when
     * one is set, else by a {@code <meta>} declaration in an HTML page, else UTF-8, and pushes
     * it to the peer.
     */
    private void read(java.io.InputStream in, javax.swing.text.Document doc) throws java.io.IOException {
        byte[] bytes;
        try (in) {
            bytes = in.readAllBytes();
        }
        java.lang.String charset = (java.lang.String) getClientProperty("charset");
        java.nio.charset.Charset cs;
        if (charset != null) {
            try {
                cs = java.nio.charset.Charset.forName(charset);
            } catch (IllegalArgumentException e) {
                // What the JDK's InputStreamReader(in, charsetName) throws.
                throw new java.io.UnsupportedEncodingException(charset);
            }
        } else {
            cs = "text/html".equals(contentType)
                    ? com.vaadin.swingbridge.surrogates.internal.HtmlCharset.detect(null, bytes)
                    : java.nio.charset.StandardCharsets.UTF_8;
        }
        java.lang.String content = new java.lang.String(bytes, cs);
        if (doc instanceof vaadinx.swing.text.html.HTMLDocument html) {
            // Decoded already, so a <meta> charset must not restart the parse. The JDK's
            // document ends in the same state, after its ChangedCharSetException re-read.
            html.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
            html._setMarkup(content);
        } else {
            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, content, null);
            } catch (javax.swing.text.BadLocationException e) {
                throw new java.io.IOException(e.getMessage());
            }
        }
        pushTextToPeer(content);
    }

    /**
     * Scrolls to the named anchor — which needs view geometry only the browser has, so it
     * WARNs. {@link #setPage} calls it for a URL with a reference, as the JDK's does.
     */
    public void scrollToReference(java.lang.String reference) {
        vaadinx.EHelper.onUnimplemented("JEditorPane", "scrollToReference", reference);
    }

    /**
     * Parses {@code url} and loads it.
     *
     * @throws java.io.IOException if {@code url} is null ({@code "invalid url"}) or not a URL
     *         ({@link java.net.MalformedURLException}, as the JDK's {@code new URL(String)} throws)
     */
    public void setPage(java.lang.String url) throws java.io.IOException {
        if (url == null) {
            throw new java.io.IOException("invalid url");
        }
        @SuppressWarnings("deprecation")
        java.net.URL page = new java.net.URL(url);
        setPage(page);
    }

    /** The page loaded into the current document — its {@code StreamDescriptionProperty}. */
    public java.net.URL getPage() {
        return (java.net.URL) getDocument().getProperty(javax.swing.text.Document.StreamDescriptionProperty);
    }

    // The installed kit, or null when none was set. Only a vaadinx HTMLEditorKit
    // can be installed — it is the only kit with a path to the peer.
    private javax.swing.text.EditorKit editorKit;

    /**
     * Install an editor kit. Every kit is stored and read back by
     * {@link #getEditorKit}; only a {@link vaadinx.swing.text.html.HTMLEditorKit}
     * additionally takes effect, its stylesheet binding to the peer so rules added to
     * it (before or after this call) style the pane's content. Any other kind WARNs —
     * without a route to the Vaadin peer, installing it could only pretend.
     *
     * <p>Two JDK behaviours are deliberately not reproduced. The document is left
     * alone — the JDK replaces it with {@code kit.createDefaultDocument()}, whereas
     * here the element model follows the <em>content type</em> instead
     * ({@link #setContentType}), so installing a kit never silently changes what
     * {@code getDocument()} returns. And the content type is left alone; set it
     * explicitly if it matters.
     */
    public void setEditorKit(javax.swing.text.EditorKit kit) {
        javax.swing.text.EditorKit old = this.editorKit;
        this.editorKit = kit;
        if (kit instanceof vaadinx.swing.text.html.HTMLEditorKit htmlKit
                && getPeer() instanceof com.vaadin.swingbridge.surrogates.SJEditorPane editor) {
            withPeer(p -> htmlKit._bindTo(editor));
        } else if (kit != null) {
            vaadinx.EHelper.onUnimplemented("JEditorPane", "setEditorKit(install)", kit);
        }
        firePropertyChange("editorKit", old, kit);
    }

    /**
     * @return the installed kit, or {@code null} when none was set — where the JDK
     *         would manufacture one from the content type, which here would drive
     *         nothing (R_match_swing_errors sub-bucket (b))
     */
    public javax.swing.text.EditorKit getEditorKit() {
        return editorKit;
    }

    public javax.swing.text.EditorKit getEditorKitForContentType(java.lang.String type) {
        vaadinx.EHelper.onUnimplemented("JEditorPane", "getEditorKitForContentType", type);
        return null;
    }

    public void setEditorKitForContentType(java.lang.String type, javax.swing.text.EditorKit k) {
        vaadinx.EHelper.onUnimplemented("JEditorPane", "setEditorKitForContentType", type, k);
    }

    public static javax.swing.text.EditorKit createEditorKitForContentType(java.lang.String type) {
        vaadinx.EHelper.onUnimplemented("JEditorPane", "createEditorKitForContentType", type);
        return null;
    }

    public java.lang.String getUIClassID() {
        return "EditorPaneUI";
    }

    @Override
    protected javax.swing.text.Document createDefaultDocument() {
        // PlainDocument, matching the JDK's text/plain default — the HTML element
        // model swaps in from setContentType("text/html"), not from here, because
        // this runs from the base ctor before any content type is known.
        return super.createDefaultDocument();
    }
}
