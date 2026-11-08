package kr.securityadvisor.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 문서 머리말. 계약 파일(docs/api/openapi.yaml)은 이 문서를 추출한 것이다(docs/api/README.md). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI securityAdvisorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SecurityAdvisor(보안길잡이) API")
                        .version("0.1")
                        .description("공공기관 보안 서류(방화벽신청서·보안성검토 신청서) 작성·협의·제출·확정 API. "
                                + "세션 쿠키 인증 + CSRF(XSRF-TOKEN 쿠키 ↔ X-XSRF-TOKEN 헤더). 오류는 ProblemDetail."));
    }
}
