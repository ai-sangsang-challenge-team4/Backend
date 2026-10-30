package com.teacherhub.common.exception;

import com.teacherhub.auth.exception.SignupConflictException;
import com.teacherhub.auth.exception.SignupReferenceNotFoundException;
import com.teacherhub.auth.exception.SignupValidationException;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.escalation.exception.*;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.exception.RiskReviewNotAllowedException;
import com.teacherhub.risk.exception.RiskReviewReasonRequiredException;
import com.teacherhub.risk.exception.EmergencyReviewAccessDeniedException;
import com.teacherhub.risk.exception.EmergencyReviewDuplicateException;
import com.teacherhub.risk.exception.EmergencyReviewNotFoundException;
import com.teacherhub.user.exception.StudentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(SignupValidationException.class)
    public ResponseEntity<ErrorResponse> handleSignupValidation(
            SignupValidationException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "SIGNUP_VALIDATION_FAILED", e, request);
    }

    @ExceptionHandler(SignupReferenceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSignupReferenceNotFound(
            SignupReferenceNotFoundException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "SIGNUP_REFERENCE_NOT_FOUND", e, request);
    }

    @ExceptionHandler(SignupConflictException.class)
    public ResponseEntity<ErrorResponse> handleSignupConflict(
            SignupConflictException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "SIGNUP_CONFLICT", e, request);
    }

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

        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
            BindException e,
            HttpServletRequest request
    ) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(errorMessage -> errorMessage != null && !errorMessage.isBlank())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException e,
            HttpServletRequest request
    ) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .filter(errorMessage -> errorMessage != null && !errorMessage.isBlank())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "요청 본문을 확인해주세요.", request);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter(
            Exception e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETER", "요청 파라미터를 확인해주세요.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "API_NOT_FOUND", "요청한 API를 찾을 수 없습니다.", request);
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

    @ExceptionHandler(EscalationVersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleEscalationVersionConflict(
            EscalationVersionConflictException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "ESCALATION_VERSION_CONFLICT", e, request);
    }

    @ExceptionHandler(EmergencyReviewDuplicateException.class)
    public ResponseEntity<ErrorResponse> handleEmergencyReviewDuplicate(
            EmergencyReviewDuplicateException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "EMERGENCY_REVIEW_DUPLICATE", e, request);
    }

    @ExceptionHandler(EmergencyReviewNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEmergencyReviewNotFound(
            EmergencyReviewNotFoundException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "EMERGENCY_REVIEW_NOT_FOUND", e, request);
    }

    @ExceptionHandler(EmergencyReviewAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleEmergencyReviewAccessDenied(
            EmergencyReviewAccessDeniedException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.FORBIDDEN, "EMERGENCY_REVIEW_ACCESS_DENIED", e, request);
    }

    @ExceptionHandler(FinalAnalysisOnSubmitException.class)
    public ResponseEntity<ErrorResponse> handleFinalAnalysisOnSubmit(FinalAnalysisOnSubmitException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "FINAL_ANALYSIS_ON_SUBMIT", e, request);
    }

    @ExceptionHandler(ReanalysisLimitException.class)
    public ResponseEntity<ErrorResponse> handleReanalysisLimit(ReanalysisLimitException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "REANALYSIS_LIMIT_REACHED", e, request);
    }

    @ExceptionHandler(ComplaintNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleComplaintNotFound(ComplaintNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "COMPLAINT_NOT_FOUND", e, request);
    }

    @ExceptionHandler(StudentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStudentNotFound(StudentNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", e, request);
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

    @ExceptionHandler(ContentVersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleContentVersionConflict(
            ContentVersionConflictException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", e, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException e,
            HttpServletRequest request
    ) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "접근 권한이 없습니다.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e,
            HttpServletRequest request
    ) {
        log.warn("Data conflict while handling request {}", request.getRequestURI(), e);
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "이미 존재하거나 현재 상태와 충돌하는 데이터입니다.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException e,
            HttpServletRequest request
    ) {
        log.warn("Invalid request while handling {}: {}", request.getRequestURI(), e.getMessage(), e);
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "요청 값이 올바르지 않습니다.",
                request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException e,
            HttpServletRequest request
    ) {
        log.warn("Invalid state while handling {}: {}", request.getRequestURI(), e.getMessage(), e);
        return error(
                HttpStatus.CONFLICT,
                "INVALID_STATE",
                "요청을 현재 상태에서 처리할 수 없습니다.",
                request
        );
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, RuntimeException e, HttpServletRequest request) {
        return error(status, code, e.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatusCode status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(
                ErrorResponse.of(code, message, request.getRequestURI())
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException e,
            HttpServletRequest request
    ) {

        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception e,
            HttpServletRequest request
    ) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            log.warn(
                    "Spring request error while handling {} with status {}",
                    request.getRequestURI(),
                    status.value(),
                    e
            );
            return error(
                    status,
                    springErrorCode(status),
                    springErrorMessage(status),
                    request
            );
        }

        log.error("Unexpected error while handling request {}", request.getRequestURI(), e);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다.",
                request
        );
    }

    private String springErrorCode(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.name() : "HTTP_" + status.value();
    }

    private String springErrorMessage(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved == null) {
            return status.is4xxClientError()
                    ? "요청을 처리할 수 없습니다."
                    : "서버 내부 오류가 발생했습니다.";
        }

        return switch (resolved) {
            case BAD_REQUEST -> "요청 형식 또는 값을 확인해주세요.";
            case NOT_FOUND -> "요청한 API를 찾을 수 없습니다.";
            case METHOD_NOT_ALLOWED -> "지원하지 않는 HTTP 메서드입니다.";
            case NOT_ACCEPTABLE -> "요청한 응답 형식을 제공할 수 없습니다.";
            case UNSUPPORTED_MEDIA_TYPE -> "지원하지 않는 Content-Type입니다.";
            default -> resolved.is4xxClientError()
                    ? "요청을 처리할 수 없습니다."
                    : "서버 내부 오류가 발생했습니다.";
        };
    }
}
