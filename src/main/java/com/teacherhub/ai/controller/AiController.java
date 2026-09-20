package com.teacherhub.ai.controller;

import com.teacherhub.ai.dto.LlmRequest;
import com.teacherhub.ai.dto.LlmResponse;
import com.teacherhub.ai.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
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
public class AiController {

    private final LlmService llmService;

    @Operation(
            summary = "LLM 분석",
            description = "민원 내용을 LLM에 전달하여 AI 분석 결과를 반환합니다."
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