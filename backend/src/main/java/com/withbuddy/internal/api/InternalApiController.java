package com.withbuddy.internal.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.withbuddy.internal.api.InternalApiModels.CacheDeleteRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheGetMultiRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheGetMultiResponse;
import static com.withbuddy.internal.api.InternalApiModels.CacheGetRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheGetResponse;
import static com.withbuddy.internal.api.InternalApiModels.CacheSetMultiRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheSetRequest;
import static com.withbuddy.internal.api.InternalApiModels.CacheWriteResponse;
import static com.withbuddy.internal.api.InternalApiModels.JobCreateRequest;
import static com.withbuddy.internal.api.InternalApiModels.JobCreateResponse;
import static com.withbuddy.internal.api.InternalApiModels.JobStatusResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class InternalApiController {

    private final InternalCacheApiService cacheApiService;
    private final InternalJobApiService jobApiService;

    @PostMapping("/cache/get")
    public ResponseEntity<CacheGetResponse> getCache(@Valid @RequestBody CacheGetRequest request) {
        return ResponseEntity.ok(cacheApiService.get(request));
    }

    @PostMapping("/cache/get-multi")
    public ResponseEntity<CacheGetMultiResponse> getMultiCache(@Valid @RequestBody CacheGetMultiRequest request) {
        return ResponseEntity.ok(cacheApiService.getMulti(request));
    }

    @PostMapping("/cache/set")
    public ResponseEntity<CacheWriteResponse> setCache(@Valid @RequestBody CacheSetRequest request) {
        return ResponseEntity.ok(cacheApiService.set(request));
    }

    @PostMapping("/cache/set-multi")
    public ResponseEntity<CacheWriteResponse> setMultiCache(@Valid @RequestBody CacheSetMultiRequest request) {
        return ResponseEntity.ok(cacheApiService.setMulti(request));
    }

    @PostMapping("/cache/del")
    public ResponseEntity<CacheWriteResponse> deleteCache(@Valid @RequestBody CacheDeleteRequest request) {
        return ResponseEntity.ok(cacheApiService.delete(request));
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobCreateResponse> createJob(@Valid @RequestBody JobCreateRequest request) {
        return ResponseEntity.ok(jobApiService.create(request));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable("jobId") String jobId) {
        return ResponseEntity.ok(jobApiService.getStatus(jobId));
    }

    @GetMapping("/jobs/{jobId}/result")
    public ResponseEntity<JobStatusResponse> getJobResult(@PathVariable("jobId") String jobId) {
        return ResponseEntity.ok(jobApiService.getResult(jobId));
    }
}
