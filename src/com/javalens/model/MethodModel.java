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

    public MethodModel(String file, String packageName, String className, String name,
                       String returnType, String modifiers, String parameters, int parameterCount,
                       String throwsList, String annotations, String kind, String paramTypes) {
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
    }
}
