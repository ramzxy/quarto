package Protocol;

/**
 * Protocol constants for the Quarto game client-server communication.
 * Based on TCS Module 2 Implementation Project 2025/2026 specification.
 * 
 * Message format: COMMAND~arg1~arg2~...
 * Commands are terminated by newline (\n).
 */
public class PROTOCOL {
    
    // ==================== Message Separator ====================
    /** Separator character used between command and arguments */
    public static final String SEPARATOR = "~";
    
    // ==================== Handshake Commands ====================
    
    /** 
     * Initiates/responds to handshake. 
     * Client: HELLO~<description>[~extension]*
     * Server: HELLO~<description>[~extension]*
     */
    public static final String HELLO = "HELLO";
    
    /** 
     * Client: Claims a username. Format: LOGIN~<username>
     * Server: Confirms successful login. Format: LOGIN
     */
    public static final String LOGIN = "LOGIN";
    
    /** Username is already taken. Format: ALREADYLOGGEDIN */
    public static final String ALREADYLOGGEDIN = "ALREADYLOGGEDIN";
    
    // ==================== Lobby Commands ====================
    
    /** 
     * Client: Requests list of logged-in users. Format: LIST
     * Server: Returns users. Format: LIST[~username]*
     */
    public static final String LIST = "LIST";
    
    /** 
     * Toggle queue status. Format: QUEUE 
     * With NAMEDQUEUES extension: QUEUE[~name]
     */
    public static final String QUEUE = "QUEUE";
    
    // ==================== Game Commands ====================
    
    /** 
     * Server notifies game start. Format: NEWGAME~<player1>~<player2>
     * First player listed makes the first move.
     */
    public static final String NEWGAME = "NEWGAME";
    
    /** 
     * Client: Submits a move. 
     *   First move: MOVE~<pieceId> (give piece to opponent)
     *   Subsequent: MOVE~<position>~<pieceId> (place piece, give next)
     * Server: Broadcasts move to all players in game.
     * 
     * Values: 0-15 = piece/position, 16 = claim Quarto, 17 = final piece no claim
     */
    public static final String MOVE = "MOVE";
    
    /** 
     * Game ended. Format: GAMEOVER~<reason>[~winner]
     * Reasons: VICTORY, DRAW, DISCONNECT
     */
    public static final String GAMEOVER = "GAMEOVER";
    
    // ==================== GAMEOVER Reasons ====================
    
    /** Game ended with a winner */
    public static final String VICTORY = "VICTORY";
    
    /** Game ended in a draw */
    public static final String DRAW = "DRAW";
    
    /** Game ended due to player disconnect */
    public static final String DISCONNECT = "DISCONNECT";
    
    // ==================== Error Handling ====================
    
    /** Protocol violation. Format: ERROR[~description] */
    public static final String ERROR = "ERROR";
    
    // ==================== Extensions ====================
    
    // --- CHAT Extension ---
    /** 
     * Client: Broadcast message. Format: CHAT~<message>
     * Server: Delivers message. Format: CHAT~<sender>~<message>
     */
    public static final String CHAT = "CHAT";
    
    /** Private message. Format: WHISPER~<recipient/sender>~<message> */
    public static final String WHISPER = "WHISPER";
    
    /** Private message delivery failed. Format: CANNOTWHISPER~<recipient> */
    public static final String CANNOTWHISPER = "CANNOTWHISPER";
    
    // --- RANK Extension ---
    /** 
     * Client: Request rankings. Format: RANK
     * Server: Return rankings. Format: RANK[~username~score]*
     */
    public static final String RANK = "RANK";
    
    // --- NOISE Extension ---
    /** Authentication failed with different public key. Format: WRONGKEY */
    public static final String WRONGKEY = "WRONGKEY";
    
    // ==================== Special Move Values ====================
    
    /** Claim Quarto (used as M value in MOVE command) */
    public static final int CLAIM_QUARTO = 16;
    
    /** Place final piece without claiming Quarto */
    public static final int FINAL_PIECE_NO_CLAIM = 17;
}
