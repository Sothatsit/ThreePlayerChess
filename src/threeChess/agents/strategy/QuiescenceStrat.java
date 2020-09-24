package threeChess.agents.strategy;

import threeChess.agents.GameLogic.GameConstants;
import threeChess.agents.GameLogic.GameState;
import threeChess.agents.GameLogic.Move;
import threeChess.agents.GameLogic.MoveMany;
import threeChess.agents.IdentitySet;

import java.util.List;
import java.util.Random;

/**
 * This strategy employs maximax, which assumes that each agent chooses the move that
 * maximises their own utility. This strategy also employs quiescence searching, which
 * is a strategy to selectively deepen the search after capturing moves.
 *
 * @author Paddy Lamont, 22494652
 */
public class QuiescenceStrat extends MaximaxStrat {

  /** Used to randomly select between equivalently good moves. **/
  private static final Random random = new Random();

  /** The maximum depth to check all moves. **/
  private final int traditionalPly;
  /** The maximum depth to check for quiescence. **/
  private final int quiescencePly;

  public QuiescenceStrat(GameConstants constants, int ply) {
    this(constants, ply, 1);
  }

  public QuiescenceStrat(GameConstants constants, int ply, int quiescencePly) {
    super(constants, ply + quiescencePly);

    this.traditionalPly = ply;
    this.quiescencePly = quiescencePly;
  }

  /** @return a move list with an initial capacity. **/
  private static IdentitySet<Move> newSet() {
    return new IdentitySet<>(4, 64);
  }

  /** @return the best move for the current agent by using max-max-max and quiescence. **/
  @Override  public final Move decideMove(GameState state) {
    // We pre-cache these fields to avoid reading them many times.
    int turnColour = state.turnColour;
    int depth = traditionalPly;

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
      GameState representativeState;
      if (moveState.gameOverPacked != 0) {
        // Instant win, return this move.
        return move;
      } else if (depth == 1) {
        representativeState = moveState;
      } else {
        boolean isCapture = (state.pieces[move.toIndex] != 0);
        representativeState = findMaxMaxMaxRepresentativeState(
            moveState, depth - 1, false, isCapture
        );
        if (representativeState == null)
          continue;
      }
      int utility = representativeState.getUtility(turnColour);

      // If its the best utility, record this move. Also logic to select from equivalent utility moves.
      if (utility > bestUtility || (utility == bestUtility && random.nextBoolean())) {
        bestUtility = utility;
        bestMove = move;
      }
    }
    return bestMove;
  }

  /** @return the predicted end state {@param depth} turns into the future by using minimax. **/
  private GameState findMaxMaxMaxRepresentativeState(
      GameState state, int depth, boolean inQuiescence, boolean lastMoveCaptured) {

    // We pre-cache these fields to avoid reading them many times.
    int turnColour = state.turnColour;
    byte[] pieces = state.pieces;
    Move[] potentialMoves = GameConstants.potentialMovesFlattened;
    int[] potentialMoveDirectives = GameConstants.potentialMovesFlattenedDirectives;

    // We keep track of the best state we've found so far.
    int bestUtility = Integer.MIN_VALUE;
    int stateIndex = (inQuiescence ? ply - depth : depth - 1);
    GameState bestState = bestStates[stateIndex];
    GameState moveState = moveStates[stateIndex];
    boolean bestIsCapture = false;

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
        GameState representativeState;
        boolean isCapture = (toPiece != 0);
        if (moveState.gameOverPacked != 0) {
          // Instant win, return this move.
          bestState.softCopyFrom(moveState);
          return bestState;
        } else if (inQuiescence && !isCapture && !lastMoveCaptured) {
          representativeState = moveState;
        } else if (depth == 1) {
          // Perform quiescence search if the last move is taking a piece.
          if (quiescencePly <= 0 || inQuiescence || (!isCapture && !lastMoveCaptured)) {
            representativeState = moveState;
          } else {
            representativeState = findMaxMaxMaxRepresentativeState(
                moveState, quiescencePly, true, isCapture
            );
          }
        } else {
          representativeState = findMaxMaxMaxRepresentativeState(
              moveState, depth - 1, inQuiescence, isCapture
          );
        }
        if (representativeState == null)
          continue;
        int utility = representativeState.getUtility(turnColour);

        // Keep track of the best option available for this agent.
        if (utility > bestUtility || (utility == bestUtility && isCapture)) {
          bestUtility = utility;
          bestState.softCopyFrom(representativeState);
          bestIsCapture = isCapture;
        }
      }
    }
    // If we found a best state, return it. In quiescence, we only return capturing moves.
    if ((!inQuiescence && bestUtility > Integer.MIN_VALUE) || bestIsCapture)
      return bestState;
    // In quiescence, we always want to return a state.
    return inQuiescence ? state : null;
  }
}
