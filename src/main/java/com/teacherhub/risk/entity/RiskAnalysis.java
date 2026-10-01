package com.teacherhub.risk.entity;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.risk.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAnalysis {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Column(nullable = false)
    private long contentVersion;

    @Column(nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    private String modelName;

    private Double temperature;

    @Column(columnDefinition = "TEXT")
    private String aiReason;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    public RiskAnalysis(Complaint complaint, int riskScore, RiskLevel riskLevel,
                        String modelName, Double temperature, String aiReason) {
        this.complaint = complaint;
        this.contentVersion = complaint.getContentVersion();
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.modelName = modelName;
        this.temperature = temperature;
        this.aiReason = aiReason;
        this.analyzedAt = LocalDateTime.now();
    }
}
