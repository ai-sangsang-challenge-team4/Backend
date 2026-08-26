package com.teacherhub.complaint.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 학부모
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    // 학생
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    // 학급
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    // 교사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    // 원문
    @Column(name = "submitted_content", nullable = false, columnDefinition = "TEXT")
    private String submittedContent;

    // 마스킹된 내용
    @Column(name = "masked_content", nullable = false, columnDefinition = "TEXT")
    private String maskedContent;

    // 진행 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;

    // 중복 방지
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Complaint(
            Parent parent,
            Student student,
            SchoolClass schoolClass,
            Teacher teacher,
            String submittedContent,
            String maskedContent,
            String idempotencyKey
    ) {
        this.parent = parent;
        this.student = student;
        this.schoolClass = schoolClass;
        this.teacher = teacher;
        this.submittedContent = submittedContent;
        this.maskedContent = maskedContent;
        this.idempotencyKey = idempotencyKey;
        this.status = ComplaintStatus.RECEIVED;
    }

    public void updateStatus(ComplaintStatus status) {
        this.status = status;
    }
}

