package dk.dtu.aims.replay.domain;

import java.util.Objects;

public final class Action {
    public enum Type {
        MOVE,
        PUSH,
        PULL,
        NOOP
    }

    public static final Action NOOP = new Action(Type.NOOP, null, null);

    public final Type type;
    public final Direction agentDir;
    public final Direction boxDir;

    private Action(Type type, Direction agentDir, Direction boxDir) {
        this.type = type;
        this.agentDir = agentDir;
        this.boxDir = boxDir;
    }

    public static Action move(Direction direction) {
        return new Action(Type.MOVE, Objects.requireNonNull(direction), null);
    }

    public static Action push(Direction agentDir, Direction boxDir) {
        return new Action(Type.PUSH, Objects.requireNonNull(agentDir), Objects.requireNonNull(boxDir));
    }

    public static Action pull(Direction agentDir, Direction boxDir) {
        return new Action(Type.PULL, Objects.requireNonNull(agentDir), Objects.requireNonNull(boxDir));
    }

    public static Action noOp() {
        return NOOP;
    }

    public static Action parse(String raw) {
        String text = stripCallout(raw).trim();
        if (text.equals("NoOp")) {
            return NOOP;
        }
        if (text.startsWith("Move(") && text.endsWith(")")) {
            return move(Direction.parse(text.substring(5, text.length() - 1)));
        }
        if (text.startsWith("Push(") && text.endsWith(")")) {
            String[] parts = text.substring(5, text.length() - 1).split(",");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid push action: " + raw);
            return push(Direction.parse(parts[0]), Direction.parse(parts[1]));
        }
        if (text.startsWith("Pull(") && text.endsWith(")")) {
            String[] parts = text.substring(5, text.length() - 1).split(",");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid pull action: " + raw);
            return pull(Direction.parse(parts[0]), Direction.parse(parts[1]));
        }
        throw new IllegalArgumentException("Unknown action: " + raw);
    }

    public String toProtocolString() {
        return switch (type) {
            case MOVE -> "Move(" + agentDir.name() + ")";
            case PUSH -> "Push(" + agentDir.name() + "," + boxDir.name() + ")";
            case PULL -> "Pull(" + agentDir.name() + "," + boxDir.name() + ")";
            case NOOP -> "NoOp";
        };
    }

    private static String stripCallout(String raw) {
        int at = raw.indexOf('@');
        return at >= 0 ? raw.substring(0, at) : raw;
    }

    @Override
    public String toString() {
        return toProtocolString();
    }
}
