package Game;

public class Piece {
    public final int id;
    public final boolean isTall;
    public final boolean isRound;
    public final boolean isSolid;
    public final boolean isDark;

    /**
     * Constructs a unique piece based on their characteristics and giving each one an id.
     *
     * @param id       unique id given to every piece
     * @param isHollow solid or hollow
     * @param isRound  round or square
     * @param isLarge  tall or short
     * @param isDark   dark or light-colored
     */
    public Piece(int id, boolean isHollow, boolean isRound, boolean isLarge, boolean isDark) {
        this.id = id;
        this.isRound = isRound;
        this.isSolid = isHollow;
        this.isDark = isDark;
        this.isTall = isLarge;
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
