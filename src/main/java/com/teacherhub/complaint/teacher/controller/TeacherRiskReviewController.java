package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.risk.dto.response.teacher.TeacherRiskResponse;
import com.teacherhub.risk.dto.review.TeacherRiskReviewRequest;
import com.teacherhub.risk.dto.review.TeacherRiskReviewResponse;
import com.teacherhub.risk.service.TeacherRiskQueryService;
import com.teacherhub.risk.service.TeacherRiskReviewService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints")
@Tag(name = "Teacher Risk Review", description = "교사 위험 분석 조회 및 검토 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "담당 교사가 아니거나 교사 권한이 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TeacherRiskReviewController {

    private final TeacherRiskQueryService teacherRiskQueryService;
    private final TeacherRiskReviewService teacherRiskReviewService;
    private final UserRepository userRepository;

    @Operation(summary = "민원 위험 분석 조회", description = "시스템 위험도, 교사 검토값과 감지된 위험 태그를 조회합니다. 완료된 위험 분석이 없으면 분석 관련 필드는 null, detectedFactors는 빈 배열로 반환합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "위험 분석 조회 성공. 분석이 없으면 nullable 필드와 빈 detectedFactors를 반환", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/risk")
    public ResponseEntity<TeacherRiskResponse> findRisk(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(teacherRiskQueryService.findRisk(userId(authentication), complaintId));
    }

    @Operation(summary = "교사 위험도 검토", description = "교사가 1~7점 위험도를 검토합니다. EMERGENCY 변경은 관리자 승인이 필요할 수 있습니다.", responses = {
            @ApiResponse(responseCode = "201", description = "위험도 검토 저장 또는 관리자 승인 요청 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "점수·사유 입력값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "민원 또는 위험 분석 결과를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "검토할 수 없는 상태 또는 관리자 공유 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/risk-reviews")
    public ResponseEntity<TeacherRiskReviewResponse> reviewRisk(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Valid @RequestBody TeacherRiskReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teacherRiskReviewService.review(userId(authentication), complaintId, request));
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
