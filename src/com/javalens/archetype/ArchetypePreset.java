package com.javalens.archetype;

import java.util.*;

public class ArchetypePreset {

    public static List<ArchetypeRule> getBancsRules() {
        List<ArchetypeRule> rules = new ArrayList<>();
        // Method Rules
        rules.add(new ArchetypeRule("rule-bancs-et", ArchetypeRule.Target.METHOD, ArchetypeRule.MatchType.PREFIX,
                "{MODULE}ET", "Elementary Transaction", "FETCH", "READ_ONLY", "#10b981", "download",
                "Elementary transactions only meant for fetching data", true));

        rules.add(new ArchetypeRule("rule-bancs-bt", ArchetypeRule.Target.METHOD, ArchetypeRule.MatchType.PREFIX,
                "{MODULE}BT", "Business Transaction", "MUTATE", "MUTATION", "#f59e0b", "zap",
                "Business transactions meant for create/update data", true));

        rules.add(new ArchetypeRule("rule-bancs-to", ArchetypeRule.Target.METHOD, ArchetypeRule.MatchType.PREFIX,
                "{MODULE}TO", "Own Task", "TASK-OWN", "WORKFLOW", "#0ea5e9", "check",
                "Module own task workflow execution ({MODULE}TO)", true));

        rules.add(new ArchetypeRule("rule-bancs-tc", ArchetypeRule.Target.METHOD, ArchetypeRule.MatchType.PREFIX,
                "{MODULE}TC", "Common Task", "TASK-COMMON", "SHARED_TASK", "#a855f7", "layers",
                "Cross-module common task workflow execution ({MODULE}TC)", true));

        rules.add(new ArchetypeRule("rule-bancs-ps", ArchetypeRule.Target.METHOD, ArchetypeRule.MatchType.PREFIX,
                "{MODULE}PS", "Batch Processor", "BATCH", "BATCH", "#8b5cf6", "settings",
                "Batch processor / background jobs", true));

        rules.add(new ArchetypeRule("rule-bancs-pb", ArchetypeRule.Target.METHOD, ArchetypeRule.MatchType.PREFIX,
                "{MODULE}PB", "Process Before Batch", "PRE-BATCH", "PRE_PROCESS", "#3b82f6", "skipBack",
                "Process before batch execution", true));

        rules.add(new ArchetypeRule("rule-bancs-pa", ArchetypeRule.Target.METHOD, ArchetypeRule.MatchType.PREFIX,
                "{MODULE}PA", "Process After Batch", "POST-BATCH", "POST_PROCESS", "#ec4899", "skipForward",
                "Process after batch execution", true));

        // Class Rules
        rules.add(new ArchetypeRule("rule-bancs-dg", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.PREFIX,
                "{MODULE}DG", "Data Grabber", "DATA-GRABBER", "DATA_ACCESS", "#06b6d4", "box",
                "Data grabber / data retrieval components ({MODULE}DG, *DG)", true));

        rules.add(new ArchetypeRule("rule-bancs-pc", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.STRUCTURAL,
                "* (Get,Create,Modify)", "Persistent Class", "PERSISTENT", "PERSISTENCE", "#6366f1", "database",
                "Persistent classes with Get(), Create(), and Modify() methods (non-MO entities)", true));

        rules.add(new ArchetypeRule("rule-bancs-mo", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.GLOB,
                "MO_*", "Message Object", "MSG-OBJECT", "MESSAGE_DTO", "#14b8a6", "fileText",
                "Message objects for input/output payloads (MO_INP_*, MO_OUT_*, MO_*)", true));

        return rules;
    }

    public static List<MergerRule> getBancsMergerRules() {
        List<MergerRule> rules = new ArrayList<>();
        rules.add(new MergerRule("mrule-bancs-bt", "MUTATE", MergerRule.MergeAction.MARKER_MERGE,
                "Business transaction mutations merged with marker change telemetry", true));
        rules.add(new MergerRule("mrule-bancs-et", "FETCH", MergerRule.MergeAction.PRESERVE_EXISTING,
                "Elementary transaction fetchers preserve baseline optimizations", true));
        rules.add(new MergerRule("mrule-bancs-to", "TASK-OWN", MergerRule.MergeAction.MARKER_MERGE,
                "Module own workflow task updates merged with markers", true));
        rules.add(new MergerRule("mrule-bancs-tc", "TASK-COMMON", MergerRule.MergeAction.MARKER_MERGE,
                "Cross-module shared workflow tasks merged with markers", true));
        rules.add(new MergerRule("mrule-bancs-ps", "BATCH", MergerRule.MergeAction.MARKER_MERGE,
                "Batch processor routines merged with markers", true));
        rules.add(new MergerRule("mrule-bancs-pb", "PRE-BATCH", MergerRule.MergeAction.MARKER_MERGE,
                "Pre-batch hooks merged with markers", true));
        rules.add(new MergerRule("mrule-bancs-pa", "POST-BATCH", MergerRule.MergeAction.MARKER_MERGE,
                "Post-batch hooks merged with markers", true));
        rules.add(new MergerRule("mrule-bancs-pojo", "POJO", MergerRule.MergeAction.CLEAN_OVERWRITE,
                "Standard getters, setters, and accessors cleanly refreshed", true));
        rules.add(new MergerRule("mrule-bancs-persistent", "PERSISTENT", MergerRule.MergeAction.PRESERVE_EXISTING,
                "Protect core entity persistence lifecycle methods (Get, Create, Modify)", true));
        rules.add(new MergerRule("mrule-bancs-default", "*", MergerRule.MergeAction.MARKER_MERGE,
                "Default marker-guided merge for unclassified methods", true));
        return rules;
    }

    public static List<ArchetypeRule> getSpringRules() {
        List<ArchetypeRule> rules = new ArrayList<>();
        rules.add(new ArchetypeRule("rule-spring-ctrl", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*Controller", "REST Controller", "CTRL", "CONTROLLER", "#3b82f6", "globe",
                "Spring REST / Web MVC Controller", true));
        rules.add(new ArchetypeRule("rule-spring-srv", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*Service", "Business Service", "SRV", "SERVICE", "#10b981", "settings",
                "Spring Business Service Layer", true));
        rules.add(new ArchetypeRule("rule-spring-repo", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*Repository", "Data Repository", "REPO", "DATA_ACCESS", "#8b5cf6", "database",
                "Spring Data JPA / Mongo Repository", true));
        rules.add(new ArchetypeRule("rule-spring-dto", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*DTO", "Data Transfer Object", "DTO", "DTO", "#06b6d4", "fileText",
                "Data Transfer Object / Schema", true));
        rules.add(new ArchetypeRule("rule-spring-mapper", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*Mapper", "Entity Mapper", "MAPPER", "MAPPER", "#f59e0b", "refresh",
                "MapStruct / Object transformation mapper", true));
        return rules;
    }

    public static List<MergerRule> getSpringMergerRules() {
        List<MergerRule> rules = new ArrayList<>();
        rules.add(new MergerRule("mrule-spring-ctrl", "CTRL", MergerRule.MergeAction.MARKER_MERGE,
                "Merge web controller endpoint routes", true));
        rules.add(new MergerRule("mrule-spring-srv", "SRV", MergerRule.MergeAction.MARKER_MERGE,
                "Merge business service logic", true));
        rules.add(new MergerRule("mrule-spring-repo", "REPO", MergerRule.MergeAction.PRESERVE_EXISTING,
                "Preserve baseline custom query implementations", true));
        rules.add(new MergerRule("mrule-spring-dto", "DTO", MergerRule.MergeAction.CLEAN_OVERWRITE,
                "Cleanly overwrite DTO schema definitions", true));
        rules.add(new MergerRule("mrule-spring-mapper", "MAPPER", MergerRule.MergeAction.CLEAN_OVERWRITE,
                "Cleanly overwrite object transformation mappers", true));
        rules.add(new MergerRule("mrule-spring-pojo", "POJO", MergerRule.MergeAction.CLEAN_OVERWRITE,
                "Cleanly update boilerplate getters and setters", true));
        rules.add(new MergerRule("mrule-spring-default", "*", MergerRule.MergeAction.MARKER_MERGE,
                "Default fallback marker merge", true));
        return rules;
    }

    public static List<ArchetypeRule> getDddRules() {
        List<ArchetypeRule> rules = new ArrayList<>();
        rules.add(new ArchetypeRule("rule-ddd-aggregate", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*Aggregate", "Aggregate Root", "AGGREGATE", "DOMAIN", "#8b5cf6", "box",
                "Domain-Driven Design Aggregate Root", true));
        rules.add(new ArchetypeRule("rule-ddd-entity", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*Entity", "Domain Entity", "ENTITY", "DOMAIN", "#3b82f6", "database",
                "Domain Entity with business identity", true));
        rules.add(new ArchetypeRule("rule-ddd-vo", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*VO", "Value Object", "VAL-OBJ", "DOMAIN", "#06b6d4", "tag",
                "Immutable Domain Value Object", true));
        rules.add(new ArchetypeRule("rule-ddd-usecase", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*UseCase", "Use Case", "USE-CASE", "APPLICATION", "#10b981", "zap",
                "Application Use Case / Command Handler", true));
        rules.add(new ArchetypeRule("rule-ddd-repo", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*Repository", "Domain Repository", "REPO", "INFRA", "#ec4899", "database",
                "Domain Repository Contract / Gateway", true));
        rules.add(new ArchetypeRule("rule-ddd-event", ArchetypeRule.Target.CLASS, ArchetypeRule.MatchType.SUFFIX,
                "*Event", "Domain Event", "EVENT", "EVENT", "#f59e0b", "activity",
                "Domain Event Notification", true));
        return rules;
    }

    public static List<MergerRule> getDddMergerRules() {
        List<MergerRule> rules = new ArrayList<>();
        rules.add(new MergerRule("mrule-ddd-usecase", "USE-CASE", MergerRule.MergeAction.MARKER_MERGE,
                "Merge application use case logic", true));
        rules.add(new MergerRule("mrule-ddd-aggregate", "AGGREGATE", MergerRule.MergeAction.MARKER_MERGE,
                "Merge aggregate root command logic", true));
        rules.add(new MergerRule("mrule-ddd-entity", "ENTITY", MergerRule.MergeAction.PRESERVE_EXISTING,
                "Preserve existing domain entity rules", true));
        rules.add(new MergerRule("mrule-ddd-repo", "REPO", MergerRule.MergeAction.PRESERVE_EXISTING,
                "Preserve domain repository contracts", true));
        rules.add(new MergerRule("mrule-ddd-vo", "VAL-OBJ", MergerRule.MergeAction.CLEAN_OVERWRITE,
                "Cleanly overwrite immutable value objects", true));
        rules.add(new MergerRule("mrule-ddd-event", "EVENT", MergerRule.MergeAction.CLEAN_OVERWRITE,
                "Cleanly overwrite domain event structures", true));
        rules.add(new MergerRule("mrule-ddd-pojo", "POJO", MergerRule.MergeAction.CLEAN_OVERWRITE,
                "Cleanly update boilerplate methods", true));
        rules.add(new MergerRule("mrule-ddd-default", "*", MergerRule.MergeAction.MARKER_MERGE,
                "Default fallback marker merge", true));
        return rules;
    }

    public static List<ArchetypeRule> getRulesForPreset(String presetName) {
        String name = presetName != null ? presetName.trim().toLowerCase() : "bancs";
        if (name.equals("spring") || name.equals("mvc")) {
            return getSpringRules();
        } else if (name.equals("ddd") || name.equals("clean") || name.equals("domain")) {
            return getDddRules();
        }
        return getBancsRules();
    }

    public static List<MergerRule> getMergerRulesForPreset(String presetName) {
        String name = presetName != null ? presetName.trim().toLowerCase() : "bancs";
        if (name.equals("spring") || name.equals("mvc")) {
            return getSpringMergerRules();
        } else if (name.equals("ddd") || name.equals("clean") || name.equals("domain")) {
            return getDddMergerRules();
        }
        return getBancsMergerRules();
    }
}
