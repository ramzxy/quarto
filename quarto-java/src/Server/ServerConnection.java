package Server;

import Networking.SocketConnection;
import Protocol.PROTOCOL;

import java.io.IOException;
import java.net.Socket;

/**
 * Server-side connection wrapper that extends the base SocketConnection.
 * Handles protocol parsing and delegates to ClientHandler for business logic.
 */
public class ServerConnection extends SocketConnection {
    
    private ClientHandler clientHandler;

    /**
     * Creates a new ServerConnection wrapping the given socket.
     * @param socket the client socket to wrap
     * @throws IOException if I/O streams cannot be created
     */
    public ServerConnection(Socket socket) throws IOException {
        super(socket);
    }

    /**
     * Sets the handler for client events.
     * @param clientHandler the handler to delegate to
     */
    public void setClientHandler(ClientHandler clientHandler) {
        this.clientHandler = clientHandler;
    }

    /**
     * Parses incoming protocol messages and delegates to ClientHandler.
     */
    @Override
    protected void handleMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        String[] parts = message.split(PROTOCOL.SEPARATOR);
        
        if (parts.length < 1) {
            return;
        }
        
        String command = parts[0];
        
        switch (command) {
            case PROTOCOL.HELLO:
                if (parts.length < 2) {
                    sendError("HELLO requires client description");
                    return;
                }
                // Collect extensions if present
                String[] extensions = new String[parts.length - 2];
                System.arraycopy(parts, 2, extensions, 0, extensions.length);
                clientHandler.receiveHello(parts[1], extensions);
                break;
            case PROTOCOL.LOGIN:
                if (parts.length < 2) {
                    sendError("LOGIN requires username");
                    return;
                }
                clientHandler.receiveLogin(parts[1]);
                break;
            case PROTOCOL.LIST:
                clientHandler.receiveList();
                break;
            case PROTOCOL.QUEUE:
                clientHandler.receiveQueue();
                break;
            case PROTOCOL.MOVE:
                if (parts.length == 2) {
                    // First move: just piece id
                    clientHandler.receiveFirstMove(Integer.parseInt(parts[1]));
                } else if (parts.length >= 3) {
                    // Regular move: position and next piece
                    clientHandler.receiveMove(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } else {
                    sendError("Invalid MOVE format");
                }
                break;
            default:
                sendError("Unknown command: " + command);
        }
    }

    /**
     * Called when client disconnects.
     */
    @Override
    protected void handleDisconnect() {
        clientHandler.receiveDisconnect();
    }


    public void sendHello(String serverDescription) {
        sendMessage(PROTOCOL.HELLO + PROTOCOL.SEPARATOR + serverDescription);
    }

    public void sendLogin() {
        sendMessage(PROTOCOL.LOGIN);
    }

    public void sendAlreadyLoggedIn() {
        sendMessage(PROTOCOL.ALREADYLOGGEDIN);
    }

    public void sendList(String[] usernames) {
        String msg = PROTOCOL.LIST;
        for (String username : usernames) {
            msg += PROTOCOL.SEPARATOR + username;
        }
        sendMessage(msg);
    }

    public void sendNewGame(String player1, String player2) {
        sendMessage(PROTOCOL.NEWGAME + PROTOCOL.SEPARATOR + player1 + PROTOCOL.SEPARATOR + player2);
    }

    public void sendMove(int position, int pieceId) {
        sendMessage(PROTOCOL.MOVE + PROTOCOL.SEPARATOR + position + PROTOCOL.SEPARATOR + pieceId);
    }

    public void sendFirstMove(int pieceId) {
        sendMessage(PROTOCOL.MOVE + PROTOCOL.SEPARATOR + pieceId);
    }

    public void sendGameOver(String reason, String winner) {
        String msg = PROTOCOL.GAMEOVER + PROTOCOL.SEPARATOR + reason;
        if (winner != null) {
            msg += PROTOCOL.SEPARATOR + winner;
        }
        sendMessage(msg);
    }

    public void sendError(String message) {
        sendMessage(PROTOCOL.ERROR + PROTOCOL.SEPARATOR + message);
    }

    @Override
    public void start() {
        super.start();
    }
}
