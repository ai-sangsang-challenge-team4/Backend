package com.teacherhub.complaint.controller;

import com.teacherhub.complaint.dto.ComplaintRequest;
import com.teacherhub.complaint.dto.ComplaintReviewResponse;
import com.teacherhub.complaint.service.ComplaintReviewService;
import com.teacherhub.complaint.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/complaints")
public class ComplaintController {

    private final ComplaintReviewService complaintReviewService;
    private final ComplaintService complaintService;


    // AI 검토 및 수정안 요청
    @PostMapping("/review")
    public ResponseEntity<ComplaintReviewResponse> reviewComplaint(
            @Valid @RequestBody ComplaintRequest request
    ) {

        ComplaintReviewResponse response =
                complaintReviewService.review(request);

        return ResponseEntity.ok(response);
    }


    // 최종 민원 접수
    @PostMapping
    public ResponseEntity<Void> submitComplaint(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ComplaintRequest request
    ) {
        Long parentId = (Long) authentication.getPrincipal();
        
        complaintService.submitComplaint(
                parentId,
                request,
                idempotencyKey
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    // 민원 조회

}