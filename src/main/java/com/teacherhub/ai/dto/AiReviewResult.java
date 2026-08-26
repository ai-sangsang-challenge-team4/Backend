package com.teacherhub.ai.dto;

import com.teacherhub.complaint.dto.RiskyExpressionResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiReviewResult {
    private List<RiskyExpressionResponse> riskyExpressions;

    private String partialRevision;

    private String aiRevision;
}
