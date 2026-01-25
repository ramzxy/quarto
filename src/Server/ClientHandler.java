package Server;

import Game.*;
import Protocol.PROTOCOL;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles business logic for a single connected client.
 * Receives parsed protocol events from ServerConnection.
 * Controls all protocol communication (broadcasting moves and game over).
 */
public class ClientHandler {
    private ServerConnection connection;
    private GameManager gameManager;
    private String playerName;
    private volatile GameSession gameSession;
    private volatile ClientState state;
    private List<String> supportedExtensions;
    private static final String SERVER_DESCRIPTION = "Quarto Server";

    /**
     * Creates a new handler for a single client connection.
     *
     * @param client The socket connection to the client
     * @param gameManager Reference to the main game manager
     */
    public ClientHandler(Socket client, GameManager gameManager) throws IOException {
        this.connection = new ServerConnection(client);
        this.connection.setClientHandler(this);
        this.gameManager = gameManager;
        this.state = ClientState.CONNECTED;
        this.supportedExtensions = new ArrayList<>();
    }

    /**
     * Starts listening for messages from this client.
     */
    public void start() {
        connection.start();
    }

    // --- Protocol receive handlers (called by ServerConnection) ---

    /**
     * Handshake step. Client says hello.
     * @param clientDescription Description of the client
     * @param extensions Client's supported extensions
     */
    public void receiveHello(String clientDescription, String[] extensions) {
        if (state != ClientState.CONNECTED) {
            connection.sendError("Already completed handshake");
            return;
        }
        
        for (String ext : extensions) {
            supportedExtensions.add(ext);
        }
        
        connection.sendHello(SERVER_DESCRIPTION);
        state = ClientState.HELLO_RECEIVED;
    }

    /**
     * Login step. Client tries to claim a username.
     * @param name The desired username
     */
    public void receiveLogin(String name) {
        if (state != ClientState.HELLO_RECEIVED) {
            connection.sendError("Must send HELLO first");
            return;
        }
        
        if (gameManager.registerUsername(name, this)) {
            playerName = name;
            state = ClientState.LOGGED_IN;
            connection.sendLogin();
        } else {
            Server.log("ClientHandler", playerName + " tried to login but failed (taken?)");
            connection.sendAlreadyLoggedIn();
        }
    }

    /**
     * Request list of players.
     */
    public void receiveList() {
        if (state != ClientState.LOGGED_IN && state != ClientState.IN_QUEUE && state != ClientState.IN_GAME) {
            connection.sendError("Must login first");
            return;
        }
        
        List<String> users = gameManager.getLoggedInUsers();
        connection.sendList(users.toArray(new String[0]));
    }

    /**
     * Join or leave the game queue.
     */
    public void receiveQueue() {
        if (state == ClientState.IN_GAME) {
            // Ignore queue while in game
            return;
        }
        if (state != ClientState.LOGGED_IN && state != ClientState.IN_QUEUE) {
            connection.sendError("Must login first");
            return;
        }
        
        if (state == ClientState.IN_QUEUE) {
            Server.log("ClientHandler", playerName + " left queue");
            state = ClientState.LOGGED_IN;
            gameManager.removeFromQueue(this);
        } else {
            Server.log("ClientHandler", playerName + " joined queue");
            state = ClientState.IN_QUEUE;
            gameManager.queueForGame(this);
        }
    }

    /**
     * Handles the very first move of the game (picking a piece).
     * @param pieceId The piece to give to the opponent
     */
    public void receiveFirstMove(int pieceId) {
        Server.log("ClientHandler", playerName + " sending FirstMove: " + pieceId);
        if (state != ClientState.IN_GAME || gameSession == null) {
            // Game ended - silently ignore late moves (race condition)
            Server.log("ClientHandler", playerName + " sent FirstMove but game already ended (ignoring)");
            return;
        }
        
        Game game = gameSession.getGame();

        if (!game.getCurrentPlayerName().equals(playerName)) {
             connection.sendError("Not your turn");
             return;
        }
        
        // First move: player picks a piece to give to opponent (MOVE~<pieceId>)
        Piece piece = game.getPieceById(pieceId);
        if (piece == null) {
            connection.sendError("Invalid piece");
            return;
        }
        
        // Set this piece as the current piece for the opponent to place
        // Use boardIndex = -1 for first move. No piece placed (null). Next piece is the one chosen.
        if (!game.doMove(new Move(-1, null, piece))) {
            connection.sendError("Invalid move");
            return;
        }
        
        // Broadcast the move to both players via session
        gameSession.broadcastMove(-1, pieceId);
    }

    /**
     * Handles a normal game move (place piece + pick next piece).
     * @param position Board position to place current piece
     * @param nextPieceId The piece to give to opponent
     */
    //TODO: CLEAN UP THE METHOD
    public void receiveMove(int position, int nextPieceId) {
        Server.log("ClientHandler", playerName + " sending Move: pos=" + position + ", next=" + nextPieceId);
        if (state != ClientState.IN_GAME || gameSession == null) {
            // Game ended - silently ignore late moves (race condition)
            Server.log("ClientHandler", playerName + " sent Move but game already ended (ignoring)");
            return;
        }
        
        Game game = gameSession.getGame();

        if (!game.getCurrentPlayerName().equals(playerName)) {
             connection.sendError("Not your turn");
             return;
        }
        
        // Get the current piece to place (was set by opponent's previous move)
        Piece currentPiece = game.getCurrentPiece();
        if (currentPiece == null) {
            connection.sendError("No piece to place");
            return;
        }
        
        // Place the current piece at the given position and pick next piece
        
        if (nextPieceId >= 0 && nextPieceId <= 15) {
            // Normal move: pick piece for opponent
            Piece nextPiece = game.getPieceById(nextPieceId);
            if (nextPiece == null) {
                connection.sendError("Invalid piece");
                return;
            }
            
            Move move = new Move(position, currentPiece, nextPiece);
            if (!game.doMove(move)) {
                 connection.sendError("Invalid move");
                 return;
            }
            
            // Broadcast the move via session
            gameSession.broadcastMove(position, nextPieceId);
            
            // Check for natural game ending (winning line formed without explicit claim)
            if (game.isGameOver()) {
                gameManager.endGame(gameSession, game.getEndReason(), game.getWinnerName());
            }
        } else {
 
             Move move = new Move(position, currentPiece, null); // No next piece
             if (!game.doMove(move)) {
                 connection.sendError("Invalid move");
                 return;
             }
             
             // Broadcast the move via session (with original nextPieceId: 16 or 17)
             gameSession.broadcastMove(position, nextPieceId);
             
             if (nextPieceId == PROTOCOL.CLAIM_QUARTO) {
                // Player claims Quarto
                String winner;
                if (game.getBoard().hasWinningLine()) {
                    winner = playerName;
                } else {
                    // Since doMove() already swapped turns, the "current player" is now the opponent
                    winner = game.getCurrentPlayerName();
                }
                game.setResult(PROTOCOL.VICTORY, winner);
                gameManager.endGame(gameSession, PROTOCOL.VICTORY, winner);
            } else if (nextPieceId == PROTOCOL.FINAL_PIECE_NO_CLAIM) {
                if (game.isGameOver()) {
                    game.setResult(PROTOCOL.DRAW, null);
                    gameManager.endGame(gameSession, PROTOCOL.DRAW, null);
                }
            }
        }
    }

    /**
     * Handle client disconnect.
     */
    public void receiveDisconnect() {
        gameManager.handleDisconnect(this);
    }

    /**
     * Reset state after game.
     */
    void clearGameState() {
        state = ClientState.LOGGED_IN;
        gameSession = null;
    }

    // --- Called by GameManager ---

    /**
     * Helper to start a game session.
     * @param session The game session
     */
    public void startGame(GameSession session) {
        this.gameSession = session;
        this.state = ClientState.IN_GAME;
    }

    /**
     * Sends the NEWGAME message.
     * @param player1 Name of player 1
     * @param player2 Name of player 2
     */
    public void sendNewGame(String player1, String player2) {
        connection.sendNewGame(player1, player2);
    }

    // --- Getters ---

    public String getPlayerName() {
        return playerName;
    }

    public GameSession getGameSession() {
        return gameSession;
    }

    public ClientState getState() {
        return state;
    }

    public ServerConnection getConnection() {
        return connection;
    }
}