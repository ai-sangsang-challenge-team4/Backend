package com.teacherhub.complaint.teacher.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.teacher.dto.query.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherOriginalViewResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.complaint.teacher.policy.TeacherSupportPolicy;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherComplaintQueryService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository eventRepository;
    private final ComplaintMessageRepository messageRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final ComplaintRiskTagRepository complaintRiskTagRepository;
    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherSupportContentProvider supportContentProvider;
    private final TeacherSupportPolicy supportPolicy;
    private final TeacherRiskScale teacherRiskScale;

    public TeacherComplaintDetailResponse findDetail(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        var schoolClass = studentClassRepository
                .findTopByStudent_IdOrderBySchoolClass_AcademicYearDesc(complaint.getStudent().getId())
                .orElseThrow(() -> new IllegalStateException("학생의 학급 정보를 찾을 수 없습니다."))
                .getSchoolClass();
        RiskAnalysis analysis = requiredAnalysis(complaint);
        return TeacherComplaintDetailResponse.from(
                complaint,
                schoolClass,
                submittedAt(complaint),
                analysis,
                latestReview(analysis)
        );
    }

    public TeacherConversationResponse findConversation(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        RiskAnalysis analysis = requiredAnalysis(complaint);
        RiskReview latestReview = latestReview(analysis);
        List<ComplaintMessage> messages = messageRepository
                .findByComplaintIdAndStatusNotOrderByIdAsc(complaintId, MessageStatus.DRAFT);
        Map<Long, RiskLevel> latestRiskByMessage = latestRiskByMessage(complaintId);
        Map<Long, String> bufferedSummaries = messages.stream()
                .filter(message -> message.getType() == MessageType.PARENT_INQUIRY)
                .filter(message -> latestRiskByMessage.get(message.getId()) != null)
                .filter(message -> latestRiskByMessage.get(message.getId()) != RiskLevel.LOW)
                .collect(Collectors.toMap(
                        ComplaintMessage::getId,
                        message -> supportContentProvider.createBufferedSummary(message.getContent())
                ));

        return TeacherConversationResponse.from(
                complaint,
                bufferedSummaries,
                supportPolicy.allowedActions(
                        latestReview == null ? analysis.getRiskLevel() : latestReview.getReviewedLevel()
                ),
                submittedAt(complaint),
                messages
        );
    }

    @Transactional
    public TeacherOriginalViewResponse viewOriginal(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        RiskAnalysis analysis = requiredAnalysis(complaint);
        ComplaintEvent event = eventRepository.save(ComplaintEvent.originalViewed(
                complaint,
                user,
                analysis.getMessage().getId()
        ));

        return new TeacherOriginalViewResponse(
                complaint.getId(),
                analysis.getOriginalContent(),
                event.getOccurredAt()
        );
    }

    public TeacherRiskResponse findRisk(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        RiskAnalysis analysis = requiredAnalysis(complaint);

        return TeacherRiskResponse.from(
                analysis,
                complaintRiskTagRepository.findByRiskAnalysisId(analysis.getId()),
                latestReview(analysis),
                teacherRiskScale.initialScoreFor(analysis.getRiskLevel())
        );
    }

    public TeacherHistoryResponse findHistory(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);

        return TeacherHistoryResponse.from(
                complaint.getId(),
                eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(complaintId)
        );
    }

    private Complaint findTeacherComplaint(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        if (complaint.getStatus() == ComplaintStatus.DRAFT) {
            throw new ComplaintAccessDeniedException("제출되지 않은 민원은 교사가 조회할 수 없습니다.");
        }
        return complaint;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }

    private LocalDateTime submittedAt(Complaint complaint) {
        return eventRepository
                .findFirstByComplaintIdAndEventTypeAndNewStatusOrderByOccurredAtDescIdDesc(
                        complaint.getId(),
                        MessageEventType.STATUS_CHANGED,
                        ComplaintStatus.OPEN
                )
                .map(ComplaintEvent::getOccurredAt)
                .orElseThrow(() -> new IllegalStateException("민원 제출 이력을 찾을 수 없습니다."));
    }

    private RiskAnalysis requiredAnalysis(Complaint complaint) {
        return riskAnalysisRepository
                .findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                        complaint.getId(),
                        MessageStatus.DRAFT
                )
                .orElseThrow(() -> new RiskAnalysisNotFoundException(
                        "완료된 위험 분석 결과를 찾을 수 없습니다."
                ));
    }

    private RiskReview latestReview(RiskAnalysis analysis) {
        return riskReviewRepository
                .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                .orElse(null);
    }

    private Map<Long, RiskLevel> latestRiskByMessage(Long complaintId) {
        Map<Long, RiskLevel> result = new HashMap<>();
        riskAnalysisRepository
                .findByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdAsc(
                        complaintId,
                        MessageStatus.DRAFT
                ).stream()
                .forEach(analysis -> result.put(
                        analysis.getMessage().getId(),
                        analysis.getRiskLevel()
                ));
        return result;
    }
}
