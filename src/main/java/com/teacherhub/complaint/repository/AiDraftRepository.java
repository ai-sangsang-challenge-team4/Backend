package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.AiDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AiDraftRepository extends JpaRepository<AiDraft, Long> {
    List<AiDraft> findByComplaintIdOrderByIdAsc(Long complaintId);

    Optional<AiDraft> findByIdAndComplaintId(Long id, Long complaintId);
}
