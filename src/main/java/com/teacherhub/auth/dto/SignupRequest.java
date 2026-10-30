package com.teacherhub.auth.dto;

import com.teacherhub.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignupRequest(

        @Schema(description = "학교 ID", example = "1")
        @NotNull(message = "학교 선택은 필수입니다.")
        Long schoolId,

        @Schema(description = "로그인 이메일", example = "parent@example.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @Schema(description = "비밀번호(8~100자)", example = "password123!")
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하로 입력해주세요.")
        String password,

        @Schema(description = "사용자 이름", example = "김학부모")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하로 입력해주세요.")
        String name,

        @Schema(description = "사용자 역할", example = "PARENT")
        @NotNull(message = "사용자 역할은 필수입니다.")
        UserRole role,

        @Schema(description = "교직원 번호. TEACHER 역할일 때 필수", example = "T-2026-001", nullable = true)
        @Size(max = 100, message = "교직원 번호는 100자 이하로 입력해주세요.")
        String employeeNo,

        @Schema(description = "학급 ID. PARENT 역할일 때 필수", example = "1", nullable = true)
        Long schoolClassId,

        @Schema(description = "학생 ID. PARENT 역할일 때 필수", example = "1", nullable = true)
        Long studentId,

        @Schema(description = "자녀 이름. PARENT 역할일 때 필수", example = "김학생", nullable = true)
        @Size(max = 50, message = "자녀 이름은 50자 이하로 입력해주세요.")
        String studentName,

        @NotNull(message = "필수 정책 동의 여부가 필요합니다.")
        @AssertTrue(message = "필수 정책에 동의해야 회원가입할 수 있습니다.")
        @Schema(description = "이용약관·개인정보 처리 등 필수 정책 동의 여부", example = "true")
        Boolean requiredAgreed,

        @NotNull(message = "선택 정책 동의 여부가 필요합니다.")
        @Schema(description = "마케팅·알림 등 선택 정책 동의 여부", example = "false")
        Boolean optionalAgreed

) {
}
