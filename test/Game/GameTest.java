package Game;

import Client.HumanPlayer;
import Game.Move;
import Game.Game;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class GameTest {
    HumanPlayer player1 = new HumanPlayer("Player 1");
    HumanPlayer player2 = new HumanPlayer("Player 2");
    Game game = new Game(player1, player2);
    List<Move> validMoves;

    @BeforeEach
    public void setUp(){
        // game.start(); // Removed to prevent infinite loop
        game.pickCurrentPiece(game.getAvailablePieces().get(0));
        validMoves = game.getValidMoves();
    }


    @Test
    public void testGetValidMoves(){
        game.doMove(new Move(0, game.getPieceById(10)));
        Assertions.assertFalse(validMoves.contains(new Move(0, game.getPieceById(10))));
    }

    @Test
    public void testDoMove(){
        Assertions.assertTrue(game.doMove(validMoves.get(1)));
        Assertions.assertTrue(game.doMove(validMoves.get(5)));
    }

    @Test
    public void testNotifyMove(){

    }

    @Test
    public void testNotifyGameOver(){

    }
}
