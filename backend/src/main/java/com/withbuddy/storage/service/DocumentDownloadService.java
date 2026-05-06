package com.withbuddy.storage.service;

import com.withbuddy.storage.dto.DocumentDownloadResponse;

public interface DocumentDownloadService {
    DocumentDownloadResponse getDownloadUrl(Long documentId);
}

