package com.teacherhub.ai;

import com.teacherhub.ai.dto.AiReviewResult;
import com.teacherhub.complaint.dto.RiskyExpressionResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockComplaintAiAnalyzer implements ComplaintAiAnalyzer {

    @Override
    public AiReviewResult analyze(String content) {

        List<RiskyExpressionResponse> riskyExpressions = List.of(
                new RiskyExpressionResponse(

                        "교육청에 신고하겠습니다",
                        "외부 기관에 신고 또는 조치를 요청하는 표현입니다."
                ),
                new RiskyExpressionResponse(
                        "인터넷에 공개하겠습니다",
                        "내용을 외부에 공개하거나 유포할 가능성이 있는 표현입니다."
                )
        );

        return new AiReviewResult(
                riskyExpressions,
                "계속 같은 상황이 발생하면 관련 기관에 문의하겠습니다.",
                "현재 상황에 대해 확인 부탁드리며, 필요한 경우 관련 절차에 따라 문의하고자 합니다."
        );
    }
}