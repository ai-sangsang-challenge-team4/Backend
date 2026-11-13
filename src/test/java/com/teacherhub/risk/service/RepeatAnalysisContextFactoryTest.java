package com.teacherhub.risk.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.risk.masking.PiiMaskingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepeatAnalysisContextFactoryTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-09T03:00:00Z"),
            SEOUL
    );

    private final ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
    private final PiiMaskingService masking = mock(PiiMaskingService.class);

    @Test
    void sendsMaskedHistoryInChronologicalOrderWithServerCalculatedState() {
        Complaint complaint = complaint();
        ComplaintMessage current = currentMessage();
        ComplaintMessage parent = historyMessage(
                MessageType.PARENT_INQUIRY,
                "010-1234-5678로 연락주세요",
                LocalDateTime.of(2026, 11, 1, 10, 0)
        );
        ComplaintMessage teacher = historyMessage(
                MessageType.TEACHER_REPLY,
                "확인 후 답변드렸습니다",
                LocalDateTime.of(2026, 11, 2, 9, 0)
        );

        when(messages.findRiskAnalysisHistory(
                eq(42L),
                eq(MessageStatus.DRAFT),
                any(LocalDateTime.class),
                eq(99L)
        )).thenReturn(List.of(teacher, parent));
        when(masking.mask(eq("010-1234-5678로 연락주세요"), any()))
                .thenReturn("[PHONE]로 연락주세요");
        when(masking.mask(eq("확인 후 답변드렸습니다"), any()))
                .thenReturn("확인 후 답변드렸습니다");

        var context = factory(7, 3, 20, 8_000).create(complaint, current);

        assertThat(context.historyAvailable()).isTrue();
        assertThat(context.windowDays()).isEqualTo(7);
        assertThat(context.repeatThreshold()).isEqualTo(3);
        assertThat(context.currentHandlingStatus()).isEqualTo("OPEN_WAITING_FOR_TEACHER");
        assertThat(context.previousParentMessageCount()).isEqualTo(1);
        assertThat(context.parentMessageCountIncludingCurrent()).isEqualTo(2);
        assertThat(context.repeatThresholdMet()).isFalse();
        assertThat(context.teacherSentReplyCount()).isEqualTo(1);
        assertThat(context.totalMessagesInWindow()).isEqualTo(2);
        assertThat(context.includedMessages()).isEqualTo(2);
        assertThat(context.historyTruncated()).isFalse();
        assertThat(context.messages())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly(
                        "PARENT:[PHONE]로 연락주세요",
                        "TEACHER:확인 후 답변드렸습니다"
                );

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(messages).findRiskAnalysisHistory(
                eq(42L),
                eq(MessageStatus.DRAFT),
                cutoff.capture(),
                eq(99L)
        );
        assertThat(cutoff.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 2, 12, 0));
    }

    @Test
    void classifiesAsRepeatWhenCurrentMessageMakesThreeParentMessagesWithinSevenDays() {
        ComplaintMessage previousOne = historyMessage(
                MessageType.PARENT_INQUIRY,
                "첫 번째 문의",
                LocalDateTime.of(2026, 11, 4, 10, 0)
        );
        ComplaintMessage teacher = historyMessage(
                MessageType.TEACHER_REPLY,
                "답변",
                LocalDateTime.of(2026, 11, 5, 10, 0)
        );
        ComplaintMessage previousTwo = historyMessage(
                MessageType.PARENT_INQUIRY,
                "두 번째 문의",
                LocalDateTime.of(2026, 11, 8, 10, 0)
        );

        when(messages.findRiskAnalysisHistory(any(), any(), any(), any()))
                .thenReturn(List.of(previousTwo, teacher, previousOne));
        when(masking.mask(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var context = factory(7, 3, 20, 8_000)
                .create(complaint(), currentMessage());

        assertThat(context.previousParentMessageCount()).isEqualTo(2);
        assertThat(context.parentMessageCountIncludingCurrent()).isEqualTo(3);
        assertThat(context.teacherSentReplyCount()).isEqualTo(1);
        assertThat(context.repeatThresholdMet()).isTrue();
    }

    @Test
    void keepsOnlyTheTwentyMostRecentMessages() {
        List<ComplaintMessage> history = new ArrayList<>();
        LocalDateTime newest = LocalDateTime.of(2026, 11, 9, 11, 0);

        for (int index = 0; index < 25; index++) {
            history.add(historyMessage(
                    MessageType.PARENT_INQUIRY,
                    "message-" + index,
                    newest.minusMinutes(index)
            ));
        }

        when(messages.findRiskAnalysisHistory(any(), any(), any(), any()))
                .thenReturn(history);
        when(masking.mask(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var context = factory(7, 3, 20, 8_000).create(complaint(), currentMessage());

        assertThat(context.totalMessagesInWindow()).isEqualTo(25);
        assertThat(context.includedMessages()).isEqualTo(20);
        assertThat(context.historyTruncated()).isTrue();
        assertThat(context.messages())
                .extracting(message -> message.content())
                .containsExactly(
                        "message-19", "message-18", "message-17", "message-16", "message-15",
                        "message-14", "message-13", "message-12", "message-11", "message-10",
                        "message-9", "message-8", "message-7", "message-6", "message-5",
                        "message-4", "message-3", "message-2", "message-1", "message-0"
                );
    }

    @Test
    void truncatesOlderContentWhenTheCharacterBudgetIsReached() {
        String newestContent = "나".repeat(900);
        String olderContent = "가".repeat(500);
        ComplaintMessage newest = historyMessage(
                MessageType.PARENT_INQUIRY,
                newestContent,
                LocalDateTime.of(2026, 11, 9, 11, 0)
        );
        ComplaintMessage older = historyMessage(
                MessageType.PARENT_INQUIRY,
                olderContent,
                LocalDateTime.of(2026, 11, 8, 11, 0)
        );

        when(messages.findRiskAnalysisHistory(any(), any(), any(), any()))
                .thenReturn(List.of(newest, older));
        when(masking.mask(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var context = factory(7, 3, 20, 1_000).create(complaint(), currentMessage());

        assertThat(context.includedCharacters()).isEqualTo(1_000);
        assertThat(context.historyTruncated()).isTrue();
        assertThat(context.messages()).hasSize(2);
        assertThat(context.messages().get(0).content()).hasSize(100);
        assertThat(context.messages().get(0).contentTruncated()).isTrue();
        assertThat(context.messages().get(1).content()).hasSize(900);
        assertThat(context.messages().get(1).contentTruncated()).isFalse();
    }

    private RepeatAnalysisContextFactory factory(
            int windowDays,
            int repeatThreshold,
            int maxMessages,
            int maxCharacters
    ) {
        return new RepeatAnalysisContextFactory(
                messages,
                masking,
                windowDays,
                repeatThreshold,
                maxMessages,
                maxCharacters,
                CLOCK
        );
    }

    private Complaint complaint() {
        Complaint complaint = mock(Complaint.class);
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.TEACHER);
        return complaint;
    }

    private ComplaintMessage currentMessage() {
        ComplaintMessage message = mock(ComplaintMessage.class);
        when(message.getId()).thenReturn(99L);
        when(message.getType()).thenReturn(MessageType.PARENT_INQUIRY);
        return message;
    }

    private ComplaintMessage historyMessage(
            MessageType type,
            String content,
            LocalDateTime sentAt
    ) {
        ComplaintMessage message = mock(ComplaintMessage.class);
        when(message.getType()).thenReturn(type);
        when(message.getContent()).thenReturn(content);
        when(message.getSentAt()).thenReturn(sentAt);
        return message;
    }
}
