package com.teacherhub.complaint.query.controller;

import com.teacherhub.complaint.query.dto.ComplaintDetailResponse;
import com.teacherhub.complaint.query.dto.ComplaintListResponse;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.complaint.teacher.dto.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.TeacherOriginalViewResponse;
import com.teacherhub.complaint.teacher.dto.TeacherRiskResponse;
import com.teacherhub.complaint.teacher.service.TeacherComplaintQueryService;
import com.teacherhub.common.exception.UnauthorizedException;
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
@RequestMapping("/complaints")
public class ComplaintQueryController {

    private final ComplaintQueryService complaintQueryService;
    private final TeacherComplaintQueryService teacherComplaintQueryService;
    private final UserRepository userRepository;

    // 접근 권한이 있는 민원 목록 조회
    @GetMapping
    public ResponseEntity<List<ComplaintListResponse>> findComplaints(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                complaintQueryService.findComplaints(userId(authentication))
        );
    }

    // 접근 권한이 있는 민원 상세 조회
    @GetMapping("/{complaintId}")
    public ResponseEntity<ComplaintDetailResponse> findComplaint(
            Authentication authentication, @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                complaintQueryService.findComplaint(userId(authentication), complaintId)
        );
    }

    // 교사 화면의 대화 탭 조회
    @GetMapping("/{complaintId}/conversation")
    public ResponseEntity<TeacherConversationResponse> findTeacherConversation(
            Authentication authentication, @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findConversation(userId(authentication), complaintId)
        );
    }

    // 교사가 원문 보기를 요청한 시점에 원문을 반환하고 열람 기록 생성
    @PostMapping("/{complaintId}/original-views")
    public ResponseEntity<TeacherOriginalViewResponse> viewOriginal(
            Authentication authentication, @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(teacherComplaintQueryService.viewOriginal(userId(authentication),complaintId
                ));
    }

    // 교사 화면의 위험 요소 탭 조회
    @GetMapping("/{complaintId}/risk")
    public ResponseEntity<TeacherRiskResponse> findTeacherRisk(
            Authentication authentication, @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findRisk(userId(authentication), complaintId)
        );
    }

    // 교사 화면의 처리 기록 탭 조회
    @GetMapping("/{complaintId}/history")
    public ResponseEntity<TeacherHistoryResponse> findTeacherHistory(
            Authentication authentication, @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findHistory(userId(authentication), complaintId)
        );
    }

    // 인증된 사용자의 ID를 가져오기
    private Long userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException(
                        "사용자를 찾을 수 없습니다."
                ))
                .getId();
    }
}
