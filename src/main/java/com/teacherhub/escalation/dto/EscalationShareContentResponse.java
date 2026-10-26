package com.teacherhub.escalation.dto;

import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.user.enums.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record EscalationShareContentResponse(
        Long escalationId,
        Long complaintId,
        Long adminId,
        Set<EscalationScope> scopes,
        String reason,
        List<SharedMessage> maskedMessages,
        List<SharedMessage> originalMessages,
        boolean mediationPackageIncluded
) {
    public static EscalationShareContentResponse from(
            ComplaintEscalation escalation,
            List<ComplaintMessage> messages,
            Function<ComplaintMessage, String> maskingContent
    ) {
        Set<EscalationScope> scopes = Set.copyOf(escalation.getScopes());
        List<SharedMessage> maskedMessages = scopes.contains(EscalationScope.MASKING_MESSAGES)
                ? messages.stream()
                .map(message -> SharedMessage.from(message, maskingContent.apply(message), true))
                .toList()
                : List.of();
        List<SharedMessage> originalMessages = scopes.contains(EscalationScope.ORIGINAL_MESSAGES)
                ? messages.stream()
                .map(message -> SharedMessage.from(message, message.getContent(), false))
                .toList()
                : List.of();

        return new EscalationShareContentResponse(
                escalation.getId(),
                escalation.getComplaint().getId(),
                escalation.getAssignedAdmin().getId(),
                scopes,
                escalation.getReason(),
                maskedMessages,
                originalMessages,
                scopes.contains(EscalationScope.MEDIATION_PACKAGE)
        );
    }

    public record SharedMessage(
            Long messageId,
            UserRole senderRole,
            String senderName,
            String content,
            LocalDateTime sentAt,
            boolean masked
    ) {
        private static SharedMessage from(
                ComplaintMessage message,
                String content,
                boolean masked
        ) {
            return new SharedMessage(
                    message.getId(),
                    message.getType() == MessageType.PARENT_INQUIRY
                            ? UserRole.PARENT
                            : UserRole.TEACHER,
                    message.getSender().getName(),
                    content,
                    message.getSentAt(),
                    masked
            );
        }
    }
}
