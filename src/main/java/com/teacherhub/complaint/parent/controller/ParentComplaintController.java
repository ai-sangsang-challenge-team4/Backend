package com.teacherhub.complaint.parent.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.parent.dto.complaint.ParentComplaintCreateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ParentComplaintDetailResponse;
import com.teacherhub.complaint.parent.dto.complaint.ParentComplaintDraftUpdateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ParentComplaintRecipientResponse;
import com.teacherhub.complaint.parent.dto.complaint.ParentComplaintStateResponse;
import com.teacherhub.complaint.parent.dto.complaint.ParentComplaintSubmissionResponse;
import com.teacherhub.complaint.parent.service.ParentComplaintQueryService;
import com.teacherhub.complaint.parent.service.ParentComplaintService;
import com.teacherhub.complaint.query.dto.ComplaintListQuery;
import com.teacherhub.complaint.query.dto.ComplaintPageResponse;
import com.teacherhub.complaint.query.service.ComplaintListQueryService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/complaints")
@Tag(name = "Parent Complaint", description = "학부모 민원 작성 및 조회 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "민원 접근 권한 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ParentComplaintController {

    private final ComplaintListQueryService complaintListQueryService;
    private final ParentComplaintQueryService parentComplaintQueryService;
    private final ParentComplaintService parentComplaintService;
    private final UserRepository userRepository;

    @Operation(summary = "민원 초안 생성", description = "GET /users/me의 students 목록에서 자녀를 선택하고, 해당 studentId로 담당 교사를 조회한 뒤 민원 초안을 생성합니다.", responses = {
            @ApiResponse(responseCode = "201", description = "민원 초안 생성 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 잘못된 수신 교사",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "학생 또는 사용자 정보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ParentComplaintStateResponse> createComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestBody ParentComplaintCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(parentComplaintService.createDraft(userId(authentication), request));
    }

    @Operation(summary = "민원 초안 수정", description = "DRAFT 상태의 최초 민원 내용을 수정합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 초안 수정 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "수정 가능한 초안 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{complaintId}")
    public ResponseEntity<Map<String, String>> updateComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Valid @RequestBody ParentComplaintDraftUpdateRequest request) {
        parentComplaintService.updateDraft(userId(authentication), complaintId, request);
        return ResponseEntity.ok(Map.of("message", "민원 내용이 수정되었습니다."));
    }

    @Operation(summary = "문의 대상 교사 조회", description = "GET /users/me의 students 목록에서 선택한 자녀의 studentId로 현재 학급 담당 교사 목록을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "문의 대상 교사 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "studentId 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "학생 정보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/recipients")
    public ResponseEntity<List<ParentComplaintRecipientResponse>> findRecipients(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "학생 ID", example = "10") @RequestParam Long studentId) {
        return ResponseEntity.ok(parentComplaintService.findRecipients(userId(authentication), studentId));
    }

    @Operation(summary = "민원 최종 제출", description = "민원 초안을 제출합니다. 서버는 최종 내용의 위험 분석을 시도하며 AI 분석에 실패해도 원문 전송은 유지합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 제출 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "제출 가능한 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/send")
    public ResponseEntity<ParentComplaintSubmissionResponse> submitComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(parentComplaintService.submitComplaint(userId(authentication), complaintId));
    }

    @Operation(summary = "민원 상태 조회", description = "민원 상태와 현재 답변 차례를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 상태 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/status")
    public ResponseEntity<ParentComplaintStateResponse> findStatus(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(parentComplaintService.findStatus(userId(authentication), complaintId));
    }

    @Operation(summary = "학부모 문의 완료 처리", description = "교사 답변을 확인한 학부모가 ANSWERED 상태의 민원을 최종 종료합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "문의 완료 처리 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "COMPLAINT_NOT_CLOSABLE: 교사 답변 완료 상태가 아니거나 이미 종료된 민원",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/close")
    public ResponseEntity<ParentComplaintStateResponse> closeComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(parentComplaintService.close(userId(authentication), complaintId));
    }

    @Operation(summary = "학부모 민원 목록 조회", description = "검색어, 상태, 정렬, 페이지 조건으로 본인의 민원 목록을 조회합니다.", responses = {
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

    @Operation(summary = "학부모 민원 상세 조회", description = "본인이 작성한 민원의 상세 정보를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}")
    public ResponseEntity<ParentComplaintDetailResponse> findComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(parentComplaintQueryService.findComplaint(userId(authentication), complaintId));
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
