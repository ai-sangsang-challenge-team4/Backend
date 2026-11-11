package com.teacherhub.complaint.parent.dto.message;

import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.risk.dto.response.SubmissionAnalysisResult;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParentMessageResponse(
        Long messageId,
        Long complaintId,
        String content,
        MessageStatus status,
        WaitingFor waitingFor,
        long contentVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime sentAt,
        SubmissionAnalysisStatus aiAnalysisStatus,
        String aiAnalysisMessage,
        RiskSummaryResponse riskAnalysis
) {
    public ParentMessageResponse(
            Long messageId,
            Long complaintId,
            String content,
            MessageStatus status,
            WaitingFor waitingFor,
            long contentVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime sentAt
    ) {
        this(
                messageId,
                complaintId,
                content,
                status,
                waitingFor,
                contentVersion,
                createdAt,
                updatedAt,
                sentAt,
                null,
                null,
                null
        );
    }

    public static ParentMessageResponse from(ComplaintMessage message) {
        return new ParentMessageResponse(
                message.getId(),
                message.getComplaint().getId(),
                message.getContent(),
                message.getStatus(),
                message.getComplaint().getWaitingFor(),
                message.getContentVersion(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.getSentAt(),
                null,
                null,
                null
        );
    }

    public static ParentMessageResponse submitted(
            ComplaintMessage message,
            SubmissionAnalysisResult analysisResult
    ) {
        return new ParentMessageResponse(
                message.getId(),
                message.getComplaint().getId(),
                message.getContent(),
                message.getStatus(),
                message.getComplaint().getWaitingFor(),
                message.getContentVersion(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.getSentAt(),
                analysisResult.status(),
                analysisResult.status().userMessage(),
                analysisResult.summary()
        );
    }
}
