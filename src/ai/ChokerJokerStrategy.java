package ai;

import java.util.List;
import java.util.Random;

import Game.Board;
import Game.Game;
import Game.Move;
import Game.Piece;
import Protocol.PROTOCOL;

/**
 * Advanced AI strategy implementing the "Constraint-Solver" Hybrid Engine.
 * 
 * Phase 1 (The Strangler): Heuristic-based play for early/mid game (EmptySquares > 9)
 *   - Maximizes opponent panic by minimizing their safe moves
 *   - Formula: Score = -(2.21 * Safety) - (0.37 * Traps)
 * 
 * Phase 2 (The God Engine): Perfect play for endgame (EmptySquares <= 9)
 *   - Alpha-Beta pruning to terminal states
 *   - Maximal symmetry reduction (32 board variants + piece isomorphism)
 *   - Parity/Zugzwang protection
 * 
 * Optimized for JIT compilation with zero-allocation primitives.
 */
public class ChokerJokerStrategy implements Strategy {
    
    // ==================== GLOBAL BIT CONSTANTS (Server Protocol) ====================
    // Bit 0 (1): COLOR (0=Light, 1=Dark)
    // Bit 1 (2): SIZE (0=Small, 1=Large/Tall)
    // Bit 2 (4): SHAPE (0=Round, 1=Square)
    // Bit 3 (8): FILL (0=Solid, 1=Hollow)
    
    private static final int BIT_DARK   = 0;  // Mask: 1
    private static final int BIT_TALL   = 1;  // Mask: 2
    private static final int BIT_SQUARE = 2;  // Mask: 4
    private static final int BIT_HOLLOW = 3;  // Mask: 8
    
    private static final int MASK_DARK   = 1 << BIT_DARK;    // 1
    private static final int MASK_TALL   = 1 << BIT_TALL;    // 2
    private static final int MASK_SQUARE = 1 << BIT_SQUARE;  // 4
    private static final int MASK_HOLLOW = 1 << BIT_HOLLOW;  // 8
    
    // ==================== PHASE THRESHOLDS ====================
    private static final int ENDGAME_THRESHOLD_MIN = 9;   // Minimum threshold
    private static final int ENDGAME_THRESHOLD_MAX = 11;  // Maximum threshold (with time)

    // ==================== SEARCH CONSTANTS ====================
    private static final int MAX_DEPTH = 32;
    private static final int LMR_THRESHOLD = 3;  // Apply LMR after this many moves
    private static final int LMR_DEPTH_THRESHOLD = 2;  // Only apply LMR at depth > this
    
    // ==================== STRANGLER WEIGHTS ====================
    private static final double W_SAFETY = 2.21;
    private static final double W_TRAPS = 0.37;
    
    // ==================== ZOBRIST HASHING ====================
    private static final long[][] Z_SQUARE_PIECE = new long[16][16]; // [square][pieceId]
    private static final long[] Z_NEXT_PIECE = new long[16];   // [pieceId]
    
    // ==================== D4 SPATIAL SYMMETRIES (8) ====================
    // Board layout:
    //  0   1   2   3
    //  4   5   6   7
    //  8   9  10  11
    // 12  13  14  15
    private static final int[][] D4_SYMMETRIES = {
        {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},     // Identity
        {12,8,4,0,13,9,5,1,14,10,6,2,15,11,7,3},     // Rotate 90 CW
        {15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0},     // Rotate 180
        {3,7,11,15,2,6,10,14,1,5,9,13,0,4,8,12},     // Rotate 270 CW
        {3,2,1,0,7,6,5,4,11,10,9,8,15,14,13,12},     // Horizontal flip
        {12,13,14,15,8,9,10,11,4,5,6,7,0,1,2,3},     // Vertical flip
        {0,4,8,12,1,5,9,13,2,6,10,14,3,7,11,15},     // Main diagonal flip
        {15,11,7,3,14,10,6,2,13,9,5,1,12,8,4,0}      // Anti-diagonal flip
    };
    
    // ==================== TOPOLOGICAL SYMMETRIES (4) ====================
    // These are non-standard transforms that preserve winning lines
    // Combined with D4: 8 * 4 = 32 total variants
    private static final int[][] TOPO_SYMMETRIES = {
        {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},     // Identity
        // Mid-Flip: Swap inner 2x2 (5,6,9,10) with corners (0,3,12,15)
        {5,1,2,6,4,0,3,7,8,12,15,11,9,13,14,10},
        // Inside-Out: Center (5,6,9,10) <-> Edge midpoints (1,4,7,11,8,14,13,2)
        {0,5,6,3,9,1,2,10,4,7,11,8,12,14,13,15},
        // Combined Mid-Flip + Inside-Out variant
        {5,9,10,6,1,0,3,2,4,12,15,7,8,11,14,13}
    };
    
    // Combined symmetries: 8 D4 * 4 Topo = 32 total
    private static final int[][] ALL_SYMMETRIES = new int[32][16];
    private static final int[][] INVERSE_SYMMETRIES = new int[32][16];
    
    // Opening optimization: unique orbit representatives
    private static final int[] OPENING_SQUARES = {0, 1, 5};
    
    // ==================== TRANSPOSITION TABLE ====================
    private static final int TT_SIZE = 1 << 22; // 4 Million entries
    private static final long[] TT_KEYS = new long[TT_SIZE];
    private static final long[] TT_VALUES = new long[TT_SIZE];  // Packed: score(16) | depth(8) | flag(8)
    private static final int[] TT_MOVES = new int[TT_SIZE];     // Packed: (sq << 8) | (nextP << 4) | sym
    
    private static final int TT_EXACT = 0;
    private static final int TT_ALPHA = 1;
    private static final int TT_BETA = 2;
    
    // ==================== WIN MASKS ====================
    private static final int[] WIN_MASKS = initializeWinMasks();
    
    // ==================== REUSABLE ARRAYS ====================
    private final long[] symHashes = new long[32];

    // ==================== KILLER & HISTORY HEURISTICS ====================
    // Killer moves: [depth][slot] -> packed move (sq << 4 | nextP)
    private final int[][] killerMoves = new int[MAX_DEPTH][2];
    // History table: [square][piece] -> score (higher = more cutoffs)
    private final int[][] historyTable = new int[16][16];
    // Move scoring for ordering
    private final int[] moveScores = new int[256];  // Reusable array for move ordering

    // ==================== TIME MANAGEMENT ====================
    private long searchStartTime;
    private long searchTimeLimit;
    
    // ==================== STATIC INITIALIZATION ====================
    static {
        initializeZobrist();
        initializeAllSymmetries();
    }
    
    private static void initializeZobrist() {
        Random rand = new Random(123456789L);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                Z_SQUARE_PIECE[i][j] = rand.nextLong();
            }
            Z_NEXT_PIECE[i] = rand.nextLong();
        }
    }
    
    private static void initializeAllSymmetries() {
        // Combine D4 and Topological symmetries
        int idx = 0;
        for (int d4 = 0; d4 < 8; d4++) {
            for (int topo = 0; topo < 4; topo++) {
                for (int sq = 0; sq < 16; sq++) {
                    // Apply topo first, then d4
                    int intermediate = TOPO_SYMMETRIES[topo][sq];
                    ALL_SYMMETRIES[idx][sq] = D4_SYMMETRIES[d4][intermediate];
                }
                idx++;
            }
        }
        
        // Compute inverse symmetries
        for (int sym = 0; sym < 32; sym++) {
            for (int i = 0; i < 16; i++) {
                INVERSE_SYMMETRIES[sym][ALL_SYMMETRIES[sym][i]] = i;
            }
        }
    }
    
    private static int[] initializeWinMasks() {
        int[] masks = new int[10];
        int idx = 0;
        
        // Rows
        for (int r = 0; r < 4; r++) {
            int m = 0;
            for (int c = 0; c < 4; c++) m |= (1 << (r * 4 + c));
            masks[idx++] = m;
        }
        
        // Columns
        for (int c = 0; c < 4; c++) {
            int m = 0;
            for (int r = 0; r < 4; r++) m |= (1 << (r * 4 + c));
            masks[idx++] = m;
        }
        
        // Diagonals
        masks[idx++] = (1 << 0) | (1 << 5) | (1 << 10) | (1 << 15);
        masks[idx++] = (1 << 3) | (1 << 6) | (1 << 9) | (1 << 12);
        
        return masks;
    }
    
    // ==================== PIECE ATTRIBUTE DECODING ====================
    // Unified bit decoding using server protocol constants
    
    private static boolean isDark(int pieceId) {
        return (pieceId & MASK_DARK) != 0;
    }
    
    private static boolean isTall(int pieceId) {
        return (pieceId & MASK_TALL) != 0;
    }
    
    private static boolean isSquare(int pieceId) {
        return (pieceId & MASK_SQUARE) != 0;
    }
    
    private static boolean isHollow(int pieceId) {
        return (pieceId & MASK_HOLLOW) != 0;
    }
    
    // Convenience: round = NOT square, solid = NOT hollow
    private static boolean isRound(int pieceId) {
        return !isSquare(pieceId);
    }
    
    private static boolean isSolid(int pieceId) {
        return !isHollow(pieceId);
    }
    
    // ==================== PIECE ISOMORPHISM ====================
    /**
     * Normalize piece representation based on attribute majority.
     * If more pieces have attribute X than NOT-X, invert that bit.
     * Returns a 4-bit inversion mask to XOR with piece IDs.
     */
    private int computeInversionMask(int dark, int tall, int round, int solid, int occupied) {
        int count = Integer.bitCount(occupied);
        if (count == 0) return 0;
        
        int mask = 0;
        
        // Count dark pieces vs light pieces
        int darkCount = Integer.bitCount(dark & occupied);
        if (darkCount > count / 2) {
            mask |= MASK_DARK;
        }
        
        // Count tall pieces vs short pieces
        int tallCount = Integer.bitCount(tall & occupied);
        if (tallCount > count / 2) {
            mask |= MASK_TALL;
        }
        
        // Count square pieces vs round pieces (remember: round bitboard stores round, not square)
        int roundCount = Integer.bitCount(round & occupied);
        if (roundCount <= count / 2) {  // More square than round
            mask |= MASK_SQUARE;
        }
        
        // Count solid pieces vs hollow pieces
        int solidCount = Integer.bitCount(solid & occupied);
        if (solidCount <= count / 2) {  // More hollow than solid
            mask |= MASK_HOLLOW;
        }
        
        return mask;
    }
    
    // ==================== CANONICAL HASH ====================
    /**
     * Compute canonical hash using all 32 symmetries + piece isomorphism.
     * Returns packed: (hash & ~0xF) | (bestSymmetry & 0x1F)
     */
    private long computeCanonicalHash(int tall, int round, int solid, int dark, int occupied, int pieceToPlace) {
        // Compute inversion mask for piece isomorphism
        int invMask = computeInversionMask(dark, tall, round, solid, occupied);
        
        long minHash = Long.MAX_VALUE;
        int minSym = 0;
        
        for (int sym = 0; sym < 32; sym++) {
            long h = 0;
            int[] symMap = ALL_SYMMETRIES[sym];
            
            for (int newIdx = 0; newIdx < 16; newIdx++) {
                int oldIdx = symMap[newIdx];
                if ((occupied & (1 << oldIdx)) != 0) {
                    // Reconstruct piece ID from bitboards
                    int pieceId = 0;
                    if ((dark & (1 << oldIdx)) != 0) pieceId |= MASK_DARK;
                    if ((tall & (1 << oldIdx)) != 0) pieceId |= MASK_TALL;
                    if ((round & (1 << oldIdx)) == 0) pieceId |= MASK_SQUARE;  // NOT round = square
                    if ((solid & (1 << oldIdx)) == 0) pieceId |= MASK_HOLLOW;  // NOT solid = hollow
                    
                    // Apply inversion mask for isomorphism
                    pieceId ^= invMask;
                    
                    h ^= Z_SQUARE_PIECE[newIdx][pieceId];
                }
            }
            
            if (pieceToPlace >= 0) {
                // Apply same inversion to piece to place
                h ^= Z_NEXT_PIECE[pieceToPlace ^ invMask];
            }
            
            symHashes[sym] = h;
            
            if (Long.compareUnsigned(h, minHash) < 0) {
                minHash = h;
                minSym = sym;
            }
        }
        
        // Pack hash with symmetry index (5 bits for 32 symmetries)
        return (minHash & 0xFFFFFFFFFFFFFFE0L) | (minSym & 0x1F);
    }
    
    // ==================== BOARD CONVERSION ====================
    private void toBitboard(Board b, int[] state) {
        // state[0]=tall, [1]=round, [2]=solid, [3]=dark, [4]=occupied
        state[0] = state[1] = state[2] = state[3] = state[4] = 0;
        
        for (int i = 0; i < 16; i++) {
            Piece p = b.getPiece(i);
            if (p != null) {
                int bit = 1 << i;
                state[4] |= bit;
                if (p.isTall) state[0] |= bit;
                if (p.isRound) state[1] |= bit;
                if (!p.isHollow) state[2] |= bit;
                if (p.isDark) state[3] |= bit;
            }
        }
    }
    
    // ==================== WIN CHECK ====================
    private boolean checkWin(int tall, int round, int solid, int dark, int occupied) {
        for (int mask : WIN_MASKS) {
            if ((occupied & mask) == mask) {
                // Check if all 4 pieces share any attribute
                // All tall or all short
                int tallLine = tall & mask;
                if (tallLine == mask || tallLine == 0) return true;
                
                // All round or all square
                int roundLine = round & mask;
                if (roundLine == mask || roundLine == 0) return true;
                
                // All solid or all hollow
                int solidLine = solid & mask;
                if (solidLine == mask || solidLine == 0) return true;
                
                // All dark or all light
                int darkLine = dark & mask;
                if (darkLine == mask || darkLine == 0) return true;
            }
        }
        return false;
    }
    
    // ==================== CAN WIN WITH PIECE ====================
    private boolean canWinWithPiece(int tall, int round, int solid, int dark, int occupied, int pieceId) {
        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);
        
        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) == 0) {
                int bit = 1 << sq;
                int nTall = pTall ? (tall | bit) : tall;
                int nRound = pRound ? (round | bit) : round;
                int nSolid = pSolid ? (solid | bit) : solid;
                int nDark = pDark ? (dark | bit) : dark;
                int nOcc = occupied | bit;
                
                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== BITWISE DANGER MASKS (O(1) Safety Check) ====================
    /**
     * Compute danger mask for a single attribute.
     * Returns a bitmask of squares where placing a piece with attrValue would complete a line.
     */
    private int computeDangerMaskForAttr(int attrBitboard, boolean attrValue, int occupied) {
        int danger = 0;
        for (int mask : WIN_MASKS) {
            int lineOcc = occupied & mask;
            // Need exactly 3 pieces on this line
            if (Integer.bitCount(lineOcc) != 3) continue;

            // Find the empty square on this line
            int emptyBit = mask & ~occupied;

            // Check if all 3 existing pieces share the attribute
            int lineAttr = attrBitboard & lineOcc;
            boolean allHaveAttr = (lineAttr == lineOcc);
            boolean noneHaveAttr = (lineAttr == 0);

            // Danger if: (all have attr AND piece has attr) OR (none have AND piece doesn't)
            if ((allHaveAttr && attrValue) || (noneHaveAttr && !attrValue)) {
                danger |= emptyBit;
            }
        }
        return danger;
    }

    /**
     * Get all danger squares for a specific piece.
     * Returns bitmask of squares where placing this piece would complete a winning line.
     */
    private int getDangerSquares(int tall, int round, int solid, int dark, int occupied, int pieceId) {
        int danger = 0;
        // Tall attribute
        danger |= computeDangerMaskForAttr(tall, isTall(pieceId), occupied);
        // Round attribute (round bitboard stores round, so check isRound)
        danger |= computeDangerMaskForAttr(round, isRound(pieceId), occupied);
        // Solid attribute
        danger |= computeDangerMaskForAttr(solid, isSolid(pieceId), occupied);
        // Dark attribute
        danger |= computeDangerMaskForAttr(dark, isDark(pieceId), occupied);
        return danger;
    }

    /**
     * Count safe squares for a piece using O(1) bitwise operations.
     */
    private int countSafeSquares(int tall, int round, int solid, int dark, int occupied, int pieceId) {
        int danger = getDangerSquares(tall, round, solid, dark, occupied, pieceId);
        int safe = ~danger & ~occupied & 0xFFFF;
        return Integer.bitCount(safe);
    }

    /**
     * Check if piece can win (has any danger square) - O(1) version.
     */
    private boolean canWinWithPieceFast(int tall, int round, int solid, int dark, int occupied, int pieceId) {
        int danger = getDangerSquares(tall, round, solid, dark, occupied, pieceId);
        return (danger & ~occupied) != 0;
    }

    // ==================== DYNAMIC ENDGAME THRESHOLD ====================
    /**
     * Calculate dynamic threshold based on elapsed time.
     * More time remaining = earlier switch to God Engine for perfect play.
     */
    private int getDynamicEndgameThreshold(long elapsedMs, long timeLimitMs) {
        long remaining = timeLimitMs - elapsedMs;
        if (remaining > 4000) return ENDGAME_THRESHOLD_MAX;      // 11 squares
        if (remaining > 2000) return ENDGAME_THRESHOLD_MAX - 1;  // 10 squares
        return ENDGAME_THRESHOLD_MIN;  // 9 squares (safe default)
    }

    // ==================== MOVE ORDERING HELPERS ====================
    /**
     * Update killer moves when a move causes a beta cutoff.
     */
    private void updateKillerMove(int depth, int sq, int nextP) {
        int packed = (sq << 4) | (nextP & 0xF);
        if (killerMoves[depth][0] != packed) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = packed;
        }
    }

    /**
     * Update history table when a move causes a cutoff.
     */
    private void updateHistory(int sq, int nextP, int depth) {
        if (sq >= 0 && sq < 16 && nextP >= 0 && nextP < 16) {
            historyTable[sq][nextP] += depth * depth;  // Deeper cutoffs worth more
            // Prevent overflow
            if (historyTable[sq][nextP] > 1000000) {
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        historyTable[i][j] /= 2;
                    }
                }
            }
        }
    }

    /**
     * Clear history and killers for a new search.
     */
    private void clearSearchTables() {
        for (int i = 0; i < MAX_DEPTH; i++) {
            killerMoves[i][0] = -1;
            killerMoves[i][1] = -1;
        }
        // History persists across searches (learned knowledge)
    }

    // ==================== MAIN ENTRY POINT ====================
    @Override
    public Move computeMove(Game game) {
        try {
            System.err.println("[ChokerJoker] computeMove called");
            System.err.println("[ChokerJoker] Current piece to place: " + (game.getCurrentPiece() != null ? game.getCurrentPiece().getId() : "null"));
            System.err.println("[ChokerJoker] Available pieces: " + game.getAvailablePieces().size());

            Move bestMove = determineMove(game);

        System.err.println("[ChokerJoker] determineMove returned: " + (bestMove != null ?
            "sq=" + bestMove.getBoardIndex() + ", piece=" + (bestMove.getPiece() != null ? bestMove.getPiece().getId() : "null") +
            ", nextPiece=" + (bestMove.getNextPiece() != null ? bestMove.getNextPiece().getId() : "null") : "null"));

        if (bestMove == null) return null;
        
        // Handle protocol signals
        if (bestMove.getBoardIndex() != -1) {
            Board boardCopy = game.getBoard().copy();
            boardCopy.setPiece(bestMove.getBoardIndex(), game.getCurrentPiece());

            if (boardCopy.hasWinningLine()) {
                System.err.println("[ChokerJoker] WINNING MOVE! Claiming Quarto with CLAIM_QUARTO=" + PROTOCOL.CLAIM_QUARTO);
                return new Move(bestMove.getBoardIndex(), game.getCurrentPiece(),
                    new Piece(PROTOCOL.CLAIM_QUARTO, false, false, false, false));
            }

            if (game.getAvailablePieces().isEmpty()) {
                System.err.println("[ChokerJoker] Last piece placed, no pieces left. Using FINAL_PIECE_NO_CLAIM=" + PROTOCOL.FINAL_PIECE_NO_CLAIM);
                return new Move(bestMove.getBoardIndex(), game.getCurrentPiece(),
                    new Piece(PROTOCOL.FINAL_PIECE_NO_CLAIM, false, false, false, false));
            }
        }

        System.err.println("[ChokerJoker] Final move: sq=" + bestMove.getBoardIndex() +
            ", piece=" + (bestMove.getPiece() != null ? bestMove.getPiece().getId() : "null") +
            ", nextPiece=" + (bestMove.getNextPiece() != null ? bestMove.getNextPiece().getId() : "null"));
        return bestMove;
        } catch (Exception e) {
            System.err.println("[ChokerJoker] EXCEPTION in computeMove: " + e.getMessage());
            e.printStackTrace(System.err);
            // Return a safe fallback
            List<Move> valid = game.getValidMoves();
            if (!valid.isEmpty()) {
                Move fallback = valid.get(0);
                List<Piece> avail = game.getAvailablePieces();
                Piece nextP = avail.isEmpty() ? null : avail.get(0);
                return new Move(fallback.getBoardIndex(), fallback.getPiece(), nextP);
            }
            return null;
        }
    }
    
    // ==================== PHASE SWITCHING ====================
    private Move determineMove(Game game) {
        Board b = game.getBoard();
        int[] state = new int[5];
        toBitboard(b, state);

        int tall = state[0];
        int round = state[1];
        int solid = state[2];
        int dark = state[3];
        int occupied = state[4];

        // Count empty squares
        int emptySquares = 16 - Integer.bitCount(occupied);
        System.err.println("[ChokerJoker] Empty squares: " + emptySquares + ", occupied mask: " + Integer.toBinaryString(occupied));

        // Encode available pieces
        int available = 0;
        List<Piece> availList = game.getAvailablePieces();
        for (Piece p : availList) {
            available |= (1 << p.getId());
        }

        Piece currentP = game.getCurrentPiece();
        int pieceToPlaceId = (currentP != null) ? currentP.getId() : -1;

        // First turn: just pick a piece
        if (pieceToPlaceId == -1) {
            return pickBestOpeningPiece(available, game);
        }

        // Initialize search state
        clearSearchTables();
        searchStartTime = System.currentTimeMillis();

        // PHASE SWITCH: Choose search strategy based on game phase
        // Use dynamic threshold based on time budget
        long searchResult;
        int dynamicThreshold = getDynamicEndgameThreshold(0, 5000);  // 5s total budget

        System.err.println("[ChokerJoker] Dynamic threshold: " + dynamicThreshold + ", pieceToPlace: " + pieceToPlaceId);

        if (emptySquares <= dynamicThreshold) {
            // Phase 2: God Engine - Perfect play
            System.err.println("[ChokerJoker] Using GOD ENGINE (endgame)");
            searchTimeLimit = 3000;  // 3 seconds for endgame
            searchResult = godEngineSearch(tall, round, solid, dark, occupied, available, pieceToPlaceId);
        } else {
            // Phase 1: Strangler - Heuristic play
            System.err.println("[ChokerJoker] Using STRANGLER (mid-game)");
            searchTimeLimit = 2000;  // 2 seconds for mid-game
            searchResult = stranglerSearch(tall, round, solid, dark, occupied, available, pieceToPlaceId);
        }
        System.err.println("[ChokerJoker] Search result raw: " + Long.toHexString(searchResult));
        
        // Unpack result
        int score = (int)(searchResult >> 32);
        int movePacked = (int)searchResult;
        int bestSq = (movePacked >> 16) & 0xFFFF;
        if (bestSq == 0xFFFF) bestSq = -1;
        int bestNextP = movePacked & 0xFFFF;
        if (bestNextP == 0xFFFF) bestNextP = -1;

        System.err.println("[ChokerJoker] Unpacked: score=" + score + ", bestSq=" + bestSq + ", bestNextP=" + bestNextP);
        System.err.println("[ChokerJoker] Available pieces mask: " + Integer.toBinaryString(available));

        if (bestSq != -1) {
            Piece pToPlace = game.getPieceById(pieceToPlaceId);
            if (pToPlace == null) {
                pToPlace = currentP;
            }
            Piece nextP = game.getPieceById(bestNextP);
            System.err.println("[ChokerJoker] nextP from getPieceById(" + bestNextP + "): " + (nextP != null ? nextP.getId() : "null"));
            if (nextP == null && !availList.isEmpty()) {
                nextP = availList.get(0);
                System.err.println("[ChokerJoker] nextP fallback to first available: " + (nextP != null ? nextP.getId() : "null"));
            }
            System.err.println("[ChokerJoker] Returning Move: sq=" + bestSq + ", piece=" + (pToPlace != null ? pToPlace.getId() : "null") + ", nextPiece=" + (nextP != null ? nextP.getId() : "null"));
            return new Move(bestSq, pToPlace, nextP);
        }
        
        // Fallback
        System.err.println("[ChokerJoker] WARNING: bestSq == -1, using fallback!");
        List<Move> valid = game.getValidMoves();
        if (valid.isEmpty()) {
            System.err.println("[ChokerJoker] ERROR: No valid moves available!");
            return null;
        }
        Move fallbackMove = valid.get(0);
        Piece fallbackNextPiece = availList.isEmpty() ? null : availList.get(0);
        System.err.println("[ChokerJoker] Fallback move: sq=" + fallbackMove.getBoardIndex() + ", nextPiece=" + (fallbackNextPiece != null ? fallbackNextPiece.getId() : "null"));
        return new Move(fallbackMove.getBoardIndex(), fallbackMove.getPiece(), fallbackNextPiece);
    }
    
    // ==================== PHASE 2: GOD ENGINE ====================
    /**
     * Perfect play via Alpha-Beta with PVS to terminal states.
     * Used when EmptySquares <= dynamic threshold.
     * Optimizations: PVS, LMR, Killer/History move ordering.
     */
    private long godEngineSearch(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int emptySquares = 16 - Integer.bitCount(occupied);
        int maxDepth = emptySquares * 2 + 2;  // Search to end

        System.err.println("[GodEngine] Starting search: emptySquares=" + emptySquares + ", maxDepth=" + maxDepth + ", pieceId=" + pieceId);
        System.err.println("[GodEngine] Available pieces: " + Integer.toBinaryString(available) + " (" + Integer.bitCount(available) + " pieces)");

        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;

        int alpha = -1000000;
        int beta = 1000000;

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        // Generate and order moves
        int moveCount = 0;
        int[] moveSqs = new int[256];
        int[] moveNextPs = new int[256];
        int[] movePriorities = new int[256];

        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) == 0) {
                int bit = 1 << sq;
                int nTall = pTall ? (tall | bit) : tall;
                int nRound = pRound ? (round | bit) : round;
                int nSolid = pSolid ? (solid | bit) : solid;
                int nDark = pDark ? (dark | bit) : dark;
                int nOcc = occupied | bit;

                // Check immediate win first
                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return pack(10000, sq, -1);
                }

                if (available == 0) {
                    if (0 > bestScore) {
                        bestScore = 0;
                        bestSq = sq;
                        bestNextP = -1;
                    }
                    continue;
                }

                // Generate piece choices with priorities
                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        // Prune: never give piece that immediately wins for opponent
                        if (canWinWithPieceFast(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                            continue;
                        }

                        // Calculate move priority for ordering
                        int priority = historyTable[sq][nextP];

                        // Boost killer moves
                        int packed = (sq << 4) | nextP;
                        if (killerMoves[maxDepth][0] == packed) priority += 50000;
                        else if (killerMoves[maxDepth][1] == packed) priority += 40000;

                        moveSqs[moveCount] = sq;
                        moveNextPs[moveCount] = nextP;
                        movePriorities[moveCount] = priority;
                        moveCount++;
                    }
                }
            }
        }

        // Sort moves by priority (simple insertion sort for small arrays)
        for (int i = 1; i < moveCount; i++) {
            int j = i;
            while (j > 0 && movePriorities[j] > movePriorities[j - 1]) {
                // Swap
                int tmpSq = moveSqs[j]; moveSqs[j] = moveSqs[j-1]; moveSqs[j-1] = tmpSq;
                int tmpP = moveNextPs[j]; moveNextPs[j] = moveNextPs[j-1]; moveNextPs[j-1] = tmpP;
                int tmpPri = movePriorities[j]; movePriorities[j] = movePriorities[j-1]; movePriorities[j-1] = tmpPri;
                j--;
            }
        }

        System.err.println("[GodEngine] Generated " + moveCount + " moves to search");

        // PVS: Search first move with full window, rest with null window
        boolean firstMove = true;
        for (int i = 0; i < moveCount; i++) {
            if (System.currentTimeMillis() - searchStartTime > searchTimeLimit) break;

            int sq = moveSqs[i];
            int nextP = moveNextPs[i];
            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            int val;
            if (firstMove) {
                // Full window search for first move
                val = -godEngineNegamax(maxDepth - 1, -beta, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << nextP), nextP, maxDepth - 1);
                firstMove = false;
            } else {
                // Null window search (PVS)
                val = -godEngineNegamax(maxDepth - 1, -alpha - 1, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << nextP), nextP, maxDepth - 1);

                // Re-search with full window if it beats alpha
                if (val > alpha && val < beta) {
                    val = -godEngineNegamax(maxDepth - 1, -beta, -alpha,
                        nTall, nRound, nSolid, nDark, nOcc,
                        available & ~(1 << nextP), nextP, maxDepth - 1);
                }
            }

            // Parity protection: penalize even parity draws
            int newEmpty = 16 - Integer.bitCount(nOcc);
            if (val == 0 && (newEmpty % 2) == 0) {
                val -= 10;  // Small penalty for even parity
            }

            if (val > bestScore) {
                bestScore = val;
                bestSq = sq;
                bestNextP = nextP;
            }
            if (bestScore > alpha) {
                alpha = bestScore;
                if (alpha >= beta) {
                    updateKillerMove(maxDepth, sq, nextP);
                    updateHistory(sq, nextP, maxDepth);
                    break;
                }
            }
        }

        // If no safe piece found, search poison pieces as fallback
        if (bestNextP == -1 && bestSq == -1) {
            System.err.println("[GodEngine] WARNING: No safe move found, trying poison fallback");
            for (int sq = 0; sq < 16; sq++) {
                if ((occupied & (1 << sq)) == 0) {
                    int bit = 1 << sq;
                    int nTall = pTall ? (tall | bit) : tall;
                    int nRound = pRound ? (round | bit) : round;
                    int nSolid = pSolid ? (solid | bit) : solid;
                    int nDark = pDark ? (dark | bit) : dark;
                    int nOcc = occupied | bit;

                    for (int nextP = 0; nextP < 16; nextP++) {
                        if ((available & (1 << nextP)) != 0) {
                            int val = -godEngineNegamax(maxDepth - 1, -beta, -alpha,
                                nTall, nRound, nSolid, nDark, nOcc,
                                available & ~(1 << nextP), nextP, maxDepth - 1);

                            if (val > bestScore) {
                                bestScore = val;
                                bestSq = sq;
                                bestNextP = nextP;
                            }
                        }
                    }
                    break;  // Just need one fallback
                }
            }
        }

        // Emergency fallback: if we still have no valid move, pick anything
        if (bestSq == -1) {
            System.err.println("[GodEngine] EMERGENCY: No move found, picking first available");
            for (int sq = 0; sq < 16; sq++) {
                if ((occupied & (1 << sq)) == 0) {
                    bestSq = sq;
                    // Pick first available piece or -1 if none
                    for (int p = 0; p < 16; p++) {
                        if ((available & (1 << p)) != 0) {
                            bestNextP = p;
                            break;
                        }
                    }
                    break;
                }
            }
        }

        System.err.println("[GodEngine] Search complete: bestScore=" + bestScore + ", bestSq=" + bestSq + ", bestNextP=" + bestNextP);
        return pack(bestScore, bestSq, bestNextP);
    }
    
    private int godEngineNegamax(int depth, int alpha, int beta,
            int tall, int round, int solid, int dark, int occupied,
            int available, int pieceId, int ply) {

        // Safety check for negative depth
        if (depth < 0) {
            System.err.println("[GodNegamax] WARNING: negative depth=" + depth + ", returning 0");
            return 0;
        }

        // Time check
        if (System.currentTimeMillis() - searchStartTime > searchTimeLimit) {
            return 0;
        }

        int alphaOrig = alpha;

        // Transposition table lookup with canonical hash
        long canonicalPacked = computeCanonicalHash(tall, round, solid, dark, occupied, pieceId);
        long canonicalHash = canonicalPacked & 0xFFFFFFFFFFFFFFE0L;
        int canonicalSym = (int)(canonicalPacked & 0x1F);

        int ttIndex = (int)((canonicalHash >>> 5) & (TT_SIZE - 1));

        int ttBestSq = -1;
        int ttBestNextP = -1;

        if (TT_KEYS[ttIndex] == canonicalHash) {
            long ttEntry = TT_VALUES[ttIndex];
            int ttDepth = (int)((ttEntry >> 8) & 0xFF);
            int ttFlag = (int)(ttEntry & 0xFF);
            int ttScore = (int)(ttEntry >> 16);

            if (ttDepth >= depth) {
                if (ttFlag == TT_EXACT) return ttScore;
                if (ttFlag == TT_ALPHA && ttScore <= alpha) return alpha;
                if (ttFlag == TT_BETA && ttScore >= beta) return beta;
            }

            // Move ordering from TT
            int ttMove = TT_MOVES[ttIndex];
            int storedSq = (ttMove >> 8) & 0xFF;
            int storedNextP = (ttMove >> 4) & 0xF;
            if (storedSq < 16) {
                ttBestSq = INVERSE_SYMMETRIES[canonicalSym][storedSq];
                ttBestNextP = storedNextP;
            }
        }

        // Terminal check
        if (depth == 0 || available == 0) {
            return 0;
        }

        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        // Generate and order moves
        int moveCount = 0;
        int[] moveSqs = new int[256];
        int[] moveNextPs = new int[256];
        int[] movePriorities = new int[256];

        // Try TT move first (highest priority)
        if (ttBestSq >= 0 && (occupied & (1 << ttBestSq)) == 0) {
            int sq = ttBestSq;
            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                return 10000 + depth;
            }

            if (ttBestNextP >= 0 && (available & (1 << ttBestNextP)) != 0) {
                moveSqs[moveCount] = sq;
                moveNextPs[moveCount] = ttBestNextP;
                movePriorities[moveCount] = 1000000;  // TT move highest priority
                moveCount++;
            }
        }

        // Generate remaining moves with priorities
        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) == 0) {
                int bit = 1 << sq;
                int nTall = pTall ? (tall | bit) : tall;
                int nRound = pRound ? (round | bit) : round;
                int nSolid = pSolid ? (solid | bit) : solid;
                int nDark = pDark ? (dark | bit) : dark;
                int nOcc = occupied | bit;

                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return 10000 + depth;
                }

                if (available == 0) {
                    if (0 > bestScore) {
                        bestScore = 0;
                        bestSq = sq;
                        bestNextP = -1;
                    }
                    continue;
                }

                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        // Skip if already added as TT move
                        if (sq == ttBestSq && nextP == ttBestNextP) continue;

                        int priority = historyTable[sq][nextP];

                        // Killer move bonus
                        int packed = (sq << 4) | nextP;
                        if (ply < MAX_DEPTH) {
                            if (killerMoves[ply][0] == packed) priority += 50000;
                            else if (killerMoves[ply][1] == packed) priority += 40000;
                        }

                        moveSqs[moveCount] = sq;
                        moveNextPs[moveCount] = nextP;
                        movePriorities[moveCount] = priority;
                        moveCount++;
                    }
                }
            }
        }

        // Sort moves by priority
        for (int i = 1; i < moveCount; i++) {
            int j = i;
            while (j > 0 && movePriorities[j] > movePriorities[j - 1]) {
                int tmpSq = moveSqs[j]; moveSqs[j] = moveSqs[j-1]; moveSqs[j-1] = tmpSq;
                int tmpP = moveNextPs[j]; moveNextPs[j] = moveNextPs[j-1]; moveNextPs[j-1] = tmpP;
                int tmpPri = movePriorities[j]; movePriorities[j] = movePriorities[j-1]; movePriorities[j-1] = tmpPri;
                j--;
            }
        }

        // PVS + LMR search
        boolean firstMove = true;
        for (int i = 0; i < moveCount && alpha < beta; i++) {
            int sq = moveSqs[i];
            int nextP = moveNextPs[i];
            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            int val;
            int newDepth = depth - 1;

            // LMR: Reduce depth for late moves
            int reduction = 0;
            if (!firstMove && i >= LMR_THRESHOLD && depth > LMR_DEPTH_THRESHOLD) {
                reduction = 1;
            }

            if (firstMove) {
                // Full window search for first move
                val = -godEngineNegamax(newDepth, -beta, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << nextP), nextP, ply + 1);
                firstMove = false;
            } else {
                // PVS with potential LMR
                val = -godEngineNegamax(newDepth - reduction, -alpha - 1, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << nextP), nextP, ply + 1);

                // Re-search if LMR failed high
                if (reduction > 0 && val > alpha) {
                    val = -godEngineNegamax(newDepth, -alpha - 1, -alpha,
                        nTall, nRound, nSolid, nDark, nOcc,
                        available & ~(1 << nextP), nextP, ply + 1);
                }

                // Re-search with full window if PVS failed high
                if (val > alpha && val < beta) {
                    val = -godEngineNegamax(newDepth, -beta, -alpha,
                        nTall, nRound, nSolid, nDark, nOcc,
                        available & ~(1 << nextP), nextP, ply + 1);
                }
            }

            if (val > bestScore) {
                bestScore = val;
                bestSq = sq;
                bestNextP = nextP;
            }
            if (bestScore > alpha) {
                alpha = bestScore;
                if (alpha >= beta) {
                    // Update killer and history on cutoff
                    if (ply < MAX_DEPTH) {
                        updateKillerMove(ply, sq, nextP);
                    }
                    updateHistory(sq, nextP, depth);
                    break;
                }
            }
        }

        // Store in TT
        int ttFlag;
        if (bestScore <= alphaOrig) {
            ttFlag = TT_ALPHA;
        } else if (bestScore >= beta) {
            ttFlag = TT_BETA;
        } else {
            ttFlag = TT_EXACT;
        }

        TT_KEYS[ttIndex] = canonicalHash;
        TT_VALUES[ttIndex] = ((long)bestScore << 16) | ((long)depth << 8) | ttFlag;
        int canonicalSq = (bestSq >= 0) ? ALL_SYMMETRIES[canonicalSym][bestSq] : -1;
        TT_MOVES[ttIndex] = ((canonicalSq & 0xFF) << 8) | ((bestNextP & 0xF) << 4) | (canonicalSym & 0x1F);

        return bestScore;
    }
    
    // ==================== PHASE 1: THE STRANGLER ====================
    /**
     * Heuristic play via "Panic Maximization" with iterative deepening.
     * Score = -(2.21 * Safety) - (0.37 * Traps)
     * Optimizations: PVS, LMR, Aspiration Windows, Killer/History.
     */
    private long stranglerSearch(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int maxDepth = 5;  // Iterative deepening to depth 5 (faster with optimizations)

        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;

        boolean isOpening = (occupied == 0);

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        int previousScore = 0;  // For aspiration windows

        // Iterative deepening with aspiration windows
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() - searchStartTime > searchTimeLimit) break;

            int iterBestScore = -1000000;
            int iterBestSq = -1;
            int iterBestNextP = -1;

            // Aspiration window (except first iteration)
            int alpha, beta;
            int delta = 50;
            if (depth > 1) {
                alpha = previousScore - delta;
                beta = previousScore + delta;
            } else {
                alpha = -1000000;
                beta = 1000000;
            }

            boolean researching = false;
            do {
                researching = false;
                iterBestScore = -1000000;

                // Generate and order moves
                int moveCount = 0;
                int[] moveSqs = new int[256];
                int[] moveNextPs = new int[256];
                int[] movePriorities = new int[256];

                int numSquares = isOpening ? OPENING_SQUARES.length : 16;

                for (int sqIdx = 0; sqIdx < numSquares; sqIdx++) {
                    int sq = isOpening ? OPENING_SQUARES[sqIdx] : sqIdx;

                    if ((occupied & (1 << sq)) == 0) {
                        int bit = 1 << sq;
                        int nTall = pTall ? (tall | bit) : tall;
                        int nRound = pRound ? (round | bit) : round;
                        int nSolid = pSolid ? (solid | bit) : solid;
                        int nDark = pDark ? (dark | bit) : dark;
                        int nOcc = occupied | bit;

                        // Check immediate win
                        if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                            return pack(10000, sq, -1);
                        }

                        if (available == 0) {
                            if (0 > iterBestScore) {
                                iterBestScore = 0;
                                iterBestSq = sq;
                                iterBestNextP = -1;
                            }
                            continue;
                        }

                        for (int nextP = 0; nextP < 16; nextP++) {
                            if ((available & (1 << nextP)) != 0) {
                                // Immediate pruning: don't give winning piece
                                if (canWinWithPieceFast(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                                    continue;
                                }

                                int priority = historyTable[sq][nextP];
                                int packed = (sq << 4) | nextP;
                                if (killerMoves[depth][0] == packed) priority += 50000;
                                else if (killerMoves[depth][1] == packed) priority += 40000;

                                moveSqs[moveCount] = sq;
                                moveNextPs[moveCount] = nextP;
                                movePriorities[moveCount] = priority;
                                moveCount++;
                            }
                        }
                    }
                }

                // Sort moves by priority
                for (int i = 1; i < moveCount; i++) {
                    int j = i;
                    while (j > 0 && movePriorities[j] > movePriorities[j - 1]) {
                        int tmpSq = moveSqs[j]; moveSqs[j] = moveSqs[j-1]; moveSqs[j-1] = tmpSq;
                        int tmpP = moveNextPs[j]; moveNextPs[j] = moveNextPs[j-1]; moveNextPs[j-1] = tmpP;
                        int tmpPri = movePriorities[j]; movePriorities[j] = movePriorities[j-1]; movePriorities[j-1] = tmpPri;
                        j--;
                    }
                }

                // PVS search
                boolean firstMove = true;
                for (int i = 0; i < moveCount; i++) {
                    if (System.currentTimeMillis() - searchStartTime > searchTimeLimit) break;

                    int sq = moveSqs[i];
                    int nextP = moveNextPs[i];
                    int bit = 1 << sq;
                    int nTall = pTall ? (tall | bit) : tall;
                    int nRound = pRound ? (round | bit) : round;
                    int nSolid = pSolid ? (solid | bit) : solid;
                    int nDark = pDark ? (dark | bit) : dark;
                    int nOcc = occupied | bit;

                    int val;
                    int newDepth = depth - 1;

                    // LMR for late moves
                    int reduction = 0;
                    if (!firstMove && i >= LMR_THRESHOLD && depth > LMR_DEPTH_THRESHOLD) {
                        reduction = 1;
                    }

                    if (firstMove) {
                        val = -stranglerNegamax(newDepth, -beta, -alpha,
                            nTall, nRound, nSolid, nDark, nOcc,
                            available & ~(1 << nextP), nextP, depth);
                        firstMove = false;
                    } else {
                        // PVS with LMR
                        val = -stranglerNegamax(newDepth - reduction, -alpha - 1, -alpha,
                            nTall, nRound, nSolid, nDark, nOcc,
                            available & ~(1 << nextP), nextP, depth);

                        // Re-search if LMR failed high
                        if (reduction > 0 && val > alpha) {
                            val = -stranglerNegamax(newDepth, -alpha - 1, -alpha,
                                nTall, nRound, nSolid, nDark, nOcc,
                                available & ~(1 << nextP), nextP, depth);
                        }

                        // Re-search with full window
                        if (val > alpha && val < beta) {
                            val = -stranglerNegamax(newDepth, -beta, -alpha,
                                nTall, nRound, nSolid, nDark, nOcc,
                                available & ~(1 << nextP), nextP, depth);
                        }
                    }

                    if (val > iterBestScore) {
                        iterBestScore = val;
                        iterBestSq = sq;
                        iterBestNextP = nextP;
                    }
                    if (iterBestScore > alpha) {
                        alpha = iterBestScore;
                        if (alpha >= beta) {
                            updateKillerMove(depth, sq, nextP);
                            updateHistory(sq, nextP, depth);
                            break;
                        }
                    }
                }

                // Aspiration window fail - widen and re-search
                if (iterBestScore <= previousScore - delta || iterBestScore >= previousScore + delta) {
                    if (depth > 1 && (iterBestScore <= -999000 || iterBestScore >= 999000)) {
                        // Don't re-search on extreme scores
                    } else if (depth > 1) {
                        alpha = -1000000;
                        beta = 1000000;
                        researching = true;
                    }
                }
            } while (researching);

            // Update best from this iteration
            if (iterBestSq != -1) {
                bestScore = iterBestScore;
                bestSq = iterBestSq;
                bestNextP = iterBestNextP;
                previousScore = iterBestScore;
            }

            // Early termination on decisive score
            if (bestScore > 9000 || bestScore < -9000) break;
        }

        // Fallback: if all pieces are poison
        if (bestNextP == -1) {
            for (int sq = 0; sq < 16; sq++) {
                if ((occupied & (1 << sq)) == 0) {
                    for (int nextP = 0; nextP < 16; nextP++) {
                        if ((available & (1 << nextP)) != 0) {
                            bestSq = sq;
                            bestNextP = nextP;
                            break;
                        }
                    }
                    break;
                }
            }
        }

        return pack(bestScore, bestSq, bestNextP);
    }
    
    private int stranglerNegamax(int depth, int alpha, int beta,
            int tall, int round, int solid, int dark, int occupied,
            int available, int pieceId, int ply) {

        if (System.currentTimeMillis() - searchStartTime > searchTimeLimit) {
            return 0;
        }

        int alphaOrig = alpha;

        // TT lookup
        long canonicalPacked = computeCanonicalHash(tall, round, solid, dark, occupied, pieceId);
        long canonicalHash = canonicalPacked & 0xFFFFFFFFFFFFFFE0L;
        int canonicalSym = (int)(canonicalPacked & 0x1F);

        int ttIndex = (int)((canonicalHash >>> 5) & (TT_SIZE - 1));

        int ttBestSq = -1;
        int ttBestNextP = -1;

        if (TT_KEYS[ttIndex] == canonicalHash) {
            long ttEntry = TT_VALUES[ttIndex];
            int ttDepth = (int)((ttEntry >> 8) & 0xFF);
            int ttFlag = (int)(ttEntry & 0xFF);
            int ttScore = (int)(ttEntry >> 16);

            if (ttDepth >= depth) {
                if (ttFlag == TT_EXACT) return ttScore;
                if (ttFlag == TT_ALPHA && ttScore <= alpha) return alpha;
                if (ttFlag == TT_BETA && ttScore >= beta) return beta;
            }

            int ttMove = TT_MOVES[ttIndex];
            int storedSq = (ttMove >> 8) & 0xFF;
            int storedNextP = (ttMove >> 4) & 0xF;
            if (storedSq < 16) {
                ttBestSq = INVERSE_SYMMETRIES[canonicalSym][storedSq];
                ttBestNextP = storedNextP;
            }
        }

        // At depth 0, use Strangler heuristic (now optimized with danger masks)
        if (depth == 0) {
            return evaluateStranglerFast(tall, round, solid, dark, occupied, available, pieceId);
        }

        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        // Generate and order moves
        int moveCount = 0;
        int[] moveSqs = new int[256];
        int[] moveNextPs = new int[256];
        int[] movePriorities = new int[256];

        // TT move first (highest priority)
        if (ttBestSq >= 0 && (occupied & (1 << ttBestSq)) == 0) {
            int sq = ttBestSq;
            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                return 10000 + depth;
            }

            if (ttBestNextP >= 0 && (available & (1 << ttBestNextP)) != 0) {
                moveSqs[moveCount] = sq;
                moveNextPs[moveCount] = ttBestNextP;
                movePriorities[moveCount] = 1000000;
                moveCount++;
            }
        }

        // Generate remaining moves
        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) == 0) {
                int bit = 1 << sq;
                int nTall = pTall ? (tall | bit) : tall;
                int nRound = pRound ? (round | bit) : round;
                int nSolid = pSolid ? (solid | bit) : solid;
                int nDark = pDark ? (dark | bit) : dark;
                int nOcc = occupied | bit;

                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return 10000 + depth;
                }

                if (available == 0) {
                    if (0 > bestScore) {
                        bestScore = 0;
                        bestSq = sq;
                        bestNextP = -1;
                    }
                    continue;
                }

                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        if (sq == ttBestSq && nextP == ttBestNextP) continue;

                        int priority = historyTable[sq][nextP];
                        int packed = (sq << 4) | nextP;
                        if (ply < MAX_DEPTH) {
                            if (killerMoves[ply][0] == packed) priority += 50000;
                            else if (killerMoves[ply][1] == packed) priority += 40000;
                        }

                        moveSqs[moveCount] = sq;
                        moveNextPs[moveCount] = nextP;
                        movePriorities[moveCount] = priority;
                        moveCount++;
                    }
                }
            }
        }

        // Sort moves by priority
        for (int i = 1; i < moveCount; i++) {
            int j = i;
            while (j > 0 && movePriorities[j] > movePriorities[j - 1]) {
                int tmpSq = moveSqs[j]; moveSqs[j] = moveSqs[j-1]; moveSqs[j-1] = tmpSq;
                int tmpP = moveNextPs[j]; moveNextPs[j] = moveNextPs[j-1]; moveNextPs[j-1] = tmpP;
                int tmpPri = movePriorities[j]; movePriorities[j] = movePriorities[j-1]; movePriorities[j-1] = tmpPri;
                j--;
            }
        }

        // PVS + LMR search
        boolean firstMove = true;
        for (int i = 0; i < moveCount && alpha < beta; i++) {
            int sq = moveSqs[i];
            int nextP = moveNextPs[i];
            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            int val;
            int newDepth = depth - 1;

            // LMR for late moves
            int reduction = 0;
            if (!firstMove && i >= LMR_THRESHOLD && depth > LMR_DEPTH_THRESHOLD) {
                reduction = 1;
            }

            if (firstMove) {
                val = -stranglerNegamax(newDepth, -beta, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << nextP), nextP, ply + 1);
                firstMove = false;
            } else {
                // PVS with LMR
                val = -stranglerNegamax(newDepth - reduction, -alpha - 1, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << nextP), nextP, ply + 1);

                if (reduction > 0 && val > alpha) {
                    val = -stranglerNegamax(newDepth, -alpha - 1, -alpha,
                        nTall, nRound, nSolid, nDark, nOcc,
                        available & ~(1 << nextP), nextP, ply + 1);
                }

                if (val > alpha && val < beta) {
                    val = -stranglerNegamax(newDepth, -beta, -alpha,
                        nTall, nRound, nSolid, nDark, nOcc,
                        available & ~(1 << nextP), nextP, ply + 1);
                }
            }

            if (val > bestScore) {
                bestScore = val;
                bestSq = sq;
                bestNextP = nextP;
            }
            if (bestScore > alpha) {
                alpha = bestScore;
                if (alpha >= beta) {
                    if (ply < MAX_DEPTH) {
                        updateKillerMove(ply, sq, nextP);
                    }
                    updateHistory(sq, nextP, depth);
                    break;
                }
            }
        }

        // Store in TT
        int ttFlag;
        if (bestScore <= alphaOrig) {
            ttFlag = TT_ALPHA;
        } else if (bestScore >= beta) {
            ttFlag = TT_BETA;
        } else {
            ttFlag = TT_EXACT;
        }

        TT_KEYS[ttIndex] = canonicalHash;
        TT_VALUES[ttIndex] = ((long)bestScore << 16) | ((long)depth << 8) | ttFlag;
        int canonicalSq = (bestSq >= 0) ? ALL_SYMMETRIES[canonicalSym][bestSq] : -1;
        TT_MOVES[ttIndex] = ((canonicalSq & 0xFF) << 8) | ((bestNextP & 0xF) << 4) | (canonicalSym & 0x1F);

        return bestScore;
    }
    
    // ==================== STRANGLER EVALUATION ====================
    /**
     * FAST evaluation using bitwise danger masks.
     * Score = -(2.21 * S) - (0.37 * T)
     * S = Safety: safe (sq, piece) pairs for opponent
     * T = Traps: moves that appear safe but lead to forced loss
     *
     * Optimized from O(Empty² × Pieces) to O(Pieces × Lines).
     */
    private int evaluateStranglerFast(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        // First check if we (as opponent placing pieceId) can win
        int ourDanger = getDangerSquares(tall, round, solid, dark, occupied, pieceId);
        int ourWinSquares = ourDanger & ~occupied & 0xFFFF;
        if (ourWinSquares != 0) {
            return 10000;  // Opponent can win immediately
        }

        if (available == 0) {
            return 0;  // Draw
        }

        int safety = 0;
        int traps = 0;

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        // For each empty square where opponent can place
        int emptySquares = ~occupied & 0xFFFF;
        for (int sq = 0; sq < 16; sq++) {
            if ((emptySquares & (1 << sq)) == 0) continue;

            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            // For each piece opponent could give us
            for (int nextP = 0; nextP < 16; nextP++) {
                if ((available & (1 << nextP)) == 0) continue;

                // Use fast danger mask check instead of canWinWithPiece
                if (canWinWithPieceFast(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                    continue;  // This piece would let us win - opponent won't give it
                }

                // This is a "safe" move for opponent
                safety++;

                // Check if it's a trap (simplified: all our responses win)
                if (isTrapFast(nTall, nRound, nSolid, nDark, nOcc, available & ~(1 << nextP), nextP)) {
                    traps++;
                }
            }
        }

        // Strangler formula: minimize opponent's options
        return (int)(-(W_SAFETY * safety) - (W_TRAPS * traps));
    }

    /**
     * Fast trap detection using danger masks.
     * Returns true if ALL opponent's responses lead to giving us a winning piece.
     */
    private boolean isTrapFast(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        // Can opponent place the piece and win?
        int theirDanger = getDangerSquares(tall, round, solid, dark, occupied, pieceId);
        if ((theirDanger & ~occupied & 0xFFFF) != 0) {
            return false;  // Opponent has a winning placement - not a trap
        }

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        // For each square opponent could place
        int emptySquares = ~occupied & 0xFFFF;
        for (int sq = 0; sq < 16; sq++) {
            if ((emptySquares & (1 << sq)) == 0) continue;

            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            // Check if opponent can give us any safe piece
            for (int nextP = 0; nextP < 16; nextP++) {
                if ((available & (1 << nextP)) == 0) continue;

                // If this piece doesn't let us win, opponent has a safe out
                if (!canWinWithPieceFast(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                    return false;  // Opponent has a safe continuation
                }
            }
        }

        // All of opponent's responses lead to giving us a winning piece
        return true;
    }

    /**
     * Original evaluation (kept for reference/fallback).
     */
    private int evaluateStrangler(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int safety = 0;
        int traps = 0;

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) == 0) {
                int bit = 1 << sq;
                int nTall = pTall ? (tall | bit) : tall;
                int nRound = pRound ? (round | bit) : round;
                int nSolid = pSolid ? (solid | bit) : solid;
                int nDark = pDark ? (dark | bit) : dark;
                int nOcc = occupied | bit;

                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return 10000;
                }

                if (available == 0) continue;

                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        if (canWinWithPiece(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                            continue;
                        }
                        safety++;
                        if (isTrap(nTall, nRound, nSolid, nDark, nOcc, available & ~(1 << nextP), nextP)) {
                            traps++;
                        }
                    }
                }
            }
        }

        return (int)(-(W_SAFETY * safety) - (W_TRAPS * traps));
    }
    
    /**
     * Check if a position is a "trap" - opponent appears to have a safe move
     * but actually leads to forced loss at depth 2.
     */
    private boolean isTrap(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);
        
        // For each square opponent could place the piece
        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) == 0) {
                int bit = 1 << sq;
                int nTall = pTall ? (tall | bit) : tall;
                int nRound = pRound ? (round | bit) : round;
                int nSolid = pSolid ? (solid | bit) : solid;
                int nDark = pDark ? (dark | bit) : dark;
                int nOcc = occupied | bit;
                
                // If opponent wins here, not a trap
                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return false;  // Opponent has at least one good option
                }
                
                // Check if opponent can give us any piece safely
                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        if (!canWinWithPiece(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                            return false;  // Opponent has a safe continuation
                        }
                    }
                }
            }
        }
        
        // All of opponent's options lead to giving us a winning piece
        return true;
    }
    
    // ==================== OPENING PIECE SELECTION ====================
    private Move pickBestOpeningPiece(int available, Game game) {
        // In opening, prefer pieces that have mixed attributes
        // to make it harder for opponent to create patterns
        int bestPiece = -1;
        int bestScore = Integer.MIN_VALUE;
        
        for (int i = 0; i < 16; i++) {
            if ((available & (1 << i)) != 0) {
                // Score based on attribute balance
                int score = 0;
                // Prefer pieces with 2 of each attribute pair
                int attrCount = Integer.bitCount(i);
                score = -Math.abs(attrCount - 2);  // Prefer 2 attributes set
                
                if (score > bestScore) {
                    bestScore = score;
                    bestPiece = i;
                }
            }
        }
        
        if (bestPiece >= 0) {
            return new Move(-1, null, game.getPieceById(bestPiece));
        }
        
        // Fallback: first available
        for (int i = 0; i < 16; i++) {
            if ((available & (1 << i)) != 0) {
                return new Move(-1, null, game.getPieceById(i));
            }
        }
        return null;
    }
    
    // ==================== UTILITY ====================
    private long pack(int score, int sq, int nextP) {
        long res = 0;
        res |= ((long)score) << 32;
        int packedMove = ((sq & 0xFFFF) << 16) | (nextP & 0xFFFF);
        res |= (packedMove & 0xFFFFFFFFL);
        return res;
    }
    
    @Override
    public String getName() {
        return "O2Lock (Constraint-Solver Hybrid)";
    }
}
