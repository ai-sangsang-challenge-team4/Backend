package com.teacherhub.user.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.user.dto.UserInfoResponse;
import com.teacherhub.user.consent.dto.PolicyConsentRequest;
import com.teacherhub.user.consent.dto.PolicyConsentResponse;
import com.teacherhub.user.consent.service.PolicyConsentService;
import com.teacherhub.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@Tag(
        name = "User",
        description = "사용자 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PolicyConsentService policyConsentService;

    @Operation(
            summary = "내 계정 정보 조회",
            description = "로그인한 사용자의 계정 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "내 계정 정보 조회 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getMyInfo(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(
                userService.getMyInfo(userId)
        );
    }

    @Operation(
            summary = "현재 정책 전체 동의 저장",
            description = "서버에 설정된 현재 정책 버전과 동의 시점을 저장합니다. 같은 버전에 대한 재요청은 기존 동의 기록을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정책 동의 저장 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "전체 동의하지 않았거나 요청값이 잘못됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/me/policy-consents")
    public ResponseEntity<PolicyConsentResponse> agreeToCurrentPolicy(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PolicyConsentRequest request
    ) {
        return ResponseEntity.ok(policyConsentService.agreeAll(userId));
    }
}
