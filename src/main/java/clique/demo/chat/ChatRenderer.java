package clique.demo.chat;

import io.github.kusoroadeolu.clique.Clique;
import io.github.kusoroadeolu.clique.configuration.BoxType;
import io.github.kusoroadeolu.clique.configuration.FrameAlign;
import io.github.kusoroadeolu.clique.style.Ink;

import java.util.Map;

public final class ChatRenderer {

    private ChatRenderer() {}

    public static void welcome(ChatConfig config) {
        var modelInfo = Clique.ink().yellow().on("Model: ") + Clique.ink().brightWhite().bold().on(config.model());
        var helpHint = Clique.ink().brightBlack().on("Type ") + Clique.ink().brightWhite().on("/help") +
                Clique.ink().brightBlack().on(" for commands · ") + Clique.ink().brightWhite().on("/exit") +
                Clique.ink().brightBlack().on(" to quit");

        Clique.frame(BoxType.ROUNDED, "blue")
                .title(Clique.ink().cyan().bold().on("Clique Chat · MiMo AI"))
                .nest(modelInfo, FrameAlign.LEFT)
                .nest(helpHint, FrameAlign.LEFT)
                .render();

        System.out.println();
    }

    public static void userPrompt() {
        System.out.print(Clique.ink().green().bold().on("> "));
    }

    public static void aiLabel(String model) {
        System.out.println();
        System.out.print(Clique.ink().cyan().bold().on("MiMo"));
        System.out.print(Clique.ink().brightBlack().on(" · " + model));
        System.out.println();
        System.out.println(Clique.ink().brightBlack().on("───"));
    }

    public static void aiLabelStreaming(String model) {
        System.out.print(Clique.ink().cyan().bold().on("MiMo"));
        System.out.print(Clique.ink().brightBlack().on(" · " + model));
        System.out.println();
        System.out.print(Clique.ink().brightBlack().on("─── "));
    }

    public static void turnSeparator() {
        System.out.println();
        System.out.println(Clique.ink().brightBlack().on("─".repeat(60)));
        System.out.println();
    }

    public static void info(String message) {
        System.out.println(Clique.ink().brightBlack().on("  " + message));
    }

    public static void error(String message) {
        System.out.println(Clique.ink().red().bold().on("Error: ") + Clique.ink().red().on(message));
    }

    public static void success(String message) {
        System.out.println(Clique.ink().green().on("  " + message));
    }

    public static void toolCallBanner(ToolCall toolCall, int callNumber, DangerLevel level) {
        String label = dangerLabel(level);
        String banner = "[" + label + "] [TOOL #" + callNumber + "] " + toolCall.name();
        System.out.println();
        System.out.println(dangerInk(level).bold().on(banner));
        for (Map.Entry<String, String> arg : toolCall.arguments().entrySet()) {
            System.out.println(Clique.ink().brightBlack().on("  " + arg.getKey() + " = " + arg.getValue()));
        }
    }

    public static void toolResult(ToolCall toolCall, String result, boolean success) {
        String color = success ? "green" : "red";
        String title = (success ? "Result: " : "Error: ") + toolCall.name();
        String display = result;
        if (display != null && display.length() > 2000) {
            display = display.substring(0, 2000) + "\n... (truncated)";
        }
        String titleInk = success
                ? Clique.ink().green().bold().on(title)
                : Clique.ink().red().bold().on(title);
        Clique.frame(BoxType.ROUNDED, color)
                .title(titleInk)
                .nest(display != null ? display : "(no output)", FrameAlign.LEFT)
                .render();
        System.out.println();
    }

    public static boolean toolApprovalPrompt(ToolCall toolCall, DangerLevel level) {
        String label = dangerLabel(level);
        Ink dangerInk = dangerInk(level);

        System.out.print(dangerInk.bold().on("  [" + label + "] Execute ")
                + Clique.ink().brightWhite().bold().on(toolCall.name())
                + Clique.ink().brightBlack().on("("));
        boolean first = true;
        for (Map.Entry<String, String> arg : toolCall.arguments().entrySet()) {
            if (!first) System.out.print(Clique.ink().brightBlack().on(", "));
            System.out.print(Clique.ink().brightWhite().on(arg.getKey())
                    + Clique.ink().brightBlack().on("=")
                    + Clique.ink().brightWhite().on(arg.getValue()));
            first = false;
        }
        System.out.print(Clique.ink().brightBlack().on(")"));
        System.out.print(dangerInk.bold().on("? (y/n): "));
        System.out.flush();

        return readYesNo();
    }

    private static boolean readYesNo() {
        try {
            String line;
            if (System.console() != null) {
                line = System.console().readLine();
            } else {
                line = new java.util.Scanner(System.in).nextLine();
            }
            return line != null && (line.trim().equalsIgnoreCase("y")
                    || line.trim().equalsIgnoreCase("yes"));
        } catch (Exception e) {
            return false;
        }
    }

    private static String dangerLabel(DangerLevel level) {
        switch (level) {
            case LOW: return "低危";
            case MEDIUM: return "中危";
            case HIGH: return "高危";
            default: return "未知";
        }
    }

    private static Ink dangerInk(DangerLevel level) {
        switch (level) {
            case LOW: return Clique.ink().green();
            case MEDIUM: return Clique.ink().yellow();
            case HIGH: return Clique.ink().red();
            default: return Clique.ink().brightBlack();
        }
    }

    public static void showTools(ToolRegistry registry) {
        System.out.println();
        System.out.println(Clique.ink().yellow().bold().on("Registered tools:"));
        for (Tool tool : registry.all()) {
            String levelStr;
            switch (tool.dangerLevel(java.util.Collections.emptyMap())) {
                case LOW: levelStr = Clique.ink().green().on("[低危]"); break;
                case MEDIUM: levelStr = Clique.ink().yellow().on("[中危]"); break;
                case HIGH: levelStr = Clique.ink().red().on("[高危]"); break;
                default: levelStr = ""; break;
            }
            StringBuilder params = new StringBuilder();
            for (Tool.ParameterSpec p : tool.parameters().values()) {
                if (params.length() > 0) params.append(", ");
                params.append(p.name()).append(": ").append(p.type());
            }
            System.out.println("  " + levelStr + " "
                    + Clique.ink().brightWhite().bold().on(tool.name())
                    + Clique.ink().brightBlack().on("(" + params + ")"));
            System.out.println(Clique.ink().brightBlack().on("    " + tool.description()));
        }
        System.out.println();
    }

    public static void showDanger() {
        System.out.println();
        Clique.frame(BoxType.ROUNDED, "green")
                .title(Clique.ink().green().bold().on("Risk Levels"))
                .nest(Clique.ink().green().bold().on("[低危] ")
                        + Clique.ink().brightWhite().on("Read / Create")
                        + Clique.ink().brightBlack().on("  — ls, dir, cat, read_file, creating new files"),
                        FrameAlign.LEFT)
                .nest(Clique.ink().yellow().bold().on("[中危] ")
                        + Clique.ink().brightWhite().on("Modify / Overwrite")
                        + Clique.ink().brightBlack().on("  — write_file (overwrite), edit_file, mv, cp"),
                        FrameAlign.LEFT)
                .nest(Clique.ink().red().bold().on("[高危] ")
                        + Clique.ink().brightWhite().on("Delete / Destroy")
                        + Clique.ink().brightBlack().on("  — rm, del, rmdir, format"),
                        FrameAlign.LEFT)
                .render();
        System.out.println();
    }

    public static void showHelp() {
        System.out.println();
        Clique.frame(BoxType.ROUNDED, "yellow")
                .title(Clique.ink().yellow().bold().on("Commands"))
                .nest(Clique.ink().brightWhite().bold().on("/exit, /quit") +
                        Clique.ink().brightBlack().on("    Exit the chat"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/clear") +
                        Clique.ink().brightBlack().on("          Clear conversation history"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/model <name>") +
                        Clique.ink().brightBlack().on("    Switch AI model"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/models") +
                        Clique.ink().brightBlack().on("          List available models"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/stream on|off") +
                        Clique.ink().brightBlack().on("  Toggle streaming mode"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/system <text>") +
                        Clique.ink().brightBlack().on("  Set system prompt"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/config") +
                        Clique.ink().brightBlack().on("          Show current configuration"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/tools [on|off]") +
                        Clique.ink().brightBlack().on("  Manage tool calling"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/mcp list|connect|reload|github|tencent") +
                        Clique.ink().brightBlack().on("  MCP management"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/mcp connect [http-sse|streamable-http|stdio]") +
                        Clique.ink().brightBlack().on(""), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/danger") +
                        Clique.ink().brightBlack().on("          Show risk level descriptions"), FrameAlign.LEFT)
                .nest(Clique.ink().brightWhite().bold().on("/help") +
                        Clique.ink().brightBlack().on("            Show this help"), FrameAlign.LEFT)
                .render();
        System.out.println();
    }

    public static void showConfig(ChatConfig config) {
        System.out.println();
        Clique.frame(BoxType.ROUNDED, "blue")
                .title(Clique.ink().blue().bold().on("Configuration"))
                .nest("Model:       " + config.model(), FrameAlign.LEFT)
                .nest("API Base:    " + config.apiBaseUrl(), FrameAlign.LEFT)
                .nest("API Key:     " + maskKey(config.apiKey()), FrameAlign.LEFT)
                .nest("Streaming:   " + (config.streaming() ? "on" : "off"), FrameAlign.LEFT)
                .nest("Max Tokens:  " + config.maxTokens(), FrameAlign.LEFT)
                .nest("Temperature: " + config.temperature(), FrameAlign.LEFT)
                .render();
        System.out.println();
    }

    public static void showModels() {
        System.out.println();
        System.out.println(Clique.ink().yellow().bold().on("Available models:"));
        System.out.println(Clique.ink().brightWhite().on("  mimo-v2-flash") +
                Clique.ink().brightBlack().on("  — Fast, lightweight (309B MoE, 256K context)"));
        System.out.println(Clique.ink().brightWhite().on("  mimo-v2-pro") +
                Clique.ink().brightBlack().on("    — Flagship, deep reasoning (1T MoE, 1M context)"));
        System.out.println(Clique.ink().brightWhite().on("  mimo-v2-omni") +
                Clique.ink().brightBlack().on("   — Multimodal (text + image + video + audio)"));
        System.out.println();
    }

    public static void goodbye() {
        System.out.println();
        System.out.println(Clique.ink().brightBlack().on("  Goodbye!"));
    }

    private static String maskKey(String key) {
        if (key == null || key.length() <= 8) return "[REDACTED]";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
