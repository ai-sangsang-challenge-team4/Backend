package com.teacherhub.complaint.controller;

import com.teacherhub.complaint.dto.ComplaintRequest;
import com.teacherhub.complaint.dto.ComplaintResponse;
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
    private final UserRepository userRepository;


    //민원 작성 DRAFT 상태로 저장
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


    //민원 내용 수정 (DRAFT 상태에서만 가능)
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

        complaintService.updateDraft(userId, complaintId, request);

        return ResponseEntity.noContent().build();
    }


    //최종 민원 제출
    @PostMapping("/{complaintId}/send")
    public ResponseEntity<Void> submitComplaint(
                Authentication authentication,
            @PathVariable Long complaintId
    ) {

            String email = authentication.getName();
            Long userId = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."))
                    .getId();

        complaintService.submitComplaint(userId, complaintId);

        return ResponseEntity.ok().build();
    }
}