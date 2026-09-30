package com.ca.ui;

import com.gt.uilib.components.AppFrame;

import java.io.Serializable;

/**
 * The app's former {@code static} state that belongs to one running app instance (one browser tab).
 * Each member is read through the static accessor on its original class, never directly.
 */
public final class FormerSingletons implements Serializable {

    /** The holder for this app instance, created on first touch. */
    public static FormerSingletons get() {
        return vaadinx.AppInstance.get(FormerSingletons.class, FormerSingletons::new);
    }

    public AppFrame appFrame;                 // was AppFrame._instance
}
