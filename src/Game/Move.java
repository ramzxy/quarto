package Game;

public class Move {
    public int boardIndex;
    public Piece piece;

    public int getBoardIndex () {return boardIndex;}
    public Piece getPiece() {return piece;}

    public Move(int boardIndex, Piece piece){
        this.piece = piece;
        this.boardIndex = boardIndex;
    }
}
