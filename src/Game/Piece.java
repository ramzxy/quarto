package Game;

public class Piece {
    final int id;
    final boolean isTall;
    final boolean isRound;
    final boolean isSolid;
    final boolean isDark;

    /**
     * Constructs a unique piece based on their characteristics and giving each one an id.
     * @param id unique id given to every piece
     * @param isRound round or square
     * @param isSolid solid or dotted
     * @param isDark dark or light-colored
     * @param isTall tall or short
     */
    public Piece(int id, boolean isRound, boolean isSolid, boolean isDark, boolean isTall) {
        this.id = id;
        this.isRound = isRound;
        this.isSolid = isSolid;
        this.isDark = isDark;
        this.isTall = isTall;
    }

    public int getId(){return this.id;}
}
