package com.teacherhub.risk.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext.HistoryMessage;
import com.teacherhub.risk.masking.PiiMaskingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class RepeatAnalysisContextFactory {

    private final ComplaintMessageRepository messageRepository;
    private final PiiMaskingService piiMaskingService;
    private final int windowDays;
    private final int repeatThreshold;
    private final int maxMessages;
    private final int maxCharacters;
    private final Clock clock;

    @Autowired
    public RepeatAnalysisContextFactory(
            ComplaintMessageRepository messageRepository,
            PiiMaskingService piiMaskingService,
            @Value("${risk.repeat.window-days:7}") int windowDays,
            @Value("${risk.repeat.threshold:3}") int repeatThreshold,
            @Value("${risk.repeat.max-messages:20}") int maxMessages,
            @Value("${risk.repeat.max-characters:8000}") int maxCharacters
    ) {
        this(
                messageRepository,
                piiMaskingService,
                windowDays,
                repeatThreshold,
                maxMessages,
                maxCharacters,
                Clock.systemDefaultZone()
        );
    }

    RepeatAnalysisContextFactory(
            ComplaintMessageRepository messageRepository,
            PiiMaskingService piiMaskingService,
            int windowDays,
            int repeatThreshold,
            int maxMessages,
            int maxCharacters,
            Clock clock
    ) {
        if (windowDays < 1 || windowDays > 365) {
            throw new IllegalArgumentException("반복 민원 이력 기간은 1~365일이어야 합니다.");
        }
        if (repeatThreshold < 2 || repeatThreshold > 100) {
            throw new IllegalArgumentException("반복 민원 판정 기준은 2~100건이어야 합니다.");
        }
        if (maxMessages < 1 || maxMessages > 100) {
            throw new IllegalArgumentException("반복 민원 이력 메시지 제한은 1~100개여야 합니다.");
        }
        if (maxCharacters < 1_000 || maxCharacters > 50_000) {
            throw new IllegalArgumentException("반복 민원 이력 글자 제한은 1,000~50,000자여야 합니다.");
        }

        this.messageRepository = messageRepository;
        this.piiMaskingService = piiMaskingService;
        this.windowDays = windowDays;
        this.repeatThreshold = repeatThreshold;
        this.maxMessages = maxMessages;
        this.maxCharacters = maxCharacters;
        this.clock = clock;
    }

    public RepeatAnalysisContext create(
            Complaint complaint,
            ComplaintMessage currentMessage
    ) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(windowDays);
        List<ComplaintMessage> history = messageRepository.findRiskAnalysisHistory(
                complaint.getId(),
                MessageStatus.DRAFT,
                cutoff,
                currentMessage.getId()
        );

        int parentCount = (int) history.stream()
                .filter(message -> message.getType() == MessageType.PARENT_INQUIRY)
                .count();
        int teacherCount = (int) history.stream()
                .filter(message -> message.getType() == MessageType.TEACHER_REPLY)
                .count();
        int parentCountIncludingCurrent = parentCount
                + (currentMessage.getType() == MessageType.PARENT_INQUIRY ? 1 : 0);

        List<SelectedMessage> selectedNewestFirst = selectWithinLimits(history);
        boolean historyTruncated = selectedNewestFirst.size() < history.size()
                || selectedNewestFirst.stream().anyMatch(SelectedMessage::contentTruncated);

        Collections.reverse(selectedNewestFirst);
        List<HistoryMessage> messages = new ArrayList<>(selectedNewestFirst.size());
        int includedCharacters = 0;

        for (int index = 0; index < selectedNewestFirst.size(); index++) {
            SelectedMessage selected = selectedNewestFirst.get(index);
            includedCharacters += selected.content().codePointCount(0, selected.content().length());
            messages.add(new HistoryMessage(
                    "H" + (index + 1),
                    selected.message().getType() == MessageType.PARENT_INQUIRY
                            ? "PARENT"
                            : "TEACHER",
                    selected.message().getSentAt().toString(),
                    selected.content(),
                    selected.contentTruncated()
            ));
        }

        return new RepeatAnalysisContext(
                true,
                windowDays,
                repeatThreshold,
                parentCountIncludingCurrent,
                parentCountIncludingCurrent >= repeatThreshold,
                handlingStatus(complaint),
                parentCount,
                teacherCount,
                history.size(),
                messages.size(),
                includedCharacters,
                historyTruncated,
                messages
        );
    }

    private List<SelectedMessage> selectWithinLimits(List<ComplaintMessage> history) {
        List<SelectedMessage> selected = new ArrayList<>();
        int remainingCharacters = maxCharacters;

        for (ComplaintMessage message : history) {
            if (selected.size() >= maxMessages || remainingCharacters == 0) {
                break;
            }

            String masked = piiMaskingService.mask(message.getContent());
            int characterCount = masked.codePointCount(0, masked.length());
            boolean truncated = characterCount > remainingCharacters;
            String content = truncated
                    ? truncateByCodePoints(masked, remainingCharacters)
                    : masked;

            selected.add(new SelectedMessage(message, content, truncated));
            remainingCharacters -= content.codePointCount(0, content.length());
        }

        return selected;
    }

    private String handlingStatus(Complaint complaint) {
        return complaint.getStatus().name() + "_WAITING_FOR_" + complaint.getWaitingFor().name();
    }

    private String truncateByCodePoints(String value, int maxCodePoints) {
        int endIndex = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, endIndex);
    }

    private record SelectedMessage(
            ComplaintMessage message,
            String content,
            boolean contentTruncated
    ) {
    }
}
