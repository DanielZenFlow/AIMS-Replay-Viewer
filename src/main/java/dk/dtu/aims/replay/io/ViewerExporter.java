package dk.dtu.aims.replay.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ViewerExporter {
    public static final String ENTRY_FILE = "AIMS-Replay-Viewer.html";
    private static final String ASSET_DIR = "viewer-assets";
    private static final String[] ASSET_FILES = {
            "viewer.css",
            "viewer.js"
    };

    public void export(Path viewerDir, String replayJson) throws IOException {
        String safeJson = replayJson.replace("</script", "<\\/script");
        exportWithLatestReplayScript(viewerDir, "window.DEFAULT_REPLAY = " + safeJson + ";\n");
    }

    public void exportEmpty(Path viewerDir) throws IOException {
        exportWithLatestReplayScript(viewerDir, "window.DEFAULT_REPLAY = null;\n");
    }

    private void exportWithLatestReplayScript(Path viewerDir, String latestReplayScript) throws IOException {
        Files.createDirectories(viewerDir);
        Path assetsDir = viewerDir.resolve(ASSET_DIR);
        Files.createDirectories(assetsDir);

        ClassLoader loader = ViewerExporter.class.getClassLoader();
        for (String file : ASSET_FILES) {
            String resource = "replay-viewer/" + file;
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("Missing bundled resource: " + resource);
                }
                Files.copy(input, assetsDir.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        String html = readResource(loader, "replay-viewer/index.html")
                .replace("href=\"viewer.css\"", "href=\"" + ASSET_DIR + "/viewer.css\"")
                .replace("src=\"latest-replay.js\"", "src=\"" + ASSET_DIR + "/latest-replay.js\"")
                .replace("src=\"viewer.js\"", "src=\"" + ASSET_DIR + "/viewer.js\"");
        Files.writeString(viewerDir.resolve(ENTRY_FILE), html, StandardCharsets.UTF_8);

        Files.writeString(assetsDir.resolve("latest-replay.js"), latestReplayScript, StandardCharsets.UTF_8);
    }

    public static Path entryPath(Path viewerDir) {
        return viewerDir.resolve(ENTRY_FILE);
    }

    private static String readResource(ClassLoader loader, String resource) throws IOException {
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing bundled resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
