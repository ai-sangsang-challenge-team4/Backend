package com.teacherhub.complaint.repository;

import com.teacherhub.complaint.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    // 분석·수정·제출 요청을 민원 단위로 직렬화합니다.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Complaint c where c.id = :id")
    Optional<Complaint> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

}
