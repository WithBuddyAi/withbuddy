package com.withbuddy.infrastructure.ai.dto;

import com.withbuddy.buddy.chat.entity.MessageType;
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

    @Schema(description = "근거 문서 목록")
    private List<DocumentRef> documents;

    @Schema(description = "메시지 타입", example = "rag_answer")
    private MessageType messageType;

    @Schema(description = "답변 내용", example = "복지카드 관련 안내 문서를 기준으로 요청드렸습니다.")
    private String content;

    @Schema(description = "추천 담당자 목록")
    private List<RecommendedContactRef> recommendedContacts;

    @Getter
    @NoArgsConstructor
    public static class DocumentRef {
        @Schema(description = "문서 ID", example = "1")
        private Long documentId;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "추천 담당자 정보")
    public static class RecommendedContactRef {

        @Schema(description = "담당 부서명", example = "경영지원팀")
        private String department;

        @Schema(description = "담당자 이름", example = "김지수")
        private String name;

        @Schema(description = "담당자 직급", example = "매니저")
        private String position;

        @Schema(description = "연락 수단 목록")
        private List<ContactMethodRef> connects;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "연락 수단 정보")
    public static class ContactMethodRef {

        @Schema(description = "연락 수단 유형", example = "email")
        private ContactType type;

        @Schema(description = "연락 값", example = "jisoo.kim@withbuddy.ai")
        private String value;

        public enum ContactType {
            SLACK,
            EMAIL,
            PHONE,
            EXTENSION
        }
    }
}
