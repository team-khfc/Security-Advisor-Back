package kr.securityadvisor.llm.adapter;

import com.openai.errors.OpenAIServiceException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.service.LlmConnectionProbe;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/** 고정 합성 문장만 보내고 반환된 원문은 저장·로그·화면에 복제하지 않는다. */
@Component
public class SpringAiConnectionProbe implements LlmConnectionProbe {
    @Override
    public Outcome test(LlmConfiguration configuration, String key) {
        try {
            var options = OpenAiChatOptions.builder()
                    .baseUrl(OpenAiEndpoint.baseUrl(configuration.getBaseUrl()))
                    .apiKey(key)
                    .model(configuration.getModel())
                    .temperature(configuration.getTemperature().doubleValue())
                    .maxTokens(Math.min(512, configuration.getMaxTokens()))
                    .timeout(Duration.ofSeconds(Math.min(30, configuration.getTimeoutSeconds())))
                    .maxRetries(0)
                    .build();
            var client = ChatClient.builder(
                            OpenAiChatModel.builder().options(options).build())
                    .build();
            String response = client.prompt()
                    .user("이것은 합성 연결 시험입니다. 개인정보나 기관 자료는 없습니다. '연결 확인'이라고 짧게 답해 주세요.")
                    .call()
                    .content();
            return response == null || response.isBlank() ? Outcome.EMPTY_RESPONSE : Outcome.CONNECTED;
        } catch (RuntimeException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException)
                    return Outcome.TIMEOUT;
                if (cause instanceof OpenAIServiceException service
                        && (service.statusCode() == 401 || service.statusCode() == 403))
                    return Outcome.AUTHENTICATION_FAILED;
                if (cause instanceof RestClientResponseException http
                        && (http.getStatusCode().value() == 401
                                || http.getStatusCode().value() == 403)) return Outcome.AUTHENTICATION_FAILED;
            }
            return Outcome.UNAVAILABLE;
        }
    }
}
