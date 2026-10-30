package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.query.dto.ComplaintListQuery;
import com.teacherhub.complaint.query.dto.ComplaintPageResponse;
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
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springdoc.core.annotations.ParameterObject;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints")
@Tag(name = "Teacher Complaint", description = "교사 민원 조회, 위험도 검토 및 답변 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "담당 교사가 아니거나 교사 권한이 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TeacherComplaintController {

    private final TeacherComplaintQueryService teacherComplaintQueryService;
    private final TeacherResponseService teacherResponseService;
    private final TeacherRiskReviewService teacherRiskReviewService;
    private final ComplaintQueryService complaintQueryService;
    private final UserRepository userRepository;

    // 민원 전체 조회
    @Operation(summary = "교사 민원 목록 조회", description = "검색어, 상태, 정렬, 페이지 조건으로 담당 민원 목록을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "목록 조회 조건 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ComplaintPageResponse> findComplaints(
            @Parameter(hidden = true) Authentication authentication,
            @ParameterObject @Valid @ModelAttribute ComplaintListQuery query
    ) {
        return ResponseEntity.ok(
                complaintQueryService.findComplaints(userId(authentication), query)
        );
    }

    // 민원 상세 조회
    @Operation(summary = "교사 민원 상세 조회", description = "담당 민원의 학생·학급·상태·위험도 정보를 조회합니다. 완료된 위험 분석이 없으면 riskLevel은 null입니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 상세 조회 성공. 완료된 분석이 없으면 riskLevel은 null", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}")
    public ResponseEntity<TeacherComplaintDetailResponse> findComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findDetail(userId(authentication), complaintId)
        );
    }

    // 민원 대화 조회 (완충요약버전)
    @Operation(summary = "교사용 대화 내역 조회", description = "위험 메시지는 완충 요약하고 위험도별 허용 행동과 함께 반환하며 최신 학부모 문의를 읽음 처리합니다. 완료된 위험 분석이 없으면 대화는 정상 반환하고 allowedActions에 REQUEST_MANUAL_REVIEW를 제공합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "대화 내역 조회 성공. 분석이 없으면 수동 검토 액션을 반환", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/messages")
    public ResponseEntity<TeacherConversationResponse> findConversation(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findConversation(userId(authentication), complaintId)
        );
    }

    @Operation(summary = "메시지 원문 열람", description = "지정한 전송 메시지의 원문을 조회하고 원문 열람 감사 이벤트를 저장합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "메시지 원문 조회 및 열람 기록 저장 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원 또는 전송 메시지를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/messages/{messageId}/original-views")
    public ResponseEntity<TeacherOriginalViewResponse> viewMessageOriginal(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Parameter(description = "메시지 ID", example = "101") @PathVariable Long messageId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(teacherComplaintQueryService.viewOriginal(
                        userId(authentication), complaintId, messageId));
    }

    // 민원 위험 분석 조회
    @Operation(summary = "민원 위험 분석 조회", description = "시스템 위험도, 교사 검토값과 감지된 위험 태그를 조회합니다. 완료된 위험 분석이 없으면 분석 관련 필드는 null, detectedFactors는 빈 배열로 반환합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "위험 분석 조회 성공. 분석이 없으면 nullable 필드와 빈 detectedFactors를 반환", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/risk")
    public ResponseEntity<TeacherRiskResponse> findRisk(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findRisk(userId(authentication), complaintId)
        );
    }

    // 교사 위험도 검토 및 조정
    @Operation(summary = "교사 위험도 검토", description = "교사가 1~7점 위험도를 검토합니다. EMERGENCY 변경은 관리자 승인이 필요할 수 있습니다.", responses = {
            @ApiResponse(responseCode = "201", description = "위험도 검토 저장 또는 관리자 승인 요청 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "점수·사유 입력값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "민원 또는 위험 분석 결과를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "검토할 수 없는 상태 또는 관리자 공유 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/risk-reviews")
    public ResponseEntity<TeacherRiskReviewResponse> reviewRisk(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
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
    @Operation(summary = "민원 처리 이력 조회", description = "상태 변경, 분석, 원문·메시지 열람, 메시지 전송 및 관리자 공유 이벤트와 로그 누락 여부를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "처리 이력 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/history")
    public ResponseEntity<TeacherHistoryResponse> findHistory(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                teacherComplaintQueryService.findHistory(userId(authentication), complaintId)
        );
    }


    // AI 초안 생성
    @Operation(summary = "교사 AI 답변 초안 생성", description = "현재 대화 이력을 기반으로 답변 초안을 생성합니다. HIGH에서는 가능하지만 유효 위험도가 EMERGENCY이거나 safetyLock이 활성화되면 차단합니다. 현재 제공자는 Mock 구현입니다.", responses = {
            @ApiResponse(responseCode = "201", description = "AI 답변 초안 생성 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "교사가 답변할 수 없는 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/response/ai-draft")
    public ResponseEntity<TeacherAiDraftResponse> generateAiDraft(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(teacherResponseService.generateAiDraft(
                        userId(authentication),
                        complaintId
                ));
    }

    // 저장된 답변 초안 조회
    @Operation(summary = "교사 답변 초안 조회", description = "현재 저장된 교사 답변 초안을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "답변 초안 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원 또는 답변 초안을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/response-draft")
    public ResponseEntity<TeacherResponseDraftResponse> findResponseDraft(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(teacherResponseService.findDraft(
                userId(authentication),
                complaintId
        ));
    }

    // 답변 임시저장: 최초 저장 + 이후 수정
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
            @Valid @RequestBody TeacherResponseRequest request
    ) {
        return ResponseEntity.ok(teacherResponseService.saveDraft(
                userId(authentication),
                complaintId,
                request
        ));
    }

    // 답변 최종 제출
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
            @Valid @RequestBody TeacherResponseRequest request
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(teacherResponseService.submit(
                        userId(authentication),
                        complaintId,
                        request,
                        idempotencyKey
                ));
    }

    @Operation(
            summary = "교사 상담 완료 처리",
            description = "교사가 답변을 전송한 뒤 학부모 응답 대기 중인 문의를 완료 처리합니다. 답변 전송만으로는 자동 완료되지 않습니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "상담 완료 처리 성공"),
                    @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "RESPONSE_NOT_EDITABLE: 이미 완료되었거나 진행 중이 아닌 민원, 교사 답변 전송 전, Emergency 위험도 또는 안전 잠금 상태",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping("/{complaintId}/close")
    public ResponseEntity<Map<String, String>> closeComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
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
