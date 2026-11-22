package com.teacherhub.school.repository;

import com.teacherhub.school.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findBySchool_IdAndAcademicYearOrderByGradeAscClassNoAsc(
            Long schoolId,
            int academicYear
    );

    List<SchoolClass> findBySchool_IdAndAcademicYearAndGradeOrderByClassNoAsc(
            Long schoolId,
            int academicYear,
            int grade
    );

    Optional<SchoolClass> findBySchool_IdAndAcademicYearAndGradeAndClassNo(
            Long schoolId,
            int academicYear,
            int grade,
            int classNo
    );
}
