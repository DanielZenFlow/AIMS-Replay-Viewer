package dk.dtu.aims.replay.io;

import dk.dtu.aims.replay.model.Replay;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ReplayOutputPaths {
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

    private ReplayOutputPaths() {
    }

    public static Path defaultReplayPath(Path replayRoot, Replay replay) {
        String levelName = safeName(replay.level().name());
        return replayRoot.resolve(levelName).resolve(defaultReplayFileName(replay));
    }

    public static String defaultReplayFileName(Replay replay) {
        String levelName = safeName(replay.level().name());
        String stamp = FILE_STAMP.format(replay.generatedAt());
        String outcome = safeName(replay.summary().outcome());
        int steps = replay.summary().executedSteps();
        return levelName + "__" + stamp + "__" + outcome + "__" + steps + "-steps.json";
    }

    public static String safeName(String name) {
        if (name == null || name.isBlank()) return "level";
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
