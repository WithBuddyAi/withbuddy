package com.withbuddy.storage.controller;

import com.withbuddy.storage.dto.*;
import com.withbuddy.storage.entity.StorageSource;
import com.withbuddy.storage.service.DocumentStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@Validated
@Tag(name = "Documents", description = "스토리지 문서 API")
public class DocumentController {

    private final DocumentStorageService documentStorageService;

    public DocumentController(DocumentStorageService documentStorageService) {
        this.documentStorageService = documentStorageService;
    }

    @Operation(summary = "문서 업로드")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestPart("file") MultipartFile file,
            @RequestPart("title") @NotBlank String title,
            @RequestPart("documentType") @NotBlank String documentType,
            @RequestPart("department") @NotBlank String department,
            @RequestPart(value = "companyCode", required = false) String companyCode
    ) {
        DocumentUploadResponse response = documentStorageService.upload(
                authorizationHeader,
                file,
                title,
                documentType,
                department,
                companyCode
        );
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "문서 목록 조회")
    @GetMapping
    public ResponseEntity<DocumentListResponse> list(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(documentStorageService.list(authorizationHeader, page, size, documentType, search));
    }

    @Operation(summary = "문서 상세 조회")
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentDetailResponse> detail(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(documentStorageService.getDetail(authorizationHeader, documentId));
    }

    @Operation(summary = "다운로드 URL 발급")
    @GetMapping("/{documentId}/download")
    public ResponseEntity<DocumentDownloadResponse> download(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(documentStorageService.getDownloadUrl(authorizationHeader, documentId));
    }

    @Operation(summary = "백업 재시도")
    @PostMapping("/{documentId}/backup/retry")
    public ResponseEntity<DocumentBackupRetryResponse> retryBackup(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(documentStorageService.retryBackup(authorizationHeader, documentId));
    }

    @Operation(summary = "문서 삭제")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<DocumentDeleteResponse> delete(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "false") boolean confirm
    ) {
        return ResponseEntity.ok(documentStorageService.deleteDocument(authorizationHeader, documentId, confirm));
    }

    @Operation(summary = "문서 삭제 전 검증")
    @GetMapping("/{documentId}/delete-check")
    public ResponseEntity<DocumentDeleteCheckResponse> deleteCheck(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(documentStorageService.getDeleteCheck(authorizationHeader, documentId));
    }

    @Operation(summary = "문서 선택 삭제 전 검증")
    @PostMapping("/bulk-delete/delete-check")
    public ResponseEntity<DocumentBulkDeleteCheckResponse> bulkDeleteCheck(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody DocumentBulkDeleteRequest request
    ) {
        return ResponseEntity.ok(documentStorageService.getBulkDeleteCheck(authorizationHeader, request));
    }

    @Operation(summary = "문서 선택 삭제 (confirm 필요)")
    @PostMapping("/bulk-delete")
    public ResponseEntity<DocumentBulkDeleteResponse> bulkDelete(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(defaultValue = "false") boolean confirm,
            @Valid @RequestBody DocumentBulkDeleteRequest request
    ) {
        return ResponseEntity.ok(documentStorageService.bulkDeleteDocuments(authorizationHeader, request, confirm));
    }

    @Operation(summary = "문서 전체 삭제 전 검증")
    @GetMapping("/delete-check")
    public ResponseEntity<DocumentBulkDeleteCheckResponse> deleteAllCheck(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(documentStorageService.getDeleteAllCheck(authorizationHeader));
    }

    @Operation(summary = "문서 전체 삭제 (confirm 필요)")
    @DeleteMapping
    public ResponseEntity<DocumentBulkDeleteResponse> deleteAll(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(defaultValue = "false") boolean confirm
    ) {
        return ResponseEntity.ok(documentStorageService.deleteAllDocuments(authorizationHeader, confirm));
    }

    @Operation(summary = "문서 파일 직접 다운로드 (로컬 개발용)")
    @GetMapping("/{documentId}/file")
    public ResponseEntity<ByteArrayResource> file(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "PRIMARY") StorageSource source
    ) {
        byte[] payload = documentStorageService.downloadFile(authorizationHeader, documentId, source);
        String fileName = documentStorageService.resolveDownloadFileName(documentId);
        String contentType = documentStorageService.resolveContentType(documentId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new ByteArrayResource(payload));
    }
}
