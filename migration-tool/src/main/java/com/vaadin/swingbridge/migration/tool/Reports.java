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

package com.vaadin.swingbridge.migration.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The {@code --report} write both tools do, with the directory created rather than demanded.
 *
 * <p><b>A report path is an output, not an input.</b> Every guide invocation writes into
 * {@code MIGRATED_APP_FOLDER/target/}, and the phases that write one run <em>before</em> the app's
 * first build — so on a freshly-copied or freshly-cloned tree that directory does not exist yet, and
 * a bare {@link Files#writeString} fails with a {@code NoSuchFileException} whose message is the
 * path alone. That read as a success line and cost a migration run a detour; hence this class rather
 * than a {@code mkdir} in the guide's command block, which would leave the same trap for anyone
 * invoking the tool a slightly different way.
 *
 * <p>Lives here, beside {@link Sources}, under this package's membership rule: both tools use it.
 *
 * <p>{@code public} only so the tool subpackages can reach it; jar-internal like everything
 * else here, and not part of any surface a migrator calls.
 */
public final class Reports {

    private Reports() {
    }

    /**
     * @param reportPath where to write; its parent directories are created if missing
     * @param rendered the whole report, UTF-8
     */
    public static void write(Path reportPath, String rendered) throws IOException {
        Path parent = reportPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(reportPath, rendered, StandardCharsets.UTF_8);
    }
}
