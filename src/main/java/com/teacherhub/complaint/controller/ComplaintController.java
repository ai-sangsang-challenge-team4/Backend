package com.teacherhub.complaint.controller;

import com.teacherhub.complaint.dto.ComplaintRequest;
import com.teacherhub.complaint.dto.ComplaintResponse;
import com.teacherhub.complaint.dto.ComplaintReviewResponse;
import com.teacherhub.complaint.service.ComplaintReviewService;
import com.teacherhub.complaint.service.ComplaintService;
import com.teacherhub.user.repository.UserRepository;
import com.teacherhub.user.entity.User;
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

    private final ComplaintService complaintService;
    private final ComplaintReviewService complaintReviewService;
    private final UserRepository userRepository;


    //1. 민원 작성 DRAFT 상태로 저장
    @PostMapping
    public ResponseEntity<ComplaintResponse> createComplaint(
                Authentication authentication,
            @Valid @RequestBody ComplaintRequest request
    ) {

            String email = authentication.getName();
            Long userId = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."))
                    .getId();

        ComplaintResponse response = complaintService.createDraft(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    //2. AI 검토
    @PostMapping("/{complaintId}/review")
    public ResponseEntity<ComplaintReviewResponse> reviewComplaint(
                Authentication authentication,
            @PathVariable Long complaintId
    ) {

            String email = authentication.getName();
            Long userId = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."))
                    .getId();

        ComplaintReviewResponse response =
                complaintReviewService.review(
                        userId,
                        complaintId
                );

        return ResponseEntity.ok(response);
    }


    //3. 민원 내용 수정 (DRAFT 상태에서만 가능)
    @PatchMapping("/{complaintId}")
    public ResponseEntity<Void> updateComplaint(
                Authentication authentication,
            @PathVariable Long complaintId,
            @Valid @RequestBody ComplaintRequest request
    ) {

            String email = authentication.getName();
            Long userId = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."))
                    .getId();

        complaintService.updateDraft(
                userId,
                complaintId,
                request
        );

        return ResponseEntity.noContent().build();
    }


    //4. 최종 민원 제출
    @PostMapping("/{complaintId}/send")
    public ResponseEntity<Void> submitComplaint(
                Authentication authentication,
            @PathVariable Long complaintId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {

            String email = authentication.getName();
            Long userId = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."))
                    .getId();

        complaintService.submitComplaint(
                userId,
                complaintId,
                idempotencyKey
        );

        return ResponseEntity.ok().build();
    }
}