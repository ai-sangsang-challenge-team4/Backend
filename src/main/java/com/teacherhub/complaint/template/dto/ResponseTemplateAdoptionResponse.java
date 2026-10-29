package com.teacherhub.complaint.template.dto;

import com.teacherhub.complaint.template.entity.ResponseTemplateAdoption;

import java.time.LocalDateTime;

public record ResponseTemplateAdoptionResponse(
        Long adoptionId,
        Long complaintId,
        Long templateVersionId,
        String renderedContent,
        String finalContent,
        LocalDateTime adoptedAt
) {
    public static ResponseTemplateAdoptionResponse from(ResponseTemplateAdoption adoption) {
        return new ResponseTemplateAdoptionResponse(
                adoption.getId(),
                adoption.getComplaint().getId(),
                adoption.getTemplateVersion().getId(),
                adoption.getRenderedContent(),
                adoption.getFinalContent(),
                adoption.getAdoptedAt()
        );
    }
}
