package com.teacherhub;

import com.teacherhub.complaint.enums.*;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintCreateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintDraftUpdateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintResponse;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintRecipientResponse;
import com.teacherhub.complaint.query.dto.ComplaintListQuery;
import com.teacherhub.complaint.query.dto.ComplaintListSort;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseRequest;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.repository.ComplaintRepository;

import com.teacherhub.risk.dto.response.RiskDetectorResponse;
import com.teacherhub.risk.masking.ComplaintMaskingService;
import com.teacherhub.risk.service.RiskAnalysisService;
import com.teacherhub.risk.service.RiskAnalysisQueryService;
import com.teacherhub.complaint.parent.service.ParentComplaintService;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.StudentClassRepository;

import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.ParentStudent;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;

import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.ParentRepository;
import com.teacherhub.user.repository.ParentStudentRepository;
import com.teacherhub.user.repository.StudentRepository;
import com.teacherhub.user.repository.TeacherRepository;
import com.teacherhub.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@Transactional
public class ComplaintFlowIntegrationTest {
    @Test
    void allowsUnlimitedDraftChangesAndPerformsFinalAnalysisOnlyOnSubmit() {
        Long id = parentComplaintService.createDraft(parentUser.getId(),
                createRequest("최초 원문")).getComplaintId();
        riskAnalysisService.analyze(parentUser.getId(), id);
        parentComplaintService.updateDraft(parentUser.getId(), id, updateRequest("첫 번째 수정"));
        parentComplaintService.updateDraft(parentUser.getId(), id, updateRequest("두 번째 수정"));
        parentComplaintService.updateDraft(parentUser.getId(), id, updateRequest("최종 제출 내용"));

        Complaint complaint = complaintRepository.findById(id).orElseThrow();
        assertThat(complaint.getContent()).isEqualTo("최종 제출 내용");
        assertThat(complaint.getContentVersion()).isEqualTo(4);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> riskAnalysisService.analyze(parentUser.getId(), id))
                .isInstanceOf(com.teacherhub.complaint.exception.FinalAnalysisOnSubmitException.class);

        parentComplaintService.submitComplaint(parentUser.getId(), id);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(id)).isEqualTo(2);
        var finalAnalysis = riskAnalysisRepository.findByComplaintIdOrderByIdAsc(id).get(1);
        assertThat(finalAnalysis.getContentVersion()).isEqualTo(4);
        assertThat(finalAnalysis.getContentHash()).hasSize(64);
        assertThat(finalAnalysis.getMessage().getContent()).isEqualTo("최종 제출 내용");
        assertThat(finalAnalysis.getAiRevision()).isNull();
        assertThat(complaintRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.OPEN);
    }
    @Test
    void reusesAnalysisWhenFinalContentMatchesReviewedContent() {
        Long id = parentComplaintService.createDraft(parentUser.getId(),
                createRequest("상황을 확인해 주세요.")).getComplaintId();
        assertThat(parentComplaintService.findStatus(parentUser.getId(), id).getStatus()).isEqualTo(ComplaintStatus.DRAFT);
        var first = riskAnalysisService.analyze(parentUser.getId(), id);
        parentComplaintService.updateDraft(parentUser.getId(), id, updateRequest("임시로 수정한 내용"));
        parentComplaintService.updateDraft(parentUser.getId(), id, updateRequest("상황을 확인해 주세요."));
        entityManager.flush();
        entityManager.clear();
        var reused = riskAnalysisService.analyze(parentUser.getId(), id);
        assertThat(reused).usingRecursiveComparison().isEqualTo(first);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(id)).isEqualTo(1);
        assertThat(complaintRepository.findById(id).orElseThrow().getContentVersion()).isEqualTo(3);
        assertThat(complaintRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.DRAFT);
        parentComplaintService.submitComplaint(parentUser.getId(), id);
        assertThat(parentComplaintService.findStatus(parentUser.getId(), id).getStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(complaintRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.OPEN);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parentComplaintService.submitComplaint(parentUser.getId(), id))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintNotDraftException.class);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(id)).isEqualTo(1);
        assertThat(eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(id))
                .filteredOn(event -> event.getEventType()
                        == MessageEventType.STATUS_CHANGED)
                .extracting(com.teacherhub.complaint.entity.ComplaintEvent::getNewStatus)
                .containsExactlyInAnyOrder(ComplaintStatus.DRAFT, ComplaintStatus.OPEN);
    }

    @Test
    void allowsSubmissionWithoutInitialReview() {
        Long id = parentComplaintService.createDraft(parentUser.getId(),
                createRequest("처음 내용")).getComplaintId();
        parentComplaintService.submitComplaint(parentUser.getId(), id);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(id)).isEqualTo(1);
        var finalAnalysis = riskAnalysisRepository.findByComplaintIdOrderByIdAsc(id).get(0);
        assertThat(finalAnalysis.getContentVersion()).isEqualTo(1);
        assertThat(finalAnalysis.getAiRevision()).isNull();
        assertThat(complaintRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.OPEN);
    }

    @Test
    void generatesAiDraftWithoutPersistenceThenSavesEditsAndSubmitsResponse() {
        Long complaintId = parentComplaintService.createDraft(
                parentUser.getId(),
                createRequest("상담 일정과 조치 내용을 확인해 주세요.")
        ).getComplaintId();
        riskAnalysisService.analyze(parentUser.getId(), complaintId);
        parentComplaintService.submitComplaint(parentUser.getId(), complaintId);

        var aiDraft = teacherResponseService.generateAiDraft(teacherUser.getId(), complaintId);
        assertThat(aiDraft.content()).contains("안녕하세요, 학부모님");

        var firstSave = teacherResponseService.saveDraft(
                teacherUser.getId(),
                complaintId,
                new TeacherResponseRequest(aiDraft.content())
        );
        var editedSave = teacherResponseService.saveDraft(
                teacherUser.getId(),
                complaintId,
                new TeacherResponseRequest("검토 후 수정한 답변입니다.")
        );

        assertThat(editedSave.responseDraftId()).isEqualTo(firstSave.responseDraftId());
        assertThat(teacherResponseService.findDraft(teacherUser.getId(), complaintId).content())
                .isEqualTo("검토 후 수정한 답변입니다.");

        var submitted = teacherResponseService.submit(
                teacherUser.getId(),
                complaintId,
                new TeacherResponseRequest("최종 답변입니다.")
        );

        assertThat(submitted.responseId()).isEqualTo(firstSave.responseDraftId());
        assertThat(submitted.complaintStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(messageRepository.findByComplaintIdAndStatusNotOrderByIdAsc(
                        complaintId,
                        MessageStatus.DRAFT
                ))
                .filteredOn(message -> message.getType()
                        == MessageType.TEACHER_REPLY)
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.getContent()).isEqualTo("최종 답변입니다.");
                    assertThat(response.getSentAt()).isNotNull();
                });
        assertThat(eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(complaintId))
                .filteredOn(event -> event.getEventType()
                        == MessageEventType.STATUS_CHANGED)
                .extracting(com.teacherhub.complaint.entity.ComplaintEvent::getNewStatus)
                .containsExactlyInAnyOrder(ComplaintStatus.DRAFT, ComplaintStatus.OPEN);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> teacherResponseService.findDraft(teacherUser.getId(), complaintId)
                )
                .isInstanceOf(com.teacherhub.complaint.exception.ResponseDraftNotFoundException.class);
    }

    @Test
    void submitsDirectResponseWithoutAPreviouslySavedDraft() {
        Long complaintId = parentComplaintService.createDraft(
                parentUser.getId(),
                createRequest("직접 답변할 민원입니다.")
        ).getComplaintId();
        riskAnalysisService.analyze(parentUser.getId(), complaintId);
        parentComplaintService.submitComplaint(parentUser.getId(), complaintId);

        var submitted = teacherResponseService.submit(
                teacherUser.getId(),
                complaintId,
                new TeacherResponseRequest("교사가 직접 작성한 답변입니다.")
        );

        assertThat(submitted.content()).isEqualTo("교사가 직접 작성한 답변입니다.");
        assertThat(submitted.complaintStatus()).isEqualTo(ComplaintStatus.OPEN);
    }

    @Test
    void showsLowRiskParentMessageWithoutBufferedSummary() {
        String content = "학교생활에 관해 확인하고 싶습니다.";
        Long complaintId = parentComplaintService.createDraft(
                parentUser.getId(),
                createRequest(content)
        ).getComplaintId();
        var summary = riskAnalysisService.analyze(parentUser.getId(), complaintId);
        var analysis = riskAnalysisRepository.findById(summary.analysisId()).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(analysis, "riskScore", 0);
        org.springframework.test.util.ReflectionTestUtils.setField(
                analysis,
                "riskLevel",
                com.teacherhub.risk.enums.RiskLevel.LOW
        );
        entityManager.flush();

        parentComplaintService.submitComplaint(parentUser.getId(), complaintId);
        var conversation = teacherComplaintQueryService.findConversation(
                teacherUser.getId(),
                complaintId
        );
        teacherComplaintQueryService.findConversation(teacherUser.getId(), complaintId);

        assertThat(conversation.messages()).singleElement().satisfies(message -> {
            assertThat(message.content()).isEqualTo(content);
            assertThat(message.status()).isEqualTo(MessageStatus.READ);
            assertThat(message.readAt()).isNotNull();
            assertThat(message.summarized()).isFalse();
        });
        assertThat(eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(complaintId))
                .filteredOn(event -> event.getEventType() == MessageEventType.MESSAGE_READ)
                .singleElement()
                .satisfies(event -> assertThat(event.getReferenceId())
                        .isEqualTo(complaintRepository.findById(complaintId).orElseThrow()
                                .initialMessage().getId()));
    }

    @Test
    void excludesUnsentParentDraftAnalysisFromEveryTeacherFacingQuery() {
        String submittedContent = "제출된 최초 민원입니다.";
        Long complaintId = parentComplaintService.createDraft(
                parentUser.getId(),
                createRequest(submittedContent)
        ).getComplaintId();
        var submittedSummary = riskAnalysisService.analyze(parentUser.getId(), complaintId);
        var submittedAnalysis = riskAnalysisRepository.findById(submittedSummary.analysisId()).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(submittedAnalysis, "riskScore", 0);
        org.springframework.test.util.ReflectionTestUtils.setField(
                submittedAnalysis,
                "riskLevel",
                com.teacherhub.risk.enums.RiskLevel.LOW
        );
        entityManager.flush();

        parentComplaintService.submitComplaint(parentUser.getId(), complaintId);
        teacherResponseService.submit(
                teacherUser.getId(),
                complaintId,
                new TeacherResponseRequest("학부모님의 민원을 확인했습니다.")
        );

        var draft = parentMessageService.saveDraft(
                parentUser.getId(),
                complaintId,
                new com.teacherhub.complaint.parent.dto.message.ParentMessageRequest(
                        "교사에게 아직 전송하지 않은 추가 문의입니다."
                )
        );
        riskAnalysisService.analyzeMessage(
                parentUser.getId(),
                complaintId,
                draft.messageId()
        );

        var detail = teacherComplaintQueryService.findDetail(teacherUser.getId(), complaintId);
        var risk = teacherComplaintQueryService.findRisk(teacherUser.getId(), complaintId);
        var original = teacherComplaintQueryService.viewOriginal(
                teacherUser.getId(),
                complaintId,
                complaintRepository.findById(complaintId).orElseThrow().initialMessage().getId()
        );
        var conversation = teacherComplaintQueryService.findConversation(teacherUser.getId(), complaintId);
        var listItem = complaintQueryService.findComplaints(teacherUser.getId()).stream()
                .filter(item -> item.complaintId().equals(complaintId))
                .findFirst()
                .orElseThrow();

        assertThat(detail.riskLevel()).isEqualTo(com.teacherhub.risk.enums.RiskLevel.LOW);
        assertThat(risk.riskAnalysisId()).isEqualTo(submittedSummary.analysisId());
        assertThat(original.content()).isEqualTo(submittedContent);
        assertThat(conversation.allowedActions()).isEmpty();
        assertThat(listItem.riskAnalysisId()).isEqualTo(submittedSummary.analysisId());
        assertThat(listItem.riskLevel()).isEqualTo(com.teacherhub.risk.enums.RiskLevel.LOW);
    }

    @Test
    void teacherReviewsSevenPointRiskAndHistoryKeepsEveryAdjustment() {
        Long complaintId = parentComplaintService.createDraft(
                parentUser.getId(),
                createRequest("계속 이러면 학교에 책임을 묻겠습니다.")
        ).getComplaintId();
        var systemAnalysis = riskAnalysisService.analyze(parentUser.getId(), complaintId);
        parentComplaintService.submitComplaint(parentUser.getId(), complaintId);

        assertThat(systemAnalysis.riskScore()).isEqualTo(60);
        assertThat(systemAnalysis.riskLevel())
                .isEqualTo(com.teacherhub.risk.enums.RiskLevel.HIGH);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        teacherRiskReviewService.review(
                                teacherUser.getId(),
                                complaintId,
                                new com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest(5, " ")
                        )
                )
                .isInstanceOf(com.teacherhub.risk.exception.RiskReviewReasonRequiredException.class);

        var lowered = teacherRiskReviewService.review(
                teacherUser.getId(),
                complaintId,
                new com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest(
                        5,
                        "표현은 강하지만 즉각적인 위해 가능성은 낮다고 판단했습니다."
                )
        );
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        teacherRiskReviewService.review(
                                teacherUser.getId(),
                                complaintId,
                                new com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest(6, null)
                        )
                )
                .isInstanceOf(com.teacherhub.risk.exception.RiskReviewReasonRequiredException.class);
        var raised = teacherRiskReviewService.review(
                teacherUser.getId(),
                complaintId,
                new com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest(
                        6,
                        "새로운 위험 정황이 확인되어 상향합니다."
                )
        );

        assertThat(lowered.previousScore()).isEqualTo(6);
        assertThat(lowered.direction())
                .isEqualTo(com.teacherhub.risk.enums.RiskReviewDirection.LOWERED);
        assertThat(lowered.riskLevel()).isEqualTo(com.teacherhub.risk.enums.RiskLevel.HIGH);
        assertThat(raised.previousScore()).isEqualTo(5);
        assertThat(raised.direction())
                .isEqualTo(com.teacherhub.risk.enums.RiskReviewDirection.RAISED);
        assertThat(raised.reason()).isEqualTo("새로운 위험 정황이 확인되어 상향합니다.");

        var currentRisk = teacherComplaintQueryService.findRisk(teacherUser.getId(), complaintId);
        assertThat(currentRisk.riskScore()).isEqualTo(60);
        assertThat(currentRisk.riskLevel()).isEqualTo(com.teacherhub.risk.enums.RiskLevel.HIGH);
        assertThat(currentRisk.reviewScore()).isEqualTo(6);
        assertThat(currentRisk.reviewLevel()).isEqualTo(com.teacherhub.risk.enums.RiskLevel.HIGH);
        assertThat(currentRisk.latestReview().direction())
                .isEqualTo(com.teacherhub.risk.enums.RiskReviewDirection.RAISED);

        var reviewEvents = teacherComplaintQueryService
                .findHistory(teacherUser.getId(), complaintId)
                .events().stream()
                .filter(event -> event.type()
                        == MessageEventType.RISK_REVIEWED)
                .toList();
        assertThat(teacherComplaintQueryService.findHistory(teacherUser.getId(), complaintId).logComplete())
                .isTrue();
        assertThat(reviewEvents).hasSize(2);
        assertThat(reviewEvents)
                .extracting(TeacherHistoryResponse.HistoryEvent::previousRiskScore)
                .containsExactlyInAnyOrder(6, 5);
        assertThat(reviewEvents)
                .extracting(TeacherHistoryResponse.HistoryEvent::riskScore)
                .containsExactlyInAnyOrder(5, 6);
        assertThat(riskReviewRepository.findByRiskAnalysisComplaintIdOrderByReviewedAtAscIdAsc(complaintId))
                .hasSize(2);
    }

    @Autowired
    private ComplaintMaskingService maskingService;

    @Test
    void masksAndRetrievesAnalysisWithOwnershipChecks() {
        var created = parentComplaintService.createDraft(parentUser.getId(),
                createRequest("전화 010-1234-5678로 연락 부탁드립니다."));
        Long id = created.getComplaintId();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parentComplaintService.findStatus(teacherUser.getId(), id))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parentComplaintService.findStatus(parentUser.getId(), Long.MAX_VALUE))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintNotFoundException.class);
        var masked = maskingService.mask(parentUser.getId(), id);
        assertThat(masked.maskedContent()).isEqualTo("전화 [전화번호]로 연락 부탁드립니다.");
        assertThat(complaintRepository.findById(id).orElseThrow().getContent()).contains("010-1234-5678");
        var review = riskAnalysisService.analyze(parentUser.getId(), id);
        assertThat(review.analysisId()).isNotNull();
        assertThat(riskAnalysisQueryService.findFindings(parentUser.getId(), review.analysisId()))
                .isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> riskAnalysisQueryService.findFindings(
                teacherUser.getId(), review.analysisId()))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> maskingService.mask(teacherUser.getId(), id))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> riskAnalysisQueryService.findFindings(parentUser.getId(), Long.MAX_VALUE))
                .isInstanceOf(com.teacherhub.risk.exception.RiskAnalysisNotFoundException.class);
        parentComplaintService.updateDraft(parentUser.getId(), id, updateRequest("새 내용"));
        assertThat(complaintRepository.findById(id).orElseThrow().getMaskedContent()).isNull();
    }

    @Test
    void persistsTagSuggestionsAndSeparatesSourceEvidence() {
        Long complaintId = parentComplaintService.createDraft(parentUser.getId(),
                createRequest("확인을 요청드립니다.")).getComplaintId();
        var review = riskAnalysisService.analyze(parentUser.getId(), complaintId);
        Long analysisId = review.analysisId();
        entityManager.flush();
        entityManager.clear();

        var findings = riskAnalysisQueryService.findFindings(parentUser.getId(), analysisId);
        assertThat(findings.findings()).hasSize(3).allSatisfy(finding -> {
            assertThat(finding.evidences()).isNotEmpty();
            assertThat(finding.revisionSuggestions()).hasSize(1);
            assertThat(finding.revisionSuggestions().get(0).suggestedExpression()).isNotBlank();
        });
        assertThat(findings.finalRevision().content()).isEqualTo(
                riskAnalysisRepository.findById(analysisId).orElseThrow().getAiRevision());
        assertThat(riskAnalysisQueryService.findFindings(parentUser.getId(), analysisId)).isEqualTo(findings);
        var detail = riskAnalysisQueryService.findDetectorResults(parentUser.getId(), analysisId);
        assertThat(detail.results()).hasSize(com.teacherhub.risk.enums.RiskTagCode.values().length);
        var threat = detail.results().stream()
                .filter(tag -> tag.code() == com.teacherhub.risk.enums.RiskTagCode.THREAT).findFirst().orElseThrow();
        assertThat(threat.rule().detected()).isTrue();
        assertThat(threat.llm().detected()).isTrue();
        assertThat(threat.rule().evidences()).containsExactly("[MOCK RULE] 협박 예시");
        assertThat(threat.llm().evidences()).containsExactly("[MOCK LLM] 협박 문맥 예시");
        assertThat(threat.llm().confidence()).isEqualTo(0.9);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> riskAnalysisQueryService.findFindings(teacherUser.getId(), analysisId))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> riskAnalysisQueryService.findDetectorResults(teacherUser.getId(), analysisId))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(complaintId)).isEqualTo(1);
    }

    @Autowired
    private com.teacherhub.risk.repository.RiskAnalysisRepository riskAnalysisRepository;
    @Autowired
    private com.teacherhub.risk.repository.ComplaintRiskTagRepository complaintRiskTagRepository;
    @Autowired
    private com.teacherhub.complaint.repository.ComplaintEventRepository eventRepository;
    @Autowired
    private com.teacherhub.complaint.repository.ComplaintMessageRepository messageRepository;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private ParentComplaintService parentComplaintService;

    @Autowired
    private com.teacherhub.complaint.parent.service.ParentMessageService parentMessageService;

    @Autowired
    private RiskAnalysisService riskAnalysisService;

    @Autowired
    private RiskAnalysisQueryService riskAnalysisQueryService;

    @Autowired
    private com.teacherhub.complaint.teacher.service.TeacherComplaintQueryService teacherComplaintQueryService;

    @Autowired
    private com.teacherhub.complaint.teacher.service.TeacherResponseService teacherResponseService;

    @Autowired
    private com.teacherhub.complaint.teacher.service.TeacherRiskReviewService teacherRiskReviewService;

    @Autowired
    private com.teacherhub.risk.repository.RiskReviewRepository riskReviewRepository;

    @Autowired
    private com.teacherhub.complaint.query.service.ComplaintQueryService complaintQueryService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private StudentClassRepository studentClassRepository;


    private User parentUser;
    private Parent parent;

    private User teacherUser;
    private Teacher teacher;

    private Student student;

    @Test
    void teacherClosesConversationAndParentFollowUpReopensIt() {
        Long complaintId = parentComplaintService.createDraft(
                parentUser.getId(),
                createRequest("첫 번째 문의입니다.")
        ).getComplaintId();
        riskAnalysisService.analyze(parentUser.getId(), complaintId);
        parentComplaintService.submitComplaint(parentUser.getId(), complaintId);

        teacherResponseService.submit(
                teacherUser.getId(),
                complaintId,
                new TeacherResponseRequest("첫 번째 교사 답변입니다.")
        );

        var firstDraft = parentMessageService.saveDraft(
                parentUser.getId(),
                complaintId,
                new com.teacherhub.complaint.parent.dto.message.ParentMessageRequest(
                        "추가로 확인하고 싶은 내용입니다."
                )
        );
        var updatedDraft = parentMessageService.saveDraft(
                parentUser.getId(),
                complaintId,
                new com.teacherhub.complaint.parent.dto.message.ParentMessageRequest(
                        "수정한 추가 문의입니다."
                )
        );

        assertThat(updatedDraft.messageId()).isEqualTo(firstDraft.messageId());
        assertThat(updatedDraft.contentVersion()).isEqualTo(2);
        riskAnalysisService.analyzeMessage(
                parentUser.getId(),
                complaintId,
                updatedDraft.messageId()
        );
        parentMessageService.submit(parentUser.getId(), complaintId, updatedDraft.messageId());
        var additionalOriginal = teacherComplaintQueryService.viewOriginal(
                teacherUser.getId(), complaintId, updatedDraft.messageId());
        assertThat(additionalOriginal.messageId()).isEqualTo(updatedDraft.messageId());
        assertThat(additionalOriginal.content()).isEqualTo("수정한 추가 문의입니다.");

        var waitingForTeacher = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(waitingForTeacher.getStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(waitingForTeacher.getWaitingFor())
                .isEqualTo(WaitingFor.TEACHER);

        var aiDraft = teacherResponseService.generateAiDraft(teacherUser.getId(), complaintId);
        teacherResponseService.submit(
                teacherUser.getId(),
                complaintId,
                new TeacherResponseRequest("추가 문의에 대한 답변입니다.")
        );

        var conversation = parentMessageService.findConversation(parentUser.getId(), complaintId);
        parentMessageService.findConversation(parentUser.getId(), complaintId);
        assertThat(conversation.messages())
                .hasSize(4)
                .extracting(com.teacherhub.complaint.parent.dto.message.ComplaintConversationResponse.Message::content)
                .containsExactly(
                        "첫 번째 문의입니다.",
                        "첫 번째 교사 답변입니다.",
                        "수정한 추가 문의입니다.",
                        "추가 문의에 대한 답변입니다."
                );
        assertThat(conversation.messages().get(3).status()).isEqualTo(MessageStatus.READ);
        assertThat(conversation.messages().get(3).readAt()).isNotNull();
        assertThat(eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(complaintId))
                .filteredOn(event -> event.getEventType() == MessageEventType.MESSAGE_READ)
                .filteredOn(event -> event.getReferenceId().equals(conversation.messages().get(3).messageId()))
                .hasSize(1);

        var teacherConversation = teacherComplaintQueryService.findConversation(
                teacherUser.getId(),
                complaintId
        );
        assertThat(teacherConversation.messages())
                .filteredOn(message -> message.senderRole() == UserRole.PARENT)
                .hasSize(2)
                .allMatch(TeacherConversationResponse.Message::summarized);

        teacherResponseService.close(teacherUser.getId(), complaintId);
        var closed = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(ComplaintStatus.CLOSED);
        assertThat(closed.getWaitingFor())
                .isEqualTo(WaitingFor.NONE);
        assertThat(messageRepository.findByComplaintIdOrderByIdAsc(complaintId))
                .allMatch(message -> message.getStatus()
                        == MessageStatus.READ);

        var reopenedDraft = parentMessageService.saveDraft(
                parentUser.getId(),
                complaintId,
                new com.teacherhub.complaint.parent.dto.message.ParentMessageRequest(
                        "완료된 문의에 같은 사안으로 추가 질문합니다."
                )
        );
        parentMessageService.submit(parentUser.getId(), complaintId, reopenedDraft.messageId());

        var reopened = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(reopened.getWaitingFor()).isEqualTo(WaitingFor.TEACHER);
        assertThat(eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(complaintId))
                .filteredOn(event -> event.getEventType() == MessageEventType.STATUS_CHANGED)
                .extracting(com.teacherhub.complaint.entity.ComplaintEvent::getNewStatus)
                .contains(ComplaintStatus.CLOSED, ComplaintStatus.OPEN);
    }

    @Test
    void selectsOneOfMultipleAssignedTeachersAsTheComplaintRecipient() {
        User subjectTeacherUser = userRepository.save(new User(
                "subject-teacher@test.com",
                "password",
                "교과 담당 교사",
                UserRole.TEACHER
        ));
        Teacher subjectTeacher = teacherRepository.save(new Teacher(subjectTeacherUser, "T-2026-002"));
        SchoolClass schoolClass = schoolClassRepository.findAll().get(0);
        schoolClass.assignTeacher(subjectTeacher);
        schoolClassRepository.saveAndFlush(schoolClass);

        var recipients = parentComplaintService.findRecipients(parentUser.getId(), student.getId());
        assertThat(recipients)
                .extracting(ComplaintRecipientResponse::teacherId)
                .containsExactlyInAnyOrder(teacher.getId(), subjectTeacher.getId());
        assertThat(recipients)
                .filteredOn(ComplaintRecipientResponse::homeroomTeacher)
                .extracting(ComplaintRecipientResponse::teacherId)
                .containsExactly(teacher.getId());

        Long complaintId = parentComplaintService.createDraft(
                parentUser.getId(),
                new ComplaintCreateRequest(
                        student.getId(),
                        subjectTeacher.getId(),
                        "교과 수업 문의",
                        "교과 수업 일정을 확인해 주세요."
                )
        ).getComplaintId();

        Complaint draft = complaintRepository.findById(complaintId).orElseThrow();
        assertThat(draft.getTitle()).isEqualTo("교과 수업 문의");
        assertThat(draft.getTeacher().getId()).isEqualTo(subjectTeacher.getId());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> teacherComplaintQueryService.findDetail(subjectTeacherUser.getId(), complaintId)
                )
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);

        riskAnalysisService.analyze(parentUser.getId(), complaintId);
        parentComplaintService.submitComplaint(parentUser.getId(), complaintId);

        assertThat(teacherComplaintQueryService.findDetail(subjectTeacherUser.getId(), complaintId).title())
                .isEqualTo("교과 수업 문의");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> teacherComplaintQueryService.findDetail(teacherUser.getId(), complaintId)
                )
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);
    }

    @Test
    void returnsNoRecipientsWhenLinkedStudentHasNoClassAndRejectsUnknownStudent() {
        Student unassignedStudent = studentRepository.save(new Student("학급 미배정 학생", "20269999"));
        parentStudentRepository.save(new ParentStudent(
                parent,
                unassignedStudent
        ));

        assertThat(parentComplaintService.findRecipients(parentUser.getId(), unassignedStudent.getId()))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> parentComplaintService.createDraft(
                                parentUser.getId(),
                                new ComplaintCreateRequest(
                                        unassignedStudent.getId(),
                                        teacher.getId(),
                                        "학급 미배정 문의",
                                        "문의 내용"
                                )
                        )
                )
                .isInstanceOf(com.teacherhub.complaint.exception.InvalidComplaintRecipientException.class)
                .hasMessage("학생의 학급 정보를 찾을 수 없습니다.");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> parentComplaintService.findRecipients(parentUser.getId(), Long.MAX_VALUE)
                )
                .isInstanceOf(com.teacherhub.user.exception.StudentNotFoundException.class)
                .hasMessage("학생 정보를 찾을 수 없습니다.");
    }

    @Test
    void searchesFiltersSortsAndPaginatesComplaintListsByTen() {
        java.util.List<Long> complaintIds = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Long complaintId = parentComplaintService.createDraft(
                    parentUser.getId(),
                    new ComplaintCreateRequest(
                            student.getId(),
                            teacher.getId(),
                            "검색 제목 " + index,
                            index == 5 ? "독서 일정을 알고 싶습니다." : "민원 내용 " + index
                    )
            ).getComplaintId();
            complaintIds.add(complaintId);
        }

        var firstPage = complaintQueryService.findComplaints(
                parentUser.getId(),
                new ComplaintListQuery(0, "검색 제목", ComplaintStatus.DRAFT, null, ComplaintListSort.LATEST)
        );
        var secondPage = complaintQueryService.findComplaints(
                parentUser.getId(),
                new ComplaintListQuery(1, "검색 제목", ComplaintStatus.DRAFT, null, ComplaintListSort.LATEST)
        );

        assertThat(firstPage.content()).hasSize(10);
        assertThat(firstPage.totalElements()).isEqualTo(12);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.content()).hasSize(2);
        assertThat(secondPage.last()).isTrue();
        assertThat(firstPage.content().get(0).complaintId()).isEqualTo(complaintIds.get(11));

        var oldest = complaintQueryService.findComplaints(
                parentUser.getId(),
                new ComplaintListQuery(0, "검색 제목", ComplaintStatus.DRAFT, null, ComplaintListSort.OLDEST)
        );
        assertThat(oldest.content().get(0).complaintId()).isEqualTo(complaintIds.get(0));

        var contentSearch = complaintQueryService.findComplaints(
                parentUser.getId(),
                new ComplaintListQuery(0, "독서 일정", null, null, null)
        );
        assertThat(contentSearch.content())
                .singleElement()
                .satisfies(item -> assertThat(item.complaintId()).isEqualTo(complaintIds.get(5)));

        Long submittedId = complaintIds.get(0);
        var analyzed = riskAnalysisService.analyze(parentUser.getId(), submittedId);
        var analysis = riskAnalysisRepository.findById(analyzed.analysisId()).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(analysis, "riskScore", 0);
        org.springframework.test.util.ReflectionTestUtils.setField(
                analysis,
                "riskLevel",
                com.teacherhub.risk.enums.RiskLevel.LOW
        );
        entityManager.flush();
        parentComplaintService.submitComplaint(parentUser.getId(), submittedId);

        var filtered = complaintQueryService.findComplaints(
                parentUser.getId(),
                new ComplaintListQuery(
                        0,
                        teacherUser.getName(),
                        ComplaintStatus.OPEN,
                        com.teacherhub.risk.enums.RiskLevel.LOW,
                        ComplaintListSort.LATEST
                )
        );
        assertThat(filtered.content())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.complaintId()).isEqualTo(submittedId);
                    assertThat(item.title()).isEqualTo("검색 제목 0");
                    assertThat(item.riskLevel()).isEqualTo(com.teacherhub.risk.enums.RiskLevel.LOW);
                });

        teacherRiskReviewService.review(
                teacherUser.getId(),
                submittedId,
                new com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest(5, "상향 조정")
        );
        var reviewedRisk = complaintQueryService.findComplaints(
                teacherUser.getId(),
                new ComplaintListQuery(
                        0,
                        null,
                        ComplaintStatus.OPEN,
                        com.teacherhub.risk.enums.RiskLevel.HIGH,
                        ComplaintListSort.LATEST
                )
        );
        assertThat(reviewedRisk.content())
                .singleElement()
                .satisfies(item -> assertThat(item.complaintId()).isEqualTo(submittedId));
        assertThat(complaintQueryService.findComplaints(
                teacherUser.getId(),
                new ComplaintListQuery(
                        0,
                        null,
                        ComplaintStatus.OPEN,
                        com.teacherhub.risk.enums.RiskLevel.LOW,
                        ComplaintListSort.LATEST
                )
        ).content()).isEmpty();

        var teacherPage = complaintQueryService.findComplaints(
                teacherUser.getId(),
                new ComplaintListQuery(0, null, null, null, null)
        );
        assertThat(teacherPage.content())
                .singleElement()
                .satisfies(item -> assertThat(item.complaintId()).isEqualTo(submittedId));
    }


    @BeforeEach
    void setUp() {

        // 학부모 계정
        parentUser = new User(
                "parent@test.com",
                "password",
                "홍길동 학부모",
                UserRole.PARENT
        );
        userRepository.save(parentUser);


        // 학부모 엔티티
        parent = new Parent(parentUser);
        parentRepository.save(parent);


        // 교사 계정
        teacherUser = new User(
                "teacher@test.com",
                "password",
                "김선생",
                UserRole.TEACHER
        );
        userRepository.save(teacherUser);


        // 교사 엔티티
        teacher = new Teacher(
                teacherUser,
                "T-2026-001"
        );
        teacherRepository.save(teacher);


        // 학급
        SchoolClass schoolClass = new SchoolClass(
                2026,
                3,
                2,
                teacher
        );
        schoolClassRepository.save(schoolClass);


        // 학생
        student = new Student(
                "홍길동",
                "20260001"
        );
        studentRepository.save(student);


        // 학부모 ↔ 학생
        ParentStudent parentStudent =
                new ParentStudent(
                        parent,
                        student
                );

        parentStudentRepository.save(parentStudent);


        // 학생 ↔ 학급
        StudentClass studentClass =
                new StudentClass(
                        student,
                        schoolClass
                );

        studentClassRepository.save(studentClass);
    }


    @Test
    public void complaintTest() {

        // =========================
        // 1. 민원 작성
        // =========================

        ComplaintCreateRequest createRequest =
                createRequest("계속 이런 식이면 교육청에 신고하겠습니다.");


        ComplaintResponse createResponse =
                parentComplaintService.createDraft(
                        parentUser.getId(),
                        createRequest
                );


        Long complaintId = createResponse.getComplaintId();


        Complaint draft = complaintRepository.findById(complaintId)
                        .orElseThrow();


        assertThat(draft.getStatus())
                .isEqualTo(ComplaintStatus.DRAFT);

        assertThat(draft.getContent())
                .isEqualTo(
                        "계속 이런 식이면 교육청에 신고하겠습니다."
                );

        assertThat(draft.getStudent().getId())
                .isEqualTo(student.getId());


        // =========================
        // 2. AI Review
        // =========================

        RiskSummaryResponse reviewResponse =
                riskAnalysisService.analyze(
                        parentUser.getId(),
                        complaintId
                );


        assertThat(reviewResponse)
                .isNotNull();

        assertThat(reviewResponse.complaintId()).isEqualTo(complaintId);
        assertThat(reviewResponse.riskScore()).isEqualTo(60);
        assertThat(reviewResponse.riskLevel())
                .isEqualTo(com.teacherhub.risk.enums.RiskLevel.HIGH);
        var findings = riskAnalysisQueryService.findFindings(parentUser.getId(), reviewResponse.analysisId());
        assertThat(findings.finalRevision().reason()).isNotBlank();
        assertThat(findings.finalRevision().content()).isNotBlank();
        assertThat(draft.getContent()).isEqualTo(createRequest.content());
        assertThat(riskAnalysisQueryService.findDetectorResults(parentUser.getId(), reviewResponse.analysisId()).results())
                .hasSize(com.teacherhub.risk.enums.RiskTagCode.values().length);
        assertThat(riskAnalysisQueryService.findDetectorResults(parentUser.getId(), reviewResponse.analysisId()).results())
                .filteredOn(RiskDetectorResponse.TagResult::finalDetected)
                .extracting(RiskDetectorResponse.TagResult::code)
                .containsExactly(
                        com.teacherhub.risk.enums.RiskTagCode.PROFANITY,
                        com.teacherhub.risk.enums.RiskTagCode.THREAT,
                        com.teacherhub.risk.enums.RiskTagCode.UNFAIR_REQUEST);

        entityManager.flush();
        var savedAnalyses = riskAnalysisRepository.findByComplaintIdOrderByIdAsc(complaintId);
        assertThat(savedAnalyses).hasSize(1);
        var firstAnalysis = savedAnalyses.get(0);
        assertThat(firstAnalysis.getRiskScore()).isEqualTo(60);
        assertThat(firstAnalysis.getModelName()).isEqualTo("mock-llm");
        assertThat(firstAnalysis.getTemperature()).isEqualTo(0.0);
        assertThat(firstAnalysis.getContentVersion()).isEqualTo(1);
        assertThat(firstAnalysis.getAnalyzedAt()).isNotNull();
        assertThat(complaintRiskTagRepository.findByRiskAnalysisId(firstAnalysis.getId()))
                .hasSize(8).filteredOn(com.teacherhub.risk.entity.ComplaintRiskTag::isFinalDetected)
                .hasSize(3);
        assertThat(draft.getMaskedContent()).isNotNull();
        assertThat(draft.getStatus()).isEqualTo(ComplaintStatus.DRAFT);
        assertThat(draft.getCreatedAt()).isNotNull();


        // =========================
        // 3. 민원 내용 수정
        // =========================

        ComplaintDraftUpdateRequest updateRequest =
                updateRequest("아이의 학교생활과 관련하여 상황 확인을 요청드립니다.");


        parentComplaintService.updateDraft(
                parentUser.getId(),
                complaintId,
                updateRequest
        );


        Complaint updated =
                complaintRepository.findById(complaintId)
                        .orElseThrow();


        assertThat(updated.getContent())
                .isEqualTo(
                        "아이의 학교생활과 관련하여 상황 확인을 요청드립니다."
                );

        assertThat(updated.getStatus())
                .isEqualTo(ComplaintStatus.DRAFT);

        assertThat(updated.getMaskedContent()).isNull();
        assertThat(updated.getContentVersion()).isEqualTo(2);



        // =========================
        // 4. 최종 제출
        // =========================

        parentComplaintService.submitComplaint(
                parentUser.getId(),
                complaintId
        );


        Complaint submitted =
                complaintRepository.findById(complaintId)
                        .orElseThrow();


        assertThat(submitted.getStatus())
                .isEqualTo(ComplaintStatus.OPEN);


        // 담임교사가 정상적으로 지정됐는지
        assertThat(submitted.getTeacher().getId())
                .isEqualTo(teacher.getId());


        // 최종 수정 내용이 유지되는지
        assertThat(submitted.getContent())
                .isEqualTo(
                        "아이의 학교생활과 관련하여 상황 확인을 요청드립니다."
                );

        eventRepository.save(com.teacherhub.complaint.entity.ComplaintEvent.aiDraftGenerated(submitted));
        var editedReply = new com.teacherhub.complaint.entity.ComplaintMessage(
                submitted,
                teacherUser,
                MessageType.TEACHER_REPLY,
                "Edited reply"
        );
        editedReply.send();
        messageRepository.save(editedReply);
        eventRepository.save(com.teacherhub.complaint.entity.ComplaintEvent.messageSent(editedReply));
        var directReply = new com.teacherhub.complaint.entity.ComplaintMessage(
                submitted,
                teacherUser,
                MessageType.TEACHER_REPLY,
                "Direct reply"
        );
        directReply.send();
        messageRepository.save(directReply);
        eventRepository.save(com.teacherhub.complaint.entity.ComplaintEvent.messageSent(directReply));
        entityManager.flush();
        entityManager.clear();
        assertThat(messageRepository.findByComplaintIdOrderByIdAsc(complaintId))
                .filteredOn(message -> message.getType()
                        == MessageType.TEACHER_REPLY)
                .hasSize(2).extracting(com.teacherhub.complaint.entity.ComplaintMessage::getContent)
                .containsExactly("Edited reply", "Direct reply");
        assertThat(eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(complaintId))
                .filteredOn(event -> event.getEventType()
                        == MessageEventType.STATUS_CHANGED)
                .extracting(com.teacherhub.complaint.entity.ComplaintEvent::getNewStatus)
                .containsExactlyInAnyOrder(ComplaintStatus.DRAFT, ComplaintStatus.OPEN);
        assertThat(complaintRepository.findById(complaintId).orElseThrow().getMaskedContent()).isNotNull();

        var teacherDetail = teacherComplaintQueryService.findDetail(teacherUser.getId(), complaintId);
        assertThat(teacherDetail.studentName()).isEqualTo(student.getName());
        assertThat(teacherDetail.parentName()).isEqualTo(parentUser.getName());
        assertThat(teacherDetail.academicYear()).isEqualTo(2026);
        assertThat(teacherDetail.grade()).isEqualTo(3);
        assertThat(teacherDetail.classNo()).isEqualTo(2);
        assertThat(teacherDetail.submittedAt()).isNotNull();
        assertThat(teacherDetail.riskLevel()).isEqualTo(com.teacherhub.risk.enums.RiskLevel.HIGH);

        var teacherListItem = complaintQueryService.findComplaints(teacherUser.getId()).stream()
                .filter(item -> item.complaintId().equals(complaintId))
                .findFirst().orElseThrow();
        assertThat(teacherListItem.counterpartName()).isEqualTo(parentUser.getName());
        assertThat(teacherListItem.counterpartRole()).isEqualTo(UserRole.PARENT);
        assertThat(teacherListItem.grade()).isEqualTo(3);
        assertThat(teacherListItem.classNo()).isEqualTo(2);
        assertThat(teacherListItem.aiSummary()).isNotBlank();
        assertThat(teacherListItem.latestReceivedAt()).isCloseTo(
                teacherDetail.submittedAt(),
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS)
        );

        var parentListItem = complaintQueryService.findComplaints(parentUser.getId()).stream()
                .filter(item -> item.complaintId().equals(complaintId))
                .findFirst().orElseThrow();
        assertThat(parentListItem.counterpartName()).isEqualTo(teacherUser.getName());
        assertThat(parentListItem.counterpartRole()).isEqualTo(UserRole.TEACHER);

        var conversation = teacherComplaintQueryService.findConversation(teacherUser.getId(), complaintId);
        assertThat(conversation.messages()).hasSize(3);
        assertThat(conversation.messages().get(0).senderRole()).isEqualTo(UserRole.PARENT);
        assertThat(conversation.messages().get(0).summarized()).isTrue();
        assertThat(conversation.messages().subList(1, 3))
                .extracting(TeacherConversationResponse.Message::senderRole)
                .containsOnly(UserRole.TEACHER);

        teacherComplaintQueryService.viewOriginal(
                teacherUser.getId(),
                complaintId,
                complaintRepository.findById(complaintId).orElseThrow().initialMessage().getId()
        );
        var history = teacherComplaintQueryService.findHistory(teacherUser.getId(), complaintId);
        assertThat(history.events())
                .extracting(TeacherHistoryResponse.HistoryEvent::type)
                .contains(
                        MessageEventType.STATUS_CHANGED,
                        MessageEventType.RISK_ANALYZED,
                        MessageEventType.ORIGINAL_VIEWED,
                        MessageEventType.AI_DRAFT_GENERATED,
                        MessageEventType.MESSAGE_SENT
                );
        assertThat(history.events())
                .filteredOn(event -> event.type()
                        == MessageEventType.STATUS_CHANGED)
                .extracting(TeacherHistoryResponse.HistoryEvent::newStatus)
                .contains(ComplaintStatus.DRAFT, ComplaintStatus.OPEN);
    }

    private ComplaintCreateRequest createRequest(String content) {
        return new ComplaintCreateRequest(
                student.getId(),
                teacher.getId(),
                "테스트 민원 제목",
                content
        );
    }

    private ComplaintDraftUpdateRequest updateRequest(String content) {
        return new ComplaintDraftUpdateRequest(content);
    }
}
