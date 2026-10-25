package com.teacherhub.complaint.teacher.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintOriginalViewLog;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.AiDraftRepository;
import com.teacherhub.complaint.repository.ComplaintOriginalViewLogRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ResponseRepository;
import com.teacherhub.complaint.repository.ComplaintStatusHistoryRepository;
import com.teacherhub.complaint.teacher.dto.query.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherOriginalViewResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.complaint.teacher.policy.TeacherSupportPolicy;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherComplaintQueryService {

    private final ComplaintRepository complaintRepository;
    private final AiDraftRepository aiDraftRepository;
    private final ComplaintOriginalViewLogRepository originalViewLogRepository;
    private final ComplaintStatusHistoryRepository statusHistoryRepository;
    private final ResponseRepository responseRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final ComplaintRiskTagRepository complaintRiskTagRepository;
    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherSupportContentProvider supportContentProvider;
    private final TeacherSupportPolicy supportPolicy;

    public TeacherComplaintDetailResponse findDetail(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        var schoolClass = studentClassRepository
                .findTopByStudent_IdOrderBySchoolClass_AcademicYearDesc(complaint.getStudent().getId())
                .orElseThrow(() -> new IllegalStateException("학생의 학급 정보를 찾을 수 없습니다."))
                .getSchoolClass();
        return TeacherComplaintDetailResponse.from(
                complaint,
                schoolClass,
                submittedAt(complaint),
                requiredAnalysis(complaint)
        );
    }

    public TeacherConversationResponse findConversation(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        RiskAnalysis analysis = requiredAnalysis(complaint);

        return TeacherConversationResponse.from(
                complaint,
                supportContentProvider.createBufferedSummary(complaint.getContent()),
                supportPolicy.allowedActions(analysis),
                submittedAt(complaint),
                responseRepository.findByComplaintIdAndSentAtIsNotNullOrderByIdAsc(complaintId)
        );
    }

    @Transactional
    public TeacherOriginalViewResponse viewOriginal(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        ComplaintOriginalViewLog viewLog = originalViewLogRepository.save(
                new ComplaintOriginalViewLog(complaint, user)
        );

        return new TeacherOriginalViewResponse(
                complaint.getId(),
                complaint.getContent(),
                viewLog.getViewedAt()
        );
    }

    public TeacherRiskResponse findRisk(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        RiskAnalysis analysis = requiredAnalysis(complaint);

        return TeacherRiskResponse.from(analysis, complaintRiskTagRepository.findByRiskAnalysisId(analysis.getId())
        );
    }

    public TeacherHistoryResponse findHistory(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);

        return TeacherHistoryResponse.from(
                complaint.getId(),
                statusHistoryRepository.findByComplaintIdOrderByIdAsc(complaintId),
                riskAnalysisRepository.findByComplaintIdOrderByIdAsc(complaintId),
                originalViewLogRepository.findByComplaintIdOrderByViewedAtDesc(complaintId),
                aiDraftRepository.findByComplaintIdOrderByIdAsc(complaintId),
                responseRepository.findByComplaintIdOrderByIdAsc(complaintId)
        );
    }

    private Complaint findTeacherComplaint(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        return complaint;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }

    private LocalDateTime submittedAt(Complaint complaint) {
        return statusHistoryRepository
                .findFirstByComplaintIdAndNewStatusOrderByChangedAtDesc(
                        complaint.getId(),
                        ComplaintStatus.ANALYZED
                )
                .map(history -> history.getChangedAt())
                .orElseThrow(() -> new IllegalStateException("민원 제출 이력을 찾을 수 없습니다."));
    }

    private RiskAnalysis requiredAnalysis(Complaint complaint) {
        return riskAnalysisRepository
                .findFirstByComplaintIdAndContentVersionAndCompletedTrueOrderByIdDesc(
                        complaint.getId(),
                        complaint.getContentVersion()
                )
                .orElseThrow(() -> new RiskAnalysisNotFoundException(
                        "완료된 위험 분석 결과를 찾을 수 없습니다."
                ));
    }
}
