package com.teacherhub.risk.entity;

import com.teacherhub.risk.enums.RiskTagCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "risk_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private RiskTagCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer defaultScore;

    public RiskTag(
            RiskTagCode code,
            String name,
            Integer defaultScore
    ) {
        this.code = code;
        this.name = name;
        this.defaultScore = defaultScore;
    }
}