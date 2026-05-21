package dk.dtu.aims.replay.run;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class RunConfig {
    static final int DEFAULT_TIMEOUT_SECONDS = 180;
    static final int DEFAULT_STEP_LIMIT = 500;

    private final Path configPath;
    private final Properties properties;

    private RunConfig(Path configPath, Properties properties) {
        this.configPath = configPath;
        this.properties = properties;
    }

    static RunConfig load(Path configPath) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            }
        }
        return new RunConfig(configPath, properties);
    }

    void save() throws IOException {
        Path parent = configPath.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream output = Files.newOutputStream(configPath)) {
            properties.store(output, "AIMS Replay Viewer configuration");
        }
    }

    Path projectRoot() {
        String value = properties.getProperty("project.root", "").trim();
        return value.isEmpty() ? null : Path.of(value);
    }

    void projectRoot(Path root) {
        properties.setProperty("project.root", root.toAbsolutePath().normalize().toString());
    }

    int timeoutSeconds() {
        return intProperty("server.timeout.seconds", DEFAULT_TIMEOUT_SECONDS);
    }

    void timeoutSeconds(int value) {
        properties.setProperty("server.timeout.seconds", String.valueOf(value));
    }

    int stepLimit() {
        return intProperty("server.step.limit", DEFAULT_STEP_LIMIT);
    }

    void stepLimit(int value) {
        properties.setProperty("server.step.limit", String.valueOf(value));
    }

    Path lastLevel() {
        String value = properties.getProperty("last.level", "").trim();
        return value.isEmpty() ? null : Path.of(value);
    }

    void lastLevel(Path level) {
        properties.setProperty("last.level", level.toAbsolutePath().normalize().toString());
    }

    private int intProperty(String key, int fallback) {
        String value = properties.getProperty(key, "").trim();
        if (value.isEmpty()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
