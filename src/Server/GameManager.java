package Server;

import Game.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Central coordinator for matchmaking, username management, and active game tracking.
 * Handles player queuing, game creation, and cleanup on disconnection.
 */
public class GameManager {
    private Queue<ClientHandler> waitingQueue;
    private Map<String, ClientHandler> loggedInUsers;
    private List<Game> activeGames;

    public GameManager() {
        this.waitingQueue = new ConcurrentLinkedQueue<>();
        this.loggedInUsers = Collections.synchronizedMap(new HashMap<>());
        this.activeGames = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Attempts to register a unique username for a client.
     * @param name the desired username
     * @param client the client handler
     * @return true if registration succeeded, false if username is taken
     */
    public boolean registerUsername(String name, ClientHandler client) {
        synchronized (loggedInUsers) {
            if (loggedInUsers.containsKey(name)) {
                return false;
            }
            loggedInUsers.put(name, client);
            return true;
        }
    }

    /**
     * Releases a username, making it available for others.
     * @param name the username to release
     */
    public void releaseUsername(String name) {
        if (name != null) {
            loggedInUsers.remove(name);
        }
    }

    /**
     * Returns a list of all currently logged-in usernames.
     * @return list of usernames
     */
    public List<String> getLoggedInUsers() {
        return new ArrayList<>(loggedInUsers.keySet());
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
     * Creates a new game between two players, notifies them with NEWGAME, and tracks the game.
     * First player listed makes the first move.
     * @param p1 the first player (will move first)
     * @param p2 the second player
     * @return the newly created Game instance
     */
    public Game createGame(ClientHandler p1, ClientHandler p2) {
        Game game = new Game(new ServerPlayer(p1.getPlayerName()), new ServerPlayer(p2.getPlayerName()));
        activeGames.add(game);
        
        // Initialize game state for both clients
        p1.startGame(game);
        p2.startGame(game);

        game.addListener(p1);
        game.addListener(p2);
        
        // NEWGAME~player1~player2 - first player moves first
        p1.sendNewGame(p1.getPlayerName(), p2.getPlayerName());
        p2.sendNewGame(p1.getPlayerName(), p2.getPlayerName());
                
        return game;
    }

    /**
     * Ends a game and sends GAMEOVER to both players.
     * @param game the game to end
     * @param reason the reason (VICTORY, DRAW, DISCONNECT)
     * @param winner the winner's username (null for DRAW)
     */
    public void endGame(Game game, String reason, String winner) {
        activeGames.remove(game);
        
        // Get both players from the game
        // TODO: Need to get players from game object
    }

    /**
     * Handles all cleanup when a client disconnects.
     * @param client the disconnected client
     */
    public void handleDisconnect(ClientHandler client) {
        removeFromQueue(client);
        releaseUsername(client.getPlayerName());
        
        // If in a game, end it with DISCONNECT reason
        Game game = client.getCurrentGame();
        if (game != null) {
            activeGames.remove(game);
            // TODO: Notify opponent with GAMEOVER~DISCONNECT~opponentName
        }
    }
}
