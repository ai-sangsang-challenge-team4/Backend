package com.teacherhub.complaint.parent.controller;

import com.teacherhub.complaint.parent.dto.ComplaintRequest;
import com.teacherhub.complaint.parent.dto.ComplaintResponse;
import com.teacherhub.complaint.parent.service.ComplaintService;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.query.dto.ComplaintDetailResponse;
import com.teacherhub.complaint.query.dto.ComplaintListResponse;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/complaints")
public class ComplaintController {

    private final ComplaintQueryService complaintQueryService;
    private final ComplaintService complaintService;
    private final UserRepository userRepository;

    // 민원 작성
    @PostMapping
    public ResponseEntity<ComplaintResponse> createComplaint(
            Authentication authentication, @Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complaintService.createDraft(userId(authentication), request));
    }

    // 민원 내용 수정
    @PatchMapping("/{complaintId}")
    public ResponseEntity<Map<String, String>> updateComplaint(
            Authentication authentication, @PathVariable Long complaintId,
            @Valid @RequestBody ComplaintRequest request) {
        complaintService.updateDraft(userId(authentication), complaintId, request);
        return ResponseEntity.ok(Map.of("message", "민원 내용이 수정되었습니다."));
    }

    // 민원 제출
    @PostMapping("/{complaintId}/send")
    public ResponseEntity<Map<String, String>> submitComplaint(
            Authentication authentication, @PathVariable Long complaintId) {
        complaintService.submitComplaint(userId(authentication), complaintId);
        return ResponseEntity.ok(Map.of("message", "민원이 제출되었습니다."));
    }

    // 민원 상태 조회
    @GetMapping("/{complaintId}/status")
    public ResponseEntity<ComplaintResponse> findStatus(
            Authentication authentication, @PathVariable Long complaintId) {
        return ResponseEntity.ok(complaintService.findStatus(userId(authentication), complaintId));
    }

    // 학부모가 작성한 민원 목록 조회
    @GetMapping
    public ResponseEntity<List<ComplaintListResponse>> findComplaints(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                complaintQueryService.findComplaints(userId(authentication))
        );
    }

    // 학부모가 작성한 민원 상세 조회
    @GetMapping("/{complaintId}")
    public ResponseEntity<ComplaintDetailResponse> findComplaint(
            Authentication authentication, @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                complaintQueryService.findComplaint(userId(authentication), complaintId)
        );
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
