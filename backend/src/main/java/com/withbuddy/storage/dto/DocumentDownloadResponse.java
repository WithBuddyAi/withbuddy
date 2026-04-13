package com.withbuddy.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentDownloadResponse {
    private final String downloadUrl;
    private final int expiresIn;
    private final String source;
}

