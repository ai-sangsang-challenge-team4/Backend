package com.teacherhub.risk.controller;

import com.teacherhub.risk.dto.RiskTagListResponse;
import com.teacherhub.risk.service.RiskTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;

@Tag(
        name = "Risk Tag",
        description = "위험 요소 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/risk-tags")
@RequiredArgsConstructor
public class RiskTagController {

    private final RiskTagService riskTagService;

    @Operation(
            summary = "위험 요소 목록 조회",
            description = "시스템에서 사용하는 위험 요소 기준 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "위험 요소 목록 조회 성공"
            )
    })
    @GetMapping
    public ResponseEntity<List<RiskTagListResponse>> getRiskTags() {
        return ResponseEntity.ok(
                riskTagService.getAllRiskTags()
        );
    }
}