package dk.dtu.aims.replay.run;

import dk.dtu.aims.replay.Main;
import dk.dtu.aims.replay.io.ViewerExporter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RunCommand {
    private final Path aimsHome;
    private final BufferedReader console;

    public RunCommand(Path aimsHome) {
        this.aimsHome = aimsHome.toAbsolutePath().normalize();
        this.console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    public void run(String[] args) throws Exception {
        RunOptions options = RunOptions.parse(args);
        RunConfig config = RunConfig.load(options.configPath() != null
                ? options.configPath()
                : aimsHome.resolve("config.properties"));

        if (options.projectRoot() != null) {
            config.projectRoot(options.projectRoot());
        }

        ensureSimpleConfig(config);
        Path level = selectLevel(config, options);

        while (true) {
            printConfirmation(config, level);
            if (options.yes()) {
                runServer(config, level);
                return;
            }

            String choice = prompt("Run? [Y]es / [L]evel / [C]onfig / [Q]uit: ").trim().toUpperCase(Locale.ROOT);
            if (choice.isEmpty() || choice.equals("Y") || choice.equals("YES")) {
                runServer(config, level);
                return;
            }
            if (choice.equals("L") || choice.equals("LEVEL")) {
                level = chooseLevelInteractively(config);
                continue;
            }
            if (choice.equals("C") || choice.equals("CONFIG")) {
                editConfig(config);
                level = selectLevel(config, new RunOptions(null, null, null, false, false, null));
                continue;
            }
            if (choice.equals("Q") || choice.equals("QUIT")) {
                System.out.println("Cancelled.");
                return;
            }
            System.out.println("Unknown choice.");
        }
    }

    private void ensureSimpleConfig(RunConfig config) throws IOException {
        Path root = config.projectRoot();
        while (root == null || !Files.isDirectory(root)) {
            String text = prompt("Enter MAvis Hospital client project root: ").trim();
            if (text.isEmpty()) continue;
            root = Path.of(text).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                System.out.println("Directory not found: " + root);
                root = null;
            }
        }
        config.projectRoot(root);
        validateDefaultProjectShape(config);
        config.save();
    }

    private void validateDefaultProjectShape(RunConfig config) {
        Path root = config.projectRoot();
        List<String> missing = new ArrayList<>();
        if (!Files.exists(root.resolve("server.jar"))) missing.add("server.jar");
        if (!Files.isDirectory(root.resolve("target").resolve("classes"))) missing.add("target/classes");
        if (!Files.isDirectory(root.resolve("levels"))) missing.add("levels/");
        if (!Files.isDirectory(root.resolve("complevels"))) missing.add("complevels/");

        if (missing.isEmpty()) {
            System.out.println("[OK] Default project layout detected.");
            return;
        }

        System.out.println("Default project layout is incomplete:");
        for (String item : missing) {
            System.out.println("  missing: " + root.resolve(item));
        }
        System.out.println("Simple mode expects server.jar, target/classes, levels/, and complevels/.");
    }

    private Path selectLevel(RunConfig config, RunOptions options) throws IOException {
        if (options.levelPath() != null) {
            return normalizeExistingLevel(options.levelPath());
        }
        if (options.levelQuery() != null && !options.levelQuery().isBlank()) {
            return resolveLevelQuery(config, options.levelQuery());
        }
        if (options.last()) {
            Path last = config.lastLevel();
            if (last != null && Files.exists(last)) return last;
        }
        return chooseLevelInteractively(config);
    }

    private Path chooseLevelInteractively(RunConfig config) throws IOException {
        Path last = config.lastLevel();
        while (true) {
            String suffix = last != null && Files.exists(last) ? " (blank = last: " + last.getFileName() + ")" : "";
            String query = prompt("Enter level name or full .lvl path" + suffix + ": ").trim();
            if (query.isEmpty() && last != null && Files.exists(last)) return last;
            if (query.isEmpty()) continue;
            try {
                return resolveLevelQuery(config, query);
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private Path resolveLevelQuery(RunConfig config, String query) throws IOException {
        Path direct = Path.of(query);
        if (Files.exists(direct)) return normalizeExistingLevel(direct);

        List<Path> matches = findLevelMatches(config, query);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No level found for: " + query);
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }

        System.out.println("Multiple levels matched:");
        int limit = Math.min(matches.size(), 20);
        for (int i = 0; i < limit; i++) {
            System.out.println("  " + (i + 1) + ". " + relativeToProject(config, matches.get(i)));
        }
        if (matches.size() > limit) {
            System.out.println("  ... " + (matches.size() - limit) + " more");
        }

        while (true) {
            String choice = prompt("Choose level number: ").trim();
            try {
                int index = Integer.parseInt(choice);
                if (index >= 1 && index <= limit) return matches.get(index - 1);
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Enter a number from 1 to " + limit + ".");
        }
    }

    private List<Path> findLevelMatches(RunConfig config, String query) throws IOException {
        String needle = query.toLowerCase(Locale.ROOT);
        if (needle.endsWith(".lvl")) {
            needle = needle.substring(0, needle.length() - 4);
        }

        List<Path> matches = new ArrayList<>();
        for (Path dir : defaultLevelDirs(config)) {
            if (!Files.isDirectory(dir)) continue;
            try (var stream = Files.walk(dir)) {
                String finalNeedle = needle;
                stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".lvl"))
                        .filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            String stem = name.endsWith(".lvl") ? name.substring(0, name.length() - 4) : name;
                            return stem.equals(finalNeedle) || stem.contains(finalNeedle);
                        })
                        .forEach(matches::add);
            }
        }

        matches.sort(Comparator.comparing(path -> relativeToProject(config, path).toString().toLowerCase(Locale.ROOT)));
        return matches;
    }

    private Path normalizeExistingLevel(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.exists(absolute)) {
            throw new IllegalArgumentException("Level file not found: " + absolute);
        }
        if (!absolute.getFileName().toString().endsWith(".lvl")) {
            throw new IllegalArgumentException("Level file must end with .lvl: " + absolute);
        }
        return absolute;
    }

    private void printConfirmation(RunConfig config, Path level) {
        Path root = config.projectRoot();
        System.out.println();
        System.out.println("Run configuration");
        System.out.println("Project: " + root);
        System.out.println("Level:   " + relativeToProject(config, level));
        System.out.println("Client:  default Java classpath client");
        System.out.println("Server:  -g -s " + config.stepLimit() + " -t " + config.timeoutSeconds());
        System.out.println("Output:  " + aimsHome.resolve("replays"));
        System.out.println("Viewer:  " + ViewerExporter.entryPath(aimsHome));
        System.out.println();
    }

    private void editConfig(RunConfig config) throws IOException {
        while (true) {
            System.out.println();
            System.out.println("Configuration");
            System.out.println("1. Change project root");
            System.out.println("2. Change server timeout seconds (current: " + config.timeoutSeconds() + ")");
            System.out.println("3. Change server step limit (current: " + config.stepLimit() + ")");
            System.out.println("4. Back");
            String choice = prompt("Choose an option: ").trim();
            switch (choice) {
                case "1" -> {
                    String root = prompt("Enter MAvis Hospital client project root: ").trim();
                    if (!root.isEmpty()) config.projectRoot(Path.of(root));
                    validateDefaultProjectShape(config);
                    config.save();
                }
                case "2" -> {
                    int seconds = promptInt("Enter server timeout seconds", RunConfig.DEFAULT_TIMEOUT_SECONDS);
                    config.timeoutSeconds(seconds);
                    config.save();
                }
                case "3" -> {
                    int steps = promptInt("Enter server step limit", RunConfig.DEFAULT_STEP_LIMIT);
                    config.stepLimit(steps);
                    config.save();
                }
                case "4", "" -> {
                    return;
                }
                default -> System.out.println("Unknown option.");
            }
        }
    }

    private int promptInt(String label, int fallback) throws IOException {
        while (true) {
            String text = prompt(label + ": ").trim();
            if (text.isEmpty()) return fallback;
            try {
                int value = Integer.parseInt(text);
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Enter a positive integer.");
        }
    }

    private void runServer(RunConfig config, Path level) throws Exception {
        config.lastLevel(level);
        config.save();

        Files.createDirectories(aimsHome.resolve("target"));
        Files.createDirectories(aimsHome.resolve("replays"));
        Files.createDirectories(aimsHome.resolve("logs"));

        Path proxyScript = aimsHome.resolve("target").resolve("aims-proxy-client.cmd");
        Path userClientScript = aimsHome.resolve("target").resolve("aims-user-client.cmd");
        writeUserClientScript(userClientScript);
        writeProxyScript(proxyScript);

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(config.projectRoot().resolve("server.jar").toString());
        command.add("-l");
        command.add(level.toString());
        command.add("-c");
        command.add("cmd /d /s /c target\\aims-proxy-client.cmd");
        command.add("-g");
        command.add("-s");
        command.add(String.valueOf(config.stepLimit()));
        command.add("-t");
        command.add(String.valueOf(config.timeoutSeconds()));

        System.out.println("Running MAvis server through AIMS Replay Viewer...");
        ProcessBuilder serverBuilder = new ProcessBuilder(command)
                .directory(aimsHome.toFile())
                .inheritIO();
        configureServerEnvironment(serverBuilder.environment(), config, userClientScript);
        Process process = serverBuilder.start();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("server.jar exited with code " + exit);
        }
    }

    private void writeUserClientScript(Path script) throws IOException {
        String body = """
                @echo off
                cd /d "%AIMS_PROJECT_ROOT%"
                set "MAVIS_TIMEOUT_MS=%AIMS_TIMEOUT_MS%"
                java -Xmx4g -cp "%AIMS_CLIENT_CLASSES%" mapf.client.Client
                """;
        Files.writeString(script, body, StandardCharsets.US_ASCII);
    }

    private void writeProxyScript(Path proxyScript) throws IOException {
        String body = """
                @echo off
                cd /d "%AIMS_HOME%"
                set "MAVIS_TIMEOUT_MS=%AIMS_TIMEOUT_MS%"
                %AIMS_LAUNCHER% proxy-client --client-script "%AIMS_USER_CLIENT_SCRIPT%" --client-cwd "%AIMS_PROJECT_ROOT%" --out-dir "%AIMS_REPLAYS%" --viewer-dir "%AIMS_HOME%"
                """;
        Files.writeString(proxyScript, body, StandardCharsets.US_ASCII);
    }

    private void configureServerEnvironment(Map<String, String> environment,
                                            RunConfig config,
                                            Path userClientScript) throws Exception {
        int timeoutMs = config.timeoutSeconds() * 1000;
        Path root = config.projectRoot();
        environment.put("AIMS_HOME", aimsHome.toString());
        environment.put("AIMS_PROJECT_ROOT", root.toAbsolutePath().normalize().toString());
        environment.put("AIMS_CLIENT_CLASSES", root.resolve("target").resolve("classes").toAbsolutePath().normalize().toString());
        environment.put("AIMS_USER_CLIENT_SCRIPT", userClientScript.toAbsolutePath().normalize().toString());
        environment.put("AIMS_REPLAYS", aimsHome.resolve("replays").toAbsolutePath().normalize().toString());
        environment.put("AIMS_TIMEOUT_MS", String.valueOf(timeoutMs));
        environment.put("AIMS_LAUNCHER", launcherCommand(currentLauncherPath()));
    }

    private static Path currentLauncherPath() throws Exception {
        CodeSource source = Main.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new IllegalStateException("Cannot locate current launcher.");
        }
        return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
    }

    private static String launcherCommand(Path path) {
        if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
            return "java -jar \"" + path + "\"";
        }
        return "java -cp \"" + path + "\" " + Main.class.getName();
    }

    private List<Path> defaultLevelDirs(RunConfig config) {
        Path root = config.projectRoot();
        return List.of(root.resolve("levels"), root.resolve("complevels"));
    }

    private Path relativeToProject(RunConfig config, Path path) {
        Path root = config.projectRoot();
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return root.toAbsolutePath().normalize().relativize(absolute);
        } catch (IllegalArgumentException ignored) {
            return absolute;
        }
    }

    private String prompt(String label) throws IOException {
        System.out.print(label);
        return console.readLine();
    }

    private record RunOptions(String levelQuery, Path levelPath, Path projectRoot,
                              boolean yes, boolean last, Path configPath) {
        static RunOptions parse(String[] args) {
            String levelQuery = null;
            Path levelPath = null;
            Path projectRoot = null;
            boolean yes = false;
            boolean last = false;
            Map<String, String> values = new HashMap<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--yes") || arg.equals("-y")) {
                    yes = true;
                } else if (arg.equals("--last")) {
                    last = true;
                } else if (arg.startsWith("--")) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for " + arg);
                    }
                    values.put(arg.substring(2), args[++i]);
                } else if (levelQuery == null) {
                    levelQuery = arg;
                } else {
                    throw new IllegalArgumentException("Unexpected argument: " + arg);
                }
            }

            if (values.containsKey("level")) levelPath = Path.of(values.get("level"));
            if (values.containsKey("project-root")) projectRoot = Path.of(values.get("project-root"));
            Path configPath = values.containsKey("config") ? Path.of(values.get("config")) : null;
            return new RunOptions(levelQuery, levelPath, projectRoot, yes, last, configPath);
        }
    }
}
