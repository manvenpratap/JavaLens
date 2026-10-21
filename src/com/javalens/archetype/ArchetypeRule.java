package com.javalens.archetype;

import java.util.*;

public class ArchetypeRule {
    public enum Target {
        METHOD, CLASS, ANY;

        public static Target fromString(String val) {
            if (val == null) return METHOD;
            String v = val.trim().toUpperCase();
            if (v.equals("TYPE")) return CLASS;
            try {
                return Target.valueOf(v);
            } catch (Exception e) {
                return METHOD;
            }
        }
    }

    public enum MatchType {
        PREFIX, SUFFIX, CONTAINS, EXACT, GLOB, REGEX, STRUCTURAL;

        public static MatchType fromString(String val) {
            if (val == null) return PREFIX;
            try {
                return MatchType.valueOf(val.trim().toUpperCase());
            } catch (Exception e) {
                return PREFIX;
            }
        }
    }

    public final String id;
    public final Target target;
    public final MatchType matchType;
    public final String pattern;
    public final String label;
    public final String badge;
    public final String category;
    public final String color;
    public final String icon;
    public final String description;
    public final boolean enabled;

    public ArchetypeRule(String id, Target target, MatchType matchType, String pattern,
                         String label, String badge, String category, String color,
                         String icon, String description, boolean enabled) {
        this.id = id != null ? id : "rule-" + UUID.randomUUID().toString().substring(0, 8);
        this.target = target != null ? target : Target.METHOD;
        this.matchType = matchType != null ? matchType : MatchType.PREFIX;
        this.pattern = pattern != null ? pattern : "";
        this.label = label != null ? label : "";
        this.badge = badge != null ? badge : "";
        this.category = category != null ? category : "CUSTOM";
        this.color = color != null ? color : "#3b82f6";
        this.icon = icon != null ? icon : "tag";
        this.description = description != null ? description : "";
        this.enabled = enabled;
    }

    public String toJson() {
        return "{" +
                "\"id\":\"" + escapeJson(id) + "\"," +
                "\"target\":\"" + target.name() + "\"," +
                "\"scope\":\"" + target.name() + "\"," +
                "\"matchType\":\"" + matchType.name() + "\"," +
                "\"pattern\":\"" + escapeJson(pattern) + "\"," +
                "\"label\":\"" + escapeJson(label) + "\"," +
                "\"badge\":\"" + escapeJson(badge) + "\"," +
                "\"category\":\"" + escapeJson(category) + "\"," +
                "\"color\":\"" + escapeJson(color) + "\"," +
                "\"icon\":\"" + escapeJson(icon) + "\"," +
                "\"description\":\"" + escapeJson(description) + "\"," +
                "\"enabled\":" + enabled +
                "}";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
