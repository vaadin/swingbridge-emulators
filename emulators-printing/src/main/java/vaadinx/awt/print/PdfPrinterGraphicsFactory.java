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

package vaadinx.awt.print;

import java.awt.Graphics2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

/**
 * The Graphics2D→PDF seam isolating the PDF-library choice (PDFBox +
 * {@code de.rototor.pdfbox:graphics2d}, D_printing) from {@link PrinterJob}'s render
 * loop. Swapping the PDF engine later means rewriting only this class.
 *
 * <p>The page protocol mirrors the JDK print loop's "ask first, commit after"
 * shape: {@link #beginPage} hands out a live {@link Graphics2D} before we know
 * whether the page exists; {@link #commitPage} materialises the PDF page from
 * what was drawn ({@code Printable.PAGE_EXISTS}); {@link #discardPage} drops
 * the drawing without adding a page ({@code Printable.NO_SUCH_PAGE} — the
 * orphaned form object never reaches the saved PDF since PDFBox only writes
 * objects reachable from the page tree). One begin per commit/discard,
 * strictly alternating.
 */
final class PdfPrinterGraphicsFactory {

    private final PDDocument doc = new PDDocument();
    private PdfBoxGraphics2D g2d;
    private float pageWidth;
    private float pageHeight;

    Graphics2D beginPage(double width, double height) throws IOException {
        pageWidth = (float) width;
        pageHeight = (float) height;
        g2d = new PdfBoxGraphics2D(doc, pageWidth, pageHeight);
        return g2d;
    }

    void commitPage() throws IOException {
        g2d.dispose();
        PDFormXObject xform = g2d.getXFormObject();
        g2d = null;
        PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // The form's matrix (set by getXFormObject) already flips Java's
            // y-down user space into PDF's y-up page space; with the page sized
            // exactly to the Graphics2D canvas, drawing at the origin is a 1:1
            // placement — no extra transform.
            cs.drawForm(xform);
        }
    }

    void discardPage() {
        g2d.dispose();
        g2d = null;
    }

    int pageCount() {
        return doc.getNumberOfPages();
    }

    byte[] finish() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        doc.close();
        return out.toByteArray();
    }

    /** Failure-path cleanup — frees PDFBox scratch resources. */
    void abort() {
        try {
            doc.close();
        } catch (IOException e) {
            // already on a failure path; the original exception is the story
        }
    }
}
