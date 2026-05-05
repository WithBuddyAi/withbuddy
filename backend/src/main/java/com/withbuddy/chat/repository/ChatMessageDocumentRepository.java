package com.withbuddy.chat.repository;

import com.withbuddy.chat.entity.ChatMessageDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ChatMessageDocumentRepository extends JpaRepository<ChatMessageDocument, Long> {

    List<ChatMessageDocument> findByChatMessageIdIn(Collection<Long> chatMessageIds);

    List<ChatMessageDocument> findByChatMessageId(Long chatMessageId);

    boolean existsByChatMessageIdInAndDocumentId(Collection<Long> chatMessageIds, Long documentId);

    @Query("""
            SELECT COUNT(cmd) > 0
            FROM ChatMessageDocument cmd
            WHERE cmd.documentId = :documentId
              AND cmd.chatMessageId IN (
                  SELECT cm.id FROM ChatMessage cm WHERE cm.userId = :userId
              )
            """)
    boolean existsByUserIdAndDocumentId(@Param("userId") Long userId, @Param("documentId") Long documentId);
}
