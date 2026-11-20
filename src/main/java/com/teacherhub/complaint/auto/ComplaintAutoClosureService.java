package com.teacherhub.complaint.auto;

import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ComplaintAutoClosureService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository eventRepository;

    @Transactional
    public int closeExpiredBatch(LocalDateTime cutoff, LocalDateTime closedAt, int batchSize) {
        var candidateIds = complaintRepository.findAnsweredIdsForAutoClose(
                cutoff,
                PageRequest.of(0, batchSize)
        );
        int closedCount = 0;
        for (Long complaintId : candidateIds) {
            var complaint = complaintRepository.findForUpdate(complaintId).orElse(null);
            if (complaint == null || !complaint.isAnsweredNoLaterThan(cutoff)) {
                continue;
            }
            complaint.closeAutomatically(closedAt);
            eventRepository.save(ComplaintEvent.statusChanged(
                    complaint,
                    null,
                    ComplaintStatus.ANSWERED,
                    ComplaintStatus.CLOSED
            ));
            closedCount++;
        }
        return closedCount;
    }
}
