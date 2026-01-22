package Client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Helper class for console styling to match the reference server standard.
 * Supports ANSI colors and consistent timestamped logging.
 */
public class ConsoleUtils {
    // ANSI Color Codes
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    
    // Bright/Bold variations if needed
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_CYAN = "\u001B[96m";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Prints a message with a timestamp and a " > " separator, replicating the reference style.
     * @param color The ANSI color code for the message text.
     * @param message The message to print.
     */
    public static void log(String color, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        // Gray timestamp, default separator, colored message
        System.out.println("\u001B[90m" + timestamp + RESET + " > " + color + message + RESET);
    }
    
    /**
     * Standard info log (White/Default).
     */
    public static void info(String message) {
        log(RESET, message);
    }
    
    /**
     * Success log (Green).
     */
    public static void success(String message) {
        log(GREEN, message);
    }

    /**
     * Error log (Red).
     */
    public static void error(String message) {
        log(RED, message);
    }
    
    /**
     * Warning log (Yellow).
     */
    public static void warning(String message) {
        log(YELLOW, message);
    }
    
    /**
     * Highlights a specific part of a message.
     */
    public static void log(String prefix, String highlightColor, String highlight, String suffix) {
         String timestamp = LocalDateTime.now().format(TIME_FORMAT);
         System.out.println("\u001B[90m" + timestamp + RESET + " > " + prefix + highlightColor + highlight + RESET + suffix);
    }

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
