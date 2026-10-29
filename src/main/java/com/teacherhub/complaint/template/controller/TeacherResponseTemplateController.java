package com.teacherhub.complaint.template.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.template.dto.ResponseTemplateAdoptionRequest;
import com.teacherhub.complaint.template.dto.ResponseTemplateAdoptionResponse;
import com.teacherhub.complaint.template.dto.ResponseTemplateResponse;
import com.teacherhub.complaint.template.service.TeacherResponseTemplateService;
import com.teacherhub.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints/{complaintId}/response-templates")
@Tag(name = "Teacher Response Template", description = "High 제한형 답변 템플릿 API")
@SecurityRequirement(name = "bearerAuth")
public class TeacherResponseTemplateController {
    private final TeacherResponseTemplateService templateService;
    private final UserRepository userRepository;

    @Operation(
            summary = "사용 가능한 High 답변 템플릿 조회",
            description = "완료된 위험 분석이 없으면 오류 대신 빈 배열을 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "템플릿 조회 성공. 분석이 없으면 빈 배열 반환", useReturnTypeSchema = true),
                    @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "답변 대기 상태가 아니거나 안전 잠금 중임",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @GetMapping
    public ResponseEntity<List<ResponseTemplateResponse>> findAvailable(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(templateService.findAvailable(userId(authentication), complaintId));
    }

    @Operation(summary = "High 답변 템플릿 채택 기록")
    @PostMapping("/{versionId}/adoptions")
    public ResponseEntity<ResponseTemplateAdoptionResponse> adopt(
            Authentication authentication,
            @PathVariable Long complaintId,
            @PathVariable Long versionId,
            @Valid @RequestBody ResponseTemplateAdoptionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.adopt(userId(authentication), complaintId, versionId, request));
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
