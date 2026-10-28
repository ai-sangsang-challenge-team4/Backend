package com.teacherhub.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Tag(name = "System", description = "서비스 상태 확인 API")
public class HealthController {

    @Operation(
            summary = "서비스 상태 확인",
            description = "애플리케이션 프로세스가 요청을 처리할 수 있는지 확인합니다.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "서비스 정상",
                    useReturnTypeSchema = true
            )
    )
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", LocalDateTime.now()));
    }

    @Schema(description = "서비스 상태 응답")
    public record HealthResponse(
            @Schema(description = "서비스 상태", example = "UP")
            String status,
            @Schema(description = "상태 확인 시각", example = "2026-10-28T12:30:00")
            LocalDateTime timestamp
    ) {
    }
}
