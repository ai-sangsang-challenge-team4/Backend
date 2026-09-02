package com.teacherhub.school.repository;

import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.user.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {
    Optional<StudentClass> findTopByStudent_IdOrderBySchoolClass_AcademicYearDesc(Long studentId);
    Optional<StudentClass> findTopByStudentOrderBySchoolClass_AcademicYearDesc(Student student);
}
