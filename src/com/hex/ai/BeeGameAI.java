package com.hex.ai;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.hex.core.AI;
import com.hex.core.Game;
import com.hex.core.GameAction;
import com.hex.core.Point;

/**
 * The "Bee" class. Purpose: To play the game of Hex
 * 
 * @author Konstantin Lopyrev
 * @version June 2006
 */
public class BeeGameAI extends AI {
    private final static boolean DEBUG = false;
    private final static long serialVersionUID = 1L;
    private final static int RED = 1, BLUE = 2;

    // List of the AI's state. Used when Undo is called.
    private final LinkedList<AIHistoryObject> history = new LinkedList<AIHistoryObject>();
    private final int gridSize, maxDepth, beamSize;

    private transient EvaluationNode[][] nodesArray;
    private transient int[][] pieces;
    private transient HashMap<Integer, Integer> lookUpTable;

    /**
     * Constructor for the Bee object
     * 
     * @param game
     *            the currently running game
     * @param board
     *            the board that stores the currently running game
     * @param colour
     *            the colour of Bee
     */
    public BeeGameAI(int team, int gridSize, int depth, int beamSize) {
        super(team);
        // Creates the pieces array that stores the board inside Bee
        this.maxDepth = depth;
        this.beamSize = beamSize;
        this.gridSize = gridSize;
        pieces = new int[gridSize + 2][gridSize + 2];
        for(int i = 1; i < pieces.length - 1; i++) {
            pieces[i][0] = RED;
            pieces[0][i] = BLUE;
            pieces[i][pieces.length - 1] = RED;
            pieces[pieces.length - 1][i] = BLUE;
        }
        lookUpTable = new HashMap<Integer, Integer>();
    }

    public class AIHistoryObject implements Serializable {
        private static final long serialVersionUID = 1L;
        int[][] pieces;
        HashMap<Integer, Integer> lookUpTable;

        public AIHistoryObject(int[][] pieces, HashMap<Integer, Integer> lookUpTable) {
            this.pieces = new int[pieces.length][pieces.length];
            for(int i = 0; i < pieces.length; i++) {
                for(int j = 0; j < pieces.length; j++) {
                    this.pieces[i][j] = pieces[i][j];
                }
            }
            this.lookUpTable = lookUpTable;
        }
    }

    /**
     * Runs the Bee thread
     */
    @Override
    public void getPlayerTurn(Game game) {
        super.getPlayerTurn(game);
        AIHistoryObject state = new AIHistoryObject(pieces, lookUpTable);
        try {
            history.add(state);
        }
        catch(ConcurrentModificationException e) {
            e.printStackTrace();
            return;
        }
        int moveNumber = game.getMoveNumber();

        Point lastMove;
        try {
            if(moveNumber > 1) lastMove = new Point(gridSize - 1 - game.getMoveList().getMove().getY(), game.getMoveList().getMove().getX());
            else lastMove = null;
        }
        catch(Exception e) {
            lastMove = null;
        }
        // If Bee is to make the first move in the game,
        // it makes it in the centre of the board.
        if(lastMove == null) {
            pieces[pieces.length / 2][pieces.length / 2] = team;
            if(!getSkipMove()) GameAction.makeMove(this, new Point(pieces.length / 2 - 1, pieces.length / 2 - 1), game);
        }
        // If a move has been made already,
        // Bee records the move in the pieces array
        // and makes its own move.
        else {
            pieces[lastMove.x + 1][lastMove.y + 1] = team == 1 ? 2 : 1;
            Point bestMove = getBestMove();
            pieces[bestMove.x][bestMove.y] = team;
            int x = bestMove.x - 1;
            int y = bestMove.y - 1;

            if(!getSkipMove()) GameAction.makeMove(this, new Point(y, gridSize - 1 - x), game);
        }
    }

    @Override
    public void undoCalled() {
        if(history.size() > 0) {
            AIHistoryObject previousState = history.get(history.size() - 1);
            pieces = previousState.pieces;
            lookUpTable = previousState.lookUpTable;
            history.remove(history.size() - 1);
        }
        super.undoCalled();
    }

    /**
     * Gets the best move on the board
     * 
     * @return the point containing the move coordinates
     */
    private Point getBestMove() {
        // Initially sets the best move to an invalid move with
        // the lowest possible move value
        int bestValue = team == RED ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int bestRow = -1;
        int bestColumn = -1;
        int[][] tempValueArray = new int[pieces.length][pieces.length];
        // Tries every single move possible and evaluates how good it is.
        for(int i = 1; i < pieces.length - 1; i++) {
            for(int j = 1; j < pieces.length - 1; j++) {
                if(pieces[i][j] != 0) continue;

                // Gets the evaluation for the move by expanding
                // the game tree.
                pieces[i][j] = team;
                int value = expand(1, bestValue, team == RED ? BLUE : RED, nodesArray);
                pieces[i][j] = 0;
                tempValueArray[j][pieces.length - 1 - i] = value;

                // Compares the last move to the best move so far
                // and records the move if it is better.
                if(team == RED && value > bestValue) {
                    bestValue = value;
                    bestRow = i;
                    bestColumn = j;
                }
                else if(team == BLUE && value < bestValue) {
                    bestValue = value;
                    bestRow = i;
                    bestColumn = j;
                }
            }
        }

        if(DEBUG) {
            System.out.println("Move: " + bestColumn + "," + (pieces.length - 1 - bestRow));
            for(int i = 0; i < pieces.length; i++) {
                for(int j = 0; j < pieces.length; j++) {
                    System.out.print(tempValueArray[i][j] + ",");
                }
                System.out.println();
            }
        }
        return new Point(bestRow, bestColumn);
    }

    /**
     * Evaluates the current branch of the game tree.
     * 
     * @param depth
     *            the depth of the current branch
     * @param previousBest
     *            the best move value of parallel branches
     * @param currentColour
     *            the player colour to which the current branch corresponds to
     * @return the value of the current branch
     */
    private int expand(int depth, int previousBest, int currentColour, EvaluationNode[][] nodesArray) {
        // Break early if the move is no longer needed
        if(getSkipMove()) return 0;

        // If depth is maximum depth, evaluates the branch using
        // a board evaluation instead of expanding it.
        if(depth == maxDepth) return evaluate();
        int bestValue = currentColour == RED ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Gets all the moves possible to make.
        Iterator<Move> iter = getMoves().iterator();

        // Considers only the several best moves that are possible to make.
        for(int i = 0; i < beamSize && iter.hasNext(); i++) {
            // Gets the move value of the next move.
            Move nextMove = iter.next();
            pieces[nextMove.row][nextMove.column] = currentColour;
            int value = expand(depth + 1, bestValue, currentColour == RED ? BLUE : RED, nodesArray);
            pieces[nextMove.row][nextMove.column] = 0;

            // Compares the last move to the best move so far
            // and records the move if it is better.
            if(currentColour == RED && value > bestValue) bestValue = value;
            else if(currentColour == BLUE && value < bestValue) bestValue = value;

            // If the current move makes the whole branch
            // too worthless to be better than any of the parallel branches,
            // stops expanding it.
            if(currentColour == RED && bestValue > previousBest || currentColour == BLUE && bestValue < previousBest) return bestValue;
        }

        // If no moves are possible at this depth,
        // returns the evaluation of the board.
        if(bestValue == Integer.MAX_VALUE || bestValue == Integer.MIN_VALUE) bestValue = evaluate();
        return bestValue;
    }

    private ArrayList<Move> getMoves() {
        // Builds the evaluation board for the current position
        nodesArray = new EvaluationNode[pieces.length][pieces.length];
        EvaluationNode.buildEvaluationBoard(pieces, nodesArray);

        // Generates the four two-distance arrays.
        int[][] redA = new int[pieces.length][pieces.length];
        int[][] redB = new int[pieces.length][pieces.length];
        int[][] blueA = new int[pieces.length][pieces.length];
        int[][] blueB = new int[pieces.length][pieces.length];
        for(int i = 0; i < pieces.length; i++) {
            for(int j = 0; j < pieces.length; j++) {
                redA[i][j] = 100000;
                redB[i][j] = 100000;
                blueA[i][j] = 100000;
                blueB[i][j] = 100000;
            }
        }
        // Sets the lower corners to 0 and builds
        // the two-distance array from there.
        redA[0][0] = 0;
        redA[redA.length - 1][0] = 0;
        redB[0][redB.length - 1] = 0;
        redB[redB.length - 1][redB.length - 1] = 0;
        blueA[0][0] = 0;
        blueA[0][blueA.length - 1] = 0;
        blueB[blueB.length - 1][0] = 0;
        blueB[blueB.length - 1][blueB.length - 1] = 0;

        // Builds the first RED two-distance array
        boolean found = true;
        while(found) {
            found = false;
            // Considers every position on the board
            // and checks if it is possible to update it.
            for(int j = 1; j < redA.length - 1; j++) {
                for(int i = 1; i < redA.length - 1; i++) {
                    if(redA[i][j] != 100000) continue;
                    if(pieces[i][j] != 0) continue;

                    // Updates the position by considering all
                    // of its neighbours and assigning
                    // the two-distance value of 1 more
                    // than the second minimum value of its neighbours.
                    int min = 100000;
                    int secondMin = 100000;
                    Iterator<EvaluationNode> iter = nodesArray[i][j].redNeighbours.iterator();

                    while(iter.hasNext()) {

                        EvaluationNode next = iter.next();
                        int number = redA[next.row][next.column];
                        if(number < secondMin) {
                            secondMin = number;
                            if(number < min) {
                                secondMin = min;
                                min = number;
                            }
                        }
                    }
                    if(secondMin < 100) {
                        if(redA[i][j] != secondMin + 1) {
                            found = true;
                            redA[i][j] = secondMin + 1;
                        }
                    }
                }
            }
        }

        // Builds the second RED two-distance array
        found = true;
        while(found) {
            found = false;
            for(int j = redB.length - 2; j > 0; j--) {
                for(int i = 1; i < redB.length - 1; i++) {
                    if(redB[i][j] != 100000) continue;
                    if(pieces[i][j] != 0) continue;
                    int min = 100000;
                    int secondMin = 100000;
                    Iterator<EvaluationNode> iter = nodesArray[i][j].redNeighbours.iterator();

                    while(iter.hasNext()) {
                        EvaluationNode next = iter.next();
                        int number = redB[next.row][next.column];
                        if(number < secondMin) {
                            secondMin = number;
                            if(number < min) {
                                secondMin = min;
                                min = number;
                            }
                        }
                    }
                    if(secondMin < 100) {
                        if(redB[i][j] != secondMin + 1) {
                            found = true;
                            redB[i][j] = secondMin + 1;
                        }
                    }
                }
            }
        }

        // Builds the first BLUE two-distance array
        found = true;
        while(found) {
            found = false;
            for(int i = 1; i < blueA.length - 1; i++) {
                for(int j = 1; j < blueA.length - 1; j++) {
                    if(blueA[i][j] != 100000) continue;
                    if(pieces[i][j] != 0) continue;
                    int min = 100000;
                    int secondMin = 100000;
                    Iterator<EvaluationNode> iter = nodesArray[i][j].blueNeighbours.iterator();

                    while(iter.hasNext()) {
                        EvaluationNode next = iter.next();
                        int number = blueA[next.row][next.column];
                        if(number < secondMin) {
                            secondMin = number;
                            if(number < min) {
                                secondMin = min;
                                min = number;
                            }
                        }
                    }
                    if(secondMin < 100) {
                        if(blueA[i][j] != secondMin + 1) {
                            found = true;
                            blueA[i][j] = secondMin + 1;
                        }
                    }
                }
            }
        }
        // Builds the second BLUE two-distance array
        found = true;
        while(found) {
            found = false;
            for(int i = 1; i < blueB.length - 1; i++) {
                for(int j = blueB.length - 2; j > 0; j--) {
                    if(blueB[i][j] != 100000) continue;
                    if(pieces[i][j] != 0) continue;
                    int min = 100000;
                    int secondMin = 100000;
                    Iterator<EvaluationNode> iter = nodesArray[i][j].blueNeighbours.iterator();

                    while(iter.hasNext()) {
                        EvaluationNode next = iter.next();
                        int number = blueB[next.row][next.column];
                        if(number < secondMin) {
                            secondMin = number;
                            if(number < min) {
                                secondMin = min;
                                min = number;
                            }
                        }
                    }
                    if(secondMin < 100) {
                        if(blueB[i][j] != secondMin + 1) {
                            found = true;
                            blueB[i][j] = secondMin + 1;
                        }
                    }
                }
            }
        }
        ArrayList<Move> moves = new ArrayList<Move>();

        // Adds each move to the moves array
        // with the move value of the sum of its two-distances.
        for(int i = 1; i < pieces.length - 1; i++) {
            for(int j = 1; j < pieces.length - 1; j++) {
                if(pieces[i][j] != 0) continue;
                moves.add(new Move(i, j, redA[i][j] + redB[i][j] + blueA[i][j] + blueB[i][j]));
            }
        }
        // Sorts the moves in order from best to worst.
        Collections.sort(moves);
        return moves;
    }

    /**
     * Evaluates the current board.
     * 
     * @return the board value
     */
    private int evaluate() {
        // Checks if the board has been
        // evaluated before and if it has, returns the previous value.
        Integer piecesString = piecesString();
        Integer piecesValue = lookUpTable.get(piecesString);
        if(piecesValue != null) return piecesValue.intValue();

        // Builds the evaluation board for the current position
        nodesArray = new EvaluationNode[pieces.length][pieces.length];
        EvaluationNode.buildEvaluationBoard(pieces, nodesArray);

        // Builds the four two-distance arrays.
        int[][] redA = new int[pieces.length][pieces.length];
        int[][] redB = new int[pieces.length][pieces.length];
        int[][] blueA = new int[pieces.length][pieces.length];
        int[][] blueB = new int[pieces.length][pieces.length];
        for(int i = 0; i < pieces.length; i++) {
            for(int j = 0; j < pieces.length; j++) {
                redA[i][j] = 100000;
                redB[i][j] = 100000;
                blueA[i][j] = 100000;
                blueB[i][j] = 100000;
            }
        }

        // Sets the four corners of the arrays to 0 and builds from there.
        redA[0][0] = 0;
        redA[redA.length - 1][0] = 0;
        redB[0][redB.length - 1] = 0;
        redB[redB.length - 1][redB.length - 1] = 0;
        blueA[0][0] = 0;
        blueA[0][blueA.length - 1] = 0;
        blueB[blueB.length - 1][0] = 0;
        blueB[blueB.length - 1][blueB.length - 1] = 0;

        // Builds the first RED two-distance array
        boolean found = true;
        while(found) {
            found = false;
            // Considers every position on the board
            // and checks if it is possible to update it.
            for(int j = 1; j < redA.length - 1; j++) {
                for(int i = 1; i < redA.length - 1; i++) {
                    if(redA[i][j] != 100000) continue;
                    if(pieces[i][j] != 0) continue;

                    // Updates the position by considering
                    // all of its neighbours and assigning
                    // the two-distance value of 1 more
                    // than the second minimum value of its neighbours.
                    int min = 100000;
                    int secondMin = 100000;
                    Iterator<EvaluationNode> iter = nodesArray[i][j].redNeighbours.iterator();

                    while(iter.hasNext()) {

                        EvaluationNode next = iter.next();
                        int number = redA[next.row][next.column];
                        if(number < secondMin) {
                            secondMin = number;
                            if(number < min) {
                                secondMin = min;
                                min = number;
                            }
                        }
                    }
                    if(secondMin < 100) {
                        if(redA[i][j] != secondMin + 1) {
                            found = true;
                            redA[i][j] = secondMin + 1;
                        }
                    }
                }
            }
        }

        // Builds the second RED two-distance array
        found = true;
        while(found) {
            found = false;
            for(int j = redB.length - 2; j > 0; j--) {
                for(int i = 1; i < redB.length - 1; i++) {
                    if(redB[i][j] != 100000) continue;
                    if(pieces[i][j] != 0) continue;
                    int min = 100000;
                    int secondMin = 100000;
                    Iterator<EvaluationNode> iter = nodesArray[i][j].redNeighbours.iterator();

                    while(iter.hasNext()) {
                        EvaluationNode next = iter.next();
                        int number = redB[next.row][next.column];
                        if(number < secondMin) {
                            secondMin = number;
                            if(number < min) {
                                secondMin = min;
                                min = number;
                            }
                        }
                    }
                    if(secondMin < 100) {
                        if(redB[i][j] != secondMin + 1) {
                            found = true;
                            redB[i][j] = secondMin + 1;
                        }
                    }
                }
            }
        }

        // Builds the first BLUE two-distance array
        found = true;
        while(found) {
            found = false;
            for(int i = 1; i < blueA.length - 1; i++) {
                for(int j = 1; j < blueA.length - 1; j++) {
                    if(blueA[i][j] != 100000) continue;
                    if(pieces[i][j] != 0) continue;
                    int min = 100000;
                    int secondMin = 100000;
                    Iterator<EvaluationNode> iter = nodesArray[i][j].blueNeighbours.iterator();

                    while(iter.hasNext()) {
                        EvaluationNode next = iter.next();
                        int number = blueA[next.row][next.column];
                        if(number < secondMin) {
                            secondMin = number;
                            if(number < min) {
                                secondMin = min;
                                min = number;
                            }
                        }
                    }
                    if(secondMin < 100) {
                        if(blueA[i][j] != secondMin + 1) {
                            found = true;
                            blueA[i][j] = secondMin + 1;
                        }
                    }
                }
            }
        }

        // Builds the second BLUE two-distance array
        found = true;
        while(found) {
            found = false;
            for(int i = 1; i < blueB.length - 1; i++) {
                for(int j = blueB.length - 2; j > 0; j--) {
                    if(blueB[i][j] != 100000) continue;
                    if(pieces[i][j] != 0) continue;
                    int min = 100000;
                    int secondMin = 100000;
                    Iterator<EvaluationNode> iter = nodesArray[i][j].blueNeighbours.iterator();

                    while(iter.hasNext()) {
                        EvaluationNode next = iter.next();
                        int number = blueB[next.row][next.column];
                        if(number < secondMin) {
                            secondMin = number;
                            if(number < min) {
                                secondMin = min;
                                min = number;
                            }
                        }
                    }
                    if(secondMin < 100) {
                        if(blueB[i][j] != secondMin + 1) {
                            found = true;
                            blueB[i][j] = secondMin + 1;
                        }
                    }
                }
            }
        }
        // Calculates the potentials and the mobility.
        // The potential of a board for a
        // particular colour is the smallest
        // two-distance value that occurs on
        // the sum of the boards corresponding to that colour.
        // The mobility of a board for a
        // particular colour is how many times
        // the smallest two-distance value occurs on
        // the sum of the boards corresponding to that colour.
        int redPotential = 100000;
        int bluePotential = 100000;
        int redMobility = 0;
        int blueMobility = 0;
        for(int i = 1; i < redA.length - 1; i++) {
            for(int j = 1; j < redA.length - 1; j++) {
                if(pieces[i][j] == 0) {
                    if(redA[i][j] + redB[i][j] < redPotential) {
                        redPotential = redA[i][j] + redB[i][j];
                        redMobility = 1;
                    }
                    else if(redA[i][j] + redB[i][j] == redPotential) redMobility++;
                    if(blueA[i][j] + blueB[i][j] < bluePotential) {
                        bluePotential = blueA[i][j] + blueB[i][j];
                        blueMobility = 1;
                    }
                    else if(blueA[i][j] + blueB[i][j] == bluePotential) blueMobility++;
                }
            }
        }

        // Stores the value of the current board in
        // the look-up table for future use.
        lookUpTable.put(piecesString, 100 * (bluePotential - redPotential) - (blueMobility - redMobility));

        // Returns the value of the board.
        return 100 * (bluePotential - redPotential) - (blueMobility - redMobility);
    }

    /**
     * Creates a BigInteger representation of the current board to use in the look-up table
     * 
     * @return the BigInteger representation
     */
    private Integer piecesString() {
        Integer value = pieces.length - 2;
        for(int i = 1; i < pieces.length - 1; i++) {
            for(int j = 1; j < pieces.length - 1; j++) {
                value *= 3;
                value += pieces[i][j];
            }
        }
        return value;
    }

    @Override
    public Serializable getSaveState() {
        return history;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setSaveState(Serializable state) {
        List<AIHistoryObject> history = (List<AIHistoryObject>) state;
        this.history.clear();
        for(AIHistoryObject ho : history) {
            this.history.add(ho);
        }
        undoCalled();
    }

    @Override
    public String getAIType() {
        return "Bee";
    }

    @Override
    public String getName() {
        return "Bee";
    }

    @Override
    public void win() {}

    @Override
    public void lose(Game game) {}

    @Override
    public void startGame() {}

    @Override
    public void newgameCalled() {
        super.newgameCalled();
        pieces = new int[gridSize + 2][gridSize + 2];
        for(int i = 1; i < pieces.length - 1; i++) {
            pieces[i][0] = RED;
            pieces[0][i] = BLUE;
            pieces[i][pieces.length - 1] = RED;
            pieces[pieces.length - 1][i] = BLUE;
        }
        lookUpTable = new HashMap<Integer, Integer>();
    }
}

/**
 * The "Move" class. Purpose: Stores a move
 * 
 * @author Konstantin Lopyrev
 * @version June 2006
 */
class Move implements Comparable<Move> {
    public int row;
    public int column;
    private final int value;

    /**
     * Constructor for the Move class
     * 
     * @param row
     *            the row of the move
     * @param column
     *            the column of the move
     * @param value
     *            the value of the move
     */
    public Move(int row, int column, int value) {
        this.row = row;
        this.column = column;
        this.value = value;
    }

    /**
     * Compares this move to another object by value
     * 
     * @param other
     *            the object to compare to
     * @return 0 if equals, -ve if less than, +ve if greater than
     */
    @Override
    public int compareTo(Move other) {
        return this.value - other.value;
    }
}

/**
 * The "EvaluationNode" class. Purpose: Stores the neighbours of each piece on the Hex board
 * 
 * @author Konstantin Lopyrev
 * @version June 2006
 */
class EvaluationNode implements Serializable {
    private static final long serialVersionUID = 1L;
    public HashSet<EvaluationNode> redNeighbours;
    public HashSet<EvaluationNode> blueNeighbours;
    public int row;
    public int column;

    /**
     * Constructor for the EvaluationNode class.
     * 
     * @param row
     *            the row of the piece
     * @param column
     *            the column of the piece
     */
    public EvaluationNode(int row, int column) {
        this.row = row;
        this.column = column;
        redNeighbours = new HashSet<EvaluationNode>();
        blueNeighbours = new HashSet<EvaluationNode>();
    }

    /**
     * Creates the evaluation board for the corresponding pieces board
     * 
     * @param pieces
     *            the corresponding pieces board
     */
    public static void buildEvaluationBoard(int[][] pieces, EvaluationNode[][] nodesArray) {
        // Initially creates all the EvaluationNodes without their neighbours
        for(int i = 0; i < nodesArray.length; i++)
            for(int j = 0; j < nodesArray.length; j++)
                nodesArray[i][j] = new EvaluationNode(i, j);

        // Builds the neighbours of each EvaluationNode
        for(int i = 0; i < nodesArray.length; i++)
            for(int j = 0; j < nodesArray.length; j++) {
                if(pieces[i][j] != 0) continue;
                nodesArray[i][j].redNeighbours = nodesArray[i][j].getNeighbours(1, new HashSet<EvaluationNode>(), nodesArray, pieces);
                nodesArray[i][j].redNeighbours.remove(nodesArray[i][j]);
                nodesArray[i][j].blueNeighbours = nodesArray[i][j].getNeighbours(2, new HashSet<EvaluationNode>(), nodesArray, pieces);
                nodesArray[i][j].blueNeighbours.remove(nodesArray[i][j]);
            }
    }

    /**
     * Recursive method which returns the neighbours of a piece
     * 
     * @param colour
     *            the current colour
     * @param piecesVisited
     *            stores the pieces that have been visited already so that they are not touched again
     * @return the neighbours of the piece in a HashSet
     */
    private HashSet<EvaluationNode> getNeighbours(int colour, HashSet<EvaluationNode> piecesVisited, EvaluationNode[][] nodesArray, int[][] pieces) {
        // If the current piece has been visited already,
        // returns an empty HashSet
        if(piecesVisited.contains(this)) return new HashSet<EvaluationNode>();
        HashSet<EvaluationNode> returnValue = new HashSet<EvaluationNode>();
        if(pieces[row][column] == colour) piecesVisited.add(this);

        // Considers all the neighbours of the current piece.
        for(int a = -1; a <= 1; a++) {
            for(int b = -1; b <= 1; b++) {
                if(a + b == 0) continue;
                if(row + a < 0 || row + a == nodesArray.length || column + b < 0 || column + b == nodesArray.length) continue;

                // If the current neighbour is empty,
                // adds it to the neighbours list.
                if(pieces[row + a][column + b] == 0) returnValue.add(nodesArray[row + a][column + b]);

                // If the current neighbour is a piece of
                // the opposing colour, ignores it.
                else if(pieces[row + a][column + b] != colour) continue;

                // If the current neighbour is a piece of
                // the same colour,
                // adds all of its neighbours to the neighbours list.
                else returnValue.addAll(nodesArray[row + a][column + b].getNeighbours(colour, piecesVisited, nodesArray, pieces));
            }
        }
        return returnValue;
    }

    /**
     * Returns a hashCode for the current EvaluationNode
     * 
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return row * 100 + column;
    }

    /**
     * Compares this EvaluationNode to another object
     * 
     * @param other
     *            the object to compare to
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object other) {
        EvaluationNode otherNode = (EvaluationNode) other;
        return row == otherNode.row && column == otherNode.column;
    }
}
