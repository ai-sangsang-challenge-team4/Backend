package com.teacherhub.complaint.dto;

import com.teacherhub.complaint.entity.ComplaintStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ComplaintResponse {
    private Long complaintId;
    private ComplaintStatus status;
}
