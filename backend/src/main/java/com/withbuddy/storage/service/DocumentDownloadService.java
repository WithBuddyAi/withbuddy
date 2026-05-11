package com.withbuddy.storage.service;

import com.withbuddy.storage.dto.response.DocumentDownloadResponse;

public interface DocumentDownloadService {
    DocumentDownloadResponse getDownloadUrl(Long documentId);
}

