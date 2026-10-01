package com.teacherhub.risk.service;

import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.dto.*;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.*;
import lombok.RequiredArgsConstructor;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RiskApiService {
    private final ComplaintRepository complaints;
    private final PiiMaskingService masking;
    private final RiskAnalysisRepository analyses;
    private final ComplaintRiskTagRepository tags;

    @Transactional
    public MaskingResponse mask(Long userId, Long complaintId) {
        var complaint = complaints.findById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException( "민원을 찾을 수 없습니다."));
        checkOwner(userId, complaint.getParent().getUser().getId());
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new ComplaintNotDraftException( "작성 중인 민원만 마스킹할 수 있습니다.");
        }
        if (complaint.getContent() == null || complaint.getContent().isBlank()) {
            throw new EmptyComplaintContentException( "마스킹할 민원 내용이 없습니다.");
        }
        String masked = masking.mask(complaint.getContent());
        complaint.updateMaskedContent(masked);
        return new MaskingResponse(complaintId, complaint.getContentVersion(), masked);
    }

    public FinalRiskResult findAnalysis(Long userId, Long analysisId) {
        var analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new RiskAnalysisNotFoundException("위험 분석 결과가 없습니다."));
        checkOwner(userId, analysis.getComplaint().getParent().getUser().getId());
        var results = tags.findByRiskAnalysisId(analysisId).stream()
                .map(tag -> new FinalRiskTagResponse(tag.getRiskTag().getCode(), tag.isFinalDetected(),
                        tag.isRuleDetected(), tag.isLlmDetected(), tag.getConfidence(), tag.getEvidence()))
                .sorted(java.util.Comparator.comparing(FinalRiskTagResponse::code))
                .toList();
        return new FinalRiskResult(analysisId, analysis.getComplaint().getId(), results,
                analysis.getRiskScore(), analysis.getRiskLevel(), analysis.getAiReason());
    }

    private void checkOwner(Long userId, Long ownerId) {
        if (!ownerId.equals(userId)) {
            throw new ComplaintAccessDeniedException( "해당 민원에 접근할 권한이 없습니다.");
        }
    }
}
