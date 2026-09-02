package com.teacherhub.user.repository;

import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.ParentStudent;
import com.teacherhub.user.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, Long> {
    boolean existsByParentAndStudent(
            Parent parent,
            Student student
    );

    Optional<ParentStudent> findByParent_Id(Long parentId);
}