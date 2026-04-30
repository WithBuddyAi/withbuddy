package com.withbuddy.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "Chat message response")
public class ChatMessageResponse {

    @Schema(description = "Message ID", example = "102")
    private Long id;

    @Schema(description = "Onboarding suggestion ID", example = "1", nullable = true)
    private Long suggestionId;

    @Schema(description = "Referenced documents")
    private List<DocumentResponse> documents;

    @Schema(description = "Sender type", example = "BOT")
    private String senderType;

    @Schema(description = "Message type", example = "rag_answer")
    private String messageType;

    @Schema(description = "Message content")
    private String content;

    @Schema(description = "Quick tap buttons for suggestion messages")
    private List<QuickQuestionResponse> quickTaps;

    @Schema(description = "Recommended contacts for no_result messages")
    private List<RecommendedContactResponse> recommendedContacts;

    @Schema(description = "Created timestamp", example = "2026-03-24T10:00:02")
    private String createdAt;

    @Getter
    @AllArgsConstructor
    @Schema(description = "Document response")
    public static class DocumentResponse {

        @Schema(description = "Document ID", example = "11")
        private Long documentId;

        @Schema(description = "Document title")
        private String title;

        @Schema(description = "Document type", example = "TEMPLATE")
        private String documentType;

        @Schema(description = "File metadata, null when not downloadable")
        private FileResponse file;
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "Document file response")
    public static class FileResponse {

        @Schema(description = "Original filename", example = "welfare-card-application.docx")
        private String fileName;

        @Schema(description = "MIME content type", example = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        private String contentType;

        @Schema(description = "Download URL", example = "/api/v1/documents/11/download")
        private String downloadUrl;
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "Recommended contact")
    public static class RecommendedContactResponse {

        @Schema(description = "Department name")
        private String department;

        @Schema(description = "Contact name")
        private String name;

        @Schema(description = "Contact position")
        private String position;

        @Schema(description = "Contact methods")
        private List<ContactMethodResponse> connects;
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "Contact method")
    public static class ContactMethodResponse {

        @Schema(description = "Contact method type", example = "email")
        private String type;

        @Schema(description = "Contact method value", example = "jisoo.kim@withbuddy.ai")
        private String value;
    }
}
