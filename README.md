# ♛ Three-Player Chess ♚

This project is my submission for the CITS3001 unit at UWA where we were tasked to make an AI to play three-player chess.

#### The final agent I submitted is a combination of the following:

- The `AgentBrutus` class is the manager of the AI
([AgentBrutus.java](src/threeChess/agents/AgentBrutus.java)). \
  It uses iterative deepening to dynamically allocate how much time to spend searching for each move.

- The `MaximaxStrat` class holds the brains of the AI
  ([MaximaxStrat.java](src/threeChess/agents/strategy/MaximaxStrat.java)). \
  It decides which moves are the best moves to take from any state.

- The `GameLogic` class is the muscle of the AI
  ([GameLogic.java](src/threeChess/agents/GameLogic.java)). \
  It quickly evaluates moves, finds available moves, and iteratively updates utility as moves are made.

**Submission:** \
The tournament required us to submit only a single source file. Therefore, the above classes \
were combined into the single file, [Agent22494652.java](src/threeChess/agents/Agent22494652.java)
for submission. This file is large \
and hard to navigate, so I would suggest looking at the individual classes above instead.


# ♞ Usage ♞

Commands to compile and run this project can be found in [USAGE.md](USAGE.md).


# ♜ The Project ♜

See the parent repository at [drtnf/threeChess](https://github.com/drtnf/threeChess).
