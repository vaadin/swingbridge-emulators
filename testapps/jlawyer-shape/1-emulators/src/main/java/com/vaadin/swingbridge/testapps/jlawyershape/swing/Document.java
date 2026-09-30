package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import java.util.Date;

public final class Document {

    public enum Type { PDF, IMAGE, TEXT, OTHER }

    private final String name;
    private final Type type;
    private final long sizeBytes;
    private final Date uploadedAt;
    private String pathHint;

    public Document(String name, Type type, long sizeBytes, Date uploadedAt, String pathHint) {
        this.name = name;
        this.type = type;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = uploadedAt;
        this.pathHint = pathHint;
    }

    public String getName() { return name; }
    public Type getType() { return type; }
    public long getSizeBytes() { return sizeBytes; }
    public Date getUploadedAt() { return uploadedAt; }
    public String getPathHint() { return pathHint; }
    public void setPathHint(String pathHint) { this.pathHint = pathHint; }

    public String formattedSize() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return (sizeBytes / 1024) + " KB";
        return (sizeBytes / (1024 * 1024)) + " MB";
    }

    @Override
    public String toString() { return name; }
}
