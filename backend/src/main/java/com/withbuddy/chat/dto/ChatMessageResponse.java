package com.withbuddy.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "채팅 메시지 응답")
public class ChatMessageResponse {

    @Schema(description = "메시지 ID", example = "102")
    private Long id;

    @Schema(description = "온보딩 제안 ID", example = "1", nullable = true)
    private Long suggestionId;

    @Schema(description = "근거 문서 상세 목록")
    private List<DocumentResponse> documents;

    @Schema(description = "발신자 타입", example = "BOT")
    private String senderType;

    @Schema(description = "메시지 타입", example = "rag_answer")
    private String messageType;

    @Schema(description = "메시지 내용", example = "복지카드 신청은 안내 문서를 참고하고, 신청서는 바로 내려받아 작성할 수 있습니다.")
    private String content;

    @Schema(description = "생성 시각", example = "2026-03-24T10:00:02")
    private String createdAt;

    @Getter
    @AllArgsConstructor
    @Schema(description = "근거 문서 정보")
    public static class DocumentResponse {

        @Schema(description = "문서 ID", example = "11")
        private Long documentId;

        @Schema(description = "문서 제목", example = "복지카드 신청서")
        private String title;

        @Schema(description = "문서 타입", example = "TEMPLATE")
        private String documentType;

        @Schema(description = "파일 정보, TEMPLATE가 아니면 null")
        private FileResponse file;
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "문서 파일 정보")
    public static class FileResponse {

        @Schema(description = "파일명", example = "welfare-card-application.docx")
        private String fileName;

        @Schema(description = "콘텐츠 타입", example = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        private String contentType;

        @Schema(description = "다운로드 URL", example = "/api/v1/documents/11/download")
        private String downloadUrl;
    }
}
