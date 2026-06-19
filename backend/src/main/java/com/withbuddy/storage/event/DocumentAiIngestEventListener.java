package com.withbuddy.storage.event;

import com.withbuddy.infrastructure.ai.client.AiDocumentIngestClient;
import com.withbuddy.infrastructure.ai.client.AiDocumentIngestClient.AiDocumentIngestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class DocumentAiIngestEventListener {

    private final AiDocumentIngestClient aiDocumentIngestClient;

    public DocumentAiIngestEventListener(AiDocumentIngestClient aiDocumentIngestClient) {
        this.aiDocumentIngestClient = aiDocumentIngestClient;
    }

    @Async("aiCallExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void ingestAfterCommit(DocumentUploadedEvent event) {
        try {
            AiDocumentIngestResponse response = aiDocumentIngestClient.ingest(
                    event.documentId(),
                    event.companyCode()
            );
            log.info(
                    "AI document ingest completed. documentId={}, companyCode={}, success={}, chunksIndexed={}",
                    response.documentId(),
                    event.companyCode(),
                    response.success(),
                    response.chunksIndexed()
            );
        } catch (Exception e) {
            log.warn(
                    "AI document ingest failed. documentId={}, companyCode={}, reason={}",
                    event.documentId(),
                    event.companyCode(),
                    e.getMessage(),
                    e
            );
        }
    }
}
