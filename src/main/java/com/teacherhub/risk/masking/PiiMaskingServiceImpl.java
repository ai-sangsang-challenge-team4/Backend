package com.teacherhub.risk.masking;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class PiiMaskingServiceImpl implements PiiMaskingService {

    private static final Pattern RESIDENT_ID = Pattern.compile("(?<![0-9])[0-9]{6}[-\\s]?[1-8][0-9]{6}(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+82[-\\s]?(?:10|2|[3-6][1-5]|70)|0(?:1[016789]|2|[3-6][1-5]|70))[-\\s]?[0-9]{3,4}[-\\s]?[0-9]{4}(?![0-9])");

    @Override
    public String mask(String content){

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("마스킹할 텍스트가 없습니다.");
        }

        String masked = RESIDENT_ID.matcher(content).replaceAll("[주민등록번호]");
        masked = EMAIL.matcher(masked).replaceAll("[이메일]");

        return PHONE.matcher(masked).replaceAll("[전화번호]");
    }
}
