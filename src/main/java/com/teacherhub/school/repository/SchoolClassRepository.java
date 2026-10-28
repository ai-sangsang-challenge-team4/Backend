package com.teacherhub.school.repository;

import com.teacherhub.school.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
}
