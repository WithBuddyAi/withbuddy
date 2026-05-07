package com.withbuddy.admin.metrics.controller;

import com.withbuddy.admin.metrics.dto.FirstInteractionRateResponse;
import com.withbuddy.admin.metrics.dto.RagExperienceRateResponse;
import com.withbuddy.admin.metrics.dto.RevisitRateResponse;
import com.withbuddy.admin.metrics.dto.TtaResponse;
import com.withbuddy.admin.metrics.dto.UnansweredRateResponse;
import com.withbuddy.admin.metrics.service.AdminMetricsService;
import com.withbuddy.global.security.AuthenticationPrincipalResolver;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/metrics")
public class AdminMetricsController implements AdminMetricsControllerDocs {

    private final AdminMetricsService adminMetricsService;

    @Override
    @GetMapping("/rag-experience-rate")
    public ResponseEntity<RagExperienceRateResponse> getRagExperienceRate(
            Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.ok(adminMetricsService.getRagExperienceRate(principal, companyCode, asOfDate));
    }

    @Override
    @GetMapping("/first-interaction-rate")
    public ResponseEntity<FirstInteractionRateResponse> getFirstInteractionRate(
            Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.ok(adminMetricsService.getFirstInteractionRate(principal, companyCode, asOfDate));
    }

    @Override
    @GetMapping("/revisit-rate")
    public ResponseEntity<RevisitRateResponse> getRevisitRate(
            Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.ok(adminMetricsService.getRevisitRate(principal, companyCode, asOfDate));
    }

    @Override
    @GetMapping("/unanswered-rate")
    public ResponseEntity<UnansweredRateResponse> getUnansweredRate(
            Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.ok(adminMetricsService.getUnansweredRate(principal, companyCode, asOfDate));
    }

    @Override
    @GetMapping("/tta")
    public ResponseEntity<TtaResponse> getTta(
            Authentication authentication,
            @RequestParam(required = false) String companyCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        JwtAuthenticationPrincipal principal = AuthenticationPrincipalResolver.requireJwtPrincipal(authentication);
        return ResponseEntity.ok(adminMetricsService.getTta(principal, companyCode, asOfDate));
    }
}
