package com.teacherhub.risk.enums;

public enum RiskTagState {
    PRESENT,
    ABSENT,
    BOUNDARY,
    UNKNOWN;

    public boolean isPresent() {
        return this == PRESENT;
    }
}
