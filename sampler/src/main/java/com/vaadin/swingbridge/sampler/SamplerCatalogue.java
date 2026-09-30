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

import vaadinx.swing.JComponent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * The single declarative roster of Sampler demos. Nav renderers and the
 * navigation gate derive from it instead of hand-listing demos, so adding one
 * is a one-line append here and nowhere else:
 *
 * <pre>{@code
 * for (Demo d : SamplerCatalogue.ALL) showDemo(d.factory().get());
 * }</pre>
 *
 * <p>Entry order is load-bearing: a renderer emits a category's header the
 * first time that category appears, so one category's entries must stay
 * contiguous.
 */
public final class SamplerCatalogue {

    /**
     * @param category the accordion group this demo sits under, or {@code null}
     *                 for a top-level leaf outside every group (Home)
     * @param label    the nav button caption, and the key every test navigates
     *                 by — treat it as stable API
     * @param factory  builds a fresh panel per navigation; demos are never
     *                 cached, which is what keeps a panel's ctor on the
     *                 WARN-inventory path
     */
    public record Demo(String category, String label, Supplier<JComponent> factory) {}

    public static final List<Demo> ALL = List.of(
            new Demo(null, "Home", HomePanel::new),

            new Demo("Forms", "Form", FormPanel::new),
            new Demo("Forms", "Edit form", EditFormPanel::new),
            new Demo("Forms", "Inputs", InputsPanel::new),
            new Demo("Forms", "Formatted fields", FormattedFieldsPanel::new),
            new Demo("Forms", "Dates", DatesPanel::new),
            new Demo("Forms", "ComboBoxes", ComboBoxesPanel::new),
            new Demo("Forms", "RadioButtons", RadioButtonsPanel::new),

            new Demo("Buttons & actions", "Buttons", ButtonsPanel::new),
            new Demo("Buttons & actions", "ToolBars", ToolBarsPanel::new),
            new Demo("Buttons & actions", "Menus", MenusPanel::new),
            new Demo("Buttons & actions", "PopupMenus", PopupMenusPanel::new),

            new Demo("Data", "Tables", TablesPanel::new),
            new Demo("Data", "Lists", ListsPanel::new),
            new Demo("Data", "Trees", TreesPanel::new),

            new Demo("Containers", "Layouts", LayoutsPanel::new),
            new Demo("Containers", "ScrollPanes", ScrollPanesPanel::new),
            new Demo("Containers", "SplitPanes", SplitPanesPanel::new),
            new Demo("Containers", "TabbedPanes", TabbedPanesPanel::new),
            new Demo("Containers", "Separators", SeparatorsPanel::new),

            new Demo("Windows & dialogs", "Windows", WindowsPanel::new),
            new Demo("Windows & dialogs", "Dialogs", DialogsPanel::new),
            new Demo("Windows & dialogs", "Option panes", OptionPanesPanel::new),
            new Demo("Windows & dialogs", "File dialogs", FileDialogsPanel::new),
            new Demo("Windows & dialogs", "ColorChoosers", ColorChoosersPanel::new),
            new Demo("Windows & dialogs", "Desktop", DesktopPanel::new),

            new Demo("Text", "Caret", CaretPanel::new),
            new Demo("Text", "EditorPanes", EditorPanesPanel::new),
            new Demo("Text", "HtmlViewer", HtmlViewerPanel::new),

            new Demo("Async", "Timers", TimersAndWorkersPanel::new),
            new Demo("Async", "ProgressBars", ProgressBarsPanel::new),

            new Demo("Platform", "Focus", FocusPanel::new),
            new Demo("Platform", "Clipboard", ClipboardPanel::new),
            new Demo("Platform", "DnD", DragAndDropPanel::new),
            new Demo("Platform", "Preferences", PreferencesPanel::new),
            new Demo("Platform", "Printing", PrintingPanel::new),

            // One leaf per AWT class, not one "AWT widgets" omnibus: the AWT
            // lane's teaching content is per-class divergence (which setters
            // are silent, which constants collide, what getSelectedObjects
            // returns), so the pane boundary that scales is the class.
            new Demo("AWT", "Button", AwtButtonPanel::new),
            new Demo("AWT", "Label", AwtLabelPanel::new),
            new Demo("AWT", "Choice", AwtChoicePanel::new),
            new Demo("AWT", "Checkbox", AwtCheckboxPanel::new),
            new Demo("AWT", "Panel", AwtPanelPanel::new),
            new Demo("AWT", "Scrollbar", AwtScrollbarPanel::new),
            new Demo("AWT", "List", AwtListPanel::new),
            new Demo("AWT", "ScrollPane", AwtScrollPanePanel::new));

    private SamplerCatalogue() {}

    /** @return the distinct non-null categories, in {@link #ALL} order */
    public static List<String> categories() {
        return new ArrayList<>(new LinkedHashSet<>(
                ALL.stream().filter(d -> d.category() != null).map(Demo::category).toList()));
    }

    /** @throws IllegalArgumentException if no demo carries that label */
    public static Demo byLabel(String label) {
        return ALL.stream().filter(d -> d.label().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No demo labelled '" + label + "'"));
    }
}
