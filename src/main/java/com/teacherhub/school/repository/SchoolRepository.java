package com.teacherhub.school.repository;

import com.teacherhub.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findTop20ByOrderByNameAsc();

    List<School> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String keyword);
}
