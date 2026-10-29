package com.teacherhub.complaint.template.entity;

import com.teacherhub.risk.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "response_templates", uniqueConstraints =
        @UniqueConstraint(name = "uk_response_template_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResponseTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    public ResponseTemplate(String code, String name, RiskLevel riskLevel) {
        this.code = code;
        this.name = name;
        this.riskLevel = riskLevel;
    }
}
