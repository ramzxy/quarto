package Server;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Central coordinator for matchmaking, username management, and active game tracking.
 * Handles player queuing, game creation, and cleanup on disconnection.
 */
public class GameManager {
    private BlockingQueue<ClientHandler> waitingQueue;
    private Set<String> takenUsernames;
    private List<Game> activeGames;
    private Map<ClientHandler, ClientHandler> rematchRequests;

    public GameManager() {
        this.waitingQueue = new ArrayBlockingQueue<>(10);
        this.takenUsernames = new HashSet<>();
        this.activeGames = new ArrayList<>();
        this.rematchRequests = new HashMap<>();
    }

    /**
     * Attempts to register a unique username.
     * @param name the desired username
     * @return true if registration succeeded, false if username is taken
     */
    public boolean registerUsername(String name) {
        synchronized (takenUsernames) {
            if (takenUsernames.contains(name)) {
                return false;
            }
            takenUsernames.add(name);
            return true;
        }
    }

    /**
     * Releases a username, making it available for others.
     * @param name the username to release
     */
    public void releaseUsername(String name) {
        synchronized (takenUsernames) {
            takenUsernames.remove(name);
        }
    }

    /**
     * Handles a rematch request. If both players request each other, starts a new game.
     * @param requester the client requesting a rematch
     * @param opponent the intended opponent
     */
    public void requestRematch(ClientHandler requester, ClientHandler opponent) {
        if (rematchRequests.get(opponent) == requester) {
            rematchRequests.remove(opponent);
            createGame(requester, opponent);
        } else {
            rematchRequests.put(requester, opponent);
            opponent.sendMessage("REMATCH_REQUEST", requester.getPlayerName());
        }
    }

    /**
     * Declines any pending rematch request from this client.
     * @param decliner the client declining
     */
    public void declineRematch(ClientHandler decliner) {
        rematchRequests.remove(decliner);
    }

    /**
     * Adds a client to the matchmaking queue and attempts to pair players.
     * @param client the client to queue
     */
    public void queueForGame(ClientHandler client) {
        waitingQueue.add(client);
        tryMatchPlayers();
    }

    /**
     * Checks if two or more players are waiting and pairs them into games.
     */
    private synchronized void tryMatchPlayers() {
        while (waitingQueue.size() >= 2) {
            ClientHandler player1 = waitingQueue.poll();
            ClientHandler player2 = waitingQueue.poll();
            
            if (player1 != null && player2 != null) {
                createGame(player1, player2);
            }
        }
    }

    /**
     * Removes a client from the matchmaking queue.
     * @param client the client to remove
     */
    public void removeFromQueue(ClientHandler client) {
        waitingQueue.remove(client);
    }

    /**
     * Creates a new game between two players, notifies them, and tracks the game.
     * @param p1 the first player
     * @param p2 the second player
     * @return the newly created Game instance
     */
    public Game createGame(ClientHandler p1, ClientHandler p2) {
        Game game = new Game(p1, p2);
        activeGames.add(game);
        
        p1.setCurrentGame(game);
        p2.setCurrentGame(game);
        
        p1.sendMessage("GAMESTART", p2.getPlayerName());
        p2.sendMessage("GAMESTART", p1.getPlayerName());
        
        p1.sendMessage("YOURTURN");
        
        return game;
    }

    /**
     * Removes a game from the active games list.
     * @param game the game to end
     */
    public void endGame(Game game) {
        activeGames.remove(game);
    }

    /**
     * Handles all cleanup when a client disconnects: removes from queue and releases username.
     * @param client the disconnected client
     */
    public void handleDisconnect(ClientHandler client) {
        removeFromQueue(client);
        releaseUsername(client.getPlayerName());
    }
}
