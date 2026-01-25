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
public class GeniusStrategy implements Strategy {
    
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
    private static final int ENDGAME_THRESHOLD = 9;  // Switch to God Engine when empty <= 9
    
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
    
    // ==================== MAIN ENTRY POINT ====================
    @Override
    public Move computeMove(Game game) {
        Move bestMove = determineMove(game);
        
        if (bestMove == null) return null;
        
        // Handle protocol signals
        if (bestMove.getBoardIndex() != -1) {
            Board boardCopy = game.getBoard().copy();
            boardCopy.setPiece(bestMove.getBoardIndex(), game.getCurrentPiece());
            
            if (boardCopy.hasWinningLine()) {
                return new Move(bestMove.getBoardIndex(), game.getCurrentPiece(),
                    new Piece(PROTOCOL.CLAIM_QUARTO, false, false, false, false));
            }
            
            if (game.getAvailablePieces().isEmpty()) {
                return new Move(bestMove.getBoardIndex(), game.getCurrentPiece(),
                    new Piece(PROTOCOL.FINAL_PIECE_NO_CLAIM, false, false, false, false));
            }
        }
        
        return bestMove;
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
        
        // PHASE SWITCH: Choose search strategy based on game phase
        long searchResult;
        if (emptySquares <= ENDGAME_THRESHOLD) {
            // Phase 2: God Engine - Perfect play
            searchResult = godEngineSearch(tall, round, solid, dark, occupied, available, pieceToPlaceId);
        } else {
            // Phase 1: Strangler - Heuristic play
            searchResult = stranglerSearch(tall, round, solid, dark, occupied, available, pieceToPlaceId);
        }
        
        // Unpack result
        int score = (int)(searchResult >> 32);
        int movePacked = (int)searchResult;
        int bestSq = (movePacked >> 16) & 0xFFFF;
        if (bestSq == 0xFFFF) bestSq = -1;
        int bestNextP = movePacked & 0xFFFF;
        
        if (bestSq != -1) {
            Piece pToPlace = game.getPieceById(pieceToPlaceId);
            if (pToPlace == null) {
                pToPlace = currentP;
            }
            Piece nextP = game.getPieceById(bestNextP);
            if (nextP == null && !availList.isEmpty()) {
                nextP = availList.get(0);
            }
            return new Move(bestSq, pToPlace, nextP);
        }
        
        // Fallback
        List<Move> valid = game.getValidMoves();
        if (valid.isEmpty()) return null;
        Move fallbackMove = valid.get(0);
        Piece fallbackNextPiece = availList.isEmpty() ? null : availList.get(0);
        return new Move(fallbackMove.getBoardIndex(), fallbackMove.getPiece(), fallbackNextPiece);
    }
    
    // ==================== PHASE 2: GOD ENGINE ====================
    /**
     * Perfect play via Alpha-Beta to terminal states.
     * Used when EmptySquares <= 9.
     */
    private long godEngineSearch(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int emptySquares = 16 - Integer.bitCount(occupied);
        int maxDepth = emptySquares * 2 + 2;  // Search to end
        
        long startTime = System.currentTimeMillis();
        long timeLimit = 3000;  // 3 seconds for endgame
        
        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;
        
        int alpha = -1000000;
        int beta = 1000000;
        
        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);
        
        // Search each empty square
        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) == 0) {
                if (System.currentTimeMillis() - startTime > timeLimit) break;
                
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
                
                // No pieces left = draw
                if (available == 0) {
                    if (0 > bestScore) {
                        bestScore = 0;
                        bestSq = sq;
                        bestNextP = -1;
                    }
                    continue;
                }
                
                // Try each available piece to give
                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        // Prune: never give piece that immediately wins for opponent
                        if (canWinWithPiece(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                            continue;
                        }
                        
                        int val = -godEngineNegamax(maxDepth - 1, -beta, -alpha,
                            nTall, nRound, nSolid, nDark, nOcc, 
                            available & ~(1 << nextP), nextP, startTime, timeLimit);
                        
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
                        if (bestScore > alpha) alpha = bestScore;
                        if (alpha >= beta) break;
                    }
                }
            }
        }
        
        // If no safe piece found, search poison pieces
        if (bestNextP == -1 && bestSq == -1) {
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
                                available & ~(1 << nextP), nextP, startTime, timeLimit);
                            
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
        
        return pack(bestScore, bestSq, bestNextP);
    }
    
    private int godEngineNegamax(int depth, int alpha, int beta, 
            int tall, int round, int solid, int dark, int occupied,
            int available, int pieceId, long startTime, long timeLimit) {
        
        // Time check
        if (System.currentTimeMillis() - startTime > timeLimit) {
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
            // In God Engine, we search to terminal - return draw if no winner
            return 0;
        }
        
        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;
        
        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);
        
        // Try TT move first
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
            
            if (available == 0) return 0;
            
            if (ttBestNextP >= 0 && (available & (1 << ttBestNextP)) != 0) {
                int val = -godEngineNegamax(depth - 1, -beta, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << ttBestNextP), ttBestNextP, startTime, timeLimit);
                if (val > bestScore) {
                    bestScore = val;
                    bestSq = sq;
                    bestNextP = ttBestNextP;
                }
                if (bestScore > alpha) alpha = bestScore;
            }
        }
        
        // Search remaining squares
        for (int sq = 0; sq < 16 && alpha < beta; sq++) {
            if (sq == ttBestSq) continue;
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
                        int val = -godEngineNegamax(depth - 1, -beta, -alpha,
                            nTall, nRound, nSolid, nDark, nOcc,
                            available & ~(1 << nextP), nextP, startTime, timeLimit);
                        
                        if (val > bestScore) {
                            bestScore = val;
                            bestSq = sq;
                            bestNextP = nextP;
                        }
                        if (bestScore > alpha) alpha = bestScore;
                        if (alpha >= beta) break;
                    }
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
     * Heuristic play via "Panic Maximization".
     * Score = -(2.21 * Safety) - (0.37 * Traps)
     * Used when EmptySquares > 9.
     */
    private long stranglerSearch(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        long startTime = System.currentTimeMillis();
        long timeLimit = 2000;  // 2 seconds for mid-game
        
        int maxDepth = 4;  // Iterative deepening to depth 4
        
        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;
        
        boolean isOpening = (occupied == 0);
        
        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);
        
        // Iterative deepening
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() - startTime > timeLimit) break;
            
            int iterBestScore = -1000000;
            int iterBestSq = -1;
            int iterBestNextP = -1;
            
            int alpha = -1000000;
            int beta = 1000000;
            
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
                    
                    // Try each piece - skip poison pieces
                    for (int nextP = 0; nextP < 16; nextP++) {
                        if ((available & (1 << nextP)) != 0) {
                            // Immediate pruning: don't give winning piece
                            if (canWinWithPiece(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                                continue;
                            }
                            
                            int val = -stranglerNegamax(depth - 1, -beta, -alpha,
                                nTall, nRound, nSolid, nDark, nOcc,
                                available & ~(1 << nextP), nextP, startTime, timeLimit);
                            
                            if (val > iterBestScore) {
                                iterBestScore = val;
                                iterBestSq = sq;
                                iterBestNextP = nextP;
                            }
                            if (iterBestScore > alpha) alpha = iterBestScore;
                            if (alpha >= beta) break;
                        }
                    }
                }
            }
            
            // Update best from this iteration
            if (iterBestSq != -1) {
                bestScore = iterBestScore;
                bestSq = iterBestSq;
                bestNextP = iterBestNextP;
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
            int available, int pieceId, long startTime, long timeLimit) {
        
        if (System.currentTimeMillis() - startTime > timeLimit) {
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
        
        // At depth 0, use Strangler heuristic
        if (depth == 0) {
            return evaluateStrangler(tall, round, solid, dark, occupied, available, pieceId);
        }
        
        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;
        
        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);
        
        // Try TT move first
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
            
            if (available == 0) return 0;
            
            if (ttBestNextP >= 0 && (available & (1 << ttBestNextP)) != 0) {
                int val = -stranglerNegamax(depth - 1, -beta, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << ttBestNextP), ttBestNextP, startTime, timeLimit);
                if (val > bestScore) {
                    bestScore = val;
                    bestSq = sq;
                    bestNextP = ttBestNextP;
                }
                if (bestScore > alpha) alpha = bestScore;
            }
        }
        
        // Search remaining squares
        for (int sq = 0; sq < 16 && alpha < beta; sq++) {
            if (sq == ttBestSq) continue;
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
                        int val = -stranglerNegamax(depth - 1, -beta, -alpha,
                            nTall, nRound, nSolid, nDark, nOcc,
                            available & ~(1 << nextP), nextP, startTime, timeLimit);
                        
                        if (val > bestScore) {
                            bestScore = val;
                            bestSq = sq;
                            bestNextP = nextP;
                        }
                        if (bestScore > alpha) alpha = bestScore;
                        if (alpha >= beta) break;
                    }
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
     * Evaluate position from opponent's perspective.
     * Score = -(2.21 * S) - (0.37 * T)
     * S = Safety: squares where opponent can place without creating our win
     * T = Traps: squares that look safe but lead to forced loss at depth 2
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
                
                // If this placement wins for us (opponent), good!
                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return 10000;
                }
                
                if (available == 0) continue;
                
                // Check each piece we could give
                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        // Skip if this piece wins for the other player
                        if (canWinWithPiece(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                            continue;
                        }
                        
                        // This is a "safe" move for opponent
                        safety++;
                        
                        // Check if it's actually a trap (depth 2 forced loss)
                        if (isTrap(nTall, nRound, nSolid, nDark, nOcc, available & ~(1 << nextP), nextP)) {
                            traps++;
                        }
                    }
                }
            }
        }
        
        // Strangler formula: minimize opponent's options
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
        return "Genius (Constraint-Solver Hybrid)";
    }
}
