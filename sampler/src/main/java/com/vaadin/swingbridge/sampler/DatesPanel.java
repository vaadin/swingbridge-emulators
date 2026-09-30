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

import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;
import vaadinx.text.SimpleDateFormat;
import vaadinx.util.Calendar;
import vaadinx.util.GregorianCalendar;

import java.util.Date;

/**
 * Demo for the date emulators (D_date_emulators): {@link SimpleDateFormat} and
 * {@link Calendar} / {@link GregorianCalendar}. Both default to the user's
 * <em>browser</em> time zone, so formatting/parsing and calendar field math
 * produce the wall-date the user expects rather than the server's.
 *
 * <ul>
 *   <li><b>SimpleDateFormat</b> — a {@code static final} formatter (the classic
 *       Swing idiom, safe under the emulator: construction never reads the zone,
 *       and each {@code format}/{@code parse} runs on a per-session, browser-zoned,
 *       thread-safe backing) formats "now" and parses a typed string back to a
 *       {@link Date}.</li>
 *   <li><b>Calendar / GregorianCalendar</b> — build a fixed date, run inherited
 *       field math ({@code add}) and read fields back — all interpreted in the
 *       browser zone.</li>
 * </ul>
 *
 * <p>No {@link vaadinx.swing.event.AncestorListener} teardown needed (unlike the
 * Timer/SwingWorker panel): nothing here runs off the EDT or outlives the panel.
 */
@SuppressWarnings("deprecation") // deliberately exercises the @Deprecated date emulators — that's the demo
public class DatesPanel extends JPanel {

    // The canonical `static final SimpleDateFormat` idiom. Safe under the
    // emulator (D_date_emulators): construction never touches the zone (deferred to first
    // use), and every format/parse runs on a per-session, browser-zoned,
    // thread-safe backing — so sharing one instance across the app is fine.
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final JLabel nowLabel = new JLabel(" ");
    private final JTextField parseInput = new JTextField("2020-06-15 12:30:00", 18);
    private final JLabel parseResult = new JLabel(" ");

    private final GregorianCalendar cal = new GregorianCalendar(2020, Calendar.JUNE, 15);
    private final JLabel calLabel = new JLabel(" ");

    public DatesPanel() {
        super(new BorderLayout(8, 8));
        add(buildSdfSection(), BorderLayout.NORTH);
        add(buildCalendarSection(), BorderLayout.CENTER);
        refreshNow();
        refreshCalendar();
    }

    private JPanel buildSdfSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("SimpleDateFormat (browser-zoned)"));

        JPanel nowRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        JButton formatNow = new JButton("Format now");
        formatNow.addActionListener(e -> refreshNow());
        nowRow.add(formatNow);
        nowRow.add(nowLabel);

        JPanel parseRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        JButton parse = new JButton("Parse");
        parse.addActionListener(e -> parse());
        parseRow.add(new JLabel("Parse \"yyyy-MM-dd HH:mm:ss\":"));
        parseRow.add(parseInput);
        parseRow.add(parse);
        parseRow.add(parseResult);

        section.add(nowRow);
        section.add(parseRow);
        return section;
    }

    private JPanel buildCalendarSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Calendar / GregorianCalendar (browser-zoned)"));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        JButton addMonth = new JButton("Add 1 month");
        addMonth.addActionListener(e -> {
            cal.add(Calendar.MONTH, 1);
            refreshCalendar();
        });
        JButton addYear = new JButton("Add 1 year");
        addYear.addActionListener(e -> {
            cal.add(Calendar.YEAR, 1);
            refreshCalendar();
        });
        JButton reset = new JButton("Reset to 2020-06-15");
        reset.addActionListener(e -> {
            cal.set(2020, Calendar.JUNE, 15, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            refreshCalendar();
        });
        row.add(addMonth);
        row.add(addYear);
        row.add(reset);

        section.add(row);
        section.add(calLabel);
        return section;
    }

    private void refreshNow() {
        nowLabel.setText("Now: " + FMT.format(new Date()));
    }

    private void parse() {
        try {
            Date d = FMT.parse(parseInput.getText());
            parseResult.setText("→ re-formatted: " + FMT.format(d) + "  (epoch millis " + d.getTime() + ")");
        } catch (java.text.ParseException ex) {
            parseResult.setText("→ unparseable: " + ex.getMessage());
        }
    }

    private void refreshCalendar() {
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1; // 0-based → human
        int day = cal.get(Calendar.DAY_OF_MONTH);
        calLabel.setText(String.format("fields: %04d-%02d-%02d  ·  getTime() → %s",
                year, month, day, FMT.format(cal.getTime())));
    }
}
