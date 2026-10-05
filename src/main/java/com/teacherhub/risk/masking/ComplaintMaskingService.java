package com.teacherhub.risk.masking;

import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.dto.response.MaskingResponse;
import lombok.RequiredArgsConstructor;
import com.teacherhub.complaint.exception.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

// AI 분석을 위해 민원을 마스킹
public class ComplaintMaskingService {

    private final ComplaintRepository complaints;
    private final PiiMaskingService masking;

    @Transactional
    public MaskingResponse mask(Long userId, Long complaintId) {

        var complaint = complaints.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException( "민원을 찾을 수 없습니다."));

        checkOwner(userId, complaint.getParent().getUser().getId());

        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new ComplaintNotDraftException( "작성 중인 민원만 마스킹할 수 있습니다.");
        }

        if (complaint.getContent() == null || complaint.getContent().isBlank()) {
            throw new EmptyComplaintContentException( "마스킹할 민원 내용이 없습니다.");
        }

        String masked = masking.mask(complaint.getContent());
        complaint.updateMaskedContent(masked);

        return new MaskingResponse(complaintId, complaint.getContentVersion(), masked);
    }

    private void checkOwner(Long userId, Long ownerId) {
        if (!ownerId.equals(userId)) {
            throw new ComplaintAccessDeniedException( "해당 민원에 접근할 권한이 없습니다.");
        }
    }
}
