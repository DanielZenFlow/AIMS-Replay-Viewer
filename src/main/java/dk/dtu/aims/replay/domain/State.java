package dk.dtu.aims.replay.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class State {
    private final Position[] agents;
    private final Map<Position, Character> boxes;

    public State(Position[] agents, Map<Position, Character> boxes) {
        this.agents = Arrays.copyOf(agents, agents.length);
        this.boxes = new HashMap<>(boxes);
    }

    private State(Position[] agents, Map<Position, Character> boxes, boolean trusted) {
        this.agents = agents;
        this.boxes = boxes;
    }

    public int numAgents() {
        return agents.length;
    }

    public Position agentPosition(int agentId) {
        return agentId >= 0 && agentId < agents.length ? agents[agentId] : null;
    }

    public Map<Position, Character> boxes() {
        return Map.copyOf(boxes);
    }

    public boolean isGoalState(Level level) {
        for (int row = 0; row < level.rows(); row++) {
            for (int col = 0; col < level.cols(); col++) {
                char boxGoal = level.boxGoalAt(row, col);
                if (boxGoal != '\0') {
                    Character actual = boxes.get(new Position(row, col));
                    if (actual == null || actual != boxGoal) return false;
                }
                int agentGoal = level.agentGoalAt(row, col);
                if (agentGoal >= 0) {
                    Position pos = agentPosition(agentGoal);
                    if (pos == null || pos.row != row || pos.col != col) return false;
                }
            }
        }
        return true;
    }

    public StepResult applyJointAction(Action[] jointAction, Level level) {
        Position[] nextAgents = Arrays.copyOf(agents, agents.length);
        Map<Position, Character> nextBoxes = new HashMap<>(boxes);

        boolean[] applicable = new boolean[jointAction.length];
        boolean[] conflicted = new boolean[jointAction.length];
        Position[] agentTo = new Position[jointAction.length];
        Position[] boxFrom = new Position[jointAction.length];
        Position[] boxTo = new Position[jointAction.length];
        Character[] boxType = new Character[jointAction.length];

        for (int agentId = 0; agentId < jointAction.length; agentId++) {
            Action action = jointAction[agentId] == null ? Action.noOp() : jointAction[agentId];
            if (action.type == Action.Type.NOOP) {
                applicable[agentId] = true;
                continue;
            }

            Position agentPos = agentPosition(agentId);
            if (agentPos == null || !isIndividuallyApplicable(action, agentId, level)) {
                continue;
            }

            applicable[agentId] = true;
            switch (action.type) {
                case MOVE -> agentTo[agentId] = agentPos.move(action.agentDir);
                case PUSH -> {
                    Position source = agentPos.move(action.agentDir);
                    agentTo[agentId] = source;
                    boxFrom[agentId] = source;
                    boxTo[agentId] = source.move(action.boxDir);
                    boxType[agentId] = boxes.get(source);
                }
                case PULL -> {
                    agentTo[agentId] = agentPos.move(action.agentDir);
                    boxFrom[agentId] = agentPos.move(action.boxDir.opposite());
                    boxTo[agentId] = agentPos;
                    boxType[agentId] = boxes.get(boxFrom[agentId]);
                }
                case NOOP -> {
                }
            }
        }

        for (int i = 0; i < jointAction.length; i++) {
            if (!applicable[i] || isNoOp(jointAction[i])) continue;
            for (int j = i + 1; j < jointAction.length; j++) {
                if (!applicable[j] || isNoOp(jointAction[j])) continue;
                if (sameNonNull(boxFrom[i], boxFrom[j]) ||
                        movingObjectsShareDestination(agentTo[i], boxTo[i], agentTo[j], boxTo[j])) {
                    conflicted[i] = true;
                    conflicted[j] = true;
                }
            }
        }

        boolean[] accepted = new boolean[jointAction.length];
        for (int agentId = 0; agentId < jointAction.length; agentId++) {
            Action action = jointAction[agentId] == null ? Action.noOp() : jointAction[agentId];
            accepted[agentId] = applicable[agentId] && !conflicted[agentId];
            if (!accepted[agentId] || action.type == Action.Type.NOOP) {
                continue;
            }
            switch (action.type) {
                case MOVE -> nextAgents[agentId] = agentTo[agentId];
                case PUSH, PULL -> {
                    nextAgents[agentId] = agentTo[agentId];
                    nextBoxes.remove(boxFrom[agentId]);
                    nextBoxes.put(boxTo[agentId], boxType[agentId]);
                }
                case NOOP -> {
                }
            }
        }

        return new StepResult(new State(nextAgents, nextBoxes, true), accepted);
    }

    private boolean isIndividuallyApplicable(Action action, int agentId, Level level) {
        Position agentPos = agentPosition(agentId);
        if (agentPos == null) return false;
        return switch (action.type) {
            case NOOP -> true;
            case MOVE -> isFree(agentPos.move(action.agentDir), level);
            case PUSH -> {
                Position source = agentPos.move(action.agentDir);
                Position destination = source.move(action.boxDir);
                Character box = boxes.get(source);
                yield box != null && level.canAgentMoveBox(agentId, box) && isFree(destination, level);
            }
            case PULL -> {
                Position newAgentPos = agentPos.move(action.agentDir);
                Position source = agentPos.move(action.boxDir.opposite());
                Character box = boxes.get(source);
                yield isFree(newAgentPos, level) && box != null && level.canAgentMoveBox(agentId, box);
            }
        };
    }

    private boolean isFree(Position position, Level level) {
        return level.isFreeFloor(position) && !hasAgentAt(position) && !boxes.containsKey(position);
    }

    private boolean hasAgentAt(Position position) {
        for (Position agent : agents) {
            if (position.equals(agent)) return true;
        }
        return false;
    }

    private static boolean isNoOp(Action action) {
        return action == null || action.type == Action.Type.NOOP;
    }

    private static boolean sameNonNull(Position a, Position b) {
        return a != null && a.equals(b);
    }

    private static boolean movingObjectsShareDestination(Position agentToA, Position boxToA,
                                                        Position agentToB, Position boxToB) {
        return sameNonNull(agentToA, agentToB)
                || sameNonNull(agentToA, boxToB)
                || sameNonNull(boxToA, agentToB)
                || sameNonNull(boxToA, boxToB);
    }

    public record StepResult(State state, boolean[] accepted) {
    }
}
