package Server;

/**
 * Represents the connection state of a client.
 * Used to enforce valid command sequences in the protocol.
 * 
 * State flow:
 * CONNECTED → (client sends HELLO) → HELLO_RECEIVED → (LOGIN) → LOGGED_IN → (QUEUE) → IN_QUEUE → (NEWGAME) → IN_GAME
 */
public enum ClientState {
    /** Just connected, awaiting client's HELLO */
    CONNECTED,
    
    /** Client sent HELLO, server responded, awaiting LOGIN */
    HELLO_RECEIVED,
    
    /** Logged in with username, can use LIST, QUEUE, etc. */
    LOGGED_IN,
    
    /** In the matchmaking queue, waiting for opponent */
    IN_QUEUE,
    
    /** Currently in an active game */
    IN_GAME
}
