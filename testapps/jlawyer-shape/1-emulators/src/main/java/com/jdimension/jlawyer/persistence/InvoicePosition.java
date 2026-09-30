package com.jdimension.jlawyer.persistence;

import java.math.BigDecimal;

/**
 * Mock of JLawyer's {@code InvoicePosition} JPA entity — the DTO the real
 * {@code InvoicePositionEntryPanel} slice reads and writes. Wave 2 exit-gate
 * scaffolding: only the fields/accessors the panel touches are reproduced (the
 * real entity carries JPA annotations and a wider column set we don't need to
 * run the panel). Services that persist it are mocked; see
 * {@link com.jdimension.jlawyer.services.ArchiveFileServiceRemote}.
 */
public class InvoicePosition {

    private String id;
    private String name = "";
    private String description = "";
    private int position;
    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal units = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}
