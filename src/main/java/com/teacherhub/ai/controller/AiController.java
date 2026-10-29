package com.teacherhub.ai.controller;

import com.teacherhub.ai.dto.LlmRequest;
import com.teacherhub.ai.dto.LlmResponse;
import com.teacherhub.ai.service.LlmService;
import com.teacherhub.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "AI",
        description = "LLM 기반 AI API"
)
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "AI 처리 또는 예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AiController {

    private final LlmService llmService;

    @Operation(
            summary = "LLM 분석",
            description = "민원 내용의 개인정보를 마스킹한 뒤 LLM에 전달하여 AI 분석 결과를 반환합니다. 현재 Mock 응답을 사용합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "LLM 분석 성공", useReturnTypeSchema = true),
                    @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping("/analyze")
    public ResponseEntity<LlmResponse> analyze(
            @Valid @RequestBody LlmRequest request
    ) {
        return ResponseEntity.ok(
                llmService.generate(request.content())
        );
    }
}
