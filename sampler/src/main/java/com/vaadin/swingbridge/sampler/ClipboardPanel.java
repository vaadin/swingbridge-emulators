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
import vaadinx.awt.Toolkit;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextArea;

import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Clipboard demo. Four sub-demos stacked vertically, each
 * exercising a different slice of the {@link vaadinx.awt.datatransfer.WebClipboard}
 * surface so the exit gate can assert "user-path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Text round-trip</b> — JTextArea source + JButton "Copy", JTextArea
 *       target + JButton "Paste". Exercises {@link StringSelection} setContents
 *       and stringFlavor getTransferData via the production
 *       {@code EHelper.callSwing} → {@code Page.executeJs} path (or the
 *       test-mode in-process Transferable when running under Karibu).</li>
 *   <li><b>Image round-trip</b> — "Generate red square + copy" creates a
 *       {@link BufferedImage} server-side and ships it via an inline
 *       {@link Transferable} advertising {@link DataFlavor#imageFlavor};
 *       "Paste image" reads it back and reports dimensions. Demonstrates the
 *       PNG round-trip through {@code ImageIO} (D_clipboard_image_png).</li>
 *   <li><b>Combined text + image</b> — single Transferable carrying both
 *       flavors atomically. Demonstrates the multi-MIME ClipboardItem
 *       assembly: write packs both into one ClipboardItem; read advertises
 *       both flavors on the returned snapshot.</li>
 *   <li><b>Empty clipboard / unsupported flavor</b> — "Paste current"
 *       reports the live flavor count; "Probe unsupported" calls
 *       {@code getTransferData(DataFlavor.javaFileListFlavor)} which the
 *       snapshot rejects per the JDK contract (UnsupportedFlavorException).
 *       Demonstrates the graceful-empty + JDK-faithful error semantics.</li>
 * </ol>
 *
 * <p>The {@link Toolkit#getSystemClipboard()} entry point is the import-swap
 * target — migrated apps reaching {@code java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()}
 * land here unchanged after import rewrite. Per-UI singleton via
 * {@code ComponentUtil.setData}; multi-tab apps share the OS clipboard
 * through {@code navigator.clipboard} naturally.
 *
 * <p><b>Test-mode hook.</b> Production code uses the real browser clipboard
 * via {@code executeJs}. Karibu (no real browser) drives the same code path
 * after the exit-gate test calls
 * {@code ((WebClipboard) Toolkit.getDefaultToolkit().getSystemClipboard()).setTestMode(true)};
 * the panel itself stays mode-agnostic.
 */
public class ClipboardPanel extends JPanel {

    public ClipboardPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildTextDemo());
        add(Box.createVerticalStrut(8));
        add(buildImageDemo());
        add(Box.createVerticalStrut(8));
        add(buildCombinedDemo());
        add(Box.createVerticalStrut(8));
        add(buildProbeDemo());
    }

    // -----------------------------------------------------------------
    // Demo 1 — text round-trip
    // -----------------------------------------------------------------

    private JComponent buildTextDemo() {
        JTextArea source = new JTextArea(3, 30);
        source.setName("clipboard-text-source");
        source.setText("hello clipboard");
        JTextArea target = new JTextArea(3, 30);
        target.setName("clipboard-text-target");
        JLabel status = new JLabel(" ");
        status.setName("clipboard-text-status");

        JButton copy = new JButton("Copy text");
        copy.setName("clipboard-copy-text");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(source.getText()), null);
            status.setText("Copied " + source.getText().length() + " char(s) as text/plain.");
        });

        JButton paste = new JButton("Paste text");
        paste.setName("clipboard-paste-text");
        paste.addActionListener(e -> {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    String s = (String) t.getTransferData(DataFlavor.stringFlavor);
                    target.setText(s);
                    status.setText("Pasted " + s.length() + " char(s).");
                } catch (UnsupportedFlavorException | IOException ex) {
                    // Shouldn't reach here — we just checked support. Defensive.
                    status.setText("Paste failed: " + ex.getMessage());
                }
            } else {
                target.setText("");
                status.setText("Clipboard had no text/plain data.");
            }
        });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(copy);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(paste);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(new JLabel("Source:"));
        body.add(source);
        body.add(Box.createVerticalStrut(4));
        body.add(buttons);
        body.add(Box.createVerticalStrut(4));
        body.add(new JLabel("Pasted into:"));
        body.add(target);
        body.add(Box.createVerticalStrut(4));
        body.add(status);

        return demoSection("Demo 1 — text round-trip (StringSelection ↔ stringFlavor)", body);
    }

    // -----------------------------------------------------------------
    // Demo 2 — image round-trip
    // -----------------------------------------------------------------

    private JComponent buildImageDemo() {
        JLabel status = new JLabel(" ");
        status.setName("clipboard-image-status");
        JLabel preview = new JLabel("(no image pasted yet)");
        preview.setName("clipboard-image-preview");

        JButton copy = new JButton("Generate 32×32 red square + copy");
        copy.setName("clipboard-copy-image");
        copy.addActionListener(e -> {
            BufferedImage img = makeRedSquare(32, 32);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(img), null);
            status.setText("Copied a " + img.getWidth() + "×" + img.getHeight() + " PNG.");
        });

        JButton paste = new JButton("Paste image");
        paste.setName("clipboard-paste-image");
        paste.addActionListener(e -> {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                try {
                    Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                    int w = img.getWidth(null);
                    int h = img.getHeight(null);
                    preview.setText("Pasted: " + w + "×" + h + " image");
                    status.setText("Decoded a " + w + "×" + h + " image from the clipboard.");
                } catch (UnsupportedFlavorException | IOException ex) {
                    status.setText("Image paste failed: " + ex.getMessage());
                }
            } else {
                preview.setText("(no image pasted yet)");
                status.setText("Clipboard had no image data.");
            }
        });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(copy);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(paste);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(buttons);
        body.add(Box.createVerticalStrut(4));
        body.add(preview);
        body.add(Box.createVerticalStrut(4));
        body.add(status);

        return demoSection("Demo 2 — image round-trip (BufferedImage ↔ image/png via ImageIO)", body);
    }

    // -----------------------------------------------------------------
    // Demo 3 — combined text + image
    // -----------------------------------------------------------------

    private JComponent buildCombinedDemo() {
        JLabel status = new JLabel(" ");
        status.setName("clipboard-combined-status");
        JLabel readout = new JLabel("(nothing pasted yet)");
        readout.setName("clipboard-combined-readout");

        JButton copy = new JButton("Copy text + image together");
        copy.setName("clipboard-copy-combined");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new TextAndImageTransferable("caption-from-server", makeRedSquare(16, 16)),
                    null);
            status.setText("Copied caption + 16×16 image in a single ClipboardItem.");
        });

        JButton paste = new JButton("Paste both");
        paste.setName("clipboard-paste-combined");
        paste.addActionListener(e -> {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            StringBuilder parts = new StringBuilder();
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    parts.append("text='").append(t.getTransferData(DataFlavor.stringFlavor)).append("'");
                } catch (UnsupportedFlavorException | IOException ignored) {
                }
            }
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                try {
                    Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                    if (parts.length() > 0) parts.append(", ");
                    parts.append("image=").append(img.getWidth(null)).append("×").append(img.getHeight(null));
                } catch (UnsupportedFlavorException | IOException ignored) {
                }
            }
            readout.setText(parts.length() == 0 ? "(clipboard had no recognised flavors)" : parts.toString());
            status.setText("Read " + t.getTransferDataFlavors().length + " flavor(s) from the clipboard.");
        });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(copy);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(paste);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(buttons);
        body.add(Box.createVerticalStrut(4));
        body.add(readout);
        body.add(Box.createVerticalStrut(4));
        body.add(status);

        return demoSection("Demo 3 — combined text + image in one Transferable", body);
    }

    // -----------------------------------------------------------------
    // Demo 4 — probe semantics: empty clipboard + JDK-faithful flavor reject
    // -----------------------------------------------------------------

    private JComponent buildProbeDemo() {
        JLabel status = new JLabel(" ");
        status.setName("clipboard-probe-status");

        JButton inspect = new JButton("Inspect current flavors");
        inspect.setName("clipboard-inspect");
        inspect.addActionListener(e -> {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            DataFlavor[] flavors = cb.getAvailableDataFlavors();
            if (flavors.length == 0) {
                status.setText("Clipboard is empty (0 flavors).");
            } else {
                StringBuilder names = new StringBuilder();
                for (DataFlavor f : flavors) {
                    if (names.length() > 0) names.append(", ");
                    names.append(f.getHumanPresentableName());
                }
                status.setText(flavors.length + " flavor(s): " + names);
            }
        });

        JButton probeUnsupported = new JButton("Probe unsupported flavor");
        probeUnsupported.setName("clipboard-probe-unsupported");
        probeUnsupported.addActionListener(e -> {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            // javaFileListFlavor is deferred to the DnD slice — the
            // snapshot doesn't advertise it; getTransferData throws
            // UnsupportedFlavorException per JDK contract.
            try {
                t.getTransferData(DataFlavor.javaFileListFlavor);
                status.setText("Unexpected: javaFileListFlavor returned data.");
            } catch (UnsupportedFlavorException ufe) {
                status.setText("javaFileListFlavor rejected (JDK-faithful): " + ufe.getMessage());
            } catch (IOException ioe) {
                status.setText("Probe IOException: " + ioe.getMessage());
            }
        });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(inspect);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(probeUnsupported);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(buttons);
        body.add(Box.createVerticalStrut(4));
        body.add(status);

        return demoSection("Demo 4 — probe: live flavor list + JDK-faithful UnsupportedFlavorException", body);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static JPanel demoSection(String labelText, JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }

    private static BufferedImage makeRedSquare(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        try {
            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Inline Transferable that advertises only the image flavor — exercises the imageFlavor path. */
    private static final class ImageTransferable implements Transferable {
        private final BufferedImage image;
        ImageTransferable(BufferedImage image) { this.image = image; }

        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] { DataFlavor.imageFlavor }; }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }

    /** Inline Transferable that carries both text and image — exercises the multi-flavor extract / pack. */
    private static final class TextAndImageTransferable implements Transferable {
        private final String text;
        private final BufferedImage image;
        TextAndImageTransferable(String text, BufferedImage image) {
            this.text = text;
            this.image = image;
        }

        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.stringFlavor, DataFlavor.imageFlavor };
        }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
        }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (DataFlavor.stringFlavor.equals(flavor)) return text;
            if (DataFlavor.imageFlavor.equals(flavor)) return image;
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
