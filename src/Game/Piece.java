package Game;

public class Piece {
    public final int id;
    public final boolean isTall;
    public final boolean isRound;
    public final boolean isHollow;
    public final boolean isDark;

    /**
     * Creates a new Piece.
     * Pieces cannot be changed after creation (immutable).
     *
     * @param id Unique number ID (0-15)
     * @param isHollow True if hollow
     * @param isRound True if round
     * @param isLarge True if tall
     * @param isDark True if dark
     */
    public Piece(int id, boolean isHollow, boolean isRound, boolean isLarge, boolean isDark) {
        this.id = id;
        this.isRound = isRound;
        this.isHollow = isHollow;
        this.isDark = isDark;
        this.isTall = isLarge;
    }

    /**
     * Gets the ID of the piece.
     */
    public int getId(){return this.id;}

    /**
     * Returns a string for the TUI.
     * Example: [D*^] meaning Square, Dark, Solid, Tall.
     */
    @Override
    public String toString() {
        char left = isRound ? '(' : '[';
        char right = isRound ? ')' : ']';
        char color = isDark ? 'D' : 'L';
        char hollow = isHollow ? ' ' : '*';
        char height = isTall ? '^' : '_';
        
        return "" + left + color + hollow + height + right;
    }
}
