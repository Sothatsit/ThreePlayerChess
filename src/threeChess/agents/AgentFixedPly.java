package threeChess.agents;

import threeChess.Agent;
import threeChess.Board;
import threeChess.Position;
import threeChess.agents.GameLogic.GameState;
import threeChess.agents.GameLogic.Move;
import threeChess.agents.GameLogic.GameConstants;
import threeChess.agents.GameLogic.CombinedGameConstants;
import threeChess.agents.strategy.MaximaxStrat;
import threeChess.agents.strategy.MoveStrat;

/**
 * A fixed-depth agent that uses maximax and optionally a custom utility function.
 *
 * @author Paddy Lamont, 22494652
 */
public final class AgentFixedPly extends Agent {

  /** The name of this agent. **/
  private String name;
  /** The depth to check moves to. **/
  public final int ply;
  /** The game constants used by this agent to evaluate states. **/
  public final CombinedGameConstants constants;

  /** The state object used to hold the initial board state. **/
  private final GameState initialState;
  /** The strategy to be used to determine moves. **/
  private final MoveStrat strategy;

  /** Used for genetic algorithm. **/
  public int consecutiveKeeps = 0;

  public AgentFixedPly(String name, int ply, CombinedGameConstants constants) {
    this.name = name;
    this.ply = ply;
    this.constants = constants;

    this.initialState = new GameState(constants);
    this.strategy = new MaximaxStrat(constants, ply);
  }

  /** Updates the name of this agent. **/
  public void setName(String name) {
    this.name = name;
  }

  /** @return an identical clone of this agent. **/
  @Override public AgentFixedPly clone() {
    return new AgentFixedPly(name, ply, constants);
  }

  /** @return a greedy agent, which is equivalent to a one-ply maximax agent. **/
  public static AgentFixedPly createGreedy() {
    return new AgentFixedPly("Greedy", 1, CombinedGameConstants.createDefault());
  }

  /** @return a mximax agent that explores to the given depth. **/
  public static AgentFixedPly createOfPly(int ply) {
    return new AgentFixedPly(ply + "-Ply-Maximax", ply, CombinedGameConstants.createDefault());
  }

  /** @return a move decided using maximax to this agent's fixed depth. **/
  @Override public Position[] playMove(Board board) {
    // Test that the custom GameState implementation matches the given Board logic.
    if (GameLogicTest.DO_VERIFY_BOARD_STATE_MATCHES) {
      initialState.copyFrom(board);
      GameLogicTest.verifyBoardMatches(board, initialState, true);
    }

    // Use the strategy to the given depth to choose the best move.
    initialState.copyFrom(board);
    constants.updateUtilities(initialState);
    Move move = strategy.decideMove(initialState);
    return GameConstants.convertMoveToPositions(move);
  }

  /** @return the name of this agent. **/
  @Override public String toString() {
    return name;
  }

  /** Unused. **/
  @Override public void finalBoard(Board finalBoard) {}
}
