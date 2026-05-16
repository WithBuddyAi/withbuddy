package com.withbuddy.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class MaskedAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MaskedAccessLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String maskedPath = RequestUrlMaskingSupport.resolveMaskedPath(request);
            log.info(
                    "[ACCESS] method={}, path={}, status={}, durationMs={}",
                    request.getMethod(),
                    maskedPath,
                    response.getStatus(),
                    elapsedMs
            );
        }
    }
}
