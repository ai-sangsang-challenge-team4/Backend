package com.teacherhub.escalation.dto;

import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;

import java.util.List;

// 관리자 공유 내용 탭
public record EscalationShareContentResponse(
        String reason,
        List<TeacherConversationResponse.Message> maskedMessages,
        List<TeacherConversationResponse.Message> originalMessages,
        boolean mediationPackageIncluded
) {
}
