package clique.demo.chat;

import java.util.Map;

public interface Tool {
    String name();
    String description();
    Map<String, ParameterSpec> parameters();

    DangerLevel dangerLevel(Map<String, String> arguments);

    String execute(Map<String, String> arguments) throws Exception;

    final class ParameterSpec {
        private final String name;
        private final String type;
        private final String description;
        private final boolean required;

        public ParameterSpec(String name, String type, String description, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }

        public String name() { return name; }
        public String type() { return type; }
        public String description() { return description; }
        public boolean required() { return required; }
    }
}
