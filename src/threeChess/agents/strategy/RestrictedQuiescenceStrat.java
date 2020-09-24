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
 * maximises their own utility. This strategy also employs restricted quiescence searching,
 * which is a strategy to selectively deepen the search after capturing moves. Restricted
 * refers to the fact that the quiescence only checks moves that were made available
 * because of the capturing move.
 *
 * @author Paddy Lamont, 22494652
 */
public class RestrictedQuiescenceStrat extends MaximaxStrat {

  /** Used to randomly select between equivalently good moves. **/
  private static final Random random = new Random();

  /** The maximum depth to check for quiescence. **/
  private final int quiescencePly;
  /** An array of lists to use in determination of the available moves before quiescence search. **/
  private final IdentitySet<Move>[] moveLists;
  /** An array of state objects to use in simulation of moves for quiescence search. **/
  private final GameState[] quiescenceMoveStates;
  /** An array of state objects to use in storing the best found states for quiescence search. **/
  private final GameState[] quiescenceBestStates;
  /** An array of lists to use in determination of the available moves for quiescence search. **/
  private final IdentitySet<Move>[] quiescenceMoveLists;

  public RestrictedQuiescenceStrat(GameConstants constants, int ply) {
    this(constants, ply, 1);
  }

  public RestrictedQuiescenceStrat(GameConstants constants, int ply, int quiescencePly) {
    super(constants, ply);

    this.quiescencePly = quiescencePly;
    this.moveLists = new IdentitySet[ply + 2];
    for (int index = 0; index < moveLists.length; ++index) {
      moveLists[index] = newSet();
    }

    this.quiescenceMoveStates = new GameState[quiescencePly];
    this.quiescenceBestStates = new GameState[quiescencePly];
    this.quiescenceMoveLists = new IdentitySet[quiescencePly];
    for (int index = 0; index < quiescencePly; ++index) {
      quiescenceMoveStates[index] = new GameState(constants);
      quiescenceBestStates[index] = new GameState(constants);
      quiescenceMoveLists[index] = newSet();
    }
  }

  /** @return a move list with an initial capacity. **/
  private static IdentitySet<Move> newSet() {
    return new IdentitySet<>(4, 64);
  }

  /** @return the best move for the current agent by using max-max-max and quiescence. **/
  @Override  public final Move decideMove(GameState state) {
    // We pre-cache these fields to avoid reading them many times.
    int turnColour = state.turnColour;
    int depth = ply;

    // Keep track of the best move we've found so far.
    int bestUtility = Integer.MIN_VALUE;
    Move bestMove = null;

    // Setup the capturing move lists.
    IdentitySet<Move> capturingMoves = moveLists[depth - 1];
    IdentitySet<Move> cMoves1Up = moveLists[depth];
    IdentitySet<Move> cMoves2Up = moveLists[depth + 1];
    state.computeCapturingMoves(capturingMoves);
    cMoves1Up.clear();
    cMoves2Up.clear();

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
            moveState, depth - 1, isCapture,
            capturingMoves, cMoves1Up, cMoves2Up
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

    // If we couldn't find any suitable moves, just return a random move.
    return bestMove != null ? bestMove : availableMoves.get(random.nextInt(availableMoves.size()));
  }

  /** @return the predicted end state {@param depth} turns into the future by using minimax. **/
  private GameState findMaxMaxMaxRepresentativeState(
      GameState state, int depth, boolean lastMoveCaptured,
      IdentitySet<Move> cMoves1Up, IdentitySet<Move> cMoves2Up, IdentitySet<Move> cMoves3Up) {

    // We pre-cache these fields to avoid reading them many times.
    int turnColour = state.turnColour;
    byte[] pieces = state.pieces;
    Move[] potentialMoves = GameConstants.potentialMovesFlattened;
    int[] potentialMoveDirectives = GameConstants.potentialMovesFlattenedDirectives;

    // Update the capturing move lists.
    IdentitySet<Move> capturingMoves = moveLists[depth - 1];
    state.computeCapturingMoves(capturingMoves);

    // We keep track of the best state we've found so far.
    int bestUtility = Integer.MIN_VALUE;
    GameState bestState = bestStates[depth - 1];
    GameState moveState = moveStates[depth - 1];

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
        if (moveState.gameOverPacked != 0) {
          // Instant win, return this move.
          bestState.softCopyFrom(moveState);
          return bestState;
        } else if (depth == 1) {
          // Perform quiescence search if the last move is taking a piece.
          boolean isCapture = (toPiece != 0);
          if (quiescencePly == 0 || (!isCapture && !lastMoveCaptured) || cMoves3Up.contains(move)) {
            representativeState = moveState;
          } else {
            representativeState = performQuiescenceSearch(
                moveState, quiescencePly, isCapture,
                capturingMoves, cMoves1Up, cMoves2Up
            );
          }
        } else {
          boolean isCapture = (toPiece != 0);
          representativeState = findMaxMaxMaxRepresentativeState(
              moveState, depth - 1, isCapture,
              cMoves1Up, cMoves2Up, cMoves3Up
          );
          if (representativeState == null)
            continue;
        }
        int utility = representativeState.getUtility(turnColour);

        // Keep track of the best option available for this agent.
        if (utility > bestUtility) {
          bestUtility = utility;
          bestState.softCopyFrom(representativeState);
        }
      }
    }
    return bestUtility > Integer.MIN_VALUE ? bestState : null;
  }

  /**
   * Performs a quiescence search for selectively deepening evaluation of capturing leaf nodes.
   *
   * @param state the game state after the last capturing move.
   * @param depth the maximum depth to perform the quiescence search to.
   * @param lastMoveCaptured whether the move before this call captured a piece.
   * @param cMoves1Up the capturing moves available before the previous move.
   * @param cMoves2Up the capturing moves available before the two previous moves.
   * @param cMoves3Up the capturing moves available before the three previous moves.
   *
   * @return a representative state after the quiescence search is complete.
   */
  public final GameState performQuiescenceSearch(
      GameState state, int depth, boolean lastMoveCaptured,
      IdentitySet<Move> cMoves1Up,
      IdentitySet<Move> cMoves2Up,
      IdentitySet<Move> cMoves3Up) {

    // We pre-cache these fields to avoid reading them many times.
    int turnColour = state.turnColour;
    byte[] pieces = state.pieces;
    Move[] potentialMoves = GameConstants.potentialMovesFlattened;
    int[] potentialMoveDirectives = GameConstants.potentialMovesFlattenedDirectives;

    // Compute all of the capturing moves in the new state.
    IdentitySet<Move> capturingMoves = quiescenceMoveLists[depth - 1];
    state.computeCapturingMoves(capturingMoves);

    // We keep track if we find any enticing captures.
    int bestUtility = Integer.MIN_VALUE;
    GameState bestState = quiescenceBestStates[depth - 1];
    GameState moveState = quiescenceMoveStates[depth - 1];
    boolean bestMoveIsCapture = false;

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
        } else if (depth == 1 || (!isCapture && !lastMoveCaptured) || cMoves3Up.contains(move)) {
          representativeState = moveState;
        } else {
          representativeState = performQuiescenceSearch(
              moveState, depth - 1, isCapture, capturingMoves, cMoves1Up, cMoves2Up
          );
          if (representativeState == null)
            continue;
        }
        int utility = representativeState.getUtility(turnColour);

        // Keep track of the best option available for this agent.
        if (utility > bestUtility || (utility == bestUtility && isCapture)) {
          bestUtility = utility;
          bestState.softCopyFrom(representativeState);
          bestMoveIsCapture = isCapture;
        }
      }
    }
    return bestMoveIsCapture ? bestState : state;
  }
}
