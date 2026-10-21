package com.javalens.archetype;

import com.javalens.model.JavaModel;
import com.javalens.model.MethodModel;

import java.util.*;
import java.util.regex.Pattern;

public class ArchetypeClassifier {

    private static ArchetypeClassifier instance;

    private String currentPreset = "bancs";
    private List<ArchetypeRule> rules = new ArrayList<>();
    private List<MergerRule> mergerRules = new ArrayList<>();

    public ArchetypeClassifier() {
        this("bancs", null, null);
    }

    public ArchetypeClassifier(String preset, String customRulesJson, String customMergerRulesJson) {
        init(preset, customRulesJson, customMergerRulesJson);
    }

    public static synchronized ArchetypeClassifier getInstance() {
        if (instance == null) {
            instance = new ArchetypeClassifier();
        }
        return instance;
    }

    public static synchronized void resetInstance(String preset, String rulesJson, String mergerRulesJson) {
        instance = new ArchetypeClassifier(preset, rulesJson, mergerRulesJson);
    }

    public void init(String preset, String customRulesJson, String customMergerRulesJson) {
        this.currentPreset = preset != null && !preset.isBlank() ? preset.trim().toLowerCase() : "bancs";
        
        List<ArchetypeRule> defaultRules = ArchetypePreset.getRulesForPreset(currentPreset);
        if (customRulesJson != null && !customRulesJson.trim().isEmpty() && !customRulesJson.trim().equals("[]")) {
            List<ArchetypeRule> parsed = parseRulesJson(customRulesJson);
            this.rules = parsed.isEmpty() ? defaultRules : parsed;
        } else {
            this.rules = defaultRules;
        }

        List<MergerRule> defaultMergerRules = ArchetypePreset.getMergerRulesForPreset(currentPreset);
        if (customMergerRulesJson != null && !customMergerRulesJson.trim().isEmpty() && !customMergerRulesJson.trim().equals("[]")) {
            List<MergerRule> parsedMerger = parseMergerRulesJson(customMergerRulesJson);
            this.mergerRules = parsedMerger.isEmpty() ? defaultMergerRules : parsedMerger;
        } else {
            this.mergerRules = defaultMergerRules;
        }
    }

    public String getCurrentPreset() {
        return currentPreset;
    }

    public List<ArchetypeRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public List<MergerRule> getMergerRules() {
        return Collections.unmodifiableList(mergerRules);
    }

    /**
     * Extracts module code (e.g. "com.tcs.bancs.TR" -> "TR", "com.tcs.bancs.PM.PaymentGateway" -> "PM").
     */
    public static String extractModule(String pkgOrFqn) {
        if (pkgOrFqn == null || pkgOrFqn.isBlank()) return "";
        String clean = pkgOrFqn.replaceAll("\\(.*\\)$", "").trim();
        String[] parts = clean.split("\\.");
        if (parts.length == 0) return "";

        // Look for 2-4 letter module acronym in the package path (e.g. "tr", "pm", "gl", "TR", "PM")
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i];
            if (p.length() >= 2 && p.length() <= 4 && !Set.of("com", "org", "net", "app", "tcs", "bancs", "java", "main", "core", "test").contains(p.toLowerCase())) {
                return p.toUpperCase();
            }
        }

        if (parts.length >= 4 && Set.of("com", "org", "io", "net", "dev", "app").contains(parts[0].toLowerCase())) {
            return parts[3].toUpperCase();
        }
        if (parts.length >= 3 && Set.of("com", "org", "io", "net", "dev", "app").contains(parts[0].toLowerCase())) {
            return parts[2].toUpperCase();
        }
        return parts[0].toUpperCase();
    }

    public static boolean isMessageObjectName(String name) {
        if (name == null || name.isBlank()) return false;
        String upper = name.trim().toUpperCase();
        if (upper.startsWith("MO_INP_") || upper.startsWith("MO_OUT_") || upper.startsWith("MO_")) {
            return true;
        }
        if (upper.matches("^[A-Z]{2,4}MO_.*") || upper.matches("^[A-Z]{2,4}MO$") || upper.endsWith("_MO") || upper.endsWith("MO")) {
            return true;
        }
        return false;
    }

    public static boolean isPojo(String methodName) {
        if (methodName == null || methodName.isBlank()) return false;
        String n = methodName.replaceAll("\\(.*\\)$", "").trim();
        if (n.equals("Get") || n.equals("Create") || n.equals("Modify")) return false;

        Set<String> standardObjectMethods = Set.of(
                "toString", "hashCode", "equals", "canEqual", "getClass", "compareTo", "clone", "finalize", "notify", "notifyAll", "wait"
        );
        if (standardObjectMethods.contains(n)) return true;

        if (n.length() > 3 && n.startsWith("get") && Character.isUpperCase(n.charAt(3))) return true;
        if (n.length() > 3 && n.startsWith("set") && Character.isUpperCase(n.charAt(3))) return true;
        if (n.length() > 2 && n.startsWith("is") && Character.isUpperCase(n.charAt(2))) return true;
        if (n.length() > 3 && n.startsWith("has") && Character.isUpperCase(n.charAt(3))) return true;
        return false;
    }

    /**
     * Classifies a class model and all of its methods, and assigns archetypes & merger policies.
     */
    public void classify(JavaModel model) {
        if (model == null) return;
        classifyClass(model);
        for (MethodModel m : model.getMethods()) {
            classifyMethod(m, model);
        }
    }

    /**
     * Classifies a Java class model into an archetype (DATA-GRABBER, PERSISTENT, MSG-OBJECT, CTRL, SRV, etc.).
     */
    public void classifyClass(JavaModel model) {
        if (model == null) return;
        String className = model.getSimpleClassName();
        if (className == null || className.isBlank()) {
            className = model.getClassName();
            if (className.contains(".")) {
                className = className.substring(className.lastIndexOf('.') + 1);
            }
        }
        if (className == null || className.isBlank()) return;

        String pkg = model.getPackageName();
        String fullFqn = (pkg != null && !pkg.isBlank()) ? pkg + "." + className : className;

        // 1. Check Message Object
        if (isMessageObjectName(className)) {
            ArchetypeRule moRule = findRuleByIdOrBadge("rule-bancs-mo", "MSG-OBJECT", ArchetypeRule.Target.CLASS);
            if (moRule != null) {
                model.setArchetype(moRule.badge, moRule.label, moRule.category, moRule.color);
                return;
            } else {
                model.setArchetype("MSG-OBJECT", "Message Object", "MESSAGE_DTO", "#14b8a6");
                return;
            }
        }

        // 2. Check Persistent Class (has Get, Create, Modify methods)
        boolean hasGet = false, hasCreate = false, hasModify = false;
        for (MethodModel m : model.getMethods()) {
            String mName = m.name != null ? m.name.trim() : "";
            if (mName.equalsIgnoreCase("get")) hasGet = true;
            if (mName.equalsIgnoreCase("create")) hasCreate = true;
            if (mName.equalsIgnoreCase("modify")) hasModify = true;
        }
        if (hasGet && hasCreate && hasModify) {
            ArchetypeRule pcRule = findRuleByIdOrBadge("rule-bancs-pc", "PERSISTENT", ArchetypeRule.Target.CLASS);
            if (pcRule != null) {
                model.setArchetype(pcRule.badge, pcRule.label, pcRule.category, pcRule.color);
                return;
            } else {
                model.setArchetype("PERSISTENT", "Persistent Class", "PERSISTENCE", "#6366f1");
                return;
            }
        }

        // 3. Match configured class rules
        for (ArchetypeRule rule : rules) {
            if (!rule.enabled) continue;
            if (rule.target != ArchetypeRule.Target.CLASS && rule.target != ArchetypeRule.Target.ANY) continue;
            if (rule.id.equals("rule-bancs-mo") || rule.id.equals("rule-bancs-pc")) continue;

            if (matchesRule(className, rule, fullFqn, pkg)) {
                model.setArchetype(rule.badge, rule.label, rule.category, rule.color);
                return;
            }
        }

        // Default / Unclassified class
        model.setArchetype("CLASS", "Java Class", "GENERAL", "#64748b");
    }

    /**
     * Classifies a MethodModel into an archetype and resolves its merger action.
     */
    public void classifyMethod(MethodModel method, JavaModel classModel) {
        if (method == null) return;
        String mName = method.name != null ? method.name.trim() : "";
        String pkg = classModel != null ? classModel.getPackageName() : method.packageName;
        String className = classModel != null ? classModel.getSimpleClassName() : method.className;
        String fqn = (className != null ? className + "." : "") + mName;

        // 1. Check if it's a persistent lifecycle method (Get, Create, Modify on a Persistent class)
        if (classModel != null && "PERSISTENT".equals(classModel.getArchetypeBadge()) &&
                (mName.equals("Get") || mName.equals("Create") || mName.equals("Modify"))) {
            method.archetypeBadge = "LIFECYCLE";
            method.archetypeLabel = "Persistent Lifecycle (" + mName + ")";
            method.archetypeCategory = "PERSISTENCE";
            method.archetypeColor = "#6366f1";
            method.mergerAction = resolveMergerAction(method, classModel);
            return;
        }

        // 2. Check standard POJO / Accessor
        if (isPojo(mName)) {
            method.archetypeBadge = "POJO";
            method.archetypeLabel = "Accessor / POJO";
            method.archetypeCategory = "ACCESSOR";
            method.archetypeColor = "#71717a";
            method.mergerAction = resolveMergerAction(method, classModel);
            return;
        }

        // 3. Check active method rules
        for (ArchetypeRule rule : rules) {
            if (!rule.enabled) continue;
            if (rule.target != ArchetypeRule.Target.METHOD && rule.target != ArchetypeRule.Target.ANY) continue;

            if (matchesRule(mName, rule, fqn, pkg)) {
                method.archetypeBadge = rule.badge != null && !rule.badge.isBlank() ? rule.badge : rule.label.toUpperCase();
                method.archetypeLabel = rule.label;
                method.archetypeCategory = rule.category;
                method.archetypeColor = rule.color;
                method.mergerAction = resolveMergerAction(method, classModel);
                return;
            }
        }

        // 4. Default / Unclassified method
        method.archetypeBadge = method.isConstructor() ? "CTOR" : "METHOD";
        method.archetypeLabel = method.isConstructor() ? "Constructor" : "Member Method";
        method.archetypeCategory = "GENERAL";
        method.archetypeColor = "#64748b";
        method.mergerAction = resolveMergerAction(method, classModel);
    }

    /**
     * Resolves the merger action for a given method according to active MergerRules.
     */
    public String resolveMergerAction(MethodModel method, JavaModel classModel) {
        String badge = method.archetypeBadge != null ? method.archetypeBadge : "";
        
        // Exact badge match
        for (MergerRule r : mergerRules) {
            if (r.enabled && r.archetype.equalsIgnoreCase(badge)) {
                return r.action.name();
            }
        }

        // Category match
        if (method.archetypeCategory != null) {
            for (MergerRule r : mergerRules) {
                if (r.enabled && r.archetype.equalsIgnoreCase(method.archetypeCategory)) {
                    return r.action.name();
                }
            }
        }

        // Persistent class lifecycle fallback
        if ("LIFECYCLE".equals(badge) || (classModel != null && "PERSISTENT".equals(classModel.getArchetypeBadge()))) {
            for (MergerRule r : mergerRules) {
                if (r.enabled && r.archetype.equalsIgnoreCase("PERSISTENT")) {
                    return r.action.name();
                }
            }
        }

        // Universal fallback rule ("*")
        for (MergerRule r : mergerRules) {
            if (r.enabled && r.archetype.equals("*")) {
                return r.action.name();
            }
        }

        return MergerRule.MergeAction.MARKER_MERGE.name();
    }

    private boolean matchesRule(String name, ArchetypeRule rule, String fqn, String pkg) {
        if (name == null || rule == null || rule.pattern == null) return false;
        String pattern = rule.pattern.trim();
        ArchetypeRule.MatchType matchType = rule.matchType;

        String moduleName = extractModule(pkg != null ? pkg : fqn);

        String resolved = pattern;
        if (resolved.contains("{MODULE}") || resolved.contains("{MOD}")) {
            if (moduleName.isBlank()) {
                resolved = resolved.replace("{MODULE}", "[A-Z]{2,4}").replace("{MOD}", "[A-Z]{2,4}");
                matchType = ArchetypeRule.MatchType.REGEX;
            } else {
                resolved = resolved.replace("{MODULE}", moduleName).replace("{MOD}", moduleName);
            }
        }

        if (resolved.startsWith("/") && resolved.endsWith("/")) {
            try {
                String rx = resolved.substring(1, resolved.length() - 1);
                Pattern p = Pattern.compile(rx, Pattern.CASE_INSENSITIVE);
                return p.matcher(name).find() || (fqn != null && p.matcher(fqn).find());
            } catch (Exception e) {
                return false;
            }
        }

        if (matchType == ArchetypeRule.MatchType.REGEX) {
            try {
                Pattern p = Pattern.compile(resolved, Pattern.CASE_INSENSITIVE);
                return p.matcher(name).find() || (fqn != null && p.matcher(fqn).find());
            } catch (Exception e) {
                return false;
            }
        }

        if (resolved.contains("*") || matchType == ArchetypeRule.MatchType.GLOB) {
            String regexStr = "^" + Pattern.quote(resolved).replace("*", "\\E.*\\Q") + "$";
            try {
                Pattern p = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE);
                return p.matcher(name).matches();
            } catch (Exception e) {
                return false;
            }
        }

        String nLower = name.toLowerCase();
        String rLower = resolved.toLowerCase();

        if (matchType == ArchetypeRule.MatchType.PREFIX) {
            return nLower.startsWith(rLower);
        }
        if (matchType == ArchetypeRule.MatchType.SUFFIX) {
            return nLower.endsWith(rLower);
        }
        if (matchType == ArchetypeRule.MatchType.CONTAINS) {
            return nLower.contains(rLower);
        }
        if (matchType == ArchetypeRule.MatchType.EXACT) {
            return nLower.equals(rLower);
        }

        return nLower.startsWith(rLower);
    }

    private ArchetypeRule findRuleByIdOrBadge(String id, String badge, ArchetypeRule.Target target) {
        for (ArchetypeRule r : rules) {
            if (r.enabled && (r.id.equals(id) || r.badge.equalsIgnoreCase(badge))) {
                if (target == null || r.target == target || r.target == ArchetypeRule.Target.ANY) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * Lightweight JSON parser for rules array without external library dependencies.
     */
    public static List<ArchetypeRule> parseRulesJson(String json) {
        List<ArchetypeRule> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        try {
            String trimmed = json.trim();
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return list;
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) return list;

            List<String> objects = splitJsonObjects(inner);
            for (String obj : objects) {
                Map<String, String> kv = parseSimpleJsonObject(obj);
                String id = kv.get("id");
                ArchetypeRule.Target target = ArchetypeRule.Target.fromString(kv.getOrDefault("target", kv.get("scope")));
                ArchetypeRule.MatchType matchType = ArchetypeRule.MatchType.fromString(kv.get("matchType"));
                String pattern = kv.getOrDefault("pattern", "");
                String label = kv.getOrDefault("label", "");
                String badge = kv.getOrDefault("badge", "");
                String category = kv.getOrDefault("category", "CUSTOM");
                String color = kv.getOrDefault("color", "#3b82f6");
                String icon = kv.getOrDefault("icon", "tag");
                String description = kv.getOrDefault("description", "");
                boolean enabled = !"false".equalsIgnoreCase(kv.get("enabled"));
                list.add(new ArchetypeRule(id, target, matchType, pattern, label, badge, category, color, icon, description, enabled));
            }
        } catch (Exception ignored) {}
        return list;
    }

    /**
     * Lightweight JSON parser for merger rules array.
     */
    public static List<MergerRule> parseMergerRulesJson(String json) {
        List<MergerRule> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        try {
            String trimmed = json.trim();
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return list;
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) return list;

            List<String> objects = splitJsonObjects(inner);
            for (String obj : objects) {
                Map<String, String> kv = parseSimpleJsonObject(obj);
                String id = kv.get("id");
                String archetype = kv.getOrDefault("archetype", "*");
                MergerRule.MergeAction action = MergerRule.MergeAction.fromString(kv.get("action"));
                String description = kv.getOrDefault("description", "");
                boolean enabled = !"false".equalsIgnoreCase(kv.get("enabled"));
                list.add(new MergerRule(id, archetype, action, description, enabled));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static List<String> splitJsonObjects(String inner) {
        List<String> list = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inQuotes = false;
        boolean escape = false;

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        list.add(inner.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return list;
    }

    private static Map<String, String> parseSimpleJsonObject(String obj) {
        Map<String, String> map = new LinkedHashMap<>();
        String clean = obj.trim();
        if (clean.startsWith("{")) clean = clean.substring(1);
        if (clean.endsWith("}")) clean = clean.substring(0, clean.length() - 1);

        boolean inQuotes = false;
        boolean escape = false;
        StringBuilder curKey = new StringBuilder();
        StringBuilder curVal = new StringBuilder();
        boolean readingKey = true;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (escape) {
                if (readingKey) curKey.append(c); else curVal.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && c == ':' && readingKey) {
                readingKey = false;
                continue;
            }
            if (!inQuotes && c == ',') {
                String k = curKey.toString().trim();
                String v = curVal.toString().trim();
                if (!k.isEmpty()) map.put(k, v);
                curKey.setLength(0);
                curVal.setLength(0);
                readingKey = true;
                continue;
            }
            if (readingKey) curKey.append(c); else curVal.append(c);
        }
        String k = curKey.toString().trim();
        String v = curVal.toString().trim();
        if (!k.isEmpty()) map.put(k, v);
        return map;
    }
}
