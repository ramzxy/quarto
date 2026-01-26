package Client.views;

/**
 * Modern console styling utilities for a polished TUI experience.
 * Provides ANSI colors, box drawing, and formatting helpers.
 */
public class ConsoleUtils {
    // ANSI Reset
    public static final String RESET = "\u001B[0m";

    // Standard Colors
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String GRAY = "\u001B[90m";

    // Bright Colors
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_PURPLE = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";

    // Text Styles
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String ITALIC = "\u001B[3m";
    public static final String UNDERLINE = "\u001B[4m";

    // Background Colors
    public static final String BG_BLACK = "\u001B[40m";
    public static final String BG_RED = "\u001B[41m";
    public static final String BG_GREEN = "\u001B[42m";
    public static final String BG_YELLOW = "\u001B[43m";
    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_PURPLE = "\u001B[45m";
    public static final String BG_CYAN = "\u001B[46m";
    public static final String BG_WHITE = "\u001B[47m";

    // Box Drawing Characters
    public static final String BOX_TL = "\u250C"; // Top-left corner
    public static final String BOX_TR = "\u2510"; // Top-right corner
    public static final String BOX_BL = "\u2514"; // Bottom-left corner
    public static final String BOX_BR = "\u2518"; // Bottom-right corner
    public static final String BOX_H = "\u2500";  // Horizontal line
    public static final String BOX_V = "\u2502";  // Vertical line
    public static final String BOX_VR = "\u251C"; // Vertical + right
    public static final String BOX_VL = "\u2524"; // Vertical + left
    public static final String BOX_HB = "\u252C"; // Horizontal + bottom
    public static final String BOX_HT = "\u2534"; // Horizontal + top
    public static final String BOX_X = "\u253C";  // Cross

    // Double Box Drawing
    public static final String DBOX_TL = "\u2554";
    public static final String DBOX_TR = "\u2557";
    public static final String DBOX_BL = "\u255A";
    public static final String DBOX_BR = "\u255D";
    public static final String DBOX_H = "\u2550";
    public static final String DBOX_V = "\u2551";

    // Special Characters (ASCII-safe for Windows compatibility)
    public static final String BULLET = "*";
    public static final String ARROW_RIGHT = "->";
    public static final String ARROW_LEFT = "<-";
    public static final String CHECK = "[+]";
    public static final String CROSS = "[x]";
    public static final String STAR = "*";
    public static final String CIRCLE = "(*)";
    public static final String CIRCLE_EMPTY = "( )";
    public static final String SQUARE = "[#]";
    public static final String SQUARE_EMPTY = "[ ]";
    public static final String DIAMOND = "<>";

    /**
     * Clears the console screen.
     */
    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    /**
     * Moves cursor up n lines.
     */
    public static void cursorUp(int n) {
        System.out.print("\033[" + n + "A");
    }

    /**
     * Clears the current line.
     */
    public static void clearLine() {
        System.out.print("\033[2K\r");
    }

    /**
     * Creates a horizontal line of given width.
     */
    public static String horizontalLine(int width) {
        return BOX_H.repeat(width);
    }

    /**
     * Creates a double horizontal line of given width.
     */
    public static String doubleHorizontalLine(int width) {
        return DBOX_H.repeat(width);
    }

    /**
     * Pads a string to center it within given width.
     */
    public static String center(String text, int width) {
        int padding = (width - stripAnsi(text).length()) / 2;
        if (padding <= 0) return text;
        return " ".repeat(padding) + text + " ".repeat(width - padding - stripAnsi(text).length());
    }

    /**
     * Pads a string to right-align it within given width.
     */
    public static String rightAlign(String text, int width) {
        int padding = width - stripAnsi(text).length();
        if (padding <= 0) return text;
        return " ".repeat(padding) + text;
    }

    /**
     * Strips ANSI codes from a string (for length calculation).
     */
    public static String stripAnsi(String text) {
        return text.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    /**
     * Creates a styled box with title.
     */
    public static void printBox(String title, String content, String borderColor) {
        int width = 60;
        String border = borderColor;

        // Top border with title
        System.out.println(border + DBOX_TL + doubleHorizontalLine(width - 2) + DBOX_TR + RESET);
        System.out.println(border + DBOX_V + RESET + center(BOLD + title + RESET, width - 2) + border + DBOX_V + RESET);
        System.out.println(border + DBOX_V + doubleHorizontalLine(width - 2) + DBOX_V + RESET);

        // Content lines
        for (String line : content.split("\n")) {
            int visibleLen = stripAnsi(line).length();
            int padding = width - 4 - visibleLen;
            System.out.println(border + DBOX_V + RESET + "  " + line + " ".repeat(Math.max(0, padding)) + border + DBOX_V + RESET);
        }

        // Bottom border
        System.out.println(border + DBOX_BL + doubleHorizontalLine(width - 2) + DBOX_BR + RESET);
    }

    /**
     * Creates a simple status line.
     */
    public static String statusLine(String icon, String iconColor, String text) {
        return iconColor + icon + RESET + " " + text;
    }

    /**
     * Formats text as a command hint.
     */
    public static String commandHint(String command, String description) {
        return BRIGHT_CYAN + command + RESET + GRAY + " - " + description + RESET;
    }

    /**
     * Creates a progress-style indicator.
     */
    public static String progressDots(int current, int total) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            if (i < current) {
                sb.append(BRIGHT_GREEN + CIRCLE + RESET);
            } else {
                sb.append(GRAY + CIRCLE_EMPTY + RESET);
            }
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * Prints a divider line.
     */
    public static void printDivider() {
        System.out.println(GRAY + horizontalLine(60) + RESET);
    }

    /**
     * Prints a message with an icon prefix.
     */
    public static void printWithIcon(String icon, String color, String message) {
        System.out.println(color + icon + RESET + " " + message);
    }

    /**
     * Prints an error message.
     */
    public static void printError(String message) {
        printWithIcon(CROSS, BRIGHT_RED, BRIGHT_RED + message + RESET);
    }

    /**
     * Prints a success message.
     */
    public static void printSuccess(String message) {
        printWithIcon(CHECK, BRIGHT_GREEN, BRIGHT_GREEN + message + RESET);
    }

    /**
     * Prints a warning message.
     */
    public static void printWarning(String message) {
        printWithIcon("!", BRIGHT_YELLOW, BRIGHT_YELLOW + message + RESET);
    }

    /**
     * Prints an info message.
     */
    public static void printInfo(String message) {
        printWithIcon(BULLET, BRIGHT_BLUE, message);
    }

    /**
     * Prints an input prompt with a styled ">" indicator.
     */
    public static void printInputPrompt() {
        System.out.print(BRIGHT_CYAN + BOLD + "> " + RESET);
    }

    /**
     * Prints an input prompt with a custom message.
     */
    public static void printInputPrompt(String message) {
        System.out.print(BRIGHT_CYAN + BOLD + "> " + RESET + message);
    }

    /**
     * Returns the styled input prompt string (for inline use).
     */
    public static String getInputPrompt() {
        return BRIGHT_CYAN + BOLD + "> " + RESET;
    }
}
