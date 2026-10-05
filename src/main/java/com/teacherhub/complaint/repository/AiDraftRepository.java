package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.AiDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiDraftRepository extends JpaRepository<AiDraft, Long> {
    List<AiDraft> findByComplaintIdOrderByIdAsc(Long complaintId);
}
