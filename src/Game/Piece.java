package Game;

public class Piece {
    public final int id;
    public final boolean isTall;
    public final boolean isRound;
    public final boolean isSolid;
    public final boolean isDark;

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

    /**
     * Formats a piece to a string representation:
     * Shape: () = Round, [] = Square
     * Color: D = Dark, L = Light
     * Solid: * = Solid, . = Hollow
     * Height: ^ = Tall, _ = Short
     * Format: [D*^] or (L._)
     */
    @Override
    public String toString() {
        char left = isRound ? '(' : '[';
        char right = isRound ? ')' : ']';
        char color = isDark ? 'D' : 'L';
        char solid = isSolid ? '*' : ' ';
        char height = isTall ? '^' : '_';
        
        return "" + left + color + solid + height + right;
    }
}
