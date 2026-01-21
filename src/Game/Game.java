package Game;

import java.util.List;

public class Game {
    private Board board;
    private List<Piece> availablePieces;
    private Piece currentPieceToPlace;
    private AbstractPlayer[] playerList;
    private int currentTurn;
    private List<GameListener> gameListeners;

    private final int PIECE_NUMBER = 16;

    /**
     * Creates an instance of Quarto game with 2 players.
     */
    public Game(AbstractPlayer player1, AbstractPlayer player2){
        board = new Board();
        playerList = new AbstractPlayer[]{player1, player2};

        for(int i = 0; i < PIECE_NUMBER; i++){
            //Turns i into list of booleans that can be put inside piece's constructor
            boolean[] flags = new boolean[4];

            for(int j = 0; j < 4; j++){
                /*Right shifts the binary representation
                Take the least significant position, make it into boolean
                Put it from the last in the list
                */
                flags[3-j] = ((j >> 1) & 1) == 1;
            }
            availablePieces.add(new Piece(i, flags[0], flags[1], flags[2], flags[3]));
        }
    }
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
