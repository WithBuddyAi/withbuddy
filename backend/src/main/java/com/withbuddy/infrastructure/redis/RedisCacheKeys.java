package com.withbuddy.infrastructure.redis;

public final class RedisCacheKeys {

    private RedisCacheKeys() {
    }

    public static String sessionToken(String token) {
        return "session:token:" + token;
    }

    public static String userSession(Long userId) {
        return "session:user:" + userId;
    }

    public static String userProfile(Long userId) {
        return "user:profile:" + userId;
    }

    public static String sseSession(Long userId) {
        return "sse:session:" + userId;
    }

    public static String sseMissed(Long userId) {
        return "sse:missed:" + userId;
    }

    public static String ragStatus(Long requestId) {
        return "rag:status:" + requestId;
    }

    public static String buddyDay(Long userId) {
        return "buddy:day:" + userId;
    }

    public static String nudgeSent(Long userId, int day) {
        return "nudge:sent:" + userId + ":" + day;
    }

    public static String quickTap(int day) {
        return "quicktap:" + day;
    }

    public static String conversation(String sessionId) {
        return "conversation:" + sessionId;
    }

    public static String presignedUrl(Long fileId) {
        return "presigned:url:" + fileId;
    }

    public static String docsListFirstPage(String scope, String formType) {
        return "docs:list:" + scope + ":" + normalize(formType) + ":first";
    }

    public static String formGenerated(Long userId, String formType) {
        return "form:generated:" + userId + ":" + normalize(formType);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "ALL";
        }
        return value.trim().toUpperCase();
    }
}
