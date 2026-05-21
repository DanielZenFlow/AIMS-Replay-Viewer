package dk.dtu.aims.replay.io;

import dk.dtu.aims.replay.domain.Color;
import dk.dtu.aims.replay.domain.Level;
import dk.dtu.aims.replay.domain.Position;
import dk.dtu.aims.replay.domain.State;
import dk.dtu.aims.replay.model.Replay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ReplayJsonWriter {
    public void write(Path path, Replay replay) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, toJson(replay), StandardCharsets.UTF_8);
    }

    public String toJson(Replay replay) {
        StringBuilder sb = new StringBuilder(Math.max(8192, replay.frames().size() * 512));
        sb.append("{\n");
        field(sb, 1, "schema", "mavis-hospital-replay-v1", true);
        field(sb, 1, "generatedAt", replay.generatedAt().toString(), true);
        sb.append(indent(1)).append("\"summary\": ");
        appendSummary(sb, replay.summary());
        sb.append(",\n");
        sb.append(indent(1)).append("\"level\": ");
        appendLevel(sb, replay.level());
        sb.append(",\n");
        sb.append(indent(1)).append("\"frames\": [\n");
        for (int i = 0; i < replay.frames().size(); i++) {
            if (i > 0) sb.append(",\n");
            appendFrame(sb, replay.level(), replay.frames().get(i), 2);
        }
        sb.append('\n').append(indent(1)).append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendSummary(StringBuilder sb, Replay.Summary summary) {
        sb.append("{\n");
        field(sb, 2, "outcome", summary.outcome(), true);
        numberField(sb, 2, "executedSteps", summary.executedSteps(), true);
        numberField(sb, 2, "plannedSteps", summary.plannedSteps(), true);
        numberField(sb, 2, "frames", summary.frames(), true);
        numberField(sb, 2, "satisfiedBoxGoals", summary.satisfiedBoxGoals(), true);
        numberField(sb, 2, "totalBoxGoals", summary.totalBoxGoals(), false);
        sb.append(indent(1)).append('}');
    }

    private static void appendLevel(StringBuilder sb, Level level) {
        sb.append("{\n");
        field(sb, 2, "name", level.name(), true);
        numberField(sb, 2, "rows", level.rows(), true);
        numberField(sb, 2, "cols", level.cols(), true);

        sb.append(indent(2)).append("\"walls\": [");
        for (int row = 0; row < level.rows(); row++) {
            if (row > 0) sb.append(", ");
            quoted(sb, level.wallRow(row));
        }
        sb.append("],\n");

        sb.append(indent(2)).append("\"boxGoals\": [");
        boolean first = true;
        for (int row = 0; row < level.rows(); row++) {
            for (int col = 0; col < level.cols(); col++) {
                char goal = level.boxGoalAt(row, col);
                if (goal == '\0') continue;
                if (!first) sb.append(", ");
                sb.append("{\"type\":");
                quoted(sb, String.valueOf(goal));
                sb.append(",\"r\":").append(row).append(",\"c\":").append(col).append('}');
                first = false;
            }
        }
        sb.append("],\n");

        sb.append(indent(2)).append("\"agentGoals\": [");
        first = true;
        for (int row = 0; row < level.rows(); row++) {
            for (int col = 0; col < level.cols(); col++) {
                int goal = level.agentGoalAt(row, col);
                if (goal < 0) continue;
                if (!first) sb.append(", ");
                sb.append("{\"agent\":").append(goal)
                        .append(",\"r\":").append(row)
                        .append(",\"c\":").append(col)
                        .append('}');
                first = false;
            }
        }
        sb.append("],\n");

        appendColorMap(sb, "agentColors", level.agentColors());
        sb.append(",\n");
        appendColorMap(sb, "boxColors", level.boxColors());
        sb.append('\n').append(indent(1)).append('}');
    }

    private static <K extends Comparable<K>> void appendColorMap(StringBuilder sb,
                                                                String name,
                                                                Map<K, Color> colors) {
        sb.append(indent(2)).append('"').append(name).append("\": {");
        List<K> keys = new ArrayList<>(colors.keySet());
        keys.sort(Comparator.naturalOrder());
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(", ");
            K key = keys.get(i);
            quoted(sb, String.valueOf(key));
            sb.append(':');
            quoted(sb, colors.get(key).name());
        }
        sb.append('}');
    }

    private static void appendFrame(StringBuilder sb, Level level, Replay.Frame frame, int depth) {
        sb.append(indent(depth)).append("{\n");
        numberField(sb, depth + 1, "t", frame.t(), true);
        sb.append(indent(depth + 1)).append("\"actions\": ");
        appendStringArray(sb, frame.actions());
        sb.append(",\n");
        sb.append(indent(depth + 1)).append("\"accepted\": ");
        appendBooleanArray(sb, frame.accepted());
        sb.append(",\n");
        appendAgents(sb, level, frame.state(), depth + 1);
        sb.append(",\n");
        appendBoxes(sb, frame.state(), depth + 1);
        sb.append('\n').append(indent(depth)).append('}');
    }

    private static void appendAgents(StringBuilder sb, Level level, State state, int depth) {
        sb.append(indent(depth)).append("\"agents\": [");
        boolean first = true;
        for (int id = 0; id < level.numAgents(); id++) {
            Position pos = state.agentPosition(id);
            if (pos == null) continue;
            if (!first) sb.append(", ");
            sb.append("{\"id\":").append(id)
                    .append(",\"r\":").append(pos.row)
                    .append(",\"c\":").append(pos.col)
                    .append('}');
            first = false;
        }
        sb.append(']');
    }

    private static void appendBoxes(StringBuilder sb, State state, int depth) {
        sb.append(indent(depth)).append("\"boxes\": [");
        List<Map.Entry<Position, Character>> boxes = new ArrayList<>(state.boxes().entrySet());
        boxes.sort(Map.Entry.<Position, Character>comparingByKey()
                .thenComparing(Map.Entry.comparingByValue()));
        for (int i = 0; i < boxes.size(); i++) {
            if (i > 0) sb.append(", ");
            Map.Entry<Position, Character> entry = boxes.get(i);
            Position pos = entry.getKey();
            sb.append("{\"type\":");
            quoted(sb, String.valueOf(entry.getValue()));
            sb.append(",\"r\":").append(pos.row)
                    .append(",\"c\":").append(pos.col)
                    .append('}');
        }
        sb.append(']');
    }

    private static void appendStringArray(StringBuilder sb, String[] values) {
        sb.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            quoted(sb, values[i]);
        }
        sb.append(']');
    }

    private static void appendBooleanArray(StringBuilder sb, boolean[] values) {
        sb.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values[i]);
        }
        sb.append(']');
    }

    private static void field(StringBuilder sb, int depth, String name, String value, boolean comma) {
        sb.append(indent(depth));
        quoted(sb, name);
        sb.append(": ");
        quoted(sb, value);
        if (comma) sb.append(',');
        sb.append('\n');
    }

    private static void numberField(StringBuilder sb, int depth, String name, int value, boolean comma) {
        sb.append(indent(depth));
        quoted(sb, name);
        sb.append(": ").append(value);
        if (comma) sb.append(',');
        sb.append('\n');
    }

    public static void quoted(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 32) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        sb.append('"');
    }

    private static String indent(int depth) {
        return "  ".repeat(depth);
    }
}
