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

package com.vaadin.swingbridge.testapps.crud.swing;

import javax.swing.table.AbstractTableModel;

public final class EmployeeTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = { "Name", "Role", "Level", "Active", "Favorite" };

    private final EmployeeStore store;
    private boolean favoritesOnly = false;

    public EmployeeTableModel(EmployeeStore store) {
        this.store = store;
        store.addListener(this::fireTableDataChanged);
    }

    public void setFavoritesOnly(boolean favoritesOnly) {
        if (this.favoritesOnly != favoritesOnly) {
            this.favoritesOnly = favoritesOnly;
            fireTableDataChanged();
        }
    }

    public int toStoreIndex(int viewRow) {
        if (!favoritesOnly) return viewRow;
        int seen = -1;
        for (int i = 0; i < store.size(); i++) {
            if (store.get(i).isFavorite()) {
                seen++;
                if (seen == viewRow) return i;
            }
        }
        throw new IndexOutOfBoundsException("view row " + viewRow);
    }

    @Override
    public int getRowCount() {
        if (!favoritesOnly) return store.size();
        int n = 0;
        for (int i = 0; i < store.size(); i++) {
            if (store.get(i).isFavorite()) n++;
        }
        return n;
    }

    @Override
    public int getColumnCount() { return COLUMNS.length; }

    @Override
    public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 2 -> Integer.class;
            case 3, 4 -> Boolean.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        Employee e = store.get(toStoreIndex(row));
        return switch (column) {
            case 0 -> e.getName();
            case 1 -> e.getRole().name();
            case 2 -> e.getLevel();
            case 3 -> e.isActive();
            case 4 -> e.isFavorite();
            default -> null;
        };
    }
}
