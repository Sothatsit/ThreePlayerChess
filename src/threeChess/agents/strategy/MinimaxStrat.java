package threeChess.agents.strategy;

import threeChess.agents.GameLogic.GameConstants;
import threeChess.agents.GameLogic.GameState;
import threeChess.agents.GameLogic.Move;
import threeChess.agents.GameLogic.MoveMany;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Minimax with no frills.
 *
 * @author Paddy Lamont, 22494652
 */
public class MinimaxStrat extends MoveStrat {

  /** Used to randomly select between equivalently good moves. **/
  private static final Random random = new Random();

  /** The depth to check. **/
  protected final int ply;
  /** A list used to store all of the available moves from the initial state. **/
  protected final List<Move> initialMoveList;
  /** An array of state objects to use in simulation of moves. **/
  protected final GameState[] moveStates;

  public MinimaxStrat(GameConstants constants, int ply) {
    this.ply = ply;
    this.initialMoveList = new ArrayList<>(128);
    this.moveStates = new GameState[ply];
    for (int index = 0; index < ply; ++index) {
      moveStates[index] = new GameState(constants);
    }
  }

  /** @return the best move for the current agent by using minimax. **/
  @Override public Move decideMove(GameState state) {
    // We pre-cache these fields to avoid reading them many times.
    int turnColour = state.turnColour;
    int depth = ply;

    // Keep track of the best move we've found so far.
    int bestUtility = Integer.MIN_VALUE;
    Move bestMove = null;

    // Loop through all of the possible moves.
    GameState moveState = moveStates[depth - 1];
    List<Move> availableMoves = initialMoveList;
    state.computeAvailableMoves(availableMoves);

    for (Move move : availableMoves) {
      // Apply the move.
      moveState.copyFrom(state);
      moveState.applyMove(move);

      // Find the utility of this move.
      int utility;
      if (depth == 1 || moveState.gameOverPacked != 0) {
        utility = moveState.getUtility(turnColour);
      } else {
        utility = performMinimax(turnColour, moveState, depth - 1);
      }

      // If its the best utility, record this move. Also logic to select from equivalent utility moves.
      if (utility > bestUtility || (utility == bestUtility && random.nextBoolean())) {
        bestUtility = utility;
        bestMove = move;
      }
    }
    return bestMove;
  }

  /** @return the score of the given state given by minimax. **/
  private int performMinimax(int agentColour, GameState state, int depth) {
    // We pre-cache these fields to avoid reading them many times.
    int turnColour = state.turnColour;
    byte[] pieces = state.pieces;
    Move[] potentialMoves = GameConstants.potentialMovesFlattened;
    int[] potentialMoveDirectives = GameConstants.potentialMovesFlattenedDirectives;
    GameState moveState = moveStates[depth - 1];

    // Maximise when the agent has the current turn.
    boolean maximise = (turnColour == agentColour);

    // Keep track of the best or worst utility.
    int notableUtility = (maximise ? Integer.MIN_VALUE : Integer.MAX_VALUE);

    // Loop through all of the available moves from this state.
    int pieceIndex = (turnColour + 1) * (576 /* pieceIndexStride */);
    for (int index = 96 /* totalSquares */; --index >= 0;) {
      pieceIndex -= 6 /* numPieces */;

      // Find the colour and type of this index, and filter out empty squares and pieces we can't move.
      byte piece = pieces[index];
      int colour = piece & 3;
      if (piece == 0 || colour != turnColour)
        continue;
      int type = (piece >> 2) & 7;

      // Find the potential move array for this piece.
      int directive = potentialMoveDirectives[pieceIndex + type];
      int fromIndex = directive >> 8;
      int length = directive & 255;
      for (int moveIndex = 0; moveIndex < length; ++moveIndex) {
        Move move = potentialMoves[fromIndex + moveIndex];

        byte toPiece = pieces[move.toIndex];
        int toColour = toPiece & 3;
        // For MoveMany moves we can skip forward moves when we hit a piece.
        if (move instanceof MoveMany) {
          if (toPiece != 0) {
            moveIndex = ((MoveMany) move).skipIndex - 1;
            if (toColour == colour)
              continue;
          }
        } else if ((toPiece != 0 && toColour == colour) || !move.isValidMove(state))
          continue;

        // Apply the move.
        moveState.copyFrom(state);
        moveState.applyMove(move);

        // Find a state representative of this move.
        int utility;
        if (depth == 1 || moveState.gameOverPacked != 0) {
          utility = moveState.getUtility(agentColour);
        } else {
          utility = performMinimax(agentColour, moveState, depth - 1);
        }

        // Keep track of the best option available for this agent.
        if (maximise) {
          if (utility > notableUtility) {
            notableUtility = utility;
          }
        } else {
          if (utility < notableUtility) {
            notableUtility = utility;
          }
        }
      }
    }
    return notableUtility;
  }
}
