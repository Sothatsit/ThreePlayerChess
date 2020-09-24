## Compilation

The following command can be used to compile threeChess,
```
javac -d bin src/threeChess/*.java src/threeChess/agents/*.java src/threeChess/agents/strategy/*.java
```

This command should be run from the directory above the src directory, where
it will create a bin directory to compile the program's class files into.



## Running ThreeChess

### Single Game
The following command can be used to run a single game between three random agents,
```
java -cp bin/ threeChess.ThreeChess
```

The three random agents for the game will be randomly selected from the following list:
- **Random**
- **Greedy**
- **Brutus-Maximax**, a maximax agent
- **Brutus-Quiescence**, a maximax agent with quiescence
- **Brutus-RQ**, a maximax agent with restricted quiescence
- **Brutus-PVS**, a principal variation search agent
- **Brutus-Minimax**, a minimax agent


### Tournament
The following command can be used to run a tournament of games, where
each game is selected from the same list of agents above under _Single Game_.
```
java -cp bin/ threeChess.ThreeChess tournament
```


### Genetic Algorithm
The following command starts a genetic algorithm that continuously runs tournaments
between agents, culls the worst agents, generates new agents, and repeats in order
to find the most effective set of parameters.
```
java -cp bin/ threeChess.ThreeChess genetic
```
