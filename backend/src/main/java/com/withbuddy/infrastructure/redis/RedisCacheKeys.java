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

    public static String nudgeSent(Long userId, int day) {
        return "nudge:sent:" + userId + ":" + day;
    }

    public static String nudgeIdempotency(String eventId) {
        return "idempotency:nudge:" + eventId;
    }

    public static String conversation(String sessionId) {
        return "conversation:" + sessionId;
    }

    public static String presignedUrl(Long fileId, String source) {
        return "presigned:url:" + fileId + ":" + normalize(source);
    }

    public static String downloadToken(String token) {
        return "download:token:" + token;
    }

    public static String docsListFirstPage(String scope, String formType) {
        return "docs:list:" + scope + ":" + normalize(formType) + ":first";
    }

    public static String formGenerated(Long userId, String formType) {
        return "form:generated:" + userId + ":" + normalize(formType);
    }

    public static String loginFailureAccount(String companyCode, String employeeNumber) {
        return "login:failure:account:" + normalize(companyCode) + ":" + normalize(employeeNumber);
    }

    public static String loginFailureIp(String clientIp) {
        return "login:failure:ip:" + normalizeKeyPart(clientIp);
    }

    public static String loginLockAccount(String companyCode, String employeeNumber) {
        return "login:lock:account:" + normalize(companyCode) + ":" + normalize(employeeNumber);
    }

    public static String loginLockIp(String clientIp) {
        return "login:lock:ip:" + normalizeKeyPart(clientIp);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "ALL";
        }
        return value.trim().toUpperCase();
    }

    private static String normalizeKeyPart(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim().toLowerCase();
    }
}
