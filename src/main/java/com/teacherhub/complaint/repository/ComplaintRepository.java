package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.Complaint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    @EntityGraph(attributePaths = {"parent.user", "student", "teacher.user"})
    @Query("select c from Complaint c where c.parent.user.id = :userId order by c.createdAt desc")
    List<Complaint> findByParentUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"parent.user", "student", "teacher.user"})
    @Query("select c from Complaint c where c.teacher.user.id = :userId order by c.createdAt desc")
    List<Complaint> findByTeacherUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"parent.user", "student", "teacher.user"})
    @Query("select c from Complaint c where c.id = :id")
    Optional<Complaint> findDetailById(@Param("id") Long id);

    // 분석·수정·제출 요청을 민원 단위로 직렬화
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Complaint c where c.id = :id")
    Optional<Complaint> findForUpdate(@Param("id") Long id);

}
