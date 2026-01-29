package Game;

public class Board {
    final int BOARD_SIZE = 4;
    private final Piece[] fields = new Piece[BOARD_SIZE*BOARD_SIZE];
    /*
     * Board Index Mapping:
     * 0  - 1  - 2  - 3
     * 4  - 5  - 6  - 7
     * 8  - 9  - 10 - 11
     * 12 - 13 - 14 - 15
     */

    /**
     * Gets the piece at the specified index.
     * Index 0 to 15.
     *
     * @param index The position on the board (0-15)
     * @return The piece at that position, or null if it's empty
     */
    public Piece getPiece(int index){
        if(index >= fields.length || index < 0) {
            throw new IllegalArgumentException("Index is between 0 - " + (fields.length-1));
        }
        else if(fields[index] == null) {
            return null;
        }
        return fields[index];
    }

    /**
     * Puts a piece on the board at a specific spot.
     *
     * @param index The position index (0-15)
     * @param piece The piece to place there
     */
    public void setPiece(int index, Piece piece){
        try{
            fields[index] = piece;
        } catch (NullPointerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if 4 pieces make a winning line (row, column, or diagonal).
     * To win, they must share at least one trait (like all Tall or all Dark).
     *
     * Traits checked: Color, Height, Shape, Solidity.
     *
     * @param a First piece
     * @param b Second piece
     * @param c Third piece
     * @param d Fourth piece
     * @return true if they share a trait, false otherwise
     */
    private boolean lineChecker(Piece a, Piece b, Piece c, Piece d) {
        return allSame(a.isDark, b.isDark, c.isDark, d.isDark)
            || allSame(a.isTall, b.isTall, c.isTall, d.isTall)
            || allSame(a.isRound, b.isRound, c.isRound, d.isRound)
            || allSame(a.isHollow, b.isHollow, c.isHollow, d.isHollow);
    }

    /**
     * Returns true if all 4 boolean values are the same (all true or all false).
     */
    private boolean allSame(boolean v1, boolean v2, boolean v3, boolean v4) {
        return v1 == v2 && v2 == v3 && v3 == v4;
    }

    /**
     * Checks if there is a winner anywhere on the board.
     * Looks at rows, columns, and diagonals.
     *
     * @return true if someone has won
     */
    public boolean hasWinningLine() {
        return hasWinningHorizontal() || hasWinningVertical() || hasWinningCross();
    }


    /**
     * Helper to check all rows for a win.
     */
    private boolean hasWinningHorizontal() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            Piece[] line = new Piece[BOARD_SIZE];
            boolean complete = true;
            
            // Get all pieces in this row
            for (int col = 0; col < BOARD_SIZE; col++) {
                line[col] = fields[row * BOARD_SIZE + col];
                if (line[col] == null) {
                    complete = false;
                    break;
                }
            }
            
            // Check if they form a line
            if (complete && lineChecker(line[0], line[1], line[2], line[3])) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Helper to check all columns for a win.
     */
    private boolean hasWinningVertical() {
        for (int col = 0; col < BOARD_SIZE; col++) {
            Piece[] line = new Piece[BOARD_SIZE];
            boolean complete = true;
            
            // Get all pieces in this column
            for (int row = 0; row < BOARD_SIZE; row++) {
                line[row] = fields[row * BOARD_SIZE + col];
                if (line[row] == null) {
                    complete = false;
                    break;
                }
            }
            
            // Check if they form a line
            if (complete && lineChecker(line[0], line[1], line[2], line[3])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper to check the two diagonals for a win.
     */
    private boolean hasWinningCross() {
        // First diagonal (top-left to bottom-right)
        Piece[] diag1 = new Piece[BOARD_SIZE];
        boolean complete1 = true;
        for (int i = 0; i < BOARD_SIZE; i++) {
            diag1[i] = fields[i * BOARD_SIZE + i];
            if (diag1[i] == null) {
                complete1 = false;
                break;
            }
        }
        if (complete1 && lineChecker(diag1[0], diag1[1], diag1[2], diag1[3])) {
            return true;
        }
        
        // Second diagonal (top-right to bottom-left)
        Piece[] diag2 = new Piece[BOARD_SIZE];
        boolean complete2 = true;
        for (int i = 0; i < BOARD_SIZE; i++) {
            diag2[i] = fields[i * BOARD_SIZE + (BOARD_SIZE - 1 - i)];
            if (diag2[i] == null) {
                complete2 = false;
                break;
            }
        }
        if (complete2 && lineChecker(diag2[0], diag2[1], diag2[2], diag2[3])) {
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the board is completely full (no more empty spots).
     *
     * @return true if full, false otherwise
     */
    public boolean isFull(){
        for (Piece piece : fields) {
            if (piece == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a copy of this board.
     * Useful for AI to test moves without changing the real game.
     *
     * @return A new Board object that looks exactly like this one
     */
    public Board copy(){
        Board copyOfBoard = new Board();
        // Copy the array contents
        for(int i = 0; i < fields.length; i++) {
             copyOfBoard.fields[i] = this.fields[i]; // Just copying reference is enough since Pieces don't change
        }
        return copyOfBoard;
    }

    @Override
    public String toString() {
        return toString(null);
    }

    /**
     * Returns a string drawing of the board.
     * Can optionally show available pieces below the board.
     *
     * @param availablePieces List of pieces left to play (or null to hide)
     * @return The board as a string
     */
    public String toString(java.util.List<Piece> availablePieces) {
        StringBuilder sb = new StringBuilder();
        
        // Board Header
        sb.append("       0       1       2       3\n");
        String divider = "   +-------+-------+-------+-------+";
        sb.append(divider).append("\n");
        
        // Board rows
        for (int row = 0; row < BOARD_SIZE; row++) {
            sb.append(" ").append(row).append(" |");
            for (int col = 0; col < BOARD_SIZE; col++) {
                Piece p = getPiece(row * BOARD_SIZE + col);
                String pStr;
                if (p == null) {
                    int idx = row * BOARD_SIZE + col;
                    pStr = " " + idx + (idx < 10 ? " " : "") + "  ";
                } else {
                    pStr = p.toString();
                }
                sb.append(" ").append(pStr).append(" |");
            }
            sb.append("\n");
            sb.append(divider).append("\n");
        }

        // Available pieces section
        if (availablePieces != null && !availablePieces.isEmpty()) {
            sb.append("\n");
            sb.append("   ╔═══════════════════════════════════════════════════╗\n");
            sb.append("   ║            AVAILABLE PIECES (").append(String.format("%2d", availablePieces.size())).append(" left)             ║\n");
            sb.append("   ╠═══════════════════════════════════════════════════╣\n");
            
            // Display pieces in rows of 4
            for (int pieceRow = 0; pieceRow < 4; pieceRow++) {
                StringBuilder idLine = new StringBuilder("   ║  ");
                StringBuilder pieceLine = new StringBuilder("   ║  ");
                
                for (int i = 0; i < 4; i++) {
                    int pieceId = pieceRow * 4 + i;
                    Piece p = findPieceById(availablePieces, pieceId);
                    if (p != null) {
                        idLine.append(String.format("ID:%-2d ", pieceId));
                        pieceLine.append(p.toString()).append("  ");
                    } else {
                        idLine.append("  --  ");
                        pieceLine.append(" ---  ");
                    }
                    if (i < 3) {
                        idLine.append("│ ");
                        pieceLine.append("│ ");
                    }
                }
                idLine.append("     ║");
                pieceLine.append("     ║");
                
                sb.append(idLine).append("\n");
                sb.append(pieceLine).append("\n");
                if (pieceRow < 3) {
                    sb.append("   ╟───────────────────────────────────────────────────╢\n");
                }
            }
            sb.append("   ╚═══════════════════════════════════════════════════╝\n");
            
            // Legend
            sb.append("\n");
            sb.append("   Legend: Shape: () Round, [] Square │ Color: D Dark, L Light\n");
            sb.append("           Fill: * Solid, ░ Hollow   │ Height: ^ Tall, _ Short\n");
        }
        
        return sb.toString();
    }

    /**
     * Helper to find a piece in a list using its ID.
     */
    public Piece findPieceById(java.util.List<Piece> pieces, int id) {
        for (Piece p : pieces) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }
}
