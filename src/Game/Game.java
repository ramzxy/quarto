package Game;

import java.util.ArrayList;
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
        availablePieces = new ArrayList<>();
        gameListeners = new ArrayList<>();

        for(int i = 0; i < PIECE_NUMBER; i++){
            //Turns i into list of booleans that can be put inside piece's constructor
            boolean[] flags = new boolean[4];

            for(int j = 0; j < 4; j++){
                /*Right shifts the binary representation
                Take the least significant position, make it into boolean
                Put it from the last in the list
                */
                flags[3-j] = ((i >> j) & 1) == 1;
            }
            availablePieces.add(new Piece(i, flags[1], flags[0], flags[3], flags[2]));
        }
    }


    /**
     * Main function to be called when starting a particular game.
     */
    public void start(){
        boolean gameOver = false;
        while(!gameOver){
            //Game logic in chronological order
        }
    }

    /**
     * Does the move on the board if the move is valid.
     * Notifies listeners after move is applied.
     * @param move The move to be played
     * @return true if move was valid and applied, false otherwise
     */
    public boolean doMove(Move move){
        if(!getValidMoves().contains(move)){
            return false;
        }
        
        // Apply the move
        availablePieces.remove(currentPieceToPlace);
        board.setPiece(move.getBoardIndex(), currentPieceToPlace);
        
        // Notify listeners
        notifyMove(move);
        
        // Check if game is over
        if (isGameOver()) {
            notifyGameOver();
        }
        // Switch turns (piece picking is handled externally after doMove)
        currentTurn = (currentTurn + 1) % 2;
        
        return true;
    }

    /**
     * Before ending a turn, player has to pick the piece for the opponent to play.
     * @param piece the piece that is intended to be played next.
     */
    public void pickCurrentPiece(Piece piece){
        if(availablePieces.contains(piece)){
            currentPieceToPlace = piece;
            availablePieces.remove(piece);
        }else System.out.println("Piece is not valid, it has been used.");
    }
    /**
     * Gives all valid moves that can be played in the current state of the game.
     * @return List of all moves that can be played
     */
    public List<Move> getValidMoves(){
        List<Move> validMoves = new ArrayList<>();
        for(int i = 0; i < board.BOARD_SIZE* board.BOARD_SIZE; i++){
            if(board.getPiece(i) == null) validMoves.add(new Move(i, currentPieceToPlace));
        }
        return validMoves;
    }

    /**
     * Checks if the game is over (win or draw).
     * @return true if game is over
     */
    public boolean isGameOver() {
        return board.hasWinningLine() || board.isFull();
    }

    /**
     * Function to add a game listener to the game.
     * @param gameListener Implementation of GameListener to be added
     */
    public void addListener(GameListener gameListener){
        gameListeners.add(gameListener);
    }

    /**
     * Notifies all listeners that a move was made.
     * @param move the move that was made
     */
    public void notifyMove(Move move) {
        for (GameListener listener : gameListeners) {
            listener.moveMade(move);
        }
    }

    /**
     * Notifies all listeners that the game has finished.
     */
    public void notifyGameOver() {
        for (GameListener listener : gameListeners) {
            listener.gameFinished(this);
        }
    }

    public Board getBoard(){
        return board;
    }

    /**
     * Gets the current piece that must be placed.
     * @return the piece to be placed, or null if no piece is set
     */
    public Piece getCurrentPiece() {
        return currentPieceToPlace;
    }

    /**
     * Gets a piece by its ID from the available pieces.
     * @param pieceId the ID of the piece to find
     * @return the Piece, or null if not found/already used
     */
    public Piece getPieceById(int pieceId) {
        for (Piece p : availablePieces) {
            if (p.getId() == pieceId) {
                return p;
            }
        }
        return null;
    }

    /**
     * Gets the name of the opponent (the player who is NOT currently taking their turn).
     * @return the opponent's name
     */
    public String getOpponentName() {
        int opponentIndex = (currentTurn + 1) % 2;
        return playerList[opponentIndex].getName();
    }

    /**
     * Gets the name of the current player (who is taking their turn).
     * @return the current player's name
     */
    public String getCurrentPlayerName() {
        return playerList[currentTurn].getName();
    }

    /**
     * Gets the list of pieces still available to be picked.
     * @return list of available pieces
     */
    public List<Piece> getAvailablePieces() {
        return new ArrayList<>(availablePieces);
    }
}
