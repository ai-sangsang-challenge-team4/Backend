package com.teacherhub.complaint.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ComplaintReviewResponse {
    private String originalContent;

    private int riskyExpressionCount;

    private List<RiskyExpressionResponse> riskyExpressions;

    private String partialRevision;

    private String aiRevision;

}
