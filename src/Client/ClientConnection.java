package Client;

import Networking.SocketConnection;
import Protocol.PROTOCOL;

import java.io.IOException;

/**
 * Client-side connection that handles protocol parsing and delegates to GameClient.
 */
public class ClientConnection extends SocketConnection {
    
    private GameClient gameClient;

    /**
     * Create a new ClientConnection by connecting to the given host and port.
     * @param host the address of the server to connect to
     * @param port the port of the server to connect to
     * @throws IOException if the connection cannot be made
     */
    public ClientConnection(String host, int port) throws IOException {
        super(host, port);
    }

    /**
     * Sets the game client to delegate events to.
     * @param gameClient the game client
     */
    public void setGameClient(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    /**
     * Parses incoming protocol messages and delegates to GameClient.
     */
    @Override
    protected void handleMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        String[] parts = message.split(PROTOCOL.SEPARATOR);
        
        if (parts.length < 1) {
            System.out.println("Parse error: empty command");
            return;
        }

        String command = parts[0];

        switch (command) {
            case PROTOCOL.HELLO:
                String serverDesc = parts.length > 1 ? parts[1] : "Unknown";
                gameClient.receiveHello(serverDesc);
                break;
            case PROTOCOL.LOGIN:
                gameClient.receiveLogin();
                break;
            case PROTOCOL.ALREADYLOGGEDIN:
                gameClient.receiveAlreadyLoggedIn();
                break;
            case PROTOCOL.LIST:
                String[] users = new String[parts.length - 1];
                System.arraycopy(parts, 1, users, 0, users.length);
                gameClient.receiveList(users);
                break;
            case PROTOCOL.NEWGAME:
                if (parts.length >= 3) {
                    gameClient.receiveNewGame(new HumanPlayer(parts[1]), new HumanPlayer(parts[2]));
                }
                break;
            case PROTOCOL.MOVE:
                if (parts.length == 2) {
                    gameClient.receiveFirstMove(Integer.parseInt(parts[1]));
                } else if (parts.length >= 3) {
                    gameClient.receiveMove(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                }
                break;
            case PROTOCOL.GAMEOVER:
                String reason = parts.length > 1 ? parts[1] : "UNKNOWN";
                String winner = parts.length > 2 ? parts[2] : null;
                gameClient.receiveGameOver(reason, winner);
                break;
            case PROTOCOL.ERROR:
                String error = parts.length > 1 ? parts[1] : "Unknown error";
                gameClient.receiveError(error);
                break;
            default:
                System.out.println("Unknown server message: " + command);
        }
    }

    /**
     * Called when disconnected from server.
     */
    @Override
    protected void handleDisconnect() {
        gameClient.receiveDisconnect();
    }

    public void sendHello(String clientDescription) {
        sendMessage(PROTOCOL.HELLO + PROTOCOL.SEPARATOR + clientDescription);
    }

    public void sendLogin(String username) {
        sendMessage(PROTOCOL.LOGIN + PROTOCOL.SEPARATOR + username);
    }

    public void sendList() {
        sendMessage(PROTOCOL.LIST);
    }

    public void sendQueue() {
        sendMessage(PROTOCOL.QUEUE);
    }

    public void sendMove(int position, int pieceId) {
        sendMessage(PROTOCOL.MOVE + PROTOCOL.SEPARATOR + position + PROTOCOL.SEPARATOR + pieceId);
    }

    public void sendFirstMove(int pieceId) {
        sendMessage(PROTOCOL.MOVE + PROTOCOL.SEPARATOR + pieceId);
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void close(){
        super.close();
    }
}
