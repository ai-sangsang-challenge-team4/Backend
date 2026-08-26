package com.teacherhub.auth.dto;

import com.teacherhub.user.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하로 입력해주세요.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하로 입력해주세요.")
        String name,

        @NotNull(message = "사용자 역할은 필수입니다.")
        UserRole role,

        @Size(max = 100, message = "교직원 번호는 100자 이하로 입력해주세요.")
        String employeeNo

) {
}