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
import vaadinx.swing.JComponent;
import vaadinx.swing.JEditorPane;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextPane;
import vaadinx.swing.text.RteHtmlCodec;
import vaadinx.swing.text.html.HTMLEditorKit;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * {@link JEditorPane} / {@link JTextPane} demo. Three sub-demos stacked vertically;
 * each exercises a different slice of the surface so the exit-gate test can assert
 * "user-path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Read-only render + hyperlink activation</b> — a small
 *       {@code <h2><p><ul><a>} snippet rendered via {@code setText} with
 *       {@code text/html}, then {@code setEditable(false)} so the RTE peer hides
 *       its toolbar. Validates the htmlValue write path, the contentType filter
 *       (text/html is the only WARN-free type), the {@code HyperlinkListener}
 *       fan-out — clicking the link fires {@code ACTIVATED} into a status
 *       {@link JLabel} rather than navigating the browser — and the
 *       {@link HTMLEditorKit} stylesheet idiom, whose {@code addRule} calls restyle
 *       the rendered heading, body font and list items.</li>
 *   <li><b>Editable HTML</b> — an editable pane whose current HTML is echoed
 *       into a status {@link JLabel} by a {@link DocumentListener}. Exercises
 *       the editable-by-default stance and the peer→Document R_swing_is_truth sync
 *       (browser edits reach {@code getText()} + fire DocumentListeners).</li>
 *   <li><b>JTextPane styled runs + mirror</b> — an editable {@link JTextPane} and a
 *       read-only {@link JTextPane} sharing one {@link StyledDocument}. Edits run
 *       {@code fromHtml} into the shared model; the mirror re-serializes it via
 *       {@code toHtml}, so it shows the model round-tripped — a live faithfulness
 *       check. A {@code condensedDump} label prints the live Element tree.</li>
 * </ol>
 *
 * <p>Editing rides Vaadin {@code RichTextEditor} (SD_sjeditorpane_rte): the single peer covers
 * both read-only and editable modes. The lossy HTML subset (no tables /
 * arbitrary CSS) applies to display too — see {@link JEditorPane}.
 */
public class EditorPanesPanel extends JPanel {

    public EditorPanesPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — read-only HTML render + hyperlink activation. The link click is
        // reported by the peer's shadow-root hook; the pane must be non-editable for
        // it to activate at all, matching Swing's HTMLEditorKit.LinkController.
        JEditorPane staticHtml = new JEditorPane("text/html", ""
                + "<h2>Vaadin Sampler</h2>"
                + "<p>JEditorPane renders HTML via the <strong>RichTextEditor</strong>"
                + " surrogate. Supported markup includes:</p>"
                + "<ul>"
                + "<li>Headings, paragraphs, emphasis</li>"
                + "<li>Lists (ordered and unordered)</li>"
                + "<li>Links and block quotes</li>"
                + "</ul>"
                + "<p>Click <a href=\"https://vaadin.com/docs\">this link</a> — it fires a"
                + " HyperlinkEvent instead of navigating.</p>");
        staticHtml.setEditable(false);

        // The field's dominant HTMLEditorKit idiom: install a kit purely to carry a
        // stylesheet, on a pane that is never edited. Rules reach the real <h2>/<p>
        // elements RTE renders, so tag selectors translate close to 1:1; "body" maps
        // onto the content element itself.
        HTMLEditorKit kit = new HTMLEditorKit();
        staticHtml.setEditorKit(kit);
        kit.getStyleSheet().addRule("body { font-family: Georgia, serif; }");
        kit.getStyleSheet().addRule("h2 { color: #336699; }");
        kit.getStyleSheet().addRule("li { color: #555; }");

        JLabel linkStatus = new JLabel("HyperlinkEvent: (none yet — click the link above)");
        staticHtml.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                linkStatus.setText("HyperlinkEvent: ACTIVATED desc=" + e.getDescription()
                        + " url=" + e.getURL());
            }
        });
        JPanel linkRow = new JPanel(new BorderLayout(0, 4));
        linkRow.add(staticHtml, BorderLayout.CENTER);
        linkRow.add(linkStatus, BorderLayout.SOUTH);
        add(demoSection("Demo 1 — read-only HTML render + HyperlinkListener", linkRow));
        add(Box.createVerticalStrut(8));

        // Demo 2 — editable editor with a live HTML echo. The DocumentListener
        // fires on every peer→Document mirror, so the status label tracks the
        // editor's current markup as the user types.
        JLabel status = new JLabel("HTML: <p>Edit me…</p>");
        JEditorPane editable = new JEditorPane("text/html", "<p>Edit me…</p>");
        editable.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { echo(); }
            @Override public void removeUpdate(DocumentEvent e) { echo(); }
            @Override public void changedUpdate(DocumentEvent e) { echo(); }
            private void echo() { status.setText("HTML: " + editable.getText()); }
        });
        JPanel editRow = new JPanel(new BorderLayout(0, 4));
        editRow.add(editable, BorderLayout.CENTER);
        editRow.add(status, BorderLayout.SOUTH);
        add(demoSection("Demo 2 — editable HTML (DocumentListener echo)", editRow));
        add(Box.createVerticalStrut(8));

        // Demo 3 — editable JTextPane. Programmatic AttributeSet styling is
        // serialized to the RTE peer's HTML value and rendered; formatting via the RTE
        // toolbar flows back through the HTML reverse-sync and rebuilds the
        // StyledDocument, so the live Element dump below shows the styled runs and
        // their attributes update as you edit.
        JTextPane styled = new JTextPane();
        StyledDocument styledDoc = styled.getStyledDocument();
        try {
            SimpleAttributeSet title = new SimpleAttributeSet();
            StyleConstants.setBold(title, true);
            StyleConstants.setForeground(title, new Color(0x1a73e8));
            styledDoc.insertString(styledDoc.getLength(), "Styled runs\n", title);

            SimpleAttributeSet note = new SimpleAttributeSet();
            StyleConstants.setItalic(note, true);
            styledDoc.insertString(styledDoc.getLength(),
                    "bold, colour, italic and alignment map to RTE and render; "
                    + "a large uniform font size becomes a heading; "
                    + "font family has no counterpart and drops.", note);

            // A uniform paragraph FontSize with no heading key maps to <hN> by band (Idea A) —
            // the only way to render bigger text, since RTE has no font-size control.
            SimpleAttributeSet heading = new SimpleAttributeSet();
            StyleConstants.setFontSize(heading, 20);
            styledDoc.insertString(styledDoc.getLength(), "\nHeading via FontSize 20", heading);

            SimpleAttributeSet center = new SimpleAttributeSet();
            StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
            styledDoc.setParagraphAttributes(0, 1, center, false);
        } catch (BadLocationException e) {
            // Fixed offsets over freshly-built content — unreachable in practice.
            throw new IllegalStateException(e);
        }
        JLabel styledDump = new JLabel("Elements: " + condensedDump(styledDoc));
        styled.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
            private void refresh() {
                styledDump.setText("Elements: " + condensedDump(styled.getStyledDocument()));
            }
        });
        // Read-only mirror over the SAME StyledDocument. A browser edit in the
        // editable pane runs fromHtml → this shared model; the mirror's own renderPush
        // then re-serializes the model via toHtml → its peer. So the mirror shows the
        // document model round-tripped back to HTML: if it matches the editable pane
        // after an edit, the serialize/deserialize is faithful. setEditable(false)
        // hides its toolbar and stops it emitting edits of its own.
        JTextPane mirror = new JTextPane(styledDoc);
        mirror.setEditable(false);

        JPanel panes = new JPanel();
        panes.setLayout(new BoxLayout(panes, BoxLayout.Y_AXIS));
        panes.add(demoSection("Editable input", styled));
        panes.add(Box.createVerticalStrut(8));
        panes.add(demoSection("Read-only mirror — document model re-serialized to HTML (round-trip check)", mirror));

        JPanel styledRow = new JPanel(new BorderLayout(0, 4));
        styledRow.add(panes, BorderLayout.CENTER);
        styledRow.add(styledDump, BorderLayout.SOUTH);
        add(demoSection("Demo 3 — editable JTextPane + read-only mirror over the same document model", styledRow));
    }

    /**
     * One-line dump of a Document's Element tree, e.g.
     * {@code section{paragraph(align=1){content(bi)="loud"}}}. Branch elements
     * nest in braces; leaves show their (truncated) text; any styling attributes
     * (bold/italic/underline/colour/alignment) render as a compact tag.
     */
    private static String condensedDump(Document doc) {
        StringBuilder sb = new StringBuilder();
        dumpElement(doc.getDefaultRootElement(), doc, sb);
        return sb.toString();
    }

    private static void dumpElement(Element e, Document doc, StringBuilder sb) {
        sb.append(e.getName());
        String attrs = attrSummary(e.getAttributes());
        if (!attrs.isEmpty()) {
            sb.append('(').append(attrs).append(')');
        }
        int n = e.getElementCount();
        if (n == 0) {
            int len = Math.min(e.getEndOffset(), doc.getLength()) - e.getStartOffset();
            String t = "";
            if (len > 0) {
                try {
                    t = doc.getText(e.getStartOffset(), len);
                } catch (BadLocationException ignored) {
                    // A read over a live element's own range doesn't happen; leave empty.
                }
            }
            if (t.length() > 30) {
                t = t.substring(0, 30) + "…";
            }
            sb.append("=\"").append(t.replace("\n", "\\n")).append('"');
        } else {
            sb.append('{');
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                dumpElement(e.getElement(i), doc, sb);
            }
            sb.append('}');
        }
    }

    private static String attrSummary(AttributeSet a) {
        List<String> parts = new ArrayList<>();
        if (StyleConstants.isBold(a)) parts.add("b");
        if (StyleConstants.isItalic(a)) parts.add("i");
        if (StyleConstants.isUnderline(a)) parts.add("u");
        if (StyleConstants.isStrikeThrough(a)) parts.add("s");
        if (a.isDefined(StyleConstants.Foreground)) parts.add("fg");
        if (a.isDefined(StyleConstants.Background)) parts.add("bg");
        if (a.isDefined(StyleConstants.FontSize)) parts.add("size=" + StyleConstants.getFontSize(a));
        if (a.isDefined(StyleConstants.Alignment)) parts.add("align=" + StyleConstants.getAlignment(a));
        // Block/inline structure the codec carries opaquely (headings/lists/quote/code/link) —
        // otherwise a browser-applied heading would show as a bare paragraph with no hint of it.
        if (a.isDefined(RteHtmlCodec.HEADER)) parts.add("h" + a.getAttribute(RteHtmlCodec.HEADER));
        if (a.isDefined(RteHtmlCodec.LIST)) parts.add("list=" + a.getAttribute(RteHtmlCodec.LIST));
        if (a.isDefined(RteHtmlCodec.BLOCKQUOTE)) parts.add("quote");
        if (a.isDefined(RteHtmlCodec.CODE_BLOCK)) parts.add("code");
        if (a.isDefined(RteHtmlCodec.LINK)) parts.add("link");
        return String.join(",", parts);
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
