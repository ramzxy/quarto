package Game;

public class Move {
    public int boardIndex;
    public int pieceId;

    public int getBoardIndex () {return boardIndex;}
    public int getPieceId() {return pieceId;}

    public Move(int boardIndex, int pieceId){
        this.pieceId = pieceId;
        this.boardIndex = boardIndex;
    }
}
