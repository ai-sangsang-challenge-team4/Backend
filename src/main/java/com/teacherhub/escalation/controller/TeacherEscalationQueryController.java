package com.teacherhub.escalation.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.escalation.dto.TeacherEscalationListQuery;
import com.teacherhub.escalation.dto.TeacherEscalationPageResponse;
import com.teacherhub.escalation.dto.TeacherEscalationSummaryResponse;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/escalations")
@Tag(name = "Teacher Escalation", description = "교사의 EMERGENCY 민원 관리자 공유 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "교사 권한이 없거나 본인이 공유하지 않은 건",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TeacherEscalationQueryController {

    private final TeacherEscalationService escalationService;
    private final UserRepository userRepository;

    @Operation(summary = "교사 관리자 공유 내역 조회", description = "로그인한 교사가 직접 공유한 건을 검색 조건과 페이지 단위로 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "공유 내역 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "조회 조건 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<TeacherEscalationPageResponse> findAll(
            @Parameter(hidden = true) Authentication authentication,
            @ParameterObject @Valid @ModelAttribute TeacherEscalationListQuery query
    ) {
        return ResponseEntity.ok(escalationService.findAll(userId(authentication), query));
    }

    @Operation(summary = "교사 관리자 공유 건 조회", description = "로그인한 교사가 직접 공유한 건의 요약과 현재 처리 상태를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "공유 건 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "공유 건을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{escalationId}")
    public ResponseEntity<TeacherEscalationSummaryResponse> findOne(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "관리자 공유 건 ID", example = "1") @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(escalationService.findOne(userId(authentication), escalationId));
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
