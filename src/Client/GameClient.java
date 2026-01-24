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
     * Creates a new GameClient and connects to the specified server.
     * Username is prompted after connection is established.
     * @param host the server host
     * @param port the server port
     * @param view the View for displaying game state and prompting user
     * @throws IOException if connection fails
     */
    public GameClient(String host, int port, ClientView view) throws IOException {
        this.view = view;
        this.connection = new ClientConnection(host, port);
        this.connection.setGameClient(this);
    }

    /**
     * Starts the connection and begins the handshake.
     */
    public void start() {
        connection.start();
        connection.sendHello("QuartoClient");
    }

    // --- Protocol receive handlers (called by ClientConnection) ---

    public void receiveHello(String serverDescription) {
        System.out.println("Connected to server: " + serverDescription);
        System.out.println("Type 'login' to sign in.");
    }
    
    public void login(String username) {
        System.out.println("[Client] LOGIN requested for: " + username);
        this.player = new HumanPlayer(username);
        connection.sendLogin(username);
    }

    public void receiveLogin() {
        loggedIn = true;
        System.out.println("[Client] LOGIN confirmed for: " + player.getName());
        view.showLoggedIn(player.getName());
    }

    public void receiveAlreadyLoggedIn() {
        view.showError("Username '" + player.getName() + "' is already in use.");
        
        // Prompt for a different username
        String username = view.promptUsername();
        this.player = new HumanPlayer(username);
        connection.sendLogin(username);
    }

    public void receiveList(String[] users) {
        System.out.println("[Client] LIST received: " + String.join(",", users));
        view.showUserList(users);
    }

    public void receiveNewGame(AbstractPlayer player1, AbstractPlayer player2) {
        boolean iAmFirst = player1.getName().equals(player.getName());
        System.out.println("[Client] NEWGAME p1=" + player1.getName()
                + " p2=" + player2.getName() + " iAmFirst=" + iAmFirst);
        
        inGame = true;
        inQueue = false;
        localGame = new Game(player1, player2);
        
        view.showGameStarted(player1.getName(), player2.getName(), iAmFirst);

        if (player instanceof HumanPlayer) {
             view.displayGame(localGame);
        }

        // Check if it's our turn immediately
        checkAndPerformMove();
    }

    public void receiveFirstMove(int pieceId) {
        System.out.println("[Client] MOVE(first) pieceId=" + pieceId);
        Piece piece = localGame.getPieceById(pieceId);
        if (piece != null) {
            // Use doMove with special first move syntax
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

    public void receiveMove(int position, int pieceId) {
        System.out.println("[Client] MOVE pos=" + position + " nextPieceId=" + pieceId);
        // Place the piece we were holding at position
        Piece pieceToPlace = localGame.getCurrentPiece();
        
        // The opponent picked 'pieceId' for us to play next
        Piece nextPiece = localGame.getPieceById(pieceId);
        
        if (pieceToPlace != null) {
            localGame.doMove(new Move(position, pieceToPlace, nextPiece));
        }
        
        view.showMove(new String[]{"MOVE", String.valueOf(position), String.valueOf(pieceId)});
        if (player instanceof HumanPlayer) {
             view.displayGame(localGame);
        }
        if (localGame.getCurrentPlayerName().equals(player.getName())){
        checkAndPerformMove();
        }
    }
    
    protected void checkAndPerformMove() {
        if (localGame == null || player == null) {
            return;
        }
        System.out.println("[Client] TURN check: current="
                + localGame.getCurrentPlayerName()
                + " me=" + player.getName()
                + " piece=" + (localGame.getCurrentPiece() == null ? "none" : localGame.getCurrentPiece().getId()));
        Move move = player.determineMove(localGame);
        System.out.println("Move Chosen: " + move.getBoardIndex() + " " + move.getNextPiece().getId());

        if (move != null) {

            if (move.getBoardIndex() == -1) {
                // First move (picking piece only)
                makeFirstMove(move.getNextPiece().getId());
            } else {
                makeMove(move.getBoardIndex(), move.getNextPiece().getId());
            }
        }
    }

    public void receiveGameOver(String reason, String winner) {
        inGame = false;
        localGame = null;
        System.out.println("[Client] GAMEOVER reason=" + reason + " winner=" + winner);
        
        view.showGameOver(reason, winner);
    }

    public void receiveError(String error) {
        System.out.println("[Client] ERROR " + error);
        view.showError(error);
    }

    public void receiveDisconnect() {
        System.out.println("[Client] DISCONNECT");
        view.showDisconnected();
    }

    // --- Public API for user actions ---

    /**
     * Request to join the matchmaking queue.
     */
    public void joinQueue() {
        if (!loggedIn) {
            view.showError("Not logged in yet.");
            return;
        }
        System.out.println("[Client] QUEUE join");
        connection.sendQueue();
        inQueue = true;
    }

    /**
     * Request to leave the matchmaking queue.
     */
    public void leaveQueue() {
        if (inQueue) {
            System.out.println("[Client] QUEUE leave");
            connection.sendQueue();
            inQueue = false;
        }
    }

    /**
     * Request the list of online players.
     */
    public void requestPlayerList() {
        System.out.println("[Client] LIST requested");
        connection.sendList();
    }

    /**
     * Make a move in the current game.
     * @param position the board position to place the piece
     * @param nextPieceId the piece to give to the opponent
     */
    public void makeMove(int position, int nextPieceId) {
        if (!inGame) {
            view.showError("Not in a game.");
            return;
        }
        System.out.println("[Client] SEND MOVE pos=" + position + " nextPieceId=" + nextPieceId);
        connection.sendMove(position, nextPieceId);
    }

    /**
     * Make the first move (just give a piece, no placement).
     * @param pieceId the piece to give to the opponent
     */
    public void makeFirstMove(int pieceId) {
        if (!inGame) {
            view.showError("Not in a game.");
            return;
        }
        System.out.println("[Client] SEND MOVE(first) pieceId=" + pieceId);
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
