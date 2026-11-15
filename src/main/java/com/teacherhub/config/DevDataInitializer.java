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
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
@Profile({"dev", "demo-seed"})
@Order(2)
@RequiredArgsConstructor
// 테스트용 데이터
public class DevDataInitializer implements ApplicationRunner {
    private static final String PARENT_EMAIL = "parent@test.com";
    private static final String TEACHER_EMAIL = "teacher@test.com";
    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String DEMO_SCHOOL_CODE = "SCHOOL-001";
    private static final String DEFAULT_DEMO_PASSWORD = "Test1234!";

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
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String demoPassword = resolveDemoPassword();
        if (isAlreadySeeded()) {
            return;
        }

        String encodedPassword = passwordEncoder.encode(demoPassword);
        User parentUser = userRepository.save(new User(
                PARENT_EMAIL, encodedPassword, "Demo Parent", UserRole.PARENT));

        Parent parent = parentRepository.save(new Parent(parentUser));

        School school = findOrCreateDemoSchool();

        User teacherUser = userRepository.save(new User(
                TEACHER_EMAIL, encodedPassword, "Demo Teacher", UserRole.TEACHER));
        Teacher teacher = teacherRepository.save(new Teacher(teacherUser, "DEV-2026-001", school));
        Teacher unregisteredTeacher = teacherRepository.save(
                new Teacher(school, "가입용 교사", "DEV-2026-002")
        );

        User adminUser = userRepository.save(new User(
                ADMIN_EMAIL, encodedPassword, "Demo Admin", UserRole.ADMIN));

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

    private boolean isAlreadySeeded() {
        boolean parentExists = userRepository.existsByEmail(PARENT_EMAIL);
        boolean teacherExists = userRepository.existsByEmail(TEACHER_EMAIL);
        boolean adminExists = userRepository.existsByEmail(ADMIN_EMAIL);
        int existingAccountCount = (parentExists ? 1 : 0)
                + (teacherExists ? 1 : 0)
                + (adminExists ? 1 : 0);

        if (existingAccountCount == 0) {
            return false;
        }
        if (existingAccountCount == 3) {
            log.info("Demo data already exists; skipping seed creation");
            return true;
        }
        throw new IllegalStateException(
                "일부 데모 계정만 존재하여 데이터를 안전하게 생성할 수 없습니다. "
                        + "parent@test.com, teacher@test.com, admin@test.com 상태를 확인하세요."
        );
    }

    private String resolveDemoPassword() {
        String configuredPassword = environment.getProperty("DEMO_SEED_PASSWORD");
        if (StringUtils.hasText(configuredPassword)) {
            return configuredPassword;
        }

        boolean demoSeedActive = List.of(environment.getActiveProfiles()).contains("demo-seed");
        if (demoSeedActive) {
            throw new IllegalStateException(
                    "demo-seed 프로필에서는 DEMO_SEED_PASSWORD를 반드시 설정해야 합니다."
            );
        }
        return DEFAULT_DEMO_PASSWORD;
    }

    School findOrCreateDemoSchool() {
        return schoolRepository.findByCode(DEMO_SCHOOL_CODE)
                .orElseGet(() -> schoolRepository.save(new School(DEMO_SCHOOL_CODE, "상상초등학교")));
    }
}
