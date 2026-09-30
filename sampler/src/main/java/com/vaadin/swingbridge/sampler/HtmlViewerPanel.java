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

package com.vaadin.swingbridge.sampler;

import vaadinx.awt.BorderLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JComponent;
import vaadinx.swing.JEditorPane;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;
import vaadinx.swing.text.html.HTMLEditorKit;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;

/**
 * The read-only HTML document viewer — the shape most real Swing code uses
 * {@code javax.swing.text.html} for (about boxes, help viewers, report panes),
 * where the pane displays HTML and is never edited. Four sub-demos:
 *
 * <ol>
 *   <li><b>Live stylesheet</b> — a read-only pane with an {@link HTMLEditorKit}
 *       installed purely to carry CSS. Type a rule, press <i>Add rule</i>, and
 *       {@code kit.getStyleSheet().addRule(...)} restyles the rendered content
 *       (D_htmleditorkit / SD_add_css_rule). Rules reach the real {@code <h2>}/{@code <p>}/{@code <li>}
 *       elements; {@code body} maps onto the content element itself.</li>
 *   <li><b>Link-driven help browser</b> — a {@code HyperlinkListener} turns link
 *       clicks into page loads (SD_hyperlink_listener) instead of browser navigation, with a Back
 *       button, because Swing only reports the activation and leaves the meaning to
 *       app code. One page is encoded in ISO-8859-1 to show charset detection from
 *       a {@code <meta>} declaration (SD_setpage_audit). The pages link by {@code http} URL
 *       and the listener maps those onto classpath resources — see
 *       {@link #helpPageFor} for why a relative href could not work.</li>
 *   <li><b>Why the pane must be read-only</b> — toggling {@code setEditable(true)}
 *       stops links activating, matching Swing, where
 *       {@code HTMLEditorKit.LinkController} activates a link only on a
 *       non-editable pane (in an editor a click positions the caret).</li>
 *   <li><b>Element model</b> — the same rendered pane read back as a document:
 *       {@code ((HTMLDocument) pane.getDocument()).getElement(id)} and
 *       {@code getDefaultRootElement()} walk what the markup parsed to (D_htmldocument). The
 *       model is read-only — the HTML mutators WARN — so writes still go through
 *       {@code setText}.</li>
 * </ol>
 *
 * <p>Appearance is stylable; structure is not. Markup outside the editor's
 * supported subset — tables above all — is dropped before any stylesheet could
 * apply, so no rule here brings a table back (SD_sjeditorpane_rte).
 */
public class HtmlViewerPanel extends JPanel {

    private static final String HELP_BASE = "/com/vaadin/swingbridge/sampler/help/";

    public HtmlViewerPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(demoSection("Demo 1 — HTMLEditorKit stylesheet, applied live", liveStylesheet()));
        add(Box.createVerticalStrut(8));
        add(demoSection("Demo 2 — help browser: link clicks drive setPage, ISO-8859-1 page, Back",
                helpBrowser()));
        add(Box.createVerticalStrut(8));
        add(demoSection("Demo 3 — links activate only while the pane is read-only", editableGate()));
        add(Box.createVerticalStrut(8));
        add(demoSection("Demo 4 — element model: getElement(id) / getDefaultRootElement()",
                elementModel()));
    }

    /**
     * The pane read back as a document. Written the way migrated code is — cast
     * {@code getDocument()} to the JDK {@code HTMLDocument}, then look elements up —
     * because that is exactly the shape this has to support.
     */
    private JComponent elementModel() {
        JEditorPane pane = new JEditorPane("text/html", ""
                + "<h2>Invoice 2024-118</h2>"
                + "<p id=\"customer\">ACME Industries</p>"
                + "<p id=\"total\">Total due: 1 240.00 EUR</p>"
                + "<p>Terms: net 30.</p>");
        pane.setEditable(false);

        JLabel outline = new JLabel(outlineOf(pane));
        JTextField idField = new JTextField("total", 20);
        JButton find = new JButton("Find element by id");
        JLabel found = new JLabel("Type an id (customer, total) and press the button");

        find.addActionListener(e -> {
            HTMLDocument doc = (HTMLDocument) pane.getDocument();
            Element element = doc.getElement(idField.getText().trim());
            if (element == null) {
                found.setText("No element with id \"" + idField.getText().trim() + "\"");
                return;
            }
            found.setText("<" + element.getName() + "> at offsets "
                    + element.getStartOffset() + ".." + element.getEndOffset()
                    + " — text: " + textOf(doc, element));
        });

        JPanel console = new JPanel(new BorderLayout(4, 0));
        console.add(idField, BorderLayout.CENTER);
        console.add(find, BorderLayout.EAST);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(outline);
        south.add(console);
        south.add(found);

        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.add(pane, BorderLayout.CENTER);
        row.add(south, BorderLayout.SOUTH);
        return row;
    }

    /** One line naming the body's child tags, straight off the parsed tree. */
    static String outlineOf(JEditorPane pane) {
        Element root = ((HTMLDocument) pane.getDocument()).getDefaultRootElement();
        StringBuilder tags = new StringBuilder("Outline: <").append(root.getName()).append(">");
        for (int i = 0; i < root.getElementCount(); i++) {
            Element child = root.getElement(i);
            tags.append(" › <").append(child.getName()).append(">");
            for (int j = 0; j < child.getElementCount(); j++) {
                tags.append(' ').append('<').append(child.getElement(j).getName()).append('>');
            }
        }
        return tags.toString();
    }

    /** The rendered text an element covers — a document's text is text, not markup. */
    static String textOf(HTMLDocument doc, Element element) {
        try {
            int len = element.getEndOffset() - element.getStartOffset();
            return doc.getText(element.getStartOffset(), Math.min(len, doc.getLength() - element.getStartOffset())).trim();
        } catch (javax.swing.text.BadLocationException e) {
            return "(unreadable: " + e + ")";
        }
    }

    /**
     * A viewer plus a one-line CSS console. The kit is installed before any rule is
     * added here, but the reverse order works identically — rules added to a
     * not-yet-installed kit are flushed when it reaches a pane.
     */
    private JComponent liveStylesheet() {
        JEditorPane pane = new JEditorPane("text/html", ""
                + "<h2>Quarterly report</h2>"
                + "<p>Revenue held steady while <strong>costs fell</strong>, driven by:</p>"
                + "<ul>"
                + "<li>lower hosting spend</li>"
                + "<li>fewer support escalations</li>"
                + "</ul>"
                + "<p>Prepared by the <em>finance</em> team.</p>");
        pane.setEditable(false);

        HTMLEditorKit kit = new HTMLEditorKit();
        pane.setEditorKit(kit);
        kit.getStyleSheet().addRule("body { font-family: Georgia, serif; }");
        kit.getStyleSheet().addRule("h2 { color: #336699; }");

        JLabel applied = new JLabel("Rules applied: 2 (body font, h2 colour)");
        JTextField ruleField = new JTextField("li { color: #b23c17; font-weight: bold; }", 40);
        JButton addRule = new JButton("Add rule");
        int[] count = {2};
        addRule.addActionListener(e -> {
            String rule = ruleField.getText();
            if (rule == null || rule.isBlank()) {
                return;
            }
            kit.getStyleSheet().addRule(rule);
            count[0]++;
            applied.setText("Rules applied: " + count[0] + " (latest: " + rule.trim() + ")");
        });

        JPanel console = new JPanel(new BorderLayout(4, 0));
        console.add(ruleField, BorderLayout.CENTER);
        console.add(addRule, BorderLayout.EAST);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(console);
        south.add(applied);

        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.add(pane, BorderLayout.CENTER);
        row.add(south, BorderLayout.SOUTH);
        return row;
    }

    /**
     * A three-page help set navigated entirely through {@code HyperlinkEvent}. The
     * listener is what decides a click means "load that page" — Swing itself only
     * reports the activation, which is why a Back button is app code here rather
     * than something the pane provides.
     */
    private JComponent helpBrowser() {
        JEditorPane viewer = new JEditorPane();
        viewer.setEditable(false);
        JLabel status = new JLabel("Loading…");
        Deque<URL> history = new ArrayDeque<>();
        JButton back = new JButton("Back");
        back.setEnabled(false);

        URL home = requireHelpPage("index.html");
        load(viewer, status, home);

        viewer.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                return;
            }
            if (e.getURL() == null) {
                // Swing leaves the URL null when the href cannot be resolved (a bare
                // fragment, say). The description still carries the raw href.
                status.setText("Unresolvable link: " + e.getDescription());
                return;
            }
            URL target = helpPageFor(e.getURL());
            if (target == null) {
                status.setText("Not part of the help set: " + e.getURL());
                return;
            }
            history.push(viewer.getPage());
            back.setEnabled(true);
            load(viewer, status, target);
        });

        back.addActionListener(e -> {
            if (history.isEmpty()) {
                return;
            }
            load(viewer, status, history.pop());
            back.setEnabled(!history.isEmpty());
        });

        JPanel north = new JPanel(new BorderLayout(4, 0));
        north.add(back, BorderLayout.WEST);
        north.add(status, BorderLayout.CENTER);

        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.add(north, BorderLayout.NORTH);
        row.add(viewer, BorderLayout.CENTER);
        return row;
    }

    /**
     * The same pane, editable or not, with the same link in it. Read-only fires
     * {@code ACTIVATED}; editable does not — the caret moves instead.
     */
    private JComponent editableGate() {
        JEditorPane pane = new JEditorPane("text/html",
                "<p>Try clicking <a href=\"https://vaadin.com/docs\">this link</a> "
                + "with the box below checked, then unchecked.</p>");
        pane.setEditable(false);

        JLabel status = new JLabel("Activations: 0 — pane is read-only, so links fire");
        int[] hits = {0};
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                hits[0]++;
                status.setText("Activations: " + hits[0] + " — last URL " + e.getURL());
            }
        });

        JCheckBox editable = new JCheckBox("Editable (links stop firing, as in Swing)");
        editable.addActionListener(e -> {
            pane.setEditable(editable.isSelected());
            status.setText("Activations: " + hits[0] + " — pane is "
                    + (editable.isSelected() ? "editable, so clicks position the caret"
                                             : "read-only, so links fire"));
        });

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(editable);
        south.add(status);

        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.add(pane, BorderLayout.CENTER);
        row.add(south, BorderLayout.SOUTH);
        return row;
    }

    /** Load a page into the viewer and report the URL it came from. */
    private static void load(JEditorPane viewer, JLabel status, URL page) {
        try {
            viewer.setPage(page);
            status.setText("Loaded: " + page);
        } catch (IOException e) {
            // A classpath fixture that fails to load means the resource was dropped
            // from the build, not a runtime condition worth degrading around.
            status.setText("Failed to load " + page + ": " + e);
        }
    }

    /**
     * Map a link URL onto a page in the bundled help set, or {@code null} if it
     * points somewhere else.
     *
     * <p>This mapping step is not ceremony — it is what a migrated help viewer has
     * to do, and why. The editor's sanitizer keeps an {@code href} only for
     * {@code http} / {@code https} / {@code mailto}; a relative or {@code file:}
     * href loses the attribute, which leaves an anchor the editor no longer treats
     * as a link, so it renders as plain text and cannot be clicked at all. Help
     * pages therefore link by http URL and the listener resolves those URLs against
     * local resources. Nothing here touches the network.
     */
    private static URL helpPageFor(URL linked) {
        if (!"help.sampler.local".equals(linked.getHost())) {
            return null;
        }
        String name = linked.getPath();
        int slash = name.lastIndexOf('/');
        name = slash < 0 ? name : name.substring(slash + 1);
        return name.isEmpty() ? null : HtmlViewerPanel.class.getResource(HELP_BASE + name);
    }

    private static URL requireHelpPage(String name) {
        URL url = HtmlViewerPanel.class.getResource(HELP_BASE + name);
        if (url == null) {
            throw new IllegalStateException("Missing Sampler help fixture: " + HELP_BASE + name);
        }
        return url;
    }

    private static JPanel demoSection(String labelText, JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }
}
