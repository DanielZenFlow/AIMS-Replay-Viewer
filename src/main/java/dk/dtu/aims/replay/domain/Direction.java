package dk.dtu.aims.replay.domain;

public enum Direction {
    N(-1, 0),
    S(1, 0),
    E(0, 1),
    W(0, -1);

    public final int dRow;
    public final int dCol;

    Direction(int dRow, int dCol) {
        this.dRow = dRow;
        this.dCol = dCol;
    }

    public Direction opposite() {
        return switch (this) {
            case N -> S;
            case S -> N;
            case E -> W;
            case W -> E;
        };
    }

    public static Direction parse(String text) {
        return switch (text.trim().toUpperCase()) {
            case "N" -> N;
            case "S" -> S;
            case "E" -> E;
            case "W" -> W;
            default -> throw new IllegalArgumentException("Invalid direction: " + text);
        };
    }
}
