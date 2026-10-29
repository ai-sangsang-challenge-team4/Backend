package com.teacherhub.complaint.entity;

import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "complaint_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_complaint_message_sender_idempotency",
                columnNames = {"sender_id", "idempotency_key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplaintMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    // 메시지 작성자 (학부모 또는 교사)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // 메시지 유형 (학부모 문의, 교사 답변)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageType type;

    // 메시지 상태 (작성 중, 전송됨, 읽음)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    // 메시지 내용
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 마스킹 된 메세지
    @Column(columnDefinition = "TEXT")
    private String maskedContent;

    @Column(nullable = false)
    private long contentVersion = 1;

    private LocalDateTime sentAt;
    private LocalDateTime readAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ComplaintMessage(
            Complaint complaint,
            User sender,
            MessageType type,
            String content
    ) {
        this.complaint = complaint;
        this.sender = sender;
        this.type = type;
        this.content = content;
        this.status = MessageStatus.DRAFT;
    }

    public void updateDraft(String content) {
        requireDraft();
        if (Objects.equals(this.content, content)) {
            return;
        }
        this.content = content;
        this.maskedContent = null;
        this.contentVersion++;
    }

    public void updateMaskedContent(String maskedContent) {
        requireDraft();
        this.maskedContent = maskedContent;
    }

    public void send() {
        requireDraft();
        this.status = MessageStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void assignIdempotencyKey(String idempotencyKey) {
        requireDraft();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등 키가 필요합니다.");
        }
        this.idempotencyKey = idempotencyKey;
    }

    public boolean markRead() {
        if (status == MessageStatus.DRAFT) {
            throw new IllegalStateException("전송되지 않은 메시지는 읽음 처리할 수 없습니다.");
        }
        if (status == MessageStatus.READ) {
            return false;
        }
        this.status = MessageStatus.READ;
        this.readAt = LocalDateTime.now();
        return true;
    }

    public boolean isDraft() {
        return status == MessageStatus.DRAFT;
    }

    public boolean isSent() {
        return status == MessageStatus.SENT || status == MessageStatus.READ;
    }

    private void requireDraft() {
        if (status != MessageStatus.DRAFT) {
            throw new IllegalStateException("작성 중인 메시지만 수정할 수 있습니다.");
        }
    }
}
