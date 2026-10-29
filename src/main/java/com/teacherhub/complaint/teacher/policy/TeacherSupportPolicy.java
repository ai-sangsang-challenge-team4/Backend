package com.teacherhub.complaint.teacher.policy;

import com.teacherhub.complaint.teacher.dto.AllowedAction;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeacherSupportPolicy {

    // 민원의 위험 수준에 따른 허용된 행동을 결정하는 정책
    public List<AllowedAction> allowedActions(RiskAnalysis analysis) {
        return allowedActions(analysis.getRiskLevel());
    }

    public List<AllowedAction> allowedActions(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW, MEDIUM -> List.of();
            case HIGH -> List.of(
                    AllowedAction.USE_RESPONSE_TEMPLATE,
                    AllowedAction.CREATE_EVIDENCE_PACKAGE,
                    AllowedAction.REQUEST_ADMIN_REVIEW
            );
            case EMERGENCY -> List.of(
                    AllowedAction.CREATE_EVIDENCE_PACKAGE,
                    AllowedAction.REQUEST_ADMIN_REVIEW,
                    AllowedAction.VIEW_OFFICIAL_PROCEDURE
            );
        };
    }
}
