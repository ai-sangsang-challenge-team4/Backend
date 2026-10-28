package com.teacherhub.auth;

import com.teacherhub.school.entity.School;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.SchoolRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.repository.ParentRepository;
import com.teacherhub.user.repository.ParentStudentRepository;
import com.teacherhub.user.repository.StudentRepository;
import com.teacherhub.user.repository.TeacherRepository;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "JWT_SECRET=test-jwt-secret-key-must-be-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class SignupFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private StudentClassRepository studentClassRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ParentRepository parentRepository;
    @Autowired
    private ParentStudentRepository parentStudentRepository;

    private School school;
    private SchoolClass schoolClass;
    private Student student;
    private Teacher teacherRoster;

    @BeforeEach
    void setUp() {
        school = schoolRepository.save(new School("TEST-SCHOOL", "테스트초등학교"));
        teacherRoster = teacherRepository.save(new Teacher(school, "김교사", "T-001"));
        schoolClass = schoolClassRepository.save(new SchoolClass(school, 2026, 2, 3, teacherRoster));
        student = studentRepository.save(new Student("김학생", "7"));
        studentClassRepository.save(new StudentClass(student, schoolClass));
    }

    @Test
    void exposesOperationalSchoolDataAndRegistersParentAndTeacher() throws Exception {
        mockMvc.perform(get("/auth/signup-options/schools").param("keyword", "테스트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("SCHOOL"))
                .andExpect(jsonPath("$[0].id").value(school.getId()))
                .andExpect(jsonPath("$[0].displayName").value("테스트초등학교"));

        mockMvc.perform(get("/auth/signup-options/schools/{schoolId}/classes", school.getId())
                        .param("academicYear", "2026")
                        .param("grade", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("SCHOOL_CLASS"))
                .andExpect(jsonPath("$[0].id").value(schoolClass.getId()))
                .andExpect(jsonPath("$[0].classNo").value(3));

        mockMvc.perform(get("/auth/signup-options/classes/{classId}/students", schoolClass.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("STUDENT"))
                .andExpect(jsonPath("$[0].id").value(student.getId()))
                .andExpect(jsonPath("$[0].referenceCode").value("7"))
                .andExpect(jsonPath("$[0].studentName").doesNotExist());

        String parentRequest = """
                {
                  "schoolId": %d,
                  "email": "new-parent@example.com",
                  "password": "Password123!",
                  "name": "김학부모",
                  "role": "PARENT",
                  "schoolClassId": %d,
                  "studentId": %d,
                  "studentName": "김학생"
                }
                """.formatted(school.getId(), schoolClass.getId(), student.getId());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parentRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PARENT"));

        var parentUser = userRepository.findByEmail("new-parent@example.com").orElseThrow();
        var parent = parentRepository.findByUserId(parentUser.getId()).orElseThrow();
        assertThat(parentStudentRepository.findByParent_Id(parent.getId()).orElseThrow().getStudent().getId())
                .isEqualTo(student.getId());

        String teacherRequest = """
                {
                  "schoolId": %d,
                  "email": "new-teacher@example.com",
                  "password": "Password123!",
                  "name": "김교사",
                  "role": "TEACHER",
                  "employeeNo": "T-001"
                }
                """.formatted(school.getId());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TEACHER"));

        assertThat(teacherRepository.findById(teacherRoster.getId()).orElseThrow().getUser().getEmail())
                .isEqualTo("new-teacher@example.com");
    }
}
