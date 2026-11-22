package com.teacherhub.config;

import com.teacherhub.school.entity.School;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.SchoolRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.repository.StudentRepository;
import com.teacherhub.user.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;

@Slf4j
@Component
@Profile({"dev", "demo-seed"})
@Order(3)
@RequiredArgsConstructor
public class DemoSignupDataInitializer implements ApplicationRunner {

    static final String SCHOOL_CODE = "DEMO-SANGSANG-MIDDLE";
    static final String SCHOOL_NAME = "상상중학교";
    static final int GRADE = 2;
    static final int CLASS_NO = 1;

    private static final List<TeacherRosterEntry> TEACHERS = List.of(
            new TeacherRosterEntry("김민지", "SSMS-T-001"),
            new TeacherRosterEntry("이준호", "SSMS-T-002")
    );
    private static final List<StudentRosterEntry> STUDENTS = List.of(
            new StudentRosterEntry("김서윤", "1"),
            new StudentRosterEntry("이도윤", "2"),
            new StudentRosterEntry("박하린", "3")
    );

    private final SchoolRepository schoolRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentClassRepository studentClassRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        School school = schoolRepository.findByCode(SCHOOL_CODE)
                .map(this::validateSchool)
                .orElseGet(() -> schoolRepository.save(new School(SCHOOL_CODE, SCHOOL_NAME)));

        List<Teacher> teachers = TEACHERS.stream()
                .map(entry -> findOrCreateTeacher(school, entry))
                .toList();

        int academicYear = Year.now().getValue();
        SchoolClass schoolClass = schoolClassRepository
                .findBySchool_IdAndAcademicYearAndGradeAndClassNo(
                        school.getId(),
                        academicYear,
                        GRADE,
                        CLASS_NO
                )
                .orElseGet(() -> schoolClassRepository.save(new SchoolClass(
                        school,
                        academicYear,
                        GRADE,
                        CLASS_NO,
                        teachers.get(0)
                )));

        teachers.forEach(schoolClass::assignTeacher);
        schoolClassRepository.save(schoolClass);
        STUDENTS.forEach(entry -> findOrCreateStudent(schoolClass, entry));

        log.info(
                "Demo signup directory ready: school={}, academicYear={}, grade={}, classNo={}, "
                        + "teacherEmployeeNos={}, studentNos={}",
                SCHOOL_NAME,
                academicYear,
                GRADE,
                CLASS_NO,
                TEACHERS.stream().map(TeacherRosterEntry::employeeNo).toList(),
                STUDENTS.stream().map(StudentRosterEntry::studentNo).toList()
        );
    }

    private School validateSchool(School school) {
        if (!SCHOOL_NAME.equals(school.getName())) {
            throw new IllegalStateException("데모 학교 코드가 다른 학교명에 사용되고 있습니다.");
        }
        return school;
    }

    private Teacher findOrCreateTeacher(School school, TeacherRosterEntry entry) {
        return teacherRepository.findByEmployeeNo(entry.employeeNo())
                .map(teacher -> validateTeacher(school, teacher, entry))
                .orElseGet(() -> teacherRepository.save(
                        new Teacher(school, entry.name(), entry.employeeNo())
                ));
    }

    private Teacher validateTeacher(School school, Teacher teacher, TeacherRosterEntry entry) {
        if (teacher.getSchool() == null
                || !school.getId().equals(teacher.getSchool().getId())
                || !entry.name().equals(teacher.getName())) {
            throw new IllegalStateException("데모 교직원 번호가 다른 교사 정보에 사용되고 있습니다.");
        }
        return teacher;
    }

    private void findOrCreateStudent(SchoolClass schoolClass, StudentRosterEntry entry) {
        studentClassRepository
                .findBySchoolClass_IdAndStudent_StudentNo(schoolClass.getId(), entry.studentNo())
                .ifPresentOrElse(
                        studentClass -> validateStudent(studentClass, entry),
                        () -> {
                            Student student = studentRepository.save(
                                    new Student(entry.name(), entry.studentNo())
                            );
                            studentClassRepository.save(new StudentClass(student, schoolClass));
                        }
                );
    }

    private void validateStudent(StudentClass studentClass, StudentRosterEntry entry) {
        if (!entry.name().equals(studentClass.getStudent().getName())) {
            throw new IllegalStateException("데모 학생 번호가 다른 학생 정보에 사용되고 있습니다.");
        }
    }

    private record TeacherRosterEntry(String name, String employeeNo) {
    }

    private record StudentRosterEntry(String name, String studentNo) {
    }
}
