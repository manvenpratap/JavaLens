package com.javalens.model;

public class MethodModel {
    public final String file;
    public final String packageName;
    public final String className;
    public final String name;
    public final String returnType;
    public final String modifiers;
    public final String parameters;
    public final int parameterCount;
    public final String throwsList;
    public final String annotations;
    public final String kind;
    public final String paramTypes;

    // Archetype & Merger Policy Fields
    public String archetypeBadge = "METHOD";
    public String archetypeLabel = "Member Method";
    public String archetypeCategory = "GENERAL";
    public String archetypeColor = "#64748b";
    public String mergerAction = "MARKER_MERGE";

    public MethodModel(String file, String packageName, String className, String name,
                       String returnType, String modifiers, String parameters, int parameterCount,
                       String throwsList, String annotations, String kind, String paramTypes) {
        this(file, packageName, className, name, returnType, modifiers, parameters, parameterCount,
             throwsList, annotations, kind, paramTypes, "METHOD", "Member Method", "GENERAL", "#64748b", "MARKER_MERGE");
    }

    public MethodModel(String file, String packageName, String className, String name,
                       String returnType, String modifiers, String parameters, int parameterCount,
                       String throwsList, String annotations, String kind, String paramTypes,
                       String archetypeBadge, String archetypeLabel, String archetypeCategory,
                       String archetypeColor, String mergerAction) {
        this.file = file;
        this.packageName = packageName;
        this.className = className;
        this.name = name;
        this.returnType = returnType;
        this.modifiers = modifiers;
        this.parameters = parameters;
        this.parameterCount = parameterCount;
        this.throwsList = throwsList;
        this.annotations = annotations;
        this.kind = kind;
        this.paramTypes = paramTypes;
        this.archetypeBadge = archetypeBadge != null ? archetypeBadge : "METHOD";
        this.archetypeLabel = archetypeLabel != null ? archetypeLabel : "Member Method";
        this.archetypeCategory = archetypeCategory != null ? archetypeCategory : "GENERAL";
        this.archetypeColor = archetypeColor != null ? archetypeColor : "#64748b";
        this.mergerAction = mergerAction != null ? mergerAction : "MARKER_MERGE";
    }

    public boolean isConstructor() {
        return "constructor".equalsIgnoreCase(kind) || "<init>".equals(name);
    }

    public String getArchetypeBadge() {
        return archetypeBadge != null ? archetypeBadge : (isConstructor() ? "CTOR" : "METHOD");
    }

    public String getArchetypeCategory() {
        return archetypeCategory != null ? archetypeCategory : "GENERAL";
    }

    public String getArchetypeLabel() {
        return archetypeLabel != null ? archetypeLabel : "Member Method";
    }

    public String getArchetypeColor() {
        return archetypeColor != null ? archetypeColor : "#64748b";
    }

    public String getMergerAction() {
        return mergerAction != null ? mergerAction : "MARKER_MERGE";
    }
}
