package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.RiskDetectionResult;
import java.util.List;

public interface RuleBasedRiskDetector {
    List<RiskDetectionResult> detect(String content);
}
