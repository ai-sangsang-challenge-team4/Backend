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
        RiskTagCode.PROFANITY,
        "욕설",
        15
));

riskTagRepository.save(new RiskTag(
        RiskTagCode.INSULT,
        "인격 모욕",
        15
));

riskTagRepository.save(new RiskTag(
        RiskTagCode.THREAT,
        "위험·협박",
        30
));

riskTagRepository.save(new RiskTag(
        RiskTagCode.LEGAL,
        "법적 조치 언급",
        10
));

riskTagRepository.save(new RiskTag(
        RiskTagCode.EXPOSURE,
        "공개·유포 위험",
        20
));

riskTagRepository.save(new RiskTag(
        RiskTagCode.REPEAT,
        "반복 민원",
        10
));

riskTagRepository.save(new RiskTag(
        RiskTagCode.UNFAIR_REQUEST,
        "부당 요구",
        15
));

riskTagRepository.save(new RiskTag(
        RiskTagCode.PRIVACY,
        "개인정보 위험",
        20
));
    }
}