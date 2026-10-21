package com.javalens.archetype;

import java.util.UUID;

public class MergerRule {
    public enum MergeAction {
        MARKER_MERGE("Marker Merge", "Merge marked features with change tracking"),
        PRESERVE_EXISTING("Preserve Base", "Keep existing baseline implementation without overwrite"),
        CLEAN_OVERWRITE("Clean Overwrite", "Directly replace method with generated implementation"),
        ADD_IF_MISSING("Add If Missing", "Only insert if method is absent in baseline file"),
        SKIP("Skip", "Skip merging this method archetype entirely");

        private final String displayName;
        private final String description;

        MergeAction(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public static MergeAction fromString(String val) {
            if (val == null) return MARKER_MERGE;
            String v = val.trim().toUpperCase().replace('-', '_').replace(' ', '_');
            try {
                return MergeAction.valueOf(v);
            } catch (Exception e) {
                return MARKER_MERGE;
            }
        }
    }

    public final String id;
    public final String archetype; // e.g. "MUTATE", "FETCH", "TASK-OWN", "TASK-COMMON", "BATCH", "POJO", "PERSISTENT", "*"
    public final MergeAction action;
    public final String description;
    public final boolean enabled;

    public MergerRule(String id, String archetype, MergeAction action, String description, boolean enabled) {
        this.id = id != null ? id : "mrule-" + UUID.randomUUID().toString().substring(0, 8);
        this.archetype = archetype != null ? archetype.trim() : "*";
        this.action = action != null ? action : MergeAction.MARKER_MERGE;
        this.description = description != null ? description : "";
        this.enabled = enabled;
    }

    public String toJson() {
        return "{" +
                "\"id\":\"" + escapeJson(id) + "\"," +
                "\"archetype\":\"" + escapeJson(archetype) + "\"," +
                "\"action\":\"" + action.name() + "\"," +
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
