package Server;

import Game.*;
import Protocol.PROTOCOL;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles communication with a single connected client.
 * Parses incoming protocol messages and dispatches to appropriate handlers.
 * Implements Runnable to run in its own thread.
 */
public class ClientHandler implements Runnable, GameListener {
    private ServerConnection connection;
    private GameManager gameManager;
    private String playerName;
    private Game currentGame;
    private ClientState state;
    private List<String> supportedExtensions;
    private static final String SERVER_DESCRIPTION = "Quarto Server";

    /**
     * Creates a new handler for a client connection.
     * Waits for client to initiate handshake with HELLO.
     * @param client the client socket
     * @param gameManager the shared game manager instance
     */
    public ClientHandler(Socket client, GameManager gameManager) throws IOException {
        this.connection = new ServerConnection(client);
        this.gameManager = gameManager;
        this.state = ClientState.CONNECTED;
        this.supportedExtensions = new ArrayList<>();
    }

    /**
     * Main loop: reads messages from the client and processes them until disconnection.
     */
    @Override
    public void run() {
        try {
            String inputLine;
            while ((inputLine = connection.readMessage()) != null) {
                handleProtocolMessage(inputLine);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + playerName);
        } finally {
            disconnect();
        }
    }

    /**
     * Parses and dispatches a protocol message to the appropriate handler.
     * Enforces state-based command validation.
     * @param message the raw message string (format: COMMAND~arg1~arg2~...)
     */
    private void handleProtocolMessage(String message) {
        String[] parts = message.split(PROTOCOL.SEPARATOR);
        if (parts.length == 0) return;
        
        String command = parts[0];
        
        try {
            switch (command) {
                case PROTOCOL.HELLO:
                    handleHello(parts);
                    break;
                case PROTOCOL.LOGIN:
                    if (state != ClientState.HELLO_RECEIVED) {
                        sendMessage(PROTOCOL.ERROR, "Must send HELLO first");
                        return;
                    }
                    if (parts.length < 2) {
                        sendMessage(PROTOCOL.ERROR, "Username required");
                        return;
                    }
                    handleLogin(parts[1]);
                    break;
                case PROTOCOL.LIST:
                    if (state != ClientState.LOGGED_IN && state != ClientState.IN_QUEUE && state != ClientState.IN_GAME) {
                        sendMessage(PROTOCOL.ERROR, "Must login first");
                        return;
                    }
                    handleList();
                    break;
                case PROTOCOL.QUEUE:
                    if (state == ClientState.IN_GAME) {
                        // Ignore queue while in game
                        return;
                    }
                    if (state != ClientState.LOGGED_IN && state != ClientState.IN_QUEUE) {
                        sendMessage(PROTOCOL.ERROR, "Must login first");
                        return;
                    }
                    handleQueue();
                    break;
                case PROTOCOL.MOVE:
                    if (state != ClientState.IN_GAME) {
                        sendMessage(PROTOCOL.ERROR, "Not in a game");
                        return;
                    }
                    handleMove(parts);
                    break;
                default:
                    sendMessage(PROTOCOL.ERROR, "Unknown command");
            }
        } catch (Exception e) {
            sendMessage(PROTOCOL.ERROR, "Invalid message format");
        }
    }

    /**
     * Handles HELLO command from client: responds with server HELLO.
     */
    private void handleHello(String[] parts) {
        if (state != ClientState.CONNECTED) {
            sendMessage(PROTOCOL.ERROR, "Already completed handshake");
            return;
        }
        
        // Parse client extensions (if any)
        for (int i = 2; i < parts.length; i++) {
            supportedExtensions.add(parts[i]);
        }
        
        // Respond with server HELLO
        sendMessage(PROTOCOL.HELLO, SERVER_DESCRIPTION);
        state = ClientState.HELLO_RECEIVED;
    }

    /**
     * Handles LOGIN command: registers username if available.
     */
    private void handleLogin(String name) {
        if (gameManager.registerUsername(name, this)) {
            playerName = name;
            state = ClientState.LOGGED_IN;
            sendMessage(PROTOCOL.LOGIN);
        } else {
            sendMessage(PROTOCOL.ALREADYLOGGEDIN);
        }
    }

    /**
     * Handles LIST command: returns all logged-in users.
     */
    private void handleList() {
        List<String> users = gameManager.getLoggedInUsers();
        String[] usernames = users.toArray(new String[0]);
        sendMessage(PROTOCOL.LIST, usernames);
    }

    /**
     * Handles QUEUE command: toggles queue status.
     */
    private void handleQueue() {
        if (state == ClientState.IN_QUEUE) {
            // Already in queue, leave it
            gameManager.removeFromQueue(this);
            state = ClientState.LOGGED_IN;
        } else {
            // Join queue
            gameManager.queueForGame(this);
            state = ClientState.IN_QUEUE;
        }
    }

    /**
     * Handles MOVE command: validates and applies a game move.
     * First move: MOVE~<pieceId> (give piece to opponent)
     * Subsequent: MOVE~<position>~<pieceId> (place and give)
     */
    private void handleMove(String[] parts) {
        if (currentGame == null) {
            sendMessage(PROTOCOL.ERROR, "Not in a game");
            return;
        }
        
        try {
            if (parts.length == 2) {
                // First move: just give a piece
                int pieceId = Integer.parseInt(parts[1]);
                // currentGame.doFirstMove(pieceId, this);
            } else if (parts.length >= 3) {
                // Subsequent move: place piece and give next
                int position = Integer.parseInt(parts[1]);
                int pieceId = Integer.parseInt(parts[2]);
                // currentGame.doMove(position, pieceId, this);
            } else {
                sendMessage(PROTOCOL.ERROR, "Invalid move format");
            }
        } catch (NumberFormatException e) {
            sendMessage(PROTOCOL.ERROR, "Invalid move format");
        }
    }

    /**
     * Sends a formatted protocol message to the client.
     * @param command the command name
     * @param args optional arguments to append with separator
     */
    public void sendMessage(String command, String... args) {
        connection.sendMessage(command, args);
    }

    /**
     * Called when this client starts a game.
     */
    public void startGame(Game game) {
        this.currentGame = game;
        this.state = ClientState.IN_GAME;
    }

    /**
     * Closes the connection and performs cleanup via GameManager.
     */
    public void disconnect() {
        gameManager.handleDisconnect(this);
        connection.close();
    }

    /**
     * @return the player's registered username, or null if not logged in
     */
    public String getPlayerName() {
        return playerName;
    }
    
    /**
     * @return the current game this player is in, or null if not in a game
     */
    public Game getCurrentGame() {
        return currentGame;
    }
    
    /**
     * Sets the current game for this player
     */
    public void setCurrentGame(Game game) {
        this.currentGame = game;
    }
    
    /**
     * @return the current state of this client
     */
    public ClientState getState() {
        return state;
    }

    @Override
    public void moveMade(Move move) {
        // Broadcast MOVE to this player (server broadcasts to all players)
        if (move.getPieceId() >= 0) {
            sendMessage(PROTOCOL.MOVE, String.valueOf(move.getBoardIndex()), String.valueOf(move.getPieceId()));
        } else {
            // First move (only piece given)
            sendMessage(PROTOCOL.MOVE, String.valueOf(move.getBoardIndex()));
        }
    }

    @Override
    public void gameFinished(Game game) {
        // Send GAMEOVER with reason and winner
        // TODO: Get winner and reason from game
        state = ClientState.LOGGED_IN;
        currentGame = null;
    }
}