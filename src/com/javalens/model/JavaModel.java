package com.javalens.model;

import java.util.ArrayList;
import java.util.List;

public class JavaModel {
    private final String file;
    private final String packageName;
    private final List<AttributeModel> attributes = new ArrayList<>();
    private final List<MethodModel> methods = new ArrayList<>();

    public JavaModel(String file, String packageName) {
        this.file = file;
        this.packageName = packageName;
    }

    public String getFile() { return file; }
    public String getPackageName() { return packageName; }
    public List<AttributeModel> getAttributes() { return attributes; }
    public List<MethodModel> getMethods() { return methods; }

    public void addAttribute(AttributeModel attr) {
        attributes.add(attr);
    }

    public void addMethod(MethodModel method) {
        methods.add(method);
    }
}
