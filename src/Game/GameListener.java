package Game;

public interface GameListener {

    void moveMade(Move move);

    void gameFinished(Game game);
}
