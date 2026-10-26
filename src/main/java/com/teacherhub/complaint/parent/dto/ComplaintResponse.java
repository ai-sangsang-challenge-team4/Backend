package com.teacherhub.complaint.parent.dto;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ComplaintResponse {
    private Long complaintId;
    private ComplaintStatus status;
    private WaitingFor waitingFor;
}
