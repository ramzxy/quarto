package game;

import Game.Game;
import Game.Move;

import Client.HumanPlayer;

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
        System.out.println("DEBUG: Class Name = " + this.getClass().getName());
        game.pickCurrentPiece(game.getAvailablePieces().get(0));
        validMoves = game.getValidMoves();
    }


    /**
     * Tests validMoves.
     */
    @Test
    public void testGetValidMoves(){
        game.doMove(new Move(0, game.getPieceById(10)));
        Assertions.assertFalse(validMoves.contains(new Move(0, game.getPieceById(10))));
    }

    /**
     * Testing that move doesn't return false.
     */
    @Test
    public void testDoMove(){
        Assertions.assertTrue(game.doMove(validMoves.get(1)));
        Assertions.assertTrue(game.doMove(validMoves.get(5)));
    }
}
