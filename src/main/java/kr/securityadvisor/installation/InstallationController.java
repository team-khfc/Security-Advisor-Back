package kr.securityadvisor.installation;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 기관 표시 정보(단일 자원, 인증 없이 읽기 전용 — 기관명·코드만, AGENTS.md 5장). */
@RestController
@RequiredArgsConstructor
public class InstallationController {

    private final InstallationProperties properties;

    @GetMapping("/api/installation")
    public Map<String, String> get() {
        return Map.of("name", properties.displayName(), "code", properties.code() == null ? "" : properties.code());
    }
}
