package com.teacherhub.school.repository;

import com.teacherhub.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {

    Optional<School> findByCode(String code);

    List<School> findTop20ByOrderByNameAsc();

    List<School> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String keyword);
}
