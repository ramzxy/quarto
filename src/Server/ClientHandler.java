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
    private Game currentGame;
    private ClientState state;
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
            gameManager.removeFromQueue(this);
            state = ClientState.LOGGED_IN;
        } else {
            gameManager.queueForGame(this);
            state = ClientState.IN_QUEUE;
        }
    }

    public void receiveFirstMove(int pieceId) {
        if (state != ClientState.IN_GAME || currentGame == null) {
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
        currentGame.pickCurrentPiece(piece);
        
        // Use boardIndex = -1 for first move
        currentGame.notifyMove(new Move(-1, piece));
    }

    public void receiveMove(int position, int nextPieceId) {
        if (state != ClientState.IN_GAME || currentGame == null) {
            connection.sendError("Not in a game");
            return;
        }
        
        // Get the current piece to place (was set by opponent's previous move)
        Piece currentPiece = currentGame.getCurrentPiece();
        if (currentPiece == null) {
            connection.sendError("No piece to place");
            return;
        }
        
        // Place the current piece at the given position
        Move move = new Move(position, currentPiece);
        if (!currentGame.doMove(move)) {
            connection.sendError("Invalid move");
            return;
        }
        
        if (nextPieceId >= 0 && nextPieceId <= 15) {
            // Normal move: pick piece for opponent
            Piece nextPiece = currentGame.getPieceById(nextPieceId);
            if (nextPiece == null) {
                connection.sendError("Invalid piece");
                return;
            }
            currentGame.pickCurrentPiece(nextPiece);
        } else if (nextPieceId == PROTOCOL.CLAIM_QUARTO) {
            // Player claims Quarto
            if (currentGame.getBoard().hasWinningLine()) {
                gameManager.endGame(currentGame, PROTOCOL.VICTORY, playerName);
            } else {
                gameManager.endGame(currentGame, PROTOCOL.VICTORY, currentGame.getOpponentName());
            }
        } else if (nextPieceId == PROTOCOL.FINAL_PIECE_NO_CLAIM) {
            // Final piece placed without claiming Quarto
            // Game ends in draw (board full, no winner claimed)
            if (currentGame.isGameOver()) {
                gameManager.endGame(currentGame, PROTOCOL.DRAW, null);
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
            connection.sendFirstMove(move.getPieceId());
        } else {
            // Subsequent move: MOVE~<position>~<pieceId>
            connection.sendMove(move.getBoardIndex(), move.getPieceId());
        }
    }

    @Override
    public void gameFinished(Game game) {
        // TODO: Get winner and reason from game
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