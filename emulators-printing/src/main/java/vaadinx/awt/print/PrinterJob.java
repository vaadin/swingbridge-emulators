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
 * This file is derived from OpenJDK's java.awt.print.PrinterJob
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt.print;

import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.awt.print.PrinterIOException;
import java.io.IOException;
import java.util.Locale;

import javax.print.PrintService;

import com.vaadin.flow.component.UI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vaadinx.BrowserFileTransfer;
import vaadinx.EHelper;

/**
 * The virtual PDF printer — import-swap replacement for
 * {@code java.awt.print.PrinterJob} (D_printing). One printer, always present: every
 * page the app's {@link Printable} draws is rendered server-side into a PDF
 * (via {@link PdfPrinterGraphicsFactory}), and the finished document is
 * offered to the user in a Vaadin download dialog; the user prints the PDF
 * with their local viewer, on their own printer. No OS print subsystem is
 * ever touched — the server's printers are the wrong machine's printers.
 *
 * <p>This is the only {@code java.awt.print} type the migrator swaps: the JDK
 * no longer honors the historical {@code java.awt.printerjob} system property
 * ({@code getPrinterJob()} hard-wires a platform job since JDK 24-ish), so the
 * injection seam is the import-rewrite, same as for the component classes.
 * {@link Printable}, {@link PageFormat}, {@link Paper}, {@code Book},
 * {@link Pageable} and the printer exceptions reference nothing we port and
 * stay JDK. Extending the JDK class keeps assignments to
 * {@code java.awt.print.PrinterJob}-typed variables compiling, and inherits
 * the concrete defaults ({@code printDialog(attributes)} delegates to
 * {@link #printDialog()}, {@code print(attributes)} to {@link #print()},
 * {@code getPrintService()} returns null).
 *
 * <p><b>UI-context contract:</b> the job captures {@link UI#getCurrent()} at
 * creation, so the classic Swing pattern of calling {@link #print()} on a
 * background thread still finds the right browser session — provided the job
 * itself was created in a UI context (event listener, {@code SwingWorker},
 * {@code Timer} callback). When neither the captured nor the calling-thread
 * UI exists, {@link #print()} throws {@link IllegalStateException}: a print
 * with no session to deliver to is a programming error, not a soft
 * incompleteness (the R_callswing_envelope stance).
 *
 * <p><b>Faithful divergences</b> (documented in the module README):
 * {@link #printDialog()} returns true without UI — nothing to configure on a
 * single always-present printer, and the terminal download dialog is the
 * cancelable moment. {@link #pageDialog(PageFormat)} echoes its argument.
 * {@link #defaultPage(PageFormat)} is A4 portrait with 1" margins where the
 * JDK is locale-dependent. Copies are ignored (WARN when {@code > 1}) — a PDF
 * download is inherently one copy. {@link #lookupPrintServices()} is empty —
 * the virtual printer is not a {@code javax.print.PrintService}, and the
 * inherited JDK static would have enumerated the <i>server's</i> printers.
 */
public class PrinterJob extends java.awt.print.PrinterJob {

    private static final Logger log = LoggerFactory.getLogger(PrinterJob.class);

    /** A4 in 1/72" points — PDF's native unit, and PageFormat's. */
    private static final double A4_WIDTH = 595;
    private static final double A4_HEIGHT = 842;
    private static final double MARGIN = 72; // 1", matching the JDK's default margins

    /**
     * The browser session the finished PDF is delivered to; captured at
     * creation per the class contract, null when created off-UI (then
     * {@link #print()} falls back to its calling thread's UI).
     */
    private final UI ui;

    private Printable printable;
    /** Page format for the Printable path; null → {@link #defaultPage()} at print time. */
    private PageFormat printableFormat;
    private Pageable pageable;

    private String jobName = "Java Printing"; // JDK RasterPrinterJob default
    private int copies = 1;

    // JDK cancel semantics (RasterPrinterJob): cancel() only takes effect while
    // a print() is in progress, isCancelled() reports in-progress-and-cancelled.
    private volatile boolean performingPrinting;
    private volatile boolean userCancelled;

    public PrinterJob() {
        ui = UI.getCurrent();
    }

    /** Hides the JDK static; returns the virtual PDF printer job. */
    public static PrinterJob getPrinterJob() {
        return new PrinterJob();
    }

    /**
     * Hides the JDK static, which would enumerate the <i>server's</i> print
     * services. The virtual printer is not a {@code PrintService}; code that
     * needs one is in must-rewrite territory (see the migration checklist).
     */
    public static PrintService[] lookupPrintServices() {
        return new PrintService[0];
    }

    @Override
    public void setPrintable(Printable painter) {
        printable = painter;
        pageable = null;
    }

    @Override
    public void setPrintable(Printable painter, PageFormat format) {
        printable = painter;
        printableFormat = format;
        pageable = null;
    }

    @Override
    public void setPageable(Pageable document) throws NullPointerException {
        if (document == null) {
            throw new NullPointerException("Pageable cannot be null"); // JDK contract
        }
        pageable = document;
        printable = null;
        printableFormat = null;
    }

    /**
     * Always true, with no dialog shown: a single always-present virtual
     * printer has no options to ask about, and the download dialog {@link
     * #print()} ends in is itself the cancelable moment (close it without
     * downloading). The attribute-taking overload inherits the JDK default,
     * which delegates here.
     */
    @Override
    public boolean printDialog() {
        return true;
    }

    /**
     * No page-setup UI — echoes {@code page} unchanged (the JDK contract's
     * "user cancelled" shape). Pass the desired format to
     * {@link #setPrintable(Printable, PageFormat)} instead.
     */
    @Override
    public PageFormat pageDialog(PageFormat page) {
        return page;
    }

    /**
     * A4 portrait with 1" margins. Divergence from the JDK's locale-dependent
     * default (Letter in US locales): the server's locale says nothing about
     * the user's paper anyway, and A4-vs-Letter differences are absorbed by
     * the PDF viewer's fit-to-page at physical print time.
     */
    @Override
    public PageFormat defaultPage(PageFormat page) {
        PageFormat pf = (PageFormat) page.clone();
        Paper paper = new Paper();
        paper.setSize(A4_WIDTH, A4_HEIGHT);
        paper.setImageableArea(MARGIN, MARGIN, A4_WIDTH - 2 * MARGIN, A4_HEIGHT - 2 * MARGIN);
        pf.setPaper(paper);
        pf.setOrientation(PageFormat.PORTRAIT);
        return pf;
    }

    /** The virtual printer accepts any page geometry — a clone is already valid. */
    @Override
    public PageFormat validatePage(PageFormat page) {
        return (PageFormat) page.clone();
    }

    @Override
    public void print() throws PrinterException {
        UI targetUi = ui != null ? ui : UI.getCurrent();
        if (targetUi == null) {
            throw new IllegalStateException("No UI to deliver the printed PDF to: UI.getCurrent() is null "
                    + "and none was captured when this PrinterJob was created. Create the job "
                    + "(PrinterJob.getPrinterJob()) from code running in a UI context — an event listener, "
                    + "SwingWorker or Timer callback — print() itself may then run on any thread.");
        }
        if (printable == null && pageable == null) {
            throw new PrinterException("No Printable or Pageable set — call setPrintable/setPageable first.");
        }
        if (copies > 1) {
            // Not silently one-copy: the intent is visible, the outcome differs.
            EHelper.onUnimplemented("vaadinx.awt.print.PrinterJob", "print",
                    "copies=" + copies + " ignored — a PDF download is inherently one copy");
        }

        performingPrinting = true;
        userCancelled = false;
        PdfPrinterGraphicsFactory pdf = new PdfPrinterGraphicsFactory();
        byte[] bytes = null;
        try {
            renderAllPages(pdf);
            if (pdf.pageCount() == 0) {
                // Printable said NO_SUCH_PAGE at index 0. Swing spools nothing,
                // silently; a zero-page PDF would be broken, so no dialog either.
                log.warn("print() produced no pages (Printable returned NO_SUCH_PAGE at index 0); no PDF offered");
                return;
            }
            bytes = pdf.finish();
        } catch (IOException e) {
            throw new PrinterIOException(e);
        } finally {
            if (bytes == null) {
                pdf.abort();
            }
            performingPrinting = false;
        }
        deliver(targetUi, bytes);
    }

    private void renderAllPages(PdfPrinterGraphicsFactory pdf) throws PrinterException, IOException {
        for (int pageIndex = 0;; pageIndex++) {
            if (userCancelled) {
                throw new PrinterAbortException("Printing cancelled by cancel()");
            }
            Printable painter;
            PageFormat pf;
            if (pageable != null) {
                int n = pageable.getNumberOfPages();
                if (n != Pageable.UNKNOWN_NUMBER_OF_PAGES && pageIndex >= n) {
                    return;
                }
                try {
                    pf = pageable.getPageFormat(pageIndex);
                    painter = pageable.getPrintable(pageIndex);
                } catch (IndexOutOfBoundsException e) {
                    // The Pageable contract's end-of-document signal under
                    // UNKNOWN_NUMBER_OF_PAGES.
                    return;
                }
            } else {
                painter = printable;
                pf = printableFormat != null ? printableFormat : defaultPage();
            }

            // getWidth/getHeight are already orientation-adjusted, so a
            // LANDSCAPE format simply yields a landscape-sized PDF page.
            Graphics2D g2d = pdf.beginPage(pf.getWidth(), pf.getHeight());
            int result;
            try {
                result = painter.print(g2d, pf, pageIndex);
            } catch (PrinterException e) {
                pdf.discardPage();
                throw e;
            } catch (RuntimeException e) {
                pdf.discardPage();
                // PrinterException has no (String, Throwable) ctor; initCause
                // keeps the user's stack trace reachable.
                PrinterException pe = new PrinterException(
                        "Printable.print threw at page index " + pageIndex + ": " + e);
                pe.initCause(e);
                throw pe;
            }
            if (result == Printable.NO_SUCH_PAGE) {
                pdf.discardPage();
                return;
            }
            pdf.commitPage();
        }
    }

    /**
     * Opens the download dialog in the target session. ui.access runs the
     * command inline when the calling thread already holds the session lock
     * (the common case: print() called from an EDT continuation), and queues
     * it for @Push delivery when called from a background thread.
     */
    private void deliver(UI targetUi, byte[] bytes) {
        String name = BrowserFileTransfer.safeName(jobName == null || jobName.isBlank() ? "print" : jobName);
        String fileName = name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
        targetUi.access(() -> BrowserFileTransfer.openDownloadDialog(bytes, fileName));
    }

    @Override
    public void setCopies(int copies) {
        this.copies = copies;
    }

    @Override
    public int getCopies() {
        return copies;
    }

    /**
     * The server-process user, matching the JDK. Nearly meaningless in a web
     * deployment (every session sees the same value) but harmless — apps use
     * it as a cosmetic job-attribute default.
     */
    @Override
    public String getUserName() {
        return System.getProperty("user.name");
    }

    @Override
    public void setJobName(String jobName) {
        if (jobName == null) {
            throw new NullPointerException(); // JDK contract
        }
        this.jobName = jobName;
    }

    @Override
    public String getJobName() {
        return jobName;
    }

    @Override
    public void cancel() {
        if (performingPrinting) { // JDK semantics: only effective mid-print
            userCancelled = true;
        }
    }

    @Override
    public boolean isCancelled() {
        return performingPrinting && userCancelled;
    }
}
