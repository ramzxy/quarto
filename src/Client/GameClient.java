package Client;

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
    private ClientConnection connection;
    private AbstractPlayer player;
    private Game localGame;
    private ClientView view;
    private boolean loggedIn = false;
    private boolean inQueue = false;
    private boolean inGame = false;

    /**
     * Creates a new GameClient and connects to the specified server.
     * @param host the server host
     * @param port the server port
     * @param playerName the player's username
     * @param view the view for displaying game state
     * @throws IOException if connection fails
     */
    public GameClient(String host, int port, AbstractPlayer playerName, ClientView view) throws IOException {
        this.player = playerName;
        this.view = view;
        this.connection = new ClientConnection(host, port);
        this.connection.setGameClient(this);
    }

    /**
     * Starts the connection and begins the handshake.
     */
    public void start() {
        connection.start();
        connection.sendHello("GameClient");
    }

    // --- Protocol receive handlers (called by ClientConnection) ---

    public void receiveHello(String serverDescription) {
        System.out.println("Connected to: " + serverDescription);
        // Auto-login after handshake
        connection.sendLogin(player.getName());
    }

    public void receiveLogin() {
        loggedIn = true;
        System.out.println("Logged in as: " + player);
        view.showLoggedIn(player.getName());
    }

    public void receiveAlreadyLoggedIn() {
        System.out.println("Username '" + player + "' is already taken");
        view.showError("Username already taken");
    }

    public void receiveList(String[] users) {
        view.showUserList(users);
    }

    public void receiveNewGame(AbstractPlayer player1, AbstractPlayer player2) {
        boolean iAmFirst = player1.equals(player);
        
        inGame = true;
        inQueue = false;
        localGame = new Game(player1, player2); // Create fresh game
        
        System.out.println("Game started! " + player1.getName() + " vs " + player2.getName());
        view.showGameStarted(player1.getName(), player2.getName(), iAmFirst);
        view.displayGame(localGame);
    }

    public void receiveFirstMove(int pieceId) {

        Piece piece = localGame.getPieceById(pieceId);
        if (piece != null) {
            localGame.pickCurrentPiece(piece);
        } else {
            System.err.println("Received unknown piece ID: " + pieceId);
        }
        
        view.showMove(new String[]{"MOVE", String.valueOf(pieceId)});
        view.displayGame(localGame);
    }

    public void receiveMove(int position, int pieceId) {
        
        // 1. Place the piece we were holding (currentPieceToPlace) at position
        Piece pieceToPlace = localGame.getCurrentPiece(); // This is what we were given previously
        if (pieceToPlace != null) {
            localGame.doMove(new Move(position, pieceToPlace));
        }
        
        // 2. The opponent picked 'pieceId' for us to play next.
        Piece nextPiece = localGame.getPieceById(pieceId);
        if (nextPiece != null) {
            localGame.pickCurrentPiece(nextPiece);
        }

        view.showMove(new String[]{"MOVE", String.valueOf(position), String.valueOf(pieceId)});
        view.displayGame(localGame);
    }

    public void receiveGameOver(String reason, String winner) {
        inGame = false;
        localGame = null;
        
        System.out.println("Game over: " + reason + (winner != null ? " Winner: " + winner : ""));
        view.showGameOver(reason, winner);
    }

    public void receiveError(String error) {
        System.out.println("Server error: " + error);
        view.showError(error);
    }

    public void receiveDisconnect() {
        System.out.println("Disconnected from server");
        view.showDisconnected();
    }

    // --- Public API for user actions ---

    /**
     * Request to join the matchmaking queue.
     */
    public void joinQueue() {
        if (!loggedIn) {
            view.showError("Not logged in");
            return;
        }
        connection.sendQueue();
        inQueue = true;
    }

    /**
     * Request to leave the matchmaking queue.
     */
    public void leaveQueue() {
        if (inQueue) {
            connection.sendQueue();
            inQueue = false;
        }
    }

    /**
     * Request the list of online players.
     */
    public void requestPlayerList() {
        connection.sendList();
    }

    /**
     * Make a move in the current game.
     * @param position the board position to place the piece
     * @param nextPieceId the piece to give to the opponent
     */
    public void makeMove(int position, int nextPieceId) {
        if (!inGame) {
            view.showError("Not in a game");
            return;
        }
        connection.sendMove(position, nextPieceId);
    }

    /**
     * Make the first move (just give a piece, no placement).
     * @param pieceId the piece to give to the opponent
     */
    public void makeFirstMove(int pieceId) {
        if (!inGame) {
            view.showError("Not in a game");
            return;
        }
        connection.sendFirstMove(pieceId);
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        connection.close();
    }

    // --- Getters ---

    public boolean isInGame() {
        return inGame;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isInQueue() {
        return inQueue;
    }

    public Game getLocalGame() {
        return localGame;
    }

    public AbstractPlayer getPlayer() {
        return player;
    }
}
