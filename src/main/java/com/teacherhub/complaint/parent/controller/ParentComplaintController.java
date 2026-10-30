package com.teacherhub.complaint.parent.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintCreateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintDraftUpdateRequest;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintRecipientResponse;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintResponse;
import com.teacherhub.complaint.parent.service.ParentComplaintService;
import com.teacherhub.complaint.parent.dto.message.ComplaintConversationResponse;
import com.teacherhub.complaint.parent.dto.message.ParentMessageRequest;
import com.teacherhub.complaint.parent.dto.message.ParentMessageResponse;
import com.teacherhub.complaint.parent.dto.message.ParentMessageSendRequest;
import com.teacherhub.complaint.parent.service.ParentMessageService;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.query.dto.ComplaintDetailResponse;
import com.teacherhub.complaint.query.dto.ComplaintListQuery;
import com.teacherhub.complaint.query.dto.ComplaintPageResponse;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springdoc.core.annotations.ParameterObject;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/complaints")
@Tag(name = "Parent Complaint", description = "학부모 민원 작성, 조회 및 추가 문의 API")
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

    private final ComplaintQueryService complaintQueryService;
    private final ParentComplaintService parentComplaintService;
    private final ParentMessageService parentMessageService;
    private final UserRepository userRepository;

    // 민원 작성
    @Operation(summary = "민원 초안 생성", description = "GET /users/me의 students 목록에서 자녀를 선택하고, 해당 studentId로 담당 교사를 조회한 뒤 민원 초안을 생성합니다.", responses = {
            @ApiResponse(responseCode = "201", description = "민원 초안 생성 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 잘못된 수신 교사",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "학생 또는 사용자 정보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ComplaintResponse> createComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Valid @RequestBody ComplaintCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(parentComplaintService.createDraft(userId(authentication), request));
    }

    // 민원 내용 수정
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
            @Valid @RequestBody ComplaintDraftUpdateRequest request) {
        parentComplaintService.updateDraft(userId(authentication), complaintId, request);
        return ResponseEntity.ok(Map.of("message", "민원 내용이 수정되었습니다."));
    }

    // 문의 대상 교사 조회
    @Operation(summary = "문의 대상 교사 조회", description = "GET /users/me의 students 목록에서 선택한 자녀의 studentId로 현재 학급 담당 교사 목록을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "문의 대상 교사 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "studentId 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "학생 정보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/recipients")
    public ResponseEntity<List<ComplaintRecipientResponse>> findRecipients(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "학생 ID", example = "10") @RequestParam Long studentId
    ) {
        return ResponseEntity.ok(parentComplaintService.findRecipients(userId(authentication), studentId));
    }

    // 민원 제출
    @Operation(
            summary = "민원 최종 제출",
            description = "민원 초안을 제출합니다. 서버는 최종 내용의 위험 분석을 시도하며 AI 분석에 실패해도 원문 전송은 유지합니다.",
            responses = {
            @ApiResponse(responseCode = "200", description = "민원 제출 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "제출 가능한 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping("/{complaintId}/send")
    public ResponseEntity<Map<String, String>> submitComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        parentComplaintService.submitComplaint(userId(authentication), complaintId);
        return ResponseEntity.ok(Map.of("message", "민원이 제출되었습니다."));
    }

    // 민원 상태 조회
    @Operation(summary = "민원 상태 조회", description = "민원 상태와 현재 답변 차례를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 상태 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/status")
    public ResponseEntity<ComplaintResponse> findStatus(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(parentComplaintService.findStatus(userId(authentication), complaintId));
    }

    // 학부모가 작성한 민원 목록 조회
    @Operation(summary = "학부모 민원 목록 조회", description = "검색어, 상태, 정렬, 페이지 조건으로 본인의 민원 목록을 조회합니다.", responses = {
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

    // 학부모가 작성한 민원 상세 조회
    @Operation(summary = "학부모 민원 상세 조회", description = "본인이 작성한 민원의 상세 정보를 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "민원 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}")
    public ResponseEntity<ComplaintDetailResponse> findComplaint(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(
                complaintQueryService.findComplaint(userId(authentication), complaintId)
        );
    }

    // 교사 답변 이후 학부모의 추가 문의 임시 저장(없으면 생성, 있으면 수정)
    @Operation(summary = "추가 문의 초안 저장", description = "교사 답변 이후 학부모 추가 문의를 생성하거나 수정합니다. 기존 초안 수정 시 expectedContentVersion이 현재 버전과 다르면 CONTENT_VERSION_CONFLICT로 거절합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "추가 문의 초안 저장 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "학부모가 작성할 차례가 아니거나 CONTENT_VERSION_CONFLICT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{complaintId}/message-draft")
    public ResponseEntity<ParentMessageResponse> saveMessageDraft(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Valid @RequestBody ParentMessageRequest request
    ) {
        return ResponseEntity.ok(parentMessageService.saveDraft(
                userId(authentication),
                complaintId,
                request
        ));
    }

    // 학부모의 추가 문의 임시 저장 조회
    @Operation(summary = "추가 문의 초안 조회", description = "현재 저장된 학부모 추가 문의 초안을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "추가 문의 초안 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원 또는 저장된 초안을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/message-draft")
    public ResponseEntity<ParentMessageResponse> findMessageDraft(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(parentMessageService.findDraft(
                userId(authentication),
                complaintId
        ));
    }

    // 학부모 추가 문의 전송
    @Operation(summary = "추가 문의 전송", description = "expectedContentVersion과 현재 초안 버전이 일치할 때만 위험 분석 후 전송합니다. 검사와 전송은 같은 트랜잭션에서 처리합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "추가 문의 전송 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원 또는 메시지 초안을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "전송 가능한 상태가 아니거나 CONTENT_VERSION_CONFLICT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{complaintId}/messages/{messageId}/send")
    public ResponseEntity<ParentMessageResponse> sendMessage(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId,
            @Parameter(description = "추가 문의 메시지 ID", example = "101") @PathVariable Long messageId,
            @Valid @RequestBody ParentMessageSendRequest request
    ) {
        return ResponseEntity.ok(parentMessageService.submit(
                userId(authentication),
                complaintId,
                messageId,
                request.expectedContentVersion()
        ));
    }

    // 민원과 관련된 학부모-교사 간 대화 내역 조회
    @Operation(summary = "민원 대화 내역 조회", description = "민원에 포함된 전송 완료 메시지를 시간순으로 조회하고 최신 교사 답변을 읽음 처리합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "대화 내역 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/messages")
    public ResponseEntity<ComplaintConversationResponse> findConversation(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(parentMessageService.findConversation(
                userId(authentication),
                complaintId
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
