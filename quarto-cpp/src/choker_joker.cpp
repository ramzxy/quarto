#include "choker_joker.hpp"

#include <algorithm>
#include <cstring>
#include <cstdio>

namespace quarto {

ChokerJoker::ChokerJoker() {
    Zobrist::init();
    Symmetry::init();
    clear_search_tables();
}

// ==================== HELPERS ====================

bool ChokerJoker::check_win(int tall, int round, int solid, int dark, int occupied) {
    for (int i = 0; i < 10; i++) {
        uint16_t mask = LINES[i];
        if ((occupied & mask) != mask) continue;

        int t = tall & mask;
        if (t == mask || t == 0) return true;
        int r = round & mask;
        if (r == mask || r == 0) return true;
        int s = solid & mask;
        if (s == mask || s == 0) return true;
        int d = dark & mask;
        if (d == mask || d == 0) return true;
    }
    return false;
}

int ChokerJoker::compute_danger_attr(int attr_bb, bool attr_val, int occupied) {
    int danger = 0;
    for (int i = 0; i < 10; i++) {
        uint16_t mask = LINES[i];
        int line_occ = occupied & mask;
        if (popcount(line_occ) != 3) continue;

        int empty_bit = mask & ~occupied;
        int line_attr = attr_bb & line_occ;
        bool all_have = (line_attr == line_occ);
        bool none_have = (line_attr == 0);

        if ((all_have && attr_val) || (none_have && !attr_val)) {
            danger |= empty_bit;
        }
    }
    return danger;
}

int ChokerJoker::get_danger_squares(int tall, int round, int solid, int dark, int occupied, int piece_id) {
    int danger = 0;
    danger |= compute_danger_attr(tall,  piece_is_tall(piece_id),  occupied);
    danger |= compute_danger_attr(round, piece_is_round(piece_id), occupied);
    danger |= compute_danger_attr(solid, piece_is_solid(piece_id), occupied);
    danger |= compute_danger_attr(dark,  piece_is_dark(piece_id),  occupied);
    return danger;
}

bool ChokerJoker::can_win_with_piece_fast(int tall, int round, int solid, int dark, int occupied, int piece_id) {
    int danger = get_danger_squares(tall, round, solid, dark, occupied, piece_id);
    return (danger & ~occupied & 0xFFFF) != 0;
}

int ChokerJoker::get_god_threshold(int time_remaining_ms) {
    if (time_remaining_ms > 10000) return 11;
    if (time_remaining_ms > 5000) return 10;
    if (time_remaining_ms > 2000) return 9;
    return 8;
}

bool ChokerJoker::is_timeout() const {
    if (stop_flag_ && stop_flag_->load(std::memory_order_relaxed)) return true;
    return std::chrono::steady_clock::now() >= deadline_;
}

void ChokerJoker::update_killer(int ply, int sq, int next_p) {
    if (ply >= MAX_DEPTH) return;
    int packed = (sq << 4) | (next_p & 0xF);
    if (killers_[ply][0] != packed) {
        killers_[ply][1] = killers_[ply][0];
        killers_[ply][0] = packed;
    }
}

void ChokerJoker::update_history(int sq, int next_p, int depth) {
    if (sq >= 0 && sq < 16 && next_p >= 0 && next_p < 16) {
        history_[sq][next_p] += depth * depth;
        if (history_[sq][next_p] > 1000000) {
            for (int i = 0; i < 16; i++)
                for (int j = 0; j < 16; j++)
                    history_[i][j] /= 2;
        }
    }
}

void ChokerJoker::clear_search_tables() {
    for (int i = 0; i < MAX_DEPTH; i++) {
        killers_[i][0] = -1;
        killers_[i][1] = -1;
    }
}

// ==================== MAIN ENTRY POINT ====================

Move ChokerJoker::compute_move(const BoardState& board, int piece_to_place,
                                int time_budget_ms) {
    nodes_ = 0;
    deadline_ = std::chrono::steady_clock::now() +
                std::chrono::milliseconds(time_budget_ms);

    tt_.new_generation();
    clear_search_tables();

    int empty = board.empty_count();
    int threshold = get_god_threshold(time_budget_ms);

    if (empty <= threshold) {
        return god_search(board, piece_to_place);
    } else {
        int max_depth = std::min(5, empty - threshold + 1);
        return strangler_search(board, piece_to_place, max_depth);
    }
}

int ChokerJoker::compute_first_move(const BoardState& board, int time_budget_ms) {
    (void)time_budget_ms;
    uint16_t avail = board.available;
    // Give a neutral piece
    if (avail & (1 << 7)) return 7;
    if (avail & (1 << 8)) return 8;
    return ctz(avail);
}

// ==================== STRANGLER EVALUATION ====================

int ChokerJoker::evaluate_strangler(int tall, int round, int solid, int dark, int occupied,
                                     int available, int next_piece) {
    int safety = 0;
    int traps = 0;

    int danger = get_danger_squares(tall, round, solid, dark, occupied, next_piece);
    int safe_squares = (~occupied & 0xFFFF) & ~danger;

    safety = popcount(safe_squares);

    // Check traps: safe squares that lead to forced loss at depth 2
    int safe_copy = safe_squares;
    while (safe_copy) {
        int sq = ctz(safe_copy);
        safe_copy &= safe_copy - 1;

        int bit = 1 << sq;
        int n_tall  = piece_is_tall(next_piece)  ? (tall | bit) : tall;
        int n_round = piece_is_round(next_piece) ? (round | bit) : round;
        int n_solid = piece_is_solid(next_piece) ? (solid | bit) : solid;
        int n_dark  = piece_is_dark(next_piece)  ? (dark | bit) : dark;
        int n_occ = occupied | bit;

        // For each piece we could give after this
        int avail2 = available & ~(1 << next_piece);
        while (avail2) {
            int p2 = ctz(avail2);
            avail2 &= avail2 - 1;

            // If ALL squares lose for opponent with this piece, it's a trap
            int danger2 = get_danger_squares(n_tall, n_round, n_solid, n_dark, n_occ, p2);
            int safe2 = (~n_occ & 0xFFFF) & ~danger2;

            if (safe2 == 0 && (~n_occ & 0xFFFF) != 0) {
                traps++;
                break;
            }
        }
    }

    return (int)(-(W_SAFETY * safety) - (W_TRAPS * traps));
}

// ==================== STRANGLER SEARCH ====================

Move ChokerJoker::strangler_search(const BoardState& b, int piece, int depth_limit) {
    int tall = b.tall, round = b.round, solid = b.solid, dark = b.dark;
    int occupied = b.occupied;
    int available = b.available & ~(1u << piece);

    Move best_move;
    int best_score = -INF;

    bool p_tall  = piece_is_tall(piece);
    bool p_round = piece_is_round(piece);
    bool p_solid = piece_is_solid(piece);
    bool p_dark  = piece_is_dark(piece);

    for (int depth = 1; depth <= depth_limit; depth++) {
        if (is_timeout()) break;

        int iter_best_score = -INF;
        Move iter_best;

        int empty = ~occupied & 0xFFFF;
        while (empty) {
            int sq = ctz(empty);
            empty &= empty - 1;

            int bit = 1 << sq;
            int n_tall  = p_tall  ? (tall | bit) : tall;
            int n_round = p_round ? (round | bit) : round;
            int n_solid = p_solid ? (solid | bit) : solid;
            int n_dark  = p_dark  ? (dark | bit) : dark;
            int n_occ = occupied | bit;

            if (check_win(n_tall, n_round, n_solid, n_dark, n_occ)) {
                return Move(sq, CLAIM_QUARTO, WIN);
            }

            if (available == 0) {
                return Move(sq, FINAL_PIECE_NO_CLAIM, 0);
            }

            int avail = available;
            while (avail) {
                int next_p = ctz(avail);
                avail &= avail - 1;

                if (can_win_with_piece_fast(n_tall, n_round, n_solid, n_dark, n_occ, next_p)) {
                    continue;
                }

                int score = -strangler_negamax(
                    n_tall, n_round, n_solid, n_dark, n_occ,
                    available & ~(1 << next_p), next_p,
                    depth - 1, -INF, -iter_best_score, 1);

                if (score > iter_best_score) {
                    iter_best_score = score;
                    iter_best = Move(sq, next_p, score);
                }

                if (is_timeout()) break;
            }
            if (is_timeout()) break;
        }

        if (!is_timeout()) {
            best_move = iter_best;
            best_score = iter_best_score;
        }

        if (is_timeout() || best_score >= WIN - 100) break;
    }

    // Fallback
    if (best_move.square < 0) {
        int empty = ~occupied & 0xFFFF;
        if (empty) {
            int sq = ctz(empty);
            int next_p = available ? ctz(available) : (int)FINAL_PIECE_NO_CLAIM;
            best_move = Move(sq, next_p, (int16_t)-32000);
        }
    }

    return best_move;
}

int ChokerJoker::strangler_negamax(int tall, int round, int solid, int dark, int occupied,
                                    int available, int piece, int depth,
                                    int alpha, int beta, int ply) {
    nodes_++;

    if (depth <= 0) {
        return evaluate_strangler(tall, round, solid, dark, occupied, available, piece);
    }

    if (is_timeout()) return 0;

    bool p_tall  = piece_is_tall(piece);
    bool p_round = piece_is_round(piece);
    bool p_solid = piece_is_solid(piece);
    bool p_dark  = piece_is_dark(piece);

    int best = -INF;
    int empty = ~occupied & 0xFFFF;

    while (empty) {
        int sq = ctz(empty);
        empty &= empty - 1;

        int bit = 1 << sq;
        int n_tall  = p_tall  ? (tall | bit) : tall;
        int n_round = p_round ? (round | bit) : round;
        int n_solid = p_solid ? (solid | bit) : solid;
        int n_dark  = p_dark  ? (dark | bit) : dark;
        int n_occ = occupied | bit;

        if (check_win(n_tall, n_round, n_solid, n_dark, n_occ)) {
            return WIN + depth;
        }

        if (available == 0) {
            return std::max(best, 0);
        }

        int avail = available;
        while (avail) {
            int next_p = ctz(avail);
            avail &= avail - 1;

            int score = -strangler_negamax(
                n_tall, n_round, n_solid, n_dark, n_occ,
                available & ~(1 << next_p), next_p,
                depth - 1, -beta, -alpha, ply + 1);

            if (score > best) {
                best = score;
                if (score > alpha) alpha = score;
                if (alpha >= beta) return best;
            }
        }
    }

    return best == -INF ? 0 : best;
}

// ==================== GOD ENGINE ====================

Move ChokerJoker::god_search(const BoardState& b, int piece) {
    int tall = b.tall, round = b.round, solid = b.solid, dark = b.dark;
    int occupied = b.occupied;
    int available = b.available & ~(1u << piece);

    int empty_count = popcount(~occupied & 0xFFFF);
    int max_depth = empty_count * 2 + 2;

    int best_score = -INF;
    int best_sq = -1;
    int best_next_p = -1;
    int alpha = -INF;
    int beta = INF;

    bool p_tall  = piece_is_tall(piece);
    bool p_round = piece_is_round(piece);
    bool p_solid = piece_is_solid(piece);
    bool p_dark  = piece_is_dark(piece);

    struct MoveEntry { int sq; int next_p; int priority; };
    MoveEntry moves[256];
    int move_count = 0;

    int empty = ~occupied & 0xFFFF;
    while (empty) {
        int sq = ctz(empty);
        empty &= empty - 1;

        int bit = 1 << sq;
        int n_tall  = p_tall  ? (tall | bit) : tall;
        int n_round = p_round ? (round | bit) : round;
        int n_solid = p_solid ? (solid | bit) : solid;
        int n_dark  = p_dark  ? (dark | bit) : dark;
        int n_occ = occupied | bit;

        if (check_win(n_tall, n_round, n_solid, n_dark, n_occ)) {
            return Move(sq, CLAIM_QUARTO, WIN);
        }

        if (available == 0) {
            return Move(sq, FINAL_PIECE_NO_CLAIM, 0);
        }

        int avail = available;
        while (avail) {
            int next_p = ctz(avail);
            avail &= avail - 1;

            if (can_win_with_piece_fast(n_tall, n_round, n_solid, n_dark, n_occ, next_p)) {
                continue;
            }

            int priority = history_[sq][next_p];
            int packed = (sq << 4) | next_p;
            if (killers_[max_depth % MAX_DEPTH][0] == packed) priority += 50000;
            else if (killers_[max_depth % MAX_DEPTH][1] == packed) priority += 40000;

            moves[move_count++] = {sq, next_p, priority};
        }
    }

    // Sort by priority
    for (int i = 1; i < move_count; i++) {
        MoveEntry key = moves[i];
        int j = i - 1;
        while (j >= 0 && moves[j].priority < key.priority) {
            moves[j + 1] = moves[j];
            j--;
        }
        moves[j + 1] = key;
    }

    // PVS search
    bool first_move = true;
    for (int i = 0; i < move_count; i++) {
        if (is_timeout()) break;

        int sq = moves[i].sq;
        int next_p = moves[i].next_p;
        int bit = 1 << sq;
        int n_tall  = p_tall  ? (tall | bit) : tall;
        int n_round = p_round ? (round | bit) : round;
        int n_solid = p_solid ? (solid | bit) : solid;
        int n_dark  = p_dark  ? (dark | bit) : dark;
        int n_occ = occupied | bit;

        int val;
        if (first_move) {
            val = -god_negamax(n_tall, n_round, n_solid, n_dark, n_occ,
                               available & ~(1 << next_p), next_p,
                               max_depth - 1, -beta, -alpha, 1);
            first_move = false;
        } else {
            val = -god_negamax(n_tall, n_round, n_solid, n_dark, n_occ,
                               available & ~(1 << next_p), next_p,
                               max_depth - 1, -alpha - 1, -alpha, 1);
            if (val > alpha && val < beta) {
                val = -god_negamax(n_tall, n_round, n_solid, n_dark, n_occ,
                                   available & ~(1 << next_p), next_p,
                                   max_depth - 1, -beta, -alpha, 1);
            }
        }

        int new_empty = popcount(~n_occ & 0xFFFF);
        if (val == 0 && (new_empty % 2) == 0) {
            val -= 10;
        }

        if (val > best_score) {
            best_score = val;
            best_sq = sq;
            best_next_p = next_p;
        }
        if (best_score > alpha) {
            alpha = best_score;
            if (alpha >= beta) {
                update_killer(max_depth % MAX_DEPTH, sq, next_p);
                update_history(sq, next_p, max_depth);
                break;
            }
        }
    }

    // Fallback: try poison pieces
    if (best_sq == -1) {
        empty = ~occupied & 0xFFFF;
        while (empty) {
            int sq = ctz(empty);
            empty &= empty - 1;

            int avail = available;
            while (avail) {
                int next_p = ctz(avail);
                avail &= avail - 1;

                int bit = 1 << sq;
                int n_tall  = p_tall  ? (tall | bit) : tall;
                int n_round = p_round ? (round | bit) : round;
                int n_solid = p_solid ? (solid | bit) : solid;
                int n_dark  = p_dark  ? (dark | bit) : dark;
                int n_occ = occupied | bit;

                int val = -god_negamax(n_tall, n_round, n_solid, n_dark, n_occ,
                                       available & ~(1 << next_p), next_p,
                                       max_depth - 1, -beta, -alpha, 1);

                if (val > best_score) {
                    best_score = val;
                    best_sq = sq;
                    best_next_p = next_p;
                }
            }
            break;
        }
    }

    // Emergency fallback
    if (best_sq == -1) {
        empty = ~occupied & 0xFFFF;
        if (empty) {
            best_sq = ctz(empty);
            best_next_p = available ? ctz(available) : -1;
        }
    }

    return Move(best_sq, best_next_p >= 0 ? best_next_p : FINAL_PIECE_NO_CLAIM, best_score);
}

int ChokerJoker::god_negamax(int tall, int round, int solid, int dark, int occupied,
                              int available, int piece, int depth,
                              int alpha, int beta, int ply) {
    nodes_++;

    if (depth < 0) return 0;
    if (is_timeout()) return 0;

    int alpha_orig = alpha;

    // TT lookup using canonical hash
    BoardState tmp_b;
    tmp_b.tall = tall; tmp_b.round = round; tmp_b.solid = solid; tmp_b.dark = dark;
    tmp_b.occupied = occupied; tmp_b.available = available;
    uint64_t hash = Symmetry::canonical_hash(tmp_b, piece);

    Move tt_move;
    int tt_score;
    if (tt_.probe(hash, depth, alpha, beta, tt_score, tt_move)) {
        return tt_score;
    }

    // Terminal check
    if (depth == 0 || available == 0) {
        return 0;
    }

    int best = -INF;
    int best_sq = -1;
    int best_next_p = -1;

    bool p_tall  = piece_is_tall(piece);
    bool p_round = piece_is_round(piece);
    bool p_solid = piece_is_solid(piece);
    bool p_dark  = piece_is_dark(piece);

    struct MoveEntry { int sq; int next_p; int priority; };
    MoveEntry moves[256];
    int move_count = 0;

    // TT move first
    if (tt_move.square >= 0 && ((~occupied & 0xFFFF) & (1 << tt_move.square))) {
        int sq = tt_move.square;
        int bit = 1 << sq;
        int n_tall  = p_tall  ? (tall | bit) : tall;
        int n_round = p_round ? (round | bit) : round;
        int n_solid = p_solid ? (solid | bit) : solid;
        int n_dark  = p_dark  ? (dark | bit) : dark;
        int n_occ = occupied | bit;

        if (check_win(n_tall, n_round, n_solid, n_dark, n_occ)) {
            tt_.store(hash, depth, WIN + depth, TT_EXACT, sq, CLAIM_QUARTO);
            return WIN + depth;
        }

        if (tt_move.piece >= 0 && (available & (1 << tt_move.piece))) {
            moves[move_count++] = {sq, (int)tt_move.piece, 1000000};
        }
    }

    // Remaining moves
    int empty = ~occupied & 0xFFFF;
    while (empty) {
        int sq = ctz(empty);
        empty &= empty - 1;

        int bit = 1 << sq;
        int n_tall  = p_tall  ? (tall | bit) : tall;
        int n_round = p_round ? (round | bit) : round;
        int n_solid = p_solid ? (solid | bit) : solid;
        int n_dark  = p_dark  ? (dark | bit) : dark;
        int n_occ = occupied | bit;

        if (check_win(n_tall, n_round, n_solid, n_dark, n_occ)) {
            tt_.store(hash, depth, WIN + depth, TT_EXACT, sq, CLAIM_QUARTO);
            return WIN + depth;
        }

        if (available == 0) {
            if (0 > best) {
                best = 0;
                best_sq = sq;
                best_next_p = -1;
            }
            continue;
        }

        int avail = available;
        while (avail) {
            int next_p = ctz(avail);
            avail &= avail - 1;

            if (sq == tt_move.square && next_p == tt_move.piece) continue;

            int priority = history_[sq][next_p];
            int packed = (sq << 4) | next_p;
            if (ply < MAX_DEPTH) {
                if (killers_[ply][0] == packed) priority += 50000;
                else if (killers_[ply][1] == packed) priority += 40000;
            }

            moves[move_count++] = {sq, next_p, priority};
        }
    }

    // Sort by priority
    for (int i = 1; i < move_count; i++) {
        MoveEntry key = moves[i];
        int j = i - 1;
        while (j >= 0 && moves[j].priority < key.priority) {
            moves[j + 1] = moves[j];
            j--;
        }
        moves[j + 1] = key;
    }

    // PVS + LMR search
    bool first_move = true;
    for (int i = 0; i < move_count && alpha < beta; i++) {
        int sq = moves[i].sq;
        int next_p = moves[i].next_p;
        int bit = 1 << sq;
        int n_tall  = p_tall  ? (tall | bit) : tall;
        int n_round = p_round ? (round | bit) : round;
        int n_solid = p_solid ? (solid | bit) : solid;
        int n_dark  = p_dark  ? (dark | bit) : dark;
        int n_occ = occupied | bit;

        int new_depth = depth - 1;
        int val;

        int reduction = 0;
        if (!first_move && i >= LMR_THRESHOLD && depth > LMR_DEPTH_THRESHOLD) {
            reduction = 1;
        }

        if (first_move) {
            val = -god_negamax(n_tall, n_round, n_solid, n_dark, n_occ,
                               available & ~(1 << next_p), next_p,
                               new_depth, -beta, -alpha, ply + 1);
            first_move = false;
        } else {
            val = -god_negamax(n_tall, n_round, n_solid, n_dark, n_occ,
                               available & ~(1 << next_p), next_p,
                               new_depth - reduction, -alpha - 1, -alpha, ply + 1);

            if (reduction > 0 && val > alpha) {
                val = -god_negamax(n_tall, n_round, n_solid, n_dark, n_occ,
                                   available & ~(1 << next_p), next_p,
                                   new_depth, -alpha - 1, -alpha, ply + 1);
            }

            if (val > alpha && val < beta) {
                val = -god_negamax(n_tall, n_round, n_solid, n_dark, n_occ,
                                   available & ~(1 << next_p), next_p,
                                   new_depth, -beta, -alpha, ply + 1);
            }
        }

        if (val > best) {
            best = val;
            best_sq = sq;
            best_next_p = next_p;
        }
        if (best > alpha) {
            alpha = best;
            if (alpha >= beta) {
                if (ply < MAX_DEPTH) {
                    update_killer(ply, sq, next_p);
                }
                update_history(sq, next_p, depth);
                break;
            }
        }
    }

    if (best == -INF) best = 0;

    TTFlag flag;
    if (best <= alpha_orig) {
        flag = TT_UPPER;
    } else if (best >= beta) {
        flag = TT_LOWER;
    } else {
        flag = TT_EXACT;
    }

    tt_.store(hash, depth, best, flag,
              best_sq >= 0 ? best_sq : 0,
              best_next_p >= 0 ? best_next_p : 0);

    return best;
}

} // namespace quarto
