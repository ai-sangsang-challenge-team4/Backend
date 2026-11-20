package com.teacherhub.complaint.auto;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplaintAutoClosureServiceTest {

    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final ComplaintEventRepository events = mock(ComplaintEventRepository.class);
    private final ComplaintAutoClosureService service = new ComplaintAutoClosureService(complaints, events);

    @Test
    void closesOnlyCandidatesStillExpiredAfterAcquiringLock() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 11, 20, 9, 0);
        LocalDateTime closedAt = LocalDateTime.of(2026, 11, 23, 9, 0);
        Complaint expired = mock(Complaint.class);
        Complaint changedConcurrently = mock(Complaint.class);
        when(complaints.findAnsweredIdsForAutoClose(eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));
        when(complaints.findForUpdate(1L)).thenReturn(Optional.of(expired));
        when(complaints.findForUpdate(2L)).thenReturn(Optional.of(changedConcurrently));
        when(expired.isAnsweredNoLaterThan(cutoff)).thenReturn(true);
        when(changedConcurrently.isAnsweredNoLaterThan(cutoff)).thenReturn(false);
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int closedCount = service.closeExpiredBatch(cutoff, closedAt, 100);

        assertThat(closedCount).isEqualTo(1);
        verify(expired).closeAutomatically(closedAt);
        verify(changedConcurrently, never()).closeAutomatically(any());
        verify(events).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getPreviousStatus() == ComplaintStatus.ANSWERED
                        && event.getNewStatus() == ComplaintStatus.CLOSED
                        && "SYSTEM".equals(event.getActorName())));
    }
}
