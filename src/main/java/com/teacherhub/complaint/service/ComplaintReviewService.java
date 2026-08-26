package com.teacherhub.complaint.service;

import com.teacherhub.ai.ComplaintAiAnalyzer;
import com.teacherhub.ai.dto.AiReviewResult;
import com.teacherhub.complaint.dto.ComplaintRequest;
import com.teacherhub.complaint.dto.ComplaintReviewResponse;
import com.teacherhub.privacy.PiiMaskingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 학부모 작성 -> AI 분석 -> AI 수정안 제공 (DB 저장 없음)
public class ComplaintReviewService {

    private final ComplaintAiAnalyzer complaintAiAnalyzer;
    private final PiiMaskingService piiMaskingService;

    public ComplaintReviewResponse review(ComplaintRequest request) {

        String originalContent = request.getContent();
        String maskedContent = piiMaskingService.mask(originalContent);

        AiReviewResult result = complaintAiAnalyzer.analyze(maskedContent);

        return new ComplaintReviewResponse(
                request.getContent(),
                result.getRiskyExpressions().size(),
                result.getRiskyExpressions(),
                result.getPartialRevision(),
                result.getAiRevision()
        );
    }
}


