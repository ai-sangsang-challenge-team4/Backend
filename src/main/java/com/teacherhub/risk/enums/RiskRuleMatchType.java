package com.teacherhub.risk.enums;

public enum RiskRuleMatchType {
    KEYWORD,
    PHRASE,
    PATTERN,
    HINT_ONLY;

    public boolean isHintOnly() {
        return this == HINT_ONLY;
    }
}
