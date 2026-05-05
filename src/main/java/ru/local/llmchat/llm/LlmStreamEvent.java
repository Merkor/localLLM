package ru.local.llmchat.llm;

public record LlmStreamEvent(
        Type type,
        String content,
        Long durationMs,
        Integer totalTokens,
        String error
) {
    public enum Type {
        TOKEN,
        COMPLETE,
        ERROR
    }

    public static LlmStreamEvent token(String content) {
        return new LlmStreamEvent(Type.TOKEN, content, null, null, null);
    }

    public static LlmStreamEvent complete(Long durationMs, Integer totalTokens) {
        return new LlmStreamEvent(Type.COMPLETE, "", durationMs, totalTokens, null);
    }

    public static LlmStreamEvent error(String error) {
        return new LlmStreamEvent(Type.ERROR, "", null, null, error);
    }
}
