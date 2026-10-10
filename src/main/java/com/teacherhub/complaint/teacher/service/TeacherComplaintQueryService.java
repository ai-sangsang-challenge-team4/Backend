package com.teacherhub.complaint.teacher.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintOriginalViewLog;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.ComplaintOriginalViewLogRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ResponseRepository;
import com.teacherhub.complaint.teacher.dto.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.TeacherOriginalViewResponse;
import com.teacherhub.complaint.teacher.dto.TeacherRiskResponse;
import com.teacherhub.complaint.teacher.policy.TeacherSupportPolicy;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherComplaintQueryService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintOriginalViewLogRepository originalViewLogRepository;
    private final ResponseRepository responseRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final ComplaintRiskTagRepository complaintRiskTagRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherSupportContentProvider supportContentProvider;
    private final TeacherSupportPolicy supportPolicy;

    public TeacherConversationResponse findConversation(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        RiskAnalysis analysis = latestAnalysis(complaint);

        return TeacherConversationResponse.from(
                complaint,
                supportContentProvider.createBufferedSummary(complaint.getContent()),
                supportPolicy.allowedActions(analysis),
                responseRepository.findByComplaintIdOrderByIdAsc(complaintId)
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
        RiskAnalysis analysis = latestAnalysis(complaint);

        if (analysis == null) {
            return TeacherRiskResponse.withoutAnalysis(complaintId);
        }

        return TeacherRiskResponse.from(
                analysis,
                complaintRiskTagRepository.findByRiskAnalysisId(analysis.getId())
        );
    }

    public TeacherHistoryResponse findHistory(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);

        return TeacherHistoryResponse.from(
                complaint.getId(),
                riskAnalysisRepository.findByComplaintIdOrderByIdAsc(complaintId),
                originalViewLogRepository.findByComplaintIdOrderByViewedAtDesc(complaintId)
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

    private RiskAnalysis latestAnalysis(Complaint complaint) {
        return riskAnalysisRepository
                .findFirstByComplaintIdAndContentVersionAndCompletedTrueOrderByIdDesc(
                        complaint.getId(),
                        complaint.getContentVersion()
                )
                .orElse(null);
    }
}
