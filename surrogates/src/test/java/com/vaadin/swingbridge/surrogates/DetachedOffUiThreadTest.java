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

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Vaadin behaviour SB-Emulators depends on today: a surrogate can be constructed and
 * mutated on a thread with no Vaadin service, session or UI, as long as it has never been
 * attached. An emulator built in a {@code SwingWorker.doInBackground()} builds its surrogate
 * exactly that way.
 *
 * <p>That is an inference from how Flow is built, not a contract Vaadin documents, which is why
 * it is pinned: this test goes red on the Vaadin upgrade that breaks it, before a migrator finds
 * out. The way out of the dependency altogether is {@code ideas/vaadin-ui-thread-only.md}.
 *
 * <p>No Karibu here, deliberately. Setters are swept reflectively with two sample values each;
 * a rejection Swing itself would make (an {@code IllegalArgumentException} for a bad index, say)
 * is not this test's business, but a thread, session, UI or lock complaint is.
 */
class DetachedOffUiThreadTest {

    private static final Map<Class<?>, List<Object>> SAMPLES = Map.of(
            String.class, List.of("sample", ""),
            boolean.class, List.of(true, false),
            int.class, List.of(1, 0),
            double.class, List.of(1.0, 0.0),
            java.awt.Color.class, List.of(java.awt.Color.PINK, java.awt.Color.BLUE));

    @Test
    @DisplayName("every surrogate constructs and mutates on a plain thread while never attached")
    void surrogatesWorkDetachedOnAPlainThread() throws Exception {
        TreeMap<String, String> failures = new TreeMap<>();
        int probed = 0;
        for (Class<?> type : SurrogateTypes.all()) {
            Constructor<?> ctor = SurrogateTypes.noArgConstructor(type);
            if (ctor == null) continue;
            AtomicReference<Throwable> failure = new AtomicReference<>();
            int[] calls = {0};
            Thread t = new Thread(() -> {
                Component c;
                try {
                    c = (Component) ctor.newInstance();
                } catch (Throwable e) {
                    failure.set(new AssertionError("<init>", unwrap(e)));
                    return;
                }
                for (Method m : type.getMethods()) {
                    List<Object> samples = m.getParameterCount() == 1 ? SAMPLES.get(m.getParameterTypes()[0]) : null;
                    if (samples == null || !m.getName().startsWith("set") || Modifier.isStatic(m.getModifiers())) continue;
                    // Showing a window attaches it, which is not a detached mutation.
                    if (c instanceof SWindow && (m.getName().equals("setVisible") || m.getName().equals("setOpened"))) continue;
                    for (Object sample : samples) {
                        calls[0]++;
                        try {
                            m.invoke(c, sample);
                        } catch (Throwable e) {
                            Throwable cause = unwrap(e);
                            if (!isSwingShapedRejection(cause)) {
                                failure.compareAndSet(null, new AssertionError(m.getName(), cause));
                            }
                        }
                    }
                }
            }, "detached-off-ui-thread");
            t.start();
            t.join(10_000);
            probed += calls[0];
            if (t.isAlive()) {
                failures.put(type.getName(), "did not finish within 10 s");
            } else if (failure.get() != null) {
                Throwable f = failure.get();
                failures.put(type.getName(), f.getMessage() + ": " + f.getCause());
            }
        }
        assertTrue(probed > 500, "the sweep found almost nothing to probe: " + probed);
        assertEquals(Map.of(), failures, "a surrogate no longer works detached on a plain thread — "
                + "a Vaadin upgrade has changed the behaviour SB-Emulators relies on; see "
                + "ideas/vaadin-ui-thread-only.md");
    }

    private static Throwable unwrap(Throwable e) {
        return e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
    }

    /** A rejection of a crude sample value, of the kind Swing itself makes. */
    private static boolean isSwingShapedRejection(Throwable e) {
        return e instanceof IllegalArgumentException
                || e instanceof IndexOutOfBoundsException
                || e instanceof UnsupportedOperationException
                || e instanceof java.io.IOException;
    }
}
