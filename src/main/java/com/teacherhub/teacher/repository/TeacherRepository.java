package com.teacherhub.teacher.repository;

import com.teacherhub.teacher.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByUserId(Long userId);

    boolean existsByEmployeeNo(String employeeNo);
}