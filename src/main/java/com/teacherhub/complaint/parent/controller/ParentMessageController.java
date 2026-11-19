package com.teacherhub.complaint.parent.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.parent.dto.message.ParentComplaintConversationResponse;
import com.teacherhub.complaint.parent.dto.message.ParentMessageRequest;
import com.teacherhub.complaint.parent.dto.message.ParentMessageResponse;
import com.teacherhub.complaint.parent.dto.message.ParentMessageSendRequest;
import com.teacherhub.complaint.parent.service.ParentMessageService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/complaints")
@Tag(name = "Parent Message", description = "학부모 추가 문의 및 대화 조회 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "민원 접근 권한 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "예상하지 못한 서버 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ParentMessageController {

    private final ParentMessageService parentMessageService;
    private final UserRepository userRepository;

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
            @Valid @RequestBody ParentMessageRequest request) {
        return ResponseEntity.ok(parentMessageService.saveDraft(userId(authentication), complaintId, request));
    }

    @Operation(summary = "추가 문의 초안 조회", description = "현재 저장된 학부모 추가 문의 초안을 조회합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "추가 문의 초안 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원 또는 저장된 초안을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/message-draft")
    public ResponseEntity<ParentMessageResponse> findMessageDraft(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(parentMessageService.findDraft(userId(authentication), complaintId));
    }

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
            @Valid @RequestBody ParentMessageSendRequest request) {
        return ResponseEntity.ok(parentMessageService.submit(
                userId(authentication), complaintId, messageId, request.expectedContentVersion()));
    }

    @Operation(summary = "민원 대화 내역 조회", description = "민원에 포함된 전송 완료 메시지를 시간순으로 조회하고 최신 교사 답변을 읽음 처리합니다.", responses = {
            @ApiResponse(responseCode = "200", description = "대화 내역 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "민원을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{complaintId}/messages")
    public ResponseEntity<ParentComplaintConversationResponse> findConversation(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "민원 ID", example = "42") @PathVariable Long complaintId) {
        return ResponseEntity.ok(parentMessageService.findConversation(userId(authentication), complaintId));
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
