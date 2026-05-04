package com.withbuddy.chat.repository;

import com.withbuddy.chat.entity.ChatMessageDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ChatMessageDocumentRepository extends JpaRepository<ChatMessageDocument, Long> {

    List<ChatMessageDocument> findByChatMessageIdIn(Collection<Long> chatMessageIds);

    List<ChatMessageDocument> findByChatMessageId(Long chatMessageId);

    boolean existsByChatMessageIdInAndDocumentId(Collection<Long> chatMessageIds, Long documentId);
}
