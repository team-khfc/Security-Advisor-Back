package kr.securityadvisor.installation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영 기관 정보(설치당 1곳, 불변식 36). 기관 테이블·엔티티는 없고 {@code .env} 설정값만 있다.
 *
 * @param name 기관명(화면·서류 머리말)
 * @param code 기관 코드
 */
@ConfigurationProperties(prefix = "securityadvisor.installation")
public record InstallationProperties(String name, String code) {

    public String displayName() {
        return name == null || name.isBlank() ? "(기관명 미설정)" : name;
    }
}
