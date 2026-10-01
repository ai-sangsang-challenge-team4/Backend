package com.teacherhub.complaint.entity;

import com.teacherhub.user.entity.Student;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.Teacher;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;


    // 초안일 때는 아직 담임교사를 지정하지 않으므로 nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;


    @Column(name = "original_content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String maskedContent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 재분석 결과가 어느 원문 버전에 대한 것인지 구분합니다.
    @Column(nullable = false)
    private long contentVersion = 1;

    public void updateMaskedContent(String maskedContent) {
        this.maskedContent = maskedContent;
    }


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;


    public Complaint(
            Parent parent,
            Student student,
            String content
    ) {

        this.parent = parent;
        this.student = student;
        this.content = content;

        // 처음 생성하면 항상 DRAFT
        this.status = ComplaintStatus.DRAFT;
    }

    // 민원 내용 수정
    public void updateContent(String content) {

        if (this.status != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "작성 중인 민원만 수정할 수 있습니다."
            );
        }

        if (java.util.Objects.equals(this.content, content)) {
            return;
        }
        this.content = content;
        this.maskedContent = null;
        this.contentVersion++;
    }


    // 최종 민원 제출
    public void submit(
            Teacher teacher
    ) {

        if (this.status != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "이미 제출된 민원입니다."
            );
        }

        this.teacher = teacher;
        this.status = ComplaintStatus.ANALYZED;
    }
}
