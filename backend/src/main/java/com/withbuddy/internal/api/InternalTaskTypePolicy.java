package com.withbuddy.internal.api;

import java.util.Set;

public final class InternalTaskTypePolicy {

    public static final String AI_NUDGE_ANALYSIS = "ai.nudge.analysis";
    private static final Set<String> ALLOWED_TYPES = Set.of(AI_NUDGE_ANALYSIS);

    private InternalTaskTypePolicy() {
    }

    public static boolean isAllowed(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return false;
        }
        return ALLOWED_TYPES.contains(taskType.trim());
    }

    public static Set<String> allowedTypes() {
        return ALLOWED_TYPES;
    }
}
