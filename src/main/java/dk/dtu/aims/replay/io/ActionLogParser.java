package dk.dtu.aims.replay.io;

import dk.dtu.aims.replay.domain.Action;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ActionLogParser {
    public List<JointAction> parse(Path path, int numAgents) throws IOException {
        List<JointAction> actions = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                actions.add(parseJointAction(trimmed, numAgents, lineNumber));
            }
        }
        return actions;
    }

    public JointAction parseJointAction(String line, int numAgents, int lineNumber) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != numAgents) {
            throw new IllegalArgumentException("Line " + lineNumber + " has " + parts.length
                    + " actions, but the level has " + numAgents + " agents.");
        }

        Action[] actions = new Action[numAgents];
        String[] canonical = new String[numAgents];
        for (int i = 0; i < numAgents; i++) {
            actions[i] = Action.parse(parts[i]);
            canonical[i] = actions[i].toProtocolString();
        }
        return new JointAction(lineNumber, canonical, actions);
    }

    public record JointAction(int lineNumber, String[] canonicalActions, Action[] actions) {
    }
}
