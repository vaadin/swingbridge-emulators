package com.formdev.flatlaf;

/**
 * Mock of FlatLaf's {@code FlatClientProperties}, narrowed to the outline
 * client-property keys the {@code InvoicePositionEntryPanel} slice sets via
 * {@code JComponent.putClientProperty(...)}. FlatLaf is not on this pristine-
 * Swing testbed's classpath, so these constants only need to exist for the
 * panel to compile and run; without the FlatLaf Look-and-Feel installed the
 * outline styling they request is simply not painted (the {@code putClientProperty}
 * calls store the values harmlessly).
 */
public class FlatClientProperties {

    public static final String OUTLINE = "JComponent.outline";
    public static final String OUTLINE_ERROR = "error";
    public static final String OUTLINE_WARNING = "warning";
}
