package dk.dtu.aims.replay.domain;

public enum Color {
    BLUE,
    RED,
    CYAN,
    PURPLE,
    GREEN,
    ORANGE,
    PINK,
    GREY,
    LIGHTBLUE,
    BROWN;

    public static Color parse(String text) {
        return switch (text.trim().toLowerCase()) {
            case "blue" -> BLUE;
            case "red" -> RED;
            case "cyan" -> CYAN;
            case "purple" -> PURPLE;
            case "green" -> GREEN;
            case "orange" -> ORANGE;
            case "pink" -> PINK;
            case "grey", "gray" -> GREY;
            case "lightblue" -> LIGHTBLUE;
            case "brown" -> BROWN;
            default -> throw new IllegalArgumentException("Unknown color: " + text);
        };
    }
}
