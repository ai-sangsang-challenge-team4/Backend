package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import java.util.List;

public interface RuleBasedRiskDetector {
    List<RiskTagDetectionResult> detect(String content);
}
