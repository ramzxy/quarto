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
            throw new IllegalArgumentException("Index is between 0 - " + (fields.length-1));
        }
        else if(fields[index] == null) {
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
     * Checks if 4 pieces share at least one common attribute (all same value).
     * @param a Piece 1
     * @param b Piece 2
     * @param c Piece 3
     * @param d Piece 4
     * @return true if all pieces share at least one attribute
     */
    private boolean lineChecker(Piece a, Piece b, Piece c, Piece d) {
        return allSame(a.isDark, b.isDark, c.isDark, d.isDark)
            || allSame(a.isTall, b.isTall, c.isTall, d.isTall)
            || allSame(a.isRound, b.isRound, c.isRound, d.isRound)
            || allSame(a.isHollow, b.isHollow, c.isHollow, d.isHollow);
    }

    /**
     * Checks if all 4 boolean values are identical.
     * @return true if all values are the same (all true or all false)
     */
    private boolean allSame(boolean v1, boolean v2, boolean v3, boolean v4) {
        return v1 == v2 && v2 == v3 && v3 == v4;
    }

    /**
     * Checks if there is a winning line on the board.
     * @return true if there is a winning line, false if there isn't
     */
    public boolean hasWinningLine() {
        return hasWinningHorizontal() || hasWinningVertical() || hasWinningCross();
    }


    private boolean hasWinningHorizontal() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            Piece[] line = new Piece[BOARD_SIZE];
            boolean complete = true;
            
            for (int col = 0; col < BOARD_SIZE; col++) {
                line[col] = fields[row * BOARD_SIZE + col];
                if (line[col] == null) {
                    complete = false;
                    break;
                }
            }
            
            if (complete && lineChecker(line[0], line[1], line[2], line[3])) {
                return true;
            }
        }
        return false;
    }
    private boolean hasWinningVertical() {
        for (int col = 0; col < BOARD_SIZE; col++) {
            Piece[] line = new Piece[BOARD_SIZE];
            boolean complete = true;
            
            for (int row = 0; row < BOARD_SIZE; row++) {
                line[row] = fields[row * BOARD_SIZE + col];
                if (line[row] == null) {
                    complete = false;
                    break;
                }
            }
            
            if (complete && lineChecker(line[0], line[1], line[2], line[3])) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWinningCross() {
        // (0,0), (1,1), (2,2), (3,3)
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
        
        // (0,3), (1,2), (2,1), (3,0)
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
     * Checks if the board is full or not.
     * @return true if board is full with pieces, otherwise false
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
     * Copies the board with another pointer.
     * @return the copy of the board
     */
    public Board copy(){
        Board copyOfBoard = this;
        return copyOfBoard;
    }

    @Override
    public String toString() {
        return toString(null);
    }

    /**
     * Generates a string representation of the board with available pieces shown below.
     * @param availablePieces list of pieces still available, or null to hide
     * @return formatted board string
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
     * Helper to find a piece by ID in a list.
     */
    private Piece findPieceById(java.util.List<Piece> pieces, int id) {
        for (Piece p : pieces) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }
}
