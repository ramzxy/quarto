package Game;

/**
 * Base class for any player (Human or Computer).
 */
public abstract class AbstractPlayer {
    private String name;

    /**
     * Sets the player's name.
     */
    public AbstractPlayer(String name) {
        this.name = name;
    }

    /**
     * Decides the next move based on the game state.
     * This is where the AI or Human input logic goes.
     *
     * @param game The current game object
     * @return The decided move
     */
    public abstract Move determineMove(Game game);

    /**
     * Returns the player's name.
     */
    public String getName(){
        return name;
    }
}
