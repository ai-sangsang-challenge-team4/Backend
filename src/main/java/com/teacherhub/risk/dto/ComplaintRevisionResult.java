package com.teacherhub.risk.dto;

public record ComplaintRevisionResult(
    Long complaintId,
    String revisionReason,
    String aiRevision
) {
}
