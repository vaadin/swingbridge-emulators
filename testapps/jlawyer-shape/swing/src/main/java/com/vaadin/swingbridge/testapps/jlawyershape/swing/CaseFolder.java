package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import java.util.ArrayList;
import java.util.List;

public final class CaseFolder {

    private String name;
    private CaseFolder parent;
    private final List<CaseFolder> children = new ArrayList<>();
    private final List<Case> cases = new ArrayList<>();

    public CaseFolder(String name) { this.name = name; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CaseFolder getParent() { return parent; }
    public List<CaseFolder> getChildren() { return children; }
    public List<Case> getCases() { return cases; }

    public CaseFolder addChild(CaseFolder child) {
        child.parent = this;
        children.add(child);
        return child;
    }

    public Case addCase(Case c) {
        cases.add(c);
        return c;
    }

    public boolean removeCase(Case c) {
        return cases.remove(c);
    }

    @Override
    public String toString() { return name; }
}
