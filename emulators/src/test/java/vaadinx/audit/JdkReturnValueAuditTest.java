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

package vaadinx.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The return-value axis as a build gate: a real JDK answers every shadowed
 * setter/getter pair, and a disagreement nobody has written a reason for is a
 * build failure (D_return_value_audit).
 *
 * <p>To make a divergence legal, add a row to
 * {@code src/test/resources/vaadinx/audit/sanctioned-divergences.tsv} naming
 * the rule that permits it — {@code setText(null)} reading back as {@code ""}
 * is R_vaadin_first working as designed. <b>Never add a row to quiet the
 * build.</b> A row is a claim, and an unreviewed one leaves the tests green
 * over a real bug, which is the failure SD_sjpasswordfield records.
 *
 * <p>Owns its own {@code MockVaadin} lifecycle rather than extending
 * {@code AbstractKaribuTest}, because {@link JdkReturnValueDiffer}'s
 * {@code main} needs that setup too.
 *
 * <p>One {@code @Test} for the whole sweep rather than one per pair, and
 * not because of the runtime: the pair set is discovered by
 * reflection at run time, so a per-pair test would make its own
 * discovery the unit of work and a pair that stopped being found
 * would vanish silently instead of failing.
 */
class JdkReturnValueAuditTest {

    @Test
    @DisplayName("no unsanctioned divergence between SB-Emulators and a real JDK")
    void noUnsanctionedDivergences() throws Exception {
        JdkReturnValueDiffer differ = JdkReturnValueDiffer.sweepUnderKaribuUI();
        List<String> unsanctioned = differ.unsanctioned();
        assertEquals(List.<String>of(), unsanctioned, differ::report);
    }
}
