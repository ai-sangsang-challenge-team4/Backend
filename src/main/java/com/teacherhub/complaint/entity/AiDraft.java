package com.teacherhub.complaint.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String draftContent;
    @Column(columnDefinition = "TEXT")
    private String summary;
    private String modelName;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 교사용 답변 초안이며 학부모의 민원 수정 제안과 별개입니다.
    public AiDraft(Complaint complaint, String draftContent, String summary, String modelName) {
        this.complaint = complaint;
        this.draftContent = draftContent;
        this.summary = summary;
        this.modelName = modelName;
    }
}
