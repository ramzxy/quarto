package Game;

public class Move {
    public int boardIndex;
    public Piece piece;
    public Piece nextPiece;

    public int getBoardIndex () {return boardIndex;}
    public Piece getPiece() {return piece;}
    public Piece getNextPiece() {return nextPiece;}

    public Move(int boardIndex, Piece piece){
        this(boardIndex, piece, null);
    }

    public Move(int boardIndex, Piece piece, Piece nextPiece) {
        this.boardIndex = boardIndex;
        this.piece = piece;
        this.nextPiece = nextPiece;
    }

    public int getPieceId() {
        return piece != null ? piece.getId() : -1;
    }

}
