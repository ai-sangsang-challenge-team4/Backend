package com.teacherhub.user.dto;

import com.teacherhub.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "마이페이지 사용자 정보")
public record UserInfoResponse(
        @Schema(description = "사용자 ID", example = "7")
        Long userId,
        @Schema(description = "로그인 이메일", example = "parent@example.com")
        String email,
        @Schema(description = "사용자 이름", example = "김학부모")
        String name,
        @Schema(description = "사용자 역할", example = "PARENT")
        UserRole role,
        @Schema(description = "연결된 자녀 목록. 학부모가 아니면 빈 배열")
        List<MyStudentResponse> students
) {
}
