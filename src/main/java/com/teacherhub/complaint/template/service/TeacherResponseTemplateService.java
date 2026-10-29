package com.teacherhub.complaint.template.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.exception.ResponseNotEditableException;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.template.dto.ResponseTemplateAdoptionRequest;
import com.teacherhub.complaint.template.dto.ResponseTemplateAdoptionResponse;
import com.teacherhub.complaint.template.dto.ResponseTemplateResponse;
import com.teacherhub.complaint.template.entity.ResponseTemplateAdoption;
import com.teacherhub.complaint.template.entity.ResponseTemplateVersion;
import com.teacherhub.complaint.template.repository.ResponseTemplateAdoptionRepository;
import com.teacherhub.complaint.template.repository.ResponseTemplateVersionRepository;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherResponseTemplateService {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final EmergencyRiskReviewRequestRepository emergencyReviewRequestRepository;
    private final ResponseTemplateVersionRepository versionRepository;
    private final ResponseTemplateAdoptionRepository adoptionRepository;
    private final ComplaintEventRepository eventRepository;

    @Transactional(noRollbackFor = ResponseNotEditableException.class)
    public List<ResponseTemplateResponse> findAvailable(Long userId, Long complaintId) {
        Complaint complaint = findUnlockedComplaint(findUser(userId), complaintId, false);
        var analysis = latestCompletedAnalysis(complaintId);
        if (analysis.isEmpty()) {
            return List.of();
        }
        requireHighRisk(analysis.get());
        return latestActiveVersions().stream()
                .map(version -> ResponseTemplateResponse.from(
                        version,
                        requiredVariables(version.getContentTemplate()).stream().toList()
                ))
                .toList();
    }

    @Transactional(noRollbackFor = ResponseNotEditableException.class)
    public ResponseTemplateAdoptionResponse adopt(
            Long userId,
            Long complaintId,
            Long versionId,
            ResponseTemplateAdoptionRequest request
    ) {
        User user = findUser(userId);
        Complaint complaint = findUnlockedComplaint(user, complaintId, true);
        requireHighRisk(latestCompletedAnalysis(complaintId)
                .orElseThrow(() -> new ResponseNotEditableException("완료된 위험 분석이 필요합니다.")));
        ResponseTemplateVersion version = versionRepository.findById(versionId)
                .filter(ResponseTemplateVersion::isActive)
                .filter(candidate -> candidate.getTemplate().getRiskLevel() == RiskLevel.HIGH)
                .orElseThrow(() -> new ResponseNotEditableException("사용 가능한 High 답변 템플릿을 찾을 수 없습니다."));
        String rendered = render(version.getContentTemplate(), request.variables());
        ResponseTemplateAdoption saved = adoptionRepository.saveAndFlush(new ResponseTemplateAdoption(
                complaint,
                user,
                version,
                rendered,
                request.finalContent().trim()
        ));
        eventRepository.save(ComplaintEvent.responseTemplateAdopted(
                complaint,
                user,
                version.getId(),
                version.getTemplate().getCode() + " v" + version.getVersion()
        ));
        return ResponseTemplateAdoptionResponse.from(saved);
    }

    private Complaint findUnlockedComplaint(User user, Long complaintId, boolean forUpdate) {
        Complaint complaint = (forUpdate
                ? complaintRepository.findForUpdate(complaintId)
                : complaintRepository.findDetailById(complaintId))
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        if (complaint.getStatus() != ComplaintStatus.OPEN
                || complaint.getWaitingFor() != WaitingFor.TEACHER) {
            throw new ResponseNotEditableException("답변 대기 중인 민원에서만 제한형 템플릿을 사용할 수 있습니다.");
        }
        if (emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(complaintId)
                .map(EmergencyRiskReviewRequest::isSafetyLock)
                .orElse(false)) {
            eventRepository.save(ComplaintEvent.responseBlockedBySafetyLock(complaint, user, "High 답변 템플릿 사용"));
            throw new ResponseNotEditableException("Emergency 안전 잠금 중에는 답변 템플릿을 사용할 수 없습니다.");
        }
        return complaint;
    }

    private Optional<RiskAnalysis> latestCompletedAnalysis(Long complaintId) {
        return riskAnalysisRepository
                .findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                        complaintId,
                        MessageStatus.DRAFT
                );
    }

    private void requireHighRisk(RiskAnalysis analysis) {
        var review = riskReviewRepository
                .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                .orElse(null);
        RiskLevel effectiveLevel = review == null ? analysis.getRiskLevel() : review.getReviewedLevel();
        if (effectiveLevel != RiskLevel.HIGH) {
            throw new ResponseNotEditableException("High 위험도 민원에서만 제한형 템플릿을 사용할 수 있습니다.");
        }
    }

    private List<ResponseTemplateVersion> latestActiveVersions() {
        Set<Long> seenTemplates = new LinkedHashSet<>();
        return versionRepository
                .findByActiveTrueAndTemplate_RiskLevelOrderByTemplate_NameAscVersionDesc(RiskLevel.HIGH)
                .stream()
                .filter(version -> seenTemplates.add(version.getTemplate().getId()))
                .toList();
    }

    private Set<String> requiredVariables(String template) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(template);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private String render(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("템플릿 변수 값이 필요합니다: " + matcher.group(1));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value.trim()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }
}
