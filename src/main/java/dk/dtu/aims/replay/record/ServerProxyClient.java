package dk.dtu.aims.replay.record;

import dk.dtu.aims.replay.domain.Action;
import dk.dtu.aims.replay.domain.Level;
import dk.dtu.aims.replay.domain.Position;
import dk.dtu.aims.replay.domain.State;
import dk.dtu.aims.replay.io.ActionLogParser;
import dk.dtu.aims.replay.io.LevelParser;
import dk.dtu.aims.replay.io.ReplayJsonWriter;
import dk.dtu.aims.replay.io.ReplayOutputPaths;
import dk.dtu.aims.replay.io.ViewerExporter;
import dk.dtu.aims.replay.model.Replay;
import dk.dtu.aims.replay.util.CommandLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ServerProxyClient {
    private final LevelParser levelParser = new LevelParser();
    private final ActionLogParser actionLogParser = new ActionLogParser();

    public void run(String userClientCommand, Path clientCwd, Path out, Path outDir, Path viewerDir, int maxSteps)
            throws IOException, InterruptedException {
        ProcessBuilder userClientBuilder = new ProcessBuilder(CommandLine.split(userClientCommand))
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        if (clientCwd != null) {
            userClientBuilder.directory(clientCwd.toFile());
        }
        Process userClient = userClientBuilder.start();

        try (BufferedReader serverIn = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
             BufferedWriter serverOut = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.US_ASCII));
             BufferedReader userOut = new BufferedReader(new InputStreamReader(userClient.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter userIn = new BufferedWriter(new OutputStreamWriter(userClient.getOutputStream(), StandardCharsets.US_ASCII))) {

            String userName = userOut.readLine();
            if (userName == null) {
                throw new IOException("User client exited before sending its name.");
            }

            serverOut.write("AIMS Replay Proxy (" + userName + ")");
            serverOut.newLine();
            serverOut.flush();

            List<String> levelLines = readAndForwardLevel(serverIn, userIn);
            LevelParser.ParseResult parsed = parseLevel(levelLines);
            Level level = parsed.level();
            State state = parsed.initialState();

            List<Replay.Frame> frames = new ArrayList<>();
            frames.add(new Replay.Frame(0, new String[0], new boolean[0], state));

            int protocolLine = 0;
            int step = 0;
            while (step < maxSteps) {
                String userLine = userOut.readLine();
                if (userLine == null) break;
                protocolLine++;

                String trimmed = userLine.trim();
                if (trimmed.isEmpty()) continue;

                serverOut.write(trimmed);
                serverOut.newLine();
                serverOut.flush();

                if (trimmed.startsWith("#")) {
                    continue;
                }

                String response = serverIn.readLine();
                if (response == null) break;

                userIn.write(response);
                userIn.newLine();
                userIn.flush();

                ActionLogParser.JointAction jointAction =
                        actionLogParser.parseJointAction(trimmed, level.numAgents(), protocolLine);
                boolean[] accepted = parseAccepted(response, level.numAgents());
                state = applyServerAccepted(state, level, jointAction.actions(), accepted);
                step++;
                frames.add(new Replay.Frame(step, jointAction.canonicalActions(), accepted, state));
            }

            Replay replay = buildReplay(level, frames);
            Path replayPath = out != null ? out : ReplayOutputPaths.defaultReplayPath(outDir, replay);
            ReplayJsonWriter writer = new ReplayJsonWriter();
            String json = writer.toJson(replay);
            writer.write(replayPath, replay);
            if (viewerDir != null) {
                new ViewerExporter().export(viewerDir, json);
            }

            System.err.println("[AIMS Replay Proxy] Replay JSON: " + replayPath.toAbsolutePath());
            if (viewerDir != null) {
                System.err.println("[AIMS Replay Proxy] Viewer: " + ViewerExporter.entryPath(viewerDir).toAbsolutePath());
            }
            System.err.println("[AIMS Replay Proxy] Outcome: " + replay.summary().outcome()
                    + " (" + replay.summary().executedSteps() + " steps)");
        } finally {
            if (userClient.isAlive()) {
                userClient.destroy();
                if (userClient.isAlive()) {
                    userClient.destroyForcibly();
                }
            }
        }
    }

    private static List<String> readAndForwardLevel(BufferedReader serverIn, BufferedWriter userIn) throws IOException {
        List<String> levelLines = new ArrayList<>();
        String line;
        while ((line = serverIn.readLine()) != null) {
            levelLines.add(line);
            userIn.write(line);
            userIn.newLine();
            if (line.trim().equals("#end")) {
                break;
            }
        }
        userIn.flush();
        if (levelLines.isEmpty() || !levelLines.get(levelLines.size() - 1).trim().equals("#end")) {
            throw new IOException("Server closed before sending a complete level.");
        }
        return levelLines;
    }

    private LevelParser.ParseResult parseLevel(List<String> levelLines) throws IOException {
        String levelText = String.join("\n", levelLines) + "\n";
        return levelParser.parse(new BufferedReader(new StringReader(levelText)));
    }

    private static boolean[] parseAccepted(String response, int numAgents) {
        String[] parts = response.trim().split("\\|", -1);
        if (parts.length != numAgents) {
            throw new IllegalArgumentException("Server response has " + parts.length
                    + " values, but the level has " + numAgents + " agents: " + response);
        }
        boolean[] accepted = new boolean[numAgents];
        for (int i = 0; i < numAgents; i++) {
            accepted[i] = parts[i].trim().equalsIgnoreCase("true");
        }
        return accepted;
    }

    private static State applyServerAccepted(State state, Level level, Action[] actions, boolean[] accepted) {
        Action[] effective = new Action[actions.length];
        for (int i = 0; i < actions.length; i++) {
            effective[i] = accepted[i] ? actions[i] : Action.noOp();
        }
        return state.applyJointAction(effective, level).state();
    }

    private static Replay buildReplay(Level level, List<Replay.Frame> frames) {
        State finalState = frames.get(frames.size() - 1).state();
        Replay.Summary summary = new Replay.Summary(
                finalState.isGoalState(level) ? "solved" : "partial",
                frames.size() - 1,
                frames.size() - 1,
                frames.size(),
                countSatisfiedBoxGoals(level, finalState),
                countBoxGoals(level)
        );
        return new Replay(level, Instant.now(), summary, List.copyOf(frames));
    }

    private static int countSatisfiedBoxGoals(Level level, State state) {
        int count = 0;
        for (int row = 0; row < level.rows(); row++) {
            for (int col = 0; col < level.cols(); col++) {
                char goal = level.boxGoalAt(row, col);
                if (goal == '\0') continue;
                Character actual = state.boxes().get(new Position(row, col));
                if (actual != null && actual == goal) count++;
            }
        }
        return count;
    }

    private static int countBoxGoals(Level level) {
        int count = 0;
        for (int row = 0; row < level.rows(); row++) {
            for (int col = 0; col < level.cols(); col++) {
                if (level.boxGoalAt(row, col) != '\0') count++;
            }
        }
        return count;
    }
}
