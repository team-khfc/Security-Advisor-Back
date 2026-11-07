package kr.securityadvisor.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 ID 를 만들어 응답 헤더({@code X-Request-Id})·로그(MDC)·감사 이력에 공통으로 쓴다.
 * 클라이언트가 보낸 X-Request-Id 는 형식이 맞을 때만(영숫자·하이픈 64자 이하) 그대로 쓴다.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = (incoming != null && incoming.matches("^[A-Za-z0-9-]{1,64}$"))
                ? incoming
                : UUID.randomUUID().toString();
        RequestIdHolder.set(requestId);
        MDC.put("requestId", requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            RequestIdHolder.clear();
        }
    }
}
