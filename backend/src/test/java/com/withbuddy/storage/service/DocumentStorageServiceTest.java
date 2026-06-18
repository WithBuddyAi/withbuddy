package com.withbuddy.storage.service;

import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.storage.ObjectStorageClient;
import com.withbuddy.infrastructure.storage.StorageProperties;
import com.withbuddy.storage.dto.response.DocumentDownloadResponse;
import com.withbuddy.storage.dto.response.DocumentListResponse;
import com.withbuddy.storage.entity.BackupStatus;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.exception.StorageException;
import com.withbuddy.storage.repository.DocumentBackupJobRepository;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentStorageServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentFileRepository documentFileRepository;

    @Mock
    private DocumentBackupJobRepository documentBackupJobRepository;

    @Mock
    private ObjectStorageClient objectStorageClient;

    @Mock
    private StorageProperties storageProperties;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DocumentStorageService documentStorageService;

    @BeforeEach
    void setUpAuthentication() {
        JwtAuthenticationPrincipal principal = new JwtAuthenticationPrincipal(
                1L,
                "A001",
                "관리자",
                "WB0001",
                "위드버디",
                "2026-06-01"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        User admin = mock(User.class);
        Company company = mock(Company.class);
        when(admin.isActiveAdmin()).thenReturn(true);
        when(admin.getCompany()).thenReturn(company);
        when(admin.getId()).thenReturn(1L);
        when(company.getCompanyCode()).thenReturn("WB0001");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void issuesDownloadUrlForRequiredCommonTemplate() {
        Document document = document(56L, null, "TEMPLATE");
        DocumentFile file = documentFile(56L);
        when(documentRepository.findByIdAndIsActiveTrue(56L)).thenReturn(Optional.of(document));
        when(documentFileRepository.findByDocumentId(56L)).thenReturn(Optional.of(file));
        when(objectStorageClient.exists("primary-ns", "primary-bucket", "documents/56.pdf")).thenReturn(true);
        when(storageProperties.getDownloadUrlTtlSeconds()).thenReturn(30);
        when(storageProperties.getDownloadUrlMaxUses()).thenReturn(1);

        DocumentDownloadResponse response =
                documentStorageService.getAdminDocumentDownloadUrl(56L);

        assertThat(response.getDownloadUrl())
                .startsWith("/api/v1/documents/56/file?source=PRIMARY&token=");
        assertThat(response.getExpiresIn()).isEqualTo(30);
        assertThat(response.getSource()).isEqualTo("PRIMARY");
        verify(redisCacheService).putHash(
                anyString(),
                anyMap(),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void rejectsDocumentOutsideRequiredTemplateIds() {
        Document document = document(65L, null, "TEMPLATE");
        when(documentRepository.findByIdAndIsActiveTrue(65L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() ->
                documentStorageService.getAdminDocumentDownloadUrl(65L)
        )
                .isInstanceOfSatisfying(StorageException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("RESOURCE_004");
                    assertThat(exception.getField()).isEqualTo("documentId");
                });

        verify(documentFileRepository, never()).findByDocumentId(any());
    }

    @Test
    void issuesDownloadUrlForOwnCompanyTemplate() {
        Document document = document(56L, "WB0001", "TEMPLATE");
        DocumentFile file = documentFile(56L);
        when(documentRepository.findByIdAndIsActiveTrue(56L)).thenReturn(Optional.of(document));
        when(documentFileRepository.findByDocumentId(56L)).thenReturn(Optional.of(file));
        when(objectStorageClient.exists("primary-ns", "primary-bucket", "documents/56.pdf")).thenReturn(true);
        when(storageProperties.getDownloadUrlTtlSeconds()).thenReturn(30);
        when(storageProperties.getDownloadUrlMaxUses()).thenReturn(1);

        DocumentDownloadResponse response =
                documentStorageService.getAdminDocumentDownloadUrl(56L);

        assertThat(response.getDownloadUrl())
                .startsWith("/api/v1/documents/56/file?source=PRIMARY&token=");
    }

    @Test
    void rejectsOtherCompanyTemplate() {
        Document document = document(56L, "WB9999", "TEMPLATE");
        when(documentRepository.findByIdAndIsActiveTrue(56L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() ->
                documentStorageService.getAdminDocumentDownloadUrl(56L)
        )
                .isInstanceOfSatisfying(StorageException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("RESOURCE_004");
                });
    }

    @Test
    void rejectsNonTemplateDocument() {
        Document document = document(56L, null, "GUIDE");
        when(documentRepository.findByIdAndIsActiveTrue(56L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() ->
                documentStorageService.getAdminDocumentDownloadUrl(56L)
        )
                .isInstanceOfSatisfying(StorageException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("RESOURCE_004");
                });
    }

    @Test
    void listsCompanyDocumentsWithExtensionContentType() {
        Document document = document(101L, "WB0001", "GUIDE");
        lenient().when(document.getTitle()).thenReturn("온보딩 가이드");
        lenient().when(document.getDepartment()).thenReturn("HR");
        lenient().when(document.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 5, 20, 9, 30));

        DocumentFile file = mock(DocumentFile.class);
        when(file.getDocumentId()).thenReturn(101L);
        when(file.getOriginalFileName()).thenReturn("onboarding-guide.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getFileSize()).thenReturn(1048576L);
        when(file.getBackupStatus()).thenReturn(BackupStatus.COMPLETED);

        when(documentRepository.searchCompanyDocuments(eq("WB0001"), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(document)));
        when(documentFileRepository.findByDocumentIdIn(eq(List.of(101L))))
                .thenReturn(List.of(file));

        DocumentListResponse response =
                documentStorageService.listCompanyDocuments(0, 5, "COMPANY", null, null);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getDocumentType()).isEqualTo("GUIDE");
        assertThat(response.getContent().getFirst().getContentType()).isEqualTo("pdf");
    }

    @Test
    void rejectsDuplicatePolicyByCompanyAndTitle() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                "payload".getBytes()
        );
        when(storageProperties.getMaxDocumentSizeMb()).thenReturn(20);
        when(documentRepository.existsByCompanyCodeAndTitleAndIsActiveTrue(
                "WB0001",
                "취업규칙"
        )).thenReturn(true);

        assertThatThrownBy(() ->
                documentStorageService.uploadCompanyDocument(file, "취업규칙", "POLICY", "HR")
        )
                .isInstanceOfSatisfying(StorageException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("DOCUMENT_DUPLICATE");
                    assertThat(exception.getField()).isEqualTo("title");
                });

        verify(objectStorageClient, never()).putObject(anyString(), anyString(), anyString(), any());
    }

    @Test
    void rejectsDuplicateTemplateByCompanyTypeTitleAndContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "template.pdf",
                "application/pdf",
                "payload".getBytes()
        );
        when(storageProperties.getMaxDocumentSizeMb()).thenReturn(20);
        when(documentRepository.existsActiveTemplateDuplicate(
                "WB0001",
                "TEMPLATE",
                "근로계약서",
                "application/pdf"
        )).thenReturn(true);

        assertThatThrownBy(() ->
                documentStorageService.uploadCompanyDocument(file, "근로계약서", "TEMPLATE", "HR")
        )
                .isInstanceOfSatisfying(StorageException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("DOCUMENT_DUPLICATE");
                    assertThat(exception.getField()).isEqualTo("title");
                });

        verify(objectStorageClient, never()).putObject(anyString(), anyString(), anyString(), any());
    }

    private Document document(Long id, String companyCode, String documentType) {
        Document document = mock(Document.class);
        lenient().when(document.getId()).thenReturn(id);
        lenient().when(document.getCompanyCode()).thenReturn(companyCode);
        lenient().when(document.getDocumentType()).thenReturn(documentType);
        return document;
    }

    private DocumentFile documentFile(Long documentId) {
        DocumentFile file = mock(DocumentFile.class);
        when(file.getPrimaryNamespace()).thenReturn("primary-ns");
        when(file.getPrimaryBucket()).thenReturn("primary-bucket");
        when(file.getPrimaryObjectKey()).thenReturn("documents/" + documentId + ".pdf");
        return file;
    }
}
