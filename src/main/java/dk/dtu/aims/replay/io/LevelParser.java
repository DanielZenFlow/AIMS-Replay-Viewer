package dk.dtu.aims.replay.io;

import dk.dtu.aims.replay.domain.Color;
import dk.dtu.aims.replay.domain.Level;
import dk.dtu.aims.replay.domain.Position;
import dk.dtu.aims.replay.domain.State;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LevelParser {
    public ParseResult parse(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            return parse(reader);
        }
    }

    public ParseResult parse(BufferedReader reader) throws IOException {
        String levelName = "Unknown";
        Map<Character, Color> boxColors = new HashMap<>();
        Map<Integer, Color> agentColors = new HashMap<>();
        List<String> initialGrid = new ArrayList<>();
        List<String> goalGrid = new ArrayList<>();

        String section = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                section = line.substring(1).trim().toLowerCase();
                if (section.equals("end")) break;
                continue;
            }
            if (section == null) continue;
            switch (section) {
                case "domain" -> {
                }
                case "levelname" -> levelName = line.trim();
                case "colors" -> parseColorLine(line, boxColors, agentColors);
                case "initial" -> initialGrid.add(line);
                case "goal" -> goalGrid.add(line);
                default -> {
                }
            }
        }

        if (initialGrid.isEmpty()) {
            throw new IllegalArgumentException("Level has no #initial grid.");
        }
        if (goalGrid.isEmpty()) {
            throw new IllegalArgumentException("Level has no #goal grid.");
        }
        if (goalGrid.size() != initialGrid.size()) {
            throw new IllegalArgumentException("Initial and goal grids must have the same row count.");
        }

        int rows = initialGrid.size();
        int cols = 0;
        for (String row : initialGrid) cols = Math.max(cols, row.length());
        for (String row : goalGrid) cols = Math.max(cols, row.length());

        boolean[][] walls = new boolean[rows][cols];
        char[][] boxGoals = new char[rows][cols];
        int[][] agentGoals = new int[rows][cols];
        for (int[] row : agentGoals) Arrays.fill(row, -1);

        Position[] agents = new Position[10];
        Map<Position, Character> boxes = new HashMap<>();

        for (int row = 0; row < rows; row++) {
            String initial = initialGrid.get(row);
            String goal = goalGrid.get(row);
            for (int col = 0; col < cols; col++) {
                char initialChar = charAt(initial, col);
                char goalChar = charAt(goal, col);

                boolean initialWall = initialChar == '+';
                boolean goalWall = goalChar == '+';
                if (initialWall != goalWall) {
                    throw new IllegalArgumentException("Wall mismatch at " + row + "," + col + ".");
                }
                walls[row][col] = initialWall;

                if (initialChar >= '0' && initialChar <= '9') {
                    agents[initialChar - '0'] = new Position(row, col);
                } else if (initialChar >= 'A' && initialChar <= 'Z') {
                    boxes.put(new Position(row, col), initialChar);
                }

                if (goalChar >= '0' && goalChar <= '9') {
                    agentGoals[row][col] = goalChar - '0';
                } else if (goalChar >= 'A' && goalChar <= 'Z') {
                    boxGoals[row][col] = goalChar;
                }
            }
        }

        int maxAgent = -1;
        for (int i = 0; i < agents.length; i++) {
            if (agents[i] != null) maxAgent = i;
        }
        for (int i = 0; i <= maxAgent; i++) {
            if (agents[i] == null) {
                throw new IllegalArgumentException("Agent IDs must be contiguous from 0. Missing agent " + i + ".");
            }
            if (!agentColors.containsKey(i)) {
                throw new IllegalArgumentException("Agent " + i + " has no color in #colors.");
            }
        }

        Set<Character> boxTypes = new HashSet<>(boxes.values());
        for (char boxType : boxTypes) {
            if (!boxColors.containsKey(boxType)) {
                throw new IllegalArgumentException("Box type " + boxType + " has no color in #colors.");
            }
        }

        Position[] compactAgents = Arrays.copyOf(agents, Math.max(0, maxAgent + 1));
        Level level = new Level(levelName, rows, cols, compactAgents.length, walls, boxGoals, agentGoals, boxColors, agentColors);
        State initialState = new State(compactAgents, boxes);
        return new ParseResult(level, initialState);
    }

    private static char charAt(String line, int col) {
        return col < line.length() ? line.charAt(col) : ' ';
    }

    private static void parseColorLine(String line,
                                       Map<Character, Color> boxColors,
                                       Map<Integer, Color> agentColors) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return;
        int colon = trimmed.indexOf(':');
        if (colon < 0) return;

        Color color = Color.parse(trimmed.substring(0, colon));
        String[] objects = trimmed.substring(colon + 1).split(",");
        for (String object : objects) {
            String text = object.trim();
            if (text.isEmpty()) continue;
            char ch = text.charAt(0);
            if (ch >= '0' && ch <= '9') {
                agentColors.put(ch - '0', color);
            } else if (ch >= 'A' && ch <= 'Z') {
                boxColors.put(ch, color);
            }
        }
    }

    public record ParseResult(Level level, State initialState) {
    }
}
