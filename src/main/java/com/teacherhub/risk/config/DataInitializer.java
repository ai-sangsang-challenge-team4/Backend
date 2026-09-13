package com.teacherhub.risk.config;

import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.repository.RiskTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RiskTagRepository riskTagRepository;

    @Override
    public void run(String... args) {

        if (riskTagRepository.count() > 0) {
            return;
        }

        riskTagRepository.save(new RiskTag(
                RiskTagCode.EMOTIONAL_EXPRESSION,
                "감정적 표현",
                0
        ));

        riskTagRepository.save(new RiskTag(
                RiskTagCode.PERSONAL_ATTACK,
                "인격적 공격",
                0
        ));

        riskTagRepository.save(new RiskTag(
                RiskTagCode.THREAT,
                "위협",
                0
        ));

        riskTagRepository.save(new RiskTag(
                RiskTagCode.LEGAL_ADMINISTRATIVE_ACTION,
                "법적·행정적 조치 언급",
                0
        ));

        riskTagRepository.save(new RiskTag(
                RiskTagCode.PUBLIC_DISCLOSURE_THREAT,
                "공개·유포 위협",
                0
        ));

        riskTagRepository.save(new RiskTag(
                RiskTagCode.REPETITION,
                "반복 민원",
                0
        ));

        riskTagRepository.save(new RiskTag(
                RiskTagCode.UNREASONABLE_DEMAND,
                "부당 요구",
                0
        ));

        riskTagRepository.save(new RiskTag(
                RiskTagCode.PERSONAL_INFORMATION_RISK,
                "개인정보 위험",
                0
        ));
    }
}