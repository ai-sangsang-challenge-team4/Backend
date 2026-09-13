package com.teacherhub.risk.dto;

import com.teacherhub.risk.enums.RiskTagCode;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RiskTagRequest(
        @NotEmpty
        List<RiskTagCode> tags
) {
}