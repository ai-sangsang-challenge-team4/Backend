package com.teacherhub.escalation.controller;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.escalation.dto.EscalationResponse;
import com.teacherhub.escalation.dto.ShareEscalationRequest;
import com.teacherhub.escalation.service.TeacherEscalationService;
import com.teacherhub.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({
        "/teacher/complaints/{complaintId}/escalation",
        "/teacher/complaints/{complaintId}/escalations"
})
public class TeacherEscalationController {

    private final TeacherEscalationService escalationService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<EscalationResponse> share(
            Authentication authentication,
            @PathVariable Long complaintId,
            @Valid @RequestBody ShareEscalationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(escalationService.share(userId(authentication), complaintId, request));
    }

    @GetMapping
    public ResponseEntity<EscalationResponse> findStatus(
            Authentication authentication,
            @PathVariable Long complaintId
    ) {
        return ResponseEntity.ok(escalationService.findStatus(userId(authentication), complaintId));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("인증 정보가 없습니다.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."))
                .getId();
    }
}
