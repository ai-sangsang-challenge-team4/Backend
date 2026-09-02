package com.teacherhub.user.repository;

import com.teacherhub.user.entity.ParentStudent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentStudentReposotory extends JpaRepository<ParentStudent, Long> {
}
