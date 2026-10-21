package com.javalens.model;

import java.util.ArrayList;
import java.util.List;

public class JavaModel {
    private final String file;
    private final String packageName;
    private String className = "";
    private String simpleClassName = "";

    // Class Archetype Fields
    private String archetypeBadge = "CLASS";
    private String archetypeLabel = "Java Class";
    private String archetypeCategory = "GENERAL";
    private String archetypeColor = "#64748b";

    private final List<AttributeModel> attributes = new ArrayList<>();
    private final List<MethodModel> methods = new ArrayList<>();

    public JavaModel(String file, String packageName) {
        this.file = file;
        this.packageName = packageName;
    }

    public String getFile() { return file; }
    public String getPackageName() { return packageName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getSimpleClassName() { return simpleClassName; }
    public void setSimpleClassName(String simpleClassName) { this.simpleClassName = simpleClassName; }

    public String getArchetypeBadge() { return archetypeBadge != null ? archetypeBadge : "CLASS"; }
    public String getArchetypeLabel() { return archetypeLabel != null ? archetypeLabel : "Java Class"; }
    public String getArchetypeCategory() { return archetypeCategory != null ? archetypeCategory : "GENERAL"; }
    public String getArchetypeColor() { return archetypeColor != null ? archetypeColor : "#64748b"; }

    public void setArchetype(String badge, String label, String category, String color) {
        this.archetypeBadge = badge;
        this.archetypeLabel = label;
        this.archetypeCategory = category;
        this.archetypeColor = color;
    }

    public List<AttributeModel> getAttributes() { return attributes; }
    public List<MethodModel> getMethods() { return methods; }

    public void addAttribute(AttributeModel attr) {
        attributes.add(attr);
    }

    public void addMethod(MethodModel method) {
        methods.add(method);
    }
}
