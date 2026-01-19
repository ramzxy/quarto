package Protocol;

/**
 * Protocol constants for client-server communication.
 * All messages use the SEPARATOR (~) character to delimit command and arguments.
 * Format: COMMAND~arg1~arg2~...
 */
public class PROTOCOL {
    
    // ==================== Message Separator ====================
    /** Separator character used between command and arguments */
    public static final String SEPARATOR = "~";
    
    // ==================== Client → Server Commands ====================
    
    /** Register with a unique username. Format: LOGIN~<username> */
    public static final String LOGIN = "LOGIN";
    
    /** Join the matchmaking queue. Format: QUEUE */
    public static final String QUEUE = "QUEUE";
    
    /** Submit a move during an active game. Format: MOVE~<boardIndex>~<pieceId> */
    public static final String MOVE = "MOVE";
    
    /** Join a named queue (extension). Format: JOIN~<queueName> */
    public static final String JOIN = "JOIN";
    
    /** Leave the current queue. Format: LEAVE */
    public static final String LEAVE = "LEAVE";
    
    /** Send a chat message (extension). Format: CHAT~<message> */
    public static final String CHAT = "CHAT";
    
    /** Request a rematch after game ends. Format: REMATCH_REQUEST */
    public static final String REMATCH_REQUEST = "REMATCH_REQUEST";
    
    /** Accept a pending rematch request. Format: REMATCH_ACCEPT */
    public static final String REMATCH_ACCEPT = "REMATCH_ACCEPT";
    
    /** Decline a pending rematch request. Format: REMATCH_DENY */
    public static final String REMATCH_DENY = "REMATCH_DENY";
    
    // ==================== Server → Client Commands ====================
    
    /** Confirms successful login. Format: WELCOME~<username> */
    public static final String WELCOME = "WELCOME";
    
    /** Confirms player added to matchmaking queue. Format: QUEUED */
    public static final String QUEUED = "QUEUED";
    
    /** Notifies that a game has begun. Format: GAMESTART~<opponentName> */
    public static final String GAMESTART = "GAMESTART";
    
    /** Indicates it's the player's turn. Format: YOURTURN */
    public static final String YOURTURN = "YOURTURN";
    
    /** Notifies of opponent's move. Format: OPPONENTMOVE~<boardIndex>~<pieceId> */
    public static final String OPPONENTMOVE = "OPPONENTMOVE";
    
    /** Notifies the player they won. Format: WIN */
    public static final String WIN = "WIN";
    
    /** Notifies the player they lost. Format: LOSE */
    public static final String LOSE = "LOSE";
    
    /** Notifies the game ended in a draw. Format: TIE */
    public static final String TIE = "TIE";
    
    /** Notifies of an error. Format: ERROR~<message> */
    public static final String ERROR = "ERROR";
}
