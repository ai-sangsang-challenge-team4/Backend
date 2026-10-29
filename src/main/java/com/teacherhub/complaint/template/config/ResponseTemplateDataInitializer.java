package com.teacherhub.complaint.template.config;

import com.teacherhub.complaint.template.entity.ResponseTemplate;
import com.teacherhub.complaint.template.entity.ResponseTemplateVersion;
import com.teacherhub.complaint.template.repository.ResponseTemplateRepository;
import com.teacherhub.complaint.template.repository.ResponseTemplateVersionRepository;
import com.teacherhub.risk.enums.RiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
@RequiredArgsConstructor
public class ResponseTemplateDataInitializer implements CommandLineRunner {
    private static final String HIGH_ACKNOWLEDGEMENT = "HIGH_ACKNOWLEDGEMENT";

    private final ResponseTemplateRepository templateRepository;
    private final ResponseTemplateVersionRepository versionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        ResponseTemplate template = templateRepository.findByCode(HIGH_ACKNOWLEDGEMENT)
                .orElseGet(() -> templateRepository.save(new ResponseTemplate(
                        HIGH_ACKNOWLEDGEMENT,
                        "High 민원 접수 및 확인 안내",
                        RiskLevel.HIGH
                )));

        if (versionRepository.findFirstByTemplate_CodeOrderByVersionDesc(HIGH_ACKNOWLEDGEMENT).isEmpty()) {
            versionRepository.save(new ResponseTemplateVersion(
                    template,
                    1,
                    "안녕하세요, {{parentName}} 보호자님. 전달해 주신 {{issueSummary}} 내용을 중요하게 확인하고 있습니다. "
                            + "현재 {{nextAction}} 절차를 진행 중이며, 확인되는 내용은 {{contactPlan}} 방식으로 안내드리겠습니다.",
                    true
            ));
        }
    }
}
