package com.teacherhub.config;

import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.SchoolRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.repository.StudentRepository;
import com.teacherhub.user.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DemoSignupDataInitializerTest {

    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private StudentClassRepository studentClassRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private StudentRepository studentRepository;

    @Test
    void createsIdempotentSangsangMiddleSchoolRosterForParentAndTeacherSignup() {
        DemoSignupDataInitializer initializer = new DemoSignupDataInitializer(
                schoolRepository,
                schoolClassRepository,
                studentClassRepository,
                teacherRepository,
                studentRepository
        );

        initializer.run(null);
        initializer.run(null);

        var school = schoolRepository.findByCode(DemoSignupDataInitializer.SCHOOL_CODE).orElseThrow();
        assertThat(school.getName()).isEqualTo("상상중학교");

        var firstTeacher = teacherRepository.findByEmployeeNo("SSMS-T-001").orElseThrow();
        var secondTeacher = teacherRepository.findByEmployeeNo("SSMS-T-002").orElseThrow();
        assertThat(firstTeacher.getName()).isEqualTo("김민지");
        assertThat(firstTeacher.getSchool().getId()).isEqualTo(school.getId());
        assertThat(firstTeacher.getUser()).isNull();
        assertThat(secondTeacher.getName()).isEqualTo("이준호");
        assertThat(secondTeacher.getSchool().getId()).isEqualTo(school.getId());
        assertThat(secondTeacher.getUser()).isNull();

        var schoolClass = schoolClassRepository
                .findBySchool_IdAndAcademicYearAndGradeAndClassNo(
                        school.getId(),
                        Year.now().getValue(),
                        2,
                        1
                )
                .orElseThrow();
        assertThat(schoolClass.getAssignedTeachers())
                .extracting(teacher -> teacher.getEmployeeNo())
                .containsExactlyInAnyOrder("SSMS-T-001", "SSMS-T-002");

        assertThat(studentClassRepository.findBySchoolClass_Id(schoolClass.getId()))
                .extracting(
                        studentClass -> studentClass.getStudent().getName(),
                        studentClass -> studentClass.getStudent().getStudentNo()
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("김서윤", "1"),
                        org.assertj.core.groups.Tuple.tuple("이도윤", "2"),
                        org.assertj.core.groups.Tuple.tuple("박하린", "3")
                );

        assertThat(schoolRepository.count()).isEqualTo(1);
        assertThat(schoolClassRepository.count()).isEqualTo(1);
        assertThat(teacherRepository.count()).isEqualTo(2);
        assertThat(studentRepository.count()).isEqualTo(3);
        assertThat(studentClassRepository.count()).isEqualTo(3);
    }
}
