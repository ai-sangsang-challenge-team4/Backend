package com.teacherhub.complaint.mediation.controller;

import com.teacherhub.common.exception.ErrorResponse;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.mediation.dto.MediationPackageCreateRequest;
import com.teacherhub.complaint.mediation.dto.MediationPackageDownload;
import com.teacherhub.complaint.mediation.dto.MediationPackageResponse;
import com.teacherhub.complaint.mediation.service.MediationPackageService;
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
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/teacher/complaints/{complaintId}/mediation-packages")
@Tag(name = "Teacher Mediation Package", description = "교사 대응 기록 패키지 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "담당 교사가 아니거나 제출 전 민원",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "민원 또는 패키지를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TeacherMediationPackageController {

    private final MediationPackageService mediationPackageService;
    private final UserRepository userRepository;

    @Operation(summary = "대응 기록 패키지 생성", description = "현재 민원의 마스킹본·분석·수정·대응 이력을 불변 JSON 스냅샷으로 저장합니다. 원문은 명시적으로 요청한 경우에만 포함합니다.")
    @PostMapping
    public ResponseEntity<MediationPackageResponse> create(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable Long complaintId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MediationPackageCreateRequest request
    ) {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(mediationPackageService.create(
                        userId(authentication),
                        complaintId,
                        request.includeOriginalMessages(),
                        idempotencyKey
                ));
    }

    @Operation(summary = "대응 기록 패키지 조회", description = "생성 시점에 저장된 불변 스냅샷을 조회합니다.")
    @GetMapping("/{packageId}")
    public ResponseEntity<MediationPackageResponse> find(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable Long complaintId,
            @PathVariable String packageId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mediationPackageService.find(
                        userId(authentication),
                        complaintId,
                        packageId
                ));
    }

    @Operation(summary = "대응 기록 패키지 다운로드", description = "조회 API와 동일한 스냅샷 JSON을 첨부 파일로 다운로드합니다.")
    @GetMapping(value = "/{packageId}/download", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> download(
            @Parameter(hidden = true) Authentication authentication,
            @PathVariable Long complaintId,
            @PathVariable String packageId
    ) {
        MediationPackageDownload download = mediationPackageService.download(
                userId(authentication),
                complaintId,
                packageId
        );
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-SHA256", download.checksumSha256())
                .contentType(MediaType.APPLICATION_JSON)
                .body(download.content());
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
