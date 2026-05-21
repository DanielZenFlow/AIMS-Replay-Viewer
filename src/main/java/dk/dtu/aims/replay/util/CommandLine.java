package dk.dtu.aims.replay.util;

import java.util.List;

public final class CommandLine {
    private CommandLine() {
    }

    public static List<String> split(String command) {
        if (isWindows()) {
            return List.of("cmd", "/d", "/s", "/c", command);
        }
        return List.of("sh", "-c", command);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
