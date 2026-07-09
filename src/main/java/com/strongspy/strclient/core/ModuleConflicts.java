package com.strongspy.strclient.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Hardcoded module conflict table. If two conflicting modules end up
 * enabled at the same time, the one that was enabled first gets
 * automatically disabled to prevent both from fighting over the same
 * behavior (e.g. two movement modules both setting velocity).
 *
 * To declare a conflict, add both module IDs to declareConflict(...)
 * once during static init below — the relationship is symmetric, so
 * you only need to declare it in one direction.
 */
public final class ModuleConflicts {

    private static final Map<String, Set<String>> CONFLICTS = new HashMap<>();

    static {
        // ── Movement conflicts ──────────────────────────────────────
        //declareConflict("flight", "jesus");
        //declareConflict("speed", "velocity");
        //declareConflict("speed", "flight");
        //declareConflict("flight", "jumpmodify");

        // ── Combat conflicts ────────────────────────────────────────

        // ── Add more conflicts here as needed ──────────────────────
        // declareConflict("moduleIdA", "moduleIdB");
    }

    private ModuleConflicts() {}

    private static void declareConflict(String idA, String idB) {
        CONFLICTS.computeIfAbsent(idA, k -> new HashSet<>()).add(idB);
        CONFLICTS.computeIfAbsent(idB, k -> new HashSet<>()).add(idA);
    }

    /** Returns true if moduleId conflicts with the given other module id. */
    public static boolean conflictsWith(String moduleId, String otherId) {
        Set<String> set = CONFLICTS.get(moduleId);
        return set != null && set.contains(otherId);
    }

    /** Returns the set of module ids that conflict with the given module id (may be empty). */
    public static Set<String> getConflicts(String moduleId) {
        return CONFLICTS.getOrDefault(moduleId, Set.of());
    }
}