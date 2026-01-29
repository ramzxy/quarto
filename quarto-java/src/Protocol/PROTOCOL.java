package Protocol;

/**
 * Protocol constants for the Quarto game client-server communication.
 * Based on TCS Module 2 Implementation Project 2025/2026 specification.
 * 
 * Message format: COMMAND~arg1~arg2~...
 * Commands are terminated by newline (\n).
 */
public class PROTOCOL {
    
    // --- Message Separator ---
    /**
     * The character used to separate parts of a message.
     * Example: COMMAND~arg1~arg2
     */
    public static final String SEPARATOR = "~";
    
    // --- Handshake Commands ---
    
    /** 
     * Initial greeting.
     * Sent by both client and server to start communication.
     */
    public static final String HELLO = "HELLO";
    
    /** 
     * Login command.
     * Client sends: LOGIN~username
     * Server replies: LOGIN (if success)
     */
    public static final String LOGIN = "LOGIN";
    
    /**
     * Error sent if the username is already taken.
     */
    public static final String ALREADYLOGGEDIN = "ALREADYLOGGEDIN";
    
    // --- Lobby Commands ---
    
    /** 
     * Request a list of online users.
     * Client: LIST
     * Server: LIST~user1~user2...
     */
    public static final String LIST = "LIST";
    
    /** 
     * Join or leave the game queue.
     */
    public static final String QUEUE = "QUEUE";
    
    // --- Game Commands ---
    
    /** 
     * Server tells players a game is starting.
     * Format: NEWGAME~player1~player2
     * Player 1 goes first.
     */
    public static final String NEWGAME = "NEWGAME";
    
    /** 
     * A move in the game.
     * First turn: MOVE~pieceId (Picking the first piece)
     * Other turns: MOVE~position~pieceId (Placing a piece + Picking next)
     */
    public static final String MOVE = "MOVE";
    
    /** 
     * Tells players the game happened.
     * Format: GAMEOVER~reason~winner
     */
    public static final String GAMEOVER = "GAMEOVER";
    
    // --- GAMEOVER Reasons ---
    
    /** Game ended because someone won. */
    public static final String VICTORY = "VICTORY";
    
    /** Game ended in a draw. */
    public static final String DRAW = "DRAW";
    
    /** Game ended because someone disconnected. */
    public static final String DISCONNECT = "DISCONNECT";
    
    // --- Error Handling ---
    
    /**
     * Sent when something goes wrong.
     * Format: ERROR~message
     */
    public static final String ERROR = "ERROR";
    
    // --- Extensions ---
    
    // Chat Extension
    public static final String CHAT = "CHAT";
    public static final String WHISPER = "WHISPER";
    public static final String CANNOTWHISPER = "CANNOTWHISPER";
    
    // Rank Extension
    public static final String RANK = "RANK";
    
    // Noise Extension
    public static final String WRONGKEY = "WRONGKEY";
    
    // --- Special Move Values ---
    
    /**
     * Special value sent instead of a piece ID to claim a win.
     */
    public static final int CLAIM_QUARTO = 16;
    
    /**
     * Special value sent when placing the very last piece (no next piece to pick).
     */
    public static final int FINAL_PIECE_NO_CLAIM = 17;
}
