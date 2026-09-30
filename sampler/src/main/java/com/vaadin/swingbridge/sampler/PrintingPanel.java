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

import vaadinx.awt.print.PrinterJob;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;

/**
 * The virtual PDF printer demo (D_printing). Runs the canonical Swing
 * printing flow over a hand-crafted two-page {@link Printable}: text, shapes
 * and lines drawn straight onto the printer {@code Graphics2D} — the
 * <i>supported</i> printing case (static vector rendering; printing
 * <i>components</i> stays out per the paint-code-is-out stance).
 *
 * <p>The only migration delta from a desktop Swing app is the import:
 * {@code vaadinx.awt.print.PrinterJob} instead of
 * {@code java.awt.print.PrinterJob}. {@code Printable}, {@code PageFormat}
 * and friends stay JDK. {@code printDialog()} returns true without UI (one
 * always-present virtual printer); {@code print()} renders the PDF
 * server-side and opens a download dialog — the user prints the PDF with
 * their local viewer.
 */
public class PrintingPanel extends JPanel {

    private final JLabel status = new JLabel(" ");

    public PrintingPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        status.setName("printing-status");

        JButton print = new JButton("Print sample document");
        print.setName("printing-print");
        print.addActionListener(e -> {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName("sampler-print-demo");
            job.setPrintable(new SampleDocument());
            if (job.printDialog()) {
                try {
                    job.print();
                    status.setText("Rendered a 2-page PDF — grab it from the download dialog.");
                } catch (PrinterException ex) {
                    status.setText("Print failed: " + ex.getMessage());
                }
            }
        });

        add(new JLabel("Prints a hand-drawn two-page document to the virtual PDF printer. "
                + "The classic Swing flow — getPrinterJob / setPrintable / printDialog / print — "
                + "ends in a PDF download instead of paper."));
        add(Box.createVerticalStrut(8));
        add(print);
        add(Box.createVerticalStrut(8));
        add(status);
    }

    /** Hand-crafted vector pages — what a typical Swing report Printable does. */
    private static final class SampleDocument implements Printable {
        @Override
        public int print(java.awt.Graphics g, PageFormat pf, int pageIndex) {
            if (pageIndex > 1) {
                return NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) g;
            int x = (int) pf.getImageableX();
            int y = (int) pf.getImageableY();

            g2.setColor(Color.BLACK);
            g2.setFont(new Font(Font.SERIF, Font.BOLD, 18));
            g2.drawString("Swing-on-Vaadin — print sample, page " + (pageIndex + 1), x, y + 20);
            g2.drawLine(x, y + 30, x + (int) pf.getImageableWidth(), y + 30);

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            if (pageIndex == 0) {
                g2.drawString("Shapes and text drawn straight onto the printer Graphics2D:", x, y + 60);
                g2.setColor(new Color(0x1676F3));
                g2.fillRect(x, y + 80, 120, 60);
                g2.setColor(new Color(0xC4001D));
                g2.fillOval(x + 150, y + 80, 60, 60);
                g2.setColor(Color.BLACK);
                g2.drawRect(x + 240, y + 80, 90, 60);
            } else {
                for (int line = 0; line < 20; line++) {
                    g2.drawString("Body line " + (line + 1) + " — lorem ipsum dolor sit amet.",
                            x, y + 60 + line * 16);
                }
            }
            return PAGE_EXISTS;
        }
    }
}
