package com.teacherhub.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ComplaintNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleComplaintNotFound(ComplaintNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "COMPLAINT_NOT_FOUND", e, request);
    }

    @ExceptionHandler(RiskAnalysisNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRiskAnalysisNotFound(RiskAnalysisNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RISK_ANALYSIS_NOT_FOUND", e, request);
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
