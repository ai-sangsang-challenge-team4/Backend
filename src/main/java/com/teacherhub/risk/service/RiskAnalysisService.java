package com.teacherhub.risk.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.risk.dto.*;
import com.teacherhub.risk.entity.*;
import com.teacherhub.risk.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RiskAnalysisService {
    private final RiskScoreCalculator scoreCalculator;
    private final RiskTagRepository tagRepository;
    private final RiskAnalysisRepository analysisRepository;
    private final ComplaintRiskTagRepository resultRepository;

    @Transactional
    public FinalRiskResult calculateAndSave(Complaint complaint, List<FinalRiskTagResponse> tags, LLMRiskAnalysisResult llm) {

        var definitions = tagRepository.findAll().stream()
                .collect(Collectors.toMap(RiskTag::getCode, Function.identity()));

        for (var tag : tags) {
            if (!definitions.containsKey(tag.code())) {
                throw new IllegalStateException("위험 태그 기준 정보가 없습니다: " + tag.code());
            }
        }

        int score = scoreCalculator.calculate(tags, definitions);
        var level = scoreCalculator.levelFor(score);

        var analysis = analysisRepository.save(new RiskAnalysis(complaint, score, level,
                llm.modelName(), llm.temperature(), llm.aiReason()));

        resultRepository.saveAll(tags.stream().map(tag ->
                new ComplaintRiskTag(analysis, definitions.get(tag.code()), tag)).toList());

        return new FinalRiskResult(analysis.getId(), complaint.getId(), tags, score, level, llm.aiReason());
    }
}
