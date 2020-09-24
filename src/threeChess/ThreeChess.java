package threeChess;

import threeChess.agents.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Class with static methods for running tournaments and playing threeChess matches.
 * @author Tim French
 * **/
public class ThreeChess {

  /** Used to randomly select agents. **/
  private static final Random random = new Random();
  /** The agents to select from when playing games. **/
  private static final Agent[] AGENTS = {
      new RandomAgent(),
      new GreedyAgent(),
      AgentBrutus.createMaximax(),
      AgentBrutus.createQuiescence(),
      AgentBrutus.createRestrictedQuiescence(),
      AgentBrutus.createPVS(),
      AgentBrutus.createMinimax()
  };

  /**
   * The main entry-point to this program.
   *
   * Usage:
   *    java -jar threeChess
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      runOneGame();
      return;
    }

    String argument = args[0];
    if (args.length == 1) {
      if ("tournament".equalsIgnoreCase(argument)) {
        runTournament();
        return;
      } else if ("genetic".equalsIgnoreCase(argument)) {
        runGeneticAlgorithm();
        return;
      }
    }
    System.err.println("Unknown arguments " + Arrays.toString(args));
  }

  /**
   * Runs a single game between three random agents.
   */
  public static void runOneGame() {
    Agent[] agents = selectThreeAgents();
    int numGames = 1;
    int timeLimitSeconds = 10;
    int maximumTurns = 0;
    int pauseMS = 1000;
    int threads = 1;
    boolean displayOn = true;
    Tournament tournament = new Tournament(
        agents, numGames, timeLimitSeconds, maximumTurns,
        pauseMS, threads, System.out, displayOn
    );
    tournament.runTournament();
  }

  /**
   * Runs many games, with randomly chosen agents in each game.
   */
  public static void runTournament() {
    int numGames = 100;
    int timeLimitSeconds = 10;
    int maximumTurns = 500;
    int pauseMS = 0;
    int threads = Runtime.getRuntime().availableProcessors();
    boolean displayOn = false;
    Tournament tournament = new Tournament(
        AGENTS, numGames, timeLimitSeconds, maximumTurns,
        pauseMS, threads, System.out, displayOn
    );
    tournament.runTournament();
  }

  /**
   * Runs a genetic algorithm to determine the most effective agent parameters.
   */
  public static void runGeneticAlgorithm() {
    GeneticAlgorithm algorithm = new GeneticAlgorithm(
        20, // populationSize
        10, // gamesPerAgent
        0.2, // keepRatio
        0.8 // mutateRatio
    );
    algorithm.runCycles(50);
  }

  /** @return a random set of three agents. **/
  public static Agent[] selectThreeAgents() {
    List<Agent> available = new ArrayList<>(Arrays.asList(AGENTS));
    return new Agent[] {
        available.remove(random.nextInt(available.size())),
        available.remove(random.nextInt(available.size())),
        available.remove(random.nextInt(available.size()))
    };
  }
}
