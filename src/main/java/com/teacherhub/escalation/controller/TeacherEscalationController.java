package com.teacherhub.escalation.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.escalation.dto.EscalationStateResponse;
import com.teacherhub.escalation.dto.ShareEscalationRequest;
import com.teacherhub.escalation.service.TeacherEscalationService;
import com.teacherhub.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints/{complaintId}/escalation")
@Tag(name = "Teacher Escalation", description = "교사의 EMERGENCY 민원 관리자 공유 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "담당 교사가 아니거나 교사 권한이 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TeacherEscalationController {

    private final TeacherEscalationService escalationService;
    private final UserRepository userRepository;

    // 관리자에게 공유
    @Operation(summary = "EMERGENCY 민원 관리자 공유", description = "EMERGENCY 민원을 지정 관리자에게 선택한 범위와 사유로 공유합니다.", responses = {
            @ApiResponse(responseCode = "201", description = "관리자 공유 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "공유 사유 또는 범위 입력값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "민원, 위험 분석 또는 관리자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "EMERGENCY가 아니거나 이미 다른 내용으로 공유됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<String> share(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Valid @RequestBody ShareEscalationRequest request
    ) {
        escalationService.share(userId(authentication), complaintId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("공유 요청이 성공적으로 전송되었습니다.");
    }

    // 공유 상태 조회
    @Operation(summary = "관리자 공유 상태 조회", description = "민원의 관리자 공유 여부, 처리 상태를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "공유 상태 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<EscalationStateResponse> findStatus(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(escalationService.findStatus(userId(authentication), complaintId));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("인증 정보가 없습니다.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."))
                .getId();
    }
}
