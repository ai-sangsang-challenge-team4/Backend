package com.teacherhub;

import com.teacherhub.complaint.dto.ComplaintRequest;
import com.teacherhub.complaint.dto.ComplaintResponse;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;

import com.teacherhub.risk.dto.response.RiskDetectorResponse;
import com.teacherhub.risk.masking.ComplaintMaskingService;
import com.teacherhub.risk.service.RiskAnalysisService;
import com.teacherhub.risk.service.RiskAnalysisQueryService;
import com.teacherhub.complaint.service.ComplaintService;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.StudentClassRepository;

import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.ParentStudent;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;

import com.teacherhub.user.enums.Relationship;
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
    void submittingOriginalTextAgainReusesFirstAnalysis() {
        Long id = complaintService.createDraft(parentUser.getId(),
                new ComplaintRequest(student.getId(), "최초 원문")).getComplaintId();
        riskAnalysisService.analyze(parentUser.getId(), id);
        complaintService.updateDraft(parentUser.getId(), id, new ComplaintRequest(null, "수정했다가"));
        complaintService.updateDraft(parentUser.getId(), id, new ComplaintRequest(null, "최초 원문"));
        complaintService.submitComplaint(parentUser.getId(), id);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(id)).isEqualTo(1);
        assertThat(complaintRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.ANALYZED);
    }
    @Test
    void reusesAnalysisWithoutChangesAndSubmitsAsAnalyzed() {
        Long id = complaintService.createDraft(parentUser.getId(),
                new ComplaintRequest(student.getId(), "상황을 확인해 주세요.")).getComplaintId();
        assertThat(complaintService.findStatus(parentUser.getId(), id).getStatus()).isEqualTo(ComplaintStatus.DRAFT);
        var first = riskAnalysisService.analyze(parentUser.getId(), id);
        complaintService.updateDraft(parentUser.getId(), id, new ComplaintRequest(null, "상황을 확인해 주세요."));
        entityManager.flush();
        entityManager.clear();
        var reused = riskAnalysisService.analyze(parentUser.getId(), id);
        assertThat(reused).usingRecursiveComparison().isEqualTo(first);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(id)).isEqualTo(1);
        assertThat(complaintRepository.findById(id).orElseThrow().getContentVersion()).isEqualTo(1);
        assertThat(complaintRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.DRAFT);
        complaintService.submitComplaint(parentUser.getId(), id);
        assertThat(complaintService.findStatus(parentUser.getId(), id).getStatus()).isEqualTo(ComplaintStatus.ANALYZED);
        assertThat(complaintRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.ANALYZED);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> complaintService.submitComplaint(parentUser.getId(), id))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintNotDraftException.class);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(id)).isEqualTo(1);
        assertThat(historyRepository.findByComplaintIdOrderByIdAsc(id))
                .extracting(com.teacherhub.complaint.entity.ComplaintStatusHistory::getNewStatus)
                .containsExactly(ComplaintStatus.DRAFT, ComplaintStatus.ANALYZED);
    }

    @Test
    void requiresCurrentAnalysisAndLocksEditsAfterOneReanalysis() {
        Long id = complaintService.createDraft(parentUser.getId(),
                new ComplaintRequest(student.getId(), "처음 내용")).getComplaintId();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> complaintService.submitComplaint(parentUser.getId(), id))
                .isInstanceOf(com.teacherhub.complaint.exception.AnalysisRequiredException.class);
        riskAnalysisService.analyze(parentUser.getId(), id);
        complaintService.updateDraft(parentUser.getId(), id, new ComplaintRequest(null, "수정 내용"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> riskAnalysisService.analyze(parentUser.getId(), id))
                .isInstanceOf(com.teacherhub.complaint.exception.FinalAnalysisOnSubmitException.class);
        complaintService.submitComplaint(parentUser.getId(), id);
        assertThat(riskAnalysisRepository.countByComplaintIdAndCompletedTrue(id)).isEqualTo(2);
        var finalAnalysis = riskAnalysisRepository.findByComplaintIdOrderByIdAsc(id).get(1);
        assertThat(finalAnalysis.getContentVersion()).isEqualTo(2);
        assertThat(finalAnalysis.getAiRevision()).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> complaintService.updateDraft(parentUser.getId(), id, new ComplaintRequest(null, "또 수정")))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintNotDraftException.class);
        assertThat(complaintRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.ANALYZED);
    }
    @Autowired
    private ComplaintMaskingService maskingService;

    @Test
    void masksAndRetrievesAnalysisWithOwnershipChecks() {
        var created = complaintService.createDraft(parentUser.getId(),
                new ComplaintRequest(student.getId(), "전화 010-1234-5678로 연락 부탁드립니다."));
        Long id = created.getComplaintId();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> complaintService.findStatus(teacherUser.getId(), id))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> complaintService.findStatus(parentUser.getId(), Long.MAX_VALUE))
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
        complaintService.updateDraft(parentUser.getId(), id, new ComplaintRequest(null, "새 내용"));
        assertThat(complaintRepository.findById(id).orElseThrow().getMaskedContent()).isNull();
    }

    @Test
    void persistsTagSuggestionsAndSeparatesSourceEvidence() {
        Long complaintId = complaintService.createDraft(parentUser.getId(),
                new ComplaintRequest(student.getId(), "확인을 요청드립니다.")).getComplaintId();
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
    private com.teacherhub.complaint.repository.ComplaintStatusHistoryRepository historyRepository;
    @Autowired
    private com.teacherhub.complaint.repository.AiDraftRepository aiDraftRepository;
    @Autowired
    private com.teacherhub.complaint.repository.ResponseRepository responseRepository;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private RiskAnalysisService riskAnalysisService;

    @Autowired
    private RiskAnalysisQueryService riskAnalysisQueryService;

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
        Relationship relationship =
                Relationship.values()[0];

        ParentStudent parentStudent =
                new ParentStudent(
                        parent,
                        student,
                        relationship
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

        ComplaintRequest createRequest =
                new ComplaintRequest(
                        student.getId(),
                        "계속 이런 식이면 교육청에 신고하겠습니다."
                );


        ComplaintResponse createResponse =
                complaintService.createDraft(
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
        assertThat(draft.getContent()).isEqualTo(createRequest.getContent());
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

        ComplaintRequest updateRequest =
                new ComplaintRequest(
                        null,
                        "아이의 학교생활과 관련하여 상황 확인을 요청드립니다."
                );


        complaintService.updateDraft(
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

        complaintService.submitComplaint(
                parentUser.getId(),
                complaintId
        );


        Complaint submitted =
                complaintRepository.findById(complaintId)
                        .orElseThrow();


        assertThat(submitted.getStatus())
                .isEqualTo(ComplaintStatus.ANALYZED);


        // 담임교사가 정상적으로 지정됐는지
        assertThat(submitted.getTeacher().getId())
                .isEqualTo(teacher.getId());


        // 최종 수정 내용이 유지되는지
        assertThat(submitted.getContent())
                .isEqualTo(
                        "아이의 학교생활과 관련하여 상황 확인을 요청드립니다."
                );

        var aiDraft = aiDraftRepository.save(new com.teacherhub.complaint.entity.AiDraft(
                submitted, "Teacher reply draft", "Summary", "mock-reply-model"));
        var sentAt = java.time.LocalDateTime.now();
        responseRepository.save(new com.teacherhub.complaint.entity.Response(
                submitted, teacher, aiDraft, "Edited reply", sentAt));
        responseRepository.save(new com.teacherhub.complaint.entity.Response(
                submitted, teacher, null, "Direct reply", sentAt));
        entityManager.flush();
        entityManager.clear();
        assertThat(responseRepository.findByComplaintIdOrderByIdAsc(complaintId))
                .hasSize(2).extracting(com.teacherhub.complaint.entity.Response::getContent)
                .containsExactly("Edited reply", "Direct reply");
        assertThat(aiDraftRepository.findByComplaintIdOrderByIdAsc(complaintId)).hasSize(1);
        assertThat(historyRepository.findByComplaintIdOrderByIdAsc(complaintId))
                .extracting(com.teacherhub.complaint.entity.ComplaintStatusHistory::getNewStatus)
                .containsExactly(ComplaintStatus.DRAFT, ComplaintStatus.ANALYZED);
        assertThat(complaintRepository.findById(complaintId).orElseThrow().getMaskedContent()).isNotNull();
    }
}
