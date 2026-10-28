package com.teacherhub.risk.controller;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.risk.dto.EmergencyReviewCreateRequest;
import com.teacherhub.risk.dto.EmergencyReviewResponse;
import com.teacherhub.risk.dto.EmergencyReviewUpdateRequest;
import com.teacherhub.risk.service.EmergencyReviewService;
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
@Tag(name = "Emergency Review", description = "Emergency 재검토 요청과 안전 잠금 API")
@SecurityRequirement(name = "bearerAuth")
public class EmergencyReviewController {

    private final EmergencyReviewService emergencyReviewService;
    private final UserRepository userRepository;

    @Operation(summary = "Emergency 재검토 요청")
    @PostMapping("/teacher/complaints/{complaintId}/emergency-review")
    public ResponseEntity<EmergencyReviewResponse> request(
            Authentication authentication,
            @PathVariable Long complaintId,
            @Valid @RequestBody EmergencyReviewCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                emergencyReviewService.request(userId(authentication), complaintId, request)
        );
    }

    @Operation(summary = "최근 Emergency 재검토 요청 조회")
    @GetMapping("/teacher/complaints/{complaintId}/emergency-review/current")
    public ResponseEntity<EmergencyReviewResponse> findCurrent(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                emergencyReviewService.findCurrent(userId(authentication), complaintId)
        );
    }

    @Operation(summary = "지정 검토자의 Emergency 재검토 요청 조회")
    @GetMapping("/admin/emergency-review")
    public ResponseEntity<List<EmergencyReviewResponse>> findAllForReviewer(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                emergencyReviewService.findAllForReviewer(userId(authentication))
        );
    }

    @Operation(summary = "지정 검토자의 Emergency 재검토 요청 상세 조회")
    @GetMapping("/admin/emergency-review/{requestId}")
    public ResponseEntity<EmergencyReviewResponse> findForReviewer(
            Authentication authentication,
            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(
                emergencyReviewService.findForReviewer(userId(authentication), requestId)
        );
    }

    @Operation(summary = "지정 검토자의 Emergency 재검토 결정 및 안전 잠금 변경")
    @PatchMapping("/admin/emergency-review/{requestId}")
    public ResponseEntity<EmergencyReviewResponse> update(
            Authentication authentication,
            @PathVariable Long requestId,
            @Valid @RequestBody EmergencyReviewUpdateRequest request
    ) {
        return ResponseEntity.ok(
                emergencyReviewService.update(userId(authentication), requestId, request)
        );
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
