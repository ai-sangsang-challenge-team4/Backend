package com.teacherhub.complaint.template.repository;

import com.teacherhub.complaint.template.entity.ResponseTemplateVersion;
import com.teacherhub.risk.enums.RiskLevel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResponseTemplateVersionRepository extends JpaRepository<ResponseTemplateVersion, Long> {
    @EntityGraph(attributePaths = "template")
    List<ResponseTemplateVersion> findByActiveTrueAndTemplate_RiskLevelOrderByTemplate_NameAscVersionDesc(
            RiskLevel riskLevel
    );

    Optional<ResponseTemplateVersion> findFirstByTemplate_CodeOrderByVersionDesc(String templateCode);
}
