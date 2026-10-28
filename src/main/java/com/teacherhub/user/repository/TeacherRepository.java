package com.teacherhub.user.repository;

import com.teacherhub.user.entity.Teacher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Teacher t where t.employeeNo = :employeeNo")
    Optional<Teacher> findForSignupByEmployeeNo(@Param("employeeNo") String employeeNo);
}
