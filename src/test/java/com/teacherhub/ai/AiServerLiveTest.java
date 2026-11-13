package com.teacherhub.ai;

import com.teacherhub.ai.client.AiServerClient;
import com.teacherhub.complaint.teacher.provider.PythonTeacherSupportContentProvider;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.risk.analyzer.LlmRiskOutputValidator;
import com.teacherhub.risk.analyzer.PythonComplaintRevisionGenerator;
import com.teacherhub.risk.analyzer.PythonLlmRiskAnalyzer;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live-ai")
class AiServerLiveTest {

    private final String aiServerUrl = System.getenv()
            .getOrDefault("AI_SERVER_URL", "http://localhost:8000");
    private final AiServerClient client = new AiServerClient(
            aiServerUrl,
            2_000,
            60_000
    );

    @Test
    void springClientsExchangeAllContractsWithDeployedFastApi() {
        String maskedComplaint = "상담 일정을 확인 부탁드립니다.";
        var analyzer = new PythonLlmRiskAnalyzer(client);
        var analysis = analyzer.analyze(
                maskedComplaint,
                List.of(),
                new RepeatAnalysisContext(
                        false,
                        7,
                        3,
                        1,
                        false,
                        "OPEN_WAITING_FOR_TEACHER",
                        0,
                        0,
                        1,
                        0,
                        0,
                        false,
                        List.of()
                )
        );
        LlmRiskOutputValidator.validate(analysis, maskedComplaint);

        var revisionGenerator = new PythonComplaintRevisionGenerator(client);
        var revision = revisionGenerator.generate(
                "[이름] 선생님은 무능합니다.",
                new RiskEvaluation(
                        0L,
                        List.of(new FinalRiskTagResponse(
                                RiskTagCode.INSULT,
                                RiskTagState.PRESENT,
                                RiskTagState.PRESENT,
                                RiskTagState.PRESENT,
                                0.9,
                                1,
                                1,
                                List.of(EvidenceSpan.unlocated("무능")),
                                "모욕 표현"
                        )),
                        10,
                        RiskLevel.MEDIUM
                )
        );

        var teacherSupport = new PythonTeacherSupportContentProvider(client);
        String summary = teacherSupport.createBufferedSummary(
                "학부모가 상담 일정과 조치 내용을 확인해 달라고 요청했습니다."
        );
        var draft = teacherSupport.createResponseDraft(
                "PARENT_INQUIRY: 상담 일정과 조치 내용을 확인해 주세요."
        );

        assertThat(analysis.metadata().modelName()).isNotBlank();
        assertThat(revision.finalRevision().content()).isNotBlank();
        assertThat(summary).isNotBlank();
        assertThat(draft.content()).isNotBlank();
        assertThat(draft.modelName()).isNotBlank();
    }
}
