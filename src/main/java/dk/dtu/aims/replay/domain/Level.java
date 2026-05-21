package dk.dtu.aims.replay.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Level {
    private final String name;
    private final int rows;
    private final int cols;
    private final boolean[][] walls;
    private final char[][] boxGoals;
    private final int[][] agentGoals;
    private final Map<Character, Color> boxColors;
    private final Map<Integer, Color> agentColors;

    public Level(String name,
                 int rows,
                 int cols,
                 boolean[][] walls,
                 char[][] boxGoals,
                 int[][] agentGoals,
                 Map<Character, Color> boxColors,
                 Map<Integer, Color> agentColors) {
        this.name = name;
        this.rows = rows;
        this.cols = cols;
        this.walls = copy(walls, rows, cols);
        this.boxGoals = copy(boxGoals, rows, cols);
        this.agentGoals = copy(agentGoals, rows, cols);
        this.boxColors = new HashMap<>(boxColors);
        this.agentColors = new HashMap<>(agentColors);
    }

    public String name() {
        return name;
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public int numAgents() {
        return agentColors.size();
    }

    public boolean isWall(int row, int col) {
        return row < 0 || row >= rows || col < 0 || col >= cols || walls[row][col];
    }

    public boolean isFreeFloor(Position position) {
        return !isWall(position.row, position.col);
    }

    public char boxGoalAt(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return '\0';
        return boxGoals[row][col];
    }

    public int agentGoalAt(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return -1;
        return agentGoals[row][col];
    }

    public Map<Character, Color> boxColors() {
        return Collections.unmodifiableMap(boxColors);
    }

    public Map<Integer, Color> agentColors() {
        return Collections.unmodifiableMap(agentColors);
    }

    public boolean canAgentMoveBox(int agentId, char boxType) {
        Color agentColor = agentColors.get(agentId);
        Color boxColor = boxColors.get(boxType);
        return agentColor != null && agentColor == boxColor;
    }

    public String wallRow(int row) {
        StringBuilder out = new StringBuilder(cols);
        for (int col = 0; col < cols; col++) {
            out.append(walls[row][col] ? '+' : ' ');
        }
        return out.toString();
    }

    private static boolean[][] copy(boolean[][] source, int rows, int cols) {
        boolean[][] out = new boolean[rows][cols];
        for (int row = 0; row < rows; row++) {
            System.arraycopy(source[row], 0, out[row], 0, cols);
        }
        return out;
    }

    private static char[][] copy(char[][] source, int rows, int cols) {
        char[][] out = new char[rows][cols];
        for (int row = 0; row < rows; row++) {
            System.arraycopy(source[row], 0, out[row], 0, cols);
        }
        return out;
    }

    private static int[][] copy(int[][] source, int rows, int cols) {
        int[][] out = new int[rows][cols];
        for (int row = 0; row < rows; row++) {
            System.arraycopy(source[row], 0, out[row], 0, cols);
        }
        return out;
    }
}
