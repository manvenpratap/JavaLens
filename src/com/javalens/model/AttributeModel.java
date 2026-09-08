package com.javalens.model;

public class AttributeModel {
    public final String file;
    public final String packageName;
    public final String className;
    public final String name;
    public final String type;
    public final String modifiers;
    public final String annotations;
    public final String initializer;

    public AttributeModel(String file, String packageName, String className, String name,
                          String type, String modifiers, String annotations, String initializer) {
        this.file = file;
        this.packageName = packageName;
        this.className = className;
        this.name = name;
        this.type = type;
        this.modifiers = modifiers;
        this.annotations = annotations;
        this.initializer = initializer;
    }

    public boolean isPrimaryKey() {
        if (annotations != null) {
            String lower = annotations.toLowerCase();
            if (lower.contains("@id") || lower.contains("@embeddedid") || lower.contains("@primarykey")) {
                return true;
            }
        }
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.equals("id") || lowerName.equals("_id") || lowerName.equals("pk") || lowerName.equals("_pk") || lowerName.equals("uuid") || lowerName.equals("guid")) {
                return true;
            }
            if (className != null) {
                String simpleClass = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
                String lowerClass = simpleClass.toLowerCase();
                if (lowerName.equals(lowerClass + "id") || lowerName.equals(lowerClass + "_id") ||
                    lowerName.equals("id_" + lowerClass) || lowerName.equals("pk_" + lowerClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getPrimaryKeyReason() {
        if (annotations != null) {
            String lower = annotations.toLowerCase();
            if (lower.contains("@embeddedid")) return "@EmbeddedId annotation";
            if (lower.contains("@primarykey")) return "@PrimaryKey annotation";
            if (lower.contains("@id")) return "@Id annotation";
        }
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.equals("id") || lowerName.equals("_id") || lowerName.equals("pk") || lowerName.equals("_pk") || lowerName.equals("uuid") || lowerName.equals("guid")) {
                return "Naming convention ('" + name + "')";
            }
            if (className != null) {
                String simpleClass = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
                String lowerClass = simpleClass.toLowerCase();
                if (lowerName.equals(lowerClass + "id") || lowerName.equals(lowerClass + "_id") ||
                    lowerName.equals("id_" + lowerClass) || lowerName.equals("pk_" + lowerClass)) {
                    return "Naming convention ('" + name + "' for " + simpleClass + ")";
                }
            }
        }
        return "None";
    }

    public boolean isMandatory() {
        if (isPrimaryKey()) return true;
        if (annotations != null) {
            String lower = annotations.toLowerCase();
            if (lower.contains("@notnull") || lower.contains("@nonnull") || lower.contains("@notblank") ||
                lower.contains("@notempty") || lower.contains("@required") || lower.contains("@mandatory")) {
                return true;
            }
            if (lower.contains("nullable = false") || lower.contains("nullable=false") ||
                lower.contains("optional = false") || lower.contains("optional=false")) {
                return true;
            }
        }
        if (type != null) {
            String t = type.trim();
            if (t.equals("int") || t.equals("long") || t.equals("double") || t.equals("float") ||
                t.equals("boolean") || t.equals("char") || t.equals("byte") || t.equals("short")) {
                return true;
            }
        }
        if (modifiers != null && modifiers.contains("final")) {
            return true;
        }
        return false;
    }

    public String getMandatoryReason() {
        if (isPrimaryKey()) {
            return "Primary key (" + getPrimaryKeyReason() + ")";
        }
        if (annotations != null) {
            String lower = annotations.toLowerCase();
            if (lower.contains("@notnull")) return "@NotNull annotation";
            if (lower.contains("@nonnull")) return "@NonNull annotation";
            if (lower.contains("@notblank")) return "@NotBlank annotation";
            if (lower.contains("@notempty")) return "@NotEmpty annotation";
            if (lower.contains("nullable = false") || lower.contains("nullable=false")) return "@Column(nullable = false)";
            if (lower.contains("optional = false") || lower.contains("optional=false")) return "@Basic(optional = false)";
            if (lower.contains("@required")) return "@Required annotation";
            if (lower.contains("@mandatory")) return "@Mandatory annotation";
        }
        if (type != null) {
            String t = type.trim();
            if (t.equals("int") || t.equals("long") || t.equals("double") || t.equals("float") ||
                t.equals("boolean") || t.equals("char") || t.equals("byte") || t.equals("short")) {
                return "Primitive type '" + t + "' (non-nullable)";
            }
        }
        if (modifiers != null && modifiers.contains("final")) {
            return "final modifier";
        }
        return "Optional (nullable)";
    }
}
