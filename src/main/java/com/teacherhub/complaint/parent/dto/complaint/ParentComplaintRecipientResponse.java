package com.teacherhub.complaint.parent.dto.complaint;

import com.teacherhub.user.entity.Teacher;

// 교사 정보
public record ParentComplaintRecipientResponse(
        Long teacherId,
        String teacherName,
        // 담임교사 유무
        boolean homeroomTeacher
) {
    public static ParentComplaintRecipientResponse from(Teacher teacher, Teacher homeroomTeacher) {
        return new ParentComplaintRecipientResponse(
                teacher.getId(),
                teacher.getName(),
                teacher.getId().equals(homeroomTeacher.getId())
        );
    }
}
