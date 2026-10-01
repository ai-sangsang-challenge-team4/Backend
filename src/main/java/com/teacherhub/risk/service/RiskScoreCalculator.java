package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.FinalRiskTagResponse;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RiskScoreCalculator {

    public int calculate( List<FinalRiskTagResponse> tags, Map<RiskTagCode, RiskTag> definitions
    ) {
        return tags.stream()
                .filter(FinalRiskTagResponse::detected)
                .map(FinalRiskTagResponse::code)
                .distinct()
                .mapToInt(code -> {
                    RiskTag tag = definitions.get(code);
                    if (tag == null
                            || tag.getDefaultScore() == null
                            || tag.getDefaultScore() < 0) {
                        throw new IllegalStateException(
                                "위험 태그의 점수 설정을 확인해주세요: " + code
                        );
                    }
                    return tag.getDefaultScore();
                })
                .sum();
    }

    public RiskLevel levelFor(int score) {
        if (score < 0) {
            throw new IllegalArgumentException(
                    "위험 점수는 음수일 수 없습니다."
            );
        }
        if (score <= 1) {
            return RiskLevel.LOW;
        }
        if (score <= 4) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }
}