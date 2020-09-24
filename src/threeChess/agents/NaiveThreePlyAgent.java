package threeChess.agents;

import threeChess.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A naively implemented three-ply maximax agent that uses the default Board implementation.
 *
 * This agent is _slow_. It takes about 300x as much time per move as AgentFixedPly.
 *
 * @author Paddy Lamont, 22494652
 */
public class NaiveThreePlyAgent extends Agent{

  private static final String name = "Naive-Three-Ply";
  private static final Random random = new Random();

  /** A no argument constructor, required for tournament management. **/
  public NaiveThreePlyAgent() {}

  /** @return another equivalent three-ply agent. **/
  @Override public NaiveThreePlyAgent clone() {
    return new NaiveThreePlyAgent();
  }

  /**
   * Searches through all possible moves in an attempt to find any moves that
   * are able to take pieces. Returns a random move from the set of moves found.
   */
  @Override public Position[] playMove(Board board){
    int bestUtility = Integer.MIN_VALUE;
    List<Position[]> bestMoves = new ArrayList<>();

    List<Position[]> availableMoves = getAvailableMoves(board);
    Colour colour = board.getTurn();

    for (Position[] move : availableMoves) {
      // Apply the move.
      Board moveBoard;
      try {
        moveBoard = (Board) board.clone();
        moveBoard.move(move[0], move[1]);
      } catch (CloneNotSupportedException | ImpossiblePositionException e) {
        throw new RuntimeException(e);
      }

      // Calculate the utility of the move.
      int utility = performMaxMaxMax(moveBoard, 2)[colour.ordinal()];
      if (utility > bestUtility) {
        bestUtility = utility;
        bestMoves.clear();
        bestMoves.add(move);
      } else if (utility == bestUtility) {
        bestMoves.add(move);
      }
    }
    return bestMoves.get(random.nextInt(bestMoves.size()));
  }

  /**
   * @return a length-three array containing the utility for
   * each player on the board of the chosen representative state.
   */
  public int[] performMaxMaxMax(Board board, int depth) {
    List<Position[]> availableMoves = getAvailableMoves(board);

    Colour colour = board.getTurn();
    Colour otherColour1 = Colour.values()[(colour.ordinal() + 1) % 3];
    Colour otherColour2 = Colour.values()[(colour.ordinal() + 2) % 3];
    int bestUtility = Integer.MIN_VALUE;
    int[] bestUtilities = new int[3];
    Arrays.fill(bestUtilities, Integer.MIN_VALUE);

    for (Position[] move : availableMoves) {
      // Apply the move.
      Board moveBoard;
      try {
        moveBoard = (Board) board.clone();
        moveBoard.move(move[0], move[1]);
      } catch (CloneNotSupportedException | ImpossiblePositionException e) {
        throw new RuntimeException(e);
      }

      // Calculate the utility of this new move.
      int utility;
      int[] utilities = null;
      if (depth == 1 || moveBoard.gameOver()) {
        utility = moveBoard.score(colour);
      } else {
        utilities = performMaxMaxMax(moveBoard, depth - 1);
        utility = utilities[colour.ordinal()];
      }

      // Check if its a new best move.
      if (utility > bestUtility) {
        bestUtility = utility;
        if (utilities != null) {
          bestUtilities = utilities;
        } else {
          bestUtilities[colour.ordinal()] = utility;
          bestUtilities[otherColour1.ordinal()] = moveBoard.score(otherColour1);
          bestUtilities[otherColour2.ordinal()] = moveBoard.score(otherColour2);
        }
      }
    }
    return bestUtilities;
  }

  /**
   * @return the list of available moves on {@param board}.
   */
  public List<Position[]> getAvailableMoves(Board board) {
    List<Position[]> available = new ArrayList<>();

    // For every piece of ours on the board.
    Colour turnColour = board.getTurn();
    for (Position start : board.getPositions(turnColour)) {
      Piece piece = board.getPiece(start);
      PieceType type = piece.getType();

      // For every possible move that piece could take.
      for (Direction[] step : type.getSteps()) {
        try {
          Position end = start;
          for (int reps = 0; reps < type.getStepReps(); ++reps) {
            Position last = end;
            end = board.step(piece, step, end);
            if (!board.isLegalMove(start, end))
              break;

            // Add the move to the available moves.
            available.add(new Position[] {start, end});

            // Reverse the step once crossing into another section.
            if (last.getColour() != end.getColour()) {
              step = Direction.reverse(step);
            }
          }
        } catch (ImpossiblePositionException e) { /* Ignored. */ }
      }
    }
    return available;
  }

  /** @return the Agent's name, for annotating game description. **/
  @Override public String toString(){return name;}

  /** Unused. **/
  @Override public void finalBoard(Board finalBoard){}
}
