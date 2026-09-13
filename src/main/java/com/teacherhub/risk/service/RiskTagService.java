package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.RiskTagListResponse;
import com.teacherhub.risk.dto.RiskTagResponse;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.repository.RiskTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.teacherhub.risk.dto.RiskTagListResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskTagService {

    private final RiskTagRepository riskTagRepository;

    @Transactional(readOnly = true)
    public List<RiskTagResponse> getRiskTags(List<RiskTagCode> codes) {

        return codes.stream()
                .map(this::getRiskTagResponse)
                .toList();
    }

    private RiskTagResponse getRiskTagResponse(RiskTagCode code) {

        RiskTag riskTag = riskTagRepository.findByCode(code)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 위험 요소입니다: " + code
                        )
                );

        return new RiskTagResponse(
                riskTag.getCode(),
                true,
                false,
                false,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
public List<RiskTagListResponse> getAllRiskTags() {

    return riskTagRepository.findAll()
            .stream()
            .map(riskTag -> new RiskTagListResponse(
                    riskTag.getId(),
                    riskTag.getCode(),
                    riskTag.getName(),
                    riskTag.getDefaultScore()
            ))
            .toList();
}
}