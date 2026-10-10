package com.teacherhub.complaint.query.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.query.dto.ComplaintDetailResponse;
import com.teacherhub.complaint.query.dto.ComplaintListResponse;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintQueryService {

    private final ComplaintRepository complaintRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;


    // 민원 목록 조회
    public List<ComplaintListResponse> findComplaints(Long userId) {
        User user = findUser(userId);

        List<Complaint> complaints = switch (user.getRole()) {
            case PARENT -> complaintRepository
                    .findByParentUserIdOrderByCreatedAtDesc(user.getId());
            case TEACHER -> complaintRepository
                    .findByTeacherUserIdOrderByCreatedAtDesc(user.getId());
            case ADMIN -> throw new ComplaintAccessDeniedException(
                    "관리자 민원 조회 기능은 아직 지원하지 않습니다."
            );
        };

        return complaints.stream()
                .map(complaint -> ComplaintListResponse.from(
                        complaint,
                        latestAnalysis(complaint)
                ))
                .toList();
    }

    // 민원 상세 조회
    public ComplaintDetailResponse findComplaint(
            Long userId,
            Long complaintId
    ) {
        User user = findUser(userId);
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException(
                        "민원을 찾을 수 없습니다."
                ));

        complaintAccessPolicy.validateCanRead(user, complaint);

        RiskAnalysis analysis = latestAnalysis(complaint);
        return ComplaintDetailResponse.from(
                complaint,
                analysis,
                user.getRole() == UserRole.PARENT
        );
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        "사용자를 찾을 수 없습니다."
                ));
    }

    private RiskAnalysis latestAnalysis(Complaint complaint) {
        return riskAnalysisRepository
                .findFirstByComplaintIdAndContentVersionAndCompletedTrueOrderByIdDesc(
                        complaint.getId(),
                        complaint.getContentVersion()
                )
                .orElse(null);
    }

}
