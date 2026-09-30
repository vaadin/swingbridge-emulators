package com.jdimension.jlawyer.services;

import com.jdimension.jlawyer.persistence.InvoicePosition;

import java.util.Properties;

/**
 * Mock of JLawyer's {@code JLawyerServiceLocator}. The real locator resolves EJB
 * remotes via {@code InitialContext.lookup("ejb:...")}; this exit-gate mock
 * returns an in-memory {@link ArchiveFileServiceRemote} that logs and echoes,
 * so the {@code InvoicePositionEntryPanel} slice's save/remove paths run without
 * a server. {@code getInstance(Properties)} mirrors the real static-factory
 * shape the panel calls.
 */
public class JLawyerServiceLocator {

    private static final JLawyerServiceLocator INSTANCE = new JLawyerServiceLocator();

    private final ArchiveFileServiceRemote archiveFileService = new ArchiveFileServiceRemote() {
        @Override
        public void removeInvoicePosition(String invoiceId, InvoicePosition position) {
            // In-memory mock: nothing to persist. The panel's UI side (row
            // removal, total recalculation) is what the exit gate exercises.
        }

        @Override
        public InvoicePosition updateInvoicePosition(String invoiceId, InvoicePosition position) {
            // Echo back the updated position, as the real service would after
            // persisting — the panel re-reads it into its fields.
            return position;
        }
    };

    public static JLawyerServiceLocator getInstance(Properties lookupProperties) {
        return INSTANCE;
    }

    public ArchiveFileServiceRemote lookupArchiveFileServiceRemote() {
        return archiveFileService;
    }
}
