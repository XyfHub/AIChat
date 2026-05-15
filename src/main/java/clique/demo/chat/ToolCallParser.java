package clique.demo.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolCallParser {

    private ToolCallParser() {}

    public static ParseResult parse(String content) {
        if (content == null || content.isEmpty()) {
            return new ParseResult(Collections.emptyList(), content);
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        String remaining = content;

        while (true) {
            int start = remaining.indexOf("<tool_call>");
            if (start < 0) break;

            int end = remaining.indexOf("</tool_call>", start);
            if (end < 0) break;

            String xmlBlock = remaining.substring(start + 11, end);
            ToolCall tc = parseXmlBlock(xmlBlock);
            if (tc != null) {
                toolCalls.add(tc);
            }

            remaining = (start > 0 ? remaining.substring(0, start) : "")
                    + remaining.substring(end + 12);
        }

        if (!toolCalls.isEmpty()) {
            remaining = remaining.trim();
        }

        if (toolCalls.isEmpty()) {
            toolCalls = tryParseJsonToolCalls(content);
        }

        return new ParseResult(toolCalls, remaining);
    }

    private static ToolCall parseXmlBlock(String xml) {
        String name = null;
        Map<String, String> args = new LinkedHashMap<>();

        int funcStart = xml.indexOf("<function=");
        if (funcStart < 0) return null;
        int funcEnd = xml.indexOf(">", funcStart);
        if (funcEnd < 0) return null;
        name = xml.substring(funcStart + 10, funcEnd).trim();
        if (name.isEmpty()) return null;

        int searchFrom = funcEnd + 1;
        while (true) {
            int paramStart = xml.indexOf("<parameter=", searchFrom);
            if (paramStart < 0) break;
            int paramEnd = xml.indexOf(">", paramStart);
            if (paramEnd < 0) break;
            String key = xml.substring(paramStart + 11, paramEnd).trim();

            int valueEnd = xml.indexOf("</parameter>", paramEnd);
            if (valueEnd < 0) break;
            String value = xml.substring(paramEnd + 1, valueEnd).trim();

            args.put(key, value);
            searchFrom = valueEnd + 12;
        }

        if (name == null) return null;
        return new ToolCall(null, name, args);
    }

    private static List<ToolCall> tryParseJsonToolCalls(String content) {
        List<ToolCall> results = new ArrayList<>();
        int searchFrom = 0;
        while (true) {
            int nameIdx = content.indexOf("\"name\":\"", searchFrom);
            if (nameIdx < 0) break;
            int nameStart = nameIdx + 8;
            int nameEnd = content.indexOf("\"", nameStart);
            if (nameEnd < 0) break;
            String name = content.substring(nameStart, nameEnd);

            int argsIdx = content.indexOf("\"arguments\":\"", nameEnd);
            if (argsIdx < 0) {
                searchFrom = nameEnd + 1;
                continue;
            }
            int argsStart = argsIdx + 14;
            int argsEnd = findMatchingQuote(content, argsStart);
            if (argsEnd < 0) {
                searchFrom = argsStart + 1;
                continue;
            }
            String argsJson = unescapeJson(content.substring(argsStart, argsEnd));

            Map<String, String> args = parseJsonArgs(argsJson);
            if (!name.isEmpty()) {
                results.add(new ToolCall(null, name, args));
            }
            searchFrom = argsEnd + 1;
        }
        return results;
    }

    private static Map<String, String> parseJsonArgs(String json) {
        Map<String, String> args = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) return args;

        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
        }

        int i = 0;
        while (i < json.length()) {
            while (i < json.length() && (json.charAt(i) == ',' || json.charAt(i) == ' ')) i++;
            if (i >= json.length()) break;

            int keyStart = json.indexOf("\"", i);
            if (keyStart < 0) break;
            int keyEnd = json.indexOf("\"", keyStart + 1);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart + 1, keyEnd);

            int colonIdx = json.indexOf(":", keyEnd);
            if (colonIdx < 0) break;

            int valStart = json.indexOf("\"", colonIdx);
            if (valStart < 0) break;
            int valEnd = json.indexOf("\"", valStart + 1);
            if (valEnd < 0) break;
            String value = json.substring(valStart + 1, valEnd);

            args.put(key, value);
            i = valEnd + 1;
        }
        return args;
    }

    private static int findMatchingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                i++;
                continue;
            }
            if (c == '"') return i;
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case '/': sb.append('/'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    default: sb.append(c); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static final class ParseResult {
        private final List<ToolCall> toolCalls;
        private final String remainingText;

        ParseResult(List<ToolCall> toolCalls, String remainingText) {
            this.toolCalls = Collections.unmodifiableList(toolCalls);
            this.remainingText = remainingText;
        }

        public List<ToolCall> toolCalls() { return toolCalls; }
        public String remainingText() { return remainingText; }
    }
}
