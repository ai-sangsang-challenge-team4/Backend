package com.teacherhub.complaint.teacher.dto;

import java.time.LocalDateTime;

public record TeacherOriginalViewResponse(
        Long complaintId,
        String content,
        LocalDateTime viewedAt
) {
}
