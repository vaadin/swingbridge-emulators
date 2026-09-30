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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.text.SimpleDateFormat;
import vaadinx.util.Calendar;
import vaadinx.util.GregorianCalendar;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * {@link DatesPanel} (date-emulator demo, D_date_emulators) WARN inventory exit gate.
 * Both tests fail if any {@code EHelper.onUnimplemented} / {@code onUnsupportedPeerShape}
 * fires.
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the panel and drives its
 *       surfaces: format "now", parse a typed string, and run the Calendar
 *       add/reset field math.</li>
 *   <li>{@link #inventory_api_surface} — a micro-driver over the
 *       {@link SimpleDateFormat} + {@link Calendar} / {@link GregorianCalendar}
 *       API (format/parse, applyPattern, lenient, explicit/implicit zone;
 *       getInstance, field set/get, add/roll, getTime).</li>
 * </ol>
 */
@SuppressWarnings("deprecation") // deliberately drives the @Deprecated date emulators
class DatesWarnInventoryTest {

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
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Dates");
        dump("Step 0b (DatesPanel swap — static-final SimpleDateFormat + GregorianCalendar ctor)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Format now")));
        dump("Step 1 (SimpleDateFormat.format(now))", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Parse")));
        dump("Step 2 (SimpleDateFormat.parse)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Add 1 month")));
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Add 1 year")));
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Reset to 2020-06-15")));
        dump("Step 3 (Calendar add/reset field math)", warnings);

        WarnDump.println();
        WarnDump.println("=== dates user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_api_surface() throws Exception {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- SimpleDateFormat: format / parse / config, browser-zoned --
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        fmt.setLenient(false);
        fmt.applyPattern("yyyy-MM-dd");
        fmt.toPattern();
        String s = fmt.format(new Date(0));
        fmt.parse(s);
        fmt.getTimeZone();
        // explicit zone wins over the browser default
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        utc.setTimeZone(TimeZone.getTimeZone("GMT+03:00"));
        utc.format(new Date(0));
        dump("SimpleDateFormat  format/parse/config", warnings);

        // -- Calendar / GregorianCalendar: build, field math, read back --
        Calendar now = Calendar.getInstance();
        now.get(Calendar.YEAR);
        now.getTime();
        GregorianCalendar g = new GregorianCalendar(2020, Calendar.JANUARY, 31);
        g.add(Calendar.MONTH, 1);       // Jan 31 → Feb 29 (2020 leap)
        g.roll(Calendar.DAY_OF_MONTH, true);
        g.set(2019, Calendar.MARCH, 3, 4, 5, 6);
        g.set(Calendar.MILLISECOND, 0);
        g.get(Calendar.DAY_OF_MONTH);
        g.getTime();
        g.getTimeZone();
        // explicit zone honored
        GregorianCalendar zoned = new GregorianCalendar(TimeZone.getTimeZone("GMT+03:00"));
        zoned.getTime();
        dump("Calendar / GregorianCalendar  build + field math", warnings);

        WarnDump.println();
        WarnDump.println("=== dates API-surface WARN total: " + warnings.size() + " ===");
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
                    + " stub WARN(s) fired — regression in the Dates exit gate: " + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
