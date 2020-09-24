package threeChess.agents.strategy;

import threeChess.agents.GameLogic.GameConstants;
import threeChess.agents.GameLogic.GameState;
import threeChess.agents.GameLogic.Move;
import threeChess.agents.GameLogic.MoveMany;

import java.util.*;

/**
 * Principal Variation Search is a faster alternative to alpha-beta pruning minimax.
 *
 * Paper on Minimal Window Search:
 * http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.106.2074&rep=rep1&type=pdf
 *
 * @author Paddy Lamont, 22494652
 */
public class PrincipalVariationSearchStrat extends MoveStrat {

  /** Used to randomly select between equivalently good moves. **/
  private static final Random random = new Random();

  /** The depth to check. **/
  protected final int ply;
  /** A list used to store all of the available moves from the initial state. **/
  protected final List<Move> initialMoveList;
  /** An array of state objects to use in simulation of moves. **/
  protected final GameState[] moveStates;

  public PrincipalVariationSearchStrat(GameConstants constants, int ply) {
    this.ply = ply;
    this.initialMoveList = new ArrayList<>(128);
    this.moveStates = new GameState[ply];
    for (int index = 0; index < ply; ++index) {
      moveStates[index] = new GameState(constants);
    }
  }

  /** @return the best move for the current agent by using Principal Variation Search. **/
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

    // Try to find the best move.
    for (Move move : availableMoves) {
      moveState.copyFrom(state);
      moveState.applyMove(move);

      // Find the utility of this move.
      int utility;
      if (depth == 1 || moveState.gameOverPacked != 0) {
        utility = moveState.getUtility(turnColour);
      } else {
        utility = performPVS(turnColour, moveState, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
      }

      // If its the best utility, record this move. Also logic to select from equivalent utility moves.
      if (utility > bestUtility || (utility == bestUtility && random.nextBoolean())) {
        bestUtility = utility;
        bestMove = move;
      }
    }
    return bestMove;
  }

  /** @return the score of the given state given by principal variation search. **/
  private int performPVS(int agentColour, GameState state, int depth, int alpha, int beta) {
    // We pre-cache these fields to avoid reading them many times.
    int turnColour = state.turnColour;
    int nextTurnColour = (turnColour + 1) % 3;
    byte[] pieces = state.pieces;
    Move[] potentialMoves = GameConstants.potentialMovesFlattened;
    int[] potentialMoveDirectives = GameConstants.potentialMovesFlattenedDirectives;
    GameState moveState = moveStates[depth - 1];

    // We have to selectively negate alpha, beta, and the utility values based on whose turn it is.
    // This is necessary as alpha and beta should not flip between both opponents, but should flip
    // when switching between the agent and the opponents.
    boolean isAgent = (turnColour == agentColour);
    int mul = (isAgent ? 1 : -1);
    boolean keepAlphaBeta = (!isAgent && nextTurnColour != agentColour);

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
          utility = mul * moveState.getUtility(agentColour);
        } else {
          int callAlpha, callBeta;
          callAlpha = (keepAlphaBeta ? alpha : -alpha - 1);
          callBeta = (keepAlphaBeta ? alpha + 1 : -alpha);
          utility = mul * performPVS(agentColour, moveState, depth - 1, callAlpha, callBeta);
          if (alpha < utility && utility < beta) {
            callAlpha = (keepAlphaBeta ? utility : -beta);
            callBeta = (keepAlphaBeta ? beta : -utility);
            utility = mul * performPVS(agentColour, moveState, depth - 1, callAlpha, callBeta);
          }
        }

        // Keep track of the best option available for this agent.
        if (utility > alpha) {
          alpha = utility;
          if (alpha >= beta)
            break;
        }
      }
    }
    return mul * alpha;
  }
}
