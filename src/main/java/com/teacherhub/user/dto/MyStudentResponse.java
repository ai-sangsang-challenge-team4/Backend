package com.teacherhub.user.dto;

import com.teacherhub.user.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인한 학부모에게 연결된 자녀")
public record MyStudentResponse(
        @Schema(description = "문의 작성에 사용하는 학생 ID", example = "1")
        Long studentId,
        @Schema(description = "자녀 이름", example = "김학생")
        String name,
        @Schema(description = "학교 내 학생 번호", example = "7")
        String studentNo
) {
    public static MyStudentResponse from(Student student) {
        return new MyStudentResponse(student.getId(), student.getName(), student.getStudentNo());
    }
}
