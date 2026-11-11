package com.teacherhub.complaint.template.repository;

import com.teacherhub.complaint.template.entity.ResponseTemplateAdoption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResponseTemplateAdoptionRepository extends JpaRepository<ResponseTemplateAdoption, Long> {
    Optional<ResponseTemplateAdoption> findFirstByComplaintIdAndAdoptedByIdOrderByAdoptedAtDescIdDesc(
            Long complaintId,
            Long adoptedById
    );
}
