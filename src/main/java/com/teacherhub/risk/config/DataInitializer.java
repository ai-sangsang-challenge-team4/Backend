package com.teacherhub.risk.config;

import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.repository.RiskTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RiskTagRepository riskTagRepository;

    @Override
    public void run(String... args) {
        Map<RiskTagCode, TagDefinition> definitions = new LinkedHashMap<>();
        definitions.put(RiskTagCode.PROFANITY, new TagDefinition("욕설", 8));
        definitions.put(RiskTagCode.INSULT, new TagDefinition("인격 모욕", 8));
        definitions.put(RiskTagCode.THREAT, new TagDefinition("위험·협박", 16));
        definitions.put(RiskTagCode.LEGAL, new TagDefinition("법적 조치 언급", 5));
        definitions.put(RiskTagCode.EXPOSURE, new TagDefinition("공개·유포 위험", 10));
        definitions.put(RiskTagCode.REPEAT, new TagDefinition("반복 민원", 5));
        definitions.put(RiskTagCode.UNFAIR_REQUEST, new TagDefinition("부당 요구", 8));
        definitions.put(RiskTagCode.PRIVACY, new TagDefinition("개인정보 위험", 10));

        definitions.forEach((code, definition) -> {
            RiskTag riskTag = riskTagRepository.findByCode(code)
                    .orElseGet(() -> new RiskTag(code, definition.name(), definition.score()));
            riskTag.updateDefaultScore(definition.score());
            riskTagRepository.save(riskTag);
        });
    }

    private record TagDefinition(String name, int score) {
    }
}
