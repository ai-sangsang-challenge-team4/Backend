package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.teacherhub.complaint.teacher.dto.AllowedAction;
import com.teacherhub.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

// 교사용 대화 탭 읽기 응답
public record TeacherConversationResponse(
        Long complaintId,
        @Schema(description = "위험도별 허용 행동. 완료된 분석이 없으면 REQUEST_MANUAL_REVIEW")
        List<AllowedAction> allowedActions,
        List<Message> messages
) {
    public static TeacherConversationResponse from(
            Complaint complaint,
            Map<Long, BufferedSummary> bufferedSummaries,
            List<AllowedAction> allowedActions,
            LocalDateTime submittedAt,
            List<ComplaintMessage> sourceMessages
    ) {
        List<Message> messages = sourceMessages.stream()
                .map(message -> {
                    BufferedSummary summary = bufferedSummaries.get(message.getId());
                    return Message.from(
                            message,
                            summary == null ? message.getContent() : summary.content(),
                            submittedAt,
                            summary
                    );
                })
                .sorted(Comparator.comparing(Message::sentAt))
                .toList();

        return new TeacherConversationResponse(complaint.getId(), allowedActions, messages);
    }

    public enum SummaryStatus {
        GENERATED,
        UNAVAILABLE
    }

    public record BufferedSummary(String content, SummaryStatus status) {
        public static BufferedSummary generated(String content) {
            return new BufferedSummary(content, SummaryStatus.GENERATED);
        }

        public static BufferedSummary unavailable() {
            return new BufferedSummary(
                    "완충 요약을 생성하지 못했습니다. 원문 열람 버튼을 사용해주세요.",
                    SummaryStatus.UNAVAILABLE
            );
        }
    }

    @Schema(name = "TeacherConversationMessage", description = "교사·관리자용 민원 대화 메시지")
    public record Message(
            String messageId,
            UserRole senderRole,
            String senderName,
            String content,
            MessageStatus status,
            LocalDateTime sentAt,
            LocalDateTime readAt,
            boolean summarized,
            SummaryStatus summaryStatus
    ) {
        public Message(
                String messageId,
                UserRole senderRole,
                String senderName,
                String content,
                MessageStatus status,
                LocalDateTime sentAt,
                LocalDateTime readAt,
                boolean summarized
        ) {
            this(
                    messageId,
                    senderRole,
                    senderName,
                    content,
                    status,
                    sentAt,
                    readAt,
                    summarized,
                    summarized ? SummaryStatus.GENERATED : null
            );
        }

        public static Message from(
                ComplaintMessage message,
                String content,
                LocalDateTime initialSubmittedAt,
                BufferedSummary summary
        ) {
            return new Message(
                    "message:" + message.getId(),
                    message.getType() == MessageType.PARENT_INQUIRY
                            ? UserRole.PARENT
                            : UserRole.TEACHER,
                    message.getSender().getName(),
                    content,
                    message.getStatus(),
                    message.getSentAt() == null
                            ? initialSubmittedAt
                            : message.getSentAt(),
                    message.getReadAt(),
                    summary != null && summary.status() == SummaryStatus.GENERATED,
                    summary == null ? null : summary.status()
            );
        }
    }
}
