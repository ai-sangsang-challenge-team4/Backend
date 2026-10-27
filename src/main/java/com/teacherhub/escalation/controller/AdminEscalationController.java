package com.teacherhub.escalation.controller;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.escalation.dto.EscalationMessageRequest;
import com.teacherhub.escalation.dto.EscalationStateResponse;
import com.teacherhub.escalation.dto.AdminEscalationSummaryResponse;
import com.teacherhub.escalation.dto.EscalationShareContentResponse;
import com.teacherhub.escalation.dto.RiskChangeDecisionRequest;
import com.teacherhub.escalation.service.AdminEscalationService;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/escalations")
public class AdminEscalationController {

    private final AdminEscalationService escalationService;
    private final UserRepository userRepository;

    // 공유 받은 메세지 목록 조회
    @GetMapping
    public ResponseEntity<List<AdminEscalationSummaryResponse>> findAll(
            Authentication authentication,
            @RequestParam(required = false) EscalationStatus status,
            @RequestParam(required = false) RiskLevel riskLevel
    ) {
        return ResponseEntity.ok(escalationService.findAll(
                userId(authentication),
                status,
                riskLevel
        ));
    }

    // 공유 메세지 상세 조회 (상단 정보, 처리 상태)
    @GetMapping("/{escalationId}")
    public ResponseEntity<AdminEscalationSummaryResponse> findOne(
            Authentication authentication,
            @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(escalationService.findOne(userId(authentication), escalationId));
    }

    // 공유 내용 탭 조회
    @GetMapping("/{escalationId}/messages")
    public ResponseEntity<EscalationShareContentResponse> findMessages(
            Authentication authentication,
            @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(
                escalationService.findMessages(userId(authentication), escalationId)
        );
    }

    // 위험 분석 탭 조회
    @GetMapping("/{escalationId}/risk")
    public ResponseEntity<TeacherRiskResponse> findRisk(
            Authentication authentication,
            @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(
                escalationService.findRisk(userId(authentication), escalationId)
        );
    }

    // 처리 기록 탭 조회
    @GetMapping("/{escalationId}/history")
    public ResponseEntity<TeacherHistoryResponse> findHistory(
            Authentication authentication,
            @PathVariable Long escalationId
    ) {
        return ResponseEntity.ok(
                escalationService.findHistory(userId(authentication), escalationId)
        );
    }

    // 관리자 검토 시작 (검토 메세지 작성)
    @PostMapping("/{escalationId}/review")
    public ResponseEntity<EscalationStateResponse> startReview(
            Authentication authentication,
            @PathVariable Long escalationId,
            @Valid @RequestBody EscalationMessageRequest request
    ) {
        return ResponseEntity.ok(
                escalationService.startReview(userId(authentication), escalationId, request)
        );
    }

    // EMERGENCY 위험도 변경 승인·거부
    @PostMapping("/{escalationId}/risk-change-decision")
    public ResponseEntity<EscalationStateResponse> decideRiskChange(
            Authentication authentication,
            @PathVariable Long escalationId,
            @Valid @RequestBody RiskChangeDecisionRequest request
    ) {
        return ResponseEntity.ok(
                escalationService.decideRiskChange(userId(authentication), escalationId, request)
        );
    }

    // 관리자 검토 완료
    @PostMapping("/{escalationId}/complete")
    public ResponseEntity<EscalationStateResponse> complete(
            Authentication authentication,
            @PathVariable Long escalationId,
            @Valid @RequestBody EscalationMessageRequest request
    ) {
        return ResponseEntity.ok(
                escalationService.complete(userId(authentication), escalationId, request)
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
