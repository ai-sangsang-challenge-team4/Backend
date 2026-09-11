package com.teacherhub.complaint.entity;

import com.teacherhub.school.entity.SchoolClass;
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


    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;


    @Column(unique = true)
    private String idempotencyKey;


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


    /**
     * 민원 내용 수정
     */
    public void updateContent(String content) {

        if (this.status != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "작성 중인 민원만 수정할 수 있습니다."
            );
        }

        this.content = content;
    }


    /**
     * 최종 제출
     */
    public void submit(
            Teacher teacher,
            String idempotencyKey
    ) {

        if (this.status != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "이미 제출된 민원입니다."
            );
        }

        this.teacher = teacher;
        this.idempotencyKey = idempotencyKey;
        this.status = ComplaintStatus.RECEIVED;
    }
}