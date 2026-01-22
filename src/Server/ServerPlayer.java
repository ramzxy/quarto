package Server;

import Game.AbstractPlayer;
import Game.Game;
import Game.Move;

/**
 * Server-side player representation for networked games.
 * Acts as a proxy - moves are received from network messages, not computed locally.
 */
public class ServerPlayer extends AbstractPlayer {

    public ServerPlayer(String name) {
        super(name);
    }

    @Override
    public Move determineMove(Game game) {
        // Server players don't determine moves locally - moves come from network messages
        throw new UnsupportedOperationException("Server players receive moves from network");
    }
}
