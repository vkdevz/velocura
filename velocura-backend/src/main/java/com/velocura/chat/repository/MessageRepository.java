package com.velocura.chat.repository;

import com.velocura.chat.entity.DeliveryStatus;
import com.velocura.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderBySentAtAsc(Long conversationId);
    Page<Message> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);
    long countByConversationIdAndDeliveryStatusNot(Long conversationId, DeliveryStatus status);
    List<Message> findByConversationIdAndDeliveryStatusNot(Long conversationId, DeliveryStatus status);
    long countByConversationIdAndDeliveryStatusNotAndSenderIdNot(Long conversationId, DeliveryStatus status, Long senderId);
}
