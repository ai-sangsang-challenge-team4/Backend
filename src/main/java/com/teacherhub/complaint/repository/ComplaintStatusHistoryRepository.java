package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.ComplaintStatusHistory;
import com.teacherhub.complaint.entity.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, Long> {
    List<ComplaintStatusHistory> findByComplaintIdOrderByIdAsc(Long complaintId);

    Optional<ComplaintStatusHistory> findFirstByComplaintIdAndNewStatusOrderByChangedAtDesc(
            Long complaintId,
            ComplaintStatus newStatus
    );
}
