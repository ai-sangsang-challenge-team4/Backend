package com.teacherhub.risk.masking;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.parent.service.ParentComplaintService;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.dto.request.MaskingPreviewRequest;
import com.teacherhub.risk.dto.response.MaskingPreviewResponse;
import com.teacherhub.risk.dto.response.MaskingResponse;
import lombok.RequiredArgsConstructor;
import com.teacherhub.complaint.exception.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.regex.Pattern;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

// AI 분석을 위해 민원을 마스킹
public class ComplaintMaskingService {

    private static final Pattern LABEL = Pattern.compile(
            "\\[(?:학생명|제3자 학생명|교사명|사용자명|학번|교직원번호|전화번호|이메일|주민등록번호|학생정보)]"
    );

    private final ComplaintRepository complaints;
    private final ParentComplaintService parentComplaints;
    private final PiiMaskingService masking;
    private final ComplaintPiiMaskingContextFactory maskingContextFactory;

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

        String masked = masking.mask(
                complaint.getContent(),
                maskingContextFactory.create(complaint)
        );
        complaint.updateMaskedContent(masked);

        return new MaskingResponse(complaintId, complaint.getContentVersion(), masked);
    }

    public MaskingPreviewResponse preview(Long userId, MaskingPreviewRequest request) {
        PiiMaskingContext context = previewContext(userId, request);
        String title = request.title() == null ? "" : request.title();
        String content = request.content() == null ? "" : request.content();
        String maskedTitle = title.isBlank() ? title : masking.mask(title, context);
        String maskedContent = content.isBlank() ? content : masking.mask(content, context);
        return new MaskingPreviewResponse(maskedTitle, maskedContent,
                maskedCount(title, maskedTitle), maskedCount(content, maskedContent));
    }

    private PiiMaskingContext previewContext(Long userId, MaskingPreviewRequest request) {
        if (request.complaintId() != null) {
            if (request.studentId() != null || request.teacherId() != null) {
                throw new IllegalArgumentException("기존 문의에는 문의 ID만 지정해 주세요.");
            }
            var complaint = complaints.findById(request.complaintId())
                    .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
            var parent = complaint.getParent().getUser();
            checkOwner(userId, parent.getId());
            return maskingContextFactory.create(complaint)
                    .withAdditionalNames(Collections.singletonList(parent.getName()));
        }
        if (request.studentId() == null || request.teacherId() == null) {
            throw new IllegalArgumentException("문의할 자녀와 담당 교사를 선택해 주세요.");
        }
        var recipient = parentComplaints.findDraftRecipient(userId, request.studentId(), request.teacherId());
        return maskingContextFactory.create(recipient.student(), recipient.teacher())
                .withAdditionalNames(Collections.singletonList(recipient.parent().getUser().getName()));
    }

    private long maskedCount(String original, String masked) {
        return Math.max(0, LABEL.matcher(masked).results().count() - LABEL.matcher(original).results().count());
    }

    private void checkOwner(Long userId, Long ownerId) {
        if (!ownerId.equals(userId)) {
            throw new ComplaintAccessDeniedException( "해당 민원에 접근할 권한이 없습니다.");
        }
    }
}
