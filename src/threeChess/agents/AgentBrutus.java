package threeChess.agents;

import threeChess.Agent;
import threeChess.Board;
import threeChess.Position;
import threeChess.agents.GameLogic.GameState;
import threeChess.agents.GameLogic.Move;
import threeChess.agents.GameLogic.GameConstants;
import threeChess.agents.GameLogic.CombinedGameConstants;
import threeChess.agents.strategy.*;

import java.util.*;
import java.util.function.BiFunction;

/**
 * An agent that uses iterative deepening to allocate its available time.
 *
 * @author Paddy Lamont, 22494652
 */
public class AgentBrutus extends Agent {

  /** Stop deepening once reaching this depth. **/
  public static int TESTING_CUTOFF_DEPTH = 0;

  /** The initial ply value to check in our iterative deepening. **/
  private static final int INITIAL_PLY = 2;
  /** The maximum ply value after which to abort deepening. **/
  private static final int MAX_PLY = 12;
  /** The number of turns that we use to estimate how much time we should spend per turn. **/
  private static final long EXPECTED_GAME_TURNS = 20;
  /** The amount of turns into the future it will account for in deciding how much time to take. **/
  private static final int FUTURE_TURN_BUDGET = 12;
  /** Used to randomise the selected moves when two or more moves have the same utility. **/
  private static final Random random = new Random();

  /** The name of this agent. **/
  private final String name;
  private final String suffix;
  private final BiFunction<GameConstants, Integer, MoveStrat> strategyGenerator;

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
  private final MoveStrat[] strategies = new MoveStrat[MAX_PLY];

  /** Used in the calculation of mean achieved ply. **/
  public int plySum;
  public int moveCount;

  /**
   * @param suffix a suffix to be appended to the name of this agent.
   * @param constants the constants to be used by this agent for its utility function.
   * @param strategyGenerator a function that generates a move strategy when given a target ply depth.
   */
  public AgentBrutus(
      String suffix, CombinedGameConstants constants,
      BiFunction<GameConstants, Integer, MoveStrat> strategyGenerator) {

    this.name = "Brutus" + (suffix.isEmpty() ? "" : "-" + suffix);
    this.suffix = suffix;
    this.strategyGenerator = strategyGenerator;

    this.constants = constants;
    this.initialState = new GameState(constants);
    this.moveState = new GameState(constants);

    // Generate a strategy instance for every possible ply up to the maximum.
    for (int ply = 1; ply <= MAX_PLY; ++ply) {
      strategies[ply - 1] = strategyGenerator.apply(constants, ply);
    }
  }

  /** @return a Brutus agent that uses the maximax strategy. **/
  public static AgentBrutus createMaximax() {
    return new AgentBrutus("Maximax", CombinedGameConstants.createDefault(), MaximaxStrat::new);
  }

  /** @return a Brutus agent that uses the maximax strategy. **/
  public static AgentBrutus createMaximax(String suffix, CombinedGameConstants constants) {
    return new AgentBrutus("Maximax" + suffix, constants, MaximaxStrat::new);
  }

  /** @return a Brutus agent that uses the maximax strategy with quiescence. **/
  public static AgentBrutus createQuiescence() {
    return new AgentBrutus("Quiescence", CombinedGameConstants.createDefault(), QuiescenceStrat::new);
  }

  /** @return a Brutus agent that uses the maximax strategy with restricted quiescence. **/
  public static AgentBrutus createRestrictedQuiescence() {
    return new AgentBrutus("RQ", CombinedGameConstants.createDefault(), RestrictedQuiescenceStrat::new);
  }

  /** @return a Brutus agent that uses the Principal Variation Search strategy. **/
  public static AgentBrutus createPVS() {
    return new AgentBrutus("PVS", CombinedGameConstants.createDefault(), PrincipalVariationSearchStrat::new);
  }

  /** @return a Brutus agent that uses the minimax strategy with no frills attached. **/
  public static AgentBrutus createMinimax() {
    return new AgentBrutus("Minimax", CombinedGameConstants.createDefault(), MinimaxStrat::new);
  }

  /** @return an identical clone of this agent. **/
  @Override public AgentBrutus clone() {
    return new AgentBrutus(suffix, constants.clone(), strategyGenerator);
  }

  /** @return the best available move as considered by this agent's strategy in the remaining time. **/
  @Override public Position[] playMove(Board board) {
    GameLogic.totalMoves = 0;

    // Record the total amount of time we have to spend for the game.
    boolean isFirstMove = (board.getMoveCount() < 3);
    if (isFirstMove) {
      plySum = 0;
      moveCount = 0;
    }

    // Calculate the amount of time we have to spend per turn over the duration of the game.
    long timeLeftNanos = board.getTimeLeft(board.getTurn()) * 1_000_000L;
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

    // Use iterative-deepening to determine a move using this agent's strategy.
    Move result = availableMoves.get(random.nextInt(availableMoves.size()));

    int ply = TESTING_CUTOFF_DEPTH > 0 ? TESTING_CUTOFF_DEPTH : INITIAL_PLY;
    long lastPly = 0, lastPlyDuration = 0;
    long start = System.nanoTime();
    long elapsed, remaining, plyStart, plyDuration, nextPlyDuration;
    do {
      plyStart = System.nanoTime();

      // Test the current ply.
      MoveStrat strategy = strategies[ply - 1];
      Move move = strategy.decideMove(initialState);
      if (move != null) {
        result = move;
      }

      // Used to test the agent at a fixed depth.
      if (ply == TESTING_CUTOFF_DEPTH)
        break;

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

    plySum += ply;
    moveCount += 1;
    return result;
  }

  /** @return the name of this agent. **/
  @Override public String toString() {
    return name;
  }

  /** Unused. **/
  @Override public void finalBoard(Board finalBoard) {}
}
