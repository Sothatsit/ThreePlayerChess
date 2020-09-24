package threeChess.agents.strategy;

import threeChess.agents.GameLogic.Move;
import threeChess.agents.GameLogic.GameState;

/**
 * A strategy for deciding moves that can be used with Brutus or a fixed ply agent.
 *
 * @author Paddy Lamont, 22494652
 */
public abstract class MoveStrat {

  /**
   * @return the decided move to make using this strategy from the state {@param state}.
   */
  public abstract Move decideMove(GameState state);
}
