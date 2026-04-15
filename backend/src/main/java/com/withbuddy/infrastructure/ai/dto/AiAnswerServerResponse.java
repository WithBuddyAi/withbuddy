package com.withbuddy.infrastructure.ai.dto;

import com.withbuddy.chat.entity.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "AI 서버 답변 응답")
public class AiAnswerServerResponse {

    @Schema(description = "질문 ID", example = "201")
    private Long questionId;

    @Schema(description = "메시지 타입", example = "rag_answer")
    private MessageType messageType;

    @Schema(description = "답변 내용", example = "복지카드 관련 안내 문서를 기준으로 요청드렸습니다.")
    private String content;

    @Schema(description = "근거 문서 목록")
    private List<DocumentRef> documents;

    @Getter
    @NoArgsConstructor
    public static class DocumentRef {
        @Schema(description = "문서 ID", example = "1")
        private Long documentId;
    }
}
