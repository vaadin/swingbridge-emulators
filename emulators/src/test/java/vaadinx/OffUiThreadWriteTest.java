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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every emulator setter works from a background thread on an attached component: R_tolerate_off_ui_thread
 * limb 3, gated over the whole {@code :emulators} surface (D_attach_aware_hop).
 *
 * <p>Reflective and allow-list-free, in {@code R12ProvenanceTest}'s manner, so a new setter is held to
 * it from its first build. For every public concrete emulator with a public no-arg constructor, the
 * component is attached and every public one-argument {@code set*} method whose
 * argument type has a sample is called from a thread carrying an {@link EmulatorContext}, twice, with
 * two different values, so a setter that writes only on change writes at least once.
 *
 * <p>What fails is a peer write that reached Vaadin without the session lock, and an echo of such a
 * write that reached {@link EHelper#callSwing} with no current UI. Any other throw is ignored: the
 * sample values are crude, and a setter rejecting one with the exception Swing would throw is not
 * this test's business.
 */
class OffUiThreadWriteTest extends AbstractKaribuTest {

    private static final Map<Class<?>, List<Object>> SAMPLES = Map.of(
            String.class, List.of("sample", ""),
            boolean.class, List.of(true, false),
            int.class, List.of(1, 0),
            java.awt.Color.class, List.of(java.awt.Color.PINK, java.awt.Color.BLUE),
            java.awt.Font.class, List.of(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13),
                    new java.awt.Font("Serif", java.awt.Font.PLAIN, 11)),
            java.awt.Dimension.class, List.of(new java.awt.Dimension(50, 20), new java.awt.Dimension(60, 30)));

    /** Blocks until the user answers, which nobody does here. */
    private static final java.util.Set<String> BLOCKING = java.util.Set.of("setModal", "setVisible");

    @Test
    @DisplayName("every emulator setter works off the UI thread on an attached component")
    void everySetterWorksOffTheUiThread() throws Exception {
        TreeMap<String, TreeSet<String>> failures = new TreeMap<>();
        int probed = 0;
        for (Class<?> type : emulatorTypes()) {
            // A fresh UI per type: an unlocked write corrupts the state tree it lands in, and
            // the corruption surfaces at some later, unrelated attach or detach.
            try {
                teardownKaribu();
            } catch (Throwable corrupted) {
                // The previous type's unlocked writes, reported below; the fresh UI is what matters.
            }
            setupKaribu();
            vaadinx.awt.Component component = attached(type);
            if (component == null) continue;
            for (Method m : type.getMethods()) {
                List<Object> samples = m.getParameterCount() == 1 ? SAMPLES.get(m.getParameterTypes()[0]) : null;
                if (samples == null || !m.getName().startsWith("set") || Modifier.isStatic(m.getModifiers())
                        || BLOCKING.contains(m.getName())) continue;
                for (Object sample : samples) {
                    probed++;
                    Throwable t = offUiThread(() -> m.invoke(component, sample));
                    if (isUnlockedWrite(t)) {
                        failures.computeIfAbsent(m.getDeclaringClass().getName() + "." + m.getName() + "("
                                + m.getParameterTypes()[0].getSimpleName() + ")", k -> new TreeSet<>())
                                .add(type.getSimpleName() + ": " + firstLine(t));
                    }
                }
            }
        }
        assertTrue(probed > 1000, "the reflective sweep found almost nothing to probe: " + probed);
        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder(failures.size()
                    + " setters write their peer without the session lock when called off the UI thread. "
                    + "Route the peer write through Component.withPeer (D_attach_aware_hop):\n");
            failures.forEach((m, where) -> sb.append("  ").append(m).append("  <- ").append(where.first())
                    .append(where.size() > 1 ? " (+" + (where.size() - 1) + " more)" : "").append('\n'));
            fail(sb.toString());
        }
    }

    private static boolean isUnlockedWrite(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = String.valueOf(c.getMessage());
            if (msg.contains("without locking the session")) return true;
            if (msg.contains("EHelper.callSwing called with no current UI")) return true;
        }
        return false;
    }

    private static String firstLine(Throwable t) {
        return t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).lines().findFirst().orElse("");
    }

    private static List<Class<?>> emulatorTypes() throws IOException {
        Path root = Path.of("target/classes");
        try (Stream<Path> s = Files.walk(root)) {
            return s.map(root::relativize).map(Path::toString)
                    .filter(p -> p.endsWith(".class") && !p.contains("$"))
                    .filter(p -> p.startsWith("vaadinx/"))
                    .map(p -> p.substring(0, p.length() - ".class".length()).replace('/', '.').replace('\\', '.'))
                    .sorted()
                    .<Class<?>>map(OffUiThreadWriteTest::load)
                    .filter(c -> vaadinx.awt.Component.class.isAssignableFrom(c))
                    .filter(c -> Modifier.isPublic(c.getModifiers()) && !Modifier.isAbstract(c.getModifiers()))
                    // A window attaches by being shown, which leaves the session in a state the
                    // next attach cannot reuse; windows are not swept yet.
                    .filter(c -> !vaadinx.awt.Window.class.isAssignableFrom(c))
                    .toList();
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    /** A new instance attached to the UI, or {@code null} for a type this sweep cannot build. */
    private static vaadinx.awt.Component attached(Class<?> type) {
        Constructor<?> ctor;
        try {
            ctor = type.getConstructor();
        } catch (NoSuchMethodException e) {
            return null;
        }
        try {
            vaadinx.awt.Component c = (vaadinx.awt.Component) ctor.newInstance();
            UI.getCurrent().add(c.getPeer());
            return c.getPeer().getUI().isPresent() ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private interface Call {
        void run() throws Exception;
    }

    /** Runs {@code call} on a background thread carrying this session's context; returns what it threw. */
    private static Throwable offUiThread(Call call) {
        EmulatorContext ctx = EmulatorContext.get();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> ctx.run(() -> {
            try {
                call.run();
            } catch (InvocationTargetException e) {
                failure.set(e.getCause());
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "off-ui-thread-write");
        t.setDaemon(true);
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        session.unlock();
        try {
            t.start();
            t.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
        return t.isAlive() ? new IllegalStateException("did not return within 5 s") : failure.get();
    }
}
