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


    // 초안일 때는 아직 담임교사를 지정하지 않으므로 nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;


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
            String content
    ) {

        this.parent = parent;
        this.student = student;
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
    public void updateContent(String content) {

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
    public void submit(
            Teacher teacher
    ) {

        if (this.status != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "이미 제출된 민원입니다."
            );
        }

        this.teacher = teacher;
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

    public void parentMessageSent() {
        if (status != ComplaintStatus.OPEN || waitingFor != WaitingFor.PARENT) {
            throw new IllegalStateException("학부모가 추가 문의를 작성할 차례가 아닙니다.");
        }
        this.waitingFor = WaitingFor.TEACHER;
    }

    public void close() {
        if (status != ComplaintStatus.OPEN || waitingFor != WaitingFor.PARENT) {
            throw new IllegalStateException("교사의 답변을 확인한 민원만 종료할 수 있습니다.");
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
