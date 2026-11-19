package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.teacher.dto.reply.TeacherAiDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseRequest;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseSubmissionResponse;
import com.teacherhub.complaint.teacher.service.TeacherResponseService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints")
@Tag(name = "Teacher Response", description = "교사 답변 작성 및 상담 완료 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "담당 교사가 아니거나 교사 권한이 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TeacherResponseController {

    private final TeacherResponseService teacherResponseService;
    private final UserRepository userRepository;

    @Operation(summary = "교사 AI 답변 초안 생성", description = "Low·Medium에서 FastAPI가 현재 대화 이력을 기반으로 답변 초안을 생성합니다. High는 제한형 공식 템플릿만 제공하고, Emergency 또는 safetyLock 상태에서는 답변 생성을 차단합니다.", responses = {
            @ApiResponse(responseCode = "201", description = "AI 답변 초안 생성 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "교사가 답변할 수 없는 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/response/ai-draft")
    public ResponseEntity<TeacherAiDraftResponse> generateAiDraft(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teacherResponseService.generateAiDraft(userId(authentication), complaintId));
    }

    @Operation(summary = "교사 답변 초안 조회", description = "현재 저장된 교사 답변 초안을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "답변 초안 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원 또는 답변 초안을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/response-draft")
    public ResponseEntity<TeacherResponseDraftResponse> findResponseDraft(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(teacherResponseService.findDraft(userId(authentication), complaintId));
    }

    @Operation(summary = "교사 답변 초안 저장", description = "교사 답변 초안을 생성하거나 수정합니다. 유효 위험도가 EMERGENCY이거나 safetyLock이 활성화되면 차단합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "답변 초안 저장 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "교사가 답변할 수 없는 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{complaintId}/response-draft")
    public ResponseEntity<TeacherResponseDraftResponse> saveResponseDraft(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Valid @RequestBody TeacherResponseRequest request) {
        return ResponseEntity.ok(teacherResponseService.saveDraft(userId(authentication), complaintId, request));
    }

    @Operation(summary = "교사 답변 최종 제출", description = "작성한 교사 답변을 학부모에게 전송하고 답변 차례를 학부모로 변경합니다. 답변만으로 상담이 자동 완료되지는 않으며, EMERGENCY 또는 safetyLock 상태에서는 차단합니다.", responses = {
            @ApiResponse(responseCode = "201", description = "교사 답변 제출 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "교사가 답변할 수 없는 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/responses")
    public ResponseEntity<TeacherResponseSubmissionResponse> submitResponse(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Parameter(description = "중복 전송 방지용 멱등 키", example = "response-42-20261029-001")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TeacherResponseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teacherResponseService.submit(
                        userId(authentication), complaintId, request, idempotencyKey));
    }

    @Operation(summary = "교사 상담 완료 처리", description = "교사가 답변을 전송한 뒤 학부모 응답 대기 중인 문의를 완료 처리합니다. 답변 전송만으로는 자동 완료되지 않습니다.", responses = {
            @ApiResponse(responseCode = "200", description = "상담 완료 처리 성공"),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "RESPONSE_NOT_EDITABLE: 이미 완료되었거나 진행 중이 아닌 민원, 교사 답변 전송 전, Emergency 위험도 또는 안전 잠금 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/close")
    public ResponseEntity<Map<String, String>> closeComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        teacherResponseService.close(userId(authentication), complaintId);
        return ResponseEntity.ok(Map.of("message", "상담이 완료 처리되었습니다."));
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
