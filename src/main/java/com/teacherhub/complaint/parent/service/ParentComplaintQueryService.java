package com.teacherhub.complaint.parent.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.parent.dto.complaint.ParentComplaintDetailResponse;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentComplaintQueryService {

    private final ComplaintRepository complaintRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;

    public ParentComplaintDetailResponse findComplaint(Long userId, Long complaintId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateParentOwner(user, complaint);

        RiskAnalysis analysis = riskAnalysisRepository
                .findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(complaintId)
                .orElse(null);
        RiskReview latestReview = analysis == null
                ? null
                : riskReviewRepository
                        .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                        .orElse(null);
        return ParentComplaintDetailResponse.from(complaint, analysis, latestReview, true);
    }
}
