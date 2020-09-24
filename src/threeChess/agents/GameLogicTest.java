package threeChess.agents;

import threeChess.*;
import threeChess.agents.GameLogic.GameState;
import threeChess.agents.GameLogic.Move;
import threeChess.agents.GameLogic.MoveMany;
import threeChess.agents.GameLogic.GameConstants;

import java.util.Arrays;

/**
 * Tests the GameLogic class to ensure it produces the
 * same outcome as using the default Board implementation.
 *
 * @author Paddy Lamont, 22494652
 */
public class GameLogicTest {

  /** Can be used to turn off board verification as it is slow. **/
  public static final boolean DO_VERIFY_BOARD_STATE_MATCHES = false;

  /** Thrown when the board representations do not match. **/
  private static class VerificationException extends RuntimeException {
    public VerificationException(String message) {
      super(message);
    }
    public VerificationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Ensures that {@param board} and {@param state} both match, optionally
   * also evaluating moves and checking if after that they still match.
   */
  public static void verifyBoardMatches(Board board, GameState state, boolean evaluateMoves) {
    if (!DO_VERIFY_BOARD_STATE_MATCHES)
      return;

    boolean stateGameOver = (state.gameOverPacked != 0);
    if (board.gameOver() != stateGameOver)
      throw new VerificationException("board.gameOver=" + board.gameOver() + " doesn't match stateGameOver=" + stateGameOver);

    int boardTurn = board.getTurn().ordinal();
    int stateTurn = state.turnColour;
    if (boardTurn != stateTurn)
      throw new VerificationException("boardTurn=" + boardTurn + ", but stateTurn=" + stateTurn);

    for (Position pos : Position.values()) {
      verifyPositionMatches(board, state, pos, evaluateMoves);
    }
  }

  public static void verifyPositionMatches(Board board, GameState state, Position pos, boolean evaluateMoves) {
    int index = GameConstants.getIndex(pos);
    Piece boardPiece = board.getPiece(pos);
    byte statePiece = state.pieces[index];
    int stateColour = statePiece & 3;
    int stateType = (statePiece >> 2) & 7;

    // If there's no piece in board, make sure there's no piece in state.
    if (boardPiece == null) {
      if (statePiece != 0)
        throw new VerificationException("boardPiece null, yet statePiece=" + statePiece + ", @ " + pos);
      return;
    } else if (statePiece == 0)
      throw new VerificationException("boardPiece non-null, yet statePiece=0, @ " + pos);

    // Check that the piece type matches.
    int boardType = boardPiece.getType().ordinal();
    if (stateType != boardType)
      throw new VerificationException("boardType=" + boardType + ", yet stateType=" + stateType + ", @ " + pos);

    // Check that the piece colour matches.
    int boardColour = boardPiece.getColour().ordinal();
    if (stateColour != boardColour)
      throw new VerificationException("boardColour=" + boardColour + ", yet stateColour=" + stateColour + ", @ " + pos);

    // Check that the list of valid moves matches.
    if (boardColour == board.getTurn().ordinal() && state.gameOverPacked == 0) {
      int locPieceColIndex = stateColour * (576 /* locColPieceIndexStride */) + index * (6 /* numPieces */);
      int directive = GameConstants.potentialMovesFlattenedDirectives[locPieceColIndex + stateType];
      int fromIndex = directive >> 8;
      int length = directive & 255;

      for (Position to : Position.values()) {
        int toIndex = GameConstants.getIndex(to);
        Move move = null;

        String additionalInfo = "";

        for (int moveIndex = 0; moveIndex < length; ++moveIndex) {
          Move m = GameConstants.potentialMovesFlattened[fromIndex + moveIndex];

          String info = "";

          byte toPiece = state.pieces[m.toIndex];
          int toColour = toPiece & 3;
          if (m instanceof MoveMany) {
            if (toPiece != 0) {
              moveIndex = ((MoveMany) m).skipIndex - 1;
              if (toColour == stateColour)
                continue;
            }
          } else if ((toPiece != 0 && toColour == stateColour) || !m.isValidMove(state))
            continue;

          if (m.toIndex == toIndex) {
            move = m;
            additionalInfo = info;
            break;
          }
        }

        if (board.isLegalMove(pos, to)) {
          if (move == null)
            throw new VerificationException("Move " + pos + " -> " + to + " is legal, yet no move available from state");

          if (evaluateMoves) {
            // Test the move.
            Board testBoard;
            try {
              testBoard = (Board) board.clone();
              testBoard.move(pos, to);
            } catch (CloneNotSupportedException | ImpossiblePositionException e) {
              throw new VerificationException("exception cloning board and playing move", e);
            }

            GameState testState = new GameState(state.constants);
            testState.copyFrom(state);
            testState.applyMove(move);

            try {
              verifyBoardMatches(testBoard, testState, false);
            } catch (VerificationException e) {
              System.err.println("From:\n" + state);
              System.err.println("To:\n" + testState);
              throw new VerificationException("verification exception after applying move " + move, e);
            }

            int[] updatedUtilities = Arrays.copyOf(testState.agentUtilities, 3);
            testState.calculateUtilities();
            for (Colour colour : Colour.values()) {
              int updated = updatedUtilities[colour.ordinal()];
              int fresh = testState.getUtility(colour.ordinal());
              if (updated != fresh)
                throw new VerificationException(colour + " utility does not match after move " + move + ", " + updated + " != " + fresh);
            }
          }
        } else {
          if (move != null) {
            StringBuilder extraInfo = new StringBuilder();
            if (move instanceof MoveMany) {
              Move[] moveArray = null;
              outer: for (Move[] potentialArray : GameConstants.potentialMoves) {
                for (Move potentialMove : potentialArray) {
                  if (potentialMove == move) {
                    moveArray = potentialArray;
                    break outer;
                  }
                }
              }
              for (int moveArrayIndex = 0; moveArrayIndex < moveArray.length; ++moveArrayIndex) {
                Move arrayMove = moveArray[moveArrayIndex];
                extraInfo.append("\n - ")
                    .append(moveArrayIndex)
                    .append(">")
                    .append(arrayMove.skipIndex)
                    .append(": ")
                    .append(arrayMove);
              }
            } else {
              extraInfo.append(": ").append(move).append(" (").append(additionalInfo).append(")");
            }
            throw new VerificationException(
                "Move " + pos + " -> " + to + " not legal, yet move available from state" + extraInfo
            );
          }
        }
      }
    }
  }
}
