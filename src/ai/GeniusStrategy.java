package ai;

import java.util.List;
import java.util.Random;

import Game.Board;
import Game.Game;
import Game.Move;
import Game.Piece;

/**
 * Advanced AI strategy using Zobrist hashing, bitboards, and negamax.
 * Genius difficulty - uses iterative deepening with advanced heuristics.
 */
public class GeniusStrategy implements Strategy {
    
    // Zobrist Hashing
    private static final long[][] Z_SQUARE_PIECE = new long[16][16]; // [square][pieceId]
    private static final long[] Z_NEXT_PIECE = new long[16];   // [pieceId]
    
    // D4 Symmetry: 8 transformations of 16 board indices
    // SYMMETRIES[sym][new_idx] = old_idx (maps transformed position to original)
    private static final int[][] SYMMETRIES = {
        {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},     // Identity
        {12,8,4,0,13,9,5,1,14,10,6,2,15,11,7,3},     // 90° CW
        {15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0},     // 180°
        {3,7,11,15,2,6,10,14,1,5,9,13,0,4,8,12},     // 270° CW
        {3,2,1,0,7,6,5,4,11,10,9,8,15,14,13,12},     // Horizontal flip
        {12,13,14,15,8,9,10,11,4,5,6,7,0,1,2,3},     // Vertical flip
        {0,4,8,12,1,5,9,13,2,6,10,14,3,7,11,15},     // Main diagonal
        {15,11,7,3,14,10,6,2,13,9,5,1,12,8,4,0}      // Anti-diagonal
    };
    
    // INVERSE_SYMMETRIES[sym][canonical_idx] = original_idx
    private static final int[][] INVERSE_SYMMETRIES = new int[8][16];
    
    // Opening optimization: unique orbit representatives (Corner, Edge, Center)
    private static final int[] OPENING_SQUARES = {0, 1, 5};
    
    // Transposition Table
    private static final int TT_SIZE = 1 << 20; // 1M entries
    private static final long[] TT_KEYS = new long[TT_SIZE];    // Hash keys for verification
    private static final long[] TT_VALUES = new long[TT_SIZE];  // Packed: score(16) | depth(8) | flag(8)
    private static final int[] TT_MOVES = new int[TT_SIZE];     // Packed: (sq << 8) | (nextP << 4) | sym
    
    // TT Flag constants
    private static final int TT_EXACT = 0;
    private static final int TT_ALPHA = 1; // Upper bound (failed low)
    private static final int TT_BETA = 2;  // Lower bound (failed high)

    // Weights
    private static final double W_CONSTRAINT = 1.5;
    private static final double W_DECEPTION = 3.0;

    // Precomputed Winning Masks
    private static final int[] WIN_MASKS = initializeWinMasks();
    
    // Cache for move computation
    private Move cachedMove;
    
    // Pre-allocated arrays for garbage-free canonical hash computation
    private final int[] piecesOnBoard = new int[16];  // Reused for hash computation (-1 = empty)
    private final long[] symHashes = new long[8];     // Reused for 8 symmetry hashes

    static {
        initializeZobrist();
        initializeInverseSymmetries();
    }
    
    private static void initializeInverseSymmetries() {
        for (int sym = 0; sym < 8; sym++) {
            for (int i = 0; i < 16; i++) {
                INVERSE_SYMMETRIES[sym][SYMMETRIES[sym][i]] = i;
            }
        }
    }
    
    /**
     * Computes canonical hash across all 8 D4 symmetries.
     * Uses pre-allocated piecesOnBoard and symHashes arrays.
     * @param pieceToPlace piece ID to place (-1 if none)
     * @return packed long: canonical hash in upper 60 bits, best symmetry in lower 4 bits
     */
    private long computeCanonicalHash(int pieceToPlace) {
        long minHash = Long.MAX_VALUE;
        int minSym = 0;
        
        for (int sym = 0; sym < 8; sym++) {
            long h = 0;
            int[] symMap = SYMMETRIES[sym];
            for (int newIdx = 0; newIdx < 16; newIdx++) {
                int oldIdx = symMap[newIdx];
                int pieceId = piecesOnBoard[oldIdx];
                if (pieceId >= 0) {
                    h ^= Z_SQUARE_PIECE[newIdx][pieceId];
                }
            }
            if (pieceToPlace >= 0) {
                h ^= Z_NEXT_PIECE[pieceToPlace];
            }
            symHashes[sym] = h;
            
            // Use unsigned comparison for consistent ordering
            if (Long.compareUnsigned(h, minHash) < 0) {
                minHash = h;
                minSym = sym;
            }
        }
        
        // Pack: shift hash left by 4 bits, store symmetry in lower 4 bits
        // Note: This loses 4 bits of hash precision but symmetry index fits in 3 bits
        return (minHash & 0xFFFFFFFFFFFFFFF0L) | (minSym & 0xF);
    }
    
    /**
     * Computes canonical hash for a modified board state (after placing a piece).
     * Garbage-free: operates directly on bitboards without array allocation.
     * @param tall tall bitboard
     * @param round round bitboard
     * @param solid solid bitboard
     * @param dark dark bitboard
     * @param occupied occupied bitboard
     * @param pieceToPlace piece ID to place (-1 if none)
     * @return packed long: canonical hash in upper bits, best symmetry in lower 4 bits
     */
    private long computeCanonicalHashFromBitboards(int tall, int round, int solid, int dark, int occupied, int pieceToPlace) {
        long minHash = Long.MAX_VALUE;
        int minSym = 0;
        
        for (int sym = 0; sym < 8; sym++) {
            long h = 0;
            int[] symMap = SYMMETRIES[sym];
            
            for (int newIdx = 0; newIdx < 16; newIdx++) {
                int oldIdx = symMap[newIdx];
                if ((occupied & (1 << oldIdx)) != 0) {
                    // Reconstruct piece ID from bitboards
                    // Server encoding: bit0=dark, bit1=tall, bit2=square, bit3=hollow
                    int pieceId = 0;
                    if ((dark & (1 << oldIdx)) != 0) pieceId |= 1;   // bit 0 = dark
                    if ((tall & (1 << oldIdx)) != 0) pieceId |= 2;   // bit 1 = tall
                    if ((round & (1 << oldIdx)) == 0) pieceId |= 4;  // bit 2 = square (!round)
                    if ((solid & (1 << oldIdx)) == 0) pieceId |= 8;  // bit 3 = hollow (!solid)
                    h ^= Z_SQUARE_PIECE[newIdx][pieceId];
                }
            }
            if (pieceToPlace >= 0) {
                h ^= Z_NEXT_PIECE[pieceToPlace];
            }
            
            if (Long.compareUnsigned(h, minHash) < 0) {
                minHash = h;
                minSym = sym;
            }
        }
        
        return (minHash & 0xFFFFFFFFFFFFFFF0L) | (minSym & 0xF);
    }

    private static void initializeZobrist() {
        Random rand = new Random(123456789L); // Fixed seed for determinism
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                Z_SQUARE_PIECE[i][j] = rand.nextLong();
            }
            Z_NEXT_PIECE[i] = rand.nextLong();
        }
    }

    private static int[] initializeWinMasks() {
        int[] masks = new int[10]; // 4 rows, 4 cols, 2 diags
        int idx = 0;
        
        // Rows
        for (int r = 0; r < 4; r++) {
            int m = 0;
            for (int c = 0; c < 4; c++) m |= (1 << (r * 4 + c));
            masks[idx++] = m;
        }
        
        // Cols
        for (int c = 0; c < 4; c++) {
            int m = 0;
            for (int r = 0; r < 4; r++) m |= (1 << (r * 4 + c));
            masks[idx++] = m;
        }
        
        // Diag 1 (0, 5, 10, 15)
        masks[idx++] = (1 << 0) | (1 << 5) | (1 << 10) | (1 << 15);
        
        // Diag 2 (3, 6, 9, 12)
        masks[idx++] = (1 << 3) | (1 << 6) | (1 << 9) | (1 << 12);
        
        return masks;
    }

    // Helper to ingest standard OOP board as requested
    private void toBitboard(Board b, int[] state) {
        // state[0]=tall, [1]=round, [2]=solid, [3]=dark, [4]=occupied;
        for (int i = 0; i < 16; i++) {
            Piece p = b.getPiece(i);
            if (p != null) {
                state[4] |= (1 << i);
                if (p.isTall) state[0] |= (1 << i);
                if (p.isRound) state[1] |= (1 << i);
                if (!p.isHollow) state[2] |= (1 << i);
                if (p.isDark) state[3] |= (1 << i);
            }
        }
    }

    @Override
    public Move computeMove(Game game) {
        // Compute the complete move (placement + next piece)
        Move move = determineMove(game);
        
        // Cache the move so we can extract the next piece in pickPieceForOpponent()
        cachedMove = move;
        
        // Return the move (which contains placement info)
        return move;
    }

    @Override
    public Piece pickPieceForOpponent(Game game) {
        // If we have a cached move from computeMove(), use its next piece
        if (cachedMove != null && cachedMove.getNextPiece() != null) {
            Piece nextPiece = cachedMove.getNextPiece();
            cachedMove = null; // Clear cache after use
            return nextPiece;
        }
        
        // Otherwise, call determineMove() to get the move (handles first move case)
        Move move = determineMove(game);
        
        // For first move, determineMove returns a Move with boardIndex=-1 and nextPiece set
        // For regular moves, it returns a Move with both placement and nextPiece
        if (move != null) {
            return move.getNextPiece();
        }
        
        // Fallback: pick first available piece
        List<Piece> available = game.getAvailablePieces();
        return available.isEmpty() ? null : available.get(0);
    }

    private Move determineMove(Game game) {
        // 1. Convert Board to Bitboards
        Board b = game.getBoard();
        int[] state = new int[5]; // tall, round, solid, dark, occupied
        toBitboard(b, state);
        
        int tall = state[0];
        int round = state[1];
        int solid = state[2];
        int dark = state[3];
        int occupied = state[4];

        // 2. Encoded Available Pieces
        int available = 0;
        List<Piece> availList = game.getAvailablePieces();
        for (Piece p : availList) {
            available |= (1 << p.getId());
        }

        // 3. Current Piece to Place
        Piece currentP = game.getCurrentPiece();
        int pieceToPlaceId = (currentP != null) ? currentP.getId() : -1;

        if (pieceToPlaceId == -1) {
            return pickBestOpeningPiece(available, game);
        }

        // 4. Iterative Deepening with Endgame Extension
        long startTime = System.currentTimeMillis();
        long timeLimit = 2000; // 2 seconds max
        
        // Count available pieces to determine max depth
        // Endgame: fewer pieces = smaller branching factor = can search deeper
        int numAvailable = Integer.bitCount(available);
        int maxDepth;
        if (numAvailable <= 4) {
            maxDepth = 12; // Very deep for endgame - detect parity traps
        } else if (numAvailable <= 6) {
            maxDepth = 8;  // Deep search for late game
        } else if (numAvailable <= 10) {
            maxDepth = 6;  // Medium depth
        } else {
            maxDepth = 4;  // Standard opening/early game
        }
        
        Move bestMove = null;
        
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() - startTime > timeLimit) break;
            
            long searchResult = searchRoot(depth, tall, round, solid, dark, occupied, available, pieceToPlaceId);
            
            int score = (int)(searchResult >> 32);
            int movePacked = (int)searchResult;
            int bestSq = (movePacked >> 16) & 0xFFFF;
            if (bestSq == 0xFFFF) bestSq = -1;
            int bestNextP = movePacked & 0xFFFF;
            
            Piece pToPlace = game.getPieceById(pieceToPlaceId);
            
            if (bestSq != -1) {
                Piece nextP = game.getPieceById(bestNextP);
                // If nextP is null (winning move or no pieces left), pick any available piece
                if (nextP == null && !availList.isEmpty()) {
                    nextP = availList.get(0);
                }
                bestMove = new Move(bestSq, pToPlace, nextP);
            }
            
            // Early termination if we found a winning move or are losing badly
            if (score > 9000 || score < -9000) break; 
        }

        if (bestMove == null) {
            List<Move> valid = game.getValidMoves();
            if (valid.isEmpty()) {
                return null;
            }
            // Fallback: use first valid position and pick any available piece
            Move fallbackMove = valid.get(0);
            Piece fallbackNextPiece = availList.isEmpty() ? null : availList.get(0);
            return new Move(fallbackMove.getBoardIndex(), fallbackMove.getPiece(), fallbackNextPiece);
        }
        
        return bestMove;
    }

    // Root Search with D4 Symmetry Reduction and Opening Optimization
    private long searchRoot(int depth, int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;
        
        int alpha = -1000000;
        int beta = 1000000;
        
        // Opening optimization: if board is empty, only try unique orbit representatives
        // This reduces from 16 squares to 3 (Corner=0, Edge=1, Center=5)
        boolean isOpening = (occupied == 0);
        
        // Decode piece attributes once outside the loop
        // Server encoding: bit0=dark, bit1=tall, bit2=square, bit3=hollow
        boolean isDark = ((pieceId & 1) != 0);
        boolean isTall = ((pieceId & 2) != 0);
        boolean isRound = ((pieceId & 4) == 0);  // NOT square
        boolean isSolid = ((pieceId & 8) == 0);  // NOT hollow
        
        // Choose which squares to iterate over
        int numSquares = isOpening ? OPENING_SQUARES.length : 16;
        
        for (int sqIdx = 0; sqIdx < numSquares; sqIdx++) {
            int sq = isOpening ? OPENING_SQUARES[sqIdx] : sqIdx;
            
            if ((occupied & (1 << sq)) == 0) {
                int nTall = isTall ? (tall | (1 << sq)) : tall;
                int nRound = isRound ? (round | (1 << sq)) : round;
                int nSolid = isSolid ? (solid | (1 << sq)) : solid;
                int nDark = isDark ? (dark | (1 << sq)) : dark;
                int nOcc = occupied | (1 << sq);
                
                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return pack(10000, sq, -1);
                }

                int nAvail = available;
                if (nAvail == 0) {
                     // Draw
                     if (0 > bestScore) {
                         bestScore = 0;
                         bestSq = sq;
                         bestNextP = -1;
                     }
                     continue; 
                }

                // First pass: find safe pieces (opponent can't immediately win)
                int safePiece = -1;
                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((nAvail & (1 << nextP)) != 0) {
                        // Critical check: never give a piece that immediately wins for opponent
                        if (canWinWithPiece(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                            // This piece is poison - skip it unless no alternatives
                            continue;
                        }
                        if (safePiece == -1) safePiece = nextP; // Track first safe piece
                        
                        int val = -negamax(depth - 1, -beta, -alpha, nTall, nRound, nSolid, nDark, nOcc, nAvail & ~(1 << nextP), nextP);
                        
                        if (val > bestScore) {
                            bestScore = val;
                            bestSq = sq;
                            bestNextP = nextP;
                        }
                        if (bestScore > alpha) alpha = bestScore;
                        if (alpha >= beta) break; 
                    }
                }
                
                // Fallback: if all pieces are poison (forced loss), pick the "best" losing move
                if (bestNextP == -1 && safePiece == -1) {
                    // We have to give a losing piece - search all to find least bad option
                    for (int nextP = 0; nextP < 16; nextP++) {
                        if ((nAvail & (1 << nextP)) != 0) {
                            int val = -negamax(depth - 1, -beta, -alpha, nTall, nRound, nSolid, nDark, nOcc, nAvail & ~(1 << nextP), nextP);
                            if (val > bestScore) {
                                bestScore = val;
                                bestSq = sq;
                                bestNextP = nextP;
                            }
                        }
                    }
                }
            }
        }
        return pack(bestScore, bestSq, bestNextP);
    }
    
    // Core Negamax with Transposition Table and D4 Symmetry Reduction
    private int negamax(int depth, int alpha, int beta, int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int alphaOrig = alpha;
        
        // --- CANONICAL HASH for TT (D4 Symmetry Reduction) ---
        long canonicalPacked = computeCanonicalHashFromBitboards(tall, round, solid, dark, occupied, pieceId);
        long canonicalHash = canonicalPacked & 0xFFFFFFFFFFFFFFF0L;
        int canonicalSym = (int)(canonicalPacked & 0xF);
        
        int ttIndex = (int)((canonicalHash >>> 4) & (TT_SIZE - 1));
        
        // --- TT LOOKUP ---
        int ttBestSq = -1;
        int ttBestNextP = -1;
        if (TT_KEYS[ttIndex] == canonicalHash) {
            long ttEntry = TT_VALUES[ttIndex];
            int ttDepth = (int)((ttEntry >> 8) & 0xFF);
            int ttFlag = (int)(ttEntry & 0xFF);
            int ttScore = (int)(ttEntry >> 16);
            
            if (ttDepth >= depth) {
                if (ttFlag == TT_EXACT) {
                    return ttScore;
                } else if (ttFlag == TT_ALPHA && ttScore <= alpha) {
                    return alpha;
                } else if (ttFlag == TT_BETA && ttScore >= beta) {
                    return beta;
                }
            }
            
            // --- TT MOVE ORDERING ---
            // Retrieve best move from TT even if depth insufficient for score cutoff
            int ttMove = TT_MOVES[ttIndex];
            int storedNextP = (ttMove >> 4) & 0xF;
            int storedSq = (ttMove >> 8) & 0xFF;
            if (storedSq < 16) {
                // Transform from canonical back to current orientation
                ttBestSq = INVERSE_SYMMETRIES[canonicalSym][storedSq];
                ttBestNextP = storedNextP;
            }
        }
        
        if (depth == 0) {
            return evaluate(tall, round, solid, dark, occupied, available, pieceId);
        }

        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;
        
        // Decode piece attributes once outside the loop
        // Server encoding: bit0=dark, bit1=tall, bit2=square, bit3=hollow
        boolean isDark = ((pieceId & 1) != 0);
        boolean isTall = ((pieceId & 2) != 0);
        boolean isRound = ((pieceId & 4) == 0);  // NOT square
        boolean isSolid = ((pieceId & 8) == 0);  // NOT hollow
        
        // --- SEARCH TT MOVE FIRST (if valid) ---
        if (ttBestSq >= 0 && (occupied & (1 << ttBestSq)) == 0) {
            int sq = ttBestSq;
            int nTall = isTall ? (tall | (1 << sq)) : tall;
            int nRound = isRound ? (round | (1 << sq)) : round;
            int nSolid = isSolid ? (solid | (1 << sq)) : solid;
            int nDark = isDark ? (dark | (1 << sq)) : dark;
            int nOcc = occupied | (1 << sq);
            
            if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                return 10000 + depth;
            }
            
            if (available == 0) return 0;
            
            // Try TT best next piece first
            if (ttBestNextP >= 0 && (available & (1 << ttBestNextP)) != 0) {
                int val = -negamax(depth - 1, -beta, -alpha, nTall, nRound, nSolid, nDark, nOcc, available & ~(1 << ttBestNextP), ttBestNextP);
                if (val > bestScore) {
                    bestScore = val;
                    bestSq = sq;
                    bestNextP = ttBestNextP;
                }
                if (bestScore > alpha) alpha = bestScore;
            }
            
            // Then try other next pieces for the TT square (if no cutoff)
            if (alpha < beta) {
                for (int nextP = 0; nextP < 16; nextP++) {
                    if (nextP == ttBestNextP) continue; // Already searched
                    if ((available & (1 << nextP)) != 0) {
                        int val = -negamax(depth - 1, -beta, -alpha, nTall, nRound, nSolid, nDark, nOcc, available & ~(1 << nextP), nextP);
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
        
        // --- SEARCH REMAINING SQUARES ---
        for (int sq = 0; sq < 16 && alpha < beta; sq++) {
            if (sq == ttBestSq) continue; // Already searched
            if ((occupied & (1 << sq)) == 0) {
                int nTall = isTall ? (tall | (1 << sq)) : tall;
                int nRound = isRound ? (round | (1 << sq)) : round;
                int nSolid = isSolid ? (solid | (1 << sq)) : solid;
                int nDark = isDark ? (dark | (1 << sq)) : dark;
                int nOcc = occupied | (1 << sq);
                
                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return 10000 + depth;
                }
                
                if (available == 0) return 0;

                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                         int val = -negamax(depth - 1, -beta, -alpha, nTall, nRound, nSolid, nDark, nOcc, available & ~(1 << nextP), nextP);
                         
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
        
        // --- TT STORE with canonical hash ---
        int ttFlag;
        if (bestScore <= alphaOrig) {
            ttFlag = TT_ALPHA; // Failed low, upper bound
        } else if (bestScore >= beta) {
            ttFlag = TT_BETA;  // Failed high, lower bound
        } else {
            ttFlag = TT_EXACT; // Exact score
        }
        TT_KEYS[ttIndex] = canonicalHash;
        TT_VALUES[ttIndex] = ((long)bestScore << 16) | ((long)depth << 8) | ttFlag;
        // Store move in canonical orientation with symmetry info
        // Transform square to canonical orientation: use SYMMETRIES to map original sq to canonical sq
        int canonicalSq = (bestSq >= 0) ? SYMMETRIES[canonicalSym][bestSq] : -1;
        TT_MOVES[ttIndex] = ((canonicalSq & 0xFF) << 8) | ((bestNextP & 0xF) << 4) | (canonicalSym & 0xF);
        
        return bestScore;
    }

    // Heuristic Evaluation (Opponent's Perspective)
    private int evaluate(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int safety = 0;
        int traps = 0;
        
        // Correct bit mapping: bit0=tall, bit1=dark, bit2=hollow, bit3=round
        boolean isTall = ((pieceId & 1) != 0);   // bit 0
        boolean isDark = ((pieceId & 2) != 0);   // bit 1
        boolean isSolid = ((pieceId & 4) == 0);  // bit 2 = hollow, so solid = !hollow
        boolean isRound = ((pieceId & 8) != 0);  // bit 3

        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) == 0) {
                int nTall = isTall ? (tall | (1 << sq)) : tall;
                int nRound = isRound ? (round | (1 << sq)) : round;
                int nSolid = isSolid ? (solid | (1 << sq)) : solid;
                int nDark = isDark ? (dark | (1 << sq)) : dark;
                int nOcc = occupied | (1 << sq);

                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    return 10000;
                }

                if (available == 0) continue; 

                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        // Step 2: Filter Loss
                        if (canWinWithPiece(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                             continue;
                        }
                        
                        // Step 3: Safety
                        safety++;
                        
                        // Trap Detection
                        if (canForceWin(nTall, nRound, nSolid, nDark, nOcc, available & ~(1 << nextP), nextP)) {
                            traps++;
                        }
                    }
                }
            }
        }
        
        return (int) -( (W_CONSTRAINT * safety) + (W_DECEPTION * traps) );
    }

    private boolean canWinWithPiece(int tall, int round, int solid, int dark, int occupied, int pieceId) {
        // Correct bit mapping: bit0=tall, bit1=dark, bit2=hollow, bit3=round
        boolean isTall = ((pieceId & 1) != 0);   // bit 0
        boolean isDark = ((pieceId & 2) != 0);   // bit 1
        boolean isSolid = ((pieceId & 4) == 0);  // bit 2 = hollow, so solid = !hollow
        boolean isRound = ((pieceId & 8) != 0);  // bit 3
        
        for (int sq = 0; sq < 16; sq++) {
             if ((occupied & (1 << sq)) == 0) {
                int nTall = isTall ? (tall | (1 << sq)) : tall;
                int nRound = isRound ? (round | (1 << sq)) : round;
                int nSolid = isSolid ? (solid | (1 << sq)) : solid;
                int nDark = isDark ? (dark | (1 << sq)) : dark;
                int nOcc = occupied | (1 << sq);
                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) return true;
             }
        }
        return false;
    }

    private boolean canForceWin(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        // Correct bit mapping: bit0=tall, bit1=dark, bit2=hollow, bit3=round
        boolean isTall = ((pieceId & 1) != 0);   // bit 0
        boolean isDark = ((pieceId & 2) != 0);   // bit 1
        boolean isSolid = ((pieceId & 4) == 0);  // bit 2 = hollow, so solid = !hollow
        boolean isRound = ((pieceId & 8) != 0);  // bit 3

        for (int sq2 = 0; sq2 < 16; sq2++) {
             if ((occupied & (1 << sq2)) == 0) {
                 int nTall = isTall ? (tall | (1 << sq2)) : tall;
                 int nRound = isRound ? (round | (1 << sq2)) : round;
                 int nSolid = isSolid ? (solid | (1 << sq2)) : solid;
                 int nDark = isDark ? (dark | (1 << sq2)) : dark;
                 int nOcc = occupied | (1 << sq2);
                 
                 if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) return true;

                 int nAvail = available;
                 if (nAvail == 0) continue; 
                 
                 for (int nextS = 0; nextS < 16; nextS++) {
                     if ((nAvail & (1 << nextS)) != 0) {
                         if (!opponentHasSafeMove(nTall, nRound, nSolid, nDark, nOcc, nAvail & ~(1 << nextS), nextS)) {
                             return true;
                         }
                     }
                 }
             }
        }
        return false;
    }
    
    private boolean opponentHasSafeMove(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        // Correct bit mapping: bit0=tall, bit1=dark, bit2=hollow, bit3=round
        boolean isTall = ((pieceId & 1) != 0);   // bit 0
        boolean isDark = ((pieceId & 2) != 0);   // bit 1
        boolean isSolid = ((pieceId & 4) == 0);  // bit 2 = hollow, so solid = !hollow
        boolean isRound = ((pieceId & 8) != 0);  // bit 3

        for (int sq = 0; sq < 16; sq++) {
             if ((occupied & (1 << sq)) == 0) {
                 int nTall = isTall ? (tall | (1 << sq)) : tall;
                 int nRound = isRound ? (round | (1 << sq)) : round;
                 int nSolid = isSolid ? (solid | (1 << sq)) : solid;
                 int nDark = isDark ? (dark | (1 << sq)) : dark;
                 int nOcc = occupied | (1 << sq);
                 
                 if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) return true; // Winning is beneficial for opponent, so likely picked.
                 // But in "Check Trap context", if they can win, they have a safe move (winning is very safe).

                 if (available == 0) return true;
                 
                 boolean moveIsSafe = true;
                 for (int nextT = 0; nextT < 16; nextT++) {
                     if ((available & (1 << nextT)) != 0) {
                         if (canWinWithPiece(nTall, nRound, nSolid, nDark, nOcc, nextT)) {
                             moveIsSafe = false; // They hand me a win
                             break;
                         }
                     }
                 }
                 if (moveIsSafe) return true;
             }
        }
        return false;
    }

    private boolean checkWin(int tall, int round, int solid, int dark, int occupied) {
        for (int mask : WIN_MASKS) {
             if ((occupied & mask) == mask) {
                 boolean t = (tall & mask) == mask || (tall & mask) == 0;
                 if (t) return true;
                 boolean r = (round & mask) == mask || (round & mask) == 0;
                 if (r) return true;
                 boolean s = (solid & mask) == mask || (solid & mask) == 0;
                 if (s) return true;
                 boolean d = (dark & mask) == mask || (dark & mask) == 0;
                 if (d) return true;
             }
        }
        return false;
    }

    private Move pickBestOpeningPiece(int available, Game game) {
        for (int i = 0; i < 16; i++) {
             if ((available & (1 << i)) != 0) {
                  return new Move(-1, null, game.getPieceById(i));
             }
        }
        return null;
    }

    private long pack(int score, int sq, int nextP) {
        long res = 0;
        res |= ((long)score) << 32;
        int packedMove = ((sq & 0xFFFF) << 16) | (nextP & 0xFFFF);
        res |= (packedMove & 0xFFFFFFFFL);
        return res;
    }

    @Override
    public String getName() {
        return "Genius (Advanced)";
    }
}
