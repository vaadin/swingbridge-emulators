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

import com.github.mvysny.kaributesting.v10.DownloadKt;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockService;
import com.github.mvysny.kaributesting.v10.mock.MockVaadinServlet;
import com.github.mvysny.blockingdialogs.uifiber.loom.VirtualThreadAwareLock;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import static java.awt.print.Printable.NO_SUCH_PAGE;
import static java.awt.print.Printable.PAGE_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The virtual PDF printer end-to-end under Karibu: render loop (Printable +
 * Pageable paths), download-dialog delivery, JDK-contract edges. The session lock
 * is routed through {@link VirtualThreadAwareLock} although print() never parks:
 * the loom runner's {@code SessionLockCheck} fails every session whose lock isn't.
 */
class PrinterJobTest {

    @BeforeEach
    void setupKaribu() {
        MockVaadin.setup(MockedUI::new, new MockVaadinServlet(new Routes()) {
            @Override
            protected VaadinServletService createServletService(DeploymentConfiguration configuration) {
                final VaadinServletService service = new MockService(this, configuration, getUiFactory()) {
                    @Override
                    protected Lock getSessionLock(WrappedSession wrappedSession) {
                        return VirtualThreadAwareLock.wrap(this, wrappedSession, super.getSessionLock(wrappedSession));
                    }
                };
                try {
                    service.init();
                } catch (ServiceException e) {
                    throw new RuntimeException(e);
                }
                getRoutes().register(service.getContext());
                return service;
            }
        });
    }

    @AfterEach
    void teardownKaribu() {
        EHelper.warnHook = msg -> {
        };
        MockVaadin.tearDown();
    }

    /** Hand-crafted vector page — shapes + text + an image, the supported printing case. */
    private static void drawSample(Graphics2D g, PageFormat pf, int pageIndex) {
        g.setColor(Color.BLACK);
        g.drawString("Page " + (pageIndex + 1), (float) pf.getImageableX() + 10f, (float) pf.getImageableY() + 20f);
        g.drawRect((int) pf.getImageableX(), (int) pf.getImageableY(), 100, 50);
        g.setColor(Color.RED);
        g.fillOval((int) pf.getImageableX() + 20, (int) pf.getImageableY() + 80, 40, 40);
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        g.drawImage(img, (int) pf.getImageableX() + 80, (int) pf.getImageableY() + 80, null);
    }

    private static Printable pagesPrintable(int pages) {
        return (g, pf, pageIndex) -> {
            if (pageIndex >= pages) {
                return NO_SUCH_PAGE;
            }
            drawSample((Graphics2D) g, pf, pageIndex);
            return PAGE_EXISTS;
        };
    }

    /** Pulls the produced PDF back out through the download dialog's Anchor. */
    private static byte[] downloadedPdf() {
        return DownloadKt._download(LocatorJ._get(Anchor.class));
    }

    // ------- The core flow: render → dialog → downloadable, parseable PDF -------

    @Test
    @DisplayName("three-page Printable renders a three-page A4 PDF behind a download dialog")
    void threePagePrintableRendersAThreePageA4PdfBehindADownloadDialog() throws PrinterException, IOException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(pagesPrintable(3));
        assertTrue(job.printDialog());
        job.print();

        Dialog dialog = LocatorJ._get(Dialog.class);
        assertTrue(dialog.getHeaderTitle().contains("Java Printing.pdf")); // JDK default job name

        try (PDDocument pdf = Loader.loadPDF(downloadedPdf())) {
            assertEquals(3, pdf.getNumberOfPages());
            assertEquals(595f, pdf.getPage(0).getMediaBox().getWidth());
            assertEquals(842f, pdf.getPage(0).getMediaBox().getHeight());
        }
    }

    @Test
    @DisplayName("landscape PageFormat yields landscape-sized pages")
    void landscapePageFormatYieldsLandscapeSizedPages() throws PrinterException, IOException {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat landscape = job.defaultPage();
        landscape.setOrientation(PageFormat.LANDSCAPE);
        job.setPrintable(pagesPrintable(1), landscape);
        job.print();

        try (PDDocument pdf = Loader.loadPDF(downloadedPdf())) {
            assertEquals(842f, pdf.getPage(0).getMediaBox().getWidth());
            assertEquals(595f, pdf.getPage(0).getMediaBox().getHeight());
        }
    }

    @Test
    @DisplayName("Book drives per-page formats through the Pageable path")
    void bookDrivesPerPageFormatsThroughThePageablePath() throws PrinterException, IOException {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat portrait = job.defaultPage();
        PageFormat landscape = job.defaultPage();
        landscape.setOrientation(PageFormat.LANDSCAPE);
        Book book = new Book();
        book.append(pagesPrintable(2), portrait);
        book.append(pagesPrintable(2), landscape);
        job.setPageable(book);
        job.print();

        try (PDDocument pdf = Loader.loadPDF(downloadedPdf())) {
            assertEquals(2, pdf.getNumberOfPages());
            assertEquals(595f, pdf.getPage(0).getMediaBox().getWidth());
            assertEquals(842f, pdf.getPage(1).getMediaBox().getWidth());
        }
    }

    @Test
    @DisplayName("Pageable with UNKNOWN_NUMBER_OF_PAGES ends on the Printable's NO_SUCH_PAGE")
    void pageableWithUnknownNumberOfPagesEndsOnThePrintablesNoSuchPage() throws PrinterException, IOException {
        PrinterJob job = PrinterJob.getPrinterJob();
        Printable inner = pagesPrintable(2);
        job.setPageable(new Pageable() {
            @Override
            public int getNumberOfPages() {
                return Pageable.UNKNOWN_NUMBER_OF_PAGES;
            }

            @Override
            public PageFormat getPageFormat(int pageIndex) {
                return job.defaultPage();
            }

            @Override
            public Printable getPrintable(int pageIndex) {
                return inner;
            }
        });
        job.print();

        try (PDDocument pdf = Loader.loadPDF(downloadedPdf())) {
            assertEquals(2, pdf.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("print from a background thread delivers to the UI captured at job creation")
    void printFromABackgroundThreadDeliversToTheUiCapturedAtJobCreation() throws Throwable {
        PrinterJob job = PrinterJob.getPrinterJob(); // created in UI context
        job.setPrintable(pagesPrintable(1));
        Throwable[] failure = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                job.print();
            } catch (Throwable e) {
                failure[0] = e;
            }
        });
        t.start();
        t.join();
        if (failure[0] != null) {
            throw failure[0];
        }

        // The background ui.access queued the dialog-open; a client roundtrip drains it.
        MockVaadin.clientRoundtrip();
        LocatorJ._get(Dialog.class);
        try (PDDocument pdf = Loader.loadPDF(downloadedPdf())) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }

    // ------- Naming and stub-WARN hygiene -------

    @Test
    @DisplayName("jobName drives the download file name")
    void jobNameDrivesTheDownloadFileName() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("quarterly-report");
        job.setPrintable(pagesPrintable(1));
        job.print();
        assertTrue(LocatorJ._get(Dialog.class).getHeaderTitle().contains("quarterly-report.pdf"));
    }

    @Test
    @DisplayName("happy path fires zero stub WARNs")
    void happyPathFiresZeroStubWarns() throws PrinterException {
        List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(pagesPrintable(2));
        if (job.printDialog()) {
            job.print();
        }
        assertEquals(List.of(), warns);
    }

    @Test
    @DisplayName("copies above one WARN and still print a single copy")
    void copiesAboveOneWarnAndStillPrintASingleCopy() throws PrinterException, IOException {
        List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setCopies(3);
        assertEquals(3, job.getCopies());
        job.setPrintable(pagesPrintable(1));
        job.print();
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).contains("copies=3"));
        try (PDDocument pdf = Loader.loadPDF(downloadedPdf())) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }

    // ------- JDK-contract edges -------

    @Test
    @DisplayName("NO_SUCH_PAGE at index 0 spools nothing - no dialog, no exception")
    void noSuchPageAtIndexZeroSpoolsNothingNoDialogNoException() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(pagesPrintable(0));
        job.print();
        assertEquals(List.of(), LocatorJ._find(Dialog.class));
    }

    @Test
    @DisplayName("RuntimeException from the Printable wraps into PrinterException with cause")
    void runtimeExceptionFromThePrintableWrapsIntoPrinterExceptionWithCause() {
        PrinterJob job = PrinterJob.getPrinterJob();
        RuntimeException boom = new RuntimeException("boom");
        job.setPrintable((g, pf, pageIndex) -> {
            throw boom;
        });
        PrinterException e = assertThrows(PrinterException.class, job::print);
        assertSame(boom, e.getCause());
        assertEquals(List.of(), LocatorJ._find(Dialog.class));
    }

    @Test
    @DisplayName("PrinterException from the Printable propagates as-is")
    void printerExceptionFromThePrintablePropagatesAsIs() {
        PrinterJob job = PrinterJob.getPrinterJob();
        PrinterException pe = new PrinterException("app-level refusal");
        job.setPrintable((g, pf, pageIndex) -> {
            throw pe;
        });
        assertSame(pe, assertThrows(PrinterException.class, job::print));
    }

    @Test
    @DisplayName("print without a Printable or Pageable throws PrinterException")
    void printWithoutAPrintableOrPageableThrowsPrinterException() {
        assertThrows(PrinterException.class, () -> PrinterJob.getPrinterJob().print());
    }

    @Test
    @DisplayName("print with no UI anywhere is a programming error")
    void printWithNoUiAnywhereIsAProgrammingError() {
        UI.setCurrent(null);
        PrinterJob job = PrinterJob.getPrinterJob(); // captures no UI
        job.setPrintable(pagesPrintable(1));
        assertThrows(IllegalStateException.class, job::print);
    }

    @Test
    @DisplayName("cancel mid-print aborts with PrinterAbortException and offers no PDF")
    void cancelMidPrintAbortsWithPrinterAbortExceptionAndOffersNoPdf() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable((g, pf, pageIndex) -> {
            drawSample((Graphics2D) g, pf, pageIndex);
            job.cancel(); // in progress → takes effect; checked before the next page
            assertTrue(job.isCancelled());
            return PAGE_EXISTS;
        });
        assertThrows(PrinterAbortException.class, job::print);
        assertEquals(List.of(), LocatorJ._find(Dialog.class));
    }

    @Test
    @DisplayName("cancel outside print is a no-op per JDK semantics")
    void cancelOutsidePrintIsANoOpPerJdkSemantics() throws PrinterException, IOException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.cancel();
        assertFalse(job.isCancelled());
        job.setPrintable(pagesPrintable(1));
        job.print(); // the pre-print cancel must not abort this
        try (PDDocument pdf = Loader.loadPDF(downloadedPdf())) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("setJobName null throws NPE per JDK contract")
    void setJobNameNullThrowsNpePerJdkContract() {
        assertThrows(NullPointerException.class, () -> PrinterJob.getPrinterJob().setJobName(null));
    }

    @Test
    @DisplayName("setPageable null throws NPE per JDK contract")
    void setPageableNullThrowsNpePerJdkContract() {
        assertThrows(NullPointerException.class, () -> PrinterJob.getPrinterJob().setPageable(null));
    }

    // ------- Trivial surface -------

    @Test
    @DisplayName("pageDialog echoes its argument - no page-setup UI")
    void pageDialogEchoesItsArgumentNoPageSetupUi() {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pf = job.defaultPage();
        assertSame(pf, job.pageDialog(pf));
    }

    @Test
    @DisplayName("defaultPage is A4 portrait with one-inch margins")
    void defaultPageIsA4PortraitWithOneInchMargins() {
        PageFormat pf = PrinterJob.getPrinterJob().defaultPage();
        assertEquals(PageFormat.PORTRAIT, pf.getOrientation());
        assertEquals(595.0, pf.getWidth());
        assertEquals(842.0, pf.getHeight());
        assertEquals(72.0, pf.getImageableX());
        assertEquals(595.0 - 144, pf.getImageableWidth());
    }

    @Test
    @DisplayName("validatePage returns a clone - the virtual printer accepts any geometry")
    void validatePageReturnsACloneTheVirtualPrinterAcceptsAnyGeometry() {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pf = job.defaultPage();
        PageFormat validated = job.validatePage(pf);
        assertNotSame(pf, validated);
        assertEquals(pf.getWidth(), validated.getWidth());
    }

    @Test
    @DisplayName("lookupPrintServices hides the JDK static and returns empty")
    void lookupPrintServicesHidesTheJdkStaticAndReturnsEmpty() {
        assertEquals(0, PrinterJob.lookupPrintServices().length);
    }

    @Test
    @DisplayName("getUserName reports the server-process user like the JDK")
    void getUserNameReportsTheServerProcessUserLikeTheJdk() {
        assertEquals(System.getProperty("user.name"), PrinterJob.getPrinterJob().getUserName());
    }
}
