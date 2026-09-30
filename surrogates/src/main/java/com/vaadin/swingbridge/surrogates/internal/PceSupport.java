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

package com.vaadin.swingbridge.surrogates.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Per-component {@link PropertyChangeSupport} backing the
 * {@code addPropertyChangeListener} / {@code firePropertyChange} family on
 * {@code ComponentMixin}. Lazily attached via Vaadin's
 * {@link ComponentUtil#setData} per SD_mixin_mechanism — no boilerplate field on each
 * surrogate.
 */
public final class PceSupport {

    private final PropertyChangeSupport delegate;

    private PceSupport(Object source) {
        this.delegate = new PropertyChangeSupport(source);
    }

    /** Lazy-create-or-fetch the holder for {@code target}. */
    public static PceSupport of(Component target) {
        PceSupport existing = ComponentUtil.getData(target, PceSupport.class);
        if (existing != null) return existing;
        PceSupport fresh = new PceSupport(target);
        ComponentUtil.setData(target, PceSupport.class, fresh);
        return fresh;
    }

    public void add(PropertyChangeListener l) {
        delegate.addPropertyChangeListener(l);
    }

    public void add(String propertyName, PropertyChangeListener l) {
        delegate.addPropertyChangeListener(propertyName, l);
    }

    public void remove(PropertyChangeListener l) {
        delegate.removePropertyChangeListener(l);
    }

    public void remove(String propertyName, PropertyChangeListener l) {
        delegate.removePropertyChangeListener(propertyName, l);
    }

    public PropertyChangeListener[] getListeners() {
        return delegate.getPropertyChangeListeners();
    }

    public PropertyChangeListener[] getListeners(String propertyName) {
        return delegate.getPropertyChangeListeners(propertyName);
    }

    public void fire(String propertyName, Object oldValue, Object newValue) {
        delegate.firePropertyChange(propertyName, oldValue, newValue);
    }

    public void fire(PropertyChangeEvent evt) {
        delegate.firePropertyChange(evt);
    }
}
