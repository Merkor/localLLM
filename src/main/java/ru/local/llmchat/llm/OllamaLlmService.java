package ru.local.llmchat.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import ru.local.llmchat.config.AppLlmProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OllamaLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmService.class);

    private final WebClient ollamaWebClient;
    private final AppLlmProperties properties;

    public OllamaLlmService(WebClient ollamaWebClient, AppLlmProperties properties) {
        this.ollamaWebClient = ollamaWebClient;
        this.properties = properties;
    }

    @Override
    public Flux<LlmStreamEvent> streamChat(List<LlmMessage> messages) {
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "stream", true,
                "keep_alive", properties.getKeepAlive(),
                "messages", messages.stream()
                        .map(message -> Map.of("role", message.role(), "content", message.content()))
                        .toList(),
                "options", Map.of("temperature", properties.getTemperature())
        );

        return ollamaWebClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .flatMap(this::toEvents)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .onErrorResume(ex -> {
                    log.error("Ollama stream failed", ex);
                    return Flux.just(LlmStreamEvent.error(mapError(ex.getMessage())));
                });
    }

    @Override
    public String suggestChatTitle(String userMessage, String assistantMessage) {
        String prompt = """
                Generate a short chat title (2-6 words) for this conversation.
                Return only the title without quotes, markdown, or punctuation at the end.
                User message:
                %s

                Assistant answer:
                %s
                """.formatted(userMessage, assistantMessage);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "You create concise, meaningful chat titles."),
                Map.of("role", "user", "content", prompt)
        );

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "stream", false,
                "keep_alive", properties.getKeepAlive(),
                "messages", messages,
                "options", Map.of("temperature", 0.2)
        );

        try {
            JsonNode response = ollamaWebClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();

            if (response == null || response.hasNonNull("error")) {
                return null;
            }

            String title = response.path("message").path("content").asText("").strip();
            return normalizeTitle(title);
        } catch (Exception ex) {
            log.warn("Failed to generate chat title", ex);
            return null;
        }
    }

    private Flux<LlmStreamEvent> toEvents(JsonNode node) {
        if (node.hasNonNull("error")) {
            return Flux.just(LlmStreamEvent.error(node.get("error").asText()));
        }

        String chunk = node.path("message").path("content").asText("");
        boolean done = node.path("done").asBoolean(false);

        Flux<LlmStreamEvent> tokenFlux = chunk.isBlank()
                ? Flux.empty()
                : Flux.just(LlmStreamEvent.token(chunk));

        if (!done) {
            return tokenFlux;
        }

        long totalDurationNanos = node.path("total_duration").asLong(0L);
        long durationMs = totalDurationNanos > 0 ? totalDurationNanos / 1_000_000 : 0L;
        int totalTokens = node.path("prompt_eval_count").asInt(0) + node.path("eval_count").asInt(0);
        LlmStreamEvent complete = LlmStreamEvent.complete(durationMs > 0 ? durationMs : null,
                totalTokens > 0 ? totalTokens : null);

        return tokenFlux.concatWithValues(complete);
    }

    private String mapError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Ollama is unavailable or returned an error.";
        }
        return raw;
    }

    private String normalizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.replace("\"", "")
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .strip();

        if (value.endsWith(".") || value.endsWith("!") || value.endsWith("?")) {
            value = value.substring(0, value.length() - 1).strip();
        }

        if (value.isBlank()) {
            return null;
        }
        if (value.length() > 60) {
            return value.substring(0, 60).strip();
        }
        return value;
    }
}
