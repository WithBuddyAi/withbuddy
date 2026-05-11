package com.withbuddy.storage.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class DocumentBulkDeleteRequest {

    @NotEmpty(message = "삭제할 documentIds를 1개 이상 전달해 주세요.")
    private List<@NotNull(message = "documentIds에는 null을 포함할 수 없습니다.") Long> documentIds;
}
