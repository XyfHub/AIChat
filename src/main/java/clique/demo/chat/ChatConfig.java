package clique.demo.chat;

public final class ChatConfig {
    private final String apiKey;
    private final String model;
    private final String apiBaseUrl;
    private final String systemPrompt;
    private final int maxTokens;
    private final double temperature;
    private final boolean streaming;

    private ChatConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.model = builder.model;
        this.apiBaseUrl = builder.apiBaseUrl;
        this.systemPrompt = builder.systemPrompt;
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.streaming = builder.streaming;
    }

    public static ChatConfig load() {
        String apiKey = System.getenv("MIMO_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("mimo.api.key");
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: MIMO_API_KEY environment variable is not set.");
            System.err.println("Set it with: export MIMO_API_KEY=your_key_here");
            System.err.println("Get your key at: https://platform.xiaomimimo.com");
            System.exit(1);
        }

        String model = System.getenv().getOrDefault("MIMO_MODEL", "mimo-v2-flash");

        String osName = System.getProperty("os.name", "");
        boolean isWindows = osName.toLowerCase().contains("win");
        String osHint = isWindows
                ? "You are running on Windows. Always use Windows shell commands: "
                  + "dir (not ls), type (not cat), del (not rm), copy/move (not cp/mv), "
                  + "findstr (not grep), mkdir (not mkdir -p). "
                  + "Use cmd.exe /c for shell execution."
                : "You are running on " + osName + ". Use Unix shell commands.";

        return new Builder()
                .apiKey(apiKey)
                .model(model)
                .systemPrompt("You are an AI coding assistant. " + osHint)
                .build();
    }

    public String apiKey() { return apiKey; }
    public String model() { return model; }
    public String apiBaseUrl() { return apiBaseUrl; }
    public String systemPrompt() { return systemPrompt; }
    public int maxTokens() { return maxTokens; }
    public double temperature() { return temperature; }
    public boolean streaming() { return streaming; }

    public ChatConfig withModel(String newModel) {
        return new Builder()
                .apiKey(apiKey)
                .model(newModel)
                .apiBaseUrl(apiBaseUrl)
                .systemPrompt(systemPrompt)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .streaming(streaming)
                .build();
    }

    public ChatConfig withSystemPrompt(String newPrompt) {
        return new Builder()
                .apiKey(apiKey)
                .model(model)
                .apiBaseUrl(apiBaseUrl)
                .systemPrompt(newPrompt)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .streaming(streaming)
                .build();
    }

    public ChatConfig withStreaming(boolean enabled) {
        return new Builder()
                .apiKey(apiKey)
                .model(model)
                .apiBaseUrl(apiBaseUrl)
                .systemPrompt(systemPrompt)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .streaming(enabled)
                .build();
    }

    public static final class Builder {
        private String apiKey;
        private String model = "mimo-v2-flash";
        private String apiBaseUrl = "https://api.xiaomimimo.com/v1";
        private String systemPrompt = "You are an AI coding assistant.";
        private int maxTokens = 4096;
        private double temperature = 0.7;
        private boolean streaming = true;

        public Builder apiKey(String v) { apiKey = v; return this; }
        public Builder model(String v) { model = v; return this; }
        public Builder apiBaseUrl(String v) { apiBaseUrl = v; return this; }
        public Builder systemPrompt(String v) { systemPrompt = v; return this; }
        public Builder maxTokens(int v) { maxTokens = v; return this; }
        public Builder temperature(double v) { temperature = v; return this; }
        public Builder streaming(boolean v) { streaming = v; return this; }

        public ChatConfig build() {
            return new ChatConfig(this);
        }
    }
}
