package com.teacherhub.complaint.history.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse.MissingLog;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintHistoryService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintMessageRepository messageRepository;
    private final ComplaintEventRepository eventRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final ComplaintEscalationRepository escalationRepository;

    public TeacherHistoryResponse findHistory(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        List<ComplaintEvent> events = eventRepository
                .findByComplaintIdOrderByOccurredAtDescIdDesc(complaintId);
        List<MissingLog> missingLogs = new ArrayList<>();

        checkStatusEvents(complaint, events, missingLogs);
        checkMessageEvents(complaintId, events, missingLogs);
        checkRiskEvents(complaintId, events, missingLogs);
        checkEscalationEvent(complaintId, events, missingLogs);

        return TeacherHistoryResponse.from(complaintId, events, missingLogs);
    }

    private void checkStatusEvents(
            Complaint complaint,
            List<ComplaintEvent> events,
            List<MissingLog> missingLogs
    ) {
        expectStatus(events, missingLogs, complaint.getId(), ComplaintStatus.DRAFT);
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            expectStatus(events, missingLogs, complaint.getId(), ComplaintStatus.OPEN);
        }
        if (complaint.getStatus() == ComplaintStatus.CLOSED) {
            expectStatus(events, missingLogs, complaint.getId(), ComplaintStatus.CLOSED);
        }
    }

    private void checkMessageEvents(
            Long complaintId,
            List<ComplaintEvent> events,
            List<MissingLog> missingLogs
    ) {
        messageRepository.findByComplaintIdOrderByIdAsc(complaintId).stream()
                .filter(message -> message.getStatus() != MessageStatus.DRAFT)
                .forEach(message -> {
                    expectReference(events, missingLogs, MessageEventType.MESSAGE_SENT,
                            "COMPLAINT_MESSAGE", message.getId(), "메시지 전송 로그가 누락되었습니다.");
                    if (message.getStatus() == MessageStatus.READ) {
                        expectReference(events, missingLogs, MessageEventType.MESSAGE_READ,
                                "COMPLAINT_MESSAGE", message.getId(), "메시지 읽음 로그가 누락되었습니다.");
                    }
                });
    }

    private void checkRiskEvents(
            Long complaintId,
            List<ComplaintEvent> events,
            List<MissingLog> missingLogs
    ) {
        riskAnalysisRepository.findByComplaintIdOrderByIdAsc(complaintId).stream()
                .filter(analysis -> analysis.isCompleted())
                .forEach(analysis -> expectReference(events, missingLogs,
                        MessageEventType.RISK_ANALYZED, "RISK_ANALYSIS", analysis.getId(),
                        "위험 분석 로그가 누락되었습니다."));
        riskReviewRepository.findByRiskAnalysisComplaintIdOrderByReviewedAtAscIdAsc(complaintId)
                .forEach(review -> expectReference(events, missingLogs,
                        MessageEventType.RISK_REVIEWED, "RISK_REVIEW", review.getId(),
                        "위험도 검토 로그가 누락되었습니다."));
    }

    private void checkEscalationEvent(
            Long complaintId,
            List<ComplaintEvent> events,
            List<MissingLog> missingLogs
    ) {
        escalationRepository.findByComplaintId(complaintId)
                .ifPresent(escalation -> {
                    expectReference(events, missingLogs,
                            MessageEventType.ESCALATION_SHARED, "COMPLAINT_ESCALATION", escalation.getId(),
                            "관리자 공유 로그가 누락되었습니다.");
                    if (escalation.getStatus() != EscalationStatus.SHARED) {
                        expectReference(events, missingLogs,
                                MessageEventType.ESCALATION_REVIEW_STARTED,
                                "COMPLAINT_ESCALATION", escalation.getId(),
                                "관리자 검토 시작 로그가 누락되었습니다.");
                    }
                    if (escalation.getStatus() == EscalationStatus.COMPLETED) {
                        expectReference(events, missingLogs,
                                MessageEventType.ESCALATION_COMPLETED,
                                "COMPLAINT_ESCALATION", escalation.getId(),
                                "관리자 처리 완료 로그가 누락되었습니다.");
                    }
                });
    }

    private void expectStatus(
            List<ComplaintEvent> events,
            List<MissingLog> missingLogs,
            Long complaintId,
            ComplaintStatus status
    ) {
        boolean exists = events.stream().anyMatch(event ->
                event.getEventType() == MessageEventType.STATUS_CHANGED
                        && event.getNewStatus() == status);
        if (!exists) {
            missingLogs.add(new MissingLog(
                    MessageEventType.STATUS_CHANGED,
                    "COMPLAINT",
                    complaintId,
                    status + " 상태 변경 로그가 누락되었습니다."
            ));
        }
    }

    private void expectReference(
            List<ComplaintEvent> events,
            List<MissingLog> missingLogs,
            MessageEventType expectedType,
            String referenceType,
            Long referenceId,
            String description
    ) {
        boolean exists = events.stream().anyMatch(event ->
                event.getEventType() == expectedType
                        && referenceType.equals(event.getReferenceType())
                        && Objects.equals(referenceId, event.getReferenceId()));
        if (!exists) {
            missingLogs.add(new MissingLog(expectedType, referenceType, referenceId, description));
        }
    }
}
