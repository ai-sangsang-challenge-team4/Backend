package com.teacherhub.escalation.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.escalation.dto.EscalationMessageRequest;
import com.teacherhub.escalation.dto.EscalationStateResponse;
import com.teacherhub.escalation.dto.AdminEscalationSummaryResponse;
import com.teacherhub.escalation.dto.EscalationShareContentResponse;
import com.teacherhub.escalation.service.AdminEscalationService;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.enums.RiskLevel;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/escalations")
@Tag(name = "Admin Escalation", description = "관리자 공유 건 조회, 검토 및 완료 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "관리자 권한이 없거나 배정받지 않은 공유 건",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AdminEscalationController {

    private final AdminEscalationService escalationService;
    private final UserRepository userRepository;

    // 공유 받은 메세지 목록 조회
    @Operation(summary = "관리자 공유 건 목록 조회", description = "로그인한 관리자에게 배정된 공유 건을 상태와 위험도로 필터링합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "공유 건 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필터 파라미터 값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<AdminEscalationSummaryResponse>> findAll(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "공유 처리 상태") @RequestParam(required = false) EscalationStatus status,
            @Parameter(description = "현재 위험 등급") @RequestParam(required = false) RiskLevel riskLevel
    ) {
        return ResponseEntity.ok(escalationService.findAll(
                userId(authentication),
                status,
                riskLevel
        ));
    }

    // 공유 메세지 상세 조회 (상단 정보, 처리 상태)
    @Operation(summary = "관리자 공유 건 상세 조회", description = "공유 건의 상단 요약과 현재 처리 상태를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "공유 건 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "공유 건을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{escalationId}")
    public ResponseEntity<AdminEscalationSummaryResponse> findOne(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "관리자 공유 건 ID", example = "1") @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(escalationService.findOne(userId(authentication), escalationId));
    }

    // 공유 내용 탭 조회
    @Operation(summary = "관리자 공유 메시지 조회", description = "교사가 허용한 범위에 따라 마스킹 메시지, 원문 메시지 및 패키지 포함 여부를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "공유 메시지 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "공유 건을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{escalationId}/messages")
    public ResponseEntity<EscalationShareContentResponse> findMessages(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "관리자 공유 건 ID", example = "1") @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(
                escalationService.findMessages(userId(authentication), escalationId)
        );
    }

    // 위험 분석 탭 조회
    @Operation(summary = "관리자 위험 분석 조회", description = "공유된 민원의 위험 분석 결과와 교사 검토값을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "위험 분석 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "공유 건을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{escalationId}/risk")
    public ResponseEntity<TeacherRiskResponse> findRisk(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "관리자 공유 건 ID", example = "1") @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(
                escalationService.findRisk(userId(authentication), escalationId)
        );
    }

    // 처리 기록 탭 조회
    @Operation(summary = "관리자 처리 이력 조회", description = "공유 민원의 상태 변경, 위험도 검토 및 관리자 처리 이벤트를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "처리 이력 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "공유 건을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{escalationId}/history")
    public ResponseEntity<TeacherHistoryResponse> findHistory(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "관리자 공유 건 ID", example = "1") @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(
                escalationService.findHistory(userId(authentication), escalationId)
        );
    }

    // 관리자 검토 시작 (검토 메세지 작성)
    @Operation(summary = "관리자 검토 시작", description = "SHARED 상태의 공유 건을 UNDER_REVIEW로 변경하고 검토 메모를 저장합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "관리자 검토 시작 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "검토 메시지 입력값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "공유 건을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "검토 시작이 가능한 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{escalationId}/review")
    public ResponseEntity<EscalationStateResponse> startReview(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "관리자 공유 건 ID", example = "1") @PathVariable Long escalationId,
            @Valid @RequestBody EscalationMessageRequest request
    ) {
        return ResponseEntity.ok(
                escalationService.startReview(userId(authentication), escalationId, request)
        );
    }

    // 관리자 검토 완료
    @Operation(summary = "관리자 검토 완료", description = "검토 결과를 저장하고 공유 건을 COMPLETED 상태로 변경합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "관리자 검토 완료 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "처리 결과 입력값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "공유 건을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "완료 가능한 상태가 아니거나 Emergency 재검토 승인 대기 중",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{escalationId}/complete")
    public ResponseEntity<EscalationStateResponse> complete(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "관리자 공유 건 ID", example = "1") @PathVariable Long escalationId,
            @Valid @RequestBody EscalationMessageRequest request
    ) {
        return ResponseEntity.ok(
                escalationService.complete(userId(authentication), escalationId, request)
        );
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
