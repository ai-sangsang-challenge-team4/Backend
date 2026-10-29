package com.teacherhub.complaint.teacher.dto.query;

import java.time.LocalDateTime;

// 원문보기
public record TeacherOriginalViewResponse(
        Long complaintId,
        Long messageId,
        String content,
        LocalDateTime viewedAt
) {
}
