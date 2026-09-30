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

import vaadinx.EHelper;
import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.SwingWorker;
import vaadinx.swing.Timer;
import vaadinx.swing.event.AncestorEvent;
import vaadinx.swing.event.AncestorListener;

import java.beans.PropertyChangeEvent;
import java.util.List;

/**
 * Timer + SwingWorker demo (D_timer_swingworker). Two interactive surfaces:
 *
 * <ul>
 *   <li>{@link Timer} — start / stop a 500-ms repeating timer that
 *       increments a tick counter on the EDT. Exercises the per-UI
 *       scheduler thread → {@code ui.access} → {@link EHelper#callSwing}
 *       → ActionListener fan-out path.</li>
 *   <li>{@link SwingWorker} — run a fake 5-step "load" that
 *       {@code publish}es chunks to update a status label and
 *       {@code setProgress}es 0..100. {@code done()} re-enables the
 *       Run button. The progress label subscribes to PCE.</li>
 * </ul>
 *
 * <h2>Lifecycle teardown via {@link AncestorListener}</h2>
 * Timer and SwingWorker live independently of the component tree —
 * the per-UI Timer scheduler keeps firing into our ActionListener and
 * SwingWorker keeps publishing chunks regardless of whether the panel
 * is still in the showing tree. After a swap to another demo, those
 * fires would land on JLabels whose peers are no longer attached.
 *
 * <p>{@code ancestorRemoved} stops the timer ({@link Timer#stop()}) and
 * cancels any in-flight worker ({@link SwingWorker#cancel(boolean)} with
 * {@code mayInterruptIfRunning=true}, which interrupts the doInBackground
 * thread). The cancel race is handled by SwingWorker itself — callbacks
 * after cancel arrive on a worker that reports {@code isCancelled()}, so
 * {@code done()}'s {@code worker.get()} throws {@link java.util.concurrent.CancellationException}
 * and the catch path runs (the panel is gone by then anyway, so the
 * caught-status setter is a no-op visually).
 */
public class TimersAndWorkersPanel extends JPanel {

    private final JLabel timerStatus = new JLabel("Timer stopped (ticks: 0)");
    private final JLabel workerStatus = new JLabel("Worker idle.");
    private final JLabel workerProgress = new JLabel("Progress: -");

    private int ticks;
    private final Timer timer;
    private SwingWorker<String, String> currentWorker;

    public TimersAndWorkersPanel() {
        super(new BorderLayout(8, 8));

        timer = new Timer(500, e -> {
            ticks++;
            timerStatus.setText("Timer running (ticks: " + ticks + ")");
        });

        JButton startTimer = new JButton("Start timer");
        startTimer.addActionListener(e -> {
            timer.start();
            timerStatus.setText("Timer running (ticks: " + ticks + ")");
        });

        JButton stopTimer = new JButton("Stop timer");
        stopTimer.addActionListener(e -> {
            timer.stop();
            timerStatus.setText("Timer stopped (ticks: " + ticks + ")");
        });

        JButton runWorker = new JButton("Run worker");
        runWorker.addActionListener(e -> {
            runWorker.setEnabled(false);
            workerStatus.setText("Worker started…");
            workerProgress.setText("Progress: 0%");
            startFakeLoad(runWorker);
        });

        JPanel timerPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        timerPanel.add(startTimer);
        timerPanel.add(stopTimer);
        timerPanel.add(timerStatus);

        JPanel workerPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        workerPanel.add(runWorker);
        workerPanel.add(workerStatus);
        workerPanel.add(workerProgress);

        add(timerPanel, BorderLayout.NORTH);
        add(workerPanel, BorderLayout.CENTER);

        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                // Nothing to do on attach — timer starts via its own
                // button, worker via its own button.
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
                // Stop the timer and cancel any in-flight worker so they
                // don't keep firing into a detached component tree.
                timer.stop();
                if (currentWorker != null && !currentWorker.isDone()) {
                    currentWorker.cancel(true);
                }
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {
                // Not fired by our wiring (Vaadin has no server-side
                // layout-position event). No-op.
            }
        });
    }

    private void startFakeLoad(JButton runButton) {
        SwingWorker<String, String> worker = new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() throws Exception {
                final int steps = 5;
                for (int i = 1; i <= steps; i++) {
                    Thread.sleep(150);
                    publish("step " + i + " of " + steps);
                    setProgress((int) (100.0 * i / steps));
                }
                return "loaded " + steps + " steps";
            }

            @Override
            protected void process(List<String> chunks) {
                workerStatus.setText("Worker: " + chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    workerStatus.setText("Worker done — " + result);
                } catch (java.util.concurrent.CancellationException ex) {
                    // Cancellation is expected on nav-away — ancestorRemoved
                    // calls worker.cancel(true). Nothing to display since
                    // the panel is detached.
                } catch (Exception ex) {
                    workerStatus.setText("Worker failed: " + ex.getMessage());
                }
                runButton.setEnabled(true);
            }
        };
        worker.addPropertyChangeListener((PropertyChangeEvent pce) -> {
            if (SwingWorker.PROGRESS_PROPERTY.equals(pce.getPropertyName())) {
                workerProgress.setText("Progress: " + pce.getNewValue() + "%");
            }
        });
        currentWorker = worker;
        worker.execute();
    }
}
