package com.teacherhub.complaint.dto;

import com.teacherhub.risk.dto.FinalRiskResult;
import com.teacherhub.risk.dto.ComplaintRevisionResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ComplaintReviewResponse {
    private String originalContent;

    private int riskyExpressionCount;

    private List<RiskyExpressionResponse> riskyExpressions;

    private ComplaintRevisionResult revision;

    private FinalRiskResult riskAnalysis;

}
