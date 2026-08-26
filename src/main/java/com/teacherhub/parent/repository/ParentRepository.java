package com.teacherhub.parent.repository;

import com.teacherhub.parent.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParentRepository extends JpaRepository<Parent, Long> {

    Optional<Parent> findByUserId(Long userId);
}