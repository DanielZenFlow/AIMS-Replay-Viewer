package dk.dtu.aims.replay.model;

import dk.dtu.aims.replay.domain.Level;
import dk.dtu.aims.replay.domain.State;

import java.time.Instant;
import java.util.List;

public record Replay(Level level,
                     Instant generatedAt,
                     Summary summary,
                     List<Frame> frames) {

    public record Summary(String outcome,
                          int executedSteps,
                          int plannedSteps,
                          int frames,
                          int satisfiedBoxGoals,
                          int totalBoxGoals) {
    }

    public record Frame(int t,
                        String[] actions,
                        boolean[] accepted,
                        State state) {
    }
}
