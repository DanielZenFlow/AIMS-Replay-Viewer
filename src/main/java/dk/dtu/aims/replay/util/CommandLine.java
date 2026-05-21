package dk.dtu.aims.replay.util;

import java.util.ArrayList;
import java.util.List;

public final class CommandLine {
    private CommandLine() {
    }

    public static List<String> split(String command) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuote = false;

        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '"') {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (Character.isWhitespace(ch) && !inDoubleQuote) {
                flush(out, current);
                continue;
            }
            current.append(ch);
        }
        if (inDoubleQuote) {
            throw new IllegalArgumentException("Unclosed quote in command: " + command);
        }
        flush(out, current);
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Empty command.");
        }
        return out;
    }

    private static void flush(List<String> out, StringBuilder current) {
        if (current.length() == 0) return;
        out.add(current.toString());
        current.setLength(0);
    }
}
