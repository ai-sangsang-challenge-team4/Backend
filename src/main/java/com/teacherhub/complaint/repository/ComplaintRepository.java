package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByIdempotencyKey(String idempotencyKey);

}
