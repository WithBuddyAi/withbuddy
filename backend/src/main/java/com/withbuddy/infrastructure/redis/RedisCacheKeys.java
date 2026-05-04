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

    public static String nudgeSent(Long userId, int day) {
        return "nudge:sent:" + userId + ":" + day;
    }

    public static String nudgeIdempotency(String eventId) {
        return "idempotency:nudge:" + eventId;
    }

    public static String conversation(String sessionId) {
        return "conversation:" + sessionId;
    }

    public static String conversationLock(Long userId) {
        return "lock:conv:" + userId;
    }

    public static String presignedUrl(Long fileId, String source) {
        return "presigned:url:" + fileId + ":" + normalize(source);
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
