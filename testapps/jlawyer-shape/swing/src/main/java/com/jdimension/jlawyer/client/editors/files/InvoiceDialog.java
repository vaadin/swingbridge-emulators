package com.jdimension.jlawyer.client.editors.files;

import com.jdimension.jlawyer.persistence.InvoicePosition;
import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mock host for the real {@link InvoicePositionEntryPanel} slice (Wave 2 exit
 * gate). In JLawyer the real {@code InvoiceDialog} is a large invoice editor
 * that stacks many {@code InvoicePositionEntryPanel} rows inside a split pane;
 * this mock reproduces just the contract the panel talks back to:
 * <ul>
 *   <li>it is the {@code JDialog} owner passed to each panel's constructor;</li>
 *   <li>{@link #positions} is the {@code Container} the panels are added to, so
 *       {@code panel.getParent()} / {@code getComponentZOrder} / {@code remove}
 *       (the move-up/down and delete paths) operate on it;</li>
 *   <li>{@link #bumpSplitPane()} and {@link #updateTotals} are the two callbacks
 *       the panel invokes.</li>
 * </ul>
 * Seeded with two sample positions so the panel renders with data.
 */
public class InvoiceDialog extends JDialog {

    private static final List<String> TAX_RATES = List.of("19,0", "7,0", "0,0");

    private final JPanel positions = new JPanel();
    private final JLabel grandTotal = new JLabel();

    public InvoiceDialog(JFrame owner) {
        super(owner, "Rechnungspositionen — real JLawyer slice", false);
        positions.setLayout(new BoxLayout(positions, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane();
        scroll.setViewportView(positions);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(scroll, BorderLayout.CENTER);
        content.add(grandTotal, BorderLayout.SOUTH);
        add(content);

        addPosition("Erstberatung", "150.00", "1", "19.0");
        addPosition("Schriftsatz erstellen", "90.00", "2", "19.0");
        recomputeGrandTotal();

        setSize(760, 420);
    }

    private void addPosition(String name, String unitPrice, String units, String taxRate) {
        InvoicePositionEntryPanel panel = new InvoicePositionEntryPanel(this, TAX_RATES);
        positions.add(panel);

        InvoicePosition pos = new InvoicePosition();
        pos.setId("pos-" + positions.getComponentCount());
        pos.setPosition(positions.getComponentCount() - 1);
        pos.setName(name);
        pos.setDescription("");
        pos.setUnitPrice(new BigDecimal(unitPrice));
        pos.setUnits(new BigDecimal(units));
        pos.setTaxRate(new BigDecimal(taxRate));
        pos.setTotal(pos.getUnitPrice().multiply(pos.getUnits()));
        panel.setEntry("INV-2026-001", pos);
    }

    /** Real dialog nudges its split-pane divider after a row add/remove; no-op in the mock. */
    public void bumpSplitPane() {
    }

    /**
     * Invoked by a position panel whenever one of its fields changes. Mirrors the
     * real InvoiceDialog contract: recompute the changed row's own total
     * (Menge × Einzelpreis → the row's "Gesamt" field) <em>before</em> summing the
     * grand total. {@code updateEntryTotal()} is "always called by the dialog,
     * never by the entry panel itself" (see the panel's javadoc) — without this
     * call the per-row total stays stale on edit and only refreshes when a reorder
     * happens to run {@code setEntry()}.
     */
    public void updateTotals(InvoicePositionEntryPanel changed) {
        if (changed != null) {
            changed.updateEntryTotal();
        }
        recomputeGrandTotal();
    }

    private void recomputeGrandTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (Component c : positions.getComponents()) {
            if (c instanceof InvoicePositionEntryPanel p) {
                try {
                    InvoicePosition entry = p.getEntry();
                    if (entry.getTotal() != null) {
                        sum = sum.add(entry.getTotal());
                    }
                } catch (Exception ignore) {
                    // A row mid-edit may not yield a clean entry yet; skip it.
                }
            }
        }
        grandTotal.setText("Gesamt (netto): " + sum.toPlainString());
    }
}
