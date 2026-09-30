package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import javax.swing.event.EventListenerList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EventListener;
import java.util.List;

public final class CaseStore {

    public interface Listener extends EventListener {
        /** Tree structure changed — folders / cases added / removed. */
        void treeChanged();
        /** Editable fields on a specific case changed. */
        void caseUpdated(Case c);
        /** Document list within a specific case changed. */
        void documentsChanged(Case c);
    }

    private final CaseFolder root = new CaseFolder("All Cases");
    private final EventListenerList listeners = new EventListenerList();

    public CaseFolder getRoot() { return root; }

    public void addListener(Listener l) {
        listeners.add(Listener.class, l);
    }

    public void fireTreeChanged() {
        for (Listener l : listeners.getListeners(Listener.class)) l.treeChanged();
    }

    public void fireCaseUpdated(Case c) {
        for (Listener l : listeners.getListeners(Listener.class)) l.caseUpdated(c);
    }

    public void fireDocumentsChanged(Case c) {
        for (Listener l : listeners.getListeners(Listener.class)) l.documentsChanged(c);
    }

    public List<Case> allCases() {
        List<Case> result = new ArrayList<>();
        collectCases(root, result);
        return result;
    }

    private void collectCases(CaseFolder f, List<Case> out) {
        out.addAll(f.getCases());
        for (CaseFolder child : f.getChildren()) collectCases(child, out);
    }

    /** Find the folder that holds the given case, or null if not present. */
    public CaseFolder findFolderOf(Case c) {
        return findFolderOf(root, c);
    }

    private CaseFolder findFolderOf(CaseFolder f, Case c) {
        if (f.getCases().contains(c)) return f;
        for (CaseFolder child : f.getChildren()) {
            CaseFolder hit = findFolderOf(child, c);
            if (hit != null) return hit;
        }
        return null;
    }

    public void seed() {
        CaseFolder corporate = root.addChild(new CaseFolder("Corporate"));
        CaseFolder family = root.addChild(new CaseFolder("Family Law"));
        CaseFolder pi = root.addChild(new CaseFolder("Personal Injury"));

        Case acme = corporate.addCase(new Case(
                "Acme vs Globex",
                "Acme Industries Ltd.",
                Case.Priority.HIGH,
                false,
                date(2024, 11, 3),
                "<h2>Acme vs Globex</h2>"
                        + "<p><b>Status:</b> discovery phase, response brief drafted, awaiting opposing counsel.</p>"
                        + "<p><b>Next milestone:</b> deposition scheduled <i>2026-06-15</i>.</p>"
                        + "<ul><li>Filed initial complaint 2024-11-03</li><li>Discovery requests served 2025-02-10</li><li>Response brief drafted 2025-09-21</li></ul>"));
        acme.addDocument(new Document("complaint.pdf", Document.Type.PDF, 184_320, date(2024, 11, 3), "/cases/acme/complaint.pdf"));
        acme.addDocument(new Document("discovery-req.pdf", Document.Type.PDF, 92_160, date(2025, 2, 10), "/cases/acme/discovery-req.pdf"));
        acme.addDocument(new Document("evidence-photo.jpg", Document.Type.IMAGE, 2_457_600, date(2025, 5, 5), "/cases/acme/evidence-photo.jpg"));
        acme.addDocument(new Document("response-brief.pdf", Document.Type.PDF, 256_000, date(2025, 9, 21), "/cases/acme/response-brief.pdf"));

        Case initech = corporate.addCase(new Case(
                "Initech Acquisition",
                "Initech LLC",
                Case.Priority.MEDIUM,
                false,
                date(2025, 8, 12),
                "<h2>Initech Acquisition</h2><p>Due diligence in progress. Term sheet under negotiation.</p>"));
        initech.addDocument(new Document("term-sheet-v3.docx", Document.Type.TEXT, 45_056, date(2025, 9, 1), "/cases/initech/term-sheet-v3.docx"));
        initech.addDocument(new Document("due-diligence-checklist.txt", Document.Type.TEXT, 8_192, date(2025, 8, 13), "/cases/initech/due-diligence-checklist.txt"));

        Case smith = family.addCase(new Case(
                "Smith Divorce",
                "Jane Smith",
                Case.Priority.MEDIUM,
                true,
                date(2023, 4, 18),
                "<h2>Smith Divorce</h2><p><i>Archived 2024-12-01</i> — settlement finalised, decree issued.</p>"));
        smith.addDocument(new Document("settlement-final.pdf", Document.Type.PDF, 78_848, date(2024, 11, 28), "/cases/smith/settlement-final.pdf"));

        Case jones = family.addCase(new Case(
                "Estate of Jones",
                "Estate of Robert Jones",
                Case.Priority.LOW,
                false,
                date(2025, 1, 7),
                "<h2>Estate of Jones</h2><p>Probate proceedings. Heirs notified, awaiting inventory.</p>"));
        jones.addDocument(new Document("will-original.pdf", Document.Type.PDF, 31_744, date(2025, 1, 7), "/cases/jones/will-original.pdf"));
        jones.addDocument(new Document("heir-notifications.pdf", Document.Type.PDF, 19_456, date(2025, 1, 14), "/cases/jones/heir-notifications.pdf"));

        Case megacorp = pi.addCase(new Case(
                "Doe v MegaCorp",
                "John Doe",
                Case.Priority.HIGH,
                false,
                date(2025, 3, 22),
                "<h2>Doe v MegaCorp</h2><p>Workplace injury claim. Medical records subpoenaed.</p>"
                        + "<p><b>Damages sought:</b> $1.2M (medical + lost wages + pain & suffering).</p>"));
        megacorp.addDocument(new Document("incident-report.pdf", Document.Type.PDF, 51_200, date(2025, 3, 23), "/cases/megacorp/incident-report.pdf"));
        megacorp.addDocument(new Document("medical-records.pdf", Document.Type.PDF, 1_843_200, date(2025, 4, 15), "/cases/megacorp/medical-records.pdf"));
        megacorp.addDocument(new Document("witness-photo-1.jpg", Document.Type.IMAGE, 3_145_728, date(2025, 3, 22), "/cases/megacorp/witness-photo-1.jpg"));
    }

    private static Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
}
