package com.teacherhub.risk.controller;

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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@Tag(name = "Risk Analysis", description = "민원 마스킹, 위험 분석 및 수정안 생성")
@SecurityRequirement(name = "bearerAuth")
public class RiskAnalysisController {

    private final ComplaintMaskingService maskingService;
    private final RiskAnalysisService analysisService;
    private final RiskAnalysisQueryService queryService;
    private final UserRepository users;

    @PostMapping("/complaints/{complaintId}/mask")
    @Operation(summary = "규칙 기반 개인정보 마스킹",
            description = "저장된 원문에서 전화번호·이메일·주민등록번호 형태를 마스킹합니다. 이름·주소의 완전한 비식별화는 지원하지 않습니다.")

    public MaskingResponse mask(Authentication authentication,
                                @PathVariable Long complaintId) {
        return maskingService.mask(userId(authentication), complaintId);
    }

    @PostMapping("/complaints/{complaintId}/risk-analysis")
    @Operation(summary = "최종 위험 분석 및 수정안 생성",
            description = "마스킹, 룰·LLM 탐지, 병합, 점수 계산·저장 후 수정안을 생성·저장하고 위험 점수·등급만 반환합니다. 근거와 수정 제안은 findings에서 조회합니다. 현재 탐지기와 수정안 생성기는 Mock입니다.")

    public RiskSummaryResponse analyze(Authentication authentication,
                                       @PathVariable Long complaintId) {
        return analysisService.analyze(userId(authentication), complaintId);
    }

    @GetMapping("/risk-analyses/{analysisId}/findings")
    @Operation(summary = "감지된 위험 태그의 근거 및 수정 제안 조회")
    public RiskRevisionResponse findFindings(Authentication authentication, @PathVariable Long analysisId) {
        return queryService.findFindings(userId(authentication), analysisId);
    }

    @GetMapping("/risk-analyses/{analysisId}/detector-results")
    @Operation(summary = "태그별 Rule 및 LLM 상세 탐지 결과 조회")
    public RiskDetectorResponse findDetectorResults(Authentication authentication,
                                                    @PathVariable Long analysisId) {
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
