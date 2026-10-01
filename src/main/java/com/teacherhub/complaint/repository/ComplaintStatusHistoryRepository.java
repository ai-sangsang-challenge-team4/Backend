package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, Long> {
    List<ComplaintStatusHistory> findByComplaintIdOrderByIdAsc(Long complaintId);
}
