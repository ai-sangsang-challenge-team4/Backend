package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.query.dto.ComplaintListQuery;
import com.teacherhub.complaint.query.dto.ComplaintPageResponse;
import com.teacherhub.complaint.query.service.ComplaintListQueryService;
import com.teacherhub.complaint.teacher.dto.query.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherOriginalViewResponse;
import com.teacherhub.complaint.teacher.service.TeacherComplaintQueryService;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints")
@Tag(name = "Teacher Complaint", description = "교사 민원 조회 API")
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
    private final ComplaintListQueryService complaintListQueryService;
    private final UserRepository userRepository;

    @Operation(summary = "교사 민원 목록 조회", description = "검색어, 상태, 정렬, 페이지 조건으로 담당 민원 목록을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "목록 조회 조건 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ComplaintPageResponse> findComplaints(
            @Parameter(hidden = true) Authentication authentication,
            @ParameterObject @Valid @ModelAttribute ComplaintListQuery query) {
        return ResponseEntity.ok(complaintListQueryService.findComplaints(userId(authentication), query));
    }

    @Operation(summary = "교사 민원 상세 조회", description = "담당 민원의 학생·학급·상태·위험도 정보를 조회합니다. 완료된 위험 분석이 없으면 riskLevel은 null입니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 상세 조회 성공. 완료된 분석이 없으면 riskLevel은 null", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}")
    public ResponseEntity<TeacherComplaintDetailResponse> findComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(teacherComplaintQueryService.findDetail(userId(authentication), complaintId));
    }

    @Operation(summary = "교사용 대화 내역 조회", description = "위험 메시지는 완충 요약하고 위험도별 허용 행동과 함께 반환하며 최신 학부모 문의를 읽음 처리합니다. 완료된 위험 분석이 없으면 대화는 정상 반환하고 allowedActions에 REQUEST_MANUAL_REVIEW를 제공합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "대화 내역 조회 성공. 분석이 없으면 수동 검토 액션을 반환", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/messages")
    public ResponseEntity<TeacherConversationResponse> findConversation(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(teacherComplaintQueryService.findConversation(userId(authentication), complaintId));
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
            @Parameter(description = "메시지 ID", example = "101") @PathVariable Long messageId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(teacherComplaintQueryService.viewOriginal(userId(authentication), complaintId, messageId));
    }

    @Operation(summary = "민원 처리 이력 조회", description = "상태 변경, 분석, 원문·메시지 열람, 메시지 전송 및 관리자 공유 이벤트와 로그 누락 여부를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "처리 이력 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/history")
    public ResponseEntity<TeacherHistoryResponse> findHistory(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(teacherComplaintQueryService.findHistory(userId(authentication), complaintId));
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
