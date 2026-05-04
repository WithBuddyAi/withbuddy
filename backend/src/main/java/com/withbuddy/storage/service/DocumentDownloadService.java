package com.withbuddy.storage.service;

import com.withbuddy.storage.dto.DocumentDownloadResponse;
import com.withbuddy.storage.entity.Document;
import com.withbuddy.storage.entity.DocumentFile;

public interface DocumentDownloadService {
    DocumentDownloadResponse getDownloadUrl(Long documentId);
    DocumentDownloadResponse getDownloadUrl(Document document, DocumentFile file);
}

