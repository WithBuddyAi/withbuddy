package com.withbuddy.global.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class RequestUrlMaskingSupport {

    private static final String MASK = "***";

    private RequestUrlMaskingSupport() {
    }

    public static String resolveMaskedPath(HttpServletRequest request) {
        return maskPathAndQuery(request.getRequestURI(), request.getQueryString());
    }

    public static String maskPathAndQuery(String path, String queryString) {
        String safePath = StringUtils.hasText(path) ? path : "";
        if (!StringUtils.hasText(queryString)) {
            return safePath;
        }

        String maskedQuery = Arrays.stream(queryString.split("&", -1))
                .map(RequestUrlMaskingSupport::maskQuerySegment)
                .collect(Collectors.joining("&"));
        return safePath + "?" + maskedQuery;
    }

    private static String maskQuerySegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return segment;
        }

        int delimiterIndex = segment.indexOf('=');
        if (delimiterIndex < 0) {
            return MASK;
        }

        String key = segment.substring(0, delimiterIndex);
        String value = segment.substring(delimiterIndex + 1);
        if (!StringUtils.hasText(key)) {
            return MASK;
        }
        if (!StringUtils.hasText(value)) {
            return key + "=";
        }
        return key + "=" + MASK;
    }
}
