package com.ca.ui;

import com.gt.uilib.components.AppFrame;

import java.io.Serializable;

/**
 * The tab-scoped home of the app's former {@code static} state — one instance per running app
 * (browser tab). Each member is read through the static accessor on its original class, never
 * directly: {@code AppFrame.getInstance()} reads {@link #appFrame}.
 */
public final class FormerSingletons implements Serializable {

    public static FormerSingletons get() {
        return vaadinx.AppInstance.get(FormerSingletons.class, FormerSingletons::new);
    }

    public AppFrame appFrame;               // was AppFrame._instance
}
