package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.enums.RiskTagCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockRuleBasedRiskDetector implements RuleBasedRiskDetector {

    @Override
    public List<RiskTagDetectionResult> detect(String content) {
        // 연결 확인용 고정 결과. 실제 내용은 분석하지 않습니다.
        return List.of(
                new RiskTagDetectionResult(RiskTagCode.PROFANITY, true, null,
                        "[MOCK RULE] 욕설 예시", "[MOCK RULE] 욕설 패턴이 확인되었습니다."),
                new RiskTagDetectionResult(RiskTagCode.THREAT, true, null,
                        "[MOCK RULE] 협박 예시", "[MOCK RULE] 위협을 나타내는 패턴이 확인되었습니다.")
        );
    }
}
