package com.teacherhub.user.repository;

import com.teacherhub.user.entity.ParentStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ParentStudentReposotory extends JpaRepository<ParentStudent, Long> {
    Optional<ParentStudent> findByParent_Id(Long parentId);
}
