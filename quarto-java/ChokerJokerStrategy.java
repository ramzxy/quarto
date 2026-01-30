package ai;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import Game.Board;
import Game.Game;
import Game.Move;
import Game.Piece;
import Protocol.PROTOCOL;

public class ChokerJokerStrategy implements Strategy {

    // ==================== GLOBAL BIT CONSTANTS (Server Protocol) ====================
    private static final int BIT_DARK   = 0;
    private static final int BIT_TALL   = 1;
    private static final int BIT_SQUARE = 2;
    private static final int BIT_HOLLOW = 3;

    private static final int MASK_DARK   = 1 << BIT_DARK;
    private static final int MASK_TALL   = 1 << BIT_TALL;
    private static final int MASK_SQUARE = 1 << BIT_SQUARE;
    private static final int MASK_HOLLOW = 1 << BIT_HOLLOW;

    // ==================== SEARCH CONSTANTS ====================
    private static final int MAX_DEPTH = 32;
    private static final int LMR_THRESHOLD = 3;
    private static final int LMR_DEPTH_THRESHOLD = 2;
    private static final int TIMEOUT_SENTINEL = -999999;

    // ==================== STRANGLER WEIGHTS ====================
    private static final double W_SAFETY = 2.21;
    private static final double W_TRAPS = 0.37;
    private static final double W_INITIATIVE = 0.15;
    private static final double W_CONSTRAINT = 0.25;

    // ==================== ZOBRIST HASHING ====================
    private static final long[][] Z_SQUARE_PIECE = new long[16][16];
    private static final long[] Z_NEXT_PIECE = new long[16];

    // ==================== D4 SPATIAL SYMMETRIES (8) ====================
    private static final int[][] D4_SYMMETRIES = {
        {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {12,8,4,0,13,9,5,1,14,10,6,2,15,11,7,3},
        {15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0},
        {3,7,11,15,2,6,10,14,1,5,9,13,0,4,8,12},
        {3,2,1,0,7,6,5,4,11,10,9,8,15,14,13,12},
        {12,13,14,15,8,9,10,11,4,5,6,7,0,1,2,3},
        {0,4,8,12,1,5,9,13,2,6,10,14,3,7,11,15},
        {15,11,7,3,14,10,6,2,13,9,5,1,12,8,4,0}
    };

    // ==================== TOPOLOGICAL SYMMETRIES (4) ====================
    private static final int[][] TOPO_SYMMETRIES = {
        {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
        {5,1,2,6,4,0,3,7,8,12,15,11,9,13,14,10},
        {0,5,6,3,9,1,2,10,4,7,11,8,12,14,13,15},
        {5,9,10,6,1,0,3,2,4,12,15,7,8,11,14,13}
    };

    static final int[][] ALL_SYMMETRIES = new int[32][16];
    static final int[][] INVERSE_SYMMETRIES = new int[32][16];

    private static final int[] OPENING_SQUARES = {0, 1, 5};

    // ==================== TRANSPOSITION TABLE ====================
    private static final int TT_SIZE = 1 << 24; // 16 Million entries
    private static final long[] TT_KEYS = new long[TT_SIZE];
    private static final long[] TT_VALUES = new long[TT_SIZE];
    private static final int[] TT_MOVES = new int[TT_SIZE];

    private static final int TT_EXACT = 0;
    private static final int TT_ALPHA = 1;
    private static final int TT_BETA = 2;

    // ==================== WIN MASKS ====================
    private static final int[] WIN_MASKS = initializeWinMasks();

    // ==================== LINE MEMBERSHIP PER SQUARE ====================
    // SQUARE_LINES[sq] = array of indices into WIN_MASKS that include sq
    private static final int[][] SQUARE_LINES = new int[16][];
    // LINE_SQUARES[lineIdx] = the 4 squares in that win line (ordered)
    private static final int[][] LINE_SQUARES = new int[10][4];

    // ==================== DANGER LOOKUP TABLE ====================
    // For each line (10), for each 3-piece attribute pattern (8 = 2^3 combos of the 3 occupied),
    // stores whether attrValue=true or attrValue=false would complete a shared-attribute line.
    // DANGER_TABLE[lineIdx][attrPattern3] = 0b[dangerIfTrue][dangerIfFalse] (2 bits)
    private static final int[][] DANGER_TABLE = new int[10][8];

    // ==================== REUSABLE ARRAYS ====================
    private final long[] symHashes = new long[32];

    // ==================== KILLER & HISTORY HEURISTICS ====================
    private final int[][] killerMoves = new int[MAX_DEPTH][2];
    private final int[][] historyTable = new int[16][16];

    // ==================== OPENING BOOK ====================
    private static final HashMap<Long, long[]> OPENING_BOOK = new HashMap<>();

    // ==================== TIME MANAGEMENT ====================
    long searchStartTime;
    long searchTimeLimit;

    // ==================== STATIC INITIALIZATION ====================
    static {
        initializeZobrist();
        initializeAllSymmetries();
        initializeLineMembership();
        initializeDangerTable();
        loadOpeningBook();
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
        int idx = 0;
        for (int d4 = 0; d4 < 8; d4++) {
            for (int topo = 0; topo < 4; topo++) {
                for (int sq = 0; sq < 16; sq++) {
                    int intermediate = TOPO_SYMMETRIES[topo][sq];
                    ALL_SYMMETRIES[idx][sq] = D4_SYMMETRIES[d4][intermediate];
                }
                idx++;
            }
        }

        for (int sym = 0; sym < 32; sym++) {
            for (int i = 0; i < 16; i++) {
                INVERSE_SYMMETRIES[sym][ALL_SYMMETRIES[sym][i]] = i;
            }
        }
    }

    private static int[] initializeWinMasks() {
        int[] masks = new int[10];
        int idx = 0;
        for (int r = 0; r < 4; r++) {
            int m = 0;
            for (int c = 0; c < 4; c++) m |= (1 << (r * 4 + c));
            masks[idx++] = m;
        }
        for (int c = 0; c < 4; c++) {
            int m = 0;
            for (int r = 0; r < 4; r++) m |= (1 << (r * 4 + c));
            masks[idx++] = m;
        }
        masks[idx++] = (1 << 0) | (1 << 5) | (1 << 10) | (1 << 15);
        masks[idx++] = (1 << 3) | (1 << 6) | (1 << 9) | (1 << 12);
        return masks;
    }

    private static void initializeLineMembership() {
        // Extract squares per line and build reverse mapping
        int[] lineCount = new int[16]; // temp: how many lines per square

        for (int lineIdx = 0; lineIdx < 10; lineIdx++) {
            int mask = WIN_MASKS[lineIdx];
            int sqIdx = 0;
            for (int sq = 0; sq < 16; sq++) {
                if ((mask & (1 << sq)) != 0) {
                    LINE_SQUARES[lineIdx][sqIdx++] = sq;
                    lineCount[sq]++;
                }
            }
        }

        // Allocate and fill SQUARE_LINES
        for (int sq = 0; sq < 16; sq++) {
            SQUARE_LINES[sq] = new int[lineCount[sq]];
            int idx = 0;
            for (int lineIdx = 0; lineIdx < 10; lineIdx++) {
                if ((WIN_MASKS[lineIdx] & (1 << sq)) != 0) {
                    SQUARE_LINES[sq][idx++] = lineIdx;
                }
            }
        }
    }

    private static void initializeDangerTable() {
        // For each line with exactly 3 occupied squares:
        // The 3 pieces have some attribute pattern (3 bits).
        // If all 3 have the attribute (pattern=0b111), placing attrValue=true completes it.
        // If none have the attribute (pattern=0b000), placing attrValue=false completes it.
        // DANGER_TABLE[line][pattern] = bit0: dangerous if attrValue=true, bit1: dangerous if attrValue=false
        for (int lineIdx = 0; lineIdx < 10; lineIdx++) {
            for (int pattern = 0; pattern < 8; pattern++) {
                int bits = 0;
                // pattern represents attribute values of the 3 occupied pieces (3 bits)
                boolean allTrue = (pattern == 0b111);
                boolean allFalse = (pattern == 0b000);
                if (allTrue)  bits |= 1;  // dangerous if piece has attr=true
                if (allFalse) bits |= 2;  // dangerous if piece has attr=false
                DANGER_TABLE[lineIdx][pattern] = bits;
            }
        }
    }

    private static void loadOpeningBook() {
        String[] paths = {"opening_book.dat", "dist/opening_book.dat"};
        for (String path : paths) {
            try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) continue;
                    long hash = Long.parseUnsignedLong(parts[0], 16);
                    int sq = Integer.parseInt(parts[1]);
                    int nextP = Integer.parseInt(parts[2]);
                    int score = Integer.parseInt(parts[3]);
                    OPENING_BOOK.put(hash, new long[]{sq, nextP, score});
                }
                System.err.println("[Book] Loaded " + OPENING_BOOK.size() + " entries from " + path);
                return;
            } catch (IOException e) {
                // try next path
            }
        }
        System.err.println("[Book] No opening book found, proceeding without book");
    }

    // ==================== PIECE ATTRIBUTE DECODING ====================
    static boolean isDark(int pieceId) { return (pieceId & MASK_DARK) != 0; }
    static boolean isTall(int pieceId) { return (pieceId & MASK_TALL) != 0; }
    static boolean isSquare(int pieceId) { return (pieceId & MASK_SQUARE) != 0; }
    static boolean isHollow(int pieceId) { return (pieceId & MASK_HOLLOW) != 0; }
    static boolean isRound(int pieceId) { return !isSquare(pieceId); }
    static boolean isSolid(int pieceId) { return !isHollow(pieceId); }

    // ==================== PIECE ISOMORPHISM ====================
    private int computeInversionMask(int dark, int tall, int round, int solid, int occupied) {
        int count = Integer.bitCount(occupied);
        if (count == 0) return 0;

        int mask = 0;
        if (Integer.bitCount(dark & occupied) > count / 2) mask |= MASK_DARK;
        if (Integer.bitCount(tall & occupied) > count / 2) mask |= MASK_TALL;
        if (Integer.bitCount(round & occupied) <= count / 2) mask |= MASK_SQUARE;
        if (Integer.bitCount(solid & occupied) <= count / 2) mask |= MASK_HOLLOW;
        return mask;
    }

    // ==================== CANONICAL HASH ====================
    long computeCanonicalHash(int tall, int round, int solid, int dark, int occupied, int pieceToPlace) {
        int invMask = computeInversionMask(dark, tall, round, solid, occupied);

        long minHash = Long.MAX_VALUE;
        int minSym = 0;

        for (int sym = 0; sym < 32; sym++) {
            long h = 0;
            int[] symMap = ALL_SYMMETRIES[sym];

            for (int newIdx = 0; newIdx < 16; newIdx++) {
                int oldIdx = symMap[newIdx];
                if ((occupied & (1 << oldIdx)) != 0) {
                    int pieceId = 0;
                    if ((dark & (1 << oldIdx)) != 0) pieceId |= MASK_DARK;
                    if ((tall & (1 << oldIdx)) != 0) pieceId |= MASK_TALL;
                    if ((round & (1 << oldIdx)) == 0) pieceId |= MASK_SQUARE;
                    if ((solid & (1 << oldIdx)) == 0) pieceId |= MASK_HOLLOW;
                    pieceId ^= invMask;
                    h ^= Z_SQUARE_PIECE[newIdx][pieceId];
                }
            }

            if (pieceToPlace >= 0) {
                h ^= Z_NEXT_PIECE[pieceToPlace ^ invMask];
            }

            symHashes[sym] = h;

            if (Long.compareUnsigned(h, minHash) < 0) {
                minHash = h;
                minSym = sym;
            }
        }

        return (minHash & 0xFFFFFFFFFFFFFFE0L) | (minSym & 0x1F);
    }

    // ==================== BOARD CONVERSION ====================
    private void toBitboard(Board b, int[] state) {
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
    boolean checkWin(int tall, int round, int solid, int dark, int occupied) {
        for (int mask : WIN_MASKS) {
            if ((occupied & mask) == mask) {
                int tallLine = tall & mask;
                if (tallLine == mask || tallLine == 0) return true;
                int roundLine = round & mask;
                if (roundLine == mask || roundLine == 0) return true;
                int solidLine = solid & mask;
                if (solidLine == mask || solidLine == 0) return true;
                int darkLine = dark & mask;
                if (darkLine == mask || darkLine == 0) return true;
            }
        }
        return false;
    }

    // ==================== BITWISE DANGER MASKS (Precomputed) ====================
    /**
     * Get all danger squares for a piece using precomputed line membership and danger tables.
     * Only checks lines relevant to empty squares, and uses table lookup instead of branching.
     */
    private int getDangerSquares(int tall, int round, int solid, int dark, int occupied, int pieceId) {
        int danger = 0;
        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        // Check each win line
        for (int lineIdx = 0; lineIdx < 10; lineIdx++) {
            int mask = WIN_MASKS[lineIdx];
            int lineOcc = occupied & mask;
            // Need exactly 3 of 4 squares occupied
            if (Integer.bitCount(lineOcc) != 3) continue;

            int emptyBit = mask & ~occupied;
            int[] sqs = LINE_SQUARES[lineIdx];

            // Extract 3-bit attribute patterns for the 3 occupied squares
            // We iterate the 4 line squares; skip the empty one
            // For each attribute, build a 3-bit pattern of the occupied pieces

            int tallPat = 0, roundPat = 0, solidPat = 0, darkPat = 0;
            int idx = 0;
            for (int s = 0; s < 4; s++) {
                int sq = sqs[s];
                if ((occupied & (1 << sq)) == 0) continue;
                int bit = 1 << idx;
                if ((tall & (1 << sq)) != 0)  tallPat  |= bit;
                if ((round & (1 << sq)) != 0) roundPat |= bit;
                if ((solid & (1 << sq)) != 0) solidPat |= bit;
                if ((dark & (1 << sq)) != 0)  darkPat  |= bit;
                idx++;
            }

            // Table lookup for each attribute
            int tallDanger  = DANGER_TABLE[lineIdx][tallPat];
            int roundDanger = DANGER_TABLE[lineIdx][roundPat];
            int solidDanger = DANGER_TABLE[lineIdx][solidPat];
            int darkDanger  = DANGER_TABLE[lineIdx][darkPat];

            // Check if piece's attributes match any danger pattern
            // bit 0 = dangerous if attr=true, bit 1 = dangerous if attr=false
            boolean isDangerous =
                ((pTall  && (tallDanger & 1) != 0) || (!pTall  && (tallDanger & 2) != 0)) ||
                ((pRound && (roundDanger & 1) != 0) || (!pRound && (roundDanger & 2) != 0)) ||
                ((pSolid && (solidDanger & 1) != 0) || (!pSolid && (solidDanger & 2) != 0)) ||
                ((pDark  && (darkDanger & 1) != 0) || (!pDark  && (darkDanger & 2) != 0));

            if (isDangerous) {
                danger |= emptyBit;
            }
        }
        return danger;
    }

    private int countSafeSquares(int tall, int round, int solid, int dark, int occupied, int pieceId) {
        int danger = getDangerSquares(tall, round, solid, dark, occupied, pieceId);
        int safe = ~danger & ~occupied & 0xFFFF;
        return Integer.bitCount(safe);
    }

    boolean canWinWithPieceFast(int tall, int round, int solid, int dark, int occupied, int pieceId) {
        int danger = getDangerSquares(tall, round, solid, dark, occupied, pieceId);
        return (danger & ~occupied) != 0;
    }

    // ==================== DYNAMIC TIME ALLOCATION ====================
    /**
     * Returns time budget in milliseconds based on game phase (empty squares).
     */
    private long getTimeBudget(int emptySquares) {
        if (emptySquares >= 13) return 5000;       // Early game: 5s
        if (emptySquares >= 11) return 10000;       // Mid game: 10s
        if (emptySquares >= 8)  return 20000;       // Late game: 20s
        return 30000;                                // Endgame: 30s (solve to terminal)
    }

    // ==================== MOVE ORDERING HELPERS ====================
    private void updateKillerMove(int depth, int sq, int nextP) {
        int packed = (sq << 4) | (nextP & 0xF);
        if (killerMoves[depth][0] != packed) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = packed;
        }
    }

    private void updateHistory(int sq, int nextP, int depth) {
        if (sq >= 0 && sq < 16 && nextP >= 0 && nextP < 16) {
            historyTable[sq][nextP] += depth * depth;
            if (historyTable[sq][nextP] > 1000000) {
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        historyTable[i][j] /= 2;
                    }
                }
            }
        }
    }

    void clearSearchTables() {
        for (int i = 0; i < MAX_DEPTH; i++) {
            killerMoves[i][0] = -1;
            killerMoves[i][1] = -1;
        }
    }

    // ==================== MAIN ENTRY POINT ====================
    @Override
    public Move computeMove(Game game) {
        try {
            Move bestMove = determineMove(game);

            if (bestMove == null) return null;

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
        } catch (Exception e) {
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

    // ==================== UNIFIED SEARCH (Always God Engine) ====================
    private Move determineMove(Game game) {
        Board b = game.getBoard();
        int[] state = new int[5];
        toBitboard(b, state);

        int tall = state[0];
        int round = state[1];
        int solid = state[2];
        int dark = state[3];
        int occupied = state[4];

        int emptySquares = 16 - Integer.bitCount(occupied);

        int available = 0;
        List<Piece> availList = game.getAvailablePieces();
        for (Piece p : availList) {
            available |= (1 << p.getId());
        }

        Piece currentP = game.getCurrentPiece();
        int pieceToPlaceId = (currentP != null) ? currentP.getId() : -1;

        // First turn: pick piece using 1-ply Strangler eval
        if (pieceToPlaceId == -1) {
            return pickBestOpeningPiece(tall, round, solid, dark, occupied, available, game);
        }

        // Opening book lookup
        if (!OPENING_BOOK.isEmpty()) {
            long bookPacked = computeCanonicalHash(tall, round, solid, dark, occupied, pieceToPlaceId);
            long bookHash = bookPacked & 0xFFFFFFFFFFFFFFE0L;
            int bookSym = (int)(bookPacked & 0x1F);
            long[] entry = OPENING_BOOK.get(bookHash);
            if (entry != null) {
                int canonSq = (int)entry[0];
                int nextP = (int)entry[1];
                int actualSq = INVERSE_SYMMETRIES[bookSym][canonSq];
                System.err.println("[Book] Hit! sq=" + actualSq + ", nextP=" + nextP + ", score=" + entry[2]);
                Piece pToPlace = game.getPieceById(pieceToPlaceId);
                if (pToPlace == null) pToPlace = currentP;
                Piece nextPiece = game.getPieceById(nextP);
                if (nextPiece == null && !availList.isEmpty()) nextPiece = availList.get(0);
                return new Move(actualSq, pToPlace, nextPiece);
            }
        }

        // Initialize search state
        clearSearchTables();
        searchStartTime = System.currentTimeMillis();

        // Dynamic time allocation based on game phase
        searchTimeLimit = getTimeBudget(emptySquares);

        // Always use God Engine with iterative deepening
        long searchResult = godEngineSearch(tall, round, solid, dark, occupied, available, pieceToPlaceId);

        // Unpack result
        int score = (int)(searchResult >> 32);
        int movePacked = (int)searchResult;
        int bestSq = (movePacked >> 16) & 0xFFFF;
        if (bestSq == 0xFFFF) bestSq = -1;
        int bestNextP = movePacked & 0xFFFF;
        if (bestNextP == 0xFFFF) bestNextP = -1;

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
        if (valid.isEmpty()) {
            return null;
        }
        Move fallbackMove = valid.get(0);
        Piece fallbackNextPiece = availList.isEmpty() ? null : availList.get(0);
        return new Move(fallbackMove.getBoardIndex(), fallbackMove.getPiece(), fallbackNextPiece);
    }

    // ==================== GOD ENGINE (Unified Search) ====================
    long godEngineSearch(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int emptySquares = 16 - Integer.bitCount(occupied);
        int terminalDepth = emptySquares * 2 + 2;

        System.err.println("[GodEngine] Starting search: emptySquares=" + emptySquares + ", terminalDepth=" + terminalDepth + ", pieceId=" + pieceId + ", timeLimit=" + searchTimeLimit + "ms");
        System.err.println("[GodEngine] Available pieces: " + Integer.toBinaryString(available) + " (" + Integer.bitCount(available) + " pieces)");

        int bestScore = -1000000;
        int bestSq = -1;
        int bestNextP = -1;

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        // Generate moves with piece safety heuristic for ordering
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

                for (int nextP = 0; nextP < 16; nextP++) {
                    if ((available & (1 << nextP)) != 0) {
                        if (canWinWithPieceFast(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                            continue;
                        }
                        // Piece safety heuristic: prioritize pieces that constrain opponent
                        int opponentSafe = countSafeSquares(nTall, nRound, nSolid, nDark, nOcc, nextP);
                        int priority = historyTable[sq][nextP] - opponentSafe * 100;
                        moveSqs[moveCount] = sq;
                        moveNextPs[moveCount] = nextP;
                        movePriorities[moveCount] = priority;
                        moveCount++;
                    }
                }
            }
        }

        System.err.println("[GodEngine] Generated " + moveCount + " moves to search");

        int previousScore = 0;  // For aspiration windows

        // Iterative deepening with aspiration windows
        for (int iterDepth = 1; iterDepth <= terminalDepth; iterDepth++) {
            if (System.currentTimeMillis() - searchStartTime > searchTimeLimit) break;

            int iterBestScore = -1000000;
            int iterBestSq = -1;
            int iterBestNextP = -1;
            boolean timedOut = false;

            // Aspiration window
            int alpha, beta;
            int delta = 50;
            if (iterDepth > 1) {
                alpha = previousScore - delta;
                beta = previousScore + delta;
            } else {
                alpha = -1000000;
                beta = 1000000;
            }

            boolean researching;
            do {
                researching = false;
                iterBestScore = -1000000;
                timedOut = false;

                int aspirAlpha = alpha;
                int aspirBeta = beta;

                // Re-sort moves
                for (int i = 0; i < moveCount; i++) {
                    int sq = moveSqs[i];
                    int nextP = moveNextPs[i];
                    int priority = historyTable[sq][nextP];
                    // Piece safety heuristic
                    // (recompute is cheap relative to search)
                    int packed = (sq << 4) | nextP;
                    if (iterDepth < MAX_DEPTH) {
                        if (killerMoves[iterDepth][0] == packed) priority += 50000;
                        else if (killerMoves[iterDepth][1] == packed) priority += 40000;
                    }
                    if (sq == bestSq && nextP == bestNextP) priority += 100000;
                    movePriorities[i] = priority;
                }
                for (int i = 1; i < moveCount; i++) {
                    int j = i;
                    while (j > 0 && movePriorities[j] > movePriorities[j - 1]) {
                        int tmpSq = moveSqs[j]; moveSqs[j] = moveSqs[j-1]; moveSqs[j-1] = tmpSq;
                        int tmpP = moveNextPs[j]; moveNextPs[j] = moveNextPs[j-1]; moveNextPs[j-1] = tmpP;
                        int tmpPri = movePriorities[j]; movePriorities[j] = movePriorities[j-1]; movePriorities[j-1] = tmpPri;
                        j--;
                    }
                }

                // PVS search at this depth
                boolean firstMove = true;
                for (int i = 0; i < moveCount; i++) {
                    if (System.currentTimeMillis() - searchStartTime > searchTimeLimit) {
                        timedOut = true;
                        break;
                    }

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
                        val = -godEngineNegamax(iterDepth - 1, -aspirBeta, -aspirAlpha,
                            nTall, nRound, nSolid, nDark, nOcc,
                            available & ~(1 << nextP), nextP, iterDepth - 1);
                        firstMove = false;
                    } else {
                        val = -godEngineNegamax(iterDepth - 1, -aspirAlpha - 1, -aspirAlpha,
                            nTall, nRound, nSolid, nDark, nOcc,
                            available & ~(1 << nextP), nextP, iterDepth - 1);
                        if (val > aspirAlpha && val < aspirBeta) {
                            val = -godEngineNegamax(iterDepth - 1, -aspirBeta, -aspirAlpha,
                                nTall, nRound, nSolid, nDark, nOcc,
                                available & ~(1 << nextP), nextP, iterDepth - 1);
                        }
                    }

                    if (val == TIMEOUT_SENTINEL || val == -TIMEOUT_SENTINEL) {
                        timedOut = true;
                        break;
                    }

                    // Parity protection
                    int newEmpty = 16 - Integer.bitCount(nOcc);
                    if (val == 0 && (newEmpty % 2) == 0) {
                        val -= 10;
                    }

                    if (val > iterBestScore) {
                        iterBestScore = val;
                        iterBestSq = sq;
                        iterBestNextP = nextP;
                    }
                    if (iterBestScore > aspirAlpha) {
                        aspirAlpha = iterBestScore;
                        if (aspirAlpha >= aspirBeta) {
                            updateKillerMove(iterDepth, sq, nextP);
                            updateHistory(sq, nextP, iterDepth);
                            break;
                        }
                    }
                }

                // Aspiration window fail - widen and re-search
                if (!timedOut && iterDepth > 1 && iterBestScore != -1000000) {
                    if (iterBestScore <= alpha) {
                        // Fail low - widen
                        alpha = -1000000;
                        beta = iterBestScore + 1;
                        researching = true;
                    } else if (iterBestScore >= beta) {
                        // Fail high - widen
                        alpha = iterBestScore - 1;
                        beta = 1000000;
                        researching = true;
                    }
                }
                // Don't re-search on extreme scores
                if (iterBestScore > 9000 || iterBestScore < -9000) {
                    researching = false;
                }
            } while (researching);

            if (!timedOut && iterBestSq != -1) {
                bestScore = iterBestScore;
                bestSq = iterBestSq;
                bestNextP = iterBestNextP;
                previousScore = iterBestScore;
                System.err.println("[GodEngine] Depth " + iterDepth + " complete: score=" + bestScore + ", sq=" + bestSq + ", nextP=" + bestNextP);
            } else if (timedOut) {
                System.err.println("[GodEngine] Depth " + iterDepth + " timed out, using depth " + (iterDepth - 1) + " result");
                break;
            }

            if (bestScore > 9000 || bestScore < -9000) break;
        }

        // Poison fallback if no safe move found
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
                            int val = -godEngineNegamax(1, -1000000, 1000000,
                                nTall, nRound, nSolid, nDark, nOcc,
                                available & ~(1 << nextP), nextP, 1);
                            if (val > bestScore) {
                                bestScore = val;
                                bestSq = sq;
                                bestNextP = nextP;
                            }
                        }
                    }
                    break;
                }
            }
        }

        // Emergency fallback
        if (bestSq == -1) {
            System.err.println("[GodEngine] EMERGENCY: No move found, picking first available");
            for (int sq = 0; sq < 16; sq++) {
                if ((occupied & (1 << sq)) == 0) {
                    bestSq = sq;
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

        if (depth < 0) {
            return 0;
        }

        if (System.currentTimeMillis() - searchStartTime > searchTimeLimit) {
            return TIMEOUT_SENTINEL;
        }

        int alphaOrig = alpha;

        // Transposition table lookup
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

        // Terminal check
        if (available == 0) {
            return 0;
        }
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

        // TT move first
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

        // Generate remaining moves with piece safety heuristic
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
                        // Piece safety heuristic: fewer safe squares for opponent = higher priority
                        int opponentSafe = countSafeSquares(nTall, nRound, nSolid, nDark, nOcc, nextP);
                        priority -= opponentSafe * 100;

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

            int reduction = 0;
            if (!firstMove && i >= LMR_THRESHOLD && depth > LMR_DEPTH_THRESHOLD) {
                reduction = 1;
            }

            if (firstMove) {
                val = -godEngineNegamax(newDepth, -beta, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << nextP), nextP, ply + 1);
                firstMove = false;
            } else {
                val = -godEngineNegamax(newDepth - reduction, -alpha - 1, -alpha,
                    nTall, nRound, nSolid, nDark, nOcc,
                    available & ~(1 << nextP), nextP, ply + 1);

                if (val == TIMEOUT_SENTINEL || val == -TIMEOUT_SENTINEL) return TIMEOUT_SENTINEL;

                if (reduction > 0 && val > alpha) {
                    val = -godEngineNegamax(newDepth, -alpha - 1, -alpha,
                        nTall, nRound, nSolid, nDark, nOcc,
                        available & ~(1 << nextP), nextP, ply + 1);
                }

                if (val == TIMEOUT_SENTINEL || val == -TIMEOUT_SENTINEL) return TIMEOUT_SENTINEL;

                if (val > alpha && val < beta) {
                    val = -godEngineNegamax(newDepth, -beta, -alpha,
                        nTall, nRound, nSolid, nDark, nOcc,
                        available & ~(1 << nextP), nextP, ply + 1);
                }
            }

            if (val == TIMEOUT_SENTINEL || val == -TIMEOUT_SENTINEL) return TIMEOUT_SENTINEL;

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

    // ==================== STRANGLER EVALUATION (Leaf Eval) ====================
    /**
     * Enhanced evaluation with initiative and constraint pressure terms.
     * Score = -(W_SAFETY * S) - (W_TRAPS * T) + (W_INITIATIVE * I) - (W_CONSTRAINT * C)
     */
    private int evaluateStranglerFast(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        // Check if opponent can win immediately
        int ourDanger = getDangerSquares(tall, round, solid, dark, occupied, pieceId);
        int ourWinSquares = ourDanger & ~occupied & 0xFFFF;
        if (ourWinSquares != 0) {
            return 10000;
        }

        if (available == 0) {
            return 0;
        }

        int safety = 0;
        int traps = 0;
        int initiative = 0;      // Our safe placement options
        int constraintPressure = 0;  // Pieces with fewer safe squares for opponent

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        int emptySquares = ~occupied & 0xFFFF;
        for (int sq = 0; sq < 16; sq++) {
            if ((emptySquares & (1 << sq)) == 0) continue;

            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            for (int nextP = 0; nextP < 16; nextP++) {
                if ((available & (1 << nextP)) == 0) continue;

                if (canWinWithPieceFast(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                    continue;
                }

                safety++;

                // Count opponent's safe squares for this piece (constraint pressure)
                int oppSafe = countSafeSquares(nTall, nRound, nSolid, nDark, nOcc, nextP);
                constraintPressure += oppSafe;

                if (isTrapFast(nTall, nRound, nSolid, nDark, nOcc, available & ~(1 << nextP), nextP)) {
                    traps++;
                }
            }
        }

        // Count our initiative: how many safe squares WE have for each available piece
        for (int p = 0; p < 16; p++) {
            if ((available & (1 << p)) != 0) {
                initiative += countSafeSquares(tall, round, solid, dark, occupied, p);
            }
        }

        return (int)(-(W_SAFETY * safety) - (W_TRAPS * traps) + (W_INITIATIVE * initiative) - (W_CONSTRAINT * constraintPressure));
    }

    private boolean isTrapFast(int tall, int round, int solid, int dark, int occupied, int available, int pieceId) {
        int theirDanger = getDangerSquares(tall, round, solid, dark, occupied, pieceId);
        if ((theirDanger & ~occupied & 0xFFFF) != 0) {
            return false;
        }

        boolean pTall = isTall(pieceId);
        boolean pRound = isRound(pieceId);
        boolean pSolid = isSolid(pieceId);
        boolean pDark = isDark(pieceId);

        int emptySquares = ~occupied & 0xFFFF;
        for (int sq = 0; sq < 16; sq++) {
            if ((emptySquares & (1 << sq)) == 0) continue;

            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            for (int nextP = 0; nextP < 16; nextP++) {
                if ((available & (1 << nextP)) == 0) continue;
                if (!canWinWithPieceFast(nTall, nRound, nSolid, nDark, nOcc, nextP)) {
                    return false;
                }
            }
        }

        return true;
    }

    // ==================== OPENING PIECE SELECTION (1-ply Strangler eval) ====================
    private Move pickBestOpeningPiece(int tall, int round, int solid, int dark, int occupied, int available, Game game) {
        int bestPiece = -1;
        int bestScore = Integer.MAX_VALUE;  // We want to MINIMIZE opponent's best position

        // For each candidate piece we could give
        for (int candidateP = 0; candidateP < 16; candidateP++) {
            if ((available & (1 << candidateP)) == 0) continue;

            // Evaluate: what's the best the opponent can do with this piece?
            // Opponent places candidateP on any square, then picks a piece to give us.
            int opponentBest = Integer.MIN_VALUE;

            boolean cpTall = isTall(candidateP);
            boolean cpRound = isRound(candidateP);
            boolean cpSolid = isSolid(candidateP);
            boolean cpDark = isDark(candidateP);

            int newAvailable = available & ~(1 << candidateP);

            for (int sq = 0; sq < 16; sq++) {
                if ((occupied & (1 << sq)) != 0) continue;

                int bit = 1 << sq;
                int nTall = cpTall ? (tall | bit) : tall;
                int nRound = cpRound ? (round | bit) : round;
                int nSolid = cpSolid ? (solid | bit) : solid;
                int nDark = cpDark ? (dark | bit) : dark;
                int nOcc = occupied | bit;

                // If opponent wins by placing here, this piece is terrible for us
                if (checkWin(nTall, nRound, nSolid, nDark, nOcc)) {
                    opponentBest = 10000;
                    break;
                }

                // Opponent picks best piece to give us (minimize our position)
                for (int giveP = 0; giveP < 16; giveP++) {
                    if ((newAvailable & (1 << giveP)) == 0) continue;

                    // Evaluate from opponent's perspective using Strangler
                    int eval = evaluateStranglerFast(nTall, nRound, nSolid, nDark, nOcc,
                        newAvailable & ~(1 << giveP), giveP);
                    // Negate because evaluateStranglerFast evaluates for the side to move
                    if (-eval > opponentBest) {
                        opponentBest = -eval;
                    }
                }
            }

            // Pick the piece that gives opponent the WORST best outcome
            if (opponentBest < bestScore) {
                bestScore = opponentBest;
                bestPiece = candidateP;
            }
        }

        if (bestPiece >= 0) {
            return new Move(-1, null, game.getPieceById(bestPiece));
        }

        // Fallback
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
        return "ChokerJoker";
    }
}
