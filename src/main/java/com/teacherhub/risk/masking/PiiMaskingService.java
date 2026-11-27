package com.teacherhub.risk.masking;

public interface PiiMaskingService {
    String mask(String content);

    String mask(String content, PiiMaskingContext context);

    PiiMaskingResult maskReversibly(String content, PiiMaskingContext context);
}
