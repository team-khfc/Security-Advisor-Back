package kr.securityadvisor.llm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.dto.LlmComparisonResponse.Change;
import kr.securityadvisor.llm.dto.LlmConfigurationCommand;
import kr.securityadvisor.llm.dto.LlmConfigurationValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 저장·비교·시험·복원이 동일한 정규화와 입력 검증을 공유한다. */
@Component
@RequiredArgsConstructor
public class LlmConfigurationValuesService {
    private final JsonCodec json;
    private final LlmProperties properties;
    private final LlmCredentialService credentials;

    /** 외부 호출 전에 권한·기준 버전·설치 정책을 검증한다. */
    public LlmConfiguration candidate(LlmConfigurationCommand c, LlmConfiguration existing) {
        credentials.requireAllowed(c.apiKeyEnv());
        if (c.purposes() == null
                || c.purposes().isEmpty()
                || c.purposes().size() > 4
                || c.purposes().contains(null)) throw new DomainRuleViolationException("용도를 1개 이상 4개 이하로 선택해 주세요");
        int priority = c.priority() == null ? (existing == null ? 100 : existing.getPriority()) : c.priority();
        if (priority < 0 || priority > 10000) throw new DomainRuleViolationException("우선순위는 0~10000이어야 합니다");
        BigDecimal temperature = c.temperature() == null
                ? (existing == null ? new BigDecimal("0.20") : existing.getTemperature())
                : c.temperature();
        if (temperature.scale() > 2) throw new DomainRuleViolationException("응답 다양성은 소수 둘째 자리까지 입력해 주세요");
        var purposes = c.purposes().stream().map(Enum::name).distinct().sorted().toList();
        LlmConfiguration value = LlmConfiguration.create(
                c.name(),
                c.provider(),
                c.baseUrl(),
                c.model(),
                c.apiKeyEnv(),
                json.writeStrings(purposes),
                true,
                temperature,
                c.maxTokens() == null ? (existing == null ? 1024 : existing.getMaxTokens()) : c.maxTokens(),
                c.timeoutSeconds() == null
                        ? (existing == null ? 60 : existing.getTimeoutSeconds())
                        : c.timeoutSeconds(),
                priority,
                properties.allowedHostSet());
        boolean active = c.active() == null ? existing == null || existing.isActive() : c.active();
        value.update(
                value.getName(),
                value.getProvider(),
                value.getBaseUrl(),
                value.getModel(),
                value.getApiKeyEnv(),
                value.getPurposes(),
                true,
                value.getTemperature(),
                value.getMaxTokens(),
                value.getTimeoutSeconds(),
                value.getPriority(),
                active,
                properties.allowedHostSet());
        return value;
    }

    /** 키 참조를 포함한 비교 가능 값만 사본으로 분리한다. */
    public LlmConfigurationValues values(LlmConfiguration c) {
        return LlmConfigurationValues.from(
                c, json.readStrings(c.getPurposes()).stream().sorted().toList());
    }

    /** 검증된 후보를 도메인 메서드로 적용한다. */
    public void apply(LlmConfiguration target, LlmConfiguration candidate) {
        target.update(
                candidate.getName(),
                candidate.getProvider(),
                candidate.getBaseUrl(),
                candidate.getModel(),
                candidate.getApiKeyEnv(),
                candidate.getPurposes(),
                true,
                candidate.getTemperature(),
                candidate.getMaxTokens(),
                candidate.getTimeoutSeconds(),
                candidate.getPriority(),
                candidate.isActive(),
                properties.allowedHostSet());
    }

    /** 사용자에게 의미 있는 변경만 한글 비교 항목으로 제공한다. */
    public List<Change> changes(LlmConfigurationValues before, LlmConfigurationValues after) {
        List<Change> changes = new ArrayList<>();
        add(changes, "name", "구성 이름", before == null ? null : before.name(), after.name());
        add(changes, "baseUrl", "연결 주소", before == null ? null : before.baseUrl(), after.baseUrl());
        add(changes, "model", "모델", before == null ? null : before.model(), after.model());
        add(changes, "apiKeyEnv", "키 참조", before == null ? null : before.apiKeyEnv(), after.apiKeyEnv());
        add(changes, "purposes", "사용 용도", before == null ? null : labels(before.purposes()), labels(after.purposes()));
        add(changes, "temperature", "응답 다양성", before == null ? null : before.temperature(), after.temperature());
        add(changes, "maxTokens", "응답 토큰 한도", before == null ? null : before.maxTokens(), after.maxTokens());
        add(
                changes,
                "timeoutSeconds",
                "시간 제한(초)",
                before == null ? null : before.timeoutSeconds(),
                after.timeoutSeconds());
        add(changes, "priority", "우선순위", before == null ? null : before.priority(), after.priority());
        add(changes, "active", "사용 상태", before == null ? null : state(before.active()), state(after.active()));
        return List.copyOf(changes);
    }

    private static String state(boolean active) {
        return active ? "사용" : "사용 중지";
    }

    private static String labels(List<String> purposes) {
        return String.join(
                ", ",
                purposes.stream()
                        .sorted()
                        .map(p -> switch (p) {
                            case "INTERVIEW" -> "인터뷰";
                            case "SUMMARY" -> "문서 요약";
                            case "DOCUMENT" -> "초안 생성";
                            case "INQUIRY" -> "담당자 질의";
                            default -> p;
                        })
                        .toList());
    }

    private static void add(List<Change> out, String key, String label, Object before, Object after) {
        String a = before instanceof BigDecimal n
                ? n.stripTrailingZeros().toPlainString()
                : Objects.toString(before, "없음");
        String b =
                after instanceof BigDecimal n ? n.stripTrailingZeros().toPlainString() : Objects.toString(after, "없음");
        if (!a.equals(b)) out.add(new Change(key, label, a, b));
    }
}
