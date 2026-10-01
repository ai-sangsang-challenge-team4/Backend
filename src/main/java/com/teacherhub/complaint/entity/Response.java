package com.teacherhub.complaint.entity;

import com.teacherhub.user.entity.Teacher;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Response {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_draft_id")
    private AiDraft aiDraft;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    private LocalDateTime sentAt;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Response(Complaint complaint, Teacher teacher, AiDraft aiDraft, String content, LocalDateTime sentAt) {
        if (aiDraft != null && !java.util.Objects.equals(
                aiDraft.getComplaint().getId(), complaint.getId())) {
            throw new IllegalArgumentException("다른 민원의 AI 답변 초안은 사용할 수 없습니다.");
        }
        this.complaint = complaint;
        this.teacher = teacher;
        this.aiDraft = aiDraft;
        this.content = content;
        this.sentAt = sentAt;
    }
}
