package dk.dtu.aims.replay.record;

import dk.dtu.aims.replay.domain.Level;
import dk.dtu.aims.replay.domain.Position;
import dk.dtu.aims.replay.domain.State;
import dk.dtu.aims.replay.io.ActionLogParser;
import dk.dtu.aims.replay.io.LevelParser;
import dk.dtu.aims.replay.model.Replay;
import dk.dtu.aims.replay.util.CommandLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ClientRecorder {
    private final LevelParser levelParser = new LevelParser();
    private final ActionLogParser actionLogParser = new ActionLogParser();

    public Replay record(Path levelPath, String clientCommand, Path clientCwd, int maxSteps)
            throws IOException, InterruptedException {
        LevelParser.ParseResult parsed = levelParser.parse(levelPath);
        Level level = parsed.level();
        State state = parsed.initialState();

        ProcessBuilder processBuilder = new ProcessBuilder(CommandLine.split(clientCommand))
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        if (clientCwd != null) {
            processBuilder.directory(clientCwd.toFile());
        }
        Process process = processBuilder.start();

        List<Replay.Frame> frames = new ArrayList<>();
        frames.add(new Replay.Frame(0, new String[0], new boolean[0], state));

        try (BufferedReader clientOut = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter clientIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII))) {

            String clientName = clientOut.readLine();
            if (clientName == null) {
                throw new IOException("Client exited before sending its name.");
            }

            sendLevel(levelPath, clientIn);

            int protocolLine = 0;
            int step = 0;
            while (step < maxSteps) {
                String line = clientOut.readLine();
                if (line == null) break;
                protocolLine++;

                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("#")) continue;

                ActionLogParser.JointAction jointAction =
                        actionLogParser.parseJointAction(trimmed, level.numAgents(), protocolLine);
                State.StepResult result = state.applyJointAction(jointAction.actions(), level);
                state = result.state();
                step++;
                frames.add(new Replay.Frame(step, jointAction.canonicalActions(), result.accepted(), state));

                clientIn.write(successLine(result.accepted()));
                clientIn.newLine();
                clientIn.flush();

                if (state.isGoalState(level)) {
                    break;
                }
            }
        } finally {
            if (process.isAlive()) {
                process.destroy();
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }

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

    private static void sendLevel(Path levelPath, BufferedWriter clientIn) throws IOException {
        for (String line : Files.readAllLines(levelPath, StandardCharsets.US_ASCII)) {
            clientIn.write(line);
            clientIn.newLine();
        }
        clientIn.flush();
    }

    private static String successLine(boolean[] accepted) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < accepted.length; i++) {
            if (i > 0) out.append('|');
            out.append(accepted[i]);
        }
        return out.toString();
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
