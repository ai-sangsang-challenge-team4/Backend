package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.RiskTagList;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.repository.RiskTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
// 위험 태그 조회
public class RiskTagService {

    private final RiskTagRepository riskTagRepository;

    @Transactional(readOnly = true)
    public List<RiskTagList> getRiskTags(List<RiskTagCode> codes) {

        return codes.stream()
                .map(this::getRiskTagResponse)
                .toList();
    }

    private RiskTagList getRiskTagResponse(RiskTagCode code) {

        RiskTag riskTag = riskTagRepository.findByCode(code)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 위험 요소입니다: " + code
                        )
                );

        return new RiskTagList(
                riskTag.getId(),
                riskTag.getCode(),
                riskTag.getName(),
                riskTag.getDefaultScore()
        );
    }

    @Transactional(readOnly = true)
    public List<RiskTagList> getAllRiskTags() {
        return riskTagRepository.findAll()
                .stream()
                .map(riskTag -> new RiskTagList(
                        riskTag.getId(),
                        riskTag.getCode(),
                        riskTag.getName(),
                        riskTag.getDefaultScore()
                ))
                .toList();
    }
}
