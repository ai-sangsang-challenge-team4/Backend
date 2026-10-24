package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.query.dto.ComplaintListResponse;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.complaint.teacher.dto.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.TeacherOriginalViewResponse;
import com.teacherhub.complaint.teacher.dto.TeacherRiskResponse;
import com.teacherhub.complaint.teacher.service.TeacherComplaintQueryService;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints")
public class TeacherComplaintQueryController {

    private final TeacherComplaintQueryService teacherComplaintQueryService;
    private final ComplaintQueryService complaintQueryService;
    private final UserRepository userRepository;

    // 민원 전체 조회
    @GetMapping
    public ResponseEntity<List<ComplaintListResponse>> findComplaints(Authentication authentication) {
        return ResponseEntity.ok(
                complaintQueryService.findComplaints(userId(authentication))
        );
    }

    // 민원 상세 조회
    @GetMapping("/{complaintId}")
    public ResponseEntity<TeacherComplaintDetailResponse> findComplaint(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findDetail(userId(authentication), complaintId)
        );
    }

    // 민원 대화 조회 (완충요약버전)
    @GetMapping("/{complaintId}/conversation")
    public ResponseEntity<TeacherConversationResponse> findConversation(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findConversation(userId(authentication), complaintId)
        );
    }

    // 민원 원문 조회
    @PostMapping("/{complaintId}/original-views")
    public ResponseEntity<TeacherOriginalViewResponse> viewOriginal(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(teacherComplaintQueryService.viewOriginal(userId(authentication), complaintId));
    }

    // 민원 위험 분석 조회
    @GetMapping("/{complaintId}/risk")
    public ResponseEntity<TeacherRiskResponse> findRisk(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findRisk(userId(authentication), complaintId)
        );
    }

    // 민원 히스토리 조회
    @GetMapping("/{complaintId}/history")
    public ResponseEntity<TeacherHistoryResponse> findHistory(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findHistory(userId(authentication), complaintId)
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
