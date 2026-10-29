package com.teacherhub.complaint.entity;

import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
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
import java.util.ArrayList;
import java.util.List;

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


    // 최초 민원 작성 시 학생의 담당 교사 중 한 명을 문의 대상으로 선택
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(nullable = false, length = 100)
    private String title;


    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitingFor waitingFor;

    private LocalDateTime closedAt;

    // 하나의 민원에 여러 메세지 존재
    @OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ComplaintMessage> messages = new ArrayList<>();


    public Complaint(
            Parent parent,
            Student student,
            Teacher teacher,
            String title,
            String content
    ) {

        this.parent = parent;
        this.student = student;
        this.teacher = teacher;
        this.title = title;
        this.status = ComplaintStatus.DRAFT;
        this.waitingFor = WaitingFor.NONE;
        this.messages.add(new ComplaintMessage(
                this,
                parent.getUser(),
                MessageType.PARENT_INQUIRY,
                content
        ));
    }

    // 민원 내용 수정
    public void updateDraft(String content) {

        if (this.status != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "작성 중인 민원만 수정할 수 있습니다."
            );
        }

        initialMessage().updateDraft(content);
    }

    public void updateMaskedContent(String maskedContent) {
        initialMessage().updateMaskedContent(maskedContent);
    }


    // 최종 민원 제출
    public void submit() {

        if (this.status != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "이미 제출된 민원입니다."
            );
        }

        initialMessage().send();
        this.status = ComplaintStatus.OPEN;
        this.waitingFor = WaitingFor.TEACHER;
    }

    public void teacherMessageSent() {
        if (status != ComplaintStatus.OPEN || waitingFor != WaitingFor.TEACHER) {
            throw new IllegalStateException("교사가 답변할 차례가 아닙니다.");
        }
        this.waitingFor = WaitingFor.PARENT;
    }

    public ComplaintStatus parentMessageSent() {
        ComplaintStatus previousStatus = status;
        if (status == ComplaintStatus.CLOSED) {
            this.status = ComplaintStatus.OPEN;
            this.waitingFor = WaitingFor.TEACHER;
            this.closedAt = null;
            return previousStatus;
        }
        if (status != ComplaintStatus.OPEN || waitingFor != WaitingFor.PARENT) {
            throw new IllegalStateException("학부모가 추가 문의를 작성할 차례가 아닙니다.");
        }
        this.waitingFor = WaitingFor.TEACHER;
        return previousStatus;
    }

    public void closeByTeacher() {
        if (status != ComplaintStatus.OPEN || waitingFor != WaitingFor.PARENT) {
            throw new IllegalStateException("교사 답변 전송 후 학부모 응답 대기 중인 민원만 종료할 수 있습니다.");
        }
        this.status = ComplaintStatus.CLOSED;
        this.waitingFor = WaitingFor.NONE;
        this.closedAt = LocalDateTime.now();
    }

    public String getContent() {
        return initialMessage().getContent();
    }

    public String getMaskedContent() {
        return initialMessage().getMaskedContent();
    }

    public long getContentVersion() {
        return initialMessage().getContentVersion();
    }

    public ComplaintMessage initialMessage() {
        if (messages.isEmpty()) {
            throw new IllegalStateException("최초 민원 메시지가 없습니다.");
        }
        return messages.get(0);
    }
}
