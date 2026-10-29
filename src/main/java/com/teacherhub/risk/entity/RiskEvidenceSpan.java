package com.teacherhub.risk.entity;

import com.teacherhub.risk.dto.detection.EvidenceSpan;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskEvidenceSpan {

    @Column(name = "evidence_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "start_offset")
    private Integer start;

    @Column(name = "end_offset")
    private Integer end;

    public RiskEvidenceSpan(EvidenceSpan span) {
        this.text = span.text();
        this.start = span.start();
        this.end = span.end();
    }

    public EvidenceSpan toDto() {
        return new EvidenceSpan(text, start, end);
    }
}
