package com.teacherhub.complaint.template.controller;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.template.dto.ResponseTemplateAdoptionRequest;
import com.teacherhub.complaint.template.dto.ResponseTemplateAdoptionResponse;
import com.teacherhub.complaint.template.dto.ResponseTemplateResponse;
import com.teacherhub.complaint.template.service.TeacherResponseTemplateService;
import com.teacherhub.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "사용 가능한 High 답변 템플릿 조회")
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
