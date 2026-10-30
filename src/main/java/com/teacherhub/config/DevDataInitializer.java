package com.teacherhub.config;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.entity.ComplaintRiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskTagRepository;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.School;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.SchoolRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.*;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
// 테스트용 데이터
public class DevDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ParentRepository parentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentClassRepository studentClassRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository complaintEventRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskTagRepository riskTagRepository;
    private final ComplaintRiskTagRepository complaintRiskTagRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String encodedPassword = passwordEncoder.encode("Test1234!");
        User parentUser = userRepository.save(new User(
                "parent@test.com", encodedPassword, "Demo Parent", UserRole.PARENT));

        Parent parent = parentRepository.save(new Parent(parentUser));

        School school = schoolRepository.save(new School("SCHOOL-001", "상상초등학교"));

        User teacherUser = userRepository.save(new User(
                "teacher@test.com", encodedPassword, "Demo Teacher", UserRole.TEACHER));
        Teacher teacher = teacherRepository.save(new Teacher(teacherUser, "DEV-2026-001", school));
        Teacher unregisteredTeacher = teacherRepository.save(
                new Teacher(school, "가입용 교사", "DEV-2026-002")
        );

        User adminUser = userRepository.save(new User(
                "admin@test.com", encodedPassword, "Demo Admin", UserRole.ADMIN));

        Student student = studentRepository.save(new Student("Demo Student", "DEV-20260001"));
        parentStudentRepository.save(new ParentStudent(parent, student));

        SchoolClass schoolClass = new SchoolClass(school, 2026, 3, 2, teacher);
        schoolClass.assignTeacher(unregisteredTeacher);
        schoolClassRepository.save(schoolClass);
        studentClassRepository.save(new StudentClass(student, schoolClass));

        Complaint emergencyComplaint = new Complaint(
                parent,
                student,
                teacher,
                "긴급 민원 테스트",
                "내일 하교 시간에 학교 정문에서 기다렸다가 직접 다치게 하겠습니다. 어디로 가는지도 알고 있습니다."
        );
        complaintRepository.save(emergencyComplaint);
        complaintEventRepository.save(ComplaintEvent.statusChanged(
                emergencyComplaint,
                parentUser,
                null,
                ComplaintStatus.DRAFT
        ));
        emergencyComplaint.updateMaskedContent(
                "내일 하교 시간에 학교 정문에서 기다렸다가 직접 다치게 하겠습니다. 어디로 가는지도 알고 있습니다."
        );

        // 긴급 판정 규칙을 실행하지 않고도 관리자 공유 흐름을 검증할 수 있는 개발용 분석 결과다.
        RiskAnalysis emergencyAnalysis = new RiskAnalysis(
                emergencyComplaint,
                70,
                RiskLevel.EMERGENCY,
                "dev-emergency-fixture",
                0.0
        );
        emergencyAnalysis.complete(
                "직접적인 신체 위해와 구체적인 보복 계획이 포함되어 즉시 긴급 처리합니다.",
                null,
                List.of()
        );
        riskAnalysisRepository.save(emergencyAnalysis);

        var threatTag = riskTagRepository.findByCode(RiskTagCode.THREAT)
                .orElseThrow(() -> new IllegalStateException("개발 데이터 생성에 필요한 THREAT 태그가 없습니다."));
        ComplaintRiskTag emergencyTag = new ComplaintRiskTag(
                emergencyAnalysis,
                threatTag,
                new FinalRiskTagResponse(
                        RiskTagCode.THREAT,
                        true,
                        true,
                        false,
                        null,
                        "직접 다치게 하겠다는 표현과 하교 시간·장소를 특정한 계획",
                        "직접적인 신체 위해 및 구체적인 보복 계획"
                )
        );
        emergencyTag.recordSourceEvidence("긴급 요소 개발용 데이터", null);
        complaintRiskTagRepository.save(emergencyTag);
        complaintEventRepository.save(ComplaintEvent.riskAnalyzed(
                emergencyComplaint,
                emergencyAnalysis.getId(),
                emergencyAnalysis.getRiskScore(),
                emergencyAnalysis.getRiskLevel()
        ));

//        emergencyComplaint.submit();
//        complaintEventRepository.save(ComplaintEvent.statusChanged(
//                emergencyComplaint,
//                parentUser,
//                ComplaintStatus.DRAFT,
//                ComplaintStatus.OPEN
//        ));
        complaintEventRepository.save(ComplaintEvent.messageSent(emergencyComplaint.initialMessage()));

        log.info(
                "Dev data ready: teacherEmail=teacher@test.com, adminEmail=admin@test.com, "
                        + "adminId={}, emergencyComplaintId={}, studentId={}, classId={}",
                adminUser.getId(), emergencyComplaint.getId(), student.getId(), schoolClass.getId()
        );
    }
}
