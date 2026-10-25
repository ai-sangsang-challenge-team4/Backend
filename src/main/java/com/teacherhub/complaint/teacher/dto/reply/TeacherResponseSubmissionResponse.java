package com.teacherhub.complaint.teacher.dto.reply;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.WaitingFor;

import java.time.LocalDateTime;

// 최종 제출된 답변
public record TeacherResponseSubmissionResponse(
        Long responseId,
        Long complaintId,
        String content,
        ComplaintStatus complaintStatus,
        WaitingFor waitingFor,
        LocalDateTime sentAt
) {
    public static TeacherResponseSubmissionResponse from(ComplaintMessage response) {
        return new TeacherResponseSubmissionResponse(
                response.getId(),
                response.getComplaint().getId(),
                response.getContent(),
                response.getComplaint().getStatus(),
                response.getComplaint().getWaitingFor(),
                response.getSentAt()
        );
    }
}
