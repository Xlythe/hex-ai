package com.hex.ai;

import com.hex.core.AI;
import com.hex.core.Game;
import com.hex.core.GameAction;
import com.hex.core.Point;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

/** A bounded Monte Carlo tree search bot with connection-path move ordering. */
public final class TreeGameAI extends AI {
    private static final long serialVersionUID = 1L;
    private static final int[] DX = {-1, 1, 0, 0, -1, 1};
    private static final int[] DY = {0, 0, -1, 1, 1, -1};
    private static final int INF = 100000;
    private final int simulations;

    public TreeGameAI(int team, int simulations) {
        super(team);
        if (team != 1 && team != 2) throw new IllegalArgumentException("Invalid team");
        if (simulations < 1) throw new IllegalArgumentException("Simulation count must be positive");
        this.simulations = simulations;
    }

    @Override
    public String getAIType() {
        return "Tree";
    }

    @Override
    public Serializable getSaveState() {
        return null;
    }

    @Override
    public void setSaveState(Serializable state) {
        // The search reconstructs its position from the game on every turn.
    }

    @Override
    public void win() {}

    @Override
    public void lose(Game game) {}

    @Override
    public void getPlayerTurn(Game game) {
        super.getPlayerTurn(game);
        int size = game.gameOptions.gridSize;
        byte[] board = new byte[size * size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) board[x * size + y] = game.gamePieces[x][y].getTeam();
        }
        boolean canSwap = game.gameOptions.swap && game.getMoveNumber() == 2 && team == 2;
        int move = choose(board, size, team, canSwap, simulations, System.nanoTime(), this);
        if (move < 0 || getSkipMove()) return;
        Point point;
        if (move == board.length) {
            int opening = openingStone(board);
            point = new Point(opening / size, opening % size);
        } else {
            point = new Point(move / size, move % size);
        }
        GameAction.makeMove(this, point, game);
    }

    /** Returns a cell index, or size*size for the pie-rule swap. */
    public static int choose(byte[] position, int size, int player, boolean canSwap,
                             int simulations, long seed) {
        return choose(position, size, player, canSwap, simulations, seed, null);
    }

    private static int choose(byte[] position, int size, int player, boolean canSwap,
                              int simulations, long seed, TreeGameAI owner) {
        if (size < 2 || size > 19 || position.length != size * size || (player != 1 && player != 2)
                || simulations < 1) throw new IllegalArgumentException("Invalid search position");
        byte[] board = position.clone();
        int empty = 0;
        for (byte value : board) {
            if (value == 0) empty++;
            else if (value != 1 && value != 2) throw new IllegalArgumentException("Invalid stone");
        }
        if (empty == 0) return -1;
        if (empty == board.length) return (size / 2) * size + size / 2;
        if (canSwap && (player != 2 || empty != board.length - 1)) {
            throw new IllegalArgumentException("Swap is only legal after the opening move");
        }

        int win = immediateWin(board, size, player);
        if (win >= 0) return win;
        int threat = immediateWin(board, size, 3 - player);
        if (threat >= 0) return threat;

        double[] priority = priorities(board, size, player);
        int[] order = orderedMoves(board, priority, canSwap);
        Node root = new Node(null, -1, 3 - player, order);
        Random random = new Random(seed);
        for (int sample = 0; sample < simulations; sample++) {
            if (owner != null && (sample & 31) == 0 && owner.getSkipMove()) break;
            byte[] state = board.clone();
            Node node = root;
            int turn = player;
            while (true) {
                int capacity = Math.min(node.moves.length, 2 + (int) Math.sqrt(node.visits));
                if (node.nextMove < node.moves.length && node.children.size() < capacity) {
                    int move = node.moves[node.nextMove++];
                    apply(state, size, move, turn);
                    Node child = new Node(node, move, turn,
                            orderedMoves(state, priority, false));
                    node.children.add(child);
                    node = child;
                    turn = 3 - turn;
                    break;
                }
                if (node.children.isEmpty()) break;
                node = select(node, random);
                apply(state, size, node.move, turn);
                turn = 3 - turn;
            }
            int winner = rollout(state, size, turn, priority, random);
            for (Node path = node; path != null; path = path.parent) {
                path.visits++;
                if (path.justMoved == winner) path.wins++;
            }
        }
        Node best = null;
        for (Node child : root.children) {
            if (best == null || child.visits > best.visits
                    || child.visits == best.visits && child.wins > best.wins) best = child;
        }
        return best == null ? order[0] : best.move;
    }

    private static Node select(Node node, Random random) {
        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Node child : node.children) {
            double score = (double) child.wins / child.visits
                    + 1.35 * Math.sqrt(Math.log(node.visits + 1.0) / child.visits)
                    + random.nextDouble() * 1e-9;
            if (score > bestScore) {
                best = child;
                bestScore = score;
            }
        }
        return best;
    }

    private static int rollout(byte[] board, int size, int turn, double[] priority, Random random) {
        int[] remaining = new int[board.length];
        int count = 0;
        for (int i = 0; i < board.length; i++) if (board[i] == 0) remaining[count++] = i;
        while (count > 0) {
            double total = 0;
            for (int i = 0; i < count; i++) total += priority[remaining[i]];
            double ticket = random.nextDouble() * total;
            int selected = count - 1;
            for (int i = 0; i < count; i++) {
                ticket -= priority[remaining[i]];
                if (ticket < 0) { selected = i; break; }
            }
            board[remaining[selected]] = (byte) turn;
            remaining[selected] = remaining[--count];
            turn = 3 - turn;
        }
        return connected(board, size, 1) ? 1 : 2;
    }

    private static int immediateWin(byte[] board, int size, int player) {
        for (int i = 0; i < board.length; i++) {
            if (board[i] != 0) continue;
            board[i] = (byte) player;
            boolean won = connected(board, size, player);
            board[i] = 0;
            if (won) return i;
        }
        return -1;
    }

    private static boolean connected(byte[] board, int size, int player) {
        boolean[] seen = new boolean[board.length];
        int[] queue = new int[board.length];
        int head = 0, tail = 0;
        for (int i = 0; i < size; i++) {
            int cell = player == 1 ? i : i * size;
            if (board[cell] == player) { queue[tail++] = cell; seen[cell] = true; }
        }
        while (head < tail) {
            int cell = queue[head++], x = cell / size, y = cell % size;
            if (player == 1 ? x == size - 1 : y == size - 1) return true;
            for (int dir = 0; dir < 6; dir++) {
                int nx = x + DX[dir], ny = y + DY[dir];
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;
                int next = nx * size + ny;
                if (!seen[next] && board[next] == player) {
                    seen[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        return false;
    }

    private static void apply(byte[] board, int size, int move, int player) {
        if (move == board.length) {
            int opening = openingStone(board);
            board[opening] = 0;
            board[(opening % size) * size + opening / size] = 2;
        } else {
            board[move] = (byte) player;
        }
    }

    private static int openingStone(byte[] board) {
        for (int i = 0; i < board.length; i++) if (board[i] == 1) return i;
        throw new IllegalArgumentException("No opening stone to swap");
    }

    private static int[] orderedMoves(byte[] board, double[] priority, boolean swap) {
        Integer[] ranked = new Integer[board.length];
        int count = 0;
        for (int i = 0; i < board.length; i++) if (board[i] == 0) ranked[count++] = i;
        Arrays.sort(ranked, 0, count, Comparator.comparingDouble((Integer cell) -> priority[cell]).reversed());
        int[] moves = new int[count + (swap ? 1 : 0)];
        int offset = swap ? 1 : 0;
        if (swap) moves[0] = board.length;
        for (int i = 0; i < count; i++) moves[i + offset] = ranked[i];
        return moves;
    }

    private static double[] priorities(byte[] board, int size, int player) {
        PathDistances own = distances(board, size, player);
        PathDistances rival = distances(board, size, 3 - player);
        double[] result = new double[board.length];
        for (int i = 0; i < board.length; i++) {
            if (board[i] != 0) continue;
            int x = i / size, y = i % size;
            int ownSlack = own.start[i] + own.end[i] - 1 - own.best;
            int rivalSlack = rival.start[i] + rival.end[i] - 1 - rival.best;
            double center = 1.0 - (Math.abs(x - (size - 1) / 2.0)
                    + Math.abs(y - (size - 1) / 2.0)) / size;
            result[i] = 1.0 + Math.max(0, 5 - ownSlack) * 1.5
                    + Math.max(0, 5 - rivalSlack) + center;
        }
        return result;
    }

    private static PathDistances distances(byte[] board, int size, int player) {
        int[] start = dijkstra(board, size, player, false);
        int[] end = dijkstra(board, size, player, true);
        int best = INF;
        for (int i = 0; i < size; i++) {
            int cell = player == 1 ? (size - 1) * size + i : i * size + size - 1;
            best = Math.min(best, start[cell]);
        }
        return new PathDistances(start, end, best);
    }

    private static int[] dijkstra(byte[] board, int size, int player, boolean reverse) {
        int[] distance = new int[board.length];
        Arrays.fill(distance, INF);
        PriorityQueue<int[]> queue = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
        for (int i = 0; i < size; i++) {
            int cell = player == 1 ? (reverse ? (size - 1) * size + i : i)
                    : (reverse ? i * size + size - 1 : i * size);
            int cost = board[cell] == player ? 0 : board[cell] == 0 ? 1 : INF;
            if (cost < INF) { distance[cell] = cost; queue.add(new int[] {cost, cell}); }
        }
        while (!queue.isEmpty()) {
            int[] current = queue.remove();
            int cell = current[1];
            if (current[0] != distance[cell]) continue;
            int x = cell / size, y = cell % size;
            for (int dir = 0; dir < 6; dir++) {
                int nx = x + DX[dir], ny = y + DY[dir];
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;
                int next = nx * size + ny;
                int cost = board[next] == player ? 0 : board[next] == 0 ? 1 : INF;
                if (current[0] + cost < distance[next]) {
                    distance[next] = current[0] + cost;
                    queue.add(new int[] {distance[next], next});
                }
            }
        }
        return distance;
    }

    private static final class Node {
        final Node parent;
        final int move;
        final int justMoved;
        final int[] moves;
        final ArrayList<Node> children = new ArrayList<>();
        int nextMove;
        int visits;
        int wins;

        Node(Node parent, int move, int justMoved, int[] moves) {
            this.parent = parent;
            this.move = move;
            this.justMoved = justMoved;
            this.moves = moves;
        }
    }

    private static final class PathDistances {
        final int[] start;
        final int[] end;
        final int best;

        PathDistances(int[] start, int[] end, int best) {
            this.start = start;
            this.end = end;
            this.best = best;
        }
    }
}
