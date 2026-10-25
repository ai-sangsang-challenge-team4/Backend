package com.teacherhub.complaint.entity;

import com.teacherhub.user.entity.Teacher;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
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

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

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

    public void updateDraft(AiDraft aiDraft, String content) {
        if (sentAt != null) {
            throw new IllegalStateException("이미 전송한 답변은 수정할 수 없습니다.");
        }
        validateAiDraft(aiDraft);
        this.aiDraft = aiDraft;
        this.content = content;
    }

    public void submit(LocalDateTime sentAt) {
        if (this.sentAt != null) {
            throw new IllegalStateException("이미 전송한 답변입니다.");
        }
        this.sentAt = sentAt;
    }

    private void validateAiDraft(AiDraft aiDraft) {
        if (aiDraft != null && !java.util.Objects.equals(
                aiDraft.getComplaint().getId(), complaint.getId())) {
            throw new IllegalArgumentException("다른 민원의 AI 답변 초안은 사용할 수 없습니다.");
        }
    }
}
