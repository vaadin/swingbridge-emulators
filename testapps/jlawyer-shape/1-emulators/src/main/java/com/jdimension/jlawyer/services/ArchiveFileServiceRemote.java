package com.jdimension.jlawyer.services;

import com.jdimension.jlawyer.persistence.InvoicePosition;

/**
 * Mock of JLawyer's {@code ArchiveFileServiceRemote} EJB remote interface,
 * narrowed to the two methods the {@code InvoicePositionEntryPanel} slice calls.
 * The real interface is a large case-management facade looked up over JNDI; the
 * Wave 2 exit gate mocks it (see {@link JLawyerServiceLocator}) so the panel
 * runs without the server tier.
 */
public interface ArchiveFileServiceRemote {

    void removeInvoicePosition(String invoiceId, InvoicePosition position);

    InvoicePosition updateInvoicePosition(String invoiceId, InvoicePosition position);
}
