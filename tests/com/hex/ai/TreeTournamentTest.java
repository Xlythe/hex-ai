package com.hex.ai;

import java.util.Random;

/** A deterministic strength regression against legal random play. */
public final class TreeTournamentTest {
    private static final int[] DX = {-1, 1, 0, 0, -1, 1};
    private static final int[] DY = {0, 0, -1, 1, 1, -1};

    public static void main(String[] args) {
        int wins = 0;
        for (int game = 0; game < 20; game++) {
            int size = 5, bot = game % 2 + 1;
            byte[] board = new byte[size * size];
            Random random = new Random(1000 + game);
            for (int turn = 0; turn < board.length; turn++) {
                int player = turn % 2 + 1;
                int move = player == bot
                        ? TreeGameAI.choose(board, size, player, false, 350, 2000 + game * 31L + turn)
                        : randomMove(board, random);
                if (move < 0 || board[move] != 0) throw new AssertionError("Illegal search move");
                board[move] = (byte) player;
                if (connected(board, size, player)) {
                    if (player == bot) wins++;
                    break;
                }
            }
        }
        if (wins < 16) throw new AssertionError("Tree lost too many games to random play: " + wins);
        System.out.println("PASS TreeGameAI vs random: " + wins + "/20 wins");
    }

    private static int randomMove(byte[] board, Random random) {
        int[] legal = new int[board.length];
        int count = 0;
        for (int i = 0; i < board.length; i++) if (board[i] == 0) legal[count++] = i;
        return legal[random.nextInt(count)];
    }

    private static boolean connected(byte[] board, int size, int player) {
        boolean[] seen = new boolean[board.length];
        int[] stack = new int[board.length];
        int count = 0;
        for (int i = 0; i < size; i++) {
            int start = player == 1 ? i : i * size;
            if (board[start] == player) { stack[count++] = start; seen[start] = true; }
        }
        while (count > 0) {
            int cell = stack[--count], x = cell / size, y = cell % size;
            if (player == 1 ? x == size - 1 : y == size - 1) return true;
            for (int direction = 0; direction < 6; direction++) {
                int nx = x + DX[direction], ny = y + DY[direction];
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;
                int next = nx * size + ny;
                if (!seen[next] && board[next] == player) {
                    seen[next] = true;
                    stack[count++] = next;
                }
            }
        }
        return false;
    }
}
