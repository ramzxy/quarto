package Client;

import Client.views.ClientView;
import Game.Game;
import Game.AbstractPlayer;
import Game.Move;
import Game.Piece;

import java.io.IOException;

/**
 * Main game client that handles game state and user interaction.
 * Receives parsed protocol events from ClientConnection.
 */
public class GameClient {
    protected ClientConnection connection;
    protected AbstractPlayer player;
    protected Game localGame;
    protected ClientView view;
    protected boolean loggedIn = false;
    protected boolean inQueue = false;
    protected boolean inGame = false;

    /**
     * Connects to the server.
     *
     * @param host Address of the server
     * @param port Port of the server
     * @param view The UI to use
     */
    public GameClient(String host, int port, ClientView view) throws IOException {
        this.view = view;
        this.connection = new ClientConnection(host, port);
        this.connection.setGameClient(this);
    }

    /**
     * Helper to start the connection and say Hello.
     */
    public void start() {
        connection.start();
        connection.sendHello("QuartoClient");
    }

    // --- Message Handlers (What to do when Server sends something) ---

    /**
     * Server said Hello. We can now login.
     * @param serverDescription The server's greeting message
     */
    public void receiveHello(String serverDescription) {
        System.out.println("Connected to server: " + serverDescription);
        System.out.println("Type 'login' to sign in.");
    }
    
    /**
     * Send login request to server.
     * @param username The name you want to use
     */
    public void login(String username) {
        this.player = new HumanPlayer(username);
        connection.sendLogin(username);
    }

    /**
     * Login worked!
     */
    public void receiveLogin() {
        loggedIn = true;
        view.showLoggedIn(player.getName());
    }

    /**
     * Login failed (name taken). Try again.
     */
    public void receiveAlreadyLoggedIn() {
        view.showError("Username '" + player.getName() + "' is already in use.");
        
        // Ask for a new name
        String username = view.promptUsername();
        this.player = new HumanPlayer(username);
        connection.sendLogin(username);
    }

    /**
     * Server sent the list of players. Show it.
     * @param users The array of usernames
     */
    public void receiveList(String[] users) {
        view.showUserList(users);
    }

    /**
     * Game is starting!
     * @param player1 The first player
     * @param player2 The second player
     */
    public void receiveNewGame(AbstractPlayer player1, AbstractPlayer player2) {
        boolean iAmFirst = player1.getName().equals(player.getName());
        inGame = true;
        inQueue = false;
        localGame = new Game(player1, player2);
        view.showGameStarted(player1.getName(), player2.getName(), iAmFirst);

        if (player instanceof HumanPlayer) {
             view.displayGame(localGame);
        }

        // if it's my turn, do something
        checkAndPerformMove();
    }

    /**
     * The other player made the very first move (just picked a piece).
     * @param pieceId The piece they picked
     */
    public void receiveFirstMove(int pieceId) {
        Piece piece = localGame.getPieceById(pieceId);
        if (piece != null) {
            // Apply the move locally
            localGame.doMove(new Move(-1, null, piece));
        } else {
            System.err.println("Received unknown piece ID: " + pieceId);
        }
        
        view.showMove(new String[]{"MOVE", String.valueOf(pieceId)});
        if (player instanceof HumanPlayer) {
             view.displayGame(localGame);
        }
        
        checkAndPerformMove();
    }

    /**
     * The other player made a move (placed one, picked next).
     * @param position Where they placed the piece
     * @param pieceId The piece they picked for us
     */
    public void receiveMove(int position, int pieceId) {
        // Place the piece we were holding
        Piece pieceToPlace = localGame.getCurrentPiece();
        // The piece they picked for us
        Piece nextPiece = localGame.getPieceById(pieceId);
        
        if (pieceToPlace != null) {
            localGame.doMove(new Move(position, pieceToPlace, nextPiece));
        }
        
        view.showMove(new String[]{"MOVE", String.valueOf(position), String.valueOf(pieceId)});
        if (player instanceof HumanPlayer) {
             view.displayGame(localGame);
        }

        checkAndPerformMove();
    }
    
    /**
     * Checks if it is my turn. If it is, ask player for a move and send it.
     */
    protected void checkAndPerformMove() {
        if (!localGame.getCurrentPlayerName().equals(player.getName())) {
            return;
        }
        Move move = player.determineMove(localGame);

        if (move != null) {
            if (move.getBoardIndex() == -1) {
                // First move (picking piece only)
                makeFirstMove(move.getNextPiece().getId());
            } else {
                int nextPieceId = move.getNextPiece() != null ? move.getNextPiece().getId() : -1;

                if (move.getNextPiece() == null) {
                    System.err.println("Warning: Move had null nextPiece, defaulting to -1");
                }
                
                makeMove(move.getBoardIndex(), nextPieceId);
            }
        }
    }

    /**
     * Game over. Show result.
     * @param reason Why it ended (VICTORY, DRAW, DISCONNECT)
     * @param winner The winner's name (if any)
     */
    public void receiveGameOver(String reason, String winner) {
        inGame = false;
        localGame = null;
        view.showGameOver(reason, winner);
    }

    /**
     * Error from server.
     * @param error The error message
     */
    public void receiveError(String error) {
        view.showError(error);
    }

    /**
     * Server disconnected us.
     */
    public void receiveDisconnect() {
        view.showDisconnected();
    }

    // --- Actions called by the UI ---

    /**
     * Use this to join the game queue.
     */
    public void joinQueue() {
        if (!loggedIn) {
            view.showError("Not logged in yet.");
            return;
        }
        connection.sendQueue();
        inQueue = true;
    }

    /**
     * Use this to leave the game queue.
     */
    public void leaveQueue() {
        if (inQueue) {
            connection.sendQueue();
            inQueue = false;
        }
    }

    /**
     * Ask server who is online.
     */
    public void requestPlayerList() {
        connection.sendList();
    }

    /**
     * Send a move to the server.
     * @param position Where to put the piece
     * @param nextPieceId Which piece to give the other player
     */
    public void makeMove(int position, int nextPieceId) {
        if (!inGame) {
            view.showError("Not in a game.");
            return;
        }
        if (!localGame.getCurrentPlayerName().equals(player.getName())) {
            view.showError("It is not your turn! Waiting for opponent.");
            return;
        }
        connection.sendMove(position, nextPieceId);
    }

    /**
     * Send the first move (picking a piece).
     * @param pieceId The piece to give
     */
    public void makeFirstMove(int pieceId) {
        if (!inGame) {
            view.showError("Not in a game.");
            return;
        }
        if (!localGame.getCurrentPlayerName().equals(player.getName())) {
            view.showError("It is not your turn! Waiting for opponent.");
            return;
        }
        connection.sendFirstMove(pieceId);
    }

    /**
     * Disconnects from server.
     */
    public void disconnect() {
        connection.close();
    }

    // --- Getters ---

    /**
     * @return true if currently playing a game
     */
    public boolean isInGame() {
        return inGame;
    }

    /**
     * @return true if logged in
     */
    public boolean isLoggedIn() {
        return loggedIn;
    }

    /**
     * @return true if waiting in queue
     */
    public boolean isInQueue() {
        return inQueue;
    }

    /**
     * @return The local game state object
     */
    public Game getLocalGame() {
        return localGame;
    }

    /**
     * @return The local player object
     */
    public AbstractPlayer getPlayer() {
        return player;
    }
}
