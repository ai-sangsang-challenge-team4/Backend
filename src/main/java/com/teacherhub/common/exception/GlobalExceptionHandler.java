package com.teacherhub.common.exception;

import com.teacherhub.escalation.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.exception.RiskReviewNotAllowedException;
import com.teacherhub.risk.exception.RiskReviewReasonRequiredException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("요청 값을 확인해주세요.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                "ERROR", "VALIDATION_FAILED", message, OffsetDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(AdminNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAdminNotFound(AdminNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ADMIN_NOT_FOUND", e, request);
    }

    @ExceptionHandler(EscalationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEscalationNotFound(EscalationNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ESCALATION_NOT_FOUND", e, request);
    }

    @ExceptionHandler(EscalationAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleEscalationAccessDenied(EscalationAccessDeniedException e, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "ESCALATION_ACCESS_DENIED", e, request);
    }

    @ExceptionHandler(EscalationReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> handleEscalationReasonRequired(EscalationReasonRequiredException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "ESCALATION_REASON_REQUIRED", e, request);
    }

    @ExceptionHandler(EscalationNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleEscalationNotAllowed(EscalationNotAllowedException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ESCALATION_NOT_ALLOWED", e, request);
    }

    @ExceptionHandler(EscalationStateException.class)
    public ResponseEntity<ErrorResponse> handleEscalationState(EscalationStateException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ESCALATION_STATE_INVALID", e, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleEscalationVersionConflict(
            OptimisticLockingFailureException e,
            HttpServletRequest request
    ) {
        log.warn("Optimistic locking conflict while handling request {}", request.getRequestURI(), e);
        return error(
                HttpStatus.CONFLICT,
                "ESCALATION_VERSION_CONFLICT",
                "다른 요청에서 데이터가 변경되었습니다. 최신 상태를 다시 조회해주세요.",
                request
        );
    }

    @ExceptionHandler(AdminApprovalRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAdminApprovalRequired(AdminApprovalRequiredException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADMIN_APPROVAL_REQUIRED", e, request);
    }

    @ExceptionHandler(FinalAnalysisOnSubmitException.class)
    public ResponseEntity<ErrorResponse> handleFinalAnalysisOnSubmit(FinalAnalysisOnSubmitException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "FINAL_ANALYSIS_ON_SUBMIT", e, request);
    }

    @ExceptionHandler(AnalysisRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisRequired(AnalysisRequiredException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ANALYSIS_REQUIRED", e, request);
    }

    @ExceptionHandler(ReanalysisLimitException.class)
    public ResponseEntity<ErrorResponse> handleReanalysisLimit(ReanalysisLimitException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "REANALYSIS_LIMIT_REACHED", e, request);
    }

    @ExceptionHandler(ComplaintNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleComplaintNotFound(ComplaintNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "COMPLAINT_NOT_FOUND", e, request);
    }

    @ExceptionHandler(RiskAnalysisNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRiskAnalysisNotFound(RiskAnalysisNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RISK_ANALYSIS_NOT_FOUND", e, request);
    }

    @ExceptionHandler(RiskReviewReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> handleRiskReviewReasonRequired(RiskReviewReasonRequiredException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "RISK_REVIEW_REASON_REQUIRED", e, request);
    }

    @ExceptionHandler(RiskReviewNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleRiskReviewNotAllowed(RiskReviewNotAllowedException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "RISK_REVIEW_NOT_ALLOWED", e, request);
    }

    @ExceptionHandler(ComplaintAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleComplaintAccessDenied(ComplaintAccessDeniedException e, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "COMPLAINT_ACCESS_DENIED", e, request);
    }

    @ExceptionHandler(ComplaintNotDraftException.class)
    public ResponseEntity<ErrorResponse> handleComplaintNotDraft(ComplaintNotDraftException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "COMPLAINT_NOT_DRAFT", e, request);
    }

    @ExceptionHandler(EmptyComplaintContentException.class)
    public ResponseEntity<ErrorResponse> handleEmptyComplaintContent(EmptyComplaintContentException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "EMPTY_COMPLAINT_CONTENT", e, request);
    }

    @ExceptionHandler(ResponseDraftNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResponseDraftNotFound(ResponseDraftNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESPONSE_DRAFT_NOT_FOUND", e, request);
    }

    @ExceptionHandler(MessageDraftNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMessageDraftNotFound(MessageDraftNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "MESSAGE_DRAFT_NOT_FOUND", e, request);
    }

    @ExceptionHandler(ResponseNotEditableException.class)
    public ResponseEntity<ErrorResponse> handleResponseNotEditable(ResponseNotEditableException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "RESPONSE_NOT_EDITABLE", e, request);
    }

    @ExceptionHandler(MessageNotEditableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotEditable(MessageNotEditableException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "MESSAGE_NOT_EDITABLE", e, request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, RuntimeException e, HttpServletRequest request) {
        return error(status, code, e.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                "ERROR", code, message, OffsetDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException e,
            HttpServletRequest request
    ) {

        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e, request);
    }
}
