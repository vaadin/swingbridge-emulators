package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import com.vaadin.swingbridge.migration.IntentionallyStatic;
import static com.vaadin.swingbridge.migration.IntentionallyStatic.Reason.COUNTER;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class Case {

    public enum Priority { LOW, MEDIUM, HIGH }

    @IntentionallyStatic(value = COUNTER,
            note = "stands in for the server DB sequence: IDs need only be unique, so a second session's seed cases numbering from #1007 is fine")
    private static int idSequence = 1000;
    private static synchronized int nextId() { return ++idSequence; }

    private final int caseId;
    private String title;
    private String client;
    private Priority priority;
    private boolean archived;
    private Date dateOpened;
    private String notes;
    private final List<Document> documents = new ArrayList<>();

    public Case(String title, String client, Priority priority, boolean archived, Date dateOpened, String notes) {
        this.caseId = nextId();
        this.title = title;
        this.client = client;
        this.priority = priority;
        this.archived = archived;
        this.dateOpened = dateOpened;
        this.notes = notes;
    }

    public Case(Case other) {
        this.caseId = other.caseId;
        this.title = other.title;
        this.client = other.client;
        this.priority = other.priority;
        this.archived = other.archived;
        this.dateOpened = other.dateOpened == null ? null : new Date(other.dateOpened.getTime());
        this.notes = other.notes;
        this.documents.addAll(other.documents);
    }

    public int getCaseId() { return caseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public Date getDateOpened() { return dateOpened; }
    public void setDateOpened(Date dateOpened) { this.dateOpened = dateOpened; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<Document> getDocuments() { return documents; }
    public void addDocument(Document d) { documents.add(d); }
    public boolean removeDocument(Document d) { return documents.remove(d); }

    public void copyEditableFieldsFrom(Case other) {
        this.title = other.title;
        this.client = other.client;
        this.priority = other.priority;
        this.archived = other.archived;
        this.dateOpened = other.dateOpened;
    }

    @Override
    public String toString() { return title + " (#" + caseId + ")"; }
}
