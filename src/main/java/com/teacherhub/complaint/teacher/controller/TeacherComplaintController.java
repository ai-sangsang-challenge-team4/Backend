package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.query.dto.ComplaintListResponse;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.complaint.teacher.dto.query.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherOriginalViewResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherAiDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseRequest;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseSubmissionResponse;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewResponse;
import com.teacherhub.complaint.teacher.service.TeacherComplaintQueryService;
import com.teacherhub.complaint.teacher.service.TeacherResponseService;
import com.teacherhub.complaint.teacher.service.TeacherRiskReviewService;
import com.teacherhub.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints")
public class TeacherComplaintController {

    private final TeacherComplaintQueryService teacherComplaintQueryService;
    private final TeacherResponseService teacherResponseService;
    private final TeacherRiskReviewService teacherRiskReviewService;
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

    // 교사 위험도 검토 및 조정
    @PostMapping("/{complaintId}/risk-reviews")
    public ResponseEntity<TeacherRiskReviewResponse> reviewRisk(
            Authentication authentication,
            @PathVariable Long complaintId,
            @Valid @RequestBody TeacherRiskReviewRequest request
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(teacherRiskReviewService.review(
                        userId(authentication),
                        complaintId,
                        request
                ));
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


    // AI 초안 생성
    @PostMapping("/{complaintId}/response/ai-draft")
    public ResponseEntity<TeacherAiDraftResponse> generateAiDraft(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(teacherResponseService.generateAiDraft(
                        userId(authentication),
                        complaintId
                ));
    }

    // 저장된 답변 초안 조회
    @GetMapping("/{complaintId}/response-draft")
    public ResponseEntity<TeacherResponseDraftResponse> findResponseDraft(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(teacherResponseService.findDraft(
                userId(authentication),
                complaintId
        ));
    }

    // 답변 임시저장: 최초 저장 + 이후 수정
    @PutMapping("/{complaintId}/response-draft")
    public ResponseEntity<TeacherResponseDraftResponse> saveResponseDraft(
            Authentication authentication,
            @PathVariable Long complaintId,
            @Valid @RequestBody TeacherResponseRequest request
    ) {
        return ResponseEntity.ok(teacherResponseService.saveDraft(
                userId(authentication),
                complaintId,
                request
        ));
    }

    // 답변 최종 제출
    @PostMapping("/{complaintId}/responses")
    public ResponseEntity<TeacherResponseSubmissionResponse> submitResponse(
            Authentication authentication,
            @PathVariable Long complaintId,
            @Valid @RequestBody TeacherResponseRequest request
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(teacherResponseService.submit(
                        userId(authentication),
                        complaintId,
                        request
                ));
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
