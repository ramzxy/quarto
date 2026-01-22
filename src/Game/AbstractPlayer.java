package Game;

/**
 * This class acts as a template to every Player model that can play the game.
 */
public abstract class AbstractPlayer {
    private String name;

    public AbstractPlayer(String name) {
        this.name = name;
    }

    /**
     * Called when player has to make a move. The main method responsible for giving back a valid move.
     * @param game the game object which it's playing in
     * @return a valid move for the current game object
     */
    public abstract Move determineMove(Game game);

    /**
     * Gets the name of player.
     * @return name of player
     */
    public String getName(){
        return name;
    }
}
