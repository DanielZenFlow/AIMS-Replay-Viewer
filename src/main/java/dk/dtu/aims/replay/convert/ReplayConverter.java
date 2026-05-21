package dk.dtu.aims.replay.convert;

import dk.dtu.aims.replay.domain.Level;
import dk.dtu.aims.replay.domain.State;
import dk.dtu.aims.replay.io.ActionLogParser;
import dk.dtu.aims.replay.io.LevelParser;
import dk.dtu.aims.replay.model.Replay;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ReplayConverter {
    private final LevelParser levelParser = new LevelParser();
    private final ActionLogParser actionLogParser = new ActionLogParser();

    public Replay convert(Path levelPath, Path actionsPath) throws IOException {
        LevelParser.ParseResult parsed = levelParser.parse(levelPath);
        Level level = parsed.level();
        State state = parsed.initialState();

        List<ActionLogParser.JointAction> jointActions = actionLogParser.parse(actionsPath, level.numAgents());
        List<Replay.Frame> frames = new ArrayList<>();
        frames.add(new Replay.Frame(0, new String[0], new boolean[0], state));

        int t = 0;
        for (ActionLogParser.JointAction jointAction : jointActions) {
            State.StepResult result = state.applyJointAction(jointAction.actions(), level);
            state = result.state();
            t++;
            frames.add(new Replay.Frame(t, jointAction.canonicalActions(), result.accepted(), state));
        }

        Replay.Summary summary = new Replay.Summary(
                state.isGoalState(level) ? "solved" : "partial",
                jointActions.size(),
                jointActions.size(),
                frames.size(),
                countSatisfiedBoxGoals(level, state),
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
                Character actual = state.boxes().get(new dk.dtu.aims.replay.domain.Position(row, col));
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
