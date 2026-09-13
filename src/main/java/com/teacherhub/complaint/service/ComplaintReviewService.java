package com.teacherhub.complaint.service;

import com.teacherhub.ai.ComplaintAiAnalyzer;
import com.teacherhub.ai.dto.AiReviewResult;
import com.teacherhub.complaint.dto.ComplaintReviewResponse;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.privacy.PiiMaskingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintReviewService {

    private final ComplaintRepository complaintRepository;
    private final PiiMaskingService piiMaskingService;
    private final ComplaintAiAnalyzer complaintAiAnalyzer;


    public ComplaintReviewResponse review(
            Long userId,
            Long complaintId
    ) {

        // 1. 민원 조회
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "민원 정보를 찾을 수 없습니다."
                        )
                );


        // 2. 로그인한 사용자가 작성한 민원인지 확인
        Long complaintUserId = complaint.getParent()
                .getUser()
                .getId();

        if (!complaintUserId.equals(userId)) {
            throw new IllegalArgumentException(
                    "해당 민원에 접근할 권한이 없습니다."
            );
        }


        // 3. DRAFT 상태인지 확인
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new IllegalStateException(
                    "작성 중인 민원만 AI 검토할 수 있습니다."
            );
        }


        // 4. DB에 저장된 원본 민원 내용
        String originalContent = complaint.getContent();


        // 5. 개인정보 마스킹
        String maskedContent =
                piiMaskingService.mask(originalContent);


        // 6. AI 분석
        AiReviewResult result = complaintAiAnalyzer.analyze(maskedContent);


        // 7. Controller에 반환할 Response 생성
        return new ComplaintReviewResponse(
                originalContent,
                result.getRiskyExpressions().size(),
                result.getRiskyExpressions(),
                result.getPartialRevision(),
                result.getAiRevision()
        );
    }
}