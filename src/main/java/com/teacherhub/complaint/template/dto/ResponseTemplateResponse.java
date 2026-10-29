package com.teacherhub.complaint.template.dto;

import com.teacherhub.complaint.template.entity.ResponseTemplateVersion;

import java.util.List;

public record ResponseTemplateResponse(
        Long templateId,
        Long versionId,
        String code,
        String name,
        int version,
        String contentTemplate,
        List<String> requiredVariables
) {
    public static ResponseTemplateResponse from(ResponseTemplateVersion version, List<String> requiredVariables) {
        return new ResponseTemplateResponse(
                version.getTemplate().getId(),
                version.getId(),
                version.getTemplate().getCode(),
                version.getTemplate().getName(),
                version.getVersion(),
                version.getContentTemplate(),
                requiredVariables
        );
    }
}
