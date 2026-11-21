package com.teacherhub.complaint.mediation.dto;

import java.time.LocalDateTime;

public record MediationPackageResponse(
        String packageId,
        Long complaintId,
        int schemaVersion,
        boolean originalMessagesIncluded,
        String checksumSha256,
        LocalDateTime createdAt,
        MediationPackageContent content
) {
}
