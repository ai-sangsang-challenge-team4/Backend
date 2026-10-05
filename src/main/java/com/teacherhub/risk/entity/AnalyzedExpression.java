package com.teacherhub.risk.entity;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AnalyzedExpression {
    @Column(columnDefinition = "TEXT")
    private String expression;
    @Column(columnDefinition = "TEXT")
    private String reason;
}
