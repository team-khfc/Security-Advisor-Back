package kr.securityadvisor.llm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.RevisionConflictException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * LLM 구성(F-10). API 키는 환경변수 이름만 가진다(비밀값 DB 저장 금지). base_url 호스트는 허용 목록에 있어야 한다(SSRF 방지).
 * 용도별로 priority 가 낮은 활성 구성부터 쓴다.
 */
@Entity
@Table(name = "llm_configurations")
public class LlmConfiguration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "llm_configuration_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LlmProvider provider;

    @Column(name = "base_url", nullable = false, length = 300)
    private String baseUrl;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "api_key_env", nullable = false, length = 100)
    private String apiKeyEnv;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String purposes;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal temperature;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long revision;

    protected LlmConfiguration() {}

    private LlmConfiguration(
            String name,
            LlmProvider provider,
            String baseUrl,
            String model,
            String apiKeyEnv,
            String purposesJson,
            BigDecimal temperature,
            int maxTokens,
            int timeoutSeconds,
            int priority) {
        this.name = name;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKeyEnv = apiKeyEnv;
        this.purposes = purposesJson;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.priority = priority;
    }

    /**
     * @param allowedHosts 등록 가능한 호스트(소문자). 비어 있으면 어떤 호스트도 허용하지 않는다
     */
    public static LlmConfiguration create(
            String name,
            LlmProvider provider,
            String baseUrl,
            String model,
            String apiKeyEnv,
            String purposesJson,
            boolean hasPurposes,
            BigDecimal temperature,
            int maxTokens,
            int timeoutSeconds,
            int priority,
            Set<String> allowedHosts) {
        validate(
                name,
                provider,
                baseUrl,
                model,
                apiKeyEnv,
                hasPurposes,
                temperature,
                maxTokens,
                timeoutSeconds,
                allowedHosts);
        return new LlmConfiguration(
                name.strip(),
                provider,
                baseUrl.strip(),
                model.strip(),
                apiKeyEnv.strip(),
                purposesJson,
                temperature,
                maxTokens,
                timeoutSeconds,
                priority);
    }

    public void update(
            String name,
            LlmProvider provider,
            String baseUrl,
            String model,
            String apiKeyEnv,
            String purposesJson,
            boolean hasPurposes,
            BigDecimal temperature,
            int maxTokens,
            int timeoutSeconds,
            int priority,
            boolean active,
            Set<String> allowedHosts) {
        validate(
                name,
                provider,
                baseUrl,
                model,
                apiKeyEnv,
                hasPurposes,
                temperature,
                maxTokens,
                timeoutSeconds,
                allowedHosts);
        this.name = name.strip();
        this.provider = provider;
        this.baseUrl = baseUrl.strip();
        this.model = model.strip();
        this.apiKeyEnv = apiKeyEnv.strip();
        this.purposes = purposesJson;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.priority = priority;
        this.active = active;
    }

    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new RevisionConflictException("다른 관리자가 모델 구성을 수정했습니다. 최신 값을 확인해 주세요.", revision);
        }
    }

    /** 호출 직전에도 다시 검사한다 — 허용 목록이 바뀌었을 수 있다. */
    public void requireAllowedHost(Set<String> allowedHosts) {
        String host = hostOf(baseUrl);
        if (!allowedHosts.contains(host)) {
            throw new DomainRuleViolationException("허용되지 않은 LLM 엔드포인트 호스트입니다: " + host);
        }
    }

    public static String hostOf(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.strip());
            if (uri.getHost() == null
                    || (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new DomainRuleViolationException("연결 주소는 사용자 정보·쿼리 없이 http(s)://host 형식이어야 합니다.");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("169.254.") || host.equals("[::ffff:a9fe:a9fe]"))
                throw new DomainRuleViolationException("메타데이터 주소는 모델 엔드포인트로 사용할 수 없습니다");
            return host;
        } catch (IllegalArgumentException e) {
            throw new DomainRuleViolationException("연결 주소 형식이 잘못되었습니다.");
        }
    }

    private static void validate(
            String name,
            LlmProvider provider,
            String baseUrl,
            String model,
            String apiKeyEnv,
            boolean hasPurposes,
            BigDecimal temperature,
            int maxTokens,
            int timeoutSeconds,
            Set<String> allowedHosts) {
        if (name == null || name.isBlank()) {
            throw new DomainRuleViolationException("구성 이름이 필요합니다");
        }
        if (provider == null) {
            throw new DomainRuleViolationException("공급자가 필요합니다");
        }
        if (model == null || model.isBlank()) {
            throw new DomainRuleViolationException("모델명이 필요합니다");
        }
        if (apiKeyEnv == null || !apiKeyEnv.matches("[A-Z][A-Z0-9_]{2,99}")) {
            throw new DomainRuleViolationException("api_key_env 는 환경변수 이름(대문자·숫자·_)이어야 하며 키 값 자체를 넣지 않습니다");
        }
        if (!hasPurposes) {
            throw new DomainRuleViolationException("용도가 하나 이상 필요합니다");
        }
        if (temperature == null || temperature.signum() < 0 || temperature.compareTo(BigDecimal.valueOf(2)) > 0) {
            throw new DomainRuleViolationException("temperature 는 0~2 사이여야 합니다");
        }
        if (maxTokens < 64 || maxTokens > 32_000 || timeoutSeconds < 5 || timeoutSeconds > 600) {
            throw new DomainRuleViolationException("maxTokens 는 64~32000, timeoutSeconds 는 5~600 이어야 합니다");
        }
        String host = hostOf(baseUrl);
        if (!allowedHosts.contains(host)) {
            throw new DomainRuleViolationException("허용되지 않은 LLM 엔드포인트 호스트입니다: " + host);
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public String getPurposes() {
        return purposes;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public long getRevision() {
        return revision;
    }

    /** 감사·근거 표시용 참조(키 없음). */
    public String reference() {
        return name + "/" + model;
    }
}
