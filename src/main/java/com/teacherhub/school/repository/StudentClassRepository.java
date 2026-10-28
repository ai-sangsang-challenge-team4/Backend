package com.teacherhub.school.repository;

import com.teacherhub.school.entity.StudentClass;
import com.teacherhub.user.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {
    Optional<StudentClass> findTopByStudent_IdOrderBySchoolClass_AcademicYearDesc(Long studentId);
    Optional<StudentClass> findTopByStudentOrderBySchoolClass_AcademicYearDesc(Student student);

    Optional<StudentClass> findBySchoolClass_School_IdAndSchoolClass_IdAndStudent_Id(
            Long schoolId,
            Long schoolClassId,
            Long studentId
    );

    @EntityGraph(attributePaths = "student")
    List<StudentClass> findBySchoolClass_IdOrderByStudent_StudentNoAsc(Long schoolClassId);
}
