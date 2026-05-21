package dk.dtu.aims.replay;

import dk.dtu.aims.replay.convert.ReplayConverter;
import dk.dtu.aims.replay.io.ReplayJsonWriter;
import dk.dtu.aims.replay.io.ReplayOutputPaths;
import dk.dtu.aims.replay.io.ViewerExporter;
import dk.dtu.aims.replay.model.Replay;
import dk.dtu.aims.replay.record.ClientRecorder;
import dk.dtu.aims.replay.record.ServerProxyClient;
import dk.dtu.aims.replay.run.RunCommand;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        try {
            if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
                printUsage();
                return;
            }

            String command = args[0];
            if (command.equals("run")) {
                new RunCommand(Path.of(".")).run(Arrays.copyOfRange(args, 1, args.length));
                return;
            }
            if (command.equals("init-viewer")) {
                runInitViewer(parseOptions(args, 1));
                return;
            }
            if (command.equals("convert")) {
                runConvert(parseOptions(args, 1));
                return;
            }
            if (command.equals("record")) {
                runRecord(parseOptions(args, 1));
                return;
            }
            if (command.equals("proxy-client")) {
                runProxyClient(parseOptions(args, 1));
                return;
            }

            throw new IllegalArgumentException("Unknown command: " + command);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            printUsage();
            System.exit(1);
        }
    }

    private static void runConvert(Map<String, String> options) throws Exception {
        Path level = requiredPath(options, "level");
        Path actions = requiredPath(options, "actions");
        Path requestedOut = optionalPath(options, "out");
        Path outDir = Path.of(options.getOrDefault("out-dir", "replays"));
        Path viewerDir = options.containsKey("viewer-dir") ? Path.of(options.get("viewer-dir")) : null;

        Replay replay = new ReplayConverter().convert(level, actions);
        Path out = requestedOut != null ? requestedOut : ReplayOutputPaths.defaultReplayPath(outDir, replay);
        ReplayJsonWriter writer = new ReplayJsonWriter();
        String json = writer.toJson(replay);
        writer.write(out, replay);

        System.out.println("Replay JSON: " + out.toAbsolutePath());
        System.out.println("Outcome: " + replay.summary().outcome()
                + " (" + replay.summary().executedSteps() + " steps)");

        if (viewerDir != null) {
            new ViewerExporter().export(viewerDir, json);
            System.out.println("Viewer: " + ViewerExporter.entryPath(viewerDir).toAbsolutePath());
        }
    }

    private static void runInitViewer(Map<String, String> options) throws Exception {
        Path viewerDir = Path.of(options.getOrDefault("viewer-dir", "."));
        new ViewerExporter().exportEmpty(viewerDir);
        System.out.println("Viewer: " + ViewerExporter.entryPath(viewerDir).toAbsolutePath());
    }

    private static void runRecord(Map<String, String> options) throws Exception {
        Path level = requiredPath(options, "level");
        Path requestedOut = optionalPath(options, "out");
        Path outDir = Path.of(options.getOrDefault("out-dir", "replays"));
        String client = clientCommand(options);
        Path clientCwd = optionalPath(options, "client-cwd");
        int maxSteps = Integer.parseInt(options.getOrDefault("max-steps", "20000"));
        Path viewerDir = options.containsKey("viewer-dir") ? Path.of(options.get("viewer-dir")) : null;

        Replay replay = new ClientRecorder().record(level, client, clientCwd, maxSteps);
        Path out = requestedOut != null ? requestedOut : ReplayOutputPaths.defaultReplayPath(outDir, replay);
        ReplayJsonWriter writer = new ReplayJsonWriter();
        String json = writer.toJson(replay);
        writer.write(out, replay);

        System.out.println("Replay JSON: " + out.toAbsolutePath());
        System.out.println("Outcome: " + replay.summary().outcome()
                + " (" + replay.summary().executedSteps() + " steps)");

        if (viewerDir != null) {
            new ViewerExporter().export(viewerDir, json);
            System.out.println("Viewer: " + ViewerExporter.entryPath(viewerDir).toAbsolutePath());
        }
    }

    private static void runProxyClient(Map<String, String> options) throws Exception {
        String client = clientCommand(options);
        Path clientCwd = optionalPath(options, "client-cwd");
        Path out = optionalPath(options, "out");
        Path outDir = Path.of(options.getOrDefault("out-dir", "replays"));
        Path viewerDir = options.containsKey("viewer-dir") ? Path.of(options.get("viewer-dir")) : null;
        int maxSteps = Integer.parseInt(options.getOrDefault("max-steps", "20000"));
        new ServerProxyClient().run(client, clientCwd, out, outDir, viewerDir, maxSteps);
    }

    private static Map<String, String> parseOptions(String[] args, int start) {
        Map<String, String> out = new HashMap<>();
        for (int i = start; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Expected option, got: " + key);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for option: " + key);
            }
            out.put(key.substring(2), args[++i]);
        }
        return out;
    }

    private static Path requiredPath(Map<String, String> options, String name) {
        String value = required(options, name);
        return Path.of(value);
    }

    private static Path optionalPath(Map<String, String> options, String name) {
        String value = options.get(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + name);
        }
        return value;
    }

    private static String clientCommand(Map<String, String> options) {
        String client = options.get("client");
        if (client != null && !client.isBlank()) {
            return client;
        }
        String script = options.get("client-script");
        if (script != null && !script.isBlank()) {
            return "cmd /c \"" + Path.of(script).toAbsolutePath().normalize() + "\"";
        }
        throw new IllegalArgumentException("Missing required option --client or --client-script");
    }

    private static void printUsage() {
        System.out.println("""
                AIMS Replay Viewer

                Usage:
                  java -jar aims-replay-viewer.jar run [LEVEL_NAME | --level LEVEL.lvl] [--project-root USER_PROJECT] [--yes]
                  java -jar aims-replay-viewer.jar init-viewer [--viewer-dir VIEWER_OUTPUT_DIR]
                  java -jar aims-replay-viewer.jar convert --level LEVEL.lvl --actions actions.txt [--out replay.json | --out-dir replays] [--viewer-dir VIEWER_OUTPUT_DIR]
                  java -jar aims-replay-viewer.jar record --level LEVEL.lvl (--client "CLIENT COMMAND" | --client-script SCRIPT.cmd) [--client-cwd USER_PROJECT] [--out replay.json | --out-dir replays] [--viewer-dir VIEWER_OUTPUT_DIR]
                  java -jar aims-replay-viewer.jar proxy-client (--client "CLIENT COMMAND" | --client-script SCRIPT.cmd) [--client-cwd USER_PROJECT] [--out replay.json | --out-dir replays] [--viewer-dir VIEWER_OUTPUT_DIR]

                Commands:
                  run       Interactive one-click runner. Simple mode only asks for the project root.
                  init-viewer
                            Create an empty root-level viewer so open-viewer.cmd has something to open.
                  convert   Convert a Hospital level plus joint action log into replay JSON.
                  record    Run a standard Hospital client command and record its protocol into replay JSON.
                  proxy-client
                            Run as the client command inside server.jar. It launches the real user client,
                            forwards the official server protocol, and records the server's true/false replies.

                Action log format:
                  One joint action per line, for example:
                    Move(S)
                    Move(N)|NoOp
                    Push(E,E)|Pull(N,W)

                  Blank lines and lines starting with # are ignored.
                """);
    }
}
