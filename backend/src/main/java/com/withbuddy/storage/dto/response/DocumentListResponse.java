package com.withbuddy.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DocumentListResponse {
    private final List<DocumentListItemResponse> content;
    private final long totalElements;
    private final int totalPages;
    private final int size;
    private final int number;
}

