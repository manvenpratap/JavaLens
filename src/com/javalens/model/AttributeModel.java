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
}
