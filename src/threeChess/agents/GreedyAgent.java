package threeChess.agents;

import threeChess.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A greedy agent that will try to take the piece with the highest value,
 * or otherwise will just pick a random move.
 *
 * @author Paddy Lamont, 22494652
 */
public class GreedyAgent extends Agent{

  private static final Random random = new Random();
  private static final String name = "Greedy";

  /** A no argument constructor, required for tournament management. **/
  public GreedyAgent() {}

  /** @return an identical greedy agent. **/
  @Override public GreedyAgent clone() {
    return new GreedyAgent();
  }

  /**
   * Searches through all possible moves in an attempt to find any moves that
   * are able to take pieces. Returns a random move from the set of moves found.
   */
  @Override public Position[] playMove(Board board){
    int bestTakeValue = 0;
    List<Position[]> bestMoves = new ArrayList<>();

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

            // If the move is to take another piece, record the value of the take.
            Piece endPiece = board.getPiece(end);
            if (endPiece != null) {
              if (endPiece.getColour() != turnColour) {
                int takeValue = endPiece.getType().getValue();
                if (takeValue > bestTakeValue) {
                  bestMoves.clear();
                }
                if (takeValue >= bestTakeValue) {
                  bestTakeValue = takeValue;
                  bestMoves.add(new Position[] {start, end});
                }
              }
              break;
            } else if (bestTakeValue == 0) {
              bestMoves.add(new Position[] {start, end});
            }

            // Reverse the step once crossing into another section.
            if (last.getColour() != end.getColour()) {
              step = Direction.reverse(step);
            }
          }
        } catch (ImpossiblePositionException e) { /* Ignored. */ }
      }
    }
    return bestMoves.get(random.nextInt(bestMoves.size()));
  }

  /** @return the Agent's name, for annotating game description. **/
  @Override public String toString(){return name;}

  /** Unused. **/
  @Override public void finalBoard(Board finalBoard) {}
}
