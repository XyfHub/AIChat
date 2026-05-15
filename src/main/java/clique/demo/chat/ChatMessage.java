package clique.demo.chat;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ChatMessage {
    private final String role;
    private final String content;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;

    private ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls != null ? Collections.unmodifiableList(toolCalls) : null;
        this.toolCallId = toolCallId;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage assistantWithToolCalls(List<ToolCall> toolCalls, String content) {
        return new ChatMessage("assistant", content, toolCalls, null);
    }

    public static ChatMessage tool(String toolCallId, String result) {
        return new ChatMessage("tool", result, null, toolCallId);
    }

    public String role() { return role; }
    public String content() { return content; }
    public List<ToolCall> toolCalls() { return toolCalls; }
    public String toolCallId() { return toolCallId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage)) return false;
        ChatMessage that = (ChatMessage) o;
        return Objects.equals(role, that.role)
                && Objects.equals(content, that.content)
                && Objects.equals(toolCalls, that.toolCalls)
                && Objects.equals(toolCallId, that.toolCallId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content, toolCalls, toolCallId);
    }

    @Override
    public String toString() {
        return role + ": " + (content != null ? content : "(tool_calls)");
    }
}
