package kr.securityadvisor.llm.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 모델 출력의 마지막 그물(원칙 3): IP·대역처럼 보이는 토큰이 서버가 넘긴 사실 값에 없으면 "근거 없는 내부 정보"로 본다.
 * 신청자용 제안에는 사실을 넘기지 않으므로 IP 가 보이면 무조건 거부된다.
 */
@Component
public class InternalInfoGuard {

    private static final Pattern IP_LIKE = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?:/\\d{1,2})?\\b");

    /** 허용되지 않은 IP 류 토큰 목록(비어 있으면 통과). */
    public List<String> findUnbacked(String text, Collection<String> knownValues) {
        if (text == null) {
            return List.of();
        }
        List<String> unbacked = new ArrayList<>();
        Matcher m = IP_LIKE.matcher(text);
        while (m.find()) {
            String token = m.group();
            boolean backed = knownValues.stream().anyMatch(v -> v != null && v.contains(token));
            if (!backed && !unbacked.contains(token)) {
                unbacked.add(token);
            }
        }
        return unbacked;
    }
}
