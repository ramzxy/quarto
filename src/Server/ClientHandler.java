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
 * Implements GameListener to receive game events.
 */
public class ClientHandler implements GameListener {
    private ServerConnection connection;
    private GameManager gameManager;
    private String playerName;
    private volatile Game currentGame;
    private volatile ClientState state;
    private List<String> supportedExtensions;
    private static final String SERVER_DESCRIPTION = "Quarto Server";

    /**
     * Creates a new handler for a client connection.
     * @param client the client socket
     * @param gameManager the shared game manager instance
     */
    public ClientHandler(Socket client, GameManager gameManager) throws IOException {
        this.connection = new ServerConnection(client);
        this.connection.setClientHandler(this);
        this.gameManager = gameManager;
        this.state = ClientState.CONNECTED;
        this.supportedExtensions = new ArrayList<>();
    }

    /**
     * Starts the connection's receive loop.
     */
    public void start() {
        connection.start();
    }

    // --- Protocol receive handlers (called by ServerConnection) ---

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

    public void receiveList() {
        if (state != ClientState.LOGGED_IN && state != ClientState.IN_QUEUE && state != ClientState.IN_GAME) {
            connection.sendError("Must login first");
            return;
        }
        
        List<String> users = gameManager.getLoggedInUsers();
        connection.sendList(users.toArray(new String[0]));
    }

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

    public void receiveFirstMove(int pieceId) {
        Server.log("ClientHandler", playerName + " sending FirstMove: " + pieceId);
        if (state != ClientState.IN_GAME || currentGame == null) {
            Server.log("ClientHandler", "Error: Not in game (State: " + state + ", Game: " + (currentGame==null?"null":"ok") + ")");
            connection.sendError("Not in a game");
            return;
        }
        
        // First move: player picks a piece to give to opponent (MOVE~<pieceId>)
        Piece piece = currentGame.getPieceById(pieceId);
        if (piece == null) {
            connection.sendError("Invalid piece");
            return;
        }
        
        // Set this piece as the current piece for the opponent to place
        // Use boardIndex = -1 for first move. No piece placed (null). Next piece is the one chosen.
        currentGame.doMove(new Move(-1, null, piece));
    }

    public void receiveMove(int position, int nextPieceId) {
        Server.log("ClientHandler", playerName + " sending Move: pos=" + position + ", next=" + nextPieceId);
        if (state != ClientState.IN_GAME || currentGame == null) {
            Server.log("ClientHandler", "Error: Not in game (State: " + state + ", Game: " + (currentGame==null?"null":"ok") + ")");
            connection.sendError("Not in a game");
            return;
        }
        
        // Get the current piece to place (was set by opponent's previous move)
        Piece currentPiece = currentGame.getCurrentPiece();
        if (currentPiece == null) {
            connection.sendError("No piece to place");
            return;
        }
        
        // Place the current piece at the given position and pick next piece
        
        if (nextPieceId >= 0 && nextPieceId <= 15) {
            // Normal move: pick piece for opponent
            Piece nextPiece = currentGame.getPieceById(nextPieceId);
            if (nextPiece == null) {
                connection.sendError("Invalid piece");
                return;
            }
            
            Move move = new Move(position, currentPiece, nextPiece);
            if (!currentGame.doMove(move)) {
                 connection.sendError("Invalid move");
                 return;
            }
        } else {
 
             Move move = new Move(position, currentPiece, null); // No next piece
             if (!currentGame.doMove(move)) {
                 connection.sendError("Invalid move");
                 return;
             }
             
             if (nextPieceId == PROTOCOL.CLAIM_QUARTO) {
                // Player claims Quarto
                if (currentGame.getBoard().hasWinningLine()) {
                    gameManager.endGame(currentGame, PROTOCOL.VICTORY, playerName);
                } else {
                    gameManager.endGame(currentGame, PROTOCOL.VICTORY, currentGame.getOpponentName());
                }
            } else if (nextPieceId == PROTOCOL.FINAL_PIECE_NO_CLAIM) {
                if (currentGame.isGameOver()) {
                    gameManager.endGame(currentGame, PROTOCOL.DRAW, null);
                }
            }
        }
    }

    public void receiveDisconnect() {
        gameManager.handleDisconnect(this);
    }

    // --- Called by GameManager ---

    /**
     * Called when this client starts a game.
     */
    public void startGame(Game game) {
        this.currentGame = game;
        this.state = ClientState.IN_GAME;
    }

    /**
     * Sends NEWGAME message to this client.
     */
    public void sendNewGame(String player1, String player2) {
        connection.sendNewGame(player1, player2);
    }

    // --- GameListener implementation ---

    @Override
    public void moveMade(Move move) {
        // First move has no board position (just piece selection)
        // Use boardIndex == -1 as sentinel for first move
        if (move.getBoardIndex() < 0) {
            // First move: MOVE~<pieceId>
            // The piece chosen is in getNextPiece() because construction was (-1, null, piece)
            if (move.getNextPiece() != null) {
                connection.sendFirstMove(move.getNextPiece().getId());
            }
        } else {
            // Subsequent move: MOVE~<position>~<pieceId>
            // The piece chosen for next player is getNextPiece()
            int nextPieceId = (move.getNextPiece() != null) ? move.getNextPiece().getId() : -1;
            connection.sendMove(move.getBoardIndex(), nextPieceId);
        }
    }

    @Override
    public void gameFinished(Game game) {
        String reason = game.getEndReason();
        String winner = game.getWinnerName();
        Server.log("ClientHandler", playerName + " game finished (Reason: " + reason + ")");
        
        connection.sendGameOver(reason, winner);
        
        state = ClientState.LOGGED_IN;
        currentGame = null;
    }

    // --- Getters ---

    public String getPlayerName() {
        return playerName;
    }

    public Game getCurrentGame() {
        return currentGame;
    }

    public void setCurrentGame(Game game) {
        this.currentGame = game;
    }

    public ClientState getState() {
        return state;
    }

    public ServerConnection getConnection() {
        return connection;
    }
}