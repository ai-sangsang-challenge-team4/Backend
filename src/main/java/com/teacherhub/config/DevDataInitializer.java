package com.teacherhub.config;

import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.school.repository.SchoolClassRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.*;
import com.teacherhub.user.enums.Relationship;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ParentRepository parentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentClassRepository studentClassRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String encodedPassword = passwordEncoder.encode("Test1234!");
        User parentUser = userRepository.save(new User(
                "parent@test.com", encodedPassword, "Demo Parent", UserRole.PARENT));
        Parent parent = parentRepository.save(new Parent(parentUser));

        User teacherUser = userRepository.save(new User(
                "teacher@test.com", encodedPassword, "Demo Teacher", UserRole.TEACHER));
        Teacher teacher = teacherRepository.save(new Teacher(teacherUser, "DEV-2026-001"));

        Student student = studentRepository.save(new Student("Demo Student", "DEV-20260001"));
        parentStudentRepository.save(new ParentStudent(parent, student, Relationship.GUARDIAN));
        SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(2026, 3, 2, teacher));
        studentClassRepository.save(new StudentClass(student, schoolClass));

        log.info("Dev data ready: parentEmail=parent@test.com, studentId={}, classId={}",
                student.getId(), schoolClass.getId());
    }
}
