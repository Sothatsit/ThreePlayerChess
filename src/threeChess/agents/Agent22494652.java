package threeChess.agents;

import threeChess.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Combination of Brutus, an iterative deepening agent, and the maximax strategy to decide its moves.
 *
 * Due to the _need for speed_, some sacrifices to code readability have had to be made,
 * although I've attempted to keep it as clear as I can with plenty of comments throughout.
 *
 * Macro-Optimisation Summary:
 * - Incremental updating of utilities as moves are made on GameStates, instead
 *   of re-calculating the whole utility value for every state.
 * - Pre-computed move lists from every position on the board for every piece,
 *   which limits the amount of potential moves you have to check.
 * - Move skipping within these move lists when one move failing implies several more failing.
 *   This is available for bishop, rook, and queen moves where they can move many places at a time.
 * - Piece packing into one byte per position on the board, allowing quick iteration and copying of states.
 *
 * Some of the micro-optimisations used in the internal game state representation are explained in
 * https://www.infoworld.com/article/2077647/make-java-fast--optimize-.html?page=3.
 *
 * @author Paddy Lamont, 22494652
 */
public class Agent22494652 extends Agent {

  private static final Random random = new Random();

  /** The initial ply value to check in our iterative deepening. **/
  private static final int INITIAL_PLY = 2;
  /** The maximum ply value after which to abort deepening. **/
  private static final int MAX_PLY = 12;
  /** The number of turns that we use to estimate how much time we should spend per turn. **/
  private static final long EXPECTED_GAME_TURNS = 20;
  /** The amount of turns into the future it will account for in deciding how much time to take. **/
  private static final int FUTURE_TURN_BUDGET = 12;

  /** The name of this agent. **/
  private final String name;
  /** A time limit for Brutus to play to when there is no time limit. **/
  private final int artificialTimeLimitSeconds;
  /** The game constants used to calculate utility in the game. **/
  private final CombinedGameConstants constants;
  /** Used to hold the initial state for each move. **/
  private final GameState initialState;
  /** The length of the current game in nanoseconds. **/
  private long gameLengthNanos = 60 * 1000L * 1_000_000L;
  /** The number of nanoseconds to spend on average per game. **/
  private long nanosPerTurn = gameLengthNanos / EXPECTED_GAME_TURNS;

  /** The game state used to compute the initial move states. **/
  private final GameState moveState;
  /** The list we re-use for storing the list of available moves to take. **/
  private final List<Move> availableMoves = new ArrayList<>();

  /** The move decision strategies to use at each ply level. **/
  private final MaximaxStrategy[] strategies = new MaximaxStrategy[MAX_PLY];

  public Agent22494652() {
    this(20);
  }

  /**
   * @param artificialTimeLimitSeconds the time limit for Brutus to play to when there is no time limit.
   */
  public Agent22494652(int artificialTimeLimitSeconds) {
    this.name = "Brutus";
    this.artificialTimeLimitSeconds = artificialTimeLimitSeconds;
    this.constants = CombinedGameConstants.createDefault();
    this.initialState = new GameState(constants);
    this.moveState = new GameState(constants);
    // Generate a strategy instance for every possible ply up to the maximum.
    for (int ply = 1; ply <= MAX_PLY; ++ply) {
      strategies[ply - 1] = new MaximaxStrategy(constants, ply);
    }
  }

  @Override
  public Agent22494652 clone() {
    return new Agent22494652(artificialTimeLimitSeconds);
  }

  /** @return the best available move as considered by this agent's strategy in the remaining time. **/
  @Override public Position[] playMove(Board board) {
    // Calculate the amount of time we have to spend per turn over the duration of the game.
    boolean isFirstMove = (board.getMoveCount() < 3);
    long timeLeftNanos = board.getTimeLeft(board.getTurn()) * 1_000_000L;

    // If we have no time left but are being asked to make a move,
    // then this game is not timed.
    if (timeLeftNanos == 0) {
      timeLeftNanos = artificialTimeLimitSeconds * 1_000_000_000L;
    }

    if (isFirstMove || timeLeftNanos > gameLengthNanos) {
      gameLengthNanos = timeLeftNanos;
      nanosPerTurn = gameLengthNanos / EXPECTED_GAME_TURNS;
    }

    // Copy from the external board state to our internal initial state.
    initialState.copyFrom(board);
    initialState.computeAvailableMoves(availableMoves);
    constants.updateUtilities(initialState);

    // Check if any of the initial moves are winning.
    for (Move move : availableMoves) {
      moveState.copyFrom(initialState);
      moveState.applyMove(move);

      // Instant win! Take this move.
      if (moveState.gameOverPacked != 0)
        return GameConstants.convertMoveToPositions(move);
    }

    // Use iterative deepening to determine the best move to take.
    Move move = performIterativeDeepening(board);
    return GameConstants.convertMoveToPositions(move);
  }

  /** Uses iterative deepening to evaluate  **/
  private Move performIterativeDeepening(Board board) {
    // Check how much time we have available and if we don't have much left then don't use as much time.
    long gameRemainingNanos = board.getTimeLeft(board.getTurn()) * 1_000_000L;
    long turnBudgetNanosPerTurn = gameRemainingNanos / FUTURE_TURN_BUDGET;
    boolean throttled = (turnBudgetNanosPerTurn < nanosPerTurn);
    long targetNanosPerTurn = (throttled ? turnBudgetNanosPerTurn : nanosPerTurn);

    // Use iterative-deepening to determine a move using our maximax approach.
    Move result = availableMoves.get(random.nextInt(availableMoves.size()));

    int ply = INITIAL_PLY;
    long lastPly = 0, lastPlyDuration = 0;
    long start = System.nanoTime();
    long elapsed, remaining, plyStart, plyDuration, nextPlyDuration;
    do {
      plyStart = System.nanoTime();

      // Test the current ply.
      MaximaxStrategy strategy = strategies[ply - 1];
      Move move = strategy.decideMove(initialState);
      if (move != null) {
        result = move;
      }

      // Calculate how long it took to compute this ply value.
      plyDuration = System.nanoTime() - plyStart;
      elapsed = System.nanoTime() - start;
      remaining = targetNanosPerTurn - elapsed;

      // We use the time difference between ply values to estimate the duration of higher ply values.
      // Default to a value of 1 for the first ply value as we almost always want to promote from it by 2.
      long plyTimeMul = (lastPly > 0 ? (plyDuration + lastPlyDuration - 1) / lastPlyDuration : 0);
      if (ply - lastPly == 2) {
        plyTimeMul = (long) Math.ceil(Math.pow(plyTimeMul, 0.4));
      }
      plyTimeMul = Math.max(1, plyTimeMul) + 4; // Fail-safe minimum value
      lastPly = ply;
      lastPlyDuration = plyDuration;

      // We predict how long 1 ply value higher would take.
      // If its below our remaining time, we deepen to check it.
      nextPlyDuration = plyTimeMul * plyDuration;
      if (nextPlyDuration >= remaining)
        break;

      ply += 1;
    } while (ply < MAX_PLY);

    return result;
  }

  /** @return the name of this agent. **/
  @Override public String toString() {
    return name;
  }

  /** Unused. **/
  @Override public void finalBoard(Board finalBoard) {}

  /**
   * Maximax assumes that each agent chooses the move that maximises their own utility.
   */
  public static class MaximaxStrategy {

    /** The depth to check using maximax. **/
    protected final int ply;
    /** A list used to store all of the available moves from the initial state. **/
    protected final List<Move> initialMoveList;
    /** An array of state objects to use in simulation of moves. **/
    protected final GameState[] moveStates;
    /** An array of state objects to use in storing the best found states. **/
    protected final GameState[] bestStates;

    public MaximaxStrategy(GameConstants constants, int ply) {
      this.ply = ply;
      this.initialMoveList = new ArrayList<>(128);
      this.moveStates = new GameState[ply];
      this.bestStates = new GameState[ply];
      for (int index = 0; index < ply; ++index) {
        moveStates[index] = new GameState(constants);
        bestStates[index] = new GameState(constants);
      }
    }

    /** @return the best move for the current agent by using max-max-max. **/
    public Move decideMove(GameState state) {
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
        GameState representativeState;
        if (moveState.gameOverPacked != 0) {
          // Instant win, return this move.
          return move;
        } else if (depth == 1) {
          representativeState = moveState;
        } else {
          representativeState = findMaxMaxMaxRepresentativeState(moveState, depth - 1);
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
    protected GameState findMaxMaxMaxRepresentativeState(GameState state, int depth) {
      // We pre-cache these fields to avoid reading them many times.
      int turnColour = state.turnColour;
      byte[] pieces = state.pieces;
      Move[] potentialMoves = GameConstants.potentialMovesFlattened;
      int[] potentialMoveDirectives = GameConstants.potentialMovesFlattenedDirectives;

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
            representativeState = moveState;
          } else {
            representativeState = findMaxMaxMaxRepresentativeState(moveState, depth - 1);
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
  }

  /**
   * A game state implementation that has been programmed for speed.
   */
  public static final class GameState {

    /**
     * A byte for each square on the board that contains whether there is a piece in that square,
     * the type of that piece, and the colour of that piece.
     *
     * Bits:
     *  0 0 P T T T C C
     *
     * 0 - Always zero
     * P - 1 if there is a piece, 0 otherwise.
     * T - The type of the piece, or 0 if there is no piece.
     * C - The colour of the piece, or 0 if there is no piece.
     *
     * Setup so that if the piece byte is zero, the square is empty.
     */
    public final byte[] pieces = new byte[GameConstants.totalSquares];

    /** The colour of the agent whose turn it currently is. **/
    public int turnColour = -1;

    /**
     * Two rightmost bits store the colour of the loser.
     * Next two rightmost bits store the colour of the winner.
     * If gameOverPacked == 0, then the game isn't over.
     */
    public int gameOverPacked = 0;

    /** The computed utility values for each agent. **/
    public final int[] agentUtilities = new int[GameConstants.numColours];

    /** The constants that should be used for keeping track of utility of states. **/
    public final GameConstants constants;

    public GameState(GameConstants constants) {
      this.constants = constants;
    }

    /** Copies the state of {@param board} into this state. **/
    public final void copyFrom(Board board) {
      this.turnColour = (byte) board.getTurn().ordinal();
      if (board.gameOver()) {
        this.gameOverPacked = (byte) ((board.getWinner().ordinal() << 2) | board.getLoser().ordinal());
      } else {
        this.gameOverPacked = 0;
      }
      Arrays.fill(pieces, (byte) 0);

      for (Position position : Position.values()) {
        Piece piece = board.getPiece(position);
        if (piece != null) {
          int index = GameConstants.getIndex(position);
          pieces[index] = (byte) (32 | (piece.getType().ordinal() << 2) | piece.getColour().ordinal());
        }
      }
      calculateUtilities();
    }

    /** Copies the state of {@param state} into this state. **/
    public final void copyFrom(GameState state) {
      System.arraycopy(state.pieces, 0, pieces, 0, 96 /* totalSquares */);
      System.arraycopy(state.agentUtilities, 0, agentUtilities, 0, 3 /* numColours */);
      gameOverPacked = state.gameOverPacked;
      turnColour = state.turnColour;
    }

    /**
     * Copies SOME LIMITED STATE from {@param state} into this state.
     * Specifically, this avoids copying any state specific to the pieces on the board.
     */
    public void softCopyFrom(GameState state) {
      System.arraycopy(state.agentUtilities, 0, agentUtilities, 0, 3 /* numColours */);
      gameOverPacked = state.gameOverPacked;
      turnColour = state.turnColour;
    }

    /** Applies the move {@param move} to this state. **/
    public final void applyMove(Move move) {
      // Move the rook if this is a castling move.
      if (move instanceof KingMoveCastle) {
        KingMoveCastle castle = (KingMoveCastle) move;
        movePiece(castle.castleIndex, castle.rookPlaceIndex);
      }

      // Move the piece.
      int fromIndex = move.fromIndex;
      int toIndex = move.toIndex;
      movePiece(fromIndex, toIndex);

      // Promote the queen if needed in a pawn move.
      if (move instanceof PawnMove && ((PawnMove) move).promoteToQueen) {
        promoteToQueen(toIndex);
      }

      // Advance the turn to the next agent.
      if (gameOverPacked == 0) {
        turnColour = (byte) ((turnColour + 1 + ((turnColour + 2) >> 2)) & 3);
      } else {
        // Update to the game over utilities.
        calculateUtilities();
      }
    }

    /** @return the utility of this state for the agent with the given colour {@param colour}. **/
    public final int getUtility(int colour) {
      return agentUtilities[colour];
    }

    /** Computes the list of available moves from this state into {@param availableMoves}. **/
    public final void computeAvailableMoves(List<Move> availableMoves) {
      availableMoves.clear();

      // We pre-cache these fields to avoid reading them many times.
      int agentColour = this.turnColour;
      byte[] pieces = this.pieces;
      Move[] potentialMoves = GameConstants.potentialMovesFlattened;
      int[] potentialMoveDirectives = GameConstants.potentialMovesFlattenedDirectives;

      // Loop through all of the possible moves, and add them to the list of available moves.
      int pieceIndex = agentColour * (576 /* pieceIndexStride */) - (6 /* numPieces */);
      for (int index = 0; index < 96 /* totalSquares */; ++index) {
        pieceIndex += 6 /* numPieces */;

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
          } else if ((toPiece != 0 && toColour == colour) || !move.isValidMove(this))
            continue;

          // This move is valid.
          availableMoves.add(move);
        }
      }
    }

    /** Moves a piece on the board. **/
    private void movePiece(int fromIndex, int toIndex) {
      // We pre-cache these fields to avoid reading them many times.
      byte[] pieces = this.pieces;
      int[] agentUtilities = this.agentUtilities;
      short[] pieceUtilities = constants.pieceUtilities;

      // Record the values of the captured piece.
      byte capturedPiece = pieces[toIndex];
      int capturedColour = capturedPiece & 3;
      int capturedType = (capturedPiece >> 2) & 7;
      short capturedUtility = -1;
      // If a non-king piece has been captured, find its utility.
      if (capturedPiece != 0 && capturedType != 5 /* KING */) {
        int capturedPieceIndex = capturedColour * (576 /* pieceIndexStride */) + toIndex * (6 /* numPieces */) + capturedType;
        capturedUtility = pieceUtilities[capturedPieceIndex];
      }

      // Move the piece.
      byte fromPiece = pieces[fromIndex];
      int fromColour = fromPiece & 3;
      int fromType = (fromPiece >> 2) & 7;
      pieces[fromIndex] = 0;
      pieces[toIndex] = fromPiece;

      // Update the utility of the piece being moved.
      int basePieceIndex = fromColour * (576 /* pieceIndexStride */) + fromType;
      int fromPieceIndex = basePieceIndex + fromIndex * (6 /* numPieces */);
      int toPieceIndex = basePieceIndex + toIndex * (6 /* numPieces */);
      int utilityChange = pieceUtilities[toPieceIndex] - pieceUtilities[fromPieceIndex];
      agentUtilities[fromColour] += constants.selfWeight*utilityChange;
      agentUtilities[(fromColour + 1 + ((fromColour + 2) >> 2)) & 3] -= 10*utilityChange;
      agentUtilities[(fromColour + 2 + ((fromColour + 3) >> 2)) & 3] -= 10*utilityChange;

      // If no piece has been captured, we can stop here.
      if (capturedPiece == 0)
        return;

      // Captured a King, handle game over state.
      if (capturedType == 5 /* KING */) {
        gameOverPacked = (byte) ((fromColour << 2) | capturedColour);
        return;
      }

      // Update the utility of the piece that was captured.
      agentUtilities[capturedColour] -= constants.selfWeight*capturedUtility;
      agentUtilities[(capturedColour + 1 + ((capturedColour + 2) >> 2)) & 3] += 10*capturedUtility;
      agentUtilities[(capturedColour + 2 + ((capturedColour + 3) >> 2)) & 3] += 10*capturedUtility;
    }

    /** Promotes the piece at the given index to a queen. **/
    private void promoteToQueen(int index) {
      // We pre-cache this to avoid reading it many times.
      byte[] pieces = this.pieces;
      int[] agentUtilities = this.agentUtilities;
      short[] pieceUtilities = constants.pieceUtilities;

      // Promote the piece to a queen.
      byte fromPiece = pieces[index];
      int colour = fromPiece & 3;
      int fromType = (fromPiece >> 2) & 7;
      pieces[index] = (byte) ((48 /* P-Bit | (QUEEN (4) << 2) */) | colour);

      // Update the utility of the piece after the promotion.
      int basePieceIndex = colour * (576 /* pieceIndexStride */) + index * (6 /* numPieces */);
      int fromPieceIndex = basePieceIndex + fromType;
      int toPieceIndex = basePieceIndex + (4 /* QUEEN */);
      int utilityChange = pieceUtilities[toPieceIndex] - pieceUtilities[fromPieceIndex];
      agentUtilities[colour] += constants.selfWeight*utilityChange;
      agentUtilities[(colour + 1 + ((colour + 2) >> 2)) & 3] -= 10*utilityChange;
      agentUtilities[(colour + 2 + ((colour + 3) >> 2)) & 3] -= 10*utilityChange;
    }

    /** Calculates the utility of this state for all agents. **/
    public final void calculateUtilities() {
      // We pre-cache these fields to avoid reading them many times.
      byte[] pieces = this.pieces;
      int[] agentUtilities = this.agentUtilities;
      int selfWeight = constants.selfWeight;
      short[] pieceUtilities = constants.pieceUtilities;

      // Standard utility values for game over situation.
      if (gameOverPacked != 0) {
        int winner = gameOverPacked >> 2;
        int loser = gameOverPacked & 3;
        agentUtilities[winner] = 1_000_000;
        agentUtilities[loser] = -1_000_000;
        agentUtilities[3 - winner - loser] = -500_000;
        return;
      }

      // Add the value of all the pieces on the board.
      Arrays.fill(agentUtilities, 0);
      for (int index = 0; index < 96 /* totalSquares */; ++index) {
        byte piece = pieces[index];
        if (piece == 0)
          continue;
        int colour = piece & 3;
        int type = (piece >> 2) & 7;

        // Value given to the piece that is symmetrical.
        int pieceIndex = colour * (576 /* pieceIndexStride */) + index * (6 /* numPieces */) + type;
        int value = pieceUtilities[pieceIndex];

        // Add the value to the utilities.
        agentUtilities[colour] += selfWeight*value;
        agentUtilities[(colour + 1) % 3] -= 10*value;
        agentUtilities[(colour + 2) % 3] -= 10*value;
      }
    }
  }

  /**
   * Interpolates between one set of game constants for the start of the game,
   * and another set of game constants for the end of the game.
   *
   * {@link CombinedGameConstants#updateUtilities(GameState)} should be called periodically
   * during the game to update the constants based on the state of the game.
   */
  public static class CombinedGameConstants extends GameConstants implements Cloneable {

    /** The sum of piece values over the whole board at the start of a game. **/
    private static final int START_TOTAL_VALUE;
    static {
      int totalValue = 0;
      totalValue += PieceType.PAWN.getValue()   * 8 * numColours;
      totalValue += PieceType.KNIGHT.getValue() * 2 * numColours;
      totalValue += PieceType.BISHOP.getValue() * 2 * numColours;
      totalValue += PieceType.ROOK.getValue()   * 2 * numColours;
      totalValue += PieceType.QUEEN.getValue()  * 1 * numColours;
      totalValue += PieceType.KING.getValue()   * 1 * numColours;
      START_TOTAL_VALUE = totalValue;
    }
    /** The default values for all of the pieces on the board. **/
    private static final int[] PIECE_VALUES = new int[PieceType.values().length];
    static {
      for (PieceType type : PieceType.values()) {
        PIECE_VALUES[type.ordinal()] = type.getValue();
      }
    }

    /**
     * Game constants that were determined favourable in winning
     * the start of games through the use of a genetic algorithm.
     */
    public static final GameConstants START_GAME = new GameConstants(
        11, new double[] {4.3, 16.3, 17.9, 19.0, 36.6, 0.0}, 5.1, 2.8
    );

    /**
     * Game constants that were determined favourable in winning
     * end games through the use of a genetic algorithm.
     */
    public static final GameConstants END_GAME = new GameConstants(
        11, new double[] {8.2, 16.2, 12.2, 17.5, 35.5, 0.0}, 8.4, 4.9
    );

    /** The constants to be used at the start of the game. **/
    public final GameConstants start;
    /** The constants to be used at the end of the game. **/
    public final GameConstants end;

    public CombinedGameConstants(GameConstants start, GameConstants end) {
      super(start.selfWeight, start.typeValues, start.pawnRowValue, start.moveCountValue);
      this.start = start;
      this.end = end;
    }

    /** @return a default set of combined game constants. **/
    public static CombinedGameConstants createDefault() {
      return new CombinedGameConstants(START_GAME, END_GAME);
    }

    /**
     * Updates the interpolation between the start and end game constants based on the given state {@param state}.
     * This should be called only once per move that is decided, otherwise the utility values may become
     * out of sync due to them being incrementally updated.
     */
    public void updateUtilities(GameState state) {
      // Determine how much value remains on the board.
      int totalValue = 0;
      for (int index=0, len=totalSquares; index < len; ++index) {
        byte piece = state.pieces[index];
        if (piece != 0) {
          int type = (piece >> 2) & 7;
          totalValue += PIECE_VALUES[type];
        }
      }
      double ratio = 1.0d - ((double) totalValue / START_TOTAL_VALUE);

      // Interpolate the parameters.
      selfWeight = (int) Math.round(interp(start.selfWeight, end.selfWeight, ratio));
      interpArray(typeValues, start.typeValues, end.typeValues, ratio);
      pawnRowValue = interp(start.pawnRowValue, end.pawnRowValue, ratio);
      moveCountValue = interp(start.moveCountValue, end.moveCountValue, ratio);

      // Finally, re-calculate the piece utility table.
      recalculatePieceUtilities();
    }
  }

  /**
   * Contains the constants used for evaluating the utility of states,
   * and evaluating the potential moves from a state.
   */
  public static class GameConstants {

    public static final int sideDepth = 4;
    public static final int sideLength = 8;
    public static final int sideSquares = sideDepth * sideLength; // 32
    public static final int sides = 3;
    public static final int totalSquares = sideSquares * sides; // 96

    public static final int numColours = Colour.values().length; // 3
    public static final int numPieces = PieceType.values().length; // 6

    /** Contains an array giving the end indices for a movement in every direction from every tile. **/
    private static final int[][] moveToIndices = new int[totalSquares][Direction.values().length];
    static {
      for (int fromIndex = 0; fromIndex < totalSquares; ++fromIndex) {
        Position from = convertIndexToPosition(fromIndex);
        if (from == null)
          throw new NullPointerException("from");

        for (Direction direction : Direction.values()) {
          int toIndex;
          try {
            Position to = from.neighbour(direction);
            toIndex = getIndex(to);
          } catch (ImpossiblePositionException e) {
            toIndex = -1;
          }
          moveToIndices[fromIndex][direction.ordinal()] = toIndex;
        }
      }
    }

    /**
     * For each location
     * for each piece type
     * for each colour
     * an array of the possible moves from that position.
     */
    public static final Move[][] potentialMoves = new Move[totalSquares * numColours * numPieces][];
    public static final int[] potentialMoveCounts = new int[potentialMoves.length];
    public static final int pieceIndexStride = totalSquares * numPieces; // 576
    static {
      for (int index = 0; index < totalSquares; ++index) {
        for (Colour colour : Colour.values()) {
          addPotentialMoves(index, colour, PieceType.PAWN,   PawnMove.getPotentialMoves(index, colour));
          addPotentialMoves(index, colour, PieceType.KNIGHT, KnightMove.getPotentialMoves(index));
          addPotentialMoves(index, colour, PieceType.ROOK,   MoveMany.getPotentialMoves(index, PieceType.ROOK));
          addPotentialMoves(index, colour, PieceType.BISHOP, MoveMany.getPotentialMoves(index, PieceType.BISHOP));
          addPotentialMoves(index, colour, PieceType.QUEEN,  MoveMany.getPotentialMoves(index, PieceType.QUEEN));
          addPotentialMoves(index, colour, PieceType.KING,   KingMove.getPotentialMoves(index, colour));
        }
      }
      for (int index = 0; index < potentialMoves.length; ++index) {
        potentialMoveCounts[index] = potentialMoves[index].length;
      }
    }
    /** All of the Move[] arrays from potentialMoves flattened into one single continuous array. **/
    public static final Move[] potentialMovesFlattened;
    /** Packed directives for the starting index and length of each Move array flattened into potentialMovesFlattened. **/
    public static final int[] potentialMovesFlattenedDirectives = new int[totalSquares * numColours * numPieces];
    static {
      List<Move> allPotentialMoves = new ArrayList<>();
      for (int index = 0; index < potentialMoves.length; ++index) {
        Move[] array = potentialMoves[index];
        if (array.length > 255)
          throw new IllegalStateException();

        int flatIndex = allPotentialMoves.size();
        potentialMovesFlattenedDirectives[index] = (flatIndex << 8) | array.length;
        allPotentialMoves.addAll(Arrays.asList(array));
      }
      potentialMovesFlattened = allPotentialMoves.toArray(new Move[0]);
    }

    /** The weight given to the utility of an agents own pieces. **/
    public int selfWeight;
    /** The weight given to the utility of each piece type. **/
    public final double[] typeValues;
    /** The weight given to utility from advancing pawns. **/
    public double pawnRowValue;
    /** The weight given to utility from having a higher number of available moves. **/
    public double moveCountValue;

    /** The pre-calculated utilities for all piece types of all colours in all positions. **/
    public final short[] pieceUtilities = new short[potentialMoves.length];

    public GameConstants(int selfWeight, double[] typeValues, double pawnRowValue, double moveCountValue) {
      if (typeValues.length != PieceType.values().length)
        throw new IllegalArgumentException();

      this.selfWeight = selfWeight;
      this.typeValues = Arrays.copyOf(typeValues, typeValues.length);
      this.pawnRowValue = pawnRowValue;
      this.moveCountValue = moveCountValue;

      recalculatePieceUtilities();
    }

    /** Re-calculates the table of piece utilities. **/
    protected void recalculatePieceUtilities() {
      for (Colour colour : Colour.values()) {
        for (int index = 0; index < totalSquares; ++index) {
          for (PieceType type : PieceType.values()) {
            calculatePieceUtility(index, colour, type);
          }
        }
      }
    }

    /** Calculates the utility for the given piece position, colour, and type. **/
    private void calculatePieceUtility(int index, Colour colour, PieceType type) {
      double utility = typeValues[type.ordinal()];

      // Value based on the piece's position.
      if (type == PieceType.PAWN) {
        Colour side = Colour.values()[index >> 5];
        int row = (index & 31) >> 3;
        utility += pawnRowValue * (side == colour ? row + 1 : 8 - row);
      }

      // Value based on the piece's movements.
      int pieceIndex = colour.ordinal() * pieceIndexStride + index * numPieces + type.ordinal();
      utility += moveCountValue * meanMovesPerPosition[pieceIndex];

      // Sanity Check
      if (utility < Short.MIN_VALUE || utility > Short.MAX_VALUE)
        throw new IllegalStateException("Overflow casting to short");

      // Record the utility value.
      pieceUtilities[pieceIndex] = (short) utility;
    }

    /** Registers a set of possible moves for the given baseIndex and pieceType. **/
    private static void addPotentialMoves(int index, Colour colour, PieceType type, List<Move> moves) {
      int pieceIndex = colour.ordinal() * pieceIndexStride + index * numPieces + type.ordinal();
      potentialMoves[pieceIndex] = filterMoves(index, moves);
    }

    /**
     * Filters duplicate moves out of {@param moves}, as well as building up metadata
     * about the move array so that it can be traversed more quickly.
     */
    private static Move[] filterMoves(int fromIndex, List<Move> moveList) {
      // Filter out any duplicate moves.
      List<Move> filtered = new ArrayList<>();
      moveLoop: for (Move move : moveList) {
        if (fromIndex != move.fromIndex)
          throw new IllegalArgumentException("Move " + move + " has mismatching fromIndex: " + fromIndex + " != " + move.fromIndex);
        for (Move potentialDuplicate : filtered) {
          if (move.fromIndex == potentialDuplicate.fromIndex && move.toIndex == potentialDuplicate.toIndex)
            continue moveLoop;
        }
        filtered.add(move);
      }
      Move[] moves = filtered.toArray(new Move[0]);

      // Setup the legal move check skipping for MoveMany moves.
      // This relies on the fact that if one MoveMany move is found
      // to be invalid, many other MoveMany moves may also become
      // known to be invalid.
      if (moves.length > 0 && moves[0] instanceof MoveMany) {
        for (int index1 = 0; index1 < moves.length; ++index1) {
          MoveMany move1 = (MoveMany) moves[index1];

          int skipIndex = moves.length;
          for (int index2 = index1 + 1; index2 < moves.length; ++index2) {
            MoveMany move2 = (MoveMany) moves[index2];
            if (move2.reps <= move1.reps) {
              skipIndex = index2;
              break;
            }
          }
          move1.skipIndex = skipIndex;
        }
      }
      return moves;
    }

    /** @return the given position {@param pos} converted to a board index. **/
    public static int getIndex(Position pos) {
      return pos.getColour().ordinal() * sideSquares + pos.getRow() * sideLength + pos.getColumn();
    }

    /** @return the board index for the position with the given {@param colour}, {@param row}, and {@param col}. **/
    public static int getIndex(int colour, int row, int col) {
      return colour * sideSquares + row * sideLength + col;
    }

    /** @return the given board position index {@param index} converted to a Position value. **/
    public static Position convertIndexToPosition(int index) {
      if (index < 0)
        return null;

      int colourOrdinal = index / sideSquares;
      Colour colour = Colour.values()[colourOrdinal];
      int row = (index - colourOrdinal * sideSquares) / sideLength;
      int col = index % sideLength;

      try {
        return Position.get(colour, row, col);
      } catch (ImpossiblePositionException e) {
        e.printStackTrace();
        return null;
      }
    }

    /** @return the given move {@param move} converted to an array of its start and end positions. **/
    public static Position[] convertMoveToPositions(Move move) {
      return new Position[] {
          convertIndexToPosition(move.fromIndex),
          convertIndexToPosition(move.toIndex)
      };
    }

    /** @return the end index after applying the given move, or -1 on leaving the board. **/
    public static int getMoveToIndex(int startIndex, Direction[] move) {
      return getMoveToIndex(startIndex, move, 1);
    }

    /** @return the end index after applying the given move {@param reps} times, or -1 on leaving the board. **/
    public static int getMoveToIndex(int startIndex, Direction[] move, int reps) {
      int startColourOrdinal = startIndex / sideSquares;
      int endIndex = startIndex;

      boolean reverse = false;
      for (int rep = 0; rep < reps; ++rep) {
        for (Direction direction : move) {
          // Once we cross a section, the remaining moves must be reversed.
          if (reverse) {
            direction = reverseDirection(direction);
          }

          // Find the next index.
          int fromIndex = endIndex;
          endIndex = moveToIndices[fromIndex][direction.ordinal()];
          if (endIndex < 0)
            return -1;

          // If we crossed sections we need to reverse the remaining directions.
          int currentColourOrdinal = endIndex / sideSquares;
          if (startColourOrdinal != currentColourOrdinal) {
            reverse = true;
          }
        }
      }
      return endIndex;
    }
  }

  /**
   * Encodes the logic of performing a move.
   */
  public static abstract class Move {

    public final int fromIndex;
    public final int toIndex;

    /**
     * The index to skip forward to in the moves array is this move is invalid.
     * This is calculated after the list of all available moves is constructed.
     */
    public int skipIndex;

    public Move(int fromIndex, int toIndex) {
      this.fromIndex = fromIndex;
      this.toIndex = toIndex;
    }

    /**
     * This method assumes that the piece at toIndex does
     * not have the same colour as the piece at fromIndex.
     *
     * @return whether the move can be applied to the given game state {@param state}.
     */
    public abstract boolean isValidMove(GameState state);

    /** @return a human-readable string representation of this move. **/
    @Override public String toString() {
      String from = GameConstants.convertIndexToPosition(fromIndex) + "(" + fromIndex + ")";
      String to = GameConstants.convertIndexToPosition(toIndex) + "(" + toIndex + ")";
      return getClass().getSimpleName().replace("Move", "") + "{" + from + " -> " + to + "}";
    }
  }

  /**
   * Models the possible moves of pawns.
   */
  public static abstract class PawnMove extends Move {
    public final boolean promoteToQueen;

    public PawnMove(int fromIndex, int toIndex) {
      super(fromIndex, toIndex);
      // If the pawn reaches an end row, it should be promoted to a queen.
      this.promoteToQueen = (toIndex % GameConstants.sideSquares) < GameConstants.sideLength;
    }

    /** @return all of the possible moves of a pawn from the given position and of the given colour. **/
    public static List<Move> getPotentialMoves(int fromIndex, Colour colour) {
      List<Move> moves = new ArrayList<>();

      // When pawns are in other colours, their moves need to be reversed.
      Colour fromIndexColour = Colour.values()[fromIndex / GameConstants.sideSquares];
      boolean reverse = (colour != fromIndexColour);

      // Add the pawn moving one space forward.
      Direction[] pawnMove = PawnMoveOneForward.move;
      pawnMove = (reverse ? reverseMove(pawnMove) : pawnMove);
      int toIndex = GameConstants.getMoveToIndex(fromIndex, pawnMove);
      if (toIndex >= 0) {
        moves.add(new PawnMoveOneForward(fromIndex, toIndex));
      }

      // Add the pawn taking pieces moves.
      for (Direction[] move : PawnMoveTake.capturingMoves) {
        if (reverse) {
          move = reverseMove(move);
        }

        toIndex = GameConstants.getMoveToIndex(fromIndex, move);
        if (toIndex >= 0) {
          moves.add(new PawnMoveTake(fromIndex, toIndex));
        }
      }

      // Check if the pawn is part of the initial row, in which case it can move two forward.
      int initialRowFrom = colour.ordinal() * GameConstants.sideSquares + GameConstants.sideLength;
      int initialRowTo = initialRowFrom + GameConstants.sideLength;
      if (fromIndex >= initialRowFrom && fromIndex < initialRowTo) {
        moves.add(new PawnMoveTwoForward(fromIndex));
      }
      return moves;
    }
  }

  /**
   * Pawn moving one space forward.
   */
  public static class PawnMoveOneForward extends PawnMove {
    public final static Direction[] move = {Direction.FORWARD};

    public PawnMoveOneForward(int fromIndex, int toIndex) {
      super(fromIndex, toIndex);
    }

    @Override public boolean isValidMove(GameState state) {
      return state.pieces[toIndex] == 0;
    }
  }

  /**
   * Pawn moving two spaces forward from its starting position.
   */
  public static class PawnMoveTwoForward extends PawnMove {

    public final static Direction[] move = {Direction.FORWARD, Direction.FORWARD};
    public final int middleIndex;

    public PawnMoveTwoForward(int fromIndex) {
      super(fromIndex, fromIndex + 2 * GameConstants.sideLength);
      middleIndex = fromIndex + GameConstants.sideLength;
    }

    @Override public boolean isValidMove(GameState state) {
      return state.pieces[middleIndex] == 0 && state.pieces[toIndex] == 0;
    }
  }

  /**
   * Pawn taking a piece.
   */
  public static class PawnMoveTake extends PawnMove {

    /** A list of all the capturing moves a pawn can make. **/
    public final static Direction[][] capturingMoves = {
        {Direction.FORWARD, Direction.LEFT}, {Direction.FORWARD, Direction.RIGHT},
        {Direction.LEFT, Direction.FORWARD}, {Direction.RIGHT, Direction.FORWARD}
    };

    public PawnMoveTake(int fromIndex, int toIndex) {
      super(fromIndex, toIndex);
    }

    @Override public boolean isValidMove(GameState state) {
      return state.pieces[toIndex] != 0;
    }
  }

  /**
   * Models the possible moves of knights.
   */
  public static class KnightMove extends Move {
    private static final Direction[][] moves = PieceType.KNIGHT.getSteps();

    public KnightMove(int fromIndex, int toIndex) {
      super(fromIndex, toIndex);
    }

    @Override public boolean isValidMove(GameState state) {
      return true;
    }

    /** @return a list of all the possible moves from a knight moving from the given position. **/
    public static List<Move> getPotentialMoves(int fromIndex) {
      List<Move> possibleMoves = new ArrayList<>();
      for (Direction[] move : moves) {
        int toIndex = GameConstants.getMoveToIndex(fromIndex, move);
        if (toIndex >= 0) {
          possibleMoves.add(new KnightMove(fromIndex, toIndex));
        }
      }
      return possibleMoves;
    }
  }

  /**
   * Used to model rooks, bishops, and queens.
   * Can move many steps, and all in between tiles must be empty.
   */
  public static class MoveMany extends Move {
    private final int reps;
    private final int[] emptyIndices;

    public MoveMany(Direction[] move, int reps, int fromIndex, int toIndex) {
      super(fromIndex, toIndex);

      this.reps = reps;
      this.emptyIndices = new int[reps - 1];
      for (int intermediateReps = 1; intermediateReps < reps; ++intermediateReps) {
        int emptyIndex = GameConstants.getMoveToIndex(fromIndex, move, intermediateReps);
        if (emptyIndex < 0)
          throw new IllegalStateException();

        emptyIndices[intermediateReps - 1] = emptyIndex;
      }
    }

    @Override public boolean isValidMove(GameState state) {
      for (int emptyIndex : emptyIndices) {
        if (state.pieces[emptyIndex] != 0)
          return false;
      }
      return true;
    }

    /**
     * We need a special order for these steps for our move skipping when we iterate moves to work correctly.
     * @return a list of possible moves for the given piece type.
     */
    private static Direction[][] getSteps(PieceType pieceType) {
      if (pieceType == PieceType.QUEEN) {
        return new Direction[][] {
            {Direction.FORWARD,Direction.LEFT}, {Direction.LEFT,Direction.FORWARD},
            {Direction.FORWARD,Direction.RIGHT}, {Direction.RIGHT,Direction.FORWARD},
            {Direction.BACKWARD,Direction.LEFT}, {Direction.LEFT,Direction.BACKWARD},
            {Direction.BACKWARD,Direction.RIGHT}, {Direction.RIGHT,Direction.BACKWARD},
            {Direction.FORWARD}, {Direction.BACKWARD}, {Direction.LEFT}, {Direction.RIGHT}
        };
      } else if (pieceType == PieceType.BISHOP) {
        return new Direction[][] {
            {Direction.FORWARD,Direction.LEFT}, {Direction.LEFT,Direction.FORWARD},
            {Direction.FORWARD,Direction.RIGHT}, {Direction.RIGHT,Direction.FORWARD},
            {Direction.BACKWARD,Direction.LEFT}, {Direction.LEFT,Direction.BACKWARD},
            {Direction.BACKWARD,Direction.RIGHT}, {Direction.RIGHT,Direction.BACKWARD}
        };
      } else {
        return pieceType.getSteps();
      }
    }

    /** @return a list of all the possible moves from the given location for the given piece type. **/
    public static List<Move> getPotentialMoves(int fromIndex, PieceType type) {
      List<Move> possibleMoves = new ArrayList<>();
      for (Direction[] move : getSteps(type)) {
        for (int reps = 1; reps <= type.getStepReps(); ++reps) {
          int toIndex = GameConstants.getMoveToIndex(fromIndex, move, reps);
          if (toIndex >= 0) {
            possibleMoves.add(new MoveMany(move, reps, fromIndex, toIndex));
          }
        }
      }
      return possibleMoves;
    }
  }

  /**
   * Models the possible moves of kings.
   */
  public static class KingMove extends Move {
    public static final Direction[][] moves = PieceType.KING.getSteps();

    public KingMove(int fromIndex, int toIndex) {
      super(fromIndex, toIndex);
    }

    @Override public boolean isValidMove(GameState state) {
      return true;
    }

    /** @return a list of all the possible king moves from the given index and colour. **/
    public static List<Move> getPotentialMoves(int fromIndex, Colour colour) {
      List<Move> possibleMoves = new ArrayList<>();

      // Add all standard moves.
      for (Direction[] move : moves) {
        int toIndex = GameConstants.getMoveToIndex(fromIndex, move);
        if (toIndex >= 0) {
          possibleMoves.add(new KingMove(fromIndex, toIndex));
        }
      }

      // Check if castling is a potential move from the current position.
      if (fromIndex == colour.ordinal() * GameConstants.sideSquares + 4) {
        possibleMoves.add(new KingMoveCastleLeft(colour.ordinal()));
        possibleMoves.add(new KingMoveCastleRight(colour.ordinal()));
      }
      return possibleMoves;
    }
  }

  /** King castling. **/
  public static abstract class KingMoveCastle extends KingMove {
    public final int castleIndex;
    public final int rookPlaceIndex;

    public KingMoveCastle(int fromIndex, int toIndex, int castleIndex, int rookPlaceIndex) {
      super(fromIndex, toIndex);
      this.castleIndex = castleIndex;
      this.rookPlaceIndex = rookPlaceIndex;
    }
  }

  /** King castling to the left. **/
  public static class KingMoveCastleLeft extends KingMoveCastle {
    public static final Direction[] move = {Direction.LEFT, Direction.LEFT};
    private final int emptyIndex1;
    private final int emptyIndex2;
    private final byte rookPieceByte;

    public KingMoveCastleLeft(int colour) {
      super(
          GameConstants.getIndex(colour, 0, 4),
          GameConstants.getIndex(colour, 0, 2),
          GameConstants.getIndex(colour, 0, 0),
          GameConstants.getIndex(colour, 0, 3)
      );
      this.emptyIndex1 = GameConstants.getIndex(colour, 0, 1);
      this.emptyIndex2 = GameConstants.getIndex(colour, 0, 2);
      this.rookPieceByte = (byte) ((44 /* P-Bit | (ROOK (3) << 2) */) | colour);
    }

    @Override public boolean isValidMove(GameState state) {
      byte[] pieces = state.pieces;
      return pieces[castleIndex] == rookPieceByte
          && pieces[emptyIndex1] == 0
          && pieces[emptyIndex2] == 0
          && pieces[rookPlaceIndex] == 0;
    }
  }

  /** King castling to the right. **/
  public static class KingMoveCastleRight extends KingMoveCastle {
    public static final Direction[] move = {Direction.RIGHT, Direction.RIGHT};
    private final int emptyIndex1;
    private final byte rookPieceByte;

    public KingMoveCastleRight(int colour) {
      super(
          GameConstants.getIndex(colour, 0, 4),
          GameConstants.getIndex(colour, 0, 6),
          GameConstants.getIndex(colour, 0, 7),
          GameConstants.getIndex(colour, 0, 5)
      );
      this.emptyIndex1 = GameConstants.getIndex(colour, 0, 6);
      this.rookPieceByte = (byte) ((44 /* P-Bit | (ROOK (3) << 2) */) | colour);
    }

    @Override public boolean isValidMove(GameState state) {
      byte[] pieces = state.pieces;
      return pieces[castleIndex] == rookPieceByte
          && pieces[emptyIndex1] == 0
          && pieces[rookPlaceIndex] == 0;
    }
  }

  /** @return a value between one and two based on the given ratio. **/
  public static double interp(double one, double two, double ratio) {
    return one + ratio * (two - one);
  }

  /** Populates dest with the rounded interpolated values between one and two based on the given ratio. **/
  public static void interpArray(double[] dest, double[] one, double[] two, double ratio) {
    for (int index = 0; index < dest.length; ++index) {
      dest[index] = interp(one[index], two[index], ratio);
    }
  }

  /** @return the reversed direction to {@param direction}. **/
  public static Direction reverseDirection(Direction direction) {
    switch (direction) {
      case LEFT: return Direction.RIGHT;
      case RIGHT: return Direction.LEFT;
      case FORWARD: return Direction.BACKWARD;
      case BACKWARD: return Direction.FORWARD;
      default: throw new IllegalArgumentException("Unknown direction " + direction);
    }
  }

  /** @return a copy of {@param move} with all of the directions reversed. **/
  public static Direction[] reverseMove(Direction[] move) {
    Direction[] reversed = new Direction[move.length];
    for (int index = 0; index < move.length; ++index) {
      reversed[index] = reverseDirection(move[index]);
    }
    return reversed;
  }

  /**
   * This table was constructed by simulating 10,000 games and counting
   * the number of moves each piece had when it was in each square.
   *
   * If only we had configuration files...
   */
  public static final double[] meanMovesPerPosition = {
      0.0, 2.0, 4.5, 2.7, 9.8, 3.0, 0.0, 2.6, 4.9, 9.8, 7.6, 4.9, 0.0, 3.4, 3.3, 9.6, 8.5, 4.5, 0.0, 3.1, 4.8, 7.1,
      4.0, 4.5, 0.0, 3.0, 4.5, 9.4, 10.6, 3.1, 0.0, 3.5, 2.2, 9.0, 12.3, 4.2, 0.0, 2.4, 4.8, 9.0, 12.7, 4.1, 0.0, 1.9,
      4.1, 3.1, 11.8, 2.8, 1.9, 2.6, 5.0, 7.8, 10.2, 4.9, 1.9, 3.6, 3.6, 10.8, 14.1, 7.7, 1.7, 5.1, 6.7, 9.9, 12.5, 7.4,
      1.9, 4.9, 7.2, 8.7, 8.5, 7.2, 1.9, 5.1, 6.0, 9.4, 12.9, 7.2, 1.3, 5.2, 5.7, 10.0, 10.8, 7.0, 1.9, 3.5, 4.4, 9.7,
      13.7, 7.4, 1.9, 2.5, 5.8, 7.6, 10.6, 4.8, 0.5, 3.5, 5.6, 7.6, 14.2, 4.8, 1.0, 5.1, 5.3, 11.8, 14.6, 7.6, 0.7, 6.3,
      8.5, 10.6, 18.6, 7.7, 0.9, 7.9, 7.4, 9.4, 12.2, 6.9, 0.9, 8.2, 6.0, 10.3, 14.2, 7.3, 0.8, 5.8, 8.9, 10.1, 14.2,
      7.6, 0.8, 4.8, 6.4, 10.7, 12.3, 7.6, 0.3, 3.2, 4.2, 8.8, 11.8, 4.7, 0.3, 3.5, 5.6, 8.3, 13.2, 5.0, 0.6, 5.4, 7.2,
      10.3, 16.5, 7.8, 0.6, 8.3, 8.6, 9.7, 17.1, 7.7, 0.5, 9.2, 12.6, 10.0, 21.8, 8.2, 1.0, 9.5, 12.4, 11.0, 22.7, 8.2,
      0.7, 8.2, 7.9, 9.2, 16.2, 7.7, 0.8, 5.2, 7.7, 9.9, 13.3, 7.9, 0.4, 3.4, 5.7, 8.1, 12.8, 4.9, 0.0, 2.0, 5.7, 12.9,
      14.1, 3.0, 0.0, 3.0, 6.3, 13.0, 14.6, 5.0, 0.0, 4.0, 6.0, 11.7, 15.0, 5.0, 0.0, 4.0, 6.6, 11.6, 17.5, 5.0, 0.0,
      3.8, 6.7, 11.7, 17.8, 4.9, 0.0, 4.0, 5.4, 11.7, 17.8, 5.0, 0.0, 3.0, 5.9, 11.9, 16.6, 5.0, 0.0, 2.0, 7.4, 12.8,
      14.9, 3.0, 0.0, 3.0, 4.7, 13.2, 18.4, 5.0, 0.1, 4.0, 8.1, 13.5, 18.3, 8.0, 0.1, 5.9, 7.8, 12.4, 16.3, 8.0, 0.3,
      5.8, 7.9, 11.6, 19.0, 8.0, 0.3, 5.9, 8.3, 11.7, 18.6, 8.0, 0.3, 5.8, 8.4, 12.6, 18.0, 8.0, 0.5, 3.9, 10.0, 12.8,
      16.8, 8.0, 0.2, 3.0, 5.2, 12.9, 13.7, 5.0, 0.0, 4.0, 6.2, 13.0, 17.4, 5.0, 0.0, 6.0, 7.7, 12.9, 19.0, 8.0, 1.0,
      7.8, 11.6, 13.2, 22.2, 7.9, 0.8, 8.6, 9.8, 12.3, 20.0, 7.9, 0.7, 8.5, 9.0, 11.5, 20.3, 7.9, 0.7, 7.8, 11.3, 12.4,
      19.9, 8.0, 0.5, 5.3, 6.9, 12.6, 17.3, 7.9, 0.5, 4.0, 5.8, 12.8, 14.3, 5.0, 0.0, 4.0, 6.8, 11.6, 16.2, 5.0, 0.0,
      6.0, 7.9, 11.9, 17.9, 8.0, 0.0, 8.7, 9.9, 11.7, 20.6, 8.0, 1.0, 9.7, 12.7, 12.2, 24.3, 8.5, 0.9, 9.6, 13.3, 10.6,
      22.6, 8.4, 0.7, 8.3, 8.8, 11.0, 17.8, 7.8, 0.8, 5.5, 7.8, 11.3, 15.9, 7.8, 0.7, 3.7, 5.4, 10.4, 14.8, 4.8, 0.0,
      2.0, 7.9, 13.4, 15.2, 2.9, 0.0, 2.8, 6.5, 12.7, 16.3, 5.0, 0.0, 3.9, 5.3, 12.7, 16.8, 5.0, 0.0, 4.0, 6.7, 11.5,
      18.1, 5.0, 0.0, 4.0, 6.2, 12.1, 17.5, 5.0, 0.0, 4.0, 6.7, 13.4, 17.2, 5.0, 0.0, 3.0, 6.6, 13.6, 14.9, 5.0, 0.0,
      2.0, 7.6, 13.7, 13.1, 3.0, 0.2, 3.0, 5.1, 11.5, 12.7, 4.9, 0.3, 3.8, 8.6, 12.6, 17.2, 8.0, 0.4, 5.8, 7.7, 9.6,
      15.5, 8.0, 0.5, 5.9, 7.6, 11.3, 16.3, 8.0, 0.8, 5.9, 7.1, 10.9, 19.9, 8.0, 0.1, 6.0, 7.8, 13.1, 19.6, 8.0, 2.3,
      4.0, 8.9, 13.4, 18.3, 8.0, 0.0, 3.0, 6.0, 13.3, 13.6, 5.0, 0.6, 3.9, 5.5, 12.8, 14.6, 5.0, 0.7, 5.6, 7.4, 12.0,
      17.0, 7.9, 0.9, 7.8, 10.2, 12.1, 19.1, 7.9, 0.7, 8.5, 9.2, 11.0, 19.5, 7.9, 1.0, 8.5, 10.0, 11.1, 21.0, 7.9, 0.1,
      7.9, 11.2, 13.0, 21.3, 7.9, 0.0, 6.0, 7.2, 13.5, 15.1, 8.0, 0.0, 4.0, 6.0, 13.1, 13.5, 5.0, 0.7, 3.5, 5.5, 10.0,
      12.8, 4.8, 1.0, 5.7, 6.6, 10.6, 14.5, 7.9, 0.7, 7.8, 9.0, 10.4, 15.7, 7.9, 1.0, 9.7, 13.4, 10.1, 23.4, 8.5, 1.1,
      9.7, 13.4, 10.9, 24.3, 8.4, 0.0, 8.7, 10.3, 11.7, 19.8, 8.0, 0.0, 5.9, 8.5, 12.7, 18.4, 8.0, 0.0, 4.0, 6.8, 12.1,
      14.8, 5.0, 0.0, 2.0, 5.8, 12.3, 13.2, 3.0, 0.0, 2.8, 5.3, 12.8, 16.6, 5.0, 0.0, 4.0, 5.2, 12.8, 17.2, 5.0, 0.0,
      4.0, 5.1, 10.8, 17.5, 5.0, 0.0, 3.9, 6.3, 10.8, 17.0, 5.0, 0.0, 4.0, 6.0, 13.1, 15.4, 5.0, 0.0, 2.9, 5.3, 13.3,
      16.0, 5.0, 0.0, 2.0, 5.7, 13.3, 12.9, 3.0, 0.2, 2.5, 5.0, 12.7, 12.4, 5.0, 0.4, 3.7, 8.3, 12.4, 15.6, 7.7, 0.6,
      5.8, 7.8, 11.6, 16.5, 7.9, 0.5, 6.0, 7.2, 11.2, 17.3, 7.9, 0.5, 5.9, 8.2, 11.5, 18.8, 8.0, 1.1, 5.9, 7.4, 13.1,
      18.5, 8.0, 0.6, 4.0, 8.3, 13.7, 18.3, 7.9, 0.0, 3.0, 4.6, 13.6, 16.9, 5.0, 0.6, 3.8, 5.5, 11.5, 14.1, 5.0, 0.9,
      4.7, 7.5, 12.8, 14.7, 7.9, 0.8, 6.9, 9.8, 12.5, 19.1, 7.6, 1.1, 8.7, 9.2, 9.2, 20.5, 7.9, 1.1, 8.6, 9.3, 12.0,
      20.4, 7.8, 0.8, 7.7, 11.0, 13.2, 22.0, 8.0, 0.0, 5.8, 8.2, 12.9, 18.8, 8.0, 0.0, 4.0, 6.4, 13.1, 16.3, 5.0, 0.6,
      3.5, 5.6, 9.7, 12.1, 4.6, 1.0, 5.5, 7.2, 9.9, 13.0, 7.8, 0.5, 8.0, 9.7, 9.8, 14.4, 7.7, 0.9, 9.8, 12.9, 10.1,
      23.1, 8.1, 1.0, 9.7, 13.3, 11.4, 23.9, 8.2, 0.0, 8.6, 10.2, 11.4, 20.4, 7.9, 0.0, 5.9, 7.3, 11.6, 18.4, 8.0, 0.0,
      3.8, 6.6, 11.0, 15.6, 5.0, 0.0, 1.9, 5.7, 3.4, 11.2, 3.0, 0.0, 2.6, 4.7, 10.1, 10.9, 5.0, 0.0, 3.6, 2.2, 10.7,
      9.5, 4.5, 0.0, 3.0, 5.3, 8.3, 4.1, 4.2, 0.0, 3.5, 5.4, 9.8, 8.1, 3.2, 0.0, 3.4, 2.6, 9.4, 10.6, 4.2, 0.0, 2.4,
      6.3, 9.7, 9.0, 4.3, 0.0, 1.9, 3.1, 3.1, 12.2, 2.9, 1.8, 2.9, 5.9, 7.5, 11.9, 4.8, 1.8, 3.6, 4.3, 11.3, 14.8, 7.7,
      1.8, 5.2, 5.4, 10.3, 11.6, 7.6, 1.8, 4.9, 6.5, 9.7, 12.9, 7.4, 1.9, 4.9, 5.7, 10.3, 11.4, 7.2, 1.2, 5.1, 5.9,
      10.9, 11.3, 6.9, 1.8, 3.4, 3.9, 11.2, 12.1, 7.2, 1.9, 2.8, 4.4, 8.3, 10.9, 4.7, 0.4, 3.4, 4.9, 8.7, 10.3, 4.9,
      0.6, 5.0, 7.3, 11.2, 13.6, 7.4, 0.6, 6.2, 8.7, 12.2, 18.8, 7.7, 1.0, 8.1, 5.8, 10.4, 14.0, 7.5, 0.5, 7.7, 6.7,
      9.8, 14.4, 7.3, 0.8, 6.5, 8.1, 11.1, 13.4, 7.2, 0.8, 4.5, 6.1, 11.5, 12.8, 7.5, 0.5, 3.4, 5.1, 9.4, 8.8, 4.7, 0.2,
      3.1, 5.8, 9.0, 15.5, 4.9, 0.8, 5.4, 7.4, 10.0, 15.7, 7.8, 0.5, 8.3, 8.8, 10.4, 17.3, 7.8, 1.2, 9.3, 12.3, 11.2,
      22.0, 8.3, 0.4, 9.3, 12.4, 9.1, 22.3, 8.1, 0.7, 8.1, 9.0, 9.4, 15.5, 7.7, 0.6, 5.4, 6.5, 10.3, 14.1, 7.7, 0.3,
      3.7, 3.4, 8.1, 12.5, 4.9, 0.0, 1.9, 7.4, 12.7, 14.3, 3.0, 0.0, 3.0, 6.4, 13.6, 14.4, 5.0, 0.0, 4.0, 4.8, 13.1,
      17.0, 5.0, 0.0, 4.0, 6.0, 11.9, 14.8, 5.0, 0.0, 4.0, 7.0, 12.2, 18.3, 5.0, 0.0, 3.9, 6.8, 13.4, 17.5, 5.0, 0.0,
      3.0, 6.3, 13.0, 15.1, 5.0, 0.0, 2.0, 5.8, 13.3, 15.1, 2.9, 0.0, 3.0, 6.6, 12.9, 17.2, 5.0, 0.0, 3.9, 9.2, 12.9,
      18.9, 8.0, 0.0, 6.0, 8.0, 13.0, 17.9, 8.0, 0.4, 6.0, 7.5, 12.1, 19.9, 8.0, 0.6, 5.8, 6.6, 11.3, 18.8, 8.0, 0.4,
      5.9, 8.5, 13.2, 17.2, 8.0, 0.3, 3.9, 9.9, 13.1, 17.4, 7.8, 0.2, 3.0, 5.0, 12.7, 13.4, 4.9, 0.0, 4.0, 6.8, 12.2,
      17.3, 5.0, 0.0, 6.0, 8.0, 12.8, 20.0, 8.0, 1.0, 7.8, 10.8, 12.8, 23.0, 8.0, 0.5, 8.6, 8.9, 12.2, 19.7, 7.9, 0.7,
      8.6, 8.5, 11.6, 19.7, 7.9, 0.8, 7.5, 11.1, 12.8, 20.0, 7.9, 0.7, 5.0, 6.7, 12.8, 18.8, 7.8, 0.8, 3.6, 5.3, 11.8,
      12.4, 5.0, 0.0, 4.0, 5.2, 12.3, 16.4, 5.0, 0.0, 6.0, 8.3, 12.5, 18.8, 8.0, 0.0, 8.6, 10.4, 12.2, 20.0, 7.9, 1.0,
      9.8, 13.0, 11.0, 23.4, 8.1, 0.9, 9.7, 12.7, 11.0, 23.6, 8.4, 0.7, 8.3, 9.0, 10.0, 17.4, 7.9, 0.9, 5.8, 7.1, 10.0,
      16.3, 7.8, 0.7, 3.7, 5.2, 10.2, 13.1, 4.6, 0.0, 2.0, 6.5, 12.2, 13.9, 0.0, 0.0, 3.0, 6.5, 12.0, 15.2, 5.0, 0.0,
      3.9, 6.0, 12.4, 15.3, 5.0, 0.0, 4.0, 6.3, 10.4, 18.1, 5.0, 0.0, 3.9, 7.0, 11.2, 17.6, 5.0, 0.0, 4.0, 6.8, 12.1,
      17.0, 5.0, 0.0, 3.0, 6.3, 11.4, 16.5, 5.0, 0.0, 2.0, 6.6, 13.1, 15.6, 2.7, 0.0, 3.0, 6.3, 12.7, 15.6, 5.0, 0.0,
      4.0, 9.3, 12.6, 21.0, 8.0, 0.9, 6.0, 7.9, 12.9, 17.5, 8.0, 0.3, 5.7, 7.0, 11.8, 19.2, 8.0, 0.3, 5.7, 7.0, 10.6,
      19.3, 8.0, 0.3, 5.9, 8.1, 13.3, 16.0, 8.0, 0.3, 3.6, 9.0, 12.4, 17.6, 8.0, 0.2, 3.0, 5.2, 13.1, 14.7, 5.0, 0.0,
      3.8, 4.8, 12.5, 17.8, 5.0, 0.0, 5.9, 7.8, 13.7, 19.6, 8.0, 1.2, 7.8, 11.3, 13.5, 21.8, 8.0, 0.8, 8.4, 8.8, 11.1,
      21.3, 7.9, 0.6, 8.5, 9.5, 10.7, 18.5, 7.4, 0.8, 7.7, 10.5, 12.5, 19.2, 7.8, 0.7, 5.4, 6.6, 12.6, 14.2, 8.0, 0.6,
      3.8, 5.8, 11.6, 14.6, 5.0, 0.0, 4.0, 6.4, 10.3, 15.7, 5.0, 0.0, 6.0, 8.3, 11.0, 17.4, 8.0, 0.0, 8.7, 10.5, 10.5,
      20.2, 7.9, 1.0, 9.7, 13.2, 11.5, 23.4, 8.0, 0.9, 9.6, 13.0, 10.6, 23.4, 8.1, 0.6, 8.4, 9.2, 10.5, 16.9, 7.8, 0.9,
      5.8, 7.0, 10.8, 14.0, 7.9, 0.8, 3.8, 5.9, 9.9, 12.5, 4.9, 0.0, 2.0, 5.8, 13.1, 14.6, 3.0, 0.0, 3.0, 6.7, 12.5,
      14.5, 5.0, 0.0, 4.0, 5.5, 12.7, 16.0, 5.0, 0.0, 4.0, 6.7, 12.1, 17.2, 5.0, 0.0, 3.9, 6.2, 10.9, 16.9, 5.0, 0.0,
      4.0, 5.9, 12.0, 15.4, 5.0, 0.0, 3.0, 6.4, 13.6, 14.6, 5.0, 0.0, 2.0, 7.1, 13.6, 14.2, 3.0, 0.2, 2.9, 6.0, 12.6,
      13.5, 5.0, 0.3, 3.9, 5.8, 12.5, 16.8, 7.8, 0.4, 5.9, 7.3, 12.2, 16.7, 8.0, 0.7, 5.9, 7.1, 11.1, 18.4, 8.0, 0.3,
      6.0, 7.9, 11.8, 20.5, 8.0, 0.7, 5.9, 7.9, 12.9, 20.3, 8.0, 0.0, 3.9, 7.7, 13.3, 20.2, 8.0, 0.0, 2.9, 5.0, 13.5,
      16.2, 5.0, 0.6, 3.8, 6.7, 11.5, 14.2, 4.9, 0.6, 5.2, 6.7, 12.6, 16.8, 7.8, 0.9, 7.7, 9.9, 12.6, 19.1, 7.9, 0.5,
      8.4, 10.0, 11.4, 20.7, 7.9, 0.8, 8.4, 10.1, 9.8, 20.4, 7.9, 0.5, 7.8, 10.9, 13.0, 21.7, 8.0, 0.0, 5.9, 8.7, 13.5,
      19.0, 8.0, 0.0, 4.0, 4.7, 12.5, 15.8, 5.0, 0.8, 3.7, 6.1, 8.8, 11.9, 4.4, 0.9, 5.8, 6.2, 9.9, 14.9, 7.8, 0.7, 7.9,
      8.5, 8.3, 18.7, 7.6, 0.8, 9.6, 12.7, 11.1, 22.5, 8.2, 0.9, 9.8, 12.9, 11.3, 22.9, 8.1, 0.0, 8.5, 10.1, 12.3, 20.8,
      8.0, 0.0, 6.0, 8.3, 12.0, 17.1, 8.0, 0.0, 4.0, 6.0, 11.9, 14.4, 5.0, 0.0, 1.9, 3.9, 2.9, 10.7, 2.9, 0.0, 2.4, 5.7,
      9.2, 13.5, 5.0, 0.0, 3.7, 2.3, 9.9, 10.2, 4.5, 0.0, 3.4, 4.8, 9.1, 3.2, 4.5, 0.0, 3.6, 5.7, 9.6, 9.5, 2.8, 0.0,
      3.6, 1.4, 8.9, 11.4, 4.2, 0.0, 2.2, 4.6, 8.0, 9.0, 4.2, 0.0, 1.6, 4.4, 3.7, 9.9, 2.8, 1.9, 2.6, 5.5, 7.9, 10.6,
      4.9, 1.9, 3.6, 4.9, 11.3, 15.9, 7.7, 1.9, 5.1, 6.2, 10.4, 10.7, 7.6, 1.9, 4.9, 6.0, 9.8, 11.7, 7.1, 1.9, 5.0, 6.4,
      10.0, 9.7, 6.9, 1.6, 4.5, 6.5, 10.6, 14.4, 6.9, 1.9, 3.2, 3.8, 9.9, 15.0, 7.5, 1.9, 2.9, 5.5, 7.4, 12.0, 4.7, 0.6,
      3.6, 6.0, 8.7, 12.9, 5.0, 0.8, 5.1, 6.9, 11.4, 11.8, 7.8, 0.8, 6.1, 9.4, 11.9, 18.4, 7.6, 0.9, 8.0, 7.5, 10.3,
      14.6, 7.4, 0.9, 8.0, 7.3, 9.7, 16.7, 7.4, 0.9, 5.9, 8.7, 10.5, 16.1, 7.1, 1.0, 4.9, 5.1, 11.1, 13.9, 7.5, 0.4,
      3.0, 4.6, 7.5, 9.6, 4.3, 0.3, 3.4, 6.6, 7.9, 12.5, 5.0, 0.8, 5.3, 7.4, 10.8, 17.0, 7.9, 0.5, 8.1, 9.0, 9.9, 16.6,
      7.8, 1.4, 9.4, 12.6, 10.2, 21.4, 8.2, 0.9, 9.4, 12.3, 11.0, 23.2, 8.2, 0.5, 8.1, 8.5, 9.2, 16.9, 7.6, 0.8, 5.5,
      7.0, 10.2, 14.5, 7.8, 0.2, 3.4, 5.5, 8.3, 14.2, 4.6
  };
}
