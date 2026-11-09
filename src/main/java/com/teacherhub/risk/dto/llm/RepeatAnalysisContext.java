package com.teacherhub.risk.dto.llm;

import java.util.List;

/**
 * 개인정보를 제거한 동일 민원 이력과 서버가 계산한 처리 상태입니다.
 * 외부 LLM은 이 문맥을 사용해 REPEAT 태그의 동일 요구 여부만 판단합니다.
 */
public record RepeatAnalysisContext(
        boolean historyAvailable,
        int windowDays,
        String currentHandlingStatus,
        int previousParentMessageCount,
        int teacherSentReplyCount,
        int totalMessagesInWindow,
        int includedMessages,
        int includedCharacters,
        boolean historyTruncated,
        List<HistoryMessage> messages
) {
    public RepeatAnalysisContext {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public record HistoryMessage(
            String reference,
            String role,
            String sentAt,
            String content,
            boolean contentTruncated
    ) {
    }
}
