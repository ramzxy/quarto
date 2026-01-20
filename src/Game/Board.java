package Game;

public class Board {
    final int BOARD_SIZE = 4;
    private final Piece[] fields = new Piece[BOARD_SIZE*BOARD_SIZE];
    /*
    0 -     1 -     2 -     3
    4 -     5 -     6 -     7
    8 -     9 -     10 -    11
    12 -    13 -    14 -    15
     */

    /**
     * Gets a particular piece in the field.
     * @param index index of the piece in the board
     * @return the piece object being referred to, will return null if it's invalid
     */
    public Piece getPiece(int index){
        if(index >= fields.length || index < 0) {
            return null;
        }
        if(fields[index] == null) {
            return null;
        }
        return fields[index];
    }

    /**
     * Sets a particular piece in the field.
     * @param index index of the piece being placed
     * @param piece the piece object that is being played
     */
    public void setPiece(int index, Piece piece){
        try{
            fields[index] = piece;
        } catch (NullPointerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an XNOR comparison to every field of 4 pieces, if there are a common characteristic, it will return true.
     * @param a Piece 1
     * @param b Piece 2
     * @param c Piece 3
     * @param d Piece 4
     * @return true if there are similarities
     */
    private boolean lineChecker(Piece a, Piece b, Piece c, Piece d){
        if(!(a.isDark ^ b.isDark ^ c.isDark ^ d.isDark)) {
            return true;
        } else if (!(a.isTall ^ b.isTall ^ c.isTall ^ d.isTall)) {
            return true;
        } else if (!(a.isRound^b.isRound^c.isRound^d.isRound)) {
            return true;
        } else return !(a.isSolid ^ b.isSolid ^ c.isSolid ^ d.isSolid);
    }
    public boolean hasWinningLine(){
        //TODO: Create a checker for winning lines, true if there is, false if there isn't

        return true;
    }


    private boolean hasWinningHorizontal(){
        for(int i = 0; i < BOARD_SIZE; i++){
            for(int j = 0; j < BOARD_SIZE; i++){
                if(fields[(i*4)+j].isDark)
            }
        }
    }
    private boolean hasWinningVertical(){

    }
    private boolean hasWinningCross(){

    }

    /**
     * Checks if the board is full or not.
     * @return true if board is full with pieces, otherwise false
     */
    public boolean isFull(){
        for (Piece piece : fields) {
            if (piece != null) {
                return false;
            }
        }
        return true;
    }

    public Board copy(){
        Board copyOfBoard = this;
        return copyOfBoard;
    }
}
