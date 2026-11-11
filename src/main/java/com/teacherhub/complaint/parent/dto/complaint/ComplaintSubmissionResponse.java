package com.teacherhub.complaint.parent.dto.complaint;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.risk.dto.response.SubmissionAnalysisResult;

public record ComplaintSubmissionResponse(
        Long complaintId,
        ComplaintStatus status,
        WaitingFor waitingFor,
        SubmissionAnalysisStatus submissionAnalysisStatus,
        String aiAnalysisMessage,
        RiskSummaryResponse riskAnalysis
) {
    public ComplaintSubmissionResponse(
            Long complaintId,
            ComplaintStatus status,
            WaitingFor waitingFor,
            SubmissionAnalysisStatus submissionAnalysisStatus,
            String aiAnalysisMessage
    ) {
        this(complaintId, status, waitingFor, submissionAnalysisStatus, aiAnalysisMessage, null);
    }

    public static ComplaintSubmissionResponse from(
            Complaint complaint,
            SubmissionAnalysisResult analysisResult
    ) {
        return new ComplaintSubmissionResponse(
                complaint.getId(),
                complaint.getStatus(),
                complaint.getWaitingFor(),
                analysisResult.status(),
                analysisResult.status().userMessage(),
                analysisResult.summary()
        );
    }
}
