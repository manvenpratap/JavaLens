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

class AttributeModel {
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

class MethodModel {
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
    public final String paramTypes; // key signature for overloading

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
