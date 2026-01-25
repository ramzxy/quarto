package Game;

import java.util.ArrayList;
import java.util.List;

public class Game {
    private Board board;
    private List<Piece> availablePieces;
    private Piece currentPieceToPlace;
    private AbstractPlayer[] playerList;
    private int currentTurn;
    private String winnerName;
    private String endReason;

    private final int PIECE_NUMBER = 16;

    /**
     * Sets up a new game with 2 players.
     * Creates the board and all 16 pieces.
     *
     * @param player1 The first player
     * @param player2 The second player
     */
    public Game(AbstractPlayer player1, AbstractPlayer player2){
        board = new Board();
        playerList = new AbstractPlayer[]{player1, player2};
        availablePieces = new ArrayList<>();

        for(int i = 0; i < PIECE_NUMBER; i++){
            // Server encoding: bit0=dark, bit1=tall, bit2=square, bit3=hollow
            boolean isDark = (i & 1) != 0;
            boolean isTall = (i & 2) != 0;
            boolean isSquare = (i & 4) != 0;
            boolean isHollow = (i & 8) != 0;
            
            // Constructor: Piece(id, isHollow, isRound, isTall, isDark)
            availablePieces.add(new Piece(i, isHollow, !isSquare, isTall, isDark));
        }
    }


    /**
     * Tries to apply a move to the game.
     * A move has two steps:
     * 1. Place the piece you were given.
     * 2. Pick a piece for the next player.
     *
     * @param move The move object with the placement and next piece.
     * @return true if the move was valid and worked, false if it failed.
     */
    public boolean doMove(Move move){
        if (move == null) return false;

        // Special case: First move (selecting piece only, no placement)
        if (move.getBoardIndex() == -1) {
             if (move.getNextPiece() == null) return false; // Must pick a piece
             
             // Check if next piece is valid
             if (!availablePieces.contains(move.getNextPiece())) return false;
             
             pickCurrentPiece(move.getNextPiece());

             currentTurn = (currentTurn + 1) % 2;
             return true;
        }

        // Standard move: Validate Placement using helper
        if (!isValidMove(move)) return false;
        
        // Validate Piece placed (must match currentPieceToPlace)
        if (currentPieceToPlace == null) return false; 
        

        // Apply the move (Placement)
        availablePieces.remove(currentPieceToPlace);
        board.setPiece(move.getBoardIndex(), currentPieceToPlace);
        
        // Handle Next Piece Selection (if provided)
        if (move.getNextPiece() != null) {
            if (!availablePieces.contains(move.getNextPiece())) {
                // Invalid next piece -> Revert placement
                board.setPiece(move.getBoardIndex(), null); 
                availablePieces.add(currentPieceToPlace);
                return false;
            }
            pickCurrentPiece(move.getNextPiece());
        }
        
        
        // Check if game is over and set result (ClientHandler handles broadcasting)
        if (isGameOver()) {
            if (board.hasWinningLine()) {
                // The player who just placed the piece made the winning line
                setResult("VICTORY", playerList[currentTurn].getName());
            } else {
                // Board is full without a winning line
                setResult("DRAW", null);
            }
        }
        // Switch turns
        currentTurn = (currentTurn + 1) % 2;
        
        return true;
    }


    /**
     * Sets the piece that the next player has to play.
     *
     * @param piece The chosen piece
     */
    public void pickCurrentPiece(Piece piece){
        if(availablePieces.contains(piece)){
            currentPieceToPlace = piece;
            availablePieces.remove(piece);
        }else System.out.println("Piece is not valid, it has been used.");
    }

    /**
     * Returns a list of all valid moves right now.
     * A valid move is any empty spot on the board.
     *
     * @return List of valid moves
     */
    public List<Move> getValidMoves(){
        List<Move> validMoves = new ArrayList<>();
        for(int i = 0; i < board.BOARD_SIZE* board.BOARD_SIZE; i++){
            if(board.getPiece(i) == null) validMoves.add(new Move(i, currentPieceToPlace));
        }
        return validMoves;
    }

    /**
     * Checks if a move is valid (spot is empty and inside the board).
     *
     * @param move The move to check
     * @return true if valid
     */
    public boolean isValidMove(Move move) {
        if (move == null) return false;
        return move.getBoardIndex() >= 0 && move.getBoardIndex() < board.BOARD_SIZE * board.BOARD_SIZE 
               && board.getPiece(move.getBoardIndex()) == null;
    }

    /**
     * Checks if the game has ended (someone won or board is full).
     *
     * @return true if game is over
     */
    public boolean isGameOver() {
        return board.hasWinningLine() || board.isFull();
    }

    /**
     * Gets the current game board.
     */
    public Board getBoard(){
        return board;
    }

    /**
     * Returns the piece that must be placed on this turn.
     *
     * @return The piece to place
     */
    public Piece getCurrentPiece() {
        return currentPieceToPlace;
    }

    /**
     * Finds a piece in the available list by its ID.
     *
     * @param pieceId ID of the piece
     * @return The piece object, or null if not found
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
     * Gets the name of the other player.
     *
     * @return Opponent's name
     */
    public String getOpponentName() {
        int opponentIndex = (currentTurn + 1) % 2;
        return playerList[opponentIndex].getName();
    }

    /**
     * Gets the name of the player whose turn it is.
     *
     * @return Current player's name
     */
    public String getCurrentPlayerName() {
        return playerList[currentTurn].getName();
    }

    /**
     * Gets the list of pieces that haven't been used yet.
     *
     * @return List of available pieces
     */
    public List<Piece> getAvailablePieces() {
        return availablePieces;
    }

    /**
     * Sets who won and why.
     */
    public void setResult(String reason, String winner) {
        this.endReason = reason;
        this.winnerName = winner;
    }

    /**
     * Why the game ended (e.g., "VICTORY", "DRAW").
     * @return The reason the game ended
     */
    public String getEndReason() {
        return endReason;
    }

    /**
     * Who won the game.
     * @return The winner's name
     */
    public String getWinnerName() {
        return winnerName;
    }
}
