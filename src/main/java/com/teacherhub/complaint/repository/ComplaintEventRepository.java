package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComplaintEventRepository extends JpaRepository<ComplaintEvent, Long> {

    @EntityGraph(attributePaths = "actor")
    List<ComplaintEvent> findByComplaintIdOrderByOccurredAtDescIdDesc(Long complaintId);

    Optional<ComplaintEvent> findFirstByComplaintIdAndEventTypeAndNewStatusOrderByOccurredAtDescIdDesc(
            Long complaintId,
            MessageEventType eventType,
            ComplaintStatus newStatus
    );
}
