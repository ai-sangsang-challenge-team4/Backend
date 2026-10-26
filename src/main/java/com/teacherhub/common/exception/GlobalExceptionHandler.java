package com.teacherhub.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.OffsetDateTime;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.exception.RiskReviewNotAllowedException;
import com.teacherhub.risk.exception.RiskReviewReasonRequiredException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(errorMessage -> errorMessage != null && !errorMessage.isBlank())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");

        return ResponseEntity.badRequest().body(new ErrorResponse(
                "ERROR",
                "VALIDATION_FAILED",
                message,
                OffsetDateTime.now(),
                request.getRequestURI()
        ));
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

    @ExceptionHandler(InvalidComplaintRecipientException.class)
    public ResponseEntity<ErrorResponse> handleInvalidComplaintRecipient(InvalidComplaintRecipientException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_COMPLAINT_RECIPIENT", e, request);
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
        return ResponseEntity.status(status).body(new ErrorResponse(
                "ERROR", code, e.getMessage(), OffsetDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException e,
            HttpServletRequest request
    ) {

        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e, request);
    }
}
