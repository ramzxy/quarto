package Server;

import Game.*;
import Protocol.PROTOCOL;
import java.io.IOException;
import java.net.Socket;

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

    /**
     * Creates a new handler for a client connection.
     * @param client the client socket
     * @param gameManager the shared game manager instance
     */
    public ClientHandler(Socket client, GameManager gameManager) throws IOException {
        this.connection = new ServerConnection(client);
        this.gameManager = gameManager;
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
     * @param message the raw message string (format: COMMAND~arg1~arg2~...)
     */
    private void handleProtocolMessage(String message) {
        String[] parts = message.split("~");
        if (parts.length == 0) return;
        
        String command = parts[0];
        
        try {
            switch (command) {
                case PROTOCOL.LOGIN:
                    if (parts.length < 2) {
                        sendMessage("ERROR", "Username required");
                        return;
                    }
                    handleLogin(parts[1]);
                    break;
                case "QUEUE":
                    handleQueue();
                    break;
                case "MOVE":
                    if (parts.length < 3) {
                        sendMessage("ERROR", "Invalid move format");
                        return;
                    }
                    handleMove(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    break;
                default:
                    sendMessage("ERROR", "Unknown command");
            }
        } catch (Exception e) {
            sendMessage("ERROR", "Invalid message format");
        }
    }

    /**
     * Handles LOGIN command: registers username if available.
     */
    private void handleLogin(String name) {
        if (gameManager.registerUsername(name)) {
            playerName = name;
            sendMessage("WELCOME", name);
        } else {
            sendMessage("ERROR", "Username already taken");
        }
    }

    /**
     * Handles QUEUE command: adds player to matchmaking queue.
     */
    private void handleQueue() {
        if (playerName == null) {
            sendMessage("ERROR", "Must login first");
            return;
        }
        gameManager.queueForGame(this);
        sendMessage("QUEUED");
    }

    /**
     * Handles MOVE command: validates and applies a game move.
     */
    private void handleMove(int boardIndex, int pieceId) {
        if (currentGame == null) {
            sendMessage("ERROR", "Not in a game");
            return;
        }
        // currentGame.doMove(new Move(boardIndex, pieceId), this);
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
     * Closes the connection and performs cleanup via GameManager.
     */
    public void disconnect() {
        gameManager.handleDisconnect(this);
        connection.close();
    }

    /**
     * @return the player's registered username, or null if not logged in */
    public String getPlayerName() { return playerName; }
    
    /**
     * @return the current game this player is in, or null if not in a game */
    public Game getCurrentGame() { return currentGame; }
    
    /**
     * Sets the current game for this player */
    public void setCurrentGame(Game game) { this.currentGame = game; }

    @Override
    public void moveMade(Move move) {
        sendMessage(PROTOCOL.OPPONENTMOVE, String.valueOf(move.getBoardIndex()), String.valueOf(move.getPieceId()));
    }

    @Override
    public void gameFinished(Game game) {
        // TODO: determine win/lose/tie and send appropriate message
    }
}