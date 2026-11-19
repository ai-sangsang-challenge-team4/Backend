package com.teacherhub.complaint.teacher.policy;

import com.teacherhub.complaint.teacher.dto.TeacherAllowedAction;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeacherSupportPolicy {

    // 민원의 위험 수준에 따른 허용된 행동을 결정하는 정책
    public List<TeacherAllowedAction> allowedActions(RiskAnalysis analysis) {
        return allowedActions(analysis.getRiskLevel());
    }

    public List<TeacherAllowedAction> allowedActions(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW, MEDIUM -> List.of();
            case HIGH -> List.of(
                    TeacherAllowedAction.USE_RESPONSE_TEMPLATE,
                    TeacherAllowedAction.CREATE_EVIDENCE_PACKAGE,
                    TeacherAllowedAction.REQUEST_ADMIN_REVIEW
            );
            case EMERGENCY -> List.of(
                    TeacherAllowedAction.CREATE_EVIDENCE_PACKAGE,
                    TeacherAllowedAction.REQUEST_ADMIN_REVIEW,
                    TeacherAllowedAction.VIEW_OFFICIAL_PROCEDURE
            );
        };
    }
}
