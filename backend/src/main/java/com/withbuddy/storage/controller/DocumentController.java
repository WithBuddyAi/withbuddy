package com.withbuddy.storage.controller;

import com.withbuddy.storage.dto.request.DocumentBulkDeleteRequest;
import com.withbuddy.storage.dto.response.DocumentBackupRetryResponse;
import com.withbuddy.storage.dto.response.DocumentBulkDeleteCheckResponse;
import com.withbuddy.storage.dto.response.DocumentBulkDeleteResponse;
import com.withbuddy.storage.dto.response.DocumentDeleteCheckResponse;
import com.withbuddy.storage.dto.response.DocumentDeleteResponse;
import com.withbuddy.storage.dto.response.DocumentDetailResponse;
import com.withbuddy.storage.dto.response.DocumentDownloadResponse;
import com.withbuddy.storage.dto.response.DocumentListResponse;
import com.withbuddy.storage.dto.response.DocumentUploadResponse;
import com.withbuddy.storage.entity.StorageSource;
import com.withbuddy.storage.service.DocumentStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

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
            @RequestPart("file") MultipartFile file,
            @RequestParam("title") @NotBlank String title,
            @RequestParam("documentType") @NotBlank String documentType,
            @RequestParam("department") @NotBlank String department,
            @RequestParam(value = "companyCode", required = false) String companyCode
    ) {
        DocumentUploadResponse response = documentStorageService.upload(
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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(documentStorageService.list(page, size, documentType, search));
    }

    @Operation(summary = "문서 상세 조회")
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentDetailResponse> detail(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentStorageService.getDetail(documentId));
    }

    @Operation(summary = "다운로드 URL 발급")
    @GetMapping("/{documentId}/download")
    public ResponseEntity<DocumentDownloadResponse> download(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentStorageService.getDownloadUrl(documentId));
    }

    @Operation(summary = "백업 재시도")
    @PostMapping("/{documentId}/backup/retry")
    public ResponseEntity<DocumentBackupRetryResponse> retryBackup(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentStorageService.retryBackup(documentId));
    }

    @Operation(summary = "문서 삭제")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<DocumentDeleteResponse> delete(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "false") boolean confirm
    ) {
        return ResponseEntity.ok(documentStorageService.deleteDocument(documentId, confirm));
    }

    @Operation(summary = "문서 삭제 사전 점검")
    @GetMapping("/{documentId}/delete-check")
    public ResponseEntity<DocumentDeleteCheckResponse> deleteCheck(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentStorageService.getDeleteCheck(documentId));
    }

    @Operation(summary = "문서 선택 삭제 사전 점검")
    @PostMapping("/bulk-delete/delete-check")
    public ResponseEntity<DocumentBulkDeleteCheckResponse> bulkDeleteCheck(
            @Valid @RequestBody DocumentBulkDeleteRequest request
    ) {
        return ResponseEntity.ok(documentStorageService.getBulkDeleteCheck(request));
    }

    @Operation(summary = "문서 선택 삭제 (confirm 필요)")
    @PostMapping("/bulk-delete")
    public ResponseEntity<DocumentBulkDeleteResponse> bulkDelete(
            @RequestParam(defaultValue = "false") boolean confirm,
            @Valid @RequestBody DocumentBulkDeleteRequest request
    ) {
        return ResponseEntity.ok(documentStorageService.bulkDeleteDocuments(request, confirm));
    }

    @Operation(summary = "문서 전체 삭제 사전 점검")
    @GetMapping("/delete-check")
    public ResponseEntity<DocumentBulkDeleteCheckResponse> deleteAllCheck() {
        return ResponseEntity.ok(documentStorageService.getDeleteAllCheck());
    }

    @Operation(summary = "문서 전체 삭제 (confirm 필요)")
    @DeleteMapping
    public ResponseEntity<DocumentBulkDeleteResponse> deleteAll(
            @RequestParam(defaultValue = "false") boolean confirm
    ) {
        return ResponseEntity.ok(documentStorageService.deleteAllDocuments(confirm));
    }

    @Operation(summary = "문서 파일 다운로드 (302 Redirect)")
    @GetMapping("/{documentId}/file")
    public ResponseEntity<Void> file(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "PRIMARY") StorageSource source
    ) {
        String redirectUrl = documentStorageService.issueRedirectDownloadUrl(documentId, source);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }
}
