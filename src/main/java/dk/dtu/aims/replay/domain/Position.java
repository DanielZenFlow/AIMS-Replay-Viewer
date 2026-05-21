package dk.dtu.aims.replay.domain;

import java.util.Objects;

public final class Position implements Comparable<Position> {
    public final int row;
    public final int col;

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public Position move(Direction direction) {
        return new Position(row + direction.dRow, col + direction.dCol);
    }

    @Override
    public int compareTo(Position other) {
        int byRow = Integer.compare(row, other.row);
        if (byRow != 0) return byRow;
        return Integer.compare(col, other.col);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Position other)) return false;
        return row == other.row && col == other.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @Override
    public String toString() {
        return row + "," + col;
    }
}
