package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComplaintMessageRepository extends JpaRepository<ComplaintMessage, Long> {

    List<ComplaintMessage> findByComplaintIdAndStatusNotOrderByIdAsc(
            Long complaintId,
            MessageStatus status
    );

    List<ComplaintMessage> findByComplaintIdOrderByIdAsc(Long complaintId);

    @Query("""
            select m from ComplaintMessage m
            where m.complaint.id = :complaintId
              and m.status <> :excludedStatus
              and m.sentAt >= :cutoff
              and (:excludedMessageId is null or m.id <> :excludedMessageId)
            order by m.sentAt desc, m.id desc
            """)
    List<ComplaintMessage> findRiskAnalysisHistory(
            @Param("complaintId") Long complaintId,
            @Param("excludedStatus") MessageStatus excludedStatus,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("excludedMessageId") Long excludedMessageId
    );

    Optional<ComplaintMessage> findFirstByComplaintIdAndSenderIdAndTypeAndStatusOrderByIdDesc(
            Long complaintId,
            Long senderId,
            MessageType type,
            MessageStatus status
    );

    Optional<ComplaintMessage> findFirstByComplaintIdAndTypeAndStatusNotOrderByIdDesc(
            Long complaintId,
            MessageType type,
            MessageStatus status
    );

    Optional<ComplaintMessage> findBySenderIdAndIdempotencyKey(Long senderId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ComplaintMessage m where m.sender.id = :senderId and m.idempotencyKey = :idempotencyKey")
    Optional<ComplaintMessage> findIdempotentForUpdate(
            @Param("senderId") Long senderId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
