package com.teacherhub.complaint.teacher.dto;

import java.time.LocalDateTime;

// 원문보기
public record TeacherOriginalViewResponse(
        Long complaintId,
        String content,
        LocalDateTime viewedAt
) {
}
