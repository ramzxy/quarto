package Server;

import Game.Game;

/**
 * Represents an active game session and manages communication between its players.
 * Handles broadcasting moves and game-over notifications to both participants.
 */
public class GameSession {
    private Game game;
    private ClientHandler player1;
    private ClientHandler player2;

    /**
     * Creates a new game session.
     * @param game the game instance
     * @param player1 the first player
     * @param player2 the second player
     */
    public GameSession(Game game, ClientHandler player1, ClientHandler player2) {
        this.game = game;
        this.player1 = player1;
        this.player2 = player2;
    }

    /**
     * Broadcasts a move to both players in this session.
     * @param position the board position (-1 for first move)
     * @param nextPieceId the next piece ID (0-15, or 16/17 for special moves)
     */
    public void broadcastMove(int position, int nextPieceId) {
        if (position < 0) {
            // First move: MOVE~<pieceId>
            player1.getConnection().sendFirstMove(nextPieceId);
            player2.getConnection().sendFirstMove(nextPieceId);
        } else {
            // Subsequent move: MOVE~<position>~<pieceId>
            player1.getConnection().sendMove(position, nextPieceId);
            player2.getConnection().sendMove(position, nextPieceId);
        }
    }

    /**
     * Broadcasts game over to both players.
     * Does NOT clear state - that's handled by GameManager.cleanupSession().
     * @param reason the game end reason (VICTORY, DRAW, DISCONNECT)
     * @param winner the winner's name, or null for DRAW
     */
    public void broadcastGameOver(String reason, String winner) {
        Server.log("GameSession", "Game finished (Reason: " + reason + ")");
        player1.getConnection().sendGameOver(reason, winner);
        player2.getConnection().sendGameOver(reason, winner);
    }

    /**
     * Notifies the opponent that the other player disconnected.
     * Does NOT clear state - that's handled by GameManager.
     * @param disconnectedPlayer the player who disconnected
     */
    public void notifyOpponentDisconnect(ClientHandler disconnectedPlayer) {
        ClientHandler opponent = (disconnectedPlayer == player1) ? player2 : player1;
        Server.log("GameSession", opponent.getPlayerName() + " game finished (Reason: DISCONNECT)");
        opponent.getConnection().sendGameOver("DISCONNECT", opponent.getPlayerName());
    }

    /**
     * Gets the game instance for this session.
     * @return the game
     */
    public Game getGame() {
        return game;
    }

    /**
     * Gets player 1 of this session.
     * @return player 1
     */
    public ClientHandler getPlayer1() {
        return player1;
    }

    /**
     * Gets player 2 of this session.
     * @return player 2
     */
    public ClientHandler getPlayer2() {
        return player2;
    }
}
