package com.teacherhub.risk.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.risk.service.RiskAnalysisService;
import com.teacherhub.risk.service.RiskAnalysisQueryService;
import com.teacherhub.risk.dto.response.RiskRevisionResponse;
import com.teacherhub.risk.dto.response.MaskingResponse;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.risk.dto.response.RiskDetectorResponse;
import com.teacherhub.risk.masking.ComplaintMaskingService;
import com.teacherhub.user.repository.UserRepository;
import com.teacherhub.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@Tag(name = "Risk Analysis", description = "민원 마스킹, 위험 분석 및 수정안 생성")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "민원 또는 분석 결과 접근 권한 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class RiskAnalysisController {

    private final ComplaintMaskingService maskingService;
    private final RiskAnalysisService analysisService;
    private final RiskAnalysisQueryService queryService;
    private final UserRepository users;

    @PostMapping("/complaints/{complaintId}/mask")
    @Operation(summary = "규칙 기반 개인정보 마스킹",
            description = "저장된 원문에서 전화번호·이메일·주민등록번호 형태를 마스킹합니다. 이름·주소의 완전한 비식별화는 지원하지 않습니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "마스킹 성공", useReturnTypeSchema = true),
                    @ApiResponse(responseCode = "400", description = "마스킹할 내용이 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "마스킹 가능한 초안 상태가 아님",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })

    public MaskingResponse mask(@Parameter(hidden = true) Authentication authentication,
                                @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return maskingService.mask(userId(authentication), complaintId);
    }

    @PostMapping("/complaints/{complaintId}/risk-analysis")
    @Operation(summary = "최종 위험 분석 및 수정안 생성",
            description = "마스킹, 룰·LLM 탐지, 병합, 점수 계산·저장 후 수정안을 생성·저장하고 위험 점수·등급만 반환합니다. 근거와 수정 제안은 findings에서 조회합니다. 현재 탐지기와 수정안 생성기는 Mock입니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "위험 분석 성공", useReturnTypeSchema = true),
                    @ApiResponse(responseCode = "400", description = "분석할 내용이 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "분석 가능 상태가 아니거나 재분석 제한 초과",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })

    public RiskSummaryResponse analyze(@Parameter(hidden = true) Authentication authentication,
                                       @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return analysisService.analyze(userId(authentication), complaintId);
    }

    @PostMapping("/complaints/{complaintId}/messages/{messageId}/risk-analysis")
    @Operation(summary = "학부모 추가 문의 위험 분석 및 수정안 생성", responses = {
            @ApiResponse(responseCode = "200", description = "추가 문의 위험 분석 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "분석할 내용이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "민원 또는 메시지 초안을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "분석 가능 상태가 아니거나 재분석 제한 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RiskSummaryResponse analyzeMessage(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Parameter(description = "추가 문의 메시지 ID", example = "101") @PathVariable Long messageId
    ) {
        return analysisService.analyzeMessage(userId(authentication), complaintId, messageId);
    }

    @GetMapping("/risk-analyses/{analysisId}/findings")
    @Operation(summary = "감지된 위험 태그의 근거 및 수정 제안 조회", responses = {
            @ApiResponse(responseCode = "200", description = "위험 근거 및 수정 제안 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "완료된 위험 분석 결과를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RiskRevisionResponse findFindings(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "위험 분석 ID", example = "20") @PathVariable Long analysisId
    ) {
        return queryService.findFindings(userId(authentication), analysisId);
    }

    @GetMapping("/risk-analyses/{analysisId}/detector-results")
    @Operation(summary = "태그별 Rule 및 LLM 상세 탐지 결과 조회", responses = {
            @ApiResponse(responseCode = "200", description = "탐지기 결과 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "완료된 위험 분석 결과를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RiskDetectorResponse findDetectorResults(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "위험 분석 ID", example = "20") @PathVariable Long analysisId
    ) {
        return queryService.findDetectorResults(userId(authentication), analysisId);
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."))
                .getId();
    }
}
