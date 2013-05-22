package com.hex.ai;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

import com.hex.core.Game;
import com.hex.core.GameAction;
import com.hex.core.GamePiece;
import com.hex.core.Point;

/**
 * @author Will Harmon
 **/
public class GameAI extends AI {
    private static final long serialVersionUID = 1L;
    private GamePiece[][] gameBoard;
    // n is the leftmost AI move, m is the rightmost AI move
    private int[] n = { 0, 0 }, m = { 0, 0 };
    // ArrayList of pair-pieces
    private ArrayList<ArrayList<ArrayList<Integer>>> pairs = new ArrayList<ArrayList<ArrayList<Integer>>>();
    // ArrayList of the AI's state. Used when Undo is called.
    private final ArrayList<AIHistoryObject> history = new ArrayList<AIHistoryObject>();
    private int rand_a = 0;
    private int rand_b = 0;

    public GameAI(int team) {
        super(team);
        while(rand_a == 0 && rand_b == 0) {
            rand_a = new Random().nextInt(3) - 1;
            rand_b = new Random().nextInt(3) - 1;
        }
    }

    public class AIHistoryObject implements Serializable {
        private static final long serialVersionUID = 1L;
        ArrayList<ArrayList<ArrayList<Integer>>> pairs = new ArrayList<ArrayList<ArrayList<Integer>>>();;
        int[] n = { 0, 0 };
        int[] m = { 0, 0 };

        public AIHistoryObject(ArrayList<ArrayList<ArrayList<Integer>>> pairs, int[] n, int[] m) {
            for(int i = 0; i < pairs.size(); i++) {
                this.pairs.add(pairs.get(i));
            }
            this.n[0] = n[0];
            this.n[1] = n[1];
            this.m[0] = m[0];
            this.m[1] = m[1];
        }

        @Override
        public String toString() {
            return pairs.toString() + " : " + n.toString() + " : " + m.toString();
        }
    }

    @Override
    public void getPlayerTurn(Game game) {
        super.getPlayerTurn(game);
        this.gameBoard = game.gamePiece;
        AIHistoryObject state = new AIHistoryObject(pairs, n, m);
        history.add(state);
        makeMove(game);
    }

    @Override
    public void undoCalled() {
        if(history.size() > 0) {
            AIHistoryObject previousState = history.get(history.size() - 1);
            pairs = previousState.pairs;
            n = previousState.n;
            m = previousState.m;
            history.remove(history.size() - 1);
        }
        super.undoCalled();
    }

    private boolean right() {
        return m[0] + 2 <= gameBoard.length - 1 && m[1] + 1 <= gameBoard.length - 1 && m[1] - 1 >= 0;
    }

    private boolean left() {
        return n[0] - 2 >= 0 && n[1] - 1 >= 0 && n[1] + 1 <= gameBoard.length - 1;
    }

    private void makeMove(Game game) {
        /**
         * Will's AI
         * */
        int x = 0;
        int y = 0;
        if(team == 2) {
            x++;
        }
        else if(team == 1) {
            y++;
        }

        // Sleep to stop instantaneous playing
        try {
            for(int i = 0; i < 10; i++) {
                Thread.sleep(50);
                if(game.isGameOver()) break;
            }
        }
        catch(InterruptedException e) {
            e.printStackTrace();
        }

        try {
            // Play in the middle if possible
            int mid = 1;
            mid *= (gameBoard.length - 1) / 2;
            if(gameBoard[mid][mid].getTeam() == 0) {
                n[0] = mid;// horizontal
                n[1] = mid;// vertical
                m[0] = mid;
                m[1] = mid;
                sendMove(game, mid, mid);

                return;
            }
            else if(gameBoard[mid][mid].getTeam() != team && gameBoard[mid + rand_a][mid + rand_b].getTeam() == 0) {
                n[x] = mid + rand_a;// horizontal
                n[y] = mid + rand_b;// vertical
                m[x] = mid + rand_a;
                m[y] = mid + rand_b;
                sendMove(game, mid + rand_a, mid + rand_b);

                return;
            }

            // Add the edges as pairs after we've reached both sides of the map
            if(n[0] - 1 == 0) {
                ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                ArrayList<Integer> cord1 = new ArrayList<Integer>();
                ArrayList<Integer> cord2 = new ArrayList<Integer>();
                cord1.add(n[0] - 1);
                cord1.add(n[1]);
                pair.add(cord1);
                cord2.add(n[0] - 1);
                cord2.add(n[1] + 1);
                pair.add(cord2);
                pairs.add(pair);

                n[0] = n[0] - 1;
            }
            if(m[0] + 1 == gameBoard.length - 1) {
                ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                ArrayList<Integer> cord1 = new ArrayList<Integer>();
                ArrayList<Integer> cord2 = new ArrayList<Integer>();
                cord1.add(m[0] + 1);
                cord1.add(m[1]);
                pair.add(cord1);
                cord2.add(m[0] + 1);
                cord2.add(m[1] - 1);
                pair.add(cord2);
                pairs.add(pair);

                m[0] = m[0] + 1;
            }

            // Check if one of our pairs is being attacked, and fill in the
            // alternate if so
            for(int i = 0; i < pairs.size(); i++) {
                if(gameBoard[pairs.get(i).get(0).get(x)][pairs.get(i).get(0).get(y)].getTeam() == 0
                        || gameBoard[pairs.get(i).get(1).get(x)][pairs.get(i).get(1).get(y)].getTeam() == 0) {
                    if(gameBoard[pairs.get(i).get(0).get(x)][pairs.get(i).get(0).get(y)].getTeam() != 0) {
                        sendMove(game, pairs.get(i).get(1).get(x), pairs.get(i).get(1).get(y));
                        pairs.remove(i);
                        return;
                    }
                    else if(gameBoard[pairs.get(i).get(1).get(x)][pairs.get(i).get(1).get(y)].getTeam() != 0) {
                        sendMove(game, pairs.get(i).get(0).get(x), pairs.get(i).get(0).get(y));
                        pairs.remove(i);
                        return;
                    }
                }
                else {
                    pairs.remove(i);
                }
            }

            // Check if they were sneaky and played in front of us
            if(right() && gameBoard[m[x] + 0 * x + 1 * y][m[y] + 1 * x + 0 * y].getTeam() != 0) {
                if(gameBoard[m[x] - 1 * x + 1 * y][m[y] - 1 * y + 1 * x].getTeam() == 0) {
                    m[0] = m[0] + 1;
                    m[1] = m[1] - 1;

                    sendMove(game, m[x], m[y]);
                    return;
                }
                else if(gameBoard[m[x] + 1 * x + 0 * y][m[y] + 1 * y + 0 * x].getTeam() == 0) {
                    m[0] = m[0];
                    m[1] = m[1] + 1;

                    sendMove(game, m[x], m[y]);
                    return;
                }
            }
            if(right()
                    && (gameBoard[m[x] - 1 * x + 1 * y][m[y] - 1 * y + 1 * x].getTeam() != 0 || gameBoard[m[x] + 1 * x + 0 * y][m[y] + 1 * y + 0 * x].getTeam() != 0)
                    && gameBoard[m[x] + 0 * x + 1 * y][m[y] + 0 * y + 1 * x].getTeam() == 0) {
                m[0] = m[0] + 1;
                m[1] = m[1];

                sendMove(game, m[x], m[y]);
                return;
            }
            // Check if they were sneakier and played behind us
            if(left() && gameBoard[n[x] + 0 * x - 1 * y][n[y] + 0 * y - 1 * x].getTeam() != 0) {
                if(gameBoard[n[x] + 1 * x - 1 * y][n[y] + 1 * y - 1 * x].getTeam() == 0) {
                    n[0] = n[0] - 1;
                    n[1] = n[1] + 1;

                    sendMove(game, n[x], n[y]);
                    return;
                }
                else if(gameBoard[n[x] - 1 * x + 0 * y][n[y] - 1 * y + 0 * x].getTeam() == 0) {
                    n[0] = n[0];
                    n[1] = n[1] - 1;

                    sendMove(game, n[x], n[y]);
                    return;
                }
            }
            if(left()
                    && (gameBoard[n[x] + 1 * x - 1 * y][n[y] + 1 * y - 1 * x].getTeam() != 0 || gameBoard[n[x] - 1 * x + 0 * y][n[y] - 1 * y + 0 * x].getTeam() != 0)
                    && gameBoard[n[x] + 0 * x - 1 * y][n[y] + 0 * y - 1 * x].getTeam() == 0) {
                n[0] = n[0] - 1;
                n[1] = n[1];

                sendMove(game, n[x], n[y]);
                return;
            }

            // Check if we should extend to the left
            if(left()) {
                if(gameBoard[n[x] - 1 * x - 1 * y][n[y] - 1 * y - 1 * x].getTeam() != 0 && gameBoard[n[x] + 1 * x - 2 * y][n[y] + 1 * y - 2 * x].getTeam() == 0) {
                    ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                    ArrayList<Integer> cord1 = new ArrayList<Integer>();
                    ArrayList<Integer> cord2 = new ArrayList<Integer>();
                    cord1.add(n[0] - 1);
                    cord1.add(n[1]);
                    pair.add(cord1);
                    cord2.add(n[0] - 1);
                    cord2.add(n[1] + 1);
                    pair.add(cord2);
                    pairs.add(pair);

                    n[0] = n[0] - 2;
                    n[1] = n[1] + 1;

                    sendMove(game, n[x], n[y]);
                    return;
                }
                else if(gameBoard[n[x] + 1 * x - 2 * y][n[y] + 1 * y - 2 * x].getTeam() != 0
                        && gameBoard[n[x] - 1 * x - 1 * y][n[y] - 1 * y - 1 * x].getTeam() == 0) {
                    ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                    ArrayList<Integer> cord1 = new ArrayList<Integer>();
                    ArrayList<Integer> cord2 = new ArrayList<Integer>();
                    cord1.add(n[0]);
                    cord1.add(n[1] - 1);
                    pair.add(cord1);
                    cord2.add(n[0] - 1);
                    cord2.add(n[1]);
                    pair.add(cord2);
                    pairs.add(pair);

                    n[0] = n[0] - 1;
                    n[1] = n[1] - 1;

                    sendMove(game, n[x], n[y]);
                    return;
                }
            }

            // Check if we should extend to the right
            if(right()) {
                if(gameBoard[m[x] - 1 * x + 2 * y][m[y] - 1 * y + 2 * x].getTeam() != 0 && gameBoard[m[x] + 1 * x + 1 * y][m[y] + 1 * y + 1 * x].getTeam() == 0) {
                    ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                    ArrayList<Integer> cord1 = new ArrayList<Integer>();
                    ArrayList<Integer> cord2 = new ArrayList<Integer>();
                    cord1.add(m[0] + 1);
                    cord1.add(m[1]);
                    pair.add(cord1);
                    cord2.add(m[0]);
                    cord2.add(m[1] + 1);
                    pair.add(cord2);
                    pairs.add(pair);

                    m[0] = m[0] + 1;
                    m[1] = m[1] + 1;

                    sendMove(game, m[x], m[y]);
                    return;
                }
                else if(gameBoard[m[x] + 1 * x + 1 * y][m[y] + 1 * y + 1 * x].getTeam() != 0
                        && gameBoard[m[x] - 1 * x + 2 * y][m[y] - 1 * y + 2 * x].getTeam() == 0) {
                    ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                    ArrayList<Integer> cord1 = new ArrayList<Integer>();
                    ArrayList<Integer> cord2 = new ArrayList<Integer>();
                    cord1.add(m[0] + 1);
                    cord1.add(m[1]);
                    pair.add(cord1);
                    cord2.add(m[0] + 1);
                    cord2.add(m[1] - 1);
                    pair.add(cord2);
                    pairs.add(pair);

                    m[0] = m[0] + 2;
                    m[1] = m[1] - 1;

                    sendMove(game, m[x], m[y]);
                    return;
                }
            }
            int rand = 2;
            rand *= Math.random();

            // Extend left if we haven't gone right
            if(left() && rand == 0 && gameBoard[n[x] + 1 * x - 2 * y][n[y] + 1 * y - 2 * x].getTeam() == 0) {
                ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                ArrayList<Integer> cord1 = new ArrayList<Integer>();
                ArrayList<Integer> cord2 = new ArrayList<Integer>();
                cord1.add(n[0] - 1);
                cord1.add(n[1]);
                pair.add(cord1);
                cord2.add(n[0] - 1);
                cord2.add(n[1] + 1);
                pair.add(cord2);
                pairs.add(pair);

                n[0] = n[0] - 2;
                n[1] = n[1] + 1;

                sendMove(game, n[x], n[y]);
                return;
            }
            else if(left() && rand == 1 && gameBoard[n[x] - 1 * x - 1 * y][n[y] - 1 * y - 1 * x].getTeam() == 0) {
                ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                ArrayList<Integer> cord1 = new ArrayList<Integer>();
                ArrayList<Integer> cord2 = new ArrayList<Integer>();
                cord1.add(n[0]);
                cord1.add(n[1] - 1);
                pair.add(cord1);
                cord2.add(n[0] - 1);
                cord2.add(n[1]);
                pair.add(cord2);
                pairs.add(pair);

                n[0] = n[0] - 1;
                n[1] = n[1] - 1;

                sendMove(game, n[x], n[y]);
                return;
            }
            // Extend right if we haven't gone left
            if(right() && rand == 0 && gameBoard[m[x] - 1 * x + 2 * y][m[y] - 1 * y + 2 * x].getTeam() == 0) {
                ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                ArrayList<Integer> cord1 = new ArrayList<Integer>();
                ArrayList<Integer> cord2 = new ArrayList<Integer>();
                cord1.add(m[0] + 1);
                cord1.add(m[1]);
                pair.add(cord1);
                cord2.add(m[0] + 1);
                cord2.add(m[1] - 1);
                pair.add(cord2);
                pairs.add(pair);

                m[0] = m[0] + 2;
                m[1] = m[1] - 1;

                sendMove(game, m[x], m[y]);
                return;
            }
            else if(right() && rand == 1 && gameBoard[m[x] + 1 * x + 1 * y][m[y] + 1 * y + 1 * x].getTeam() == 0) {
                ArrayList<ArrayList<Integer>> pair = new ArrayList<ArrayList<Integer>>();
                ArrayList<Integer> cord1 = new ArrayList<Integer>();
                ArrayList<Integer> cord2 = new ArrayList<Integer>();
                cord1.add(m[0] + 1);
                cord1.add(m[1]);
                pair.add(cord1);
                cord2.add(m[0]);
                cord2.add(m[1] + 1);
                pair.add(cord2);
                pairs.add(pair);

                m[0] = m[0] + 1;
                m[1] = m[1] + 1;

                sendMove(game, m[x], m[y]);
                return;
            }

            // Fill in the pairs after we've reached both sides of the map
            if(!left() && !right() && pairs.size() > 0) {
                // Play a random pair
                sendMove(game, pairs.get(0).get(1).get(x), pairs.get(0).get(1).get(y));
                pairs.remove(0);

                return;
            }
        }
        catch(Exception e) {}

        // Pick randomly
        int moves = 0;
        for(int a = 0; a < gameBoard.length; a++) {
            for(int b = 0; b < gameBoard[a].length; b++) {
                if(gameBoard[a][b].getTeam() == 0) moves++;
            }
        }
        moves *= Math.random();
        moves++;
        for(int a = 0; a < gameBoard.length; a++) {
            for(int b = 0; b < gameBoard[a].length; b++) {
                if(gameBoard[a][b].getTeam() == 0) {
                    moves--;
                }
                if(moves == 0) {
                    sendMove(game, a, b);
                    moves = -10;
                }
            }
        }

        return;
    }

    private void sendMove(Game game, int x, int y) {
        if(!getSkipMove()) GameAction.makeMove(this, new Point(x, y), game);
    }

    @Override
    public void win() {}

    @Override
    public void lose() {}

    @Override
    public Serializable getSaveState() {
        return history;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setSaveState(Serializable state) {
        LinkedList<AIHistoryObject> history = (LinkedList<AIHistoryObject>) state;
        this.history.clear();
        for(AIHistoryObject ho : history) {
            this.history.add(ho);
        }
        undoCalled();
    }

    @Override
    public int getType() {
        return 1;
    }
}
