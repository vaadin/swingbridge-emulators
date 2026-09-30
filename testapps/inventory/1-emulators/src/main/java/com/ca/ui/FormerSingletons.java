package com.ca.ui;

import com.gt.uilib.components.AppFrame;
import vaadinx.AppInstance;

import java.io.Serializable;

/**
 * Tab-scoped home of the app's former {@code static} state: one instance per running app instance
 * (browser tab). Read only through the accessor on each member's original class.
 */
public final class FormerSingletons implements Serializable {

    /** The holder for this app instance, created on first touch. */
    public static FormerSingletons get() {
        return AppInstance.get(FormerSingletons.class, FormerSingletons::new);
    }

    public AppFrame appFrame;   // was AppFrame._instance; read via AppFrame.getInstance()
}
