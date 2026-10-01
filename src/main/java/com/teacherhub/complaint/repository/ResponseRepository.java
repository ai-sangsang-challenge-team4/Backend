package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.Response;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResponseRepository extends JpaRepository<Response, Long> {
    List<Response> findByComplaintIdOrderByIdAsc(Long complaintId);
}
