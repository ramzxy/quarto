package Game;

public class Move {
    public int boardIndex;
    public Piece piece;
    public Piece nextPiece;

    /**
     * Where to place the piece. 0-15.
     * Returns -1 if this is the first move of the game (picking only).
     */
    public int getBoardIndex () {return boardIndex;}
    
    /**
     * The piece being placed.
     */
    public Piece getPiece() {return piece;}
    
    /**
     * The piece chosen for the opponent.
     */
    public Piece getNextPiece() {return nextPiece;}

    /**
     * Create a move where we don't know the next piece yet.
     */
    public Move(int boardIndex, Piece piece){
        this(boardIndex, piece, null);
    }

    /**
     * Create a full move: Place a piece, then pick one.
     */
    public Move(int boardIndex, Piece piece, Piece nextPiece) {
        this.boardIndex = boardIndex;
        this.piece = piece;
        this.nextPiece = nextPiece;
    }

    /**
     * Helper to get ID of piece.
     */
    public int getPieceId() {
        return piece != null ? piece.getId() : -1;
    }

}
