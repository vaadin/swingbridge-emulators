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

package vaadinx.awt.datatransfer;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot {@link Transferable} returned from {@link WebClipboard#getContents}.
 * Advertises only the flavors that were actually present in the browser-side
 * read result — empty clipboard → empty flavor array; text-only → just
 * {@link DataFlavor#stringFlavor}; image-only → just {@link DataFlavor#imageFlavor}.
 * Throws {@link UnsupportedFlavorException} from {@link #getTransferData} for
 * any other flavor per the JDK contract.
 *
 * <p>JDK-faithful snapshot semantics: the held text / image is captured at
 * read-time and does not refresh on subsequent {@code getTransferData} calls.
 * {@link java.awt.datatransfer.Clipboard#getContents}'s contract is the same.
 *
 * <p>Package-private — instantiated only by {@link WebClipboard}; user code
 * receives it typed as {@link Transferable}.
 */
final class WebClipboardSnapshot implements Transferable {

    /** Sentinel snapshot used when a read yielded no usable data (or hit a runtime browser error). */
    static final WebClipboardSnapshot EMPTY = new WebClipboardSnapshot(null, null);

    private final String text;
    private final BufferedImage image;

    WebClipboardSnapshot(String text, BufferedImage image) {
        this.text = text;
        this.image = image;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        List<DataFlavor> flavors = new ArrayList<>(2);
        if (text != null) flavors.add(DataFlavor.stringFlavor);
        if (image != null) flavors.add(DataFlavor.imageFlavor);
        return flavors.toArray(new DataFlavor[0]);
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        if (flavor == null) return false;
        if (text != null && DataFlavor.stringFlavor.equals(flavor)) return true;
        // Match against imageFlavor explicitly, and against any caller-defined
        // flavor whose representation class is Image-assignable — same shape
        // the JDK uses internally so user-defined image flavors round-trip.
        if (image != null) {
            if (DataFlavor.imageFlavor.equals(flavor)) return true;
            Class<?> rep = flavor.getRepresentationClass();
            return rep != null && Image.class.isAssignableFrom(rep);
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        if (DataFlavor.stringFlavor.equals(flavor)) return text;
        return image;
    }
}
