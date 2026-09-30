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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.awt.print.PrinterJob;

import java.awt.print.PageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Virtual PDF printer WARN inventory exit gate (D_printing). Both tests fail if any
 * {@code EHelper.onUnimplemented} / {@code SHelper.onUnimplemented} fires
 * along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigate to the Sampler shell, click
 *       "Printing", run the canonical flow via "Print sample document"
 *       (getPrinterJob → setPrintable → printDialog → print), and verify the
 *       PDF download dialog appears with a working download Anchor — all
 *       WARN-free.</li>
 *   <li>{@link #inventory_printerjob_api_surface} — a micro-driver over the
 *       WARN-free PrinterJob surface (dialogs, page formats, naming, copies=1,
 *       cancel-outside-print, service lookup). {@code copies > 1} is NOT
 *       exercised here — it WARNs by design and is covered in the
 *       {@code :emulators-printing} unit test.</li>
 * </ol>
 */
class PrintingWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Printing");
        dump("Step 0b (PrintingPanel build)", warnings);

        // The canonical flow — renders a 2-page PDF and opens the download
        // dialog. The whole path is synchronous: the click's callSwing VT
        // holds the session lock, so print()'s ui.access runs inline.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Print sample document")));
        dump("Step 1 (printDialog + print → PDF download dialog)", warnings);

        LocatorJ._assertOne(Dialog.class);
        LocatorJ._get(Anchor.class, spec -> spec.withText("Download"));
        dump("Step 2 (download dialog with Anchor present)", warnings);

        WarnDump.println();
        WarnDump.println("=== printing user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_printerjob_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        PrinterJob job = PrinterJob.getPrinterJob();
        job.printDialog();
        PageFormat pf = job.defaultPage();
        job.pageDialog(pf);
        job.validatePage(pf);
        dump("PrinterJob  dialogs / page formats", warnings);

        job.setJobName("inventory");
        job.getJobName();
        job.setCopies(1);
        job.getCopies();
        job.getUserName();
        dump("PrinterJob  naming / copies / user", warnings);

        job.cancel(); // outside print — no-op per JDK semantics
        boolean unused = job.isCancelled();
        PrinterJob.lookupPrintServices();
        dump("PrinterJob  cancel outside print / service lookup", warnings);

        WarnDump.println();
        WarnDump.println("=== printerjob API-surface WARN total across buckets above ===");
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the Printing exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
