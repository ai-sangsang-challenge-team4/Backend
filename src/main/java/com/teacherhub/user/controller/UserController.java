package com.teacherhub.user.controller;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.user.dto.UserInfoResponse;
import com.teacherhub.user.consent.dto.PolicyConsentRequest;
import com.teacherhub.user.consent.dto.PolicyConsentResponse;
import com.teacherhub.user.consent.service.PolicyConsentService;
import com.teacherhub.user.service.UserService;
import com.teacherhub.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private final UserRepository userRepository;

    @Operation(
            summary = "내 계정 정보 조회",
            description = "로그인한 사용자의 계정 정보와 연결된 자녀 목록을 함께 조회합니다. 학부모가 아니면 students는 빈 배열입니다."
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
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                userService.getMyInfo(userId(authentication))
        );
    }

    @Operation(
            summary = "현재 정책 동의 저장",
            description = "필수·선택 동의 여부를 현재 정책 버전과 함께 저장합니다. 필수 동의는 true여야 하며 선택 동의는 변경할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정책 동의 저장 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 정책에 동의하지 않았거나 요청값이 잘못됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/me/policy-consents")
    public ResponseEntity<PolicyConsentResponse> agreeToCurrentPolicy(
            Authentication authentication,
            @Valid @RequestBody PolicyConsentRequest request
    ) {
        return ResponseEntity.ok(policyConsentService.saveCurrent(userId(authentication), request));
    }

    @Operation(
            summary = "현재 정책 동의 조회",
            description = "현재 정책 버전의 필수·선택 동의 여부를 조회합니다. 동의 기록이 없으면 두 값은 false입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정책 동의 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })

    // 정책 동의 조회
    @GetMapping("/me/policy-consents")
    public ResponseEntity<PolicyConsentResponse> findCurrentPolicyConsent(
            Authentication authentication
    ) {
        return ResponseEntity.ok(policyConsentService.findCurrent(userId(authentication)));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."))
                .getId();
    }
}
