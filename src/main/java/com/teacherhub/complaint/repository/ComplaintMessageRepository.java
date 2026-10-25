package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
