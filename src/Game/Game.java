package Game;

import java.util.List;

public class Game {
    private Board board;
    private List<Piece> availablePieces;
    private Piece currentPieceToPlace;
    private AbstractPlayer[] playerList;
    private int currentTurn;
    private List<GameListener> gameListeners;


    /**
     * Main function to be called when starting a particular game.
     */
    public void start(){

    }

    /**
     * Function to do a move.
     * @param move The move to be played
     */
    public void doMove(Move move){

    }

    /**
     * Gives all valid moves that can be played in the current state of the game.
     * @return List of all moves that can be played
     */
    public List<Move> getValidMoves(){
        return null;
    }

    /**
     * Function to add a game listener to the game.
     * @param gameListener Implementation of GameListener to be added
     */
    public void addListener(GameListener gameListener){

    }

    /**
     * Notifies all listeners to the game about the current state of the game.
     */
    public void notifyListeners(){

    }
}
