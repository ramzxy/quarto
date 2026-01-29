package Server;

import Game.*;

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

    /**
     * Creates the Game Manager.
     * Use this to manage players, queues, and active games.
     */
    public GameManager() {
        this.waitingQueue = new ConcurrentLinkedQueue<>();
        this.loggedInUsers = Collections.synchronizedMap(new HashMap<>());
        this.activeSessions = Collections.synchronizedMap(new HashMap<>());
    }

    /**
     * Tries to register a username.
     * Returns true if the name is available, false if taken.
     *
     * @param name The username to check
     * @param client The client asking for the name
     * @return true if successful
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
     * Frees up a username so someone else can use it.
     *
     * @param name The username to release
     */
    public void releaseUsername(String name) {
        if (name != null) {
            loggedInUsers.remove(name);
        }
    }

    /**
     * Gets a list of everyone currently logged in.
     *
     * @return List of usernames
     */
    public List<String> getLoggedInUsers() {
        return new ArrayList<>(loggedInUsers.keySet());
    }

    /**
     * Adds a player to the waiting line for a game.
     * Automatically tries to make a match after adding.
     *
     * @param client The player joining the queue
     */
    public void queueForGame(ClientHandler client) {
        waitingQueue.add(client);
        tryMatchPlayers();
    }

    /**
     * Checks if we have enough players to start a game.
     * If yes, it creates a game for them.
     * Thread-safe.
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
     * Removes a player from the waiting line.
     *
     * @param client The player leaving the queue
     */
    public void removeFromQueue(ClientHandler client) {
        waitingQueue.remove(client);
    }

    /**
     * Starts a new game between two players.
     * Sends the NEWGAME message to both.
     *
     * @param p1 Player 1
     * @param p2 Player 2
     * @return The new GameSession
     */
    public GameSession createGame(ClientHandler p1, ClientHandler p2) {
        Game game = new Game(new ServerPlayer(p1.getPlayerName()), new ServerPlayer(p2.getPlayerName()));
        GameSession session = new GameSession(game, p1, p2);
        activeSessions.put(game, session);
        
        // Tells the clients the game is starting
        p1.startGame(session);
        p2.startGame(session);
        
        // Notify them who is playing whom
        p1.sendNewGame(p1.getPlayerName(), p2.getPlayerName());
        p2.sendNewGame(p1.getPlayerName(), p2.getPlayerName());
        
        Server.log("GameManager", "Created game between " + p1.getPlayerName() + " and " + p2.getPlayerName());
        return session;
    }

    /**
     * Cleanly ends a game.
     * Tells players the result and removes the game from the active list.
     *
     * @param session The game session
     * @param reason Why it ended (VICTORY, DRAW)
     * @param winner Who won (or null)
     */
    public void endGame(GameSession session, String reason, String winner) {
        session.broadcastGameOver(reason, winner);
        cleanupSession(session);
    }

    /**
     * Internal helper to remove a session.
     */
    private void cleanupSession(GameSession session) {
        session.getPlayer1().clearGameState();
        session.getPlayer2().clearGameState();
        activeSessions.remove(session.getGame());
    }

    /**
     * Handles what happens when a player disconnects.
     * Removes them from queues, games, and frees their name.
     *
     * @param client The disconnected client
     */
    public void handleDisconnect(ClientHandler client) {
        removeFromQueue(client);
        releaseUsername(client.getPlayerName());
        
        // If they were in a game, tell the other player
        GameSession session = client.getGameSession();
        if (session != null) {
            session.notifyOpponentDisconnect(client);
            cleanupSession(session);
        }
    }
}
