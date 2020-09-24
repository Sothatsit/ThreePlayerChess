package threeChess;

import threeChess.agents.AgentFixedPly;
import threeChess.agents.GameLogic.CombinedGameConstants;
import threeChess.agents.GameLogic.GameConstants;

import java.util.List;
import java.util.Random;

/**
 * Uses a genetic algorithm to determine the most effective constants for utility functions.
 *
 * @author Paddy Lamont, 22494652
 */
public class GeneticAlgorithm {

  /**
   * The characters used for the names of agents that represent their different constants.
   * Unfortunately, these do not always work correctly in the Windows command prompt.
   */
  private static final char[] agentChars = {
      '\u2581', '\u2582', '\u2583', '\u2584', '\u2585', '\u2586', '\u2587', '\u2588'
  };

  private static final Random random = new Random();

  private final int gamesPerAgent;
  private final int keep;
  private final int mutate;
  private final AgentFixedPly[] population;

  public GeneticAlgorithm(int populationSize, int gamesPerAgent, double keepRatio, double mutateRatio) {
    this.gamesPerAgent = gamesPerAgent;
    this.keep = (int) (populationSize * keepRatio);
    this.mutate = (int) (populationSize * mutateRatio);
    this.population = new AgentFixedPly[populationSize];
    for (int i=0; i < populationSize; ++i) {
      if (i < keep) {
        population[i] = createAgent(CombinedGameConstants.createDefault());
      } else if (i < mutate) {
        population[i] = createAgent(CombinedGameConstants.mutate(
            CombinedGameConstants.createDefault(),
            CombinedGameConstants.createRandom()
        ));
      } else {
        population[i] = createRandomAgent();
      }
    }
  }

  /** Runs the given number of genetic algorithm cycles. **/
  public void runCycles(int cycles) {
    while(--cycles >= 0) {
      cycle();
    }
  }

  /** Performs one cycle of genetic algorithm. **/
  public void cycle() {
    updatePopulationNames();

    int numGames = gamesPerAgent * population.length;
    int timeLimitSeconds = 300;
    int maximumTurns = 4 * 99;
    int pauseMS = 0;
    int threads = Runtime.getRuntime().availableProcessors();
    boolean displayOn = false;
    Tournament tournament = new Tournament(
        population, numGames, timeLimitSeconds, maximumTurns,
        pauseMS, threads, System.out, displayOn
    );
    tournament.runTournament();

    int mostKept = 0;
    AgentFixedPly mostKeptAgent = null;

    List<Tournament.AgentStats> results = tournament.getDescendingStats();
    for (int index = 0; index < results.size(); ++index) {
      Tournament.AgentStats stats = results.get(index);
      AgentFixedPly statsAgent = (AgentFixedPly) stats.agent;

      AgentFixedPly agent;
      if (index < keep) {
        agent = statsAgent;
        agent.consecutiveKeeps += 1;

        if (agent.consecutiveKeeps > mostKept) {
          mostKept = agent.consecutiveKeeps;
          mostKeptAgent = agent;
        }
      } else if (index < mutate) {
        int maxMutations = 2;
        int k = maxMutations * (index - (keep + mutate) / 2) / ((mutate - keep) / 2);

        // Mutate with a random keep agent.
        AgentFixedPly randomKeepAgent = (AgentFixedPly) results.get(random.nextInt(keep)).agent;
        CombinedGameConstants constants = CombinedGameConstants.mutate(
            statsAgent.constants, randomKeepAgent.constants
        );
        // Mutate with self.
        while (k++ < 0) {
          constants = CombinedGameConstants.mutate(constants, statsAgent.constants);
        }
        // Mutate with random.
        if (k > 0) {
          constants = CombinedGameConstants.mutate(constants, CombinedGameConstants.createRandom());
        }
        // Mutate with random keep agent.
        while(k-- > 0) {
          randomKeepAgent = (AgentFixedPly) results.get(random.nextInt(keep)).agent;
          constants = CombinedGameConstants.mutate(constants, randomKeepAgent.constants);
        }
        agent = createAgent(constants);
      } else {
        agent = createRandomAgent();
      }

      population[index] = agent;
    }

    System.out.println(
        "\nCurrent best agent constants:"
            + "\n\tstart: " + mostKeptAgent.constants.start
            + "\n\tend: " + mostKeptAgent.constants.end + "\n"
    );
  }

  /** Updates the names of all agents in the population to reflect their values. **/
  private void updatePopulationNames() {
    // 2 sets of constants, 3 independent properties, and 5 type values
    int valueCount = 2 * (3 + 5);

    // Populate the array with the values of all of the agents.
    double[][] agentValues = new double[population.length][valueCount];
    for (int index = 0; index < population.length; ++index) {
      AgentFixedPly agent = population[index];
      CombinedGameConstants constants = agent.constants;
      GameConstants start = constants.start;
      GameConstants end = constants.end;

      double[] values = agentValues[index];
      values[0] = start.selfWeight;
      values[1] = end.selfWeight;
      values[2] = start.pawnRowValue;
      values[3] = end.pawnRowValue;
      values[4] = start.moveCountValue;
      values[5] = end.moveCountValue;
      System.arraycopy(start.typeValues, 0, values, 6, 5);
      System.arraycopy(end.typeValues, 0, values, 11, 5);
    }

    // Find the min and max values of each value.
    double[][] valueMinMaxs = new double[valueCount][];
    for (int valueIndex = 0; valueIndex < valueCount; ++valueIndex) {
      double min = Integer.MAX_VALUE;
      double max = Integer.MIN_VALUE;
      for (int index = 0; index < population.length; ++index) {
        double value = agentValues[index][valueIndex];
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
      valueMinMaxs[valueIndex] = new double[] {min, max};
    }

    // Construct names for each agent describing their values in respect to each other.
    for (int index = 0; index < population.length; ++index) {
      AgentFixedPly agent = population[index];
      double[] values = agentValues[index];

      StringBuilder builder = new StringBuilder();
      for (int valueIndex = 0; valueIndex < valueCount; ++valueIndex) {
        double[] minMax = valueMinMaxs[valueIndex];
        double min = minMax[0];
        double max = minMax[1];
        double value = values[valueIndex];
        int charIndex = (int) Math.floor(agentChars.length * (value - min) / (max - min));
        if (charIndex == agentChars.length) charIndex = agentChars.length - 1;
        builder.append(agentChars[charIndex]);
        if (valueIndex == 1 || valueIndex == 3 || valueIndex == 5) {
          builder.append(' ');
        }
      }
      agent.setName(builder.toString());
    }
  }

  /** @return an agent with random state evaluation constants. **/
  public AgentFixedPly createRandomAgent() {
    return createAgent(CombinedGameConstants.createRandom());
  }

  /** @return an agent with the given state evaluation constants. **/
  public AgentFixedPly createAgent(CombinedGameConstants constants) {
    return new AgentFixedPly("", 3, constants);
  }
}
