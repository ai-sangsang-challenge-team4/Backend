package com.teacherhub.complaint.template.repository;

import com.teacherhub.complaint.template.entity.ResponseTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResponseTemplateRepository extends JpaRepository<ResponseTemplate, Long> {
    Optional<ResponseTemplate> findByCode(String code);
}
