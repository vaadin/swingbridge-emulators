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
import com.github.mvysny.kaributesting.v10.UploadKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.upload.Upload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * FileDialogsPanel WARN inventory exit gate.
 * Drives the AWT FileDialog LOAD user path end-to-end and fails if any
 * {@code EHelper.onUnimplemented} fires:
 *
 * <ol>
 *   <li>nav to the panel → click "Open file" → a modal FileDialog (LOAD)
 *       opens, hosting a Vaadin Upload, parking the click VT on the modal
 *       latch;</li>
 *   <li>simulate a browser upload → bytes stage to the per-session temp dir
 *       and the UI.access success callback enables OK;</li>
 *   <li>click OK → dispose unparks the VT → status reads the staged
 *       {@code java.io.File} (name + length + content preview).</li>
 * </ol>
 *
 * <p>Covers all five wired user paths — FileDialog LOAD (single + multi-select)
 * + SAVE and JFileChooser open + save — and fails if any exercises an
 * unimplemented surface.
 */
class FileDialogsWarnInventoryTest {

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
    }

    @Test
    void inventory_filedialog_load_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("File dialogs");
        dump("Step 0b (FileDialogsPanel swap)", warnings);

        // Click "Open file" — handler shows a modal FileDialog (LOAD), which
        // parks the click VT on the inherited modal latch until OK / Cancel.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Open file")));
        dump("Step 1 (open FileDialog LOAD — Upload hosted, VT parked)", warnings);

        // Simulate a browser upload — bytes stage to the per-session temp dir;
        // the UI.access success callback enables OK.
        UploadKt._upload(LocatorJ._get(Upload.class), "report.txt", "text/plain",
                "quarterly numbers".getBytes(StandardCharsets.UTF_8));
        dump("Step 2 (upload staged to temp File)", warnings);

        // Click OK — disposes the dialog, unparks the VT, status reads back the
        // staged java.io.File (name + length + content preview).
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("OK")));
        dump("Step 3 (OK dispose → status reads the temp File)", warnings);

        WarnDump.println();
        WarnDump.println("=== file-dialogs LOAD user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_filedialog_multiselect_load_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        Navigate.to("File dialogs");
        dump("Step 0 (FileDialogsPanel swap)", warnings);

        // Click "Open files (multi-select)" — handler shows a modal FileDialog
        // (LOAD) with setMultipleMode(true), so the hosted Upload has no 1-file
        // cap; the click VT parks on the modal latch.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Open files (multi-select)")));
        dump("Step 1 (open multi-select FileDialog LOAD — VT parked)", warnings);

        // Stage two uploads — each success callback appends to the per-show temp
        // dir and enables OK. This is the path getFiles() (plural) reads back.
        Upload upload = LocatorJ._get(Upload.class);
        UploadKt._upload(upload, "q1.txt", "text/plain",
                "first quarter".getBytes(StandardCharsets.UTF_8));
        UploadKt._upload(upload, "q2.txt", "text/plain",
                "second quarter".getBytes(StandardCharsets.UTF_8));
        dump("Step 2 (two uploads staged)", warnings);

        // Click OK — dispose unparks the VT; the handler reads fd.getFiles()
        // and the status enumerates both staged java.io.Files.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("OK")));
        dump("Step 3 (OK dispose → status reads getFiles())", warnings);

        WarnDump.println();
        WarnDump.println("=== file-dialogs multi-select LOAD user-path WARN total: "
                + warnings.size() + " ===");
    }

    @Test
    void inventory_filedialog_save_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        Navigate.to("File dialogs");
        dump("Step 0 (FileDialogsPanel swap)", warnings);

        // Click "Save file" — opens the modal name-pick FileDialog (SAVE),
        // parking the click VT until Save / Cancel.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Save file")));
        dump("Step 1 (open SAVE name dialog — VT parked)", warnings);

        // Accept the pre-filled name. Save disposes the modal dialog, unparks
        // the VT; the handler writes the temp file and the synthetic, non-modal
        // download dialog opens with a Download link.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Save")));
        dump("Step 2 (Save → write temp file + open download dialog)", warnings);

        WarnDump.println();
        WarnDump.println("=== file-dialogs SAVE user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jfilechooser_open_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        Navigate.to("File dialogs");
        dump("Step 0 (FileDialogsPanel swap)", warnings);

        // Click "Open with JFileChooser" — handler shows a JFileChooser whose
        // internal modal JDialog hosts a Vaadin Upload (with an extension filter),
        // parking the click VT until the approve / cancel.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Open with JFileChooser")));
        dump("Step 1 (open JFileChooser — Upload hosted, VT parked)", warnings);

        // Simulate a browser upload matching the .txt/.csv filter — bytes stage
        // to the per-session temp dir and the success callback enables Open.
        UploadKt._upload(LocatorJ._get(Upload.class), "report.txt", "text/plain",
                "quarterly numbers".getBytes(StandardCharsets.UTF_8));
        dump("Step 2 (upload staged to temp File)", warnings);

        // Click Open (the chooser's approve button) — disposes the JDialog,
        // unparks the VT, status reads back the staged java.io.File.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Open")));
        dump("Step 3 (Open dispose → status reads the temp File)", warnings);

        WarnDump.println();
        WarnDump.println("=== jfilechooser open user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jfilechooser_save_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        Navigate.to("File dialogs");
        dump("Step 0 (FileDialogsPanel swap)", warnings);

        // Click "Save with JFileChooser" — opens the modal name-pick JDialog,
        // parking the click VT until Save / Cancel.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Save with JFileChooser")));
        dump("Step 1 (open JFileChooser SAVE name dialog — VT parked)", warnings);

        // Accept the pre-filled name. Save disposes the JDialog, unparks the VT;
        // the handler writes the temp file and the synthetic download dialog opens.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Save")));
        dump("Step 2 (Save → write temp file + open download dialog)", warnings);

        WarnDump.println();
        WarnDump.println("=== jfilechooser save user-path WARN total: " + warnings.size() + " ===");
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
                    + " stub WARN(s) fired — regression in the FileDialogs exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
