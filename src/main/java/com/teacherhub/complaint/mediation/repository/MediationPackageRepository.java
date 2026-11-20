package com.teacherhub.complaint.mediation.repository;

import com.teacherhub.complaint.mediation.entity.MediationPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediationPackageRepository extends JpaRepository<MediationPackage, Long> {

    Optional<MediationPackage> findByComplaintIdAndCreatedByIdAndIdempotencyKey(
            Long complaintId,
            Long createdById,
            String idempotencyKey
    );

    Optional<MediationPackage> findByPublicIdAndComplaintId(String publicId, Long complaintId);
}
