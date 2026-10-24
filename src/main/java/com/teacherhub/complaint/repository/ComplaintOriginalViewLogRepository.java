package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.ComplaintOriginalViewLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintOriginalViewLogRepository
        extends JpaRepository<ComplaintOriginalViewLog, Long> {

    @EntityGraph(attributePaths = "viewedBy")
    List<ComplaintOriginalViewLog> findByComplaintIdOrderByViewedAtDesc(Long complaintId);
}
