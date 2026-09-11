package com.teacherhub;

import com.teacherhub.complaint.dto.ComplaintRequest;
import com.teacherhub.complaint.dto.ComplaintResponse;
import com.teacherhub.complaint.dto.ComplaintReviewResponse;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;

import com.teacherhub.complaint.service.ComplaintReviewService;
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

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private ComplaintReviewService complaintReviewService;

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

        ComplaintReviewResponse reviewResponse =
                complaintReviewService.review(
                        parentUser.getId(),
                        complaintId
                );


        assertThat(reviewResponse)
                .isNotNull();

        assertThat(reviewResponse.getOriginalContent())
                .isEqualTo(
                        "계속 이런 식이면 교육청에 신고하겠습니다."
                );


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


        // =========================
        // 4. 최종 제출
        // =========================

        complaintService.submitComplaint(
                parentUser.getId(),
                complaintId,
                "test-idempotency-key-001"
        );


        Complaint submitted =
                complaintRepository.findById(complaintId)
                        .orElseThrow();


        assertThat(submitted.getStatus())
                .isEqualTo(ComplaintStatus.RECEIVED);


        // 담임교사가 정상적으로 지정됐는지
        assertThat(submitted.getTeacher().getId())
                .isEqualTo(teacher.getId());


        // 최종 수정 내용이 유지되는지
        assertThat(submitted.getContent())
                .isEqualTo(
                        "아이의 학교생활과 관련하여 상황 확인을 요청드립니다."
                );


        // 중복 전송 방지 키가 저장됐는지
        assertThat(submitted.getIdempotencyKey())
                .isEqualTo(
                        "test-idempotency-key-001"
                );
    }
}