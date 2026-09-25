package com.hex.ai;

import java.util.Arrays;

/** Run with: java -ea com.hex.ai.TreeGameAITest */
public final class TreeGameAITest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        byte[] empty = new byte[25];
        check(TreeGameAI.choose(empty, 5, 1, false, 100, 1) == 12,
                "Opening should use the center");

        byte[] redWin = {
                1, 0, 0,
                1, 0, 0,
                0, 0, 0,
        };
        check(TreeGameAI.choose(redWin, 3, 1, false, 50, 2) == 6,
                "Red should complete its left-to-right connection");
        check(TreeGameAI.choose(redWin, 3, 2, false, 50, 2) == 6,
                "Blue should block an immediate red win");

        byte[] blueWin = {
                2, 2, 0,
                0, 0, 0,
                0, 0, 0,
        };
        check(TreeGameAI.choose(blueWin, 3, 2, false, 50, 2) == 2,
                "Blue should complete its top-to-bottom connection");

        byte[] opening = new byte[25];
        opening[7] = 1;
        byte[] original = opening.clone();
        int move = TreeGameAI.choose(opening, 5, 2, true, 300, 42);
        check(move == opening.length || move >= 0 && move < opening.length && opening[move] == 0,
                "Pie-rule choice must be legal");
        check(Arrays.equals(original, opening), "Search must not mutate the caller's board");
        check(move == TreeGameAI.choose(opening, 5, 2, true, 300, 42),
                "A fixed seed must reproduce the same choice");

        byte[] middle = new byte[121];
        middle[60] = 1;
        middle[48] = 2;
        middle[61] = 1;
        long started = System.nanoTime();
        move = TreeGameAI.choose(middle, 11, 2, false, 500, 7);
        long millis = (System.nanoTime() - started) / 1_000_000;
        check(move >= 0 && move < middle.length && middle[move] == 0,
                "11x11 search must return a legal move");
        System.out.println("PASS TreeGameAI: tactics, swap, determinism, 11x11 legality; "
                + "500 simulations in " + millis + " ms");
    }
}
