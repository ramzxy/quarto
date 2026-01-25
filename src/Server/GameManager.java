package Server;

import Game.Game;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Central coordinator for matchmaking, username management, and active game tracking.
 * Handles player queuing, game session creation, and cleanup on disconnection.
 */
public class GameManager {
    private Queue<ClientHandler> waitingQueue;
    private Map<String, ClientHandler> loggedInUsers;
    private Map<Game, GameSession> activeSessions;

    public GameManager() {
        this.waitingQueue = new ConcurrentLinkedQueue<>();
        this.loggedInUsers = Collections.synchronizedMap(new HashMap<>());
        this.activeSessions = Collections.synchronizedMap(new HashMap<>());
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
     * Creates a new game session between two players, notifies them with NEWGAME, and tracks the session.
     * First player listed makes the first move.
     * @param p1 the first player (will move first)
     * @param p2 the second player
     * @return the newly created GameSession instance
     */
    public GameSession createGame(ClientHandler p1, ClientHandler p2) {
        Game game = new Game(new ServerPlayer(p1.getPlayerName()), new ServerPlayer(p2.getPlayerName()));
        GameSession session = new GameSession(game, p1, p2);
        activeSessions.put(game, session);
        
        // Initialize game state for both clients
        p1.startGame(session);
        p2.startGame(session);
        
        // NEWGAME~player1~player2 - first player moves first
        p1.sendNewGame(p1.getPlayerName(), p2.getPlayerName());
        p2.sendNewGame(p1.getPlayerName(), p2.getPlayerName());
        
        Server.log("GameManager", "Created game between " + p1.getPlayerName() + " and " + p2.getPlayerName());
        return session;
    }

    /**
     * Ends a game session by broadcasting game over and cleaning up.
     * @param session the game session to end
     * @param reason the game end reason (VICTORY, DRAW)
     * @param winner the winner's name, or null for DRAW
     */
    public void endGame(GameSession session, String reason, String winner) {
        session.broadcastGameOver(reason, winner);
        cleanupSession(session);
    }

    /**
     * Cleans up a finished game session by clearing player states and removing from active sessions.
     * @param session the game session to clean up
     */
    private void cleanupSession(GameSession session) {
        session.getPlayer1().clearGameState();
        session.getPlayer2().clearGameState();
        activeSessions.remove(session.getGame());
    }

    /**
     * Handles all cleanup when a client disconnects.
     * @param client the disconnected client
     */
    public void handleDisconnect(ClientHandler client) {
        removeFromQueue(client);
        releaseUsername(client.getPlayerName());
        
        // If in a game, notify opponent and clean up both players
        GameSession session = client.getGameSession();
        if (session != null) {
            session.notifyOpponentDisconnect(client);
            cleanupSession(session);
        }
    }
}
