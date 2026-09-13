package com.teacherhub.privacy;

import org.springframework.stereotype.Service;

@Service
public class PiiMaskingServiceImpl implements PiiMaskingService {
    @Override
    public String mask(String content){
        return content;
    }
}
