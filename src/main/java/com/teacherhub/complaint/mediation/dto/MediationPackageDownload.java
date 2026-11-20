package com.teacherhub.complaint.mediation.dto;

public record MediationPackageDownload(
        String filename,
        String checksumSha256,
        byte[] content
) {
}
