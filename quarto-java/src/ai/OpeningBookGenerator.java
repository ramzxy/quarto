package ai;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded offline generator that deep-searches early-game positions
 * and writes results to an opening book file for use by ChokerJokerStrategy.
 *
 * Usage: java -cp out ai.OpeningBookGenerator [--ply N] [--time MS] [--output FILE] [--resume] [--threads N]
 */
public class OpeningBookGenerator {

    private final ChokerJokerStrategy hashEngine = new ChokerJokerStrategy();
    private final Map<Long, String> results = new ConcurrentHashMap<>();
    private final Set<Long> doneHashes = ConcurrentHashMap.newKeySet();
    private int timeBudgetMs;
    private String outputFile;
    private int numThreads;

    public static void main(String[] args) throws Exception {
        int ply = 2;
        int timeMs = 60000;
        String output = "dist/opening_book.dat";
        boolean resume = false;
        int threads = Runtime.getRuntime().availableProcessors();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ply":     ply = Integer.parseInt(args[++i]); break;
                case "--time":    timeMs = Integer.parseInt(args[++i]); break;
                case "--output":  output = args[++i]; break;
                case "--resume":  resume = true; break;
                case "--threads": threads = Integer.parseInt(args[++i]); break;
            }
        }

        OpeningBookGenerator gen = new OpeningBookGenerator();
        gen.timeBudgetMs = timeMs;
        gen.outputFile = output;
        gen.numThreads = threads;

        if (resume) {
            gen.loadExisting();
        }

        System.err.println("[Gen] ply=" + ply + " time=" + timeMs + "ms threads=" + threads + " output=" + output + " resume=" + resume);
        gen.generate(ply);
        gen.writeAll();
        System.err.println("[Gen] Done. Total entries: " + gen.results.size());
    }

    private void loadExisting() {
        try (BufferedReader br = new BufferedReader(new FileReader(outputFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 4) continue;
                long hash = Long.parseUnsignedLong(parts[0], 16);
                doneHashes.add(hash);
                results.put(hash, line);
            }
            System.err.println("[Gen] Resumed with " + doneHashes.size() + " existing entries");
        } catch (IOException e) {
            System.err.println("[Gen] No existing file to resume from");
        }
    }

    private void generate(int maxPly) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);

        List<int[]> currentLevel = new ArrayList<>();
        for (int p = 0; p < 16; p++) {
            int available = 0xFFFF & ~(1 << p);
            currentLevel.add(new int[]{0, 0, 0, 0, 0, available, p});
        }

        for (int depth = 0; depth <= maxPly; depth++) {
            System.err.println("[Gen] === Level " + depth + ": " + currentLevel.size() + " positions to enumerate ===");

                // Deduplicate by canonical hash (single-threaded, fast)
            Map<Long, int[]> dedupLevel = new LinkedHashMap<>();
            for (int[] state : currentLevel) {
                long packed = hashEngine.computeCanonicalHash(state[0], state[1], state[2], state[3], state[4], state[6]);
                long hash = packed & 0xFFFFFFFFFFFFFFE0L;
                
                // We must process even if doneHashes.contains(hash) so we can generate children.
                // We only skip if we've already queued this specific canonical hash in this level.
                if (!dedupLevel.containsKey(hash)) {
                    dedupLevel.put(hash, state);
                }
            }

            System.err.println("[Gen] After dedup: " + dedupLevel.size() + " unique positions to process");

            if (dedupLevel.isEmpty()) {
                currentLevel = Collections.emptyList();
                break;
            }

            // Prepare work items
            List<Map.Entry<Long, int[]>> work = new ArrayList<>(dedupLevel.entrySet());
            int totalWork = work.size();
            AtomicInteger progress = new AtomicInteger(0);

            // Collect next-level states from all threads
            List<Future<List<int[]>>> futures = new ArrayList<>();
            boolean generateNext = (depth < maxPly);

            for (Map.Entry<Long, int[]> entry : work) {
                long hash = entry.getKey();
                int[] state = entry.getValue();

                futures.add(pool.submit(() -> {
                    // Each thread gets its own engine instance (mutable state is per-instance)
                    ChokerJokerStrategy threadEngine = new ChokerJokerStrategy();

                    int tall = state[0], round = state[1], solid = state[2], dark = state[3];
                    int occupied = state[4], available = state[5], pieceId = state[6];

                    int score = 0;
                    int bestSq = -1;
                    int bestNextP = -1;
                    
                    boolean alreadyDone = doneHashes.contains(hash);

                    if (!alreadyDone) {
                        threadEngine.clearSearchTables();
                        threadEngine.searchStartTime = System.currentTimeMillis();
                        threadEngine.searchTimeLimit = timeBudgetMs;

                        long searchResult = threadEngine.godEngineSearch(tall, round, solid, dark, occupied, available, pieceId);

                        score = (int)(searchResult >> 32);
                        int movePacked = (int)searchResult;
                        bestSq = (movePacked >> 16) & 0xFFFF;
                        if (bestSq == 0xFFFF) bestSq = -1;
                        bestNextP = movePacked & 0xFFFF;
                        if (bestNextP == 0xFFFF) bestNextP = -1;

                        // Convert bestSq to canonical coordinates
                        long canonPacked = threadEngine.computeCanonicalHash(tall, round, solid, dark, occupied, pieceId);
                        int canonSym = (int)(canonPacked & 0x1F);
                        int canonSq = bestSq >= 0 ? ChokerJokerStrategy.ALL_SYMMETRIES[canonSym][bestSq] : -1;

                        String line = String.format("%016X %d %d %d %d",
                            hash, canonSq, bestNextP, score, Integer.bitCount(occupied));
                        results.put(hash, line);
                        doneHashes.add(hash);
                    } else {
                        // If already done, we still need to know the bestSq/move to generate children?
                        // Actually, godEngineSearch finds the BEST move.
                        // If we want to explore ALL children, we usually iterate all legal moves.
                        // BUT, generateNextLevel uses the *current* state + *all legal moves* loop?
                        // Let's check generateNextLevel below.
                        // Wait, looking at generateNextLevel source:
                        // It iterates `for (int sq = 0; sq < 16; sq++) ... for (int nextP = 0...)`
                        // It generates ALL successors. It does NOT depend on the 'best move' found by search.
                        // In minimax, we visit all children. 
                        // However, line 154 says: `if (generateNext && bestSq >= 0)`
                        // The original code ONLY generated children if bestSq >= 0 (meaning not terminal/lost?).
                        // If the position is already in book, we might not know bestSq unless we parse it from the loaded line.
                        
                        // Let's recover bestSq from the loaded result if possible, or just assume we should generate children 
                        // unless it's a known terminal node.
                        // The loaded line format: "HASH SQ NEXTP SCORE COUNT"
                        // SQ is the canonical best move.
                        
                        String stored = results.get(hash);
                        if (stored != null) {
                            String[] parts = stored.split("\\s+");
                            if (parts.length >= 2) {
                                int canonSq = Integer.parseInt(parts[1]);
                                // We don't easily know bestSq (raw) from canonSq without reverse mapping or re-deriving sym.
                                // However, `generateNextLevel` iterates raw moves. 
                                // Actually, does `generateNextLevel` generate *only the best path* or *all paths*?
                                // Line 192: loops over all sq. Line 204: loops over all nextP.
                                // It generates ALL children.
                                // So why did the original code check `bestSq >= 0`?
                                // Probably to avoid generating children for terminal positions where no move is possible?
                                // Or maybe just "if we found a valid move".
                                // If the position was solved/stored, it implies it wasn't terminal-with-no-moves unless score indicates so?
                                
                                // Safe bet: if it's in the book, it's not a stalemate/error, so valid moves exist (unless it saves terminal states?).
                                // Let's just set bestSq = 0 (or any non-negative) to trigger generation.
                                bestSq = 0; 
                            }
                        }
                    }

                    int done = progress.incrementAndGet();
                    if (!alreadyDone) {
                        System.err.println("[Gen] [" + done + "/" + totalWork + "] pieces=" + Integer.bitCount(occupied)
                            + " piece=" + pieceId + " => sq=" + bestSq + " nextP=" + bestNextP + " score=" + score);
                    } else {
                         // Optional: log skip
                         // System.err.println("[Gen] [" + done + "/" + totalWork + "] Skipped search for " + String.format("%016X", hash));
                    }
                    
                    if (done % 20 == 0) {
                        writeAll();
                    }

                    // Generate next level positions
                    // We allow generation if we found a move OR if we skipped search (implying valid node).
                    // We need to valid check: if the game is actually over, generateNextLevel returns empty logic naturally?
                    // generateNextLevel checks checkWin at start of move loop.
                    if (generateNext) {
                         // We pass effectively "true" for generation if we found a move or are skipping.
                         // But we should rely on generateNextLevel to handle terminal states?
                         // The original code guarded with `bestSq >= 0`.
                         // If we skip search, `bestSq` is -1.
                         // Let's force it to 0 if we skipped, assuming non-terminal.
                         if (alreadyDone) bestSq = 1; 
                         
                         if (bestSq >= 0) {
                             return generateNextLevel(threadEngine, tall, round, solid, dark, occupied, available, pieceId);
                         }
                    }
                    return Collections.<int[]>emptyList();
                }));
            }
            // End of changes to worker submission loop

            // Collect results
            List<int[]> nextLevel = new ArrayList<>();
            for (Future<List<int[]>> f : futures) {
                try {
                    nextLevel.addAll(f.get());
                } catch (ExecutionException e) {
                    System.err.println("[Gen] ERROR in worker: " + e.getCause().getMessage());
                    e.getCause().printStackTrace(System.err);
                }
            }

            currentLevel = nextLevel;
            writeAll();
            System.err.println("[Gen] Level " + depth + " complete. Next level candidates: " + currentLevel.size());

            if (currentLevel.isEmpty()) break;
        }

        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.HOURS);
    }

    private List<int[]> generateNextLevel(ChokerJokerStrategy eng,
                                          int tall, int round, int solid, int dark,
                                          int occupied, int available, int pieceId) {
        List<int[]> next = new ArrayList<>();
        boolean pTall = ChokerJokerStrategy.isTall(pieceId);
        boolean pRound = ChokerJokerStrategy.isRound(pieceId);
        boolean pSolid = ChokerJokerStrategy.isSolid(pieceId);
        boolean pDark = ChokerJokerStrategy.isDark(pieceId);

        for (int sq = 0; sq < 16; sq++) {
            if ((occupied & (1 << sq)) != 0) continue;

            int bit = 1 << sq;
            int nTall = pTall ? (tall | bit) : tall;
            int nRound = pRound ? (round | bit) : round;
            int nSolid = pSolid ? (solid | bit) : solid;
            int nDark = pDark ? (dark | bit) : dark;
            int nOcc = occupied | bit;

            if (eng.checkWin(nTall, nRound, nSolid, nDark, nOcc)) continue;

            for (int nextP = 0; nextP < 16; nextP++) {
                if ((available & (1 << nextP)) == 0) continue;
                if (eng.canWinWithPieceFast(nTall, nRound, nSolid, nDark, nOcc, nextP)) continue;

                int oppAvailable = available & ~(1 << nextP);
                boolean opTall = ChokerJokerStrategy.isTall(nextP);
                boolean opRound = ChokerJokerStrategy.isRound(nextP);
                boolean opSolid = ChokerJokerStrategy.isSolid(nextP);
                boolean opDark = ChokerJokerStrategy.isDark(nextP);

                for (int opSq = 0; opSq < 16; opSq++) {
                    if ((nOcc & (1 << opSq)) != 0) continue;

                    int opBit = 1 << opSq;
                    int nnTall = opTall ? (nTall | opBit) : nTall;
                    int nnRound = opRound ? (nRound | opBit) : nRound;
                    int nnSolid = opSolid ? (nSolid | opBit) : nSolid;
                    int nnDark = opDark ? (nDark | opBit) : nDark;
                    int nnOcc = nOcc | opBit;

                    if (eng.checkWin(nnTall, nnRound, nnSolid, nnDark, nnOcc)) continue;

                    for (int giveP = 0; giveP < 16; giveP++) {
                        if ((oppAvailable & (1 << giveP)) == 0) continue;
                        int newAvail = oppAvailable & ~(1 << giveP);
                        next.add(new int[]{nnTall, nnRound, nnSolid, nnDark, nnOcc, newAvail, giveP});
                    }
                }
            }
        }
        return next;
    }

    private synchronized void writeAll() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputFile))) {
            pw.println("# Opening book: hash sq nextP score depth");
            for (String line : results.values()) {
                pw.println(line);
            }
            pw.flush();
        } catch (IOException e) {
            System.err.println("[Gen] ERROR writing file: " + e.getMessage());
        }
    }
}
