package com.teacherhub.complaint.query.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.query.dto.ComplaintDetailResponse;
import com.teacherhub.complaint.query.dto.ComplaintListResponse;
import com.teacherhub.complaint.query.dto.ComplaintListQuery;
import com.teacherhub.complaint.query.dto.ComplaintPageResponse;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.query.repository.ComplaintSearchQueryRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ComplaintQueryService {

    private static final String SUMMARY_UNAVAILABLE = "AI 요약을 생성하지 못했습니다.";

    private final ComplaintRepository complaintRepository;
    private final ComplaintSearchQueryRepository complaintSearchQueryRepository;
    private final ComplaintMessageRepository messageRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherSupportContentProvider supportContentProvider;
    private final PiiMaskingService piiMaskingService;


    // 민원 목록 조회
    public List<ComplaintListResponse> findComplaints(Long userId) {
        return findComplaints(userId, ComplaintListQuery.defaults()).content();
    }

    public ComplaintPageResponse findComplaints(Long userId, ComplaintListQuery query) {
        User user = findUser(userId);

        if (user.getRole() == UserRole.ADMIN) {
            throw new ComplaintAccessDeniedException(
                    "관리자 민원 목록 조회 기능은 아직 지원하지 않습니다."
            );
        }

        var complaints = complaintSearchQueryRepository.search(
                user.getId(),
                user.getRole(),
                query.normalizedKeyword(),
                query.status(),
                query.riskLevel(),
                query.resolvedPage(),
                ComplaintListQuery.PAGE_SIZE,
                query.resolvedSort()
        );

        List<ComplaintListResponse> content = complaints.getContent().stream()
                .map(complaint -> listResponse(complaint, user.getRole()))
                .toList();

        return new ComplaintPageResponse(
                content,
                complaints.getNumber(),
                complaints.getSize(),
                complaints.getTotalElements(),
                complaints.getTotalPages(),
                complaints.isFirst(),
                complaints.isLast(),
                complaints.hasNext(),
                complaints.hasPrevious()
        );
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

        complaintAccessPolicy.validateParentOwner(user, complaint);

        RiskAnalysis analysis = latestAnalysis(complaint);
        return ComplaintDetailResponse.from(
                complaint,
                analysis,
                latestReview(analysis),
                true
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
                .findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(complaint.getId())
                .orElse(null);
    }

    private ComplaintListResponse listResponse(Complaint complaint, UserRole viewerRole) {
        var schoolClass = studentClassRepository
                .findTopByStudent_IdOrderBySchoolClass_AcademicYearDesc(complaint.getStudent().getId())
                .map(studentClass -> studentClass.getSchoolClass())
                .orElse(null);
        var latestParentMessage = messageRepository
                .findFirstByComplaintIdAndTypeAndStatusNotOrderByIdDesc(
                        complaint.getId(),
                        MessageType.PARENT_INQUIRY,
                        MessageStatus.DRAFT
                );
        var latestReceivedAt = latestParentMessage
                .map(message -> message.getSentAt())
                .orElse(null);
        RiskAnalysis analysis = latestAnalysisForList(complaint, viewerRole);

        return ComplaintListResponse.from(
                complaint,
                analysis,
                latestReview(analysis),
                schoolClass,
                createListSummary(
                        complaint.getId(),
                        latestParentMessage
                                .map(message -> message.getContent())
                                .orElse(complaint.getContent())
                ),
                latestReceivedAt,
                viewerRole
        );
    }

    private String createListSummary(Long complaintId, String originalContent) {
        try {
            String maskedContent = piiMaskingService.mask(originalContent);
            if (maskedContent == null || maskedContent.isBlank()) {
                throw new IllegalStateException("마스킹 결과가 비어 있습니다.");
            }
            return supportContentProvider.createBufferedSummary(maskedContent);
        } catch (RuntimeException exception) {
            log.warn(
                    "Complaint list summary unavailable. complaintId={}, failureType={}",
                    complaintId,
                    exception.getClass().getSimpleName()
            );
            return SUMMARY_UNAVAILABLE;
        }
    }

    private RiskReview latestReview(RiskAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return riskReviewRepository
                .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                .orElse(null);
    }

    private RiskAnalysis latestAnalysisForList(Complaint complaint, UserRole viewerRole) {
        if (viewerRole == UserRole.TEACHER) {
            return riskAnalysisRepository
                    .findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                            complaint.getId(),
                            MessageStatus.DRAFT
                    )
                    .orElse(null);
        }
        return latestAnalysis(complaint);
    }

}
