package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ComplaintMessageRepository extends JpaRepository<ComplaintMessage, Long> {

    List<ComplaintMessage> findByComplaintIdAndStatusNotOrderByIdAsc(
            Long complaintId,
            MessageStatus status
    );

    List<ComplaintMessage> findByComplaintIdOrderByIdAsc(Long complaintId);

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
