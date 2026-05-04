package com.withbuddy.storage.service;

import com.withbuddy.infrastructure.storage.ObjectStorageClient;
import com.withbuddy.infrastructure.storage.StorageProperties;
import com.withbuddy.infrastructure.redis.RedisCacheKeys;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.global.security.StorageApiKeyPrincipal;
import com.withbuddy.storage.dto.DocumentBulkDeleteCheckResponse;
import com.withbuddy.storage.dto.DocumentBulkDeleteRequest;
import com.withbuddy.storage.dto.DocumentBulkDeleteResponse;
import com.withbuddy.storage.dto.DocumentBackupRetryResponse;
import com.withbuddy.storage.dto.DocumentDeleteCheckResponse;
import com.withbuddy.storage.dto.DocumentDeleteResponse;
import com.withbuddy.storage.dto.DocumentDetailResponse;
import com.withbuddy.storage.dto.DocumentDownloadResponse;
import com.withbuddy.storage.dto.DocumentListItemResponse;
import com.withbuddy.storage.dto.DocumentListResponse;
import com.withbuddy.storage.dto.DocumentUploadResponse;
import com.withbuddy.storage.entity.BackupStatus;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentBackupJob;
import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.entity.StorageSource;
import com.withbuddy.storage.exception.StorageException;
import com.withbuddy.storage.repository.DocumentBackupJobRepository;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentStorageService {
    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);
    private static final int BACKUP_JOB_ERROR_MAX_LENGTH = 1000;
    private static final String DEFAULT_DOCUMENT_CONTENT = "Object Storage 업로드 문서";

    private enum DeleteOutcome {
        DELETED,
        NOT_FOUND,
        FORBIDDEN
    }

    private record DeleteInspection(
            List<Long> deletableIds,
            List<Long> notFoundIds,
            List<Long> forbiddenIds
    ) {
    }

    private record RequesterScope(
            String companyCode,
            boolean globalAccess
    ) {
    }

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentBackupJobRepository documentBackupJobRepository;
    private final ObjectStorageClient objectStorageClient;
    private final StorageProperties storageProperties;
    private final RedisCacheService redisCacheService;

    public DocumentStorageService(
            DocumentRepository documentRepository,
            DocumentFileRepository documentFileRepository,
            DocumentBackupJobRepository documentBackupJobRepository,
            ObjectStorageClient objectStorageClient,
            StorageProperties storageProperties,
            RedisCacheService redisCacheService
    ) {
        this.documentRepository = documentRepository;
        this.documentFileRepository = documentFileRepository;
        this.documentBackupJobRepository = documentBackupJobRepository;
        this.objectStorageClient = objectStorageClient;
        this.storageProperties = storageProperties;
        this.redisCacheService = redisCacheService;
    }

    @Transactional
    public DocumentUploadResponse upload(
            MultipartFile file,
            String title,
            String documentType,
            String department,
            String requestCompanyCode
    ) {
        RequesterScope requesterScope = resolveRequesterScope();
        validateFile(file);

        String finalCompanyCode;
        if (requesterScope.globalAccess()) {
            finalCompanyCode = StringUtils.hasText(requestCompanyCode) ? requestCompanyCode : null;
        } else {
            finalCompanyCode = requesterScope.companyCode();
            if (StringUtils.hasText(requestCompanyCode) && !requestCompanyCode.equals(requesterScope.companyCode())) {
                throw new StorageException(
                        HttpStatus.FORBIDDEN,
                        "RESOURCE_004",
                        "companyCode",
                        "다른 회사 문서에는 접근할 수 없습니다."
                );
            }
        }

        byte[] payload = toBytes(file);
        String extension = resolveExtension(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID() + extension;
        String objectKey = buildObjectKey(finalCompanyCode, storedFileName);

        StorageProperties.Bucket primary = storageProperties.getPrimary();
        StorageProperties.Bucket backup = storageProperties.getBackup();

        try {
            objectStorageClient.putObject(primary.getNamespace(), primary.getBucket(), objectKey, payload);
        } catch (Exception e) {
            log.error(
                    "원본 스토리지 업로드 실패 (namespace={}, bucket={}, key={}, reason={})",
                    primary.getNamespace(),
                    primary.getBucket(),
                    objectKey,
                    safeMessage(e),
                    e
            );
            throw new StorageException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_003",
                    "file",
                    "원본 스토리지 업로드에 실패했습니다."
            );
        }

        Document document;
        DocumentFile documentFile;
        try {
            document = documentRepository.save(
                    Document.builder()
                            .companyCode(finalCompanyCode)
                            .title(title)
                            .content(DEFAULT_DOCUMENT_CONTENT)
                            .documentType(documentType)
                            .department(department)
                            .isActive(true)
                            .build()
            );

            documentFile = documentFileRepository.save(
                    DocumentFile.builder()
                            .documentId(document.getId())
                            .companyCode(finalCompanyCode)
                            .originalFileName(Optional.ofNullable(file.getOriginalFilename()).orElse(storedFileName))
                            .storedFileName(storedFileName)
                            .contentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"))
                            .fileSize(file.getSize())
                            .checksumSha256(sha256(payload))
                            .primaryNamespace(primary.getNamespace())
                            .primaryBucket(primary.getBucket())
                            .primaryObjectKey(objectKey)
                            .backupNamespace(backup.getNamespace())
                            .backupBucket(backup.getBucket())
                            .backupStatus(BackupStatus.PENDING)
                            .backupAttemptCount(0)
                            .backupRequestedAt(LocalDateTime.now())
                            .build()
            );
        } catch (RuntimeException e) {
            compensatePrimaryObject(primary.getNamespace(), primary.getBucket(), objectKey, e);
            throw new StorageException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_003",
                    "file",
                    "문서 메타데이터 저장에 실패했습니다."
            );
        }

        executeBackupAttempt(documentFile, payload, "UPLOAD_SYNC");

        return new DocumentUploadResponse(
                document.getId(),
                document.getTitle(),
                documentFile.getOriginalFileName(),
                documentFile.getContentType(),
                documentFile.getFileSize(),
                StorageSource.PRIMARY.name(),
                documentFile.getBackupStatus().name(),
                document.getCreatedAt()
        );
    }

    public DocumentBackupRetryResponse retryBackup(Long documentId) {
        RequesterScope requesterScope = resolveRequesterScope();

        Document document = documentRepository.findByIdAndIsActiveTrue(documentId)
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."));
        validateCompanyBoundary(requesterScope, document.getCompanyCode());

        DocumentFile documentFile = documentFileRepository.findByDocumentId(document.getId())
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서 파일 메타데이터를 찾을 수 없습니다."));

        if (documentFile.getBackupStatus() == BackupStatus.COMPLETED && StringUtils.hasText(documentFile.getBackupObjectKey())) {
            return toRetryResponse(documentId, documentFile);
        }

        retryBackupFromPrimary(documentFile, "MANUAL_RETRY", true);
        return toRetryResponse(documentId, documentFile);
    }

    @Transactional
    public DocumentDeleteResponse deleteDocument(Long documentId, boolean confirm) {
        RequesterScope requesterScope = resolveRequesterScope();
        if (!confirm) {
            throw new StorageException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "confirm", "문서 삭제는 confirm=true가 필요합니다.");
        }

        DeleteOutcome outcome = deleteDocumentInternal(requesterScope, documentId);
        if (outcome == DeleteOutcome.NOT_FOUND) {
            throw new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다.");
        }
        if (outcome == DeleteOutcome.FORBIDDEN) {
            throw new StorageException(HttpStatus.FORBIDDEN, "RESOURCE_004", "documentId", "다른 회사 문서에는 접근할 수 없습니다.");
        }

        return new DocumentDeleteResponse(documentId, "문서가 성공적으로 삭제되었습니다.");
    }

    public DocumentDeleteCheckResponse getDeleteCheck(Long documentId) {
        RequesterScope requesterScope = resolveRequesterScope();

        Document document = documentRepository.findByIdAndIsActiveTrue(documentId)
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."));
        validateCompanyBoundary(requesterScope, document.getCompanyCode());

        Optional<DocumentFile> optionalFile = documentFileRepository.findByDocumentId(documentId);

        return new DocumentDeleteCheckResponse(
                document.getId(),
                document.getTitle(),
                document.getCompanyCode(),
                document.isActive(),
                optionalFile.map(DocumentFile::getOriginalFileName).orElse(null),
                optionalFile.map(DocumentFile::getFileSize).orElse(null),
                optionalFile.map(file -> file.getBackupStatus().name()).orElse(null),
                document.getCreatedAt(),
                true
        );
    }

    public DocumentBulkDeleteCheckResponse getBulkDeleteCheck(DocumentBulkDeleteRequest request) {
        RequesterScope requesterScope = resolveRequesterScope();
        LinkedHashSet<Long> deduplicatedIds = deduplicateDocumentIds(request.getDocumentIds());

        DeleteInspection inspection = inspectDeleteOutcomes(requesterScope, deduplicatedIds);
        String message = buildBulkDeleteCheckMessage(
                deduplicatedIds.size(),
                inspection.deletableIds(),
                inspection.notFoundIds(),
                inspection.forbiddenIds()
        );

        return new DocumentBulkDeleteCheckResponse(
                true,
                message,
                deduplicatedIds.size(),
                inspection.deletableIds().size(),
                inspection.deletableIds(),
                inspection.notFoundIds(),
                inspection.forbiddenIds()
        );
    }

    public DocumentBulkDeleteCheckResponse getDeleteAllCheck() {
        RequesterScope requesterScope = resolveRequesterScope();
        List<Long> allDocumentIds = requesterScope.globalAccess()
                ? documentRepository.findAllActiveDocumentIds()
                : documentRepository.findActiveDocumentIdsByCompanyCode(requesterScope.companyCode());

        String message = buildBulkDeleteCheckMessage(
                allDocumentIds.size(),
                allDocumentIds,
                List.of(),
                List.of()
        );

        return new DocumentBulkDeleteCheckResponse(
                true,
                message,
                allDocumentIds.size(),
                allDocumentIds.size(),
                allDocumentIds,
                List.of(),
                List.of()
        );
    }

    @Transactional
    public DocumentBulkDeleteResponse bulkDeleteDocuments(
            DocumentBulkDeleteRequest request,
            boolean confirm
    ) {
        RequesterScope requesterScope = resolveRequesterScope();
        if (!confirm) {
            throw new StorageException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "confirm", "선택 삭제는 confirm=true가 필요합니다.");
        }

        return executeBulkDelete(requesterScope, request.getDocumentIds());
    }

    @Transactional
    public DocumentBulkDeleteResponse deleteAllDocuments(boolean confirm) {
        RequesterScope requesterScope = resolveRequesterScope();
        if (!confirm) {
            throw new StorageException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "confirm", "전체 삭제는 confirm=true가 필요합니다.");
        }

        List<Long> allDocumentIds = requesterScope.globalAccess()
                ? documentRepository.findAllActiveDocumentIds()
                : documentRepository.findActiveDocumentIdsByCompanyCode(requesterScope.companyCode());
        return executeBulkDelete(requesterScope, allDocumentIds);
    }

    @Scheduled(fixedDelayString = "${app.storage.retry.interval-ms:60000}")
    public void processBackupRetries() {
        StorageProperties.Retry retry = storageProperties.getRetry();
        if (!retry.isEnabled()) {
            return;
        }

        List<DocumentFile> candidates = documentFileRepository.findByBackupStatusInAndDeletedAtIsNull(
                List.of(BackupStatus.PENDING, BackupStatus.FAILED),
                PageRequest.of(0, retry.getBatchSize(), Sort.by(Sort.Direction.ASC, "backupRequestedAt"))
        );

        LocalDateTime now = LocalDateTime.now();
        for (DocumentFile candidate : candidates) {
            if (!isRetryDue(candidate, now, retry)) {
                continue;
            }
            retryBackupFromPrimary(candidate, "AUTO_RETRY", false);
        }
    }

    public DocumentListResponse list(
            int page,
            int size,
            String documentType,
            String search
    ) {
        RequesterScope requesterScope = resolveRequesterScope();

        Page<Document> documentPage;
        if (requesterScope.globalAccess()) {
            documentPage = documentRepository.searchDocumentsForAdmin(
                    emptyToNull(documentType),
                    emptyToNull(search),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
        } else {
            documentPage = documentRepository.searchDocuments(
                    requesterScope.companyCode(),
                    emptyToNull(documentType),
                    emptyToNull(search),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
        }

        List<Long> documentIds = documentPage.getContent().stream()
                .map(Document::getId)
                .toList();

        Map<Long, DocumentFile> fileMap = Map.of();
        if (!documentIds.isEmpty()) {
            try {
                fileMap = documentFileRepository.findByDocumentIdIn(documentIds).stream()
                        .collect(Collectors.toMap(
                                DocumentFile::getDocumentId,
                                documentFile -> documentFile,
                                (existing, replacement) -> replacement
                        ));
            } catch (RuntimeException e) {
                log.warn("문서 파일 메타데이터 조회 실패, 메타데이터 없이 목록을 반환합니다. reason={}", safeMessage(e));
            }
        }

        Map<Long, DocumentFile> resolvedFileMap = fileMap;
        List<DocumentListItemResponse> content = documentPage.getContent().stream()
                .map(document -> toListItem(document, resolvedFileMap.get(document.getId())))
                .toList();

        return new DocumentListResponse(
                content,
                documentPage.getTotalElements(),
                documentPage.getTotalPages(),
                documentPage.getSize(),
                documentPage.getNumber()
        );
    }

    public DocumentDetailResponse getDetail(Long documentId) {
        RequesterScope requesterScope = resolveRequesterScope();

        Document document = documentRepository.findByIdAndIsActiveTrue(documentId)
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."));

        validateCompanyBoundary(requesterScope, document.getCompanyCode());

        DocumentFile file = documentFileRepository.findByDocumentId(document.getId())
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서 파일 메타데이터를 찾을 수 없습니다."));

        return new DocumentDetailResponse(
                document.getId(),
                document.getTitle(),
                document.getDocumentType(),
                document.getDepartment(),
                document.isActive(),
                new DocumentDetailResponse.FileMetadata(
                        file.getOriginalFileName(),
                        file.getContentType(),
                        file.getFileSize(),
                        file.getChecksumSha256(),
                        StorageSource.PRIMARY.name(),
                        file.getBackupStatus().name()
                ),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private void validateTemplateDocument(Document document) {
        if (!"TEMPLATE".equals(document.getDocumentType())) {
            throw new StorageException(
                    HttpStatus.FORBIDDEN,
                    "RESOURCE_004",
                    "documentType",
                    "TEMPLATE 문서만 다운로드할 수 있습니다."
            );
        }
    }

    public DocumentDownloadResponse getDownloadUrl(Long documentId) {
        RequesterScope requesterScope = resolveRequesterScope();

        Document document = documentRepository.findByIdAndIsActiveTrue(documentId)
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."));

        validateCompanyBoundary(requesterScope, document.getCompanyCode());
        if (!requesterScope.globalAccess()) {
            validateTemplateDocument(document);
        }

        DocumentFile file = documentFileRepository.findByDocumentId(document.getId())
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서 파일 메타데이터를 찾을 수 없습니다."));

        StorageSource source = resolveSource(file);
        int preauthTtlSeconds = Math.max(1, storageProperties.getOciCli().getPreauthTtlSeconds());
        String redisKey = RedisCacheKeys.presignedUrl(file.getId(), source.name());
        String downloadUrl = redisCacheService.get(redisKey)
                .filter(StringUtils::hasText)
                .orElseGet(() -> createAndCacheDownloadUrl(documentId, file, source, redisKey, preauthTtlSeconds));
        return new DocumentDownloadResponse(downloadUrl, preauthTtlSeconds, source.name());
    }

    private String createAndCacheDownloadUrl(
            Long documentId,
            DocumentFile file,
            StorageSource source,
            String redisKey,
            int preauthTtlSeconds
    ) {
        try {
            String preSignedUrl = source == StorageSource.BACKUP
                    ? objectStorageClient.createPreSignedGetUrl(
                    file.getBackupNamespace(),
                    file.getBackupBucket(),
                    file.getBackupObjectKey(),
                    preauthTtlSeconds
            )
                    : objectStorageClient.createPreSignedGetUrl(
                    file.getPrimaryNamespace(),
                    file.getPrimaryBucket(),
                    file.getPrimaryObjectKey(),
                    preauthTtlSeconds
            );

            if (StringUtils.hasText(preSignedUrl)) {
                redisCacheService.put(redisKey, preSignedUrl, Duration.ofSeconds(preauthTtlSeconds));
                return preSignedUrl;
            }
        } catch (Exception e) {
            log.warn(
                    "pre-signed URL 생성 실패. documentId={}, source={}, reason={}",
                    documentId,
                    source.name(),
                    safeMessage(e)
            );
        }
        return "/api/v1/documents/" + documentId + "/file?source=" + source.name();
    }

    public byte[] downloadFile(Long documentId, StorageSource source) {
        RequesterScope requesterScope = resolveRequesterScope();

        Document document = documentRepository.findByIdAndIsActiveTrue(documentId)
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."));

        validateCompanyBoundary(requesterScope, document.getCompanyCode());
        if (!requesterScope.globalAccess()) {
            validateTemplateDocument(document);
        }

        DocumentFile file = documentFileRepository.findByDocumentId(document.getId())
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서 파일 메타데이터를 찾을 수 없습니다."));

        if (source == StorageSource.BACKUP) {
            if (!StringUtils.hasText(file.getBackupObjectKey())) {
                throw new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "source", "백업 파일을 찾을 수 없습니다.");
            }
            return objectStorageClient.getObject(file.getBackupNamespace(), file.getBackupBucket(), file.getBackupObjectKey());
        }

        return objectStorageClient.getObject(file.getPrimaryNamespace(), file.getPrimaryBucket(), file.getPrimaryObjectKey());
    }

    public String resolveDownloadFileName(Long documentId) {
        return documentFileRepository.findByDocumentId(documentId)
                .map(DocumentFile::getOriginalFileName)
                .orElse("download.bin");
    }

    public String resolveContentType(Long documentId) {
        return documentFileRepository.findByDocumentId(documentId)
                .map(DocumentFile::getContentType)
                .orElse("application/octet-stream");
    }

    private void retryBackupFromPrimary(DocumentFile documentFile, String trigger, boolean manual) {
        if (!manual && documentFile.getBackupAttemptCount() >= storageProperties.getRetry().getMaxAttempts()) {
            return;
        }

        try {
            byte[] payload = objectStorageClient.getObject(
                    documentFile.getPrimaryNamespace(),
                    documentFile.getPrimaryBucket(),
                    documentFile.getPrimaryObjectKey()
            );
            executeBackupAttempt(documentFile, payload, trigger);
        } catch (Exception e) {
            String backupError = truncate("[" + trigger + "] " + safeMessage(e), BACKUP_JOB_ERROR_MAX_LENGTH);
            documentFile.markBackupFailed(backupError);
            documentFileRepository.save(documentFile);
            saveBackupJob(
                    documentFile,
                    "FAILED",
                    documentFile.getBackupAttemptCount(),
                    null,
                    backupError,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            log.warn("백업 재시도 실패 (trigger={}, documentFileId={}): {}", trigger, documentFile.getId(), backupError);
        }
    }

    private void executeBackupAttempt(DocumentFile documentFile, byte[] payload, String trigger) {
        int attemptNo = documentFile.getBackupAttemptCount() + 1;
        LocalDateTime startedAt = LocalDateTime.now();
        String backupObjectKey = documentFile.getPrimaryObjectKey();

        documentFile.markBackupInProgress();
        documentFileRepository.save(documentFile);

        try {
            objectStorageClient.putObject(
                    documentFile.getBackupNamespace(),
                    documentFile.getBackupBucket(),
                    backupObjectKey,
                    payload
            );
            documentFile.markBackupCompleted(backupObjectKey);
            documentFileRepository.save(documentFile);
            saveBackupJob(
                    documentFile,
                    "COMPLETED",
                    attemptNo,
                    backupObjectKey,
                    null,
                    startedAt,
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            String backupError = truncate("[" + trigger + "] " + safeMessage(e), BACKUP_JOB_ERROR_MAX_LENGTH);
            documentFile.markBackupFailed(backupError);
            documentFileRepository.save(documentFile);
            saveBackupJob(
                    documentFile,
                    "FAILED",
                    attemptNo,
                    null,
                    backupError,
                    startedAt,
                    LocalDateTime.now()
            );
            log.warn("백업 처리 실패 (trigger={}, documentFileId={}): {}", trigger, documentFile.getId(), backupError);
        }
    }

    private void saveBackupJob(
            DocumentFile documentFile,
            String status,
            int attemptNo,
            String targetObjectKey,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        documentBackupJobRepository.save(
                DocumentBackupJob.builder()
                        .documentFileId(documentFile.getId())
                        .status(status)
                        .attemptNo(attemptNo)
                        .sourceNamespace(documentFile.getPrimaryNamespace())
                        .sourceBucket(documentFile.getPrimaryBucket())
                        .sourceObjectKey(documentFile.getPrimaryObjectKey())
                        .targetNamespace(documentFile.getBackupNamespace())
                        .targetBucket(documentFile.getBackupBucket())
                        .targetObjectKey(targetObjectKey)
                        .errorMessage(errorMessage)
                        .startedAt(startedAt)
                        .finishedAt(finishedAt)
                        .build()
        );
    }

    private boolean isRetryDue(DocumentFile documentFile, LocalDateTime now, StorageProperties.Retry retry) {
        if (documentFile.getBackupStatus() == BackupStatus.PENDING && documentFile.getBackupAttemptCount() == 0) {
            return true;
        }

        if (documentFile.getBackupAttemptCount() >= retry.getMaxAttempts()) {
            return false;
        }

        long backoffSeconds = calculateBackoffSeconds(documentFile.getBackupAttemptCount(), retry);
        return !documentFile.getBackupRequestedAt().plusSeconds(backoffSeconds).isAfter(now);
    }

    private long calculateBackoffSeconds(int failedAttempts, StorageProperties.Retry retry) {
        if (failedAttempts <= 0) {
            return 0;
        }

        long delay = retry.getBaseBackoffSeconds();
        for (int i = 1; i < failedAttempts; i++) {
            if (delay >= retry.getMaxBackoffSeconds()) {
                return retry.getMaxBackoffSeconds();
            }
            delay = Math.min(delay * 2, retry.getMaxBackoffSeconds());
        }
        return delay;
    }

    private DocumentBackupRetryResponse toRetryResponse(Long documentId, DocumentFile documentFile) {
        return new DocumentBackupRetryResponse(
                documentId,
                documentFile.getBackupStatus().name(),
                documentFile.getBackupAttemptCount(),
                documentFile.getBackupLastError(),
                documentFile.getBackupCompletedAt()
        );
    }

    private DocumentBulkDeleteResponse executeBulkDelete(RequesterScope requesterScope, List<Long> documentIds) {
        LinkedHashSet<Long> deduplicatedIds = deduplicateDocumentIds(documentIds);

        List<Long> deletedIds = new ArrayList<>();
        List<Long> notFoundIds = new ArrayList<>();
        List<Long> forbiddenIds = new ArrayList<>();

        for (Long documentId : deduplicatedIds) {
            DeleteOutcome outcome = deleteDocumentInternal(requesterScope, documentId);
            if (outcome == DeleteOutcome.DELETED) {
                deletedIds.add(documentId);
            } else if (outcome == DeleteOutcome.NOT_FOUND) {
                notFoundIds.add(documentId);
            } else {
                forbiddenIds.add(documentId);
            }
        }

        String message = buildBulkDeleteMessage(
                deduplicatedIds.size(),
                deletedIds,
                notFoundIds,
                forbiddenIds
        );

        return new DocumentBulkDeleteResponse(
                true,
                message,
                deduplicatedIds.size(),
                deletedIds.size(),
                deletedIds,
                notFoundIds,
                forbiddenIds
        );
    }

    private LinkedHashSet<Long> deduplicateDocumentIds(List<Long> documentIds) {
        return new LinkedHashSet<>(documentIds);
    }

    private DeleteInspection inspectDeleteOutcomes(RequesterScope requesterScope, LinkedHashSet<Long> deduplicatedIds) {
        List<Long> deletableIds = new ArrayList<>();
        List<Long> notFoundIds = new ArrayList<>();
        List<Long> forbiddenIds = new ArrayList<>();

        for (Long documentId : deduplicatedIds) {
            DeleteOutcome outcome = evaluateDeleteOutcome(requesterScope, documentId);
            if (outcome == DeleteOutcome.DELETED) {
                deletableIds.add(documentId);
            } else if (outcome == DeleteOutcome.NOT_FOUND) {
                notFoundIds.add(documentId);
            } else {
                forbiddenIds.add(documentId);
            }
        }

        return new DeleteInspection(deletableIds, notFoundIds, forbiddenIds);
    }

    private DeleteOutcome evaluateDeleteOutcome(RequesterScope requesterScope, Long documentId) {
        Optional<Document> optionalDocument = documentRepository.findByIdAndIsActiveTrue(documentId);
        if (optionalDocument.isEmpty()) {
            return DeleteOutcome.NOT_FOUND;
        }

        Document document = optionalDocument.get();
        if (!requesterScope.globalAccess()
                && StringUtils.hasText(document.getCompanyCode())
                && !document.getCompanyCode().equals(requesterScope.companyCode())) {
            return DeleteOutcome.FORBIDDEN;
        }

        return DeleteOutcome.DELETED;
    }

    private DeleteOutcome deleteDocumentInternal(RequesterScope requesterScope, Long documentId) {
        DeleteOutcome outcome = evaluateDeleteOutcome(requesterScope, documentId);
        if (outcome != DeleteOutcome.DELETED) {
            return outcome;
        }

        Document document = documentRepository.findByIdAndIsActiveTrue(documentId)
                .orElseThrow(() -> new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "문서를 찾을 수 없습니다."));

        Optional<DocumentFile> optionalFile = documentFileRepository.findByDocumentId(document.getId());
        document.softDelete();
        documentRepository.save(document);

        optionalFile.ifPresent(documentFile -> {
            documentFile.softDelete();
            documentFileRepository.save(documentFile);
        });
        optionalFile.ifPresent(this::deleteObjectsBestEffort);

        return DeleteOutcome.DELETED;
    }

    private DocumentListItemResponse toListItem(Document document, DocumentFile file) {
        if (file == null) {
            return new DocumentListItemResponse(
                    document.getId(),
                    document.getTitle(),
                    document.getDocumentType(),
                    document.getDepartment(),
                    null,
                    null,
                    null,
                    BackupStatus.PENDING.name(),
                    document.getCreatedAt()
            );
        }

        return new DocumentListItemResponse(
                document.getId(),
                document.getTitle(),
                document.getDocumentType(),
                document.getDepartment(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getFileSize(),
                file.getBackupStatus().name(),
                document.getCreatedAt()
        );
    }

    private StorageSource resolveSource(DocumentFile file) {
        boolean primaryExists = objectStorageClient.exists(
                file.getPrimaryNamespace(),
                file.getPrimaryBucket(),
                file.getPrimaryObjectKey()
        );
        if (primaryExists) {
            return StorageSource.PRIMARY;
        }

        boolean backupExists = StringUtils.hasText(file.getBackupObjectKey()) &&
                objectStorageClient.exists(file.getBackupNamespace(), file.getBackupBucket(), file.getBackupObjectKey());
        if (backupExists) {
            return StorageSource.BACKUP;
        }

        throw new StorageException(HttpStatus.NOT_FOUND, "NOT_FOUND", "documentId", "다운로드 가능한 파일을 찾을 수 없습니다.");
    }

    private void validateCompanyBoundary(RequesterScope requesterScope, String ownerCompanyCode) {
        if (!requesterScope.globalAccess()
                && StringUtils.hasText(ownerCompanyCode)
                && !ownerCompanyCode.equals(requesterScope.companyCode())) {
            throw new StorageException(HttpStatus.FORBIDDEN, "RESOURCE_004", "documentId", "다른 회사 문서에는 접근할 수 없습니다.");
        }
    }

    private RequesterScope resolveRequesterScope() {
        RequesterScope scopeFromApiKey = resolveScopeFromApiKeyAuthentication();
        if (scopeFromApiKey != null) {
            return scopeFromApiKey;
        }

        RequesterScope scopeFromJwt = resolveScopeFromJwtAuthentication();
        if (scopeFromJwt != null) {
            return scopeFromJwt;
        }

        throw new StorageException(HttpStatus.UNAUTHORIZED, "TOKEN_MISSING", "auth", "인증 토큰이 없습니다.");
    }

    private RequesterScope resolveScopeFromApiKeyAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof StorageApiKeyPrincipal storagePrincipal) {
            return new RequesterScope(null, storagePrincipal.globalAccess());
        }
        return null;
    }

    private String extractToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new StorageException(HttpStatus.UNAUTHORIZED, "TOKEN_MISSING", "auth", "인증 토큰이 없습니다.");
        }

        return authorizationHeader.substring(7);
    }

    private RequesterScope resolveScopeFromJwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtAuthenticationPrincipal jwtPrincipal) {
            return new RequesterScope(jwtPrincipal.companyCode(), false);
        }
        return null;
    }

    private void compensatePrimaryObject(String namespace, String bucket, String objectKey, Exception rootCause) {
        try {
            objectStorageClient.deleteObject(namespace, bucket, objectKey);
            log.warn("메타데이터 저장 실패 보상 완료: primary object 삭제 (namespace={}, bucket={}, key={})", namespace, bucket, objectKey);
        } catch (Exception compensationError) {
            log.error(
                    "메타데이터 저장 실패 보상 중 primary object 삭제 실패 (namespace={}, bucket={}, key={}, reason={})",
                    namespace,
                    bucket,
                    objectKey,
                    safeMessage(compensationError)
            );
        }
        log.error("문서 메타데이터 저장 실패", rootCause);
    }

    private void deleteObjectsBestEffort(DocumentFile file) {
        try {
            objectStorageClient.deleteObject(
                    file.getPrimaryNamespace(),
                    file.getPrimaryBucket(),
                    file.getPrimaryObjectKey()
            );
        } catch (Exception e) {
            log.warn(
                    "원본 객체 삭제 실패 (documentFileId={}, namespace={}, bucket={}, key={}, reason={})",
                    file.getId(),
                    file.getPrimaryNamespace(),
                    file.getPrimaryBucket(),
                    file.getPrimaryObjectKey(),
                    safeMessage(e)
            );
        }

        if (!StringUtils.hasText(file.getBackupObjectKey())) {
            return;
        }

        try {
            objectStorageClient.deleteObject(
                    file.getBackupNamespace(),
                    file.getBackupBucket(),
                    file.getBackupObjectKey()
            );
        } catch (Exception e) {
            log.warn(
                    "백업 객체 삭제 실패 (documentFileId={}, namespace={}, bucket={}, key={}, reason={})",
                    file.getId(),
                    file.getBackupNamespace(),
                    file.getBackupBucket(),
                    file.getBackupObjectKey(),
                    safeMessage(e)
            );
        }
    }

    private String buildObjectKey(String companyCode, String storedFileName) {
        String tenantSegment = StringUtils.hasText(companyCode) ? companyCode : "COMMON";
        return "companies/" + tenantSegment + "/documents/" + LocalDateTime.now().toLocalDate() + "/" + storedFileName;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException(HttpStatus.BAD_REQUEST, "FILE_001", "file", "업로드 파일이 비어 있습니다.");
        }

        String originalFileName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        String extension = resolveExtension(originalFileName).toLowerCase(Locale.ROOT);
        Set<String> allowed = Set.of(".pdf", ".docx", ".pptx", ".txt", ".xls", ".xlsx", ".png", ".jpg", ".jpeg");
        if (!allowed.contains(extension)) {
            throw new StorageException(HttpStatus.BAD_REQUEST, "FILE_002", "file", "지원하지 않는 파일 형식입니다.");
        }

        long maxSizeBytes;
        if (extension.equals(".png") || extension.equals(".jpg") || extension.equals(".jpeg")) {
            maxSizeBytes = storageProperties.getMaxImageSizeMb() * 1024L * 1024L;
        } else {
            maxSizeBytes = storageProperties.getMaxDocumentSizeMb() * 1024L * 1024L;
        }

        if (file.getSize() > maxSizeBytes) {
            throw new StorageException(HttpStatus.BAD_REQUEST, "FILE_001", "file", "파일 크기 제한을 초과했습니다.");
        }
    }

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new StorageException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_003", "file", "파일 읽기에 실패했습니다.");
        }
    }

    private String resolveExtension(String originalFileName) {
        if (!StringUtils.hasText(originalFileName) || !originalFileName.contains(".")) {
            return "";
        }

        int lastDotIndex = originalFileName.lastIndexOf(".");
        if (lastDotIndex == originalFileName.length() - 1) {
            return "";
        }
        return originalFileName.substring(originalFileName.lastIndexOf("."));
    }

    private String sha256(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String buildBulkDeleteCheckMessage(
            int requestedCount,
            List<Long> deletableIds,
            List<Long> notFoundIds,
            List<Long> forbiddenIds
    ) {
        int deletableCount = deletableIds.size();
        int blockedCount = requestedCount - deletableCount;

        StringBuilder message = new StringBuilder();
        message.append("삭제 가능 ")
                .append(deletableCount)
                .append("/")
                .append(requestedCount)
                .append("건, 삭제 불가 ")
                .append(blockedCount)
                .append("/")
                .append(requestedCount)
                .append("건");

        if (blockedCount > 0) {
            List<String> reasons = new ArrayList<>();
            if (!notFoundIds.isEmpty()) {
                reasons.add("존재하지 않는 문서: " + formatDocumentIds(notFoundIds));
            }
            if (!forbiddenIds.isEmpty()) {
                reasons.add("삭제 제한 문서: " + formatDocumentIds(forbiddenIds));
            }
            message.append(", 사유: ").append(String.join("; ", reasons));
        } else {
            message.append(", 삭제를 진행하시겠습니까?");
        }

        return message.toString();
    }

    private String buildBulkDeleteMessage(
            int requestedCount,
            List<Long> deletedIds,
            List<Long> notFoundIds,
            List<Long> forbiddenIds
    ) {
        int deletedCount = deletedIds.size();
        int failedCount = requestedCount - deletedCount;

        StringBuilder message = new StringBuilder();
        message.append("완료 ")
                .append(deletedCount)
                .append("/")
                .append(requestedCount)
                .append("건, 실패 ")
                .append(failedCount)
                .append("/")
                .append(requestedCount)
                .append("건");

        if (failedCount > 0) {
            List<String> reasons = new ArrayList<>();
            if (!notFoundIds.isEmpty()) {
                reasons.add("존재하지 않는 문서: " + formatDocumentIds(notFoundIds));
            }
            if (!forbiddenIds.isEmpty()) {
                reasons.add("삭제 제한 문서: " + formatDocumentIds(forbiddenIds));
            }
            if (reasons.isEmpty()) {
                reasons.add("원인 분류 불가");
            }
            message.append(", 실패원인: ").append(String.join("; ", reasons));
        }

        return message.toString();
    }

    private String formatDocumentIds(List<Long> documentIds) {
        return documentIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
