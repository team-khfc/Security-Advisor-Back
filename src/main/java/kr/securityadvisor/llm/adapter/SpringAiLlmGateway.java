package kr.securityadvisor.llm.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.service.LlmConfigurationSelector;
import kr.securityadvisor.llm.service.LlmGateway;
import kr.securityadvisor.llm.service.LlmPrompt;
import kr.securityadvisor.llm.service.LlmResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI 2.0 구현(live 모드). DB 구성마다 OpenAI 호환 ChatModel 을 런타임에 만들어 캐시한다 — 자동 구성 빈은 쓰지 않는다.
 * 이 패키지만 org.springframework.ai 를 import 한다(ArchUnit).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "securityadvisor.llm", name = "mode", havingValue = "live", matchIfMissing = true)
@RequiredArgsConstructor
public class SpringAiLlmGateway implements LlmGateway {

    private final LlmConfigurationSelector selector;
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

    @Override
    public LlmResult complete(LlmPurpose purpose, LlmPrompt prompt) {
        LlmConfigurationSelector.Selected selected = selector.select(purpose);
        LlmConfiguration configuration = selected.configuration();
        ChatClient client = clients.computeIfAbsent(
                configuration.getId() + ":" + configuration.getRevision(),
                key -> build(configuration, selected.apiKey()));
        try {
            String text = client.prompt().messages(messagesOf(prompt)).call().content();
            if (text == null || text.isBlank()) {
                throw new ServiceUnavailableException("모델이 빈 응답을 반환했습니다: " + configuration.reference());
            }
            return new LlmResult(text, configuration.reference());
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // 프롬프트·응답 원문은 로그에 남기지 않는다(불변식 25). 구성 참조와 예외 종류만.
            log.warn(
                    "LLM 호출 실패 {} ({})", configuration.reference(), e.getClass().getSimpleName());
            throw new ServiceUnavailableException("LLM 호출에 실패했습니다: " + configuration.reference(), e);
        }
    }

    private static ChatClient build(LlmConfiguration configuration, String apiKey) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(configuration.getBaseUrl())
                .apiKey(apiKey)
                .model(configuration.getModel())
                .temperature(configuration.getTemperature().doubleValue())
                .maxTokens(configuration.getMaxTokens())
                .timeout(Duration.ofSeconds(configuration.getTimeoutSeconds()))
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder().options(options).build();
        return ChatClient.builder(model).build();
    }

    private static List<Message> messagesOf(LlmPrompt prompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(PromptAssembler.systemText(prompt)));
        if (prompt.history() != null) {
            for (LlmPrompt.LlmMessage m : prompt.history()) {
                messages.add(
                        "ASSISTANT".equalsIgnoreCase(m.role())
                                ? new AssistantMessage(m.text())
                                : new UserMessage(m.text()));
            }
        }
        messages.add(new UserMessage(prompt.user()));
        return messages;
    }
}
