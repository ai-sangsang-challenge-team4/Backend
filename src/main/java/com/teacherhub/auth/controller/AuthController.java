package com.teacherhub.auth.controller;

import com.teacherhub.auth.dto.SignupRequest;
import com.teacherhub.auth.dto.SignupResponse;
import com.teacherhub.auth.dto.LoginRequest;
import com.teacherhub.auth.dto.LoginResponse;
import com.teacherhub.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Auth",
        description = "회원가입 및 로그인 API"
)
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "회원가입",
            description = "이메일, 비밀번호, 이름, 사용자 역할을 입력하여 회원가입을 진행합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 사용 중인 이메일 또는 교직원 번호"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류"
            )
    })
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {

        SignupResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @Operation(
        summary = "로그인",
        description = "이메일과 비밀번호를 확인하고 인증 토큰과 사용자 정보를 반환합니다."
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "로그인 성공"
        ),
        @ApiResponse(
                responseCode = "400",
                description = "이메일 또는 비밀번호 형식 오류"
        ),
        @ApiResponse(
                responseCode = "401",
                description = "이메일 또는 비밀번호 불일치"
        ),
        @ApiResponse(
                responseCode = "403",
                description = "비활성화되거나 이용할 수 없는 계정"
        )
})
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request
) {
    return ResponseEntity.ok(authService.login(request));
}
}