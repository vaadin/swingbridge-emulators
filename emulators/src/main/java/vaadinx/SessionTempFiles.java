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

package vaadinx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

/**
 * Per-{@link VaadinSession} scratch space for files that cross the browser
 * boundary — the upload-to-temp-File (LOAD) and File-to-download (SAVE) staging
 * that {@code vaadinx.awt.FileDialog} and {@code vaadinx.swing.JFileChooser}
 * use to bridge the {@code java.io.File} semantic gap.
 *
 * <p>A browser-side {@code Upload} has no real file the server can see, so its
 * bytes are written into a session-private temp directory and handed back to
 * migrated code as an ordinary {@code java.io.File} (so {@code new
 * FileInputStream(f)} / {@code f.length()} / {@code f.getName()} work
 * unchanged). The whole directory tree is recursively deleted when the session
 * is destroyed, so temp files don't outlive the user — registered lazily and
 * exactly once per session via a self-removing {@link
 * com.vaadin.flow.server.SessionDestroyListener}.
 *
 * <p><b>Work in progress</b> alongside {@code FileDialog} / {@code JFileChooser}
 * — see those classes' headers for the slice's status ledger.
 */
public final class SessionTempFiles {

    private SessionTempFiles() {
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SessionTempFiles.class);

    // Stored as a String (not Path) so the attribute stays serializable if the
    // session is ever persisted; resolved back to a Path on read.
    private static final String ATTR = SessionTempFiles.class.getName() + ".dir";

    /**
     * The session-private temp root, created and registered for cleanup on
     * first use. Throws {@link IllegalStateException} if there is no current
     * session — file staging only happens on a UI/request thread.
     */
    public static synchronized Path sessionDir() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            throw new IllegalStateException(
                    "SessionTempFiles.sessionDir() requires a current VaadinSession — "
                            + "file staging must run on a UI/request thread");
        }
        Object existing = session.getAttribute(ATTR);
        if (existing instanceof String s) {
            return Paths.get(s);
        }
        Path dir;
        try {
            dir = Files.createTempDirectory("emul-files-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create session temp directory", e);
        }
        session.setAttribute(ATTR, dir.toString());
        registerCleanup(session, dir);
        return dir;
    }

    /**
     * A fresh empty sub-directory under {@link #sessionDir()} for one
     * file-dialog show — either direction: LOAD stages uploaded bytes here,
     * SAVE points the app's write target here. Per-show isolation keeps a later
     * dialog from clobbering temp files an earlier one handed to the app (which
     * may still hold the {@code File} reference / download link).
     */
    public static Path newTransferDir() {
        try {
            return Files.createTempDirectory(sessionDir(), "up-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload temp directory", e);
        }
    }

    private static void registerCleanup(VaadinSession session, Path dir) {
        VaadinService service = session.getService();
        if (service == null) {
            // No service to hang the listener on (unusual outside tests) — the
            // temp dir then relies on JVM-exit cleanup. Don't fail staging over it.
            log.warn("No VaadinService to register temp-dir cleanup; {} will live until JVM exit", dir);
            return;
        }
        // Self-removing one-shot listener: deletes this session's tree when the
        // session is destroyed, then unregisters itself so we don't leak a
        // listener per session across the service lifetime.
        Registration[] reg = new Registration[1];
        reg[0] = service.addSessionDestroyListener(event -> {
            if (event.getSession() == session) {
                deleteRecursively(dir);
                if (reg[0] != null) {
                    reg[0].remove();
                }
            }
        });
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            // Post-order (deepest first) so directories are empty when deleted.
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete temp file {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not walk temp directory {} for cleanup", dir, e);
        }
    }
}
