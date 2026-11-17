package com.teacherhub.complaint.escalation.repository;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RevisionStatus;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ComplaintEscalationRepositoryTest {

    @Autowired
    private ComplaintEscalationRepository escalationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void filtersByOwnerKeywordAndLatestReviewedRiskLevel() {
        User parentUser = persist(new User("parent-query@test.com", "password", "학부모", UserRole.PARENT));
        User teacherUser = persist(new User("teacher-query@test.com", "password", "담임 교사", UserRole.TEACHER));
        User otherTeacherUser = persist(new User("other-teacher-query@test.com", "password", "다른 교사", UserRole.TEACHER));
        User admin = persist(new User("admin-query@test.com", "password", "박관리자", UserRole.ADMIN));
        Parent parent = persist(new Parent(parentUser));
        Teacher teacher = persist(new Teacher(teacherUser, "QUERY-T-1"));
        Teacher otherTeacher = persist(new Teacher(otherTeacherUser, "QUERY-T-2"));
        Student student = persist(new Student("김학생", "QUERY-S-1"));
        Student otherStudent = persist(new Student("이학생", "QUERY-S-2"));

        Complaint complaint = persist(new Complaint(
                parent,
                student,
                teacher,
                "학생 생활지도 관련 문의",
                "상담 내용"
        ));
        Complaint otherComplaint = persist(new Complaint(
                parent,
                otherStudent,
                otherTeacher,
                "다른 교사의 문의",
                "다른 상담 내용"
        ));
        entityManager.flush();

        RiskAnalysis analysis = completedAnalysis(complaint, 90, RiskLevel.EMERGENCY);
        RiskAnalysis otherAnalysis = completedAnalysis(otherComplaint, 90, RiskLevel.EMERGENCY);
        ComplaintEscalation escalation = persist(new ComplaintEscalation(
                complaint,
                analysis,
                teacher,
                admin,
                Set.of(EscalationScope.MASKING_MESSAGES),
                "공동 검토가 필요합니다."
        ));
        persist(new ComplaintEscalation(
                otherComplaint,
                otherAnalysis,
                otherTeacher,
                admin,
                Set.of(EscalationScope.MASKING_MESSAGES),
                "다른 교사의 공유입니다."
        ));
        persist(new RiskReview(
                analysis,
                teacher,
                90,
                RiskLevel.EMERGENCY,
                5,
                RiskLevel.HIGH,
                RiskReviewDirection.LOWERED,
                "교사 검토로 조정"
        ));
        entityManager.flush();
        entityManager.clear();

        var pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "sharedAt"));
        var highResult = escalationRepository.searchSharedByTeacher(
                teacherUser.getId(),
                "생활지도",
                null,
                RiskLevel.HIGH,
                pageable
        );
        var emergencyResult = escalationRepository.searchSharedByTeacher(
                teacherUser.getId(),
                null,
                null,
                RiskLevel.EMERGENCY,
                pageable
        );

        assertThat(highResult.getContent())
                .extracting(ComplaintEscalation::getId)
                .containsExactly(escalation.getId());
        assertThat(emergencyResult).isEmpty();
    }

    private RiskAnalysis completedAnalysis(Complaint complaint, int score, RiskLevel level) {
        RiskAnalysis analysis = new RiskAnalysis(complaint, score, level, "test-model", 0.0);
        analysis.completeAnalysis(RevisionStatus.NOT_REQUESTED);
        return persist(analysis);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
